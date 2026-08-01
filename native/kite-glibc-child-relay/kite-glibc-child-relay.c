/*
 * RF1320 Debug-only glibc child relay。
 *
 * 该资产只用于固定真机矩阵，不属于正式 libkite-glibc-compat.so。它不识别
 * Git/Python/资源，只把被测 glibc 父进程的 exec/spawn 请求改写为调用方提供的
 * PRoot argv 前缀。生产化以前必须补齐同步错误、全部入口、fd/信号与并发证明。
 */

#define _GNU_SOURCE

#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <spawn.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

extern char **environ;

typedef int (*execve_fn)(const char *, char *const[], char *const[]);
typedef int (*posix_spawn_fn)(pid_t *, const char *, const posix_spawn_file_actions_t *,
                             const posix_spawnattr_t *, char *const[], char *const[]);

struct string_list {
    char **values;
    size_t count;
};

static execve_fn real_execve;
static posix_spawn_fn real_posix_spawn;

static const char *required_env(const char *name) {
    const char *value = getenv(name);
    if (value == NULL || value[0] == '\0') {
        errno = EINVAL;
        return NULL;
    }
    return value;
}

static void append_log(const char *entry) {
    const char *path = getenv("KITE_GLIBC_CHILD_RELAY_LOG");
    if (path == NULL || path[0] == '\0') return;
    int descriptor = open(path, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0600);
    if (descriptor < 0) return;
    ssize_t entry_written = write(descriptor, entry, strlen(entry));
    ssize_t newline_written = write(descriptor, "\n", 1U);
    (void) entry_written;
    (void) newline_written;
    close(descriptor);
}

static struct string_list read_nul_list(const char *path) {
    struct string_list result = {0};
    int descriptor = open(path, O_RDONLY | O_CLOEXEC);
    if (descriptor < 0) return result;
    off_t length = lseek(descriptor, 0, SEEK_END);
    if (length <= 0 || length > 1024 * 1024 || lseek(descriptor, 0, SEEK_SET) < 0) {
        close(descriptor);
        errno = EINVAL;
        return result;
    }
    char *bytes = calloc((size_t) length + 1U, 1U);
    if (bytes == NULL) {
        close(descriptor);
        return result;
    }
    ssize_t offset = 0;
    while (offset < length) {
        ssize_t read_count = read(descriptor, bytes + offset, (size_t) (length - offset));
        if (read_count <= 0) {
            free(bytes);
            close(descriptor);
            errno = EIO;
            return result;
        }
        offset += read_count;
    }
    close(descriptor);

    size_t count = 0;
    for (off_t index = 0; index < length;) {
        size_t item_length = strnlen(bytes + index, (size_t) (length - index));
        if (item_length == 0U) break;
        count++;
        index += (off_t) item_length + 1;
    }
    char **values = calloc(count + 1U, sizeof(char *));
    if (values == NULL) {
        free(bytes);
        return result;
    }
    size_t item = 0;
    for (off_t index = 0; index < length && item < count;) {
        size_t item_length = strnlen(bytes + index, (size_t) (length - index));
        if (item_length == 0U) break;
        values[item++] = strdup(bytes + index);
        index += (off_t) item_length + 1;
    }
    free(bytes);
    result.values = values;
    result.count = item;
    return result;
}

static void free_list(struct string_list list) {
    if (list.values == NULL) return;
    for (size_t index = 0; index < list.count; ++index) free(list.values[index]);
    free(list.values);
}

static size_t vector_count(char *const values[]) {
    size_t count = 0;
    if (values != NULL) while (values[count] != NULL) count++;
    return count;
}

static bool starts_with_root(const char *value, const char *root) {
    if (value == NULL || root == NULL || root[0] == '\0') return false;
    size_t length = strlen(root);
    return strncmp(value, root, length) == 0 &&
           (value[length] == '\0' || value[length] == '/');
}

