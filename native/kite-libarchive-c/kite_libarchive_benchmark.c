#include <archive.h>
#include <archive_entry.h>

#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#define PATH_BUFFER_SIZE 4097
#define COPY_BUFFER_SIZE (64 * 1024)

typedef struct {
    char destination[PATH_BUFFER_SIZE];
    char target[PATH_BUFFER_SIZE];
    mode_t mode;
} deferred_hard_link;

static void report_failure(char const *reason)
{
    printf("failure|%s\n", reason);
    fflush(stdout);
}

static bool parse_u64(char const *value, uint64_t *result)
{
    char *end = NULL;
    errno = 0;
    unsigned long long parsed = strtoull(value, &end, 10);
    if (errno != 0 || end == value || *end != '\0') return false;
    *result = (uint64_t) parsed;
    return true;
}

static int normalize_path(
        char const *input,
        char output[PATH_BUFFER_SIZE],
        uint64_t maximum_depth)
{
    if (input == NULL) return -1;
    while (input[0] == '.' && input[1] == '/') input += 2;
    if (*input == '\0') {
        output[0] = '\0';
        return 0;
    }
    if (*input == '/' || strlen(input) >= PATH_BUFFER_SIZE) return -1;

    size_t output_length = 0;
    uint64_t depth = 0;
    char const *cursor = input;
    while (*cursor != '\0') {
        char const *slash = strchr(cursor, '/');
        size_t component_length = slash == NULL ? strlen(cursor) : (size_t) (slash - cursor);
        if (component_length == 0 ||
                (component_length == 1 && cursor[0] == '.') ||
                (component_length == 2 && cursor[0] == '.' && cursor[1] == '.')) {
            return -1;
        }
        for (size_t index = 0; index < component_length; index++) {
            unsigned char character = (unsigned char) cursor[index];
            if (character == '\\' || character < 0x20 || character == 0x7f) {
                return -1;
            }
        }
        if (++depth > maximum_depth) return -1;
        if (output_length != 0) output[output_length++] = '/';
        if (output_length + component_length >= PATH_BUFFER_SIZE) return -1;
        memcpy(output + output_length, cursor, component_length);
        output_length += component_length;
        if (slash == NULL) break;
        cursor = slash + 1;
    }
    output[output_length] = '\0';
    return 0;
}

static int create_parent_directories(char const *path)
{
    char mutable_path[PATH_BUFFER_SIZE];
    size_t length = strlen(path);
    if (length >= sizeof(mutable_path)) return -1;
    memcpy(mutable_path, path, length + 1);
    for (char *cursor = mutable_path + 1; *cursor != '\0'; cursor++) {
        if (*cursor != '/') continue;
        *cursor = '\0';
        struct stat metadata;
        if (lstat(mutable_path, &metadata) != 0) {
            if (errno != ENOENT || mkdir(mutable_path, 0700) != 0) return -1;
        } else if (!S_ISDIR(metadata.st_mode)) {
            return -1;
        }
        *cursor = '/';
    }
    return 0;
}

static int materialize_empty_file(char const *path, mode_t mode)
{
    if (create_parent_directories(path) != 0) return -1;
    int descriptor = open(path, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, mode);
    if (descriptor < 0) return -1;
    int close_result = close(descriptor);
    if (close_result != 0 || chmod(path, mode) != 0) return -1;
    return 0;
}

