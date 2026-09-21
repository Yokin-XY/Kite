/*
 * Kite 宿主 glibc 兼容层。
 *
 * Android 应用 seccomp 会以 SIGSYS 拒绝部分较新的 Linux 系统调用。Linux 软件
 * 通常把 ENOSYS 当成“内核不支持”并走兼容路径，因此在进入 syscall 前提供同样语义。
 */

#define _GNU_SOURCE

#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>

/*
 * 宿主进程的 "/tmp" 前缀重写。
 *
 * 背景：宿主车道进程没有根级 /tmp（Android 根文件系统只读）。部分 Linux
 * 软件把锁目录或临时目录硬编码为 /tmp（无环境变量配置口），在宿主车道
 * 会 ENOENT。车道在启动器环境注入 KITE_GLIBC_HOST_GUEST_TMP 指向一个
 * 可写的物理目录后本层激活：把 "/tmp" / "/tmp/..." 形式的路径重写为
 * 该目录。未注入时本层零行为；重写失败（缓冲区不足）保持原路径，
 * 让调用方拿到真实的失败语义。
 */

#define KITE_PATH_BUFFER 4096

static char kite_guest_tmp[KITE_PATH_BUFFER];
static size_t kite_guest_tmp_len;

typedef long (*kite_syscall_fn)(long number, ...);
typedef int (*kite_pthread_mutexattr_setrobust_fn)(pthread_mutexattr_t *attribute, int robustness);

kite_syscall_fn kite_real_syscall;
static kite_pthread_mutexattr_setrobust_fn real_pthread_mutexattr_setrobust;

__attribute__((constructor))
static void kite_resolve_symbols(void) {
    kite_real_syscall = (kite_syscall_fn) dlsym(RTLD_NEXT, "syscall");
    real_pthread_mutexattr_setrobust =
        (kite_pthread_mutexattr_setrobust_fn) dlsym(RTLD_NEXT, "pthread_mutexattr_setrobust");

    const char *guest_tmp = getenv("KITE_GLIBC_HOST_GUEST_TMP");
    if (guest_tmp == NULL || guest_tmp[0] != '/' || guest_tmp[1] == '\0') {
        return;
    }
    size_t length = strlen(guest_tmp);
    while (length > 1 && guest_tmp[length - 1] == '/') {
        length -= 1;
    }
    if (length == 0 || length + 1 > sizeof(kite_guest_tmp)) {
        return;
    }
    memcpy(kite_guest_tmp, guest_tmp, length);
    kite_guest_tmp[length] = '\0';
    kite_guest_tmp_len = length;
}

static const char *kite_map_path(const char *path, char *buffer) {
    if (kite_guest_tmp_len == 0 || path == NULL || path[0] != '/') {
        return path;
    }
    const char *rest;
    if (path[1] == '\0') {
        /* "/" 保持不变 */
        return path;
    } else if (path[1] == 't' && path[2] == 'm' && path[3] == 'p') {
        if (path[4] == '\0') {
            rest = "";
        } else if (path[4] == '/') {
            rest = path + 5;
        } else {
            /* "/tmpfoo" 等前缀巧合路径保持不变 */
            return path;
        }
    } else {
        return path;
    }
    size_t rest_length = strlen(rest);
    size_t needed = kite_guest_tmp_len + (rest_length > 0 ? 1 + rest_length : 0) + 1;
    if (needed > KITE_PATH_BUFFER) {
        return path;
    }
    memcpy(buffer, kite_guest_tmp, kite_guest_tmp_len);
    size_t offset = kite_guest_tmp_len;
    if (rest_length > 0) {
        buffer[offset] = '/';
        offset += 1;
        memcpy(buffer + offset, rest, rest_length);
        offset += rest_length;
    }
    buffer[offset] = '\0';
    return buffer;
}

#define KITE_RESOLVE(symbol, type, variable) \
    static type variable; \
    if (variable == NULL) { \
        variable = (type) dlsym(RTLD_NEXT, symbol); \
        if (variable == NULL) { \
            errno = ENOSYS; \
            return -1; \
        } \
    }

typedef int (*kite_path_open_fn)(const char *, int, ...);
typedef int (*kite_path_fn)(const char *);
typedef int (*kite_path_mode_fn)(const char *, mode_t);
typedef int (*kite_two_path_fn)(const char *, const char *);
typedef int (*kite_stat_fn)(const char *, struct stat *);
typedef int (*kite_fstatat_fn)(int, const char *, struct stat *, int);
typedef int (*kite_openat_fn)(int, const char *, int, ...);
typedef int (*kite_access_fn)(const char *, int);
typedef int (*kite_truncate_fn)(const char *, off_t);
typedef int (*kite_dirfd_path_mode_fn)(int, const char *, mode_t);
typedef int (*kite_dirfd_path_flags_fn)(int, const char *, int);
typedef int (*kite_renameat2_fn)(int, const char *, int, const char *, unsigned int);
typedef int (*kite_renameat_fn)(int, const char *, int, const char *);
typedef int (*kite_faccessat_fn)(int, const char *, int, int);
typedef int (*kite_linkat_fn)(int, const char *, int, const char *, int);

