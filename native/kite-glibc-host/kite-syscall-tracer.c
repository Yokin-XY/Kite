/*
 * Kite 宿主车道监护进程（静态独立版）。
 *
 * 用法：kite-syscall-tracer <exec-path> [args...]
 * 依赖环境变量（调用方设好）：
 *   KITE_GLIBC_HOST_LOADER        loader 文件路径（解析 _r_debug 偏移）
 *   KITE_GLIBC_HOST_LIBRARY_PATH  冒号分隔库路径（定位 libc.so.6 扫 svc gadget）
 *
 * 机理见 kite-glibc-host-launcher.c 的监护模式注释：seccomp 仅拦 openat2
 * （SECCOMP_RET_TRACE，需 PTRACE_O_TRACESECCOMP），PC 注入两段单步降级为
 * openat。本二进制静态链接，Android shell 可直接 exec；孩子进程链通常为
 * kite-glibc-host launcher -> ld-linux -> node（每次 exec stop 刷新 loader
 * 基址缓存，最终以 loader 为准）。
 */
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/uio.h>
#include <linux/audit.h>
#include <linux/elf.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

static const char *required_env(const char *name) {
    const char *value = getenv(name);
    if (value == NULL || value[0] == '\0') {
        fprintf(stderr, "KITE_TRACE_INVALID_ENV %s\n", name);
        exit(125);
    }
    return value;
}

/* ===================== 监护模式（syscall tracer） ===================== */

#define AT_PHDR 3
#define DT_STRTAB 5
#define DT_SYMTAB 6

struct regs_arm64 {
    unsigned long long regs[31];
    unsigned long long sp;
    unsigned long long pc;
    unsigned long long pstate;
};

static int trace_debug = 0;

static void tlog(const char *fmt, ...) {
    if (!trace_debug) return;
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    fflush(stderr);
}

static int get_regs(pid_t tid, struct regs_arm64 *out) {
    struct iovec iov = { out, sizeof(*out) };
    return ptrace(PTRACE_GETREGSET, tid, (void *) (uintptr_t) NT_PRSTATUS, &iov) == 0 ? 0 : -1;
}

static int set_regs(pid_t tid, struct regs_arm64 *in) {
    struct iovec iov = { in, sizeof(*in) };
    return ptrace(PTRACE_SETREGSET, tid, (void *) (uintptr_t) NT_PRSTATUS, &iov) == 0 ? 0 : -1;
}

static long long peek_word(pid_t tid, unsigned long long addr) {
    errno = 0;
    long v = ptrace(PTRACE_PEEKDATA, tid, (void *) (uintptr_t) addr, 0);
    return errno ? -1 : (long long) v;
}

static int install_openat2_trace_filter(void) {
    /* 拦截 openat2（降级 openat）与路径型锁操作（/tmp 前缀翻译）。
     * 结构：LD nr; 每号 JEQ 命中跳 6（越过其余 JEQ 与 ALLOW）到各自的 RET_TRACE。 */
    struct sock_filter code[] = {
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, arch)),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AUDIT_ARCH_AARCH64, 1, 0),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
        /* 只拦 openat2（降级+/tmp 翻译）：Android 应用域的 zygote seccomp
         * 过滤器与我们的多层 filter 取最严动作，mkdirat/statx 等老号会
         * 被压成 ERRNO/KILL 导致 SIGSYS；这些号的 /tmp 重写改由 glibc 兼容
         * 层的版本化符号拦截（动态层，不受 seccomp 多层语义影响）完成。
         * openat2 是新号，zygote 对其默认放行，ret_trace 可正常接管。 */
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat2, 1, 0),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRACE | (SECCOMP_RET_DATA & (unsigned int) __NR_openat2)),
    };
    struct sock_fprog prog = {
        .len = (unsigned short) (sizeof(code) / sizeof(code[0])),
        .filter = code,
    };
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) return -1;
    if (prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &prog, 0, 0) != 0) return -1;
    return 0;
}

/* ---- 文件级 ELF 解析：loader 的 _r_debug 偏移、libc 的 svc gadget 偏移 ---- */

struct elf_map {
    unsigned char *data;
    size_t size;
};

static int elf_load(struct elf_map *m, const char *path) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    off_t len = lseek(fd, 0, SEEK_END);
    if (len <= 0 || len > (64 << 20)) { close(fd); return -1; }
    m->data = malloc((size_t) len);
    if (!m->data) { close(fd); return -1; }
    if (lseek(fd, 0, SEEK_SET) != 0 || read(fd, m->data, (size_t) len) != len) {
        free(m->data);
        m->data = NULL;
        close(fd);
        return -1;
    }
    close(fd);
    m->size = (size_t) len;
    return 0;
}

/* vaddr -> 文件偏移（按 PT_LOAD 映射）。 */
static long long elf_vaddr_to_off(struct elf_map *m, unsigned long long vaddr) {
    unsigned long long e_phoff, p;
    unsigned short e_phentsize, e_phnum;
    memcpy(&e_phoff, m->data + 0x20, 8);
    memcpy(&e_phentsize, m->data + 0x36, 2);
    memcpy(&e_phnum, m->data + 0x38, 2);
    if (e_phentsize != 56) return -1;
    for (unsigned short i = 0; i < e_phnum && i < 64; i++) {
        unsigned char *ph = m->data + e_phoff + (size_t) i * 56;
        unsigned int p_type;
        memcpy(&p_type, ph, 4);
        if (p_type != 1) continue; /* PT_LOAD */
        unsigned long long p_offset, p_vaddr, p_filesz;
        memcpy(&p_offset, ph + 8, 8);
        memcpy(&p_vaddr, ph + 16, 8);
        memcpy(&p_filesz, ph + 32, 8);
        (void) p;
        if (vaddr >= p_vaddr && vaddr < p_vaddr + p_filesz) {
            return (long long) (vaddr - p_vaddr + p_offset);
        }
    }
    return -1;
}

