#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <linux/fs.h>
#include <linux/userfaultfd.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <sys/syscall.h>
#include <unistd.h>

#ifndef SYS_userfaultfd
#if defined(__aarch64__)
#define SYS_userfaultfd 282
#else
#error "SYS_userfaultfd is unknown for this architecture"
#endif
#endif

static int write_pattern(int fd, uint8_t value, size_t length, off_t offset) {
    uint8_t *buffer = malloc(1024 * 1024);
    if (buffer == NULL) return -1;
    memset(buffer, value, 1024 * 1024);
    size_t written = 0;
    while (written < length) {
        size_t chunk = length - written;
        if (chunk > 1024 * 1024) chunk = 1024 * 1024;
        ssize_t result = pwrite(fd, buffer, chunk, offset + (off_t) written);
        if (result <= 0) {
            free(buffer);
            return -1;
        }
        written += (size_t) result;
    }
    free(buffer);
    return 0;
}

static uint64_t free_bytes(const char *path) {
    struct statvfs stat;
    if (statvfs(path, &stat) != 0) return 0;
    return (uint64_t) stat.f_bavail * (uint64_t) stat.f_frsize;
}

static void print_result(const char *name, long long value, int error) {
    printf("probe\t%s\t%lld\terrno=%d\t%s\n",
           name, value, error, error == 0 ? "OK" : strerror(error));
}

int main(int argc, char **argv) {
    const char *root = argc > 1 ? argv[1] : ".";
    char base_path[1024];
    char clone_path[1024];
    char sparse_path[1024];
    snprintf(base_path, sizeof(base_path), "%s/base.bin", root);
    snprintf(clone_path, sizeof(clone_path), "%s/clone.bin", root);
    snprintf(sparse_path, sizeof(sparse_path), "%s/sparse.bin", root);
    mkdir(root, 0700);
    unlink(base_path);
    unlink(clone_path);
    unlink(sparse_path);

    const size_t base_size = 64ULL * 1024ULL * 1024ULL;
    const size_t change_size = 4ULL * 1024ULL * 1024ULL;
    int base = open(base_path, O_CREAT | O_TRUNC | O_RDWR | O_CLOEXEC, 0600);
    if (base < 0 || write_pattern(base, 0x41, base_size, 0) != 0 || fsync(base) != 0) {
        perror("prepare base");
        return 2;
    }

    uint64_t before_clone = free_bytes(root);
    int clone = open(clone_path, O_CREAT | O_TRUNC | O_RDWR | O_CLOEXEC, 0600);
    if (clone < 0) {
        perror("open clone");
        return 2;
    }
    errno = 0;
    int clone_result = ioctl(clone, FICLONE, base);
    int clone_errno = clone_result == 0 ? 0 : errno;
    fsync(clone);
    uint64_t after_clone = free_bytes(root);
    print_result("ficlone_result", clone_result, clone_errno);
    print_result("ficlone_space_delta_bytes",
                 (long long) (before_clone >= after_clone ? before_clone - after_clone : 0), 0);

    if (clone_result == 0) {
        uint64_t before_write = free_bytes(root);
        errno = 0;
        int write_result = write_pattern(clone, 0x42, change_size, 16ULL * 1024ULL * 1024ULL);
        int write_errno = write_result == 0 ? 0 : errno;
        fsync(clone);
        uint64_t after_write = free_bytes(root);
        print_result("ficlone_change_result", write_result, write_errno);
        print_result("ficlone_change_space_delta_bytes",
                     (long long) (before_write >= after_write ? before_write - after_write : 0), 0);

        uint8_t base_value = 0;
        uint8_t clone_value = 0;
        pread(base, &base_value, 1, 16ULL * 1024ULL * 1024ULL);
        pread(clone, &clone_value, 1, 16ULL * 1024ULL * 1024ULL);
        print_result("ficlone_base_byte", base_value, 0);
        print_result("ficlone_clone_byte", clone_value, 0);
    }

    int sparse = open(sparse_path, O_CREAT | O_TRUNC | O_RDWR | O_CLOEXEC, 0600);
    if (sparse >= 0) {
        int sparse_result = ftruncate(sparse, 1024LL * 1024LL * 1024LL);
        int sparse_errno = sparse_result == 0 ? 0 : errno;
        if (sparse_result == 0) {
            sparse_result = write_pattern(sparse, 0x43, change_size, 512LL * 1024LL * 1024LL);
            sparse_errno = sparse_result == 0 ? 0 : errno;
            fsync(sparse);
        }
        struct stat sparse_stat;
        memset(&sparse_stat, 0, sizeof(sparse_stat));
        fstat(sparse, &sparse_stat);
        print_result("sparse_result", sparse_result, sparse_errno);
        print_result("sparse_logical_bytes", (long long) sparse_stat.st_size, 0);
        print_result("sparse_allocated_bytes", (long long) sparse_stat.st_blocks * 512LL, 0);
        close(sparse);
    }

    errno = 0;
    int uffd = (int) syscall(SYS_userfaultfd, O_CLOEXEC | O_NONBLOCK);
    int uffd_errno = uffd >= 0 ? 0 : errno;
    print_result("userfaultfd_open", uffd, uffd_errno);
    if (uffd >= 0) {
        struct uffdio_api api;
        memset(&api, 0, sizeof(api));
        api.api = UFFD_API;
        api.features = UFFD_FEATURE_PAGEFAULT_FLAG_WP;
        errno = 0;
        int api_result = ioctl(uffd, UFFDIO_API, &api);
        int api_errno = api_result == 0 ? 0 : errno;
        print_result("userfaultfd_api", api_result, api_errno);
        print_result("userfaultfd_wp_feature",
                     (api.features & UFFD_FEATURE_PAGEFAULT_FLAG_WP) != 0, 0);
        close(uffd);
    }

    close(clone);
    close(base);
    unlink(sparse_path);
    unlink(clone_path);
    unlink(base_path);
    rmdir(root);
    return 0;
}