int open(const char *path, int flags, ...) {
    char buffer[KITE_PATH_BUFFER];
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list arguments;
        va_start(arguments, flags);
        mode = (mode_t) va_arg(arguments, int);
        va_end(arguments);
    }
    KITE_RESOLVE("open", kite_path_open_fn, kite_real_open)
    if ((flags & O_CREAT) == 0) {
        return kite_real_open(kite_map_path(path, buffer), flags);
    }
    return kite_real_open(kite_map_path(path, buffer), flags, mode);
}

int open64(const char *path, int flags, ...) {
    char buffer[KITE_PATH_BUFFER];
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list arguments;
        va_start(arguments, flags);
        mode = (mode_t) va_arg(arguments, int);
        va_end(arguments);
    }
    KITE_RESOLVE("open64", kite_path_open_fn, kite_real_open64)
    if ((flags & O_CREAT) == 0) {
        return kite_real_open64(kite_map_path(path, buffer), flags);
    }
    return kite_real_open64(kite_map_path(path, buffer), flags, mode);
}

int creat(const char *path, mode_t mode) {
    char buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("creat", kite_path_mode_fn, kite_real_creat)
    return kite_real_creat(kite_map_path(path, buffer), mode);
}

int openat(int directory, const char *path, int flags, ...) {
    char buffer[KITE_PATH_BUFFER];
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list arguments;
        va_start(arguments, flags);
        mode = (mode_t) va_arg(arguments, int);
        va_end(arguments);
    }
    KITE_RESOLVE("openat", kite_openat_fn, kite_real_openat)
    if ((flags & O_CREAT) == 0) {
        return kite_real_openat(directory, kite_map_path(path, buffer), flags);
    }
    return kite_real_openat(directory, kite_map_path(path, buffer), flags, mode);
}

int openat64(int directory, const char *path, int flags, ...) {
    char buffer[KITE_PATH_BUFFER];
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list arguments;
        va_start(arguments, flags);
        mode = (mode_t) va_arg(arguments, int);
        va_end(arguments);
    }
    KITE_RESOLVE("openat64", kite_openat_fn, kite_real_openat64)
    if ((flags & O_CREAT) == 0) {
        return kite_real_openat64(directory, kite_map_path(path, buffer), flags);
    }
    return kite_real_openat64(directory, kite_map_path(path, buffer), flags, mode);
}

static long kite_fallback_openat(long dirfd, const char *path, long flags, long mode);
static int kite_linkat_copy_fallback(
    int old_directory, const char *old_path,
    int new_directory, const char *new_path);

struct kite_open_how {
    unsigned long long flags;
    unsigned long long mode;
    unsigned long long resolve;
};

typedef int (*kite_openat2_fn)(int, const char *, const struct kite_open_how *, size_t);

/*
 * glibc 2.34+ 提供 openat2() 封装；Rust std 的 resolve_flags 语义走这里。
 * 旧内核（Android 4.19 等）没有该 syscall，proot 里曾由其模拟兜底，宿主
 * 直跑暴露真实 ENOSYS。这里按语义降级 openat（丢弃 resolve 约束），并做
 * 与符号层一致的 "/tmp" 前缀重写。
 */
int openat2(int directory, const char *path, const struct kite_open_how *how, size_t size) {
    char buffer[KITE_PATH_BUFFER];
    static kite_openat2_fn real_openat2;
    const struct kite_open_how empty = {0, 0, 0};
    if (how == NULL) how = &empty;
    (void) size;
    const char *mapped = kite_map_path(path, buffer);
    if (real_openat2 == NULL) {
        real_openat2 = (kite_openat2_fn) dlsym(RTLD_NEXT, "openat2");
        if (real_openat2 != NULL) {
            int result = real_openat2(directory, mapped, how, sizeof(*how));
            if (result >= 0 || errno != ENOSYS) return result;
        }
    } else {
        int result = real_openat2(directory, mapped, how, sizeof(*how));
        if (result >= 0 || errno != ENOSYS) return result;
    }
    errno = 0;
    if (kite_real_syscall == NULL) {
        errno = ENOSYS;
        return -1;
    }
    long flags = (long) how->flags;
    long mode = (long) how->mode;
    long syscall_result = kite_real_syscall(
        SYS_openat2, (long) directory, (long) mapped, (long) how, (long) sizeof(*how));
    if (syscall_result == -1 && errno == ENOSYS) {
        errno = 0;
        return (int) kite_fallback_openat(directory, mapped, flags, mode);
    }
    return (int) syscall_result;
}

