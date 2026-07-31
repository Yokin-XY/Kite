#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#define GIB (1024ULL * 1024ULL * 1024ULL)
#define MIB (1024ULL * 1024ULL)
#define MAP_OFFSET (510ULL * MIB)
#define MAP_LENGTH (8ULL * MIB)
#define CHANGE_OFFSET (512ULL * MIB)
#define CHANGE_SIZE (4ULL * MIB)
#define FORK_PWRITE_OFFSET (516ULL * MIB + 123ULL)
#define FORK_CHANGE_OFFSET (517ULL * MIB)
#define PWRITE_OFFSET (100ULL * MIB + 123ULL)
#define PWRITE_SIZE (128ULL * 1024ULL)
#define WRITE_OFFSET (200ULL * MIB + 17ULL)
#define WRITE_SIZE (96ULL * 1024ULL)
#define SHRINK_SIZE (600ULL * MIB + 123ULL)
#define FINAL_SIZE (700ULL * MIB)
#define ZERO_CHECK_OFFSET (650ULL * MIB)
#define TAIL_WRITE_OFFSET (699ULL * MIB)
#define TAIL_WRITE_SIZE 4096ULL

static int expect_bytes(int fd, off_t offset, size_t length, uint8_t expected) {
    uint8_t buffer[8192];
    size_t done = 0;
    while (done < length) {
        size_t requested = length - done;
        if (requested > sizeof(buffer)) requested = sizeof(buffer);
        ssize_t count = pread(fd, buffer, requested, offset + (off_t) done);
        if (count != (ssize_t) requested) {
            fprintf(stderr, "pread offset=%lld wanted=%zu got=%zd errno=%d\n",
                    (long long) (offset + (off_t) done), requested, count, errno);
            return -1;
        }
        for (ssize_t index = 0; index < count; index++) {
            if (buffer[index] != expected) {
                fprintf(stderr, "byte mismatch offset=%lld expected=%u actual=%u\n",
                        (long long) (offset + (off_t) done + index),
                        expected, buffer[index]);
                return -1;
            }
        }
        done += (size_t) count;
    }
    return 0;
}

static int write_pattern(int fd, off_t offset, size_t length, uint8_t value,
                         int positional) {
    uint8_t buffer[8192];
    size_t done = 0;
    memset(buffer, value, sizeof(buffer));
    if (!positional && lseek(fd, offset, SEEK_SET) != offset) return -1;
    while (done < length) {
        size_t requested = length - done;
        if (requested > sizeof(buffer)) requested = sizeof(buffer);
        ssize_t count = positional
            ? pwrite(fd, buffer, requested, offset + (off_t) done)
            : write(fd, buffer, requested);
        if (count != (ssize_t) requested) return -1;
        done += (size_t) count;
    }
    return 0;
}

static int verify_state(int fd) {
    struct stat st;
    uint8_t marker = 0;
    if (fstat(fd, &st) != 0 || st.st_size != (off_t) FINAL_SIZE) {
        fprintf(stderr, "virtual size mismatch=%lld errno=%d\n",
                (long long) st.st_size, errno);
        return 20;
    }
    if (pread(fd, &marker, 1, (off_t) MIB) != 1 || marker != 0x11) {
        fprintf(stderr, "base marker mismatch=%u\n", marker);
        return 21;
    }
    if (expect_bytes(fd, PWRITE_OFFSET, PWRITE_SIZE, 0x31) != 0) return 22;
    if (expect_bytes(fd, WRITE_OFFSET, WRITE_SIZE, 0x32) != 0) return 23;
    if (expect_bytes(fd, ZERO_CHECK_OFFSET, 1, 0x00) != 0) return 24;
    if (expect_bytes(fd, TAIL_WRITE_OFFSET, TAIL_WRITE_SIZE, 0x44) != 0) return 25;
    uint8_t *mapping = mmap(NULL, MAP_LENGTH, PROT_READ, MAP_SHARED, fd, MAP_OFFSET);
    if (mapping == MAP_FAILED) {
        perror("verify mmap");
        return 26;
    }
    size_t relative = CHANGE_OFFSET - MAP_OFFSET;
    if (mapping[relative] != 0x42 ||
        mapping[relative + CHANGE_SIZE - 1] != 0x42) {
        fprintf(stderr, "mmap persistence mismatch=%u/%u\n",
                mapping[relative], mapping[relative + CHANGE_SIZE - 1]);
        return 27;
    }
    if (mapping[FORK_CHANGE_OFFSET - MAP_OFFSET] != 0x6a) {
        fprintf(stderr, "cross-process mapping mismatch=%u\n",
                mapping[FORK_CHANGE_OFFSET - MAP_OFFSET]);
        return 28;
    }
    if (mapping[FORK_PWRITE_OFFSET - MAP_OFFSET] != 0x7b) {
        fprintf(stderr, "cross-process pwrite mapping mismatch=%u\n",
                mapping[FORK_PWRITE_OFFSET - MAP_OFFSET]);
        return 29;
    }
    munmap(mapping, MAP_LENGTH);
    return 0;
}