static int copy_regular_file(char const *target, char const *destination, mode_t mode)
{
    int input = open(target, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (input < 0) return errno == ENOENT ? 0 : -1;
    struct stat metadata;
    if (fstat(input, &metadata) != 0 || !S_ISREG(metadata.st_mode)) {
        close(input);
        return -1;
    }
    if (create_parent_directories(destination) != 0) {
        close(input);
        return -1;
    }
    int output = open(
            destination,
            O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW,
            mode);
    if (output < 0) {
        close(input);
        return -1;
    }

    char *buffer = malloc(COPY_BUFFER_SIZE);
    if (buffer == NULL) {
        close(input);
        close(output);
        return -1;
    }
    int result = 1;
    for (;;) {
        ssize_t count = read(input, buffer, COPY_BUFFER_SIZE);
        if (count == 0) break;
        if (count < 0) {
            result = -1;
            break;
        }
        ssize_t offset = 0;
        while (offset < count) {
            ssize_t written = write(output, buffer + offset, (size_t) (count - offset));
            if (written <= 0) {
                result = -1;
                break;
            }
            offset += written;
        }
        if (result < 0) break;
    }
    free(buffer);
    if (close(input) != 0 || close(output) != 0 || chmod(destination, mode) != 0) result = -1;
    if (result < 0) unlink(destination);
    return result;
}

static int append_deferred_link(
        deferred_hard_link **links,
        size_t *count,
        size_t *capacity,
        char const *destination,
        char const *target,
        mode_t mode)
{
    if (*count == *capacity) {
        size_t next_capacity = *capacity == 0 ? 16 : *capacity * 2;
        deferred_hard_link *next = realloc(*links, next_capacity * sizeof(**links));
        if (next == NULL) return -1;
        *links = next;
        *capacity = next_capacity;
    }
    deferred_hard_link *link = &(*links)[(*count)++];
    strcpy(link->destination, destination);
    strcpy(link->target, target);
    link->mode = mode;
    return 0;
}

static int materialize_deferred_links(deferred_hard_link *links, size_t count)
{
    size_t unresolved = count;
    bool *completed = calloc(count == 0 ? 1 : count, sizeof(bool));
    if (completed == NULL) return -1;
    while (unresolved > 0) {
        size_t progress = 0;
        for (size_t index = 0; index < count; index++) {
            if (completed[index]) continue;
            int result = copy_regular_file(
                    links[index].target,
                    links[index].destination,
                    links[index].mode);
            if (result < 0) {
                free(completed);
                return -1;
            }
            if (result > 0) {
                completed[index] = true;
                unresolved--;
                progress++;
            }
        }
        if (progress == 0) {
            free(completed);
            return -1;
        }
    }
    free(completed);
    return 0;
}

int main(int argc, char **argv)
{
    if (argc != 12) {
        report_failure("request_invalid");
        return 2;
    }
    char const *source = argv[1];
    char const *destination = argv[2];
    char const *staging = argv[3];
    char const *special_policy = argv[10];
    uint64_t maximum_archive_bytes;
    uint64_t maximum_entries;
    uint64_t maximum_total_bytes;
    uint64_t maximum_file_bytes;
    uint64_t maximum_depth;
    uint64_t maximum_expansion_ratio;
    uint64_t cancel_after_bytes;
    if (!parse_u64(argv[4], &maximum_archive_bytes) ||
            !parse_u64(argv[5], &maximum_entries) ||
            !parse_u64(argv[6], &maximum_total_bytes) ||
            !parse_u64(argv[7], &maximum_file_bytes) ||
            !parse_u64(argv[8], &maximum_depth) ||
            !parse_u64(argv[9], &maximum_expansion_ratio) ||
            !parse_u64(argv[11], &cancel_after_bytes)) {
        report_failure("request_invalid");
        return 2;
    }
    if (source[0] != '/' || destination[0] != '/' || staging[0] != '/' ||
            maximum_entries == 0 || maximum_depth == 0 || maximum_expansion_ratio == 0 ||
            (strcmp(special_policy, "reject") != 0 &&
                strcmp(special_policy, "skip") != 0 &&
                strcmp(special_policy, "materialize_empty_file") != 0)) {
        report_failure("request_invalid");
        return 2;
    }

    struct stat source_metadata;
    if (lstat(source, &source_metadata) != 0 || !S_ISREG(source_metadata.st_mode) ||
            (uint64_t) source_metadata.st_size > maximum_archive_bytes ||
            access(destination, F_OK) == 0 || access(staging, F_OK) == 0 ||
            mkdir(staging, 0700) != 0 || chdir(staging) != 0) {
        report_failure("path_invalid");
        return 2;
    }

    struct archive *reader = archive_read_new();
    if (reader == NULL || archive_read_support_filter_gzip(reader) != ARCHIVE_OK ||
            archive_read_support_format_tar(reader) != ARCHIVE_OK ||
            archive_read_open_filename(reader, source, COPY_BUFFER_SIZE) != ARCHIVE_OK) {
        if (reader != NULL) archive_read_free(reader);
        report_failure("archive_open_failed");
        return 2;
    }

    int extract_flags = ARCHIVE_EXTRACT_PERM |
        ARCHIVE_EXTRACT_SECURE_NODOTDOT |
        ARCHIVE_EXTRACT_SECURE_NOABSOLUTEPATHS |
        ARCHIVE_EXTRACT_SECURE_SYMLINKS;
    uint64_t entries = 0;
    uint64_t total_bytes = 0;
    int exit_code = 0;
    char const *failure_reason = NULL;
    deferred_hard_link *deferred_links = NULL;
    size_t deferred_count = 0;
    size_t deferred_capacity = 0;
    struct archive_entry *entry;

    for (;;) {
        int header_result = archive_read_next_header(reader, &entry);
        if (header_result == ARCHIVE_EOF) break;
        if (header_result != ARCHIVE_OK) {
            failure_reason = "entry_unreadable";
            exit_code = 2;
            break;
        }
        entries++;
        if (entries > maximum_entries) {
            failure_reason = "entry_limit";
            exit_code = 2;
            break;
        }
        int64_t declared_size = archive_entry_size(entry);
        if (declared_size < 0) declared_size = 0;
        if ((uint64_t) declared_size > maximum_file_bytes ||
                UINT64_MAX - total_bytes < (uint64_t) declared_size) {
            failure_reason = "file_size_limit";
            exit_code = 2;
            break;
        }
        total_bytes += (uint64_t) declared_size;
        if (total_bytes > maximum_total_bytes ||
                ((uint64_t) source_metadata.st_size > 0 &&
                    total_bytes / (uint64_t) source_metadata.st_size > maximum_expansion_ratio)) {
            failure_reason = "total_size_limit";
            exit_code = 2;
            break;
        }
        if (cancel_after_bytes > 0 && total_bytes >= cancel_after_bytes) {
            exit_code = 3;
            break;
        }

        char normalized_path[PATH_BUFFER_SIZE];
        if (normalize_path(archive_entry_pathname(entry), normalized_path, maximum_depth) != 0) {
            fprintf(stderr, "path_invalid:%s\n", archive_entry_pathname(entry));
            failure_reason = "path_invalid";
            exit_code = 2;
            break;
        }
        if (normalized_path[0] == '\0') {
            archive_read_data_skip(reader);
            continue;
        }
        archive_entry_set_pathname(entry, normalized_path);

        mode_t file_type = archive_entry_filetype(entry);
        if (file_type == AE_IFCHR || file_type == AE_IFBLK || file_type == AE_IFIFO) {
            if (strcmp(special_policy, "reject") == 0) {
                failure_reason = "special_entry";
                exit_code = 2;
                break;
            }
            if (strcmp(special_policy, "materialize_empty_file") == 0 &&
                    materialize_empty_file(normalized_path, archive_entry_perm(entry)) != 0) {
                failure_reason = "special_entry_failed";
                exit_code = 2;
                break;
            }
            archive_read_data_skip(reader);
            continue;
        }

        char const *hard_link_target = archive_entry_hardlink(entry);
        if (hard_link_target != NULL) {
            char normalized_target[PATH_BUFFER_SIZE];
            if (normalize_path(hard_link_target, normalized_target, maximum_depth) != 0 ||
                    normalized_target[0] == '\0') {
                fprintf(stderr, "link_target_invalid:%s\n", hard_link_target);
                failure_reason = "link_target_invalid";
                exit_code = 2;
                break;
            }
            int copy_result = copy_regular_file(
                    normalized_target,
                    normalized_path,
                    archive_entry_perm(entry));
            if (copy_result < 0 ||
                    (copy_result == 0 && append_deferred_link(
                        &deferred_links,
                        &deferred_count,
                        &deferred_capacity,
                        normalized_path,
                        normalized_target,
                        archive_entry_perm(entry)) != 0)) {
                failure_reason = "hard_link_failed";
                exit_code = 2;
                break;
            }
            archive_read_data_skip(reader);
            continue;
        }

        if (archive_read_extract(reader, entry, extract_flags) != ARCHIVE_OK) {
            failure_reason = "extract_failed";
            exit_code = 2;
            break;
        }
    }

    if (exit_code == 0 && materialize_deferred_links(deferred_links, deferred_count) != 0) {
        failure_reason = "hard_link_unresolved";
        exit_code = 2;
    }
    free(deferred_links);
    archive_read_close(reader);
    archive_read_free(reader);

    if (exit_code == 0) {
        if (chdir("/") != 0 || rename(staging, destination) != 0) {
            report_failure("atomic_publish_failed");
            return 2;
        }
        printf("success|%" PRIu64 "|%" PRIu64 "\n", entries, total_bytes);
        return 0;
    }
    if (exit_code == 3) {
        printf("cancelled|%" PRIu64 "|%" PRIu64 "\n", entries, total_bytes);
        return 3;
    }
    report_failure(failure_reason == NULL ? "unknown" : failure_reason);
    return 2;
}