int mkdir(const char *path, mode_t mode) {
    char buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("mkdir", kite_path_mode_fn, kite_real_mkdir)
    return kite_real_mkdir(kite_map_path(path, buffer), mode);
}

int mkdirat(int directory, const char *path, mode_t mode) {
    char buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("mkdirat", kite_dirfd_path_mode_fn, kite_real_mkdirat)
    return kite_real_mkdirat(directory, kite_map_path(path, buffer), mode);
}

int unlink(const char *path) {
    char buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("unlink", kite_path_fn, kite_real_unlink)
    return kite_real_unlink(kite_map_path(path, buffer));
}

int unlinkat(int directory, const char *path, int flags) {
    char buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("unlinkat", kite_dirfd_path_flags_fn, kite_real_unlinkat)
    return kite_real_unlinkat(directory, kite_map_path(path, buffer), flags);
}

int rmdir(const char *path) {
    char buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("rmdir", kite_path_fn, kite_real_rmdir)
    return kite_real_rmdir(kite_map_path(path, buffer));
}

int rename(const char *old_path, const char *new_path) {
    char old_buffer[KITE_PATH_BUFFER];
    char new_buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("rename", kite_two_path_fn, kite_real_rename)
    return kite_real_rename(kite_map_path(old_path, old_buffer), kite_map_path(new_path, new_buffer));
}

int renameat(int old_directory, const char *old_path, int new_directory, const char *new_path) {
    char old_buffer[KITE_PATH_BUFFER];
    char new_buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("renameat", kite_renameat_fn, kite_real_renameat)
    return kite_real_renameat(
        old_directory, kite_map_path(old_path, old_buffer),
        new_directory, kite_map_path(new_path, new_buffer));
}

int renameat2(int old_directory, const char *old_path, int new_directory, const char *new_path, unsigned int flags) {
    char old_buffer[KITE_PATH_BUFFER];
    char new_buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("renameat2", kite_renameat2_fn, kite_real_renameat2)
    return kite_real_renameat2(
        old_directory, kite_map_path(old_path, old_buffer),
        new_directory, kite_map_path(new_path, new_buffer),
        flags);
}

/*
 * Android SELinux（untrusted_app 域）拒绝 App 进程对 app_data_file 创建硬链接
 * （linkat 返回 EACCES，且多为 dontaudit 静默拒绝）。OpenClaw 等软件用“临时文件
 * + 硬链接”做原子发布（staging 目录写完 link 到正式名），在通用 Linux 合法，
 * 在 App 域全链路被拒。降级语义：源文件保留，目标位置得到同样内容的独立副本，
 * 用 copy+rename 完成（renameat 在 App 域允许，改名即原子可见）。
 * inode 不再共享，但 staging 目录随后被发布方删除，无共享需求。
 */