static char *map_path(const char *value) {
    if (value == NULL) return NULL;
    const char *control = getenv("KITE_GLIBC_CHILD_RELAY_HOST_CONTROL");
    const char *workspace = getenv("KITE_GLIBC_CHILD_RELAY_HOST_WORKSPACE");
    const char *rootfs = getenv("KITE_GLIBC_CHILD_RELAY_HOST_ROOTFS");
    const char *prefix = NULL;
    const char *root = NULL;
    if (starts_with_root(value, control)) {
        prefix = "/workspace/.kf";
        root = control;
    } else if (starts_with_root(value, workspace)) {
        prefix = "/workspace";
        root = workspace;
    } else if (starts_with_root(value, rootfs)) {
        prefix = "";
        root = rootfs;
    }
    if (root == NULL) return strdup(value);
    const char *suffix = value + strlen(root);
    size_t size = strlen(prefix) + strlen(suffix) + 2U;
    char *mapped = calloc(size, 1U);
    if (mapped == NULL) return NULL;
    snprintf(mapped, size, "%s%s", prefix, suffix[0] == '\0' && prefix[0] == '\0' ? "/" : suffix);
    if (mapped[0] == '\0') strcpy(mapped, "/");
    return mapped;
}

static bool private_environment(const char *entry) {
    if (entry == NULL) return true;
    return strncmp(entry, "KITE_GLIBC_HOST_", 16U) == 0 ||
           strncmp(entry, "KITE_GLIBC_CHILD_RELAY_", 24U) == 0 ||
           strncmp(entry, "LD_PRELOAD=", 11U) == 0 ||
           strncmp(entry, "LD_LIBRARY_PATH=", 16U) == 0;
}

static size_t key_length(const char *entry) {
    const char *separator = entry == NULL ? NULL : strchr(entry, '=');
    return separator == NULL ? 0U : (size_t) (separator - entry);
}

static bool has_key(char *const values[], size_t count, const char *entry) {
    size_t length = key_length(entry);
    if (length == 0U) return false;
    for (size_t index = 0; index < count; ++index) {
        if (key_length(values[index]) == length && strncmp(values[index], entry, length) == 0) return true;
    }
    return false;
}

static char **merge_environment(char *const requested[]) {
    const char *base_path = required_env("KITE_GLIBC_CHILD_RELAY_ENV_FILE");
    if (base_path == NULL) return NULL;
    struct string_list base = read_nul_list(base_path);
    if (base.values == NULL) return NULL;
    size_t requested_count = vector_count(requested);
    char **merged = calloc(base.count + requested_count + 1U, sizeof(char *));
    if (merged == NULL) {
        free_list(base);
        return NULL;
    }
    size_t count = 0;
    for (size_t index = 0; index < requested_count; ++index) {
        if (!private_environment(requested[index])) merged[count++] = strdup(requested[index]);
    }
    for (size_t index = 0; index < base.count; ++index) {
        if (!has_key(merged, count, base.values[index])) merged[count++] = strdup(base.values[index]);
    }
    free_list(base);
    return merged;
}

static void free_vector(char **values) {
    if (values == NULL) return;
    for (size_t index = 0; values[index] != NULL; ++index) free(values[index]);
    free(values);
}

static char **relay_argv(const char *file, char *const argv[], char **proot_path) {
    const char *prefix_path = required_env("KITE_GLIBC_CHILD_RELAY_PREFIX_FILE");
    if (prefix_path == NULL) return NULL;
    struct string_list prefix = read_nul_list(prefix_path);
    if (prefix.count == 0U) {
        free_list(prefix);
        errno = EINVAL;
        return NULL;
    }
    size_t child_count = vector_count(argv);
    char **result = calloc(prefix.count + child_count + 2U, sizeof(char *));
    if (result == NULL) {
        free_list(prefix);
        return NULL;
    }
    size_t output = 0;
    for (size_t index = 0; index < prefix.count; ++index) result[output++] = strdup(prefix.values[index]);
    *proot_path = strdup(prefix.values[0]);
    result[output++] = map_path(file);
    for (size_t index = child_count > 0U ? 1U : 0U; index < child_count; ++index) {
        result[output++] = map_path(argv[index]);
    }
    free_list(prefix);
    return result;
}

static int route_exec(const char *entry, const char *file, char *const argv[], char *const envp[]) {
    append_log(entry);
    char *proot_path = NULL;
    char **arguments = relay_argv(file, argv, &proot_path);
    char **environment = merge_environment(envp);
    if (arguments == NULL || environment == NULL || proot_path == NULL) {
        free(proot_path);
        free_vector(arguments);
        free_vector(environment);
        errno = EINVAL;
        return -1;
    }
    int result = real_execve(proot_path, arguments, environment);
    int saved_errno = errno;
    free(proot_path);
    free_vector(arguments);
    free_vector(environment);
    errno = saved_errno;
    return result;
}

