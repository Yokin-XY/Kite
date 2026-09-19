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