static int kite_linkat_copy_fallback(
    int old_directory, const char *old_path,
    int new_directory, const char *new_path
) {
    if (kite_real_syscall == NULL) {
        errno = ENOSYS;
        return -1;
    }
#define KITE_LINK_TRACE(step, err) \
    dprintf(2, "[kite-link-fallback] %s errno=%d path=%s\n", step, (int) (err), new_path)
    int source_fd = (int) kite_real_syscall(
        SYS_openat, old_directory, (long) old_path, O_RDONLY | O_CLOEXEC, 0L);
    if (source_fd < 0) { KITE_LINK_TRACE("open-src", errno); return -1; }
    struct stat status;
    if (kite_real_syscall(SYS_fstat, source_fd, (long) &status, 0L, 0L, 0L) != 0) {
        int saved_errno = errno;
        KITE_LINK_TRACE("fstat-src", saved_errno);
        kite_real_syscall(SYS_close, source_fd, 0L, 0L, 0L, 0L);
        errno = saved_errno;
        return -1;
    }
    char temporary[64];
    snprintf(temporary, sizeof temporary, ".kite-link-%ld-%d",
             (long) getpid(), (int) (status.st_ino & 0x7fffffff));
    int target_fd = (int) kite_real_syscall(
        SYS_openat, new_directory, (long) temporary,
        O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, (long) (status.st_mode & 0777));
    if (target_fd < 0) {
        int saved_errno = errno;
        KITE_LINK_TRACE("open-tmp", saved_errno);
        kite_real_syscall(SYS_close, source_fd, 0L, 0L, 0L, 0L);
        errno = saved_errno;
        return -1;
    }
    char buffer[65536];
    int failed = 0;
    for (;;) {
        long read_bytes = kite_real_syscall(SYS_read, source_fd, (long) buffer, sizeof buffer, 0L, 0L);
        if (read_bytes == 0) break;
        if (read_bytes < 0) { failed = 1; break; }
        long offset = 0;
        while (offset < read_bytes) {
            long written = kite_real_syscall(SYS_write, target_fd, (long) (buffer + offset), read_bytes - offset, 0L, 0L);
            if (written <= 0) { failed = 1; break; }
            offset += written;
        }
        if (failed) break;
    }
    kite_real_syscall(SYS_close, source_fd, 0L, 0L, 0L, 0L);
    if (kite_real_syscall(SYS_close, target_fd, 0L, 0L, 0L, 0L) != 0) failed = 1;
    if (failed) {
        int saved_errno = errno != 0 ? errno : EIO;
        KITE_LINK_TRACE("copy", saved_errno);
        kite_real_syscall(SYS_unlinkat, new_directory, (long) temporary, 0L, 0L, 0L);
        errno = saved_errno;
        return -1;
    }
    long renamed = kite_real_syscall(
        SYS_renameat, new_directory, (long) temporary, new_directory, (long) new_path, 0L);
    if (renamed != 0) {
        int saved_errno = errno;
        KITE_LINK_TRACE("rename", saved_errno);
        kite_real_syscall(SYS_unlinkat, new_directory, (long) temporary, 0L, 0L, 0L);
        errno = saved_errno;
        return -1;
    }
    return 0;
#undef KITE_LINK_TRACE
}

int linkat(int old_directory, const char *old_path, int new_directory, const char *new_path, int flags) {
    char old_buffer[KITE_PATH_BUFFER];
    char new_buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("linkat", kite_linkat_fn, kite_real_linkat)
    int result = kite_real_linkat(
        old_directory, kite_map_path(old_path, old_buffer),
        new_directory, kite_map_path(new_path, new_buffer),
        flags);
    if (result == 0 || errno != EACCES) return result;
    return kite_linkat_copy_fallback(
        old_directory, kite_map_path(old_path, old_buffer),
        new_directory, kite_map_path(new_path, new_buffer));
}

int link(const char *old_path, const char *new_path) {
    return linkat(AT_FDCWD, old_path, AT_FDCWD, new_path, 0);
}

int stat(const char *path, struct stat *status) {
    char buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("stat", kite_stat_fn, kite_real_stat)
    return kite_real_stat(kite_map_path(path, buffer), status);
}

int lstat(const char *path, struct stat *status) {
    char buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("lstat", kite_stat_fn, kite_real_lstat)
    return kite_real_lstat(kite_map_path(path, buffer), status);
}

int fstatat(int directory, const char *path, struct stat *status, int flags) {
    char buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("fstatat", kite_fstatat_fn, kite_real_fstatat)
    return kite_real_fstatat(directory, kite_map_path(path, buffer), status, flags);
}

int access(const char *path, int mode) {
    char buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("access", kite_access_fn, kite_real_access)
    return kite_real_access(kite_map_path(path, buffer), mode);
}

int faccessat(int directory, const char *path, int mode, int flags) {
    char buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("faccessat", kite_faccessat_fn, kite_real_faccessat)
    return kite_real_faccessat(directory, kite_map_path(path, buffer), mode, flags);
}

int truncate(const char *path, off_t length) {
    char buffer[KITE_PATH_BUFFER];
    KITE_RESOLVE("truncate", kite_truncate_fn, kite_real_truncate)
    return kite_real_truncate(kite_map_path(path, buffer), length);
}

/*
 * 路径型 syscall 重写层（与汇编 kite-glibc-syscall-arm64.S 的分流配套）。
 *
 * Rust 与静态二进制在宿主车道里直接内联 svc 发 syscall（不经 glibc 符号），
 * 但它们同时以动态符号引用 glibc 的 syscall() 函数包装；把带路径参数的
 * 常见文件 syscall 引到这里，用与符号拦截层相同的规则重写 "/tmp" 前缀。
 * 参数个数由 syscall 号精确决定，不做盲读；未知语义的号不会进入本层。
 */
long kite_syscall_path(long number, long a1, long a2, long a3, long a4, long a5);