/* 在动态符号表中找符号，返回 st_value（vaddr，PIE 下即相对基址偏移）。 */
static unsigned long long elf_dynsym_value(struct elf_map *m, const char *name) {
    unsigned long long e_phoff, dyn_off = 0;
    unsigned short e_phentsize, e_phnum;
    memcpy(&e_phoff, m->data + 0x20, 8);
    memcpy(&e_phentsize, m->data + 0x36, 2);
    memcpy(&e_phnum, m->data + 0x38, 2);
    if (e_phentsize != 56) return 0;
    for (unsigned short i = 0; i < e_phnum && i < 64; i++) {
        unsigned char *ph = m->data + e_phoff + (size_t) i * 56;
        unsigned int p_type;
        memcpy(&p_type, ph, 4);
        if (p_type == 2) {
            unsigned long long p_offset;
            memcpy(&p_offset, ph + 8, 8);
            dyn_off = p_offset;
            break;
        }
    }
    if (!dyn_off) return 0;
    unsigned long long strtab_v = 0, symtab_v = 0;
    for (size_t j = 0; dyn_off + j * 16 + 16 <= m->size && j < 512; j++) {
        unsigned long long tag, val;
        memcpy(&tag, m->data + dyn_off + j * 16, 8);
        memcpy(&val, m->data + dyn_off + j * 16 + 8, 8);
        if (tag == 0) break;
        if (tag == DT_STRTAB) strtab_v = val;
        if (tag == DT_SYMTAB) symtab_v = val;
    }
    if (!strtab_v || !symtab_v) return 0;
    long long str_off = elf_vaddr_to_off(m, strtab_v);
    long long sym_off = elf_vaddr_to_off(m, symtab_v);
    if (str_off < 0 || sym_off < 0) return 0;
    /* symtab 条数上界：到 strtab 起点（常见布局 symtab 紧邻 strtab）。 */
    long long count = (str_off - sym_off) / 24;
    if (count <= 0 || count > 200000) count = 4000;
    for (long long i = 0; i < count; i++) {
        size_t pos = (size_t) sym_off + (size_t) i * 24;
        if (pos + 24 > m->size) break;
        unsigned int st_name;
        memcpy(&st_name, m->data + pos, 4);
        unsigned long long st_value;
        memcpy(&st_value, m->data + pos + 8, 8);
        if (!st_name) continue;
        size_t npos = (size_t) str_off + st_name;
        if (npos >= m->size) continue;
        const char *sym = (const char *) (m->data + npos);
        if (strcmp(sym, name) == 0) return st_value;
    }
    return 0;
}

/* 扫 libc 可执行段找裸 svc #0，返回 vaddr 偏移。 */
static unsigned long long elf_find_svc_gadget(struct elf_map *m) {
    unsigned long long e_phoff;
    unsigned short e_phentsize, e_phnum;
    memcpy(&e_phoff, m->data + 0x20, 8);
    memcpy(&e_phentsize, m->data + 0x36, 2);
    memcpy(&e_phnum, m->data + 0x38, 2);
    if (e_phentsize != 56) return 0;
    for (unsigned short i = 0; i < e_phnum && i < 64; i++) {
        unsigned char *ph = m->data + e_phoff + (size_t) i * 56;
        unsigned int p_type, p_flags;
        memcpy(&p_type, ph, 4);
        memcpy(&p_flags, ph + 4, 4);
        if (p_type != 1 || !(p_flags & 1)) continue; /* PT_LOAD + X */
        unsigned long long p_offset, p_vaddr, p_filesz;
        memcpy(&p_offset, ph + 8, 8);
        memcpy(&p_vaddr, ph + 16, 8);
        memcpy(&p_filesz, ph + 32, 8);
        for (unsigned long long k = 0; k + 4 <= p_filesz; k += 4) {
            unsigned int insn;
            memcpy(&insn, m->data + p_offset + k, 4);
            if (insn == 0xd4000001u) return p_vaddr + k;
        }
    }
    return 0;
}

/* 在 library path（冒号分隔）中找 libc.so.6 并解析 gadget。 */
static unsigned long long locate_libc_gadget_off(const char *library_path, char *lib_path_out, size_t out_len) {
    char buf[4096];
    snprintf(buf, sizeof(buf), "%s", library_path);
    char *save = NULL;
    for (char *dir = strtok_r(buf, ":", &save); dir; dir = strtok_r(NULL, ":", &save)) {
        char p[3800];
        snprintf(p, sizeof(p), "%s/libc.so.6", dir);
        struct elf_map m;
        if (elf_load(&m, p) != 0) continue;
        unsigned long long off = elf_find_svc_gadget(&m);
        if (off) {
            snprintf(lib_path_out, out_len, "%s", p);
            free(m.data);
            return off;
        }
        free(m.data);
    }
    return 0;
}

/* ---- tracee 侧定位：exec stop 缓存 loader 基址；seccomp 时走 link_map ---- */

static unsigned long long g_r_debug_off = 0;  /* loader 内 _r_debug 的 vaddr */
static unsigned long long g_gadget_off = 0;   /* libc 内 svc 的 vaddr */

static unsigned long long auxv_find(pid_t tid, unsigned long long sp, long key) {
    for (int i = 0; i < 2048; i++) {
        unsigned long long a = sp + (unsigned long long) i * 8;
        long long k = peek_word(tid, a);
        if (k < 0) return 0;
        if (k == 0) {
            long long v = peek_word(tid, a + 8);
            if (v == 0) return 0;
            continue;
        }
        if (k == key) {
            long long v = peek_word(tid, a + 8);
            return v > 0 ? (unsigned long long) v : 0;
        }
        i++;
    }
    return 0;
}

#define SLOT_MAX 256
static struct { pid_t tid; unsigned long long loader_base; } exec_slots[SLOT_MAX];