static int route_spawn(const char *entry, pid_t *pid, const char *file,
                       const posix_spawn_file_actions_t *actions, const posix_spawnattr_t *attributes,
                       char *const argv[], char *const envp[]) {
    append_log(entry);
    char *proot_path = NULL;
    char **arguments = relay_argv(file, argv, &proot_path);
    char **environment = merge_environment(envp);
    if (arguments == NULL || environment == NULL || proot_path == NULL) {
        free(proot_path);
        free_vector(arguments);
        free_vector(environment);
        return EINVAL;
    }
    int result = real_posix_spawn(pid, proot_path, actions, attributes, arguments, environment);
    free(proot_path);
    free_vector(arguments);
    free_vector(environment);
    return result;
}

__attribute__((constructor))
static void resolve_symbols(void) {
    real_execve = (execve_fn) dlsym(RTLD_NEXT, "execve");
    real_posix_spawn = (posix_spawn_fn) dlsym(RTLD_NEXT, "posix_spawn");
}

int execve(const char *file, char *const argv[], char *const envp[]) {
    if (real_execve == NULL) {
        errno = ENOSYS;
        return -1;
    }
    return route_exec("execve", file, argv, envp);
}

int execv(const char *file, char *const argv[]) {
    return route_exec("execv", file, argv, environ);
}

int execvp(const char *file, char *const argv[]) {
    return route_exec("execvp", file, argv, environ);
}

int execvpe(const char *file, char *const argv[], char *const envp[]) {
    return route_exec("execvpe", file, argv, envp);
}

static char **collect_variadic_argv(const char *first, va_list values, char *const **environment) {
    va_list counter;
    va_copy(counter, values);
    size_t count = 1U;
    while (va_arg(counter, const char *) != NULL) count++;
    if (environment != NULL) *environment = va_arg(counter, char *const *);
    va_end(counter);
    char **arguments = calloc(count + 1U, sizeof(char *));
    if (arguments == NULL) return NULL;
    arguments[0] = (char *) first;
    for (size_t index = 1U; index < count; ++index) arguments[index] = va_arg(values, char *);
    (void) va_arg(values, char *);
    return arguments;
}

int execl(const char *file, const char *first, ...) {
    va_list values;
    va_start(values, first);
    char **arguments = collect_variadic_argv(first, values, NULL);
    va_end(values);
    if (arguments == NULL) return -1;
    int result = route_exec("execl", file, arguments, environ);
    free(arguments);
    return result;
}

int execlp(const char *file, const char *first, ...) {
    va_list values;
    va_start(values, first);
    char **arguments = collect_variadic_argv(first, values, NULL);
    va_end(values);
    if (arguments == NULL) return -1;
    int result = route_exec("execlp", file, arguments, environ);
    free(arguments);
    return result;
}

int execle(const char *file, const char *first, ...) {
    va_list values;
    va_start(values, first);
    char *const *environment = NULL;
    char **arguments = collect_variadic_argv(first, values, &environment);
    va_end(values);
    if (arguments == NULL || environment == NULL) {
        free(arguments);
        errno = EINVAL;
        return -1;
    }
    int result = route_exec("execle", file, arguments, (char *const *) environment);
    free(arguments);
    return result;
}

int posix_spawn(pid_t *pid, const char *file, const posix_spawn_file_actions_t *actions,
                const posix_spawnattr_t *attributes, char *const argv[], char *const envp[]) {
    if (real_posix_spawn == NULL) return ENOSYS;
    return route_spawn("posix_spawn", pid, file, actions, attributes, argv, envp);
}

int posix_spawnp(pid_t *pid, const char *file, const posix_spawn_file_actions_t *actions,
                 const posix_spawnattr_t *attributes, char *const argv[], char *const envp[]) {
    if (real_posix_spawn == NULL) return ENOSYS;
    return route_spawn("posix_spawnp", pid, file, actions, attributes, argv, envp);
}