struct kite_path_args {
    long args[5];
    int path_index;   /* -1 表示无路径参数 */
    int path_count;   /* 路径参数个数（renameat/linkat 有两个） */
};

static int kite_syscall_path_layout(long number, struct kite_path_args *layout) {
    layout->path_count = 1;
    switch (number) {
        case SYS_mkdirat:
        case SYS_mknodat:
        case SYS_unlinkat:
        case SYS_openat:
        case SYS_openat2:
        case SYS_newfstatat:
#ifdef SYS_fstatat
        case SYS_fstatat:
#endif
        case SYS_readlinkat:
        case SYS_faccessat:
        case SYS_faccessat2:
        case SYS_fchmodat:
        case SYS_fchownat:
        case SYS_statx:
            layout->path_index = 1;   /* (dirfd, path, ...) */
            return 1;
        case SYS_symlinkat:
        case SYS_linkat:
        case SYS_renameat:
        case SYS_renameat2:
            layout->path_index = 1;   /* (olddirfd, oldpath, newdirfd, newpath, ...) */
            layout->path_count = 2;
            return 1;
        default:
            return 0;
    }
}

static long kite_fallback_openat(long dirfd, const char *path, long flags, long mode) {
    if (flags & O_CREAT) {
        return kite_real_syscall(SYS_openat, dirfd, (long) path, flags, mode);
    }
    return kite_real_syscall(SYS_openat, dirfd, (long) path, flags);
}

long kite_syscall_path(long number, long a1, long a2, long a3, long a4, long a5) {
    if (kite_real_syscall == NULL) {
        errno = ENOSYS;
        return -1;
    }
    struct kite_path_args layout;
    if (kite_guest_tmp_len == 0 || !kite_syscall_path_layout(number, &layout)) {
        if (number == SYS_openat2) {
            long result = kite_real_syscall(number, a1, a2, a3, a4, a5);
            if (result == -1 && errno == ENOSYS) {
                struct { unsigned long long flags; unsigned long long mode; unsigned long long resolve; } how;
                memcpy(&how, (const void *) a3, sizeof(how));
                errno = 0;
                return kite_fallback_openat(a1, (const char *) a2, (long) how.flags, (long) how.mode);
            }
        }
        return kite_real_syscall(number, a1, a2, a3, a4, a5);
    }
    long args[5] = {a1, a2, a3, a4, a5};
    char buffer_a[KITE_PATH_BUFFER];
    char buffer_b[KITE_PATH_BUFFER];
    const char *original_a = (const char *) args[layout.path_index];
    const char *original_b = layout.path_count > 1 ? (const char *) args[layout.path_index + 2] : NULL;
    int rewritten = 0;
    (void) rewritten;
    if (original_a != NULL) {
        const char *mapped = kite_map_path(original_a, buffer_a);
        if (mapped != original_a) {
            args[layout.path_index] = (long) mapped;
            rewritten = 1;
        }
    }
    if (original_b != NULL) {
        const char *mapped = kite_map_path(original_b, buffer_b);
        if (mapped != original_b) {
            args[layout.path_index + 2] = (long) mapped;
            rewritten = 1;
        }
    }
    long result = kite_real_syscall(number, args[0], args[1], args[2], args[3], args[4]);
    if (result == -1 && errno == ENOSYS && number == SYS_openat2) {
        struct { unsigned long long flags; unsigned long long mode; unsigned long long resolve; } how;
        memcpy(&how, (const void *) args[2], sizeof(how));
        errno = 0;
        result = kite_fallback_openat(args[0], (const char *) args[1], (long) how.flags, (long) how.mode);
    }
    if (result == -1 && errno == EACCES && number == SYS_linkat) {
        /* App 域 SELinux 拒绝硬链接：与符号拦截层同样降级为 copy+rename。 */
        errno = 0;
        result = kite_linkat_copy_fallback(
            (int) args[0], (const char *) args[1], (int) args[2], (const char *) args[3]);
    }
    return result;
}

long kite_syscall_enosys(void) {
    errno = ENOSYS;
    return -1;
}

static int kite_set_mutex_robustness(
    pthread_mutexattr_t *attribute,
    int robustness,
    kite_pthread_mutexattr_setrobust_fn delegate
) {
    if (robustness == PTHREAD_MUTEX_ROBUST) {
        return ENOTSUP;
    }
    if (delegate != NULL) {
        return delegate(attribute, robustness);
    }
    return robustness == PTHREAD_MUTEX_STALLED ? 0 : EINVAL;
}

int pthread_mutexattr_setrobust(pthread_mutexattr_t *attribute, int robustness) {
    return kite_set_mutex_robustness(attribute, robustness, real_pthread_mutexattr_setrobust);
}