static void exec_cache(pid_t tid, unsigned long long sp) {
    unsigned long long phdr = auxv_find(tid, sp, AT_PHDR);
    if (!phdr) return;
    long long phoff = peek_word(tid, phdr - 0x40 + 0x20);
    if (phoff <= 0 || phoff > 0x10000) return;
    unsigned long long base = phdr - (unsigned long long) phoff;
    for (int i = 0; i < SLOT_MAX; i++) {
        if (exec_slots[i].tid == tid) { exec_slots[i].loader_base = base; return; }
    }
    for (int i = 0; i < SLOT_MAX; i++) {
        if (exec_slots[i].tid == 0) { exec_slots[i].tid = tid; exec_slots[i].loader_base = base; return; }
    }
}

static unsigned long long lookup_loader_base(pid_t tid) {
    for (int i = 0; i < SLOT_MAX; i++) {
        if (exec_slots[i].tid == tid) return exec_slots[i].loader_base;
    }
    return 0;
}

static int peek_name_contains(pid_t tid, unsigned long long addr, const char *needle) {
    /* 库名可能带 100+ 字节路径前缀（如 .../glibc-runtime/host/glibc/libc.so.6），
     * 窗口须覆盖完整路径；逐块读取，失败块视为串尾。 */
    char buf[216];
    for (int off = 0; off < 216; off += 8) {
        long long w = peek_word(tid, addr + (unsigned long long) off);
        if (w < 0) w = 0;
        memcpy(buf + off, &w, 8);
    }
    buf[sizeof(buf) - 1] = 0;
    return strstr(buf, needle) != NULL;
}

static unsigned long long find_libc_base(pid_t tid) {
    unsigned long long base = lookup_loader_base(tid);
    if (!base || !g_r_debug_off) return 0;
    unsigned long long r_debug = base + g_r_debug_off;
    unsigned long long lm = (unsigned long long) peek_word(tid, r_debug + 8);
    tlog("[ktrace] find_libc tid=%d base=%llx r_debug=%llx lm=%llx ver=%llx\n",
         tid, base, r_debug, lm, (unsigned long long) peek_word(tid, r_debug));
    for (int i = 0; i < 64 && lm; i++) {
        unsigned long long l_addr = (unsigned long long) peek_word(tid, lm);
        unsigned long long l_name = (unsigned long long) peek_word(tid, lm + 8);
        if (l_name && l_addr && peek_name_contains(tid, l_name, "libc.")) return l_addr;
        lm = (unsigned long long) peek_word(tid, lm + 24);
    }
    return 0;
}

/* ---- 注入（两段单步） ---- */

#define INJECT_MAX 256
static struct {
    pid_t tid;
    unsigned long long resume_pc;
    unsigned long long orig_lr;
    unsigned long long orig_x0;   /* 原始 dirfd：内核完成缓存的 openat2(ENOSYS)
                                     时 x0 会被写成 -38，第二段单步前需恢复。 */
    unsigned long long gadget;
    int stage;
    int active;
} injects[INJECT_MAX];

/* 该 tid 是否有活跃注入（两段单步中）。 */
static int inject_pending(pid_t tid) {
    for (int i = 0; i < INJECT_MAX; i++) {
        if (injects[i].active && injects[i].tid == tid) return 1;
    }
    return 0;
}

static void inject_register(pid_t tid, unsigned long long resume_pc,
                             unsigned long long orig_lr,
                             unsigned long long orig_x0, unsigned long long gadget) {
    for (int i = 0; i < INJECT_MAX; i++) {
        if (!injects[i].active) {
            injects[i].tid = tid;
            injects[i].resume_pc = resume_pc;
            injects[i].orig_lr = orig_lr;
            injects[i].orig_x0 = orig_x0;
            injects[i].gadget = gadget;
            injects[i].stage = 1;
            injects[i].active = 1;
            return;
        }
    }
}

static int inject_advance(pid_t tid, int *action) {
    for (int i = 0; i < INJECT_MAX; i++) {
        if (!injects[i].active || injects[i].tid != tid) continue;
        struct regs_arm64 regs;
        if (get_regs(tid, &regs) != 0) {
            injects[i].active = 0;
            *action = 0;
            return 1;
        }
        if (injects[i].stage == 1) {
            injects[i].stage = 2;
            regs.regs[0] = injects[i].orig_x0;
            set_regs(tid, &regs);
            *action = 1;
            return 1;
        }
            regs.pc = injects[i].resume_pc;
        regs.regs[30] = injects[i].orig_lr;
        set_regs(tid, &regs);
        injects[i].active = 0;
        *action = 0;
        return 1;
    }
    return 0;
}

static char g_guest_tmp[2048];
static size_t g_guest_tmp_len;
static int peek_path(pid_t tid, unsigned long long addr, char *out, size_t cap);
static int poke_bytes(pid_t tid, unsigned long long addr,
                      const char *data, size_t length);

static int rewrite_openat2(pid_t tid, struct regs_arm64 *regs,
                           unsigned long long *orig_lr, unsigned long long *orig_x0_out,
                           unsigned long long *gadget_out) {
    unsigned long long libc_base = find_libc_base(tid);
    if (!libc_base || !g_gadget_off) {
            return -1;
    }
    unsigned long long gadget = libc_base + g_gadget_off;
    unsigned long long how_addr = regs->regs[2];
    long long flags = how_addr ? peek_word(tid, how_addr) : -1;
    if (flags < 0) flags = O_RDONLY;
    long long mode = 0;
    if ((flags & O_CREAT) && how_addr) {
        mode = peek_word(tid, how_addr + 8);
        if (mode < 0) mode = 0;
    }
    *orig_lr = regs->regs[30];
    *gadget_out = gadget;
    orig_x0_out[0] = regs->regs[0];
    regs->regs[8] = __NR_openat;
    regs->regs[2] = (unsigned long long) flags;
    regs->regs[3] = (unsigned long long) mode;
    /* 降级后的 openat 用旧路径会在宿主车道 ENOENT（/tmp 不存在）；pathname
     * 以 /tmp 开头时同步翻译到 scratch（openat2 的 pathname 在 x1）。 */
    if (g_guest_tmp_len != 0) {
        char path[1024];
        int length = peek_path(tid, regs->regs[1], path, sizeof(path));
        if (length >= 4 && path[1] == 't' && path[2] == 'm' && path[3] == 'p' &&
            (path[4] == 0 || path[4] == '/')) {
            char mapped[3072];
            int written = snprintf(mapped, sizeof(mapped), "%s%s",
                                   g_guest_tmp, path + 4);
            if (written > 0 && (size_t) written < sizeof(mapped)) {
                unsigned long long scratch = (regs->sp - 8192) & ~7ULL;
                if (poke_bytes(tid, scratch, mapped, (size_t) written + 1) == 0) {
                    regs->regs[1] = scratch;
                }
            }
        }
    }
    regs->regs[30] = regs->pc; /* seccomp stop 时 pc 已是 svc+4（返回点）。 */
    regs->pc = gadget;
    return set_regs(tid, regs);
}

