/* RF1320 Debug-only glibc 父进程探针。每次只运行一个固定 case。 */

#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <spawn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

extern char **environ;

static int wait_child(pid_t pid) {
    int status = 0;
    if (waitpid(pid, &status, 0) != pid) return 125;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return 126;
}

static int spawn_case(const char *file, char *const child_argv[], int use_path) {
    pid_t pid = -1;
    int result = use_path
        ? posix_spawnp(&pid, file, NULL, NULL, child_argv, environ)
        : posix_spawn(&pid, file, NULL, NULL, child_argv, environ);
    if (result != 0) {
        printf("SPAWN_RETURN=%d\n", result);
        return 0;
    }
    int exit_code = wait_child(pid);
    printf("SPAWN_RETURN=0 CHILD_EXIT=%d\n", exit_code);
    return exit_code;
}

int main(int argc, char **argv) {
    if (argc < 2) return 64;
    const char *mode = argv[1];
    if (strcmp(mode, "execve") == 0) {
        char *const child[] = {"printf", "EXECVE_OK\\n", NULL};
        execve("/usr/bin/printf", child, environ);
    } else if (strcmp(mode, "execv") == 0) {
        char *const child[] = {"printf", "EXECV_OK\\n", NULL};
        execv("/usr/bin/printf", child);
    } else if (strcmp(mode, "execvp") == 0) {
        char *const child[] = {"printf", "EXECVP_OK\\n", NULL};
        execvp("printf", child);
    } else if (strcmp(mode, "execvpe") == 0) {
        char *const child[] = {"printf", "EXECVPE_OK\\n", NULL};
        execvpe("printf", child, environ);
    } else if (strcmp(mode, "execl") == 0) {
        execl("/usr/bin/printf", "printf", "EXECL_OK\\n", (char *) NULL);
    } else if (strcmp(mode, "execlp") == 0) {
        execlp("git", "git", "--version", (char *) NULL);
    } else if (strcmp(mode, "execle") == 0) {
        execle("/usr/bin/printf", "printf", "EXECLE_OK\\n", (char *) NULL, environ);
    } else if (strcmp(mode, "spawn") == 0) {
        char *const child[] = {"printf", "SPAWN_OK\\n", NULL};
        return spawn_case("/usr/bin/printf", child, 0);
    } else if (strcmp(mode, "spawnp") == 0) {
        char *const child[] = {"printf", "SPAWNP_OK\\n", NULL};
        return spawn_case("printf", child, 1);
    } else if (strcmp(mode, "envcwd") == 0) {
        char *const child[] = {"sh", "-c", "printf 'ENV=%s CWD=%s\\n' \"$KF_CHILD_ENV\" \"$(pwd)\"", NULL};
        execve("/bin/sh", child, environ);
    } else if (strcmp(mode, "stdio") == 0) {
        char *const child[] = {"sh", "-c", "printf 'STDOUT_OK\\n'; printf 'STDERR_OK\\n' >&2", NULL};
        execve("/bin/sh", child, environ);
    } else if (strcmp(mode, "stdin") == 0) {
        char *const child[] = {"cat", NULL};
        execve("/bin/cat", child, environ);
    } else if (strcmp(mode, "exit37") == 0) {
        char *const child[] = {"sh", "-c", "exit 37", NULL};
        execve("/bin/sh", child, environ);
    } else if (strcmp(mode, "signal") == 0) {
        char *const child[] = {"sh", "-c", "kill -TERM $$", NULL};
        execve("/bin/sh", child, environ);
    } else if (strcmp(mode, "script") == 0 && argc >= 3) {
        char *const child[] = {argv[2], "SCRIPT_ARG", NULL};
        execve(argv[2], child, environ);
    } else if (strcmp(mode, "missing_exec") == 0) {
        char *const child[] = {"missing", NULL};
        execve("/kf-no-such-executable", child, environ);
        printf("SYNC_ERRNO=%d\n", errno);
        return 0;
    } else if (strcmp(mode, "missing_spawn") == 0) {
        char *const child[] = {"missing", NULL};
        return spawn_case("/kf-no-such-executable", child, 0);
    } else if (strcmp(mode, "file_actions") == 0 && argc >= 3) {
        posix_spawn_file_actions_t actions;
        if (posix_spawn_file_actions_init(&actions) != 0) return 125;
        if (posix_spawn_file_actions_addopen(&actions, STDOUT_FILENO, argv[2],
                                             O_WRONLY | O_CREAT | O_TRUNC, 0600) != 0) return 125;
        char *const child[] = {"printf", "FILE_ACTION_OK\\n", NULL};
        pid_t pid = -1;
        int result = posix_spawn(&pid, "/usr/bin/printf", &actions, NULL, child, environ);
        posix_spawn_file_actions_destroy(&actions);
        printf("SPAWN_RETURN=%d\n", result);
        return result == 0 ? wait_child(pid) : 0;
    } else if (strcmp(mode, "fork_exec") == 0) {
        pid_t pid = fork();
        if (pid < 0) return 125;
        if (pid == 0) {
            char *const child[] = {"printf", "FORK_EXEC_OK\\n", NULL};
            execve("/usr/bin/printf", child, environ);
            _exit(126);
        }
        int exit_code = wait_child(pid);
        printf("FORK_CHILD_EXIT=%d\n", exit_code);
        return exit_code;
    } else if (strcmp(mode, "system") == 0) {
        int status = system("git --version");
        int exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : 126;
        printf("SYSTEM_EXIT=%d\n", exit_code);
        return exit_code;
    } else if (strcmp(mode, "popen") == 0) {
        FILE *pipe = popen("git --version", "r");
        if (pipe == NULL) {
            printf("POPEN_ERRNO=%d\n", errno);
            return 0;
        }
        char output[128] = {0};
        size_t length = fread(output, 1U, sizeof(output) - 1U, pipe);
        int status = pclose(pipe);
        printf("POPEN_BYTES=%zu POPEN_STATUS=%d OUTPUT=%s", length, status, output);
        return status == 0 ? 0 : 1;
    } else if (strcmp(mode, "fexecve") == 0 && argc >= 3) {
        int descriptor = open(argv[2], O_RDONLY);
        if (descriptor < 0) {
            printf("OPEN_ERRNO=%d\n", errno);
            return 0;
        }
        char *const child[] = {"printf", "FEXECVE_OK\\n", NULL};
        fexecve(descriptor, child, environ);
        printf("SYNC_ERRNO=%d\n", errno);
        close(descriptor);
        return 0;
    } else if (strcmp(mode, "path_exec") == 0 && argc >= 3) {
        char *const child[] = {argv[2], NULL};
        execve(argv[2], child, environ);
        printf("SYNC_ERRNO=%d\n", errno);
        return 0;
    } else {
        return 64;
    }
    printf("UNROUTED_ERRNO=%d\n", errno);
    return 126;
}
