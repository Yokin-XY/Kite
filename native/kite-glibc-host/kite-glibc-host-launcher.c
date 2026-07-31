/*
 * Kite 通用 glibc 目标启动器。
 *
 * 启动器本身链接 Android/Bionic，只负责在应用进程域打开 DNS 配置，并通过
 * 已准备的 Ubuntu glibc loader 启动一个明确的 Linux ELF。它不解析 shell，
 * 也不决定 Node、Python 或最终应用的路由。
 */

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#define KITE_RESOLV_FD 99

static const char *required_env(const char *name) {
    const char *value = getenv(name);
    if (value == NULL || value[0] == '\0') {
        fprintf(stderr, "KITE_GLIBC_HOST_INVALID_ENV %s\n", name);
        exit(125);
    }
    return value;
}

static void prepare_resolver_fd(void) {
    const char *path = required_env("KITE_GLIBC_HOST_RESOLV_CONF");
    int source = open(path, O_RDONLY | O_CLOEXEC);
    if (source < 0) {
        fprintf(stderr, "KITE_GLIBC_HOST_RESOLV_OPEN_FAILED errno=%d\n", errno);
        exit(125);
    }
    if (dup2(source, KITE_RESOLV_FD) < 0) {
        fprintf(stderr, "KITE_GLIBC_HOST_RESOLV_DUP_FAILED errno=%d\n", errno);
        close(source);
        exit(125);
    }
    if (source != KITE_RESOLV_FD) close(source);

    int flags = fcntl(KITE_RESOLV_FD, F_GETFD);
    if (flags < 0 || fcntl(KITE_RESOLV_FD, F_SETFD, flags & ~FD_CLOEXEC) < 0) {
        fprintf(stderr, "KITE_GLIBC_HOST_RESOLV_INHERIT_FAILED errno=%d\n", errno);
        exit(125);
    }
}

int main(int argc, char **argv) {
    const char *loader = required_env("KITE_GLIBC_HOST_LOADER");
    const char *library_path = required_env("KITE_GLIBC_HOST_LIBRARY_PATH");
    const char *compat_library = required_env("KITE_GLIBC_HOST_COMPAT_LIBRARY");
    const char *target = required_env("KITE_GLIBC_HOST_TARGET");
    prepare_resolver_fd();

    /* loader, --library-path, libraries, --preload, compat, target, 参数..., NULL */
    char **loader_argv = calloc((size_t) argc + 7U, sizeof(char *));
    if (loader_argv == NULL) {
        fputs("KITE_GLIBC_HOST_ARGV_ALLOC_FAILED\n", stderr);
        return 125;
    }
    loader_argv[0] = (char *) loader;
    loader_argv[1] = "--library-path";
    loader_argv[2] = (char *) library_path;
    loader_argv[3] = "--preload";
    loader_argv[4] = (char *) compat_library;
    loader_argv[5] = (char *) target;
    for (int i = 1; i < argc; ++i) loader_argv[i + 5] = argv[i];
    loader_argv[argc + 5] = NULL;

    execv(loader, loader_argv);
    fprintf(stderr, "KITE_GLIBC_HOST_EXEC_FAILED errno=%d\n", errno);
    free(loader_argv);
    return 126;
}