/*
 * 路径型 syscall 的 /tmp 前缀翻译（fs-safe 等 native addon 经 syscall() 直连，
 * JS 预载层与 glibc 符号拦截都覆盖不到）。做法与 openat2 降级同构：
 * seccomp stop 时把 PC 跳到 libc 的裸 svc 指令，寄存器预置翻译后的调用；
 * 内核先完成缓存的旧调用（对 /tmp 通常 ENOENT，无副作用），再执行注入的
 * 新调用，其返回值即最终结果。新路径写入 tracee 栈下方的 scratch 区。
 */
static int g_debug;

static int peek_path(pid_t tid, unsigned long long addr, char *out, size_t cap) {
    if (!addr) return -1;
    size_t n = 0;
    while (n + 8 <= cap) {
        errno = 0;
        long word = ptrace(PTRACE_PEEKDATA, tid, (void *) (uintptr_t) (addr + n), 0);
        if (errno != 0) return -1;
        unsigned char *bytes = (unsigned char *) &word;
        for (int i = 0; i < 8 && n < cap; i++, n++) {
            out[n] = (char) bytes[i];
            if (bytes[i] == 0) return (int) n;
        }
    }
    return -1;
}

static int poke_bytes(pid_t tid, unsigned long long addr,
                      const char *data, size_t length) {
    size_t done = 0;
    while (done < length) {
        size_t chunk = length - done;
        long word = 0;
        if (chunk >= 8) {
            memcpy(&word, data + done, 8);
        } else {
            errno = 0;
            long orig = ptrace(PTRACE_PEEKDATA, tid, (void *) (uintptr_t) (addr + done), 0);
            if (errno != 0) return -1;
            word = orig;
            memcpy(&word, data + done, chunk);
        }
        if (ptrace(PTRACE_POKEDATA, tid, (void *) (uintptr_t) (addr + done),
                   (void *) (uintptr_t) word) != 0) return -1;
        done += chunk >= 8 ? 8 : chunk;
    }
    return 0;
}

/* 命中路径型 syscall 且路径以 "/tmp" 开头时注入翻译调用；返回 0 表示已注入。 */
static int rewrite_tmp_path_syscall(pid_t tid, struct regs_arm64 *regs,
                                    unsigned long long nr,
                                    unsigned long long *orig_lr,
                                    unsigned long long *orig_x0_out,
                                    unsigned long long *gadget_out) {
    if (g_guest_tmp_len == 0) return -1;
    /* 所有目标 syscall 的 pathname 都在 a2（x1）。 */
    char path[1024];
    int length = peek_path(tid, regs->regs[1], path, sizeof(path));
    if (length < 4) return -1;
    if (path[1] != 't' || path[2] != 'm' || path[3] != 'p') return -1;
    if (path[4] != 0 && path[4] != '/') return -1;
    char mapped[3072];
    int written = snprintf(mapped, sizeof(mapped), "%s%s",
                           g_guest_tmp, path + 4);
    if (written <= 0 || (size_t) written >= sizeof(mapped)) return -1;

    unsigned long long libc_base = find_libc_base(tid);
    if (!libc_base || !g_gadget_off) return -1;
    unsigned long long gadget = libc_base + g_gadget_off;
    unsigned long long scratch = (regs->sp - 8192) & ~7ULL;
    if (poke_bytes(tid, scratch, mapped, (size_t) written + 1) != 0) return -1;

    *orig_lr = regs->regs[30];
    orig_x0_out[0] = regs->regs[0];
    *gadget_out = gadget;
    regs->regs[0] = (unsigned long long) -100; /* AT_FDCWD；绝对路径忽略 dirfd */
    regs->regs[1] = scratch;
    /* openat 的 flags/mode 在 a3/a4：搬移到注入调用的 x2/x3。 */
    if (nr == (unsigned long long) __NR_openat) {
        regs->regs[2] = regs->regs[2];
        regs->regs[3] = regs->regs[3];
    }
    regs->regs[30] = regs->pc; /* seccomp stop 时 pc 已是 svc+4（返回点）。 */
    regs->pc = gadget;
    return set_regs(tid, regs);
}

#define TRACEE_OPT (PTRACE_O_TRACEFORK | PTRACE_O_TRACEVFORK | \
                    PTRACE_O_TRACECLONE | PTRACE_O_TRACEEXEC | \
                    PTRACE_O_TRACESECCOMP | PTRACE_O_TRACEEXIT | \
                    PTRACE_O_TRACESYSGOOD | PTRACE_O_EXITKILL)

/* PTRACE_SYSCALL 模式下 per-tid 的入口/出口停交替状态。 */
struct entry_state {
    pid_t tid;
    int in_syscall;
    long last_nr;
    long ring[6];
    int ring_pos;
};
#define ENTRY_STATE_MAX 512
static struct entry_state g_entry_states[ENTRY_STATE_MAX];

static struct entry_state *entry_state_find(pid_t tid) {
    for (int i = 0; i < ENTRY_STATE_MAX; i++) {
        if (g_entry_states[i].tid == tid) return &g_entry_states[i];
        if (g_entry_states[i].tid == 0) {
            g_entry_states[i].tid = tid;
            g_entry_states[i].in_syscall = 0;
            g_entry_states[i].last_nr = -1;
            for (int k = 0; k < 6; k++) g_entry_states[i].ring[k] = -1;
            g_entry_states[i].ring_pos = 0;
            return &g_entry_states[i];
        }
    }
    return NULL;
}

