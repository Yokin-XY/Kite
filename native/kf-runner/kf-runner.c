#define _GNU_SOURCE

#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#ifndef KF_RUNNER_VERSION
#define KF_RUNNER_VERSION "0.2.0"
#endif

#define KFR_PROTOCOL_VERSION 1
#define KFR_HEADER_SIZE 12
#define KFR_MAX_FRAME_BYTES (256U * 1024U)
#define KFR_MAX_JOB_ID_BYTES 96U
#define KFR_MAX_STRING_BYTES (16U * 1024U)
#define KFR_MAX_ARGS 64U
#define KFR_MAX_ENV 32U
#define KFR_OUTPUT_CHUNK_BYTES 4096U
#define KFR_CANCEL_GRACE_MS 750LL

enum kfr_frame_type {
    KFR_RUN = 1,
    KFR_CANCEL = 2,
    KFR_SHUTDOWN = 3,
    KFR_PING = 4,
    KFR_READY = 100,
    KFR_STARTED = 101,
    KFR_STDOUT = 102,
    KFR_STDERR = 103,
    KFR_EXITED = 104,
    KFR_ERROR = 105,
    KFR_PONG = 106,
};

struct kfr_frame {
    uint8_t type;
    uint8_t *payload;
    uint32_t length;
};

struct kfr_reader {
    const uint8_t *data;
    uint32_t length;
    uint32_t offset;
};

struct kfr_job_request {
    char *job_id;
    char *working_directory;
    uint32_t timeout_ms;
    uint16_t argc;
    char **argv;
    uint16_t envc;
    char **env_keys;
    char **env_values;
};

struct kfr_active_job {
    char job_id[KFR_MAX_JOB_ID_BYTES + 1U];
    pid_t pid;
    int stdout_fd;
    int stderr_fd;
    int child_exited;
    int wait_status;
    int cancelled;
    int timed_out;
    int termination_sent;
    long long deadline_ms;
    long long termination_sent_at_ms;
};

static volatile sig_atomic_t g_stop_requested = 0;

static void request_stop(int signal_number) {
    (void)signal_number;
    g_stop_requested = 1;
}

static long long monotonic_ms(void) {
    struct timespec value;
    if (clock_gettime(CLOCK_MONOTONIC, &value) != 0) {
        return 0;
    }
    return (long long)value.tv_sec * 1000LL + value.tv_nsec / 1000000LL;
}

