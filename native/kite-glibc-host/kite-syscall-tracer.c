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
    struct sock_filter code[] = {
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, arch)),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AUDIT_ARCH_AARCH64, 1, 0),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat2, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRACE | (SECCOMP_RET_DATA & (unsigned int) __NR_openat2)),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
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
    regs->regs[30] = regs->pc; /* seccomp stop 时 pc 已是 svc+4（返回点）。 */
    regs->pc = gadget;
    return set_regs(tid, regs);
}

#define TRACEE_OPT (PTRACE_O_TRACEFORK | PTRACE_O_TRACEVFORK | \
                    PTRACE_O_TRACECLONE | PTRACE_O_TRACEEXEC | \
                    PTRACE_O_TRACESECCOMP | PTRACE_O_TRACEEXIT | \
                    PTRACE_O_EXITKILL)

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
    g_gadget_off = locate_libc_gadget_off(library_path, lib_path, sizeof(lib_path));
    if (!g_gadget_off) {
        fputs("KITE_GLIBC_HOST_TRACE_GADGET_NOT_FOUND\n", stderr);
        return 125;
    }
    tlog("[ktrace] r_debug_off=%llx gadget=%llx (%s)\n",
         g_r_debug_off, g_gadget_off, lib_path);

    pid_t child = fork();
    if (child < 0) { perror("fork"); return 125; }
    if (child == 0) {
        if (install_openat2_trace_filter() != 0) {
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
    ptrace(PTRACE_CONT, child, 0, 0);

    int exit_code = 0;
    int alive = 1;
    while (alive) {
        pid_t tid = waitpid(-1, &status, __WALL);
        if (tid < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (WIFEXITED(status) || WIFSIGNALED(status)) {
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
        if (sig == SIGTRAP && event == PTRACE_EVENT_SECCOMP) {
            unsigned long long nr = 0;
            ptrace(PTRACE_GETEVENTMSG, tid, 0, &nr);
            struct regs_arm64 regs;
            unsigned long long orig_lr = 0, orig_x0 = 0, gadget_addr = 0;
            if ((long) nr == __NR_openat2 && get_regs(tid, &regs) == 0 &&
                rewrite_openat2(tid, &regs, &orig_lr, &orig_x0, &gadget_addr) == 0) {
                inject_register(tid, regs.regs[30], orig_lr, orig_x0, gadget_addr);
                ptrace(PTRACE_SINGLESTEP, tid, 0, 0);
            } else {
                ptrace(PTRACE_CONT, tid, 0, 0);
            }
        } else if (sig == SIGTRAP && event == PTRACE_EVENT_EXEC) {
            ptrace(PTRACE_SETOPTIONS, tid, 0, TRACEE_OPT);
            struct regs_arm64 r0;
            if (get_regs(tid, &r0) == 0) exec_cache(tid, r0.sp);
            ptrace(PTRACE_CONT, tid, 0, 0);
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
            ptrace(PTRACE_CONT, tid, 0, 0);
        } else if (sig == SIGTRAP && event == 0) {
            int action = -1;
            if (inject_advance(tid, &action)) {
                if (action == 1) ptrace(PTRACE_SINGLESTEP, tid, 0, 0);
                else ptrace(PTRACE_CONT, tid, 0, 0);
            } else {
                ptrace(PTRACE_CONT, tid, 0, 0);
            }
        } else if (sig == SIGTRAP || sig == SIGSTOP || sig == SIGTSTP ||
                   sig == SIGTTIN || sig == SIGTTOU) {
            ptrace(PTRACE_CONT, tid, 0, 0);
        } else {
            ptrace(PTRACE_CONT, tid, 0, (void *) (intptr_t) sig);
        }
    }
    return exit_code;
}


/* ============================= 主流程 ============================= */

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s <exec-path> [args...]\n", argv[0]);
        return 2;
    }
    required_env("KITE_GLIBC_HOST_LOADER");
    required_env("KITE_GLIBC_HOST_LIBRARY_PATH");
    trace_debug = getenv("KITE_SYSCALL_TRACER_DEBUG") != NULL;

    char **child_argv = calloc((size_t) argc, sizeof(char *));
    if (!child_argv) return 2;
    child_argv[0] = argv[1];
    for (int i = 2; i < argc; i++) child_argv[i - 1] = argv[i];
    child_argv[argc - 1] = NULL;
    return run_traced(argv[1], child_argv);
}