static int prepare_state(int fd) {
    struct stat st;
    uint8_t marker = 0;
    if (fstat(fd, &st) != 0 || st.st_size != (off_t) GIB) return 10;
    if (pread(fd, &marker, 1, (off_t) MIB) != 1 || marker != 0x11) return 11;
    if (write_pattern(fd, PWRITE_OFFSET, PWRITE_SIZE, 0x31, 1) != 0) return 12;
    if (write_pattern(fd, WRITE_OFFSET, WRITE_SIZE, 0x32, 0) != 0) return 13;
    if (ftruncate(fd, SHRINK_SIZE) != 0) return 14;
    if (ftruncate(fd, FINAL_SIZE) != 0) return 15;
    if (fstat(fd, &st) != 0 || st.st_size != (off_t) FINAL_SIZE) return 16;
    if (expect_bytes(fd, ZERO_CHECK_OFFSET, 1, 0x00) != 0) return 17;
    if (write_pattern(fd, TAIL_WRITE_OFFSET, TAIL_WRITE_SIZE, 0x44, 1) != 0)
        return 18;

    uint8_t *first = mmap(NULL, MAP_LENGTH, PROT_READ | PROT_WRITE,
                          MAP_SHARED, fd, MAP_OFFSET);
    if (first == MAP_FAILED) return 30;
    size_t relative = CHANGE_OFFSET - MAP_OFFSET;
    memset(first + relative, 0x42, CHANGE_SIZE);
    if (msync(first + relative, CHANGE_SIZE, MS_SYNC) != 0) return 31;
    uint8_t *second = mmap(NULL, MAP_LENGTH, PROT_READ | PROT_WRITE,
                           MAP_SHARED, fd, MAP_OFFSET);
    if (second == MAP_FAILED) return 32;
    if (first[relative] != 0x42 ||
        first[relative + CHANGE_SIZE - 1] != 0x42 ||
        second[relative] != 0x42 ||
        second[relative + CHANGE_SIZE - 1] != 0x42) return 33;
    pid_t child = fork();
    if (child < 0) return 35;
    if (child == 0) {
        uint8_t marker = 0x7b;
        first[FORK_CHANGE_OFFSET - MAP_OFFSET] = 0x6a;
        if (msync(first + (FORK_CHANGE_OFFSET - MAP_OFFSET), 1, MS_SYNC) != 0)
            _exit(36);
        ssize_t written = pwrite(fd, &marker, 1, FORK_PWRITE_OFFSET);
        if (written != 1)
            _exit(39);
        _exit(0);
    }
    int child_status = 0;
    pid_t waited = waitpid(child, &child_status, 0);
    if (waited != child || !WIFEXITED(child_status) || WEXITSTATUS(child_status) != 0) {
        fprintf(stderr,
                "child completion mismatch waited=%ld exited=%d status=%d signaled=%d signal=%d raw=%d\n",
                (long) waited, WIFEXITED(child_status),
                WIFEXITED(child_status) ? WEXITSTATUS(child_status) : -1,
                WIFSIGNALED(child_status),
                WIFSIGNALED(child_status) ? WTERMSIG(child_status) : -1,
                child_status);
        return 37;
    }
    if (first[FORK_CHANGE_OFFSET - MAP_OFFSET] != 0x6a ||
        second[FORK_CHANGE_OFFSET - MAP_OFFSET] != 0x6a) return 38;
    if (first[FORK_PWRITE_OFFSET - MAP_OFFSET] != 0x7b ||
        second[FORK_PWRITE_OFFSET - MAP_OFFSET] != 0x7b) return 40;
    if (fsync(fd) != 0) return 34;
    printf("tracee\tfirst_byte\t%u\n", first[relative]);
    printf("tracee\tsecond_mapping_byte\t%u\n", second[relative]);
    fflush(stdout);
    return 0;
}

int main(int argc, char **argv) {
    if (argc != 3) {
        fprintf(stderr, "usage: %s BASE prepare-crash|verify\n", argv[0]);
        return 2;
    }
    int flags = strcmp(argv[2], "verify") == 0 ? O_RDONLY : O_RDWR;
    int fd = open(argv[1], flags | O_CLOEXEC);
    if (fd < 0) {
        perror("open");
        return 3;
    }
    if (strcmp(argv[2], "prepare-crash") == 0) {
        int status = prepare_state(fd);
        if (status != 0) return status;
        printf("result\tPROOT_BLOCK_PREPARED\n");
        fflush(stdout);
        kill(getpid(), SIGKILL);
        return 90;
    }
    if (strcmp(argv[2], "verify") == 0) {
        int status = verify_state(fd);
        if (status != 0) return status;
        printf("result\tPROOT_BLOCK_REOPEN_OK\n");
        close(fd);
        return 0;
    }
    return 4;
}