static int read_exact(int fd, void *buffer, size_t length) {
    uint8_t *cursor = (uint8_t *)buffer;
    size_t consumed = 0U;
    while (consumed < length) {
        ssize_t count = read(fd, cursor + consumed, length - consumed);
        if (count == 0) return 0;
        if (count < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        consumed += (size_t)count;
    }
    return 1;
}

static int write_all(int fd, const void *buffer, size_t length) {
    const uint8_t *cursor = (const uint8_t *)buffer;
    size_t written = 0U;
    while (written < length) {
        ssize_t count = write(fd, cursor + written, length - written);
        if (count < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        written += (size_t)count;
    }
    return 0;
}

static int read_frame(int fd, struct kfr_frame *frame) {
    uint8_t header[KFR_HEADER_SIZE];
    int header_result = read_exact(fd, header, sizeof(header));
    if (header_result <= 0) return header_result;
    if (memcmp(header, "KFR1", 4U) != 0 || header[4] != KFR_PROTOCOL_VERSION) {
        return -2;
    }
    uint32_t payload_length_network = 0U;
    memcpy(&payload_length_network, header + 8U, sizeof(payload_length_network));
    uint32_t payload_length = ntohl(payload_length_network);
    if (payload_length > KFR_MAX_FRAME_BYTES) return -2;

    uint8_t *payload = NULL;
    if (payload_length > 0U) {
        payload = (uint8_t *)malloc(payload_length);
        if (!payload) return -1;
        int payload_result = read_exact(fd, payload, payload_length);
        if (payload_result != 1) {
            free(payload);
            return payload_result == 0 ? -2 : -1;
        }
    }
    frame->type = header[5];
    frame->payload = payload;
    frame->length = payload_length;
    return 1;
}

static int write_frame(uint8_t type, const uint8_t *payload, uint32_t length) {
    uint8_t header[KFR_HEADER_SIZE] = {'K', 'F', 'R', '1', KFR_PROTOCOL_VERSION, type, 0, 0, 0, 0, 0, 0};
    uint32_t length_network = htonl(length);
    memcpy(header + 8U, &length_network, sizeof(length_network));
    if (write_all(STDOUT_FILENO, header, sizeof(header)) != 0) return -1;
    if (length > 0U && write_all(STDOUT_FILENO, payload, length) != 0) return -1;
    return 0;
}

static uint16_t read_u16(struct kfr_reader *reader, int *ok) {
    if (!*ok || reader->offset + 2U > reader->length) {
        *ok = 0;
        return 0U;
    }
    uint16_t network_value = 0U;
    memcpy(&network_value, reader->data + reader->offset, sizeof(network_value));
    reader->offset += 2U;
    return ntohs(network_value);
}

static uint32_t read_u32(struct kfr_reader *reader, int *ok) {
    if (!*ok || reader->offset + 4U > reader->length) {
        *ok = 0;
        return 0U;
    }
    uint32_t network_value = 0U;
    memcpy(&network_value, reader->data + reader->offset, sizeof(network_value));
    reader->offset += 4U;
    return ntohl(network_value);
}

static char *read_string(struct kfr_reader *reader, uint32_t max_length, int *ok) {
    uint16_t length = read_u16(reader, ok);
    if (!*ok || length > max_length || reader->offset + length > reader->length) {
        *ok = 0;
        return NULL;
    }
    char *value = (char *)calloc((size_t)length + 1U, 1U);
    if (!value) {
        *ok = 0;
        return NULL;
    }
    if (length > 0U) memcpy(value, reader->data + reader->offset, length);
    reader->offset += length;
    if (memchr(value, '\0', length) != NULL) {
        free(value);
        *ok = 0;
        return NULL;
    }
    return value;
}

static void write_u16(uint8_t *target, uint16_t value) {
    uint16_t network_value = htons(value);
    memcpy(target, &network_value, sizeof(network_value));
}

static void write_u32(uint8_t *target, uint32_t value) {
    uint32_t network_value = htonl(value);
    memcpy(target, &network_value, sizeof(network_value));
}

static uint32_t string_field_size(const char *value) {
    return 2U + (uint32_t)strlen(value ? value : "");
}

static uint32_t append_string(uint8_t *target, uint32_t offset, const char *value) {
    const char *safe_value = value ? value : "";
    uint16_t length = (uint16_t)strlen(safe_value);
    write_u16(target + offset, length);
    if (length > 0U) memcpy(target + offset + 2U, safe_value, length);
    return offset + 2U + length;
}

static int send_ready(void) {
    uint8_t payload[4U];
    write_u32(payload, (uint32_t)getpid());
    return write_frame(KFR_READY, payload, sizeof(payload));
}

static int send_pong(void) {
    uint8_t payload[4U];
    write_u32(payload, (uint32_t)getpid());
    return write_frame(KFR_PONG, payload, sizeof(payload));
}

static int send_error(const char *job_id, const char *code, const char *message) {
    uint32_t length = string_field_size(job_id) + string_field_size(code) + string_field_size(message);
    uint8_t *payload = (uint8_t *)malloc(length);
    if (!payload) return -1;
    uint32_t offset = 0U;
    offset = append_string(payload, offset, job_id);
    offset = append_string(payload, offset, code);
    append_string(payload, offset, message);
    int result = write_frame(KFR_ERROR, payload, length);
    free(payload);
    return result;
}

static int send_started(const char *job_id, pid_t pid) {
    uint32_t length = string_field_size(job_id) + 12U;
    uint8_t *payload = (uint8_t *)malloc(length);
    if (!payload) return -1;
    uint32_t offset = append_string(payload, 0U, job_id);
    write_u32(payload + offset, (uint32_t)pid);
    write_u32(payload + offset + 4U, (uint32_t)pid);
    write_u32(payload + offset + 8U, (uint32_t)pid);
    int result = write_frame(KFR_STARTED, payload, length);
    free(payload);
    return result;
}

static int send_output(uint8_t type, const char *job_id, const uint8_t *data, uint32_t data_length) {
    uint32_t length = string_field_size(job_id) + data_length;
    uint8_t *payload = (uint8_t *)malloc(length);
    if (!payload) return -1;
    uint32_t offset = append_string(payload, 0U, job_id);
    if (data_length > 0U) memcpy(payload + offset, data, data_length);
    int result = write_frame(type, payload, length);
    free(payload);
    return result;
}

static int send_exited(const struct kfr_active_job *job) {
    int exit_code = -1;
    int term_signal = 0;
    if (WIFEXITED(job->wait_status)) exit_code = WEXITSTATUS(job->wait_status);
    if (WIFSIGNALED(job->wait_status)) term_signal = WTERMSIG(job->wait_status);
    uint32_t length = string_field_size(job->job_id) + 9U;
    uint8_t *payload = (uint8_t *)malloc(length);
    if (!payload) return -1;
    uint32_t offset = append_string(payload, 0U, job->job_id);
    write_u32(payload + offset, (uint32_t)exit_code);
    write_u32(payload + offset + 4U, (uint32_t)term_signal);
    payload[offset + 8U] = (uint8_t)((job->cancelled ? 1U : 0U) | (job->timed_out ? 2U : 0U));
    int result = write_frame(KFR_EXITED, payload, length);
    free(payload);
    return result;
}

static int is_safe_job_id(const char *job_id) {
    if (!job_id || !job_id[0] || strlen(job_id) > KFR_MAX_JOB_ID_BYTES) return 0;
    for (const unsigned char *cursor = (const unsigned char *)job_id; *cursor; ++cursor) {
        if ((*cursor >= 'a' && *cursor <= 'z') || (*cursor >= 'A' && *cursor <= 'Z') ||
            (*cursor >= '0' && *cursor <= '9') || *cursor == '-' || *cursor == '_' ||
            *cursor == '.' || *cursor == ':' || *cursor == '@') {
            continue;
        }
        return 0;
    }
    return 1;
}

static int is_allowed_working_directory(const char *path) {
    if (!path || path[0] != '/') return 0;
    if (strstr(path, "//") || strstr(path, "/../") || strstr(path, "/./") ||
        strcmp(path, "/..") == 0 || strcmp(path, "/.") == 0) return 0;
    size_t length = strlen(path);
    if (length >= 2U && strcmp(path + length - 2U, "/.") == 0) return 0;
    if (length >= 3U && strcmp(path + length - 3U, "/..") == 0) return 0;
    return strcmp(path, "/") == 0 || strcmp(path, "/workspace") == 0 ||
        strncmp(path, "/workspace/", 11U) == 0 || strcmp(path, "/tmp") == 0 ||
        strncmp(path, "/tmp/", 5U) == 0 || strcmp(path, "/root") == 0 ||
        strncmp(path, "/root/", 6U) == 0;
}

static int is_allowed_env_key(const char *key) {
    if (!key || !key[0]) return 0;
    size_t length = strlen(key);
    if (length > 64U || !((key[0] >= 'A' && key[0] <= 'Z') || key[0] == '_')) return 0;
    for (size_t index = 1U; index < length; ++index) {
        if (!((key[index] >= 'A' && key[index] <= 'Z') ||
            (key[index] >= '0' && key[index] <= '9') || key[index] == '_')) return 0;
    }
    if (strcmp(key, "LANG") == 0 || strcmp(key, "LC_ALL") == 0 || strcmp(key, "LC_CTYPE") == 0 ||
        strcmp(key, "TERM") == 0 || strcmp(key, "COLORTERM") == 0 || strcmp(key, "TZ") == 0 ||
        strcmp(key, "NO_COLOR") == 0 || strcmp(key, "FORCE_COLOR") == 0 || strcmp(key, "CI") == 0) {
        return 1;
    }
    return strncmp(key, "KF_JOB_", 7U) == 0;
}

static void free_job_request(struct kfr_job_request *request) {
    if (!request) return;
    free(request->job_id);
    free(request->working_directory);
    if (request->argv) {
        for (uint16_t index = 0U; index < request->argc; ++index) free(request->argv[index]);
        free(request->argv);
    }
    if (request->env_keys) {
        for (uint16_t index = 0U; index < request->envc; ++index) free(request->env_keys[index]);
        free(request->env_keys);
    }
    if (request->env_values) {
        for (uint16_t index = 0U; index < request->envc; ++index) free(request->env_values[index]);
        free(request->env_values);
    }
    memset(request, 0, sizeof(*request));
}

static int parse_run_request(const struct kfr_frame *frame, struct kfr_job_request *request) {
    struct kfr_reader reader = {frame->payload, frame->length, 0U};
    int ok = 1;
    request->job_id = read_string(&reader, KFR_MAX_JOB_ID_BYTES, &ok);
    request->working_directory = read_string(&reader, KFR_MAX_STRING_BYTES, &ok);
    request->timeout_ms = read_u32(&reader, &ok);
    request->argc = read_u16(&reader, &ok);
    if (!ok || request->argc == 0U || request->argc > KFR_MAX_ARGS) return -1;
    request->argv = (char **)calloc((size_t)request->argc + 1U, sizeof(char *));
    if (!request->argv) return -1;
    for (uint16_t index = 0U; index < request->argc; ++index) {
        request->argv[index] = read_string(&reader, KFR_MAX_STRING_BYTES, &ok);
    }
    request->envc = read_u16(&reader, &ok);
    if (!ok || request->envc > KFR_MAX_ENV) return -1;
    request->env_keys = (char **)calloc((size_t)request->envc, sizeof(char *));
    request->env_values = (char **)calloc((size_t)request->envc, sizeof(char *));
    if (request->envc > 0U && (!request->env_keys || !request->env_values)) return -1;
    for (uint16_t index = 0U; index < request->envc; ++index) {
        request->env_keys[index] = read_string(&reader, 64U, &ok);
        request->env_values[index] = read_string(&reader, KFR_MAX_STRING_BYTES, &ok);
    }
    if (!ok || reader.offset != reader.length || !is_safe_job_id(request->job_id) ||
        !is_allowed_working_directory(request->working_directory) ||
        request->timeout_ms < 1U || request->timeout_ms > 600000U) {
        return -1;
    }
    for (uint16_t index = 0U; index < request->envc; ++index) {
        if (!is_allowed_env_key(request->env_keys[index])) return -1;
    }
    return 0;
}

static char *parse_job_id(const struct kfr_frame *frame) {
    struct kfr_reader reader = {frame->payload, frame->length, 0U};
    int ok = 1;
    char *job_id = read_string(&reader, KFR_MAX_JOB_ID_BYTES, &ok);
    if (!ok || reader.offset != reader.length || !is_safe_job_id(job_id)) {
        free(job_id);
        return NULL;
    }
    return job_id;
}

static int set_nonblocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    return flags < 0 ? -1 : fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}

static void terminate_job_group(struct kfr_active_job *job, int signal_number) {
    if (!job || job->pid <= 0) return;
    if (kill(-job->pid, signal_number) != 0 && errno == ESRCH) {
        (void)kill(job->pid, signal_number);
    }
}

static int start_job(const struct kfr_job_request *request, struct kfr_active_job *job) {
    int stdout_pipe[2] = {-1, -1};
    int stderr_pipe[2] = {-1, -1};
    if (pipe(stdout_pipe) != 0 || pipe(stderr_pipe) != 0) {
        if (stdout_pipe[0] >= 0) close(stdout_pipe[0]);
        if (stdout_pipe[1] >= 0) close(stdout_pipe[1]);
        if (stderr_pipe[0] >= 0) close(stderr_pipe[0]);
        if (stderr_pipe[1] >= 0) close(stderr_pipe[1]);
        return -1;
    }

    pid_t pid = fork();
    if (pid < 0) {
        close(stdout_pipe[0]); close(stdout_pipe[1]);
        close(stderr_pipe[0]); close(stderr_pipe[1]);
        return -1;
    }
    if (pid == 0) {
        close(stdout_pipe[0]);
        close(stderr_pipe[0]);
        int null_fd = open("/dev/null", O_RDONLY | O_CLOEXEC);
        if (null_fd >= 0) {
            dup2(null_fd, STDIN_FILENO);
            close(null_fd);
        }
        dup2(stdout_pipe[1], STDOUT_FILENO);
        dup2(stderr_pipe[1], STDERR_FILENO);
        close(stdout_pipe[1]);
        close(stderr_pipe[1]);
        if (setsid() < 0 && errno != EPERM) _exit(126);
        if (chdir(request->working_directory) != 0) {
            dprintf(STDERR_FILENO, "kf_runner_chdir_error:%s\n", strerror(errno));
            _exit(126);
        }
        for (uint16_t index = 0U; index < request->envc; ++index) {
            if (setenv(request->env_keys[index], request->env_values[index], 1) != 0) _exit(126);
        }
        execvp(request->argv[0], request->argv);
        dprintf(STDERR_FILENO, "kf_runner_exec_error:%s\n", strerror(errno));
        _exit(127);
    }

    close(stdout_pipe[1]);
    close(stderr_pipe[1]);
    (void)set_nonblocking(stdout_pipe[0]);
    (void)set_nonblocking(stderr_pipe[0]);
    memset(job, 0, sizeof(*job));
    strncpy(job->job_id, request->job_id, sizeof(job->job_id) - 1U);
    job->pid = pid;
    job->stdout_fd = stdout_pipe[0];
    job->stderr_fd = stderr_pipe[0];
    job->deadline_ms = monotonic_ms() + request->timeout_ms;
    return send_started(job->job_id, pid);
}

static int drain_output_fd(struct kfr_active_job *job, int *fd, uint8_t frame_type) {
    uint8_t buffer[KFR_OUTPUT_CHUNK_BYTES];
    while (*fd >= 0) {
        ssize_t count = read(*fd, buffer, sizeof(buffer));
        if (count > 0) {
            if (send_output(frame_type, job->job_id, buffer, (uint32_t)count) != 0) return -1;
            continue;
        }
        if (count == 0) {
            close(*fd);
            *fd = -1;
            return 0;
        }
        if (errno == EINTR) continue;
        if (errno == EAGAIN || errno == EWOULDBLOCK) return 0;
        close(*fd);
        *fd = -1;
        return 0;
    }
    return 0;
}

static void reap_job_blocking(struct kfr_active_job *job) {
    if (!job || job->pid <= 0 || job->child_exited) return;
    terminate_job_group(job, SIGKILL);
    while (waitpid(job->pid, &job->wait_status, 0) < 0 && errno == EINTR) {}
    job->child_exited = 1;
    if (job->stdout_fd >= 0) close(job->stdout_fd);
    if (job->stderr_fd >= 0) close(job->stderr_fd);
    job->stdout_fd = -1;
    job->stderr_fd = -1;
}

static int fail_server(struct kfr_active_job *job) {
    if (job && job->pid > 0) reap_job_blocking(job);
    return 70;
}

static int handle_idle_frame(const struct kfr_frame *frame, struct kfr_active_job *job, int *shutdown_requested) {
    if (frame->type == KFR_PING) return send_pong();
    if (frame->type == KFR_SHUTDOWN) {
        *shutdown_requested = 1;
        return 0;
    }
    if (frame->type == KFR_CANCEL) {
        char *job_id = parse_job_id(frame);
        int result = job_id ? 0 : send_error("", "invalid_cancel", "invalid cancel frame");
        free(job_id);
        return result;
    }
    if (frame->type != KFR_RUN) return send_error("", "unsupported_frame", "unsupported request type");

    struct kfr_job_request request;
    memset(&request, 0, sizeof(request));
    if (parse_run_request(frame, &request) != 0) {
        const char *job_id = request.job_id && is_safe_job_id(request.job_id) ? request.job_id : "";
        int result = send_error(job_id, "invalid_request", "run request violates protocol limits");
        free_job_request(&request);
        return result;
    }
    int result = start_job(&request, job);
    if (result != 0 && job->pid <= 0) {
        result = send_error(request.job_id, "spawn_failed", strerror(errno));
    }
    free_job_request(&request);
    return result;
}

static int handle_active_control_frame(
    const struct kfr_frame *frame,
    struct kfr_active_job *job,
    int *shutdown_requested
) {
    if (frame->type == KFR_PING) return send_pong();
    if (frame->type == KFR_SHUTDOWN) {
        *shutdown_requested = 1;
        job->cancelled = 1;
    } else if (frame->type == KFR_CANCEL) {
        char *job_id = parse_job_id(frame);
        if (!job_id) return send_error("", "invalid_cancel", "invalid cancel frame");
        if (strcmp(job_id, job->job_id) != 0) {
            int result = send_error(job_id, "job_mismatch", "cancel jobId does not own active job");
            free(job_id);
            return result;
        }
        free(job_id);
        job->cancelled = 1;
    } else if (frame->type == KFR_RUN) {
        return send_error("", "runner_busy", "runner already owns an active job");
    } else {
        return send_error("", "unsupported_frame", "unsupported request type");
    }

    if (!job->termination_sent) {
        terminate_job_group(job, SIGTERM);
        job->termination_sent = 1;
        job->termination_sent_at_ms = monotonic_ms();
    }
    return 0;
}

static int run_server(void) {
    struct sigaction action;
    memset(&action, 0, sizeof(action));
    action.sa_handler = request_stop;
    sigemptyset(&action.sa_mask);
    sigaction(SIGTERM, &action, NULL);
    sigaction(SIGINT, &action, NULL);
    signal(SIGPIPE, SIG_IGN);

    if (send_ready() != 0) return 70;
    struct kfr_active_job job;
    memset(&job, 0, sizeof(job));
    job.stdout_fd = -1;
    job.stderr_fd = -1;
    int shutdown_requested = 0;

    while (!g_stop_requested && !shutdown_requested) {
        if (job.pid <= 0) {
            struct kfr_frame frame = {0, NULL, 0U};
            int read_result = read_frame(STDIN_FILENO, &frame);
            if (read_result == 0) break;
            if (read_result < 0) return fail_server(&job);
            int handle_result = handle_idle_frame(&frame, &job, &shutdown_requested);
            free(frame.payload);
            if (handle_result != 0) return fail_server(&job);
            continue;
        }

        struct pollfd descriptors[3];
        descriptors[0].fd = STDIN_FILENO;
        descriptors[0].events = POLLIN | POLLHUP;
        descriptors[1].fd = job.stdout_fd;
        descriptors[1].events = job.stdout_fd >= 0 ? POLLIN | POLLHUP : 0;
        descriptors[2].fd = job.stderr_fd;
        descriptors[2].events = job.stderr_fd >= 0 ? POLLIN | POLLHUP : 0;
        int poll_result = poll(descriptors, 3U, 50);
        if (poll_result < 0 && errno != EINTR) return fail_server(&job);

        if (descriptors[0].revents & POLLHUP) {
            g_stop_requested = 1;
        } else if (descriptors[0].revents & POLLIN) {
            struct kfr_frame frame = {0, NULL, 0U};
            int read_result = read_frame(STDIN_FILENO, &frame);
            if (read_result <= 0) {
                free(frame.payload);
                g_stop_requested = 1;
            } else {
                int handle_result = handle_active_control_frame(&frame, &job, &shutdown_requested);
                free(frame.payload);
                if (handle_result != 0) return fail_server(&job);
            }
        }

        if (job.stdout_fd >= 0 && descriptors[1].revents) {
            if (drain_output_fd(&job, &job.stdout_fd, KFR_STDOUT) != 0) return fail_server(&job);
        }
        if (job.stderr_fd >= 0 && descriptors[2].revents) {
            if (drain_output_fd(&job, &job.stderr_fd, KFR_STDERR) != 0) return fail_server(&job);
        }

        if (!job.child_exited) {
            pid_t wait_result = waitpid(job.pid, &job.wait_status, WNOHANG);
            if (wait_result == job.pid) job.child_exited = 1;
        }
        long long now_ms = monotonic_ms();
        if (!job.child_exited && !job.termination_sent && now_ms >= job.deadline_ms) {
            job.timed_out = 1;
            job.termination_sent = 1;
            job.termination_sent_at_ms = now_ms;
            terminate_job_group(&job, SIGTERM);
        }
        if (!job.child_exited && job.termination_sent &&
            now_ms - job.termination_sent_at_ms >= KFR_CANCEL_GRACE_MS) {
            terminate_job_group(&job, SIGKILL);
        }

        if (job.child_exited) {
            if (drain_output_fd(&job, &job.stdout_fd, KFR_STDOUT) != 0) return fail_server(&job);
            if (drain_output_fd(&job, &job.stderr_fd, KFR_STDERR) != 0) return fail_server(&job);
            if (job.stdout_fd < 0 && job.stderr_fd < 0) {
                if (send_exited(&job) != 0) return fail_server(&job);
                memset(&job, 0, sizeof(job));
                job.stdout_fd = -1;
                job.stderr_fd = -1;
            }
        }
    }

    if (job.pid > 0) reap_job_blocking(&job);
    return 0;
}

static void print_usage(const char *name) {
    fprintf(stderr,
        "usage:\n"
        "  %s --version\n"
        "  %s --server\n"
        "  %s --shell <command>\n"
        "  %s -- <command> [args...]\n",
        name, name, name, name);
}

static int become_session_leader(void) {
    if (setsid() >= 0 || errno == EPERM) return 0;
    fprintf(stderr, "kf_runner_setsid_error:%s\n", strerror(errno));
    return -1;
}

static void print_binding_meta(void) {
    printf("__kite_root_pid:%ld\n", (long)getpid());
    printf("__kite_process_group_id:%ld\n", (long)getpgrp());
    printf("__kite_system_session_id:%ld\n", (long)getsid(0));
    fflush(stdout);
}

static int run_shell(const char *command) {
    char *const argv[] = {(char *)"/bin/bash", (char *)"-lc", (char *)(command && command[0] ? command : ":"), NULL};
    execv("/bin/bash", argv);
    char *const fallback_argv[] = {(char *)"/bin/sh", (char *)"-lc", (char *)(command && command[0] ? command : ":"), NULL};
    execv("/bin/sh", fallback_argv);
    fprintf(stderr, "kf_runner_exec_error:%s\n", strerror(errno));
    return 127;
}

int main(int argc, char **argv) {
    const char *program_name = argc > 0 && argv[0] ? argv[0] : "kf-runner";
    if (argc <= 1) {
        print_usage(program_name);
        return 2;
    }
    if (strcmp(argv[1], "--version") == 0) {
        puts("kf-runner " KF_RUNNER_VERSION);
        return 0;
    }
    if (strcmp(argv[1], "--help") == 0 || strcmp(argv[1], "-h") == 0) {
        print_usage(program_name);
        return 0;
    }
    if (strcmp(argv[1], "--server") == 0) {
        return run_server();
    }
    if (become_session_leader() != 0) return 126;
    print_binding_meta();
    if (strcmp(argv[1], "--shell") == 0) return run_shell(argc >= 3 ? argv[2] : ":");
    if (strcmp(argv[1], "--") == 0) {
        if (argc < 3) {
            fprintf(stderr, "kf_runner_exec_error:missing_command\n");
            return 2;
        }
        execvp(argv[2], &argv[2]);
    } else {
        execvp(argv[1], &argv[1]);
    }
    fprintf(stderr, "kf_runner_exec_error:%s\n", strerror(errno));
    return 127;
}
