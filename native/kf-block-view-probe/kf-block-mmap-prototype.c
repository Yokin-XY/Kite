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
#include <sys/time.h>
#include <time.h>
#include <unistd.h>

#define FILE_SIZE (1024ULL * 1024ULL * 1024ULL)
#define BLOCK_SIZE (64ULL * 1024ULL)
#define CHANGE_OFFSET (512ULL * 1024ULL * 1024ULL)
#define CHANGE_SIZE (4ULL * 1024ULL * 1024ULL)
#define MAP_LENGTH (8ULL * 1024ULL * 1024ULL)
#define MAP_OFFSET (510ULL * 1024ULL * 1024ULL)
#define BLOCK_COUNT (FILE_SIZE / BLOCK_SIZE)
#define MAX_MAPPINGS 4

typedef struct {
    uint8_t *address;
    size_t length;
    off_t file_offset;
} ViewMapping;

static int base_fd = -1;
static int delta_fd = -1;
static uint8_t bitmap[BLOCK_COUNT / 8];
static ViewMapping mappings[MAX_MAPPINGS];
static size_t mapping_count = 0;
static volatile sig_atomic_t handler_failed = 0;
static volatile sig_atomic_t fault_count = 0;
static uint8_t block_buffer[BLOCK_SIZE];

static int block_changed(uint64_t block) {
    return (bitmap[block / 8] & (uint8_t) (1U << (block % 8))) != 0;
}

static void mark_block_changed(uint64_t block) {
    bitmap[block / 8] |= (uint8_t) (1U << (block % 8));
}

