/*
 * Kite 宿主 glibc 兼容层。
 *
 * Android 应用 seccomp 会以 SIGSYS 拒绝部分较新的 Linux 系统调用。Linux 软件
 * 通常把 ENOSYS 当成“内核不支持”并走兼容路径，因此在进入 syscall 前提供同样语义。
 */

#define _GNU_SOURCE

#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <stddef.h>

typedef long (*kite_syscall_fn)(long number, ...);
typedef int (*kite_pthread_mutexattr_setrobust_fn)(pthread_mutexattr_t *attribute, int robustness);

kite_syscall_fn kite_real_syscall;
static kite_pthread_mutexattr_setrobust_fn real_pthread_mutexattr_setrobust;

__attribute__((constructor))
static void kite_resolve_symbols(void) {
    kite_real_syscall = (kite_syscall_fn) dlsym(RTLD_NEXT, "syscall");
    real_pthread_mutexattr_setrobust =
        (kite_pthread_mutexattr_setrobust_fn) dlsym(RTLD_NEXT, "pthread_mutexattr_setrobust");
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