static void install_sigsys_diag(void);

static int run_traced(char *exec_path, char **exec_argv) {
    char lib_path[3800];
    struct elf_map m;
    /* _r_debug 偏移从 loader 文件解析（exec_path 是 launcher，不含该符号）。 */
    if (elf_load(&m, required_env("KITE_GLIBC_HOST_LOADER")) != 0) {
        fputs("KITE_GLIBC_HOST_TRACE_LOADER_OPEN_FAILED\n", stderr);
        return 125;
    }
    g_r_debug_off = elf_dynsym_value(&m, "_r_debug");
    free(m.data);
    if (!g_r_debug_off) {
        fputs("KITE_GLIBC_HOST_TRACE_R_DEBUG_NOT_FOUND\n", stderr);
        return 125;
    }
    const char *library_path = required_env("KITE_GLIBC_HOST_LIBRARY_PATH");
    g_debug = getenv("KITE_SYSCALL_TRACER_DEBUG") != NULL;
    const char *guest_tmp = getenv("KITE_GLIBC_HOST_GUEST_TMP");
    if (guest_tmp != NULL && guest_tmp[0] == '/' && guest_tmp[1] != 0) {
        size_t n = strlen(guest_tmp);
        while (n > 0 && guest_tmp[n - 1] == '/') n -= 1;
        if (n > 0 && n + 1 <= sizeof(g_guest_tmp)) {
            memcpy(g_guest_tmp, guest_tmp, n);
            g_guest_tmp[n] = 0;
            g_guest_tmp_len = n;
        }
    }
    g_gadget_off = locate_libc_gadget_off(library_path, lib_path, sizeof(lib_path));
    if (!g_gadget_off) {
        fputs("KITE_GLIBC_HOST_TRACE_GADGET_NOT_FOUND\n", stderr);
        return 125;
    }
    tlog("[ktrace] r_debug_off=%llx gadget=%llx (%s)\n",
         g_r_debug_off, g_gadget_off, lib_path);

    int diag_prefilter = getenv("KITE_SYSCALL_TRACER_DIAG_ALL") != NULL;
    pid_t child = fork();
    if (child < 0) { perror("fork"); return 125; }
    if (child == 0) {
        install_sigsys_diag();
        if (!diag_prefilter && install_openat2_trace_filter() != 0) {
            perror("KITE_GLIBC_HOST_TRACE_SECCOMP");
            _exit(127);
        }
        if (ptrace(PTRACE_TRACEME, 0, 0, 0) != 0) {
            perror("KITE_GLIBC_HOST_TRACE_TRACEME");
            _exit(127);
        }
        execv(exec_path, exec_argv);
        perror("KITE_GLIBC_HOST_TRACE_EXECV");
        _exit(127);
    }

    int status;
    if (waitpid(child, &status, 0) != child) { perror("waitpid"); return 125; }
    if (ptrace(PTRACE_SETOPTIONS, child, 0, TRACEE_OPT) != 0) {
        perror("KITE_GLIBC_HOST_TRACE_SETOPTIONS");
        return 125;
    }
    {
        struct regs_arm64 r0;
        if (get_regs(child, &r0) == 0) exec_cache(child, r0.sp);
    }
    int diagnose_all = getenv("KITE_SYSCALL_TRACER_DIAG_ALL") != NULL;
    if (diagnose_all) trace_debug = 1;
    ptrace(diagnose_all ? PTRACE_SYSCALL : PTRACE_CONT, child, 0, 0);

    int exit_code = 0;
    int alive = 1;
    while (alive) {
        pid_t tid = waitpid(-1, &status, __WALL);
        if (tid < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (WIFEXITED(status) || WIFSIGNALED(status)) {
            struct entry_state *kes = entry_state_find(tid);
            if (WIFSIGNALED(status) && WTERMSIG(status) == SIGSEGV) {
                struct regs_arm64 rd;
                if (get_regs(tid, &rd) == 0) {
                    tlog("[ktrace] segv tid=%d pc=%llx lr=%llx sp=%llx x0=%llx x1=%llx\n",
                         tid, rd.pc, rd.regs[30], rd.sp, rd.regs[0], rd.regs[1]);
                }
            }
            tlog("[ktrace] tid=%d %s code=%d nr=%ld ring=%ld,%ld,%ld,%ld,%ld,%ld\n", tid,
                 WIFEXITED(status) ? "exit" : "killed",
                 WIFEXITED(status) ? WEXITSTATUS(status) : WTERMSIG(status),
                 kes != NULL ? kes->last_nr : -2,
                 kes != NULL ? kes->ring[0] : -2, kes != NULL ? kes->ring[1] : -2,
                 kes != NULL ? kes->ring[2] : -2, kes != NULL ? kes->ring[3] : -2,
                 kes != NULL ? kes->ring[4] : -2, kes != NULL ? kes->ring[5] : -2);
            if (tid == child) {
                alive = 0;
                exit_code = WIFEXITED(status) ? WEXITSTATUS(status)
                                              : 128 + WTERMSIG(status);
            }
            continue;
        }
        if (!WIFSTOPPED(status)) continue;
        unsigned event = (unsigned) status >> 16;
        int sig = WSTOPSIG(status);
        if (diagnose_all && event != 0 && sig == SIGTRAP) {
            tlog("[ktrace] event-stop tid=%d event=%u\n", tid, event);
        }
        if (diagnose_all && sig == (SIGTRAP | 0x80)) {
            /* PTRACE_SYSCALL 停。Android 应用域的 zygote seccomp 过滤器对
             * 白名单外系统调用直接 KILL(SIGSYS)，且多层 filter 取最严动作，
             * 我们的 SECCOMP_RET_TRACE 无法生效；而 ptrace 的 syscall 入口
             * 停发生在 seccomp 评估之前，是应用域内唯一可靠的内核前拦截点。
             * 入口停时检查 x8：openat2 现场降级为 openat（参数搬移+/tmp 路径
             * 翻译，与 seccomp 模式同一套 rewrite_openat2），其余号零成本放行
             * （PTRACE_PEEKUSER 只读一个字，开销 ~几十微秒/停）。 */
            /* arm64 无 PEEKUSER 寄存器语义，读 x8 必须 GETREGSET。入口/
             * 出口停用 per-tid 交替跟踪；attach/clone 首停（SIGSTOP/event）
             * 已在对应分支把 in_syscall 重置为 0，首个 syscall 停即入口。 */
            struct entry_state *es = entry_state_find(tid);
            int is_entry = 0;
            if (es != NULL) is_entry = !es->in_syscall, es->in_syscall = !es->in_syscall;
            long peek = -1;
            if (is_entry) {
                struct regs_arm64 rd;
                if (get_regs(tid, &rd) == 0) {
                    peek = (long) rd.regs[8];
                    int rewrite_kind = 0; /* 0=无 1=openat2 降级 2=无害替换 */
                    int o2_disabled = getenv("KITE_SYSCALL_TRACER_NO_O2") != NULL;
                    /* 保险丝：真入口停的 pc 必指向 svc #0 指令。交替状态可能
                     * 被信号/event 打断错位（出口停被当入口），此时 x8 是残
                     * 留值、pc 不在 svc 上，改写会造成执行流错位崩溃。 */
                    int pc_is_svc = 0;
                    {
                        errno = 0;
                        long long ins = ptrace(PTRACE_PEEKDATA, tid,
                                               (void *) (uintptr_t) rd.pc, 0);
                        if (errno == 0 && (ins & 0xFFFFFFFFLL) == 0xD4000001LL)
                            pc_is_svc = 1;
                        else if (peek == 437 || peek == 278 || peek == 99 || peek == 293)
                            tlog("[ktrace] skip-bogus-entry tid=%d nr=%ld pc=%llx\n",
                                 tid, peek, rd.pc);
                    }
                    if (!pc_is_svc) peek = -100; /* 屏蔽危险号改写判定 */
                    if (peek == (long) __NR_openat2 && !o2_disabled) {
                        unsigned long long orig_lr = 0, orig_x0 = 0, gadget = 0;
                        int rw = rewrite_openat2(tid, &rd, &orig_lr, &orig_x0, &gadget);
                        if (rw == 0) rewrite_kind = 1;
                    } else if (peek == 278 /* getrandom */) {
                        /* App 域 seccomp 对 getrandom 直接 KILL；root 对照证
                         * 实「SINGLESTEP 跳过」路径会留下隐性状态错乱（后续
                         * clock 调用链拿垃圾参数崩）。改走 gadget 注入（与
                         * openat2 同一状态机）：x8=sched_yield(124，白名单,
                         * 返回 0)。V8/glibc 看到 0 字节写入，自行走重试或
                         * fallback，不产生「假满」的垃圾缓冲。 */
                        unsigned long long libc_base = find_libc_base(tid);
                        if (libc_base != 0 && g_gadget_off != 0) {
                            unsigned long long gadget = libc_base + g_gadget_off;
                            unsigned long long orig_lr = rd.regs[30];
                            rd.regs[8] = 124; /* sched_yield -> 0 */
                            rd.regs[30] = rd.pc + 4;
                            rd.pc = gadget;
                            if (set_regs(tid, &rd) == 0) {
                                /* 登记注入状态机：stage1 恢复 x0=0（返回值），
                                 * stage2 恢复 pc/lr，避免单步停后裸跑 gadget
                                 * 所在 wrapper 的尾部（ldp 弹栈垃圾）。 */
                                inject_register(tid, rd.regs[30], orig_lr, 0, gadget);
                                rewrite_kind = 2;
                            }
                        }
                    }
                    if (rewrite_kind != 0) {
                        /* 4.19 内核在 syscall-entry 停恢复时执行缓存的
                         * 原调用（pc/x8 改动不取消）；必须单步注入 gadget
                         * 跳出本次停顿，与 seccomp 模式同一状态机。
                         * 单步链（stage1/stage2）不产生 syscall 出口停，
                         * 把交替状态重置回 0，保证下一个 syscall 停仍被
                         * 判定为入口。 */
                        if (es != NULL) es->in_syscall = 0;
                        if (rewrite_kind == 3) {
                            /* SINGLESTEP 只为丢弃缓存的 syscall：执行 pc 处
                             * （原 svc+4）一条指令后停（裸 TRAP），主循环吞掉
                             * 后继续。 */
                        }
                        ptrace(PTRACE_SINGLESTEP, tid, 0, 0);
                        continue;
                    }
                }
            }
            if (es != NULL) {
                es->last_nr = peek;
                es->ring[es->ring_pos % 6] = peek;
                es->ring_pos++;
                if (peek != -1) tlog("[ktrace] sys tid=%d nr=%ld\n", tid, peek);
            }
            ptrace(PTRACE_SYSCALL, tid, 0, 0);
            continue;
        }
        if (sig == SIGTRAP && event == 0) {
            /* 兜底：裸 TRAP（含 TRACEME exec 停）到达时确保 options 在位。 */
            ptrace(PTRACE_SETOPTIONS, tid, 0, TRACEE_OPT);
        }
        if (sig == SIGTRAP && event == PTRACE_EVENT_SECCOMP && inject_pending(tid)) {
            /* 注入的 gadget 调用撞到了自家 filter（如 mkdirat→mkdirat 同号）：
             * 这个 stop 属于进行中的注入状态机，绝不能当新事件重写——按原
             * 状态机推进（stage1 恢复参数并放行缓存调用，stage2 恢复现场）。 */
            int action = -1;
            if (inject_advance(tid, &action)) {
                if (action == 1) ptrace(PTRACE_SINGLESTEP, tid, 0, 0);
                else ptrace(diagnose_all ? PTRACE_SYSCALL : PTRACE_CONT, tid, 0, 0);
            } else {
                ptrace(diagnose_all ? PTRACE_SYSCALL : PTRACE_CONT, tid, 0, 0);
            }
        } else if (sig == SIGTRAP && event == PTRACE_EVENT_SECCOMP) {
            unsigned long long nr = 0;
            ptrace(PTRACE_GETEVENTMSG, tid, 0, &nr);
            tlog("[ktrace] seccomp stop tid=%d nr=%llu\n", tid, nr);
            struct regs_arm64 regs;
            unsigned long long orig_lr = 0, orig_x0 = 0, gadget_addr = 0;
            if (get_regs(tid, &regs) == 0 &&
                ((long) nr == __NR_openat2
                     ? rewrite_openat2(tid, &regs, &orig_lr, &orig_x0, &gadget_addr)
                     : rewrite_tmp_path_syscall(tid, &regs, nr, &orig_lr, &orig_x0, &gadget_addr)) == 0) {
                inject_register(tid, regs.regs[30], orig_lr, orig_x0, gadget_addr);
                ptrace(PTRACE_SINGLESTEP, tid, 0, 0);
            } else {
                ptrace(diagnose_all ? PTRACE_SYSCALL : PTRACE_CONT, tid, 0, 0);
            }
        } else if (sig == SIGTRAP && event == PTRACE_EVENT_EXEC) {
            ptrace(PTRACE_SETOPTIONS, tid, 0, TRACEE_OPT);
            struct regs_arm64 r0;
            if (get_regs(tid, &r0) == 0) exec_cache(tid, r0.sp);
            if (diagnose_all) {
                char ep[64], buf[4096];
                snprintf(ep, sizeof(ep), "/proc/%d/exe", tid);
                ssize_t n = readlink(ep, buf, sizeof(buf) - 1);
                if (n > 0) { buf[n] = 0; tlog("[ktrace] exec tid=%d exe=%s\n", tid, buf); }
            }
            ptrace(diagnose_all ? PTRACE_SYSCALL : PTRACE_CONT, tid, 0, 0);
        } else if (sig == SIGTRAP && (event == PTRACE_EVENT_FORK ||
                                      event == PTRACE_EVENT_VFORK ||
                                      event == PTRACE_EVENT_CLONE)) {
            ptrace(PTRACE_SETOPTIONS, tid, 0, TRACEE_OPT);
            /* fork/clone 的后代不 exec（如 node worker fork），没有自己的
             * auxv；地址空间继承自父，loader/libc 基址相同，复制槽位。 */
            unsigned long long new_tid = 0;
            ptrace(PTRACE_GETEVENTMSG, tid, 0, &new_tid);
            unsigned long long base = lookup_loader_base(tid);
            if (base) {
                int hit = 0;
                for (int i = 0; i < SLOT_MAX && !hit; i++) {
                    if (exec_slots[i].tid == (pid_t) new_tid) {
                        exec_slots[i].loader_base = base;
                        hit = 1;
                    }
                }
                for (int i = 0; i < SLOT_MAX && !hit; i++) {
                    if (exec_slots[i].tid == 0) {
                        exec_slots[i].tid = (pid_t) new_tid;
                        exec_slots[i].loader_base = base;
                        hit = 1;
                    }
                }
            }
            ptrace(diagnose_all ? PTRACE_SYSCALL : PTRACE_CONT, tid, 0, 0);
        } else if (sig == SIGTRAP && event == 0) {
            int action = -1;
            if (inject_advance(tid, &action)) {
                if (action == 1) ptrace(PTRACE_SINGLESTEP, tid, 0, 0);
                else ptrace(diagnose_all ? PTRACE_SYSCALL : PTRACE_CONT, tid, 0, 0);
            } else {
                ptrace(diagnose_all ? PTRACE_SYSCALL : PTRACE_CONT, tid, 0, 0);
            }
        } else if (sig == SIGTRAP || sig == SIGSTOP || sig == SIGTSTP ||
                   sig == SIGTTIN || sig == SIGTTOU) {
            if (diagnose_all && sig == SIGSTOP) {
                struct entry_state *rs = entry_state_find(tid);
                if (rs != NULL) rs->in_syscall = 0;
            }
            ptrace(diagnose_all ? PTRACE_SYSCALL : PTRACE_CONT, tid, 0, 0);
        } else {
            /* 信号投递停可能打断 syscall-entry 停（kernel 先报信号，恢复
             * 后 ERESTARTSYS 回到 svc 重试，产生第二次入口停）；交替状态
             * 必须重置，否则后续入/出判定永久反转，漏拦危险号。 */
            struct entry_state *ss = entry_state_find(tid);
            if (ss != NULL) ss->in_syscall = 0;
            if (sig == SIGSEGV || sig == SIGBUS || sig == SIGILL || sig == SIGSYS) {
                struct regs_arm64 rd;
                if (get_regs(tid, &rd) == 0) {
                    unsigned long long lb = find_libc_base(tid);
                    /* glibc __clock_gettime 帧结构：stp x29,x30,[sp,#-16]!
                     * → [sp]=saved x29, [sp+8]=caller 返回地址。 */
                    errno = 0;
                    long long cret = ptrace(PTRACE_PEEKDATA, tid,
                                            (void *) (uintptr_t) (rd.sp + 8), 0);
                    /* 垃圾参数的来源页：打 x1 与 x21 的 maps 归属。 */
                    if (rd.regs[1] > 0x1000) {
                        char lc[600];
                        snprintf(lc, sizeof(lc), "/proc/%d/maps", tid);
                        FILE *lf = fopen(lc, "r");
                        if (lf != NULL) {
                            char ln[512];
                            while (fgets(ln, sizeof(ln), lf) != NULL) {
                                unsigned long long ls, le;
                                if (sscanf(ln, "%llx-%llx", &ls, &le) == 2 &&
                                    rd.regs[1] >= ls && rd.regs[1] < le) {
                                    tlog("[ktrace] x1-map %llx %s", rd.regs[1], ln);
                                    break;
                                }
                            }
                            fclose(lf);
                        }
                    }
                    if (lb != 0) {
                        /* glibc __clock_gettime 的 vDSO 指针链：
                         * [libc+1af000+3728] -> A; [A+528] -> vDSO fn。 */
                        errno = 0;
                        long long a = ptrace(PTRACE_PEEKDATA, tid,
                                             (void *) (uintptr_t) (lb + 0x1af000ULL + 3728ULL), 0);
                        if (errno == 0 && a > 0x1000) {
                            errno = 0;
                            long long fn = ptrace(PTRACE_PEEKDATA, tid,
                                                  (void *) (uintptr_t) ((unsigned long long) a + 528ULL), 0);
                            tlog("[ktrace] vdsochain A=%llx fn=%llx\n", a, errno == 0 ? fn : -1LL);
                        }
                    }
                    tlog("[ktrace] fault-sig tid=%d sig=%d pc=%llx lr-off=%llx caller=%llx x0=%llx x1=%llx x2=%llx x19=%llx x20=%llx x21=%llx x28=%llx libc=%llx\n",
                         tid, sig, rd.pc,
                         lb != 0 ? rd.regs[30] - lb : 0,
                         (errno == 0 && cret > 0) ? (unsigned long long) cret : 0,
                         tid, sig, rd.pc,
                         lb != 0 ? rd.regs[30] - lb : 0, rd.regs[0], rd.regs[1], rd.regs[2],
                         rd.regs[19], rd.regs[20], rd.regs[21], rd.regs[28], lb);
                    char mp[64];
                    snprintf(mp, sizeof(mp), "/proc/%d/maps", tid);
                    FILE *mf = fopen(mp, "r");
                    if (mf != NULL) {
                        char line[512];
                        while (fgets(line, sizeof(line), mf) != NULL) {
                            unsigned long long s, e;
                            if (sscanf(line, "%llx-%llx", &s, &e) == 2 &&
                                ((rd.pc >= s && rd.pc < e) ||
                                 (rd.regs[30] >= s && rd.regs[30] < e))) {
                                tlog("[ktrace] fault-map %s", line);
                            }
                            if (strstr(line, "[vdso]") != NULL) {
                                /* dump 整个 vDSO 供本地反汇编定位崩点指令 */
                                unsigned long long vs = s;
                                FILE *vf = fopen("/data/user/0/com.kite.app/files/runtime/logs/vdso-dump.bin", "wb");
                                if (vf != NULL) {
                                    for (unsigned long long a = 0; a + 8 <= e - s; a += 8) {
                                        errno = 0;
                                        long long w = ptrace(PTRACE_PEEKDATA, tid,
                                                             (void *) (uintptr_t) (vs + a), 0);
                                        if (errno != 0) break;
                                        unsigned long long v = (unsigned long long) w;
                                        fwrite(&v, 8, 1, vf);
                                    }
                                    fclose(vf);
                                }
                                tlog("[ktrace] vdso %llx-%llx pc-off=%llx\n", s, e, rd.pc - s);
                                /* 回溯：读栈顶 24 个字，非零且像代码地址的行打 maps 归属 */
                                for (int qi = 0; qi < 24; qi++) {
                                    unsigned long long sv;
                                    errno = 0;
                                    long long wv = ptrace(PTRACE_PEEKDATA, tid,
                                                          (void *) (uintptr_t) (rd.sp + 8ULL * qi), 0);
                                    if (errno != 0) break;
                                    sv = (unsigned long long) wv;
                                    if (sv > 0x1000 && (sv >> 40) != 0) {
                                        char lc[600];
                                        snprintf(lc, sizeof(lc), "/proc/%d/maps", tid);
                                        FILE *lf = fopen(lc, "r");
                                        if (lf != NULL) {
                                            char ln[512];
                                            while (fgets(ln, sizeof(ln), lf) != NULL) {
                                                unsigned long long ls, le;
                                                if (sscanf(ln, "%llx-%llx", &ls, &le) == 2 &&
                                                    sv >= ls && sv < le) {
                                                    tlog("[ktrace] bt sp+%d=%llx %s", qi * 8, sv, ln);
                                                    break;
                                                }
                                            }
                                            fclose(lf);
                                        }
                                    }
                                }
                            }
                        }
                        fclose(mf);
                    }
                }
            }
            ptrace(diagnose_all ? PTRACE_SYSCALL : PTRACE_CONT, tid, 0, (void *) (intptr_t) sig);
        }
    }
    return exit_code;
}


/* ============================= 主流程 ============================= */

/* SIGSYS 诊断：seccomp 黑名单击杀时打印被拦的 syscall 号，
 * 便于区分 App 域继承的 zygote 过滤器与我们自己的过滤器。 */
static void kite_sigsys_handler(int sig, siginfo_t *info, void *ctx) {
    (void) sig; (void) ctx;
    char buf[96];
    int n = snprintf(buf, sizeof(buf),
                     "[ktrace] SIGSYS nr=%d code=%d errno=%d\n",
                     info->si_syscall, info->si_code,
                     info->si_errno);
    if (n > 0) {
        ssize_t w = write(2, buf, (size_t) n);
        (void) w;
    }
    _exit(231);
}

static void install_sigsys_diag(void) {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = kite_sigsys_handler;
    sa.sa_flags = SA_SIGINFO | SA_NODEFER;
    sigaction(SIGSYS, &sa, NULL);
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s <exec-path> [args...]\n", argv[0]);
        return 2;
    }
    required_env("KITE_GLIBC_HOST_LOADER");
    required_env("KITE_GLIBC_HOST_LIBRARY_PATH");
    install_sigsys_diag();
    trace_debug = getenv("KITE_SYSCALL_TRACER_DEBUG") != NULL;

    char **child_argv = calloc((size_t) argc, sizeof(char *));
    if (!child_argv) return 2;
    child_argv[0] = argv[1];
    for (int i = 2; i < argc; i++) child_argv[i - 1] = argv[i];
    child_argv[argc - 1] = NULL;
    return run_traced(argv[1], child_argv);
}