static int full_pread(int fd, void *buffer, size_t length, off_t offset) {
    size_t done = 0;
    while (done < length) {
        ssize_t result = pread(fd, (uint8_t *) buffer + done, length - done,
                               offset + (off_t) done);
        if (result < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (result == 0) {
            memset((uint8_t *) buffer + done, 0, length - done);
            break;
        }
        done += (size_t) result;
    }
    return 0;
}

static int full_pwrite(int fd, const void *buffer, size_t length, off_t offset) {
    size_t done = 0;
    while (done < length) {
        ssize_t result = pwrite(fd, (const uint8_t *) buffer + done, length - done,
                                offset + (off_t) done);
        if (result < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        done += (size_t) result;
    }
    return 0;
}

static int ensure_block(uint64_t block) {
    off_t offset = (off_t) (block * BLOCK_SIZE);
    if (block_changed(block)) return 0;
    if (full_pread(base_fd, block_buffer, BLOCK_SIZE, offset) != 0) return -1;
    if (full_pwrite(delta_fd, block_buffer, BLOCK_SIZE, offset) != 0) return -1;
    mark_block_changed(block);
    return 0;
}

static int remap_block_in_all_mappings(uint64_t block) {
    off_t block_offset = (off_t) (block * BLOCK_SIZE);
    for (size_t i = 0; i < mapping_count; i++) {
        off_t mapping_end = mappings[i].file_offset + (off_t) mappings[i].length;
        if (block_offset < mappings[i].file_offset || block_offset >= mapping_end) continue;
        uint8_t *target = mappings[i].address + (block_offset - mappings[i].file_offset);
        void *result = mmap(target, BLOCK_SIZE, PROT_READ | PROT_WRITE,
                            MAP_SHARED | MAP_FIXED, delta_fd, block_offset);
        if (result == MAP_FAILED) return -1;
    }
    return 0;
}

static void segv_handler(int signal_number, siginfo_t *info, void *context) {
    (void) signal_number;
    (void) context;
    uintptr_t fault = (uintptr_t) info->si_addr;
    for (size_t i = 0; i < mapping_count; i++) {
        uintptr_t start = (uintptr_t) mappings[i].address;
        uintptr_t end = start + mappings[i].length;
        if (fault < start || fault >= end) continue;
        uint64_t logical = (uint64_t) mappings[i].file_offset + (fault - start);
        uint64_t block = logical / BLOCK_SIZE;
        if (ensure_block(block) != 0 || remap_block_in_all_mappings(block) != 0) {
            handler_failed = 1;
            return;
        }
        fault_count++;
        return;
    }
    handler_failed = 1;
}

static ViewMapping *map_view(off_t offset, size_t length) {
    if (mapping_count >= MAX_MAPPINGS) return NULL;
    void *address = mmap(NULL, length, PROT_READ, MAP_PRIVATE, base_fd, offset);
    if (address == MAP_FAILED) return NULL;
    ViewMapping *mapping = &mappings[mapping_count++];
    mapping->address = address;
    mapping->length = length;
    mapping->file_offset = offset;
    uint64_t first = (uint64_t) offset / BLOCK_SIZE;
    uint64_t last = ((uint64_t) offset + length - 1) / BLOCK_SIZE;
    for (uint64_t block = first; block <= last; block++) {
        if (block_changed(block) && remap_block_in_all_mappings(block) != 0) return NULL;
    }
    return mapping;
}

static int read_view(void *buffer, size_t length, off_t offset) {
    if (full_pread(base_fd, buffer, length, offset) != 0) return -1;
    uint64_t first = (uint64_t) offset / BLOCK_SIZE;
    uint64_t last = ((uint64_t) offset + length - 1) / BLOCK_SIZE;
    for (uint64_t block = first; block <= last; block++) {
        if (!block_changed(block)) continue;
        off_t block_start = (off_t) (block * BLOCK_SIZE);
        off_t copy_start = block_start > offset ? block_start : offset;
        off_t request_end = offset + (off_t) length;
        off_t block_end = block_start + BLOCK_SIZE;
        off_t copy_end = block_end < request_end ? block_end : request_end;
        if (full_pread(delta_fd, (uint8_t *) buffer + (copy_start - offset),
                       (size_t) (copy_end - copy_start), copy_start) != 0) return -1;
    }
    return 0;
}

static uint64_t monotonic_ns(void) {
    struct timespec value;
    clock_gettime(CLOCK_MONOTONIC, &value);
    return (uint64_t) value.tv_sec * 1000000000ULL + (uint64_t) value.tv_nsec;
}

static long long allocated_bytes(int fd) {
    struct stat stat;
    if (fstat(fd, &stat) != 0) return -1;
    return (long long) stat.st_blocks * 512LL;
}

int main(int argc, char **argv) {
    const char *root = argc > 1 ? argv[1] : ".";
    char base_path[1024];
    char delta_path[1024];
    snprintf(base_path, sizeof(base_path), "%s/mmap-base.bin", root);
    snprintf(delta_path, sizeof(delta_path), "%s/mmap-delta.bin", root);
    mkdir(root, 0700);
    unlink(base_path);
    unlink(delta_path);

    base_fd = open(base_path, O_CREAT | O_TRUNC | O_RDWR | O_CLOEXEC, 0600);
    delta_fd = open(delta_path, O_CREAT | O_TRUNC | O_RDWR | O_CLOEXEC, 0600);
    if (base_fd < 0 || delta_fd < 0 || ftruncate(base_fd, FILE_SIZE) != 0 ||
        ftruncate(delta_fd, FILE_SIZE) != 0) {
        perror("prepare files");
        return 2;
    }
    memset(block_buffer, 0x41, sizeof(block_buffer));
    for (off_t offset = CHANGE_OFFSET; offset < (off_t) (CHANGE_OFFSET + CHANGE_SIZE);
         offset += BLOCK_SIZE) {
        if (full_pwrite(base_fd, block_buffer, BLOCK_SIZE, offset) != 0) return 2;
    }
    fsync(base_fd);

    struct sigaction action;
    memset(&action, 0, sizeof(action));
    action.sa_sigaction = segv_handler;
    action.sa_flags = SA_SIGINFO;
    sigemptyset(&action.sa_mask);
    if (sigaction(SIGSEGV, &action, NULL) != 0) {
        perror("sigaction");
        return 2;
    }

    ViewMapping *first = map_view(MAP_OFFSET, MAP_LENGTH);
    ViewMapping *second = map_view(MAP_OFFSET, MAP_LENGTH);
    if (first == NULL || second == NULL) {
        perror("map view");
        return 2;
    }
    size_t relative = CHANGE_OFFSET - MAP_OFFSET;
    uint64_t started = monotonic_ns();
    memset(first->address + relative, 0x42, CHANGE_SIZE);
    uint64_t elapsed = monotonic_ns() - started;
    if (handler_failed) {
        fprintf(stderr, "signal remap failed\n");
        return 3;
    }
    msync(first->address + relative, CHANGE_SIZE, MS_SYNC);
    fsync(delta_fd);

    uint8_t base_value = 0;
    uint8_t view_value = 0;
    uint8_t second_value = second->address[relative];
    pread(base_fd, &base_value, 1, CHANGE_OFFSET);
    read_view(&view_value, 1, CHANGE_OFFSET);
    long long delta_allocated = allocated_bytes(delta_fd);

    printf("prototype\tbase_logical_bytes\t%llu\n", (unsigned long long) FILE_SIZE);
    printf("prototype\tchanged_bytes\t%llu\n", (unsigned long long) CHANGE_SIZE);
    printf("prototype\tdelta_allocated_bytes\t%lld\n", delta_allocated);
    printf("prototype\tfault_count\t%d\n", (int) fault_count);
    printf("prototype\twrite_elapsed_ms\t%.3f\n", (double) elapsed / 1000000.0);
    printf("prototype\tbase_byte\t%u\n", base_value);
    printf("prototype\tview_byte\t%u\n", view_value);
    printf("prototype\tsecond_mapping_byte\t%u\n", second_value);

    int ok = delta_allocated > 0 && delta_allocated <= 16LL * 1024LL * 1024LL &&
             fault_count == (sig_atomic_t) (CHANGE_SIZE / BLOCK_SIZE) &&
             base_value == 0x41 && view_value == 0x42 && second_value == 0x42;

    for (size_t i = 0; i < mapping_count; i++) munmap(mappings[i].address, mappings[i].length);
    close(delta_fd);
    close(base_fd);
    unlink(delta_path);
    unlink(base_path);
    rmdir(root);
    printf("result\t%s\n", ok ? "BLOCK_MMAP_PROTOTYPE_OK" : "BLOCK_MMAP_PROTOTYPE_FAILED");
    return ok ? 0 : 4;
}
