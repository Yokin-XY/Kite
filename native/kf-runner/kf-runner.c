#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>

#ifndef KF_RUNNER_VERSION
#define KF_RUNNER_VERSION "0.1.0"
#endif

static void print_usage(const char *name) {
    fprintf(stderr,
        "usage:\n"
        "  %s --version\n"
        "  %s --shell <command>\n"
        "  %s -- <command> [args...]\n",
        name, name, name);
}

static int become_session_leader(void) {
    if (setsid() >= 0) {
        return 0;
    }
    if (errno == EPERM) {
        return 0;
    }
    fprintf(stderr, "kf_runner_setsid_error:%s\n", strerror(errno));
    return -1;
}

static void print_binding_meta(void) {
    pid_t root_pid = getpid();
    pid_t process_group_id = getpgrp();
    pid_t system_session_id = getsid(0);

    printf("__kite_root_pid:%ld\n", (long)root_pid);
    printf("__kite_process_group_id:%ld\n", (long)process_group_id);
    printf("__kite_system_session_id:%ld\n", (long)system_session_id);
    fflush(stdout);
}

static int run_shell(const char *command) {
    char *const argv[] = {
        (char *)"/bin/bash",
        (char *)"-lc",
        (char *)(command && command[0] ? command : ":"),
        NULL
    };
    execv("/bin/bash", argv);

    char *const fallback_argv[] = {
        (char *)"/bin/sh",
        (char *)"-lc",
        (char *)(command && command[0] ? command : ":"),
        NULL
    };
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

    if (become_session_leader() != 0) {
        return 126;
    }
    print_binding_meta();

    if (strcmp(argv[1], "--shell") == 0) {
        return run_shell(argc >= 3 ? argv[2] : ":");
    }

    if (strcmp(argv[1], "--") == 0) {
        if (argc < 3) {
            fprintf(stderr, "kf_runner_exec_error:missing_command\n");
            return 2;
        }
        execvp(argv[2], &argv[2]);
        fprintf(stderr, "kf_runner_exec_error:%s\n", strerror(errno));
        return 127;
    }

    execvp(argv[1], &argv[1]);
    fprintf(stderr, "kf_runner_exec_error:%s\n", strerror(errno));
    return 127;
}
