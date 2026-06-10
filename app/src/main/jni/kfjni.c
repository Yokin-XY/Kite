/*
 * KFJNI - KFShell 的 JNI 执行引擎
 *
 * 核心职责：
 * 1. 用 fork()+execvp()+PTY 启动容器会话
 * 2. 提供窗口大小、等待子进程、发送信号等基础能力
 * 3. 保持实现尽量薄，复杂的容器状态管理放在 Kotlin 层完成
 */

#include <dirent.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define KF_UNUSED(x) x __attribute__((__unused__))

static int throw_runtime_exception(JNIEnv* env, char const* message)
{
    jclass exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (exClass) (*env)->ThrowNew(env, exClass, message);
    return -1;
}

static int create_subprocess(JNIEnv* env,
        char const* cmd,
        char const* cwd,
        char* const argv[],
        char** envp,
        int* pProcessId,
        jint rows,
        jint columns)
{
    /* 打开 PTY 主端 */
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) return throw_runtime_exception(env, "Cannot open /dev/ptmx");

    char devname[64];
    if (grantpt(ptm) || unlockpt(ptm) || ptsname_r(ptm, devname, sizeof(devname))) {
        close(ptm);
        return throw_runtime_exception(env, "Cannot grantpt()/unlockpt()/ptsname_r() on /dev/ptmx");
    }

    /* 开启 UTF-8 并关闭软件流控，避免终端交互卡顿 */
    struct termios tios;
    tcgetattr(ptm, &tios);
    tios.c_iflag |= IUTF8;
    tios.c_iflag &= ~(IXON | IXOFF);
    tcsetattr(ptm, TCSANOW, &tios);

    /* 设置初始终端大小 */
    struct winsize sz;
    sz.ws_row = (unsigned short) rows;
    sz.ws_col = (unsigned short) columns;
    sz.ws_xpixel = (unsigned short) (columns * 8);
    sz.ws_ypixel = (unsigned short) (rows * 16);
    ioctl(ptm, TIOCSWINSZ, &sz);

    pid_t pid = fork();
    if (pid < 0) {
        close(ptm);
        return throw_runtime_exception(env, "Fork failed");
    } else if (pid > 0) {
        /* 父进程：返回 PTY 主端和子进程 PID */
        *pProcessId = (int) pid;
        return ptm;
    } else {
        /* 子进程：建立新的会话并接管伪终端 */

        /* 清理 Java 进程可能屏蔽的信号 */
        sigset_t signals_to_unblock;
        sigfillset(&signals_to_unblock);
        sigprocmask(SIG_UNBLOCK, &signals_to_unblock, 0);

        close(ptm);
        setsid();

        int pts = open(devname, O_RDWR);
        if (pts < 0) _exit(-1);

        dup2(pts, 0);
        dup2(pts, 1);
        dup2(pts, 2);

        if (pts > 2) close(pts);

        /* 关闭 stdin/stdout/stderr 之外的文件描述符 */
        DIR* self_dir = opendir("/proc/self/fd");
        if (self_dir != NULL) {
            int self_dir_fd = dirfd(self_dir);
            struct dirent* entry;
            while ((entry = readdir(self_dir)) != NULL) {
                int fd = atoi(entry->d_name);
                if (fd > 2 && fd != self_dir_fd) close(fd);
            }
            closedir(self_dir);
        }

        /* 只保留显式传入的环境变量，减少宿主环境污染 */
        clearenv();
        if (envp) {
            for (; *envp; ++envp) {
                putenv(*envp);
            }
        }

        /* 设置工作目录 */
        if (cwd && chdir(cwd) != 0) {
            char* error_message;
            if (asprintf(&error_message, "chdir(\"%s\")", cwd) == -1) error_message = "chdir()";
            perror(error_message);
            fflush(stderr);
        }

        /* 执行目标命令 */
        execvp(cmd, argv);

        /* 如果 execvp 返回，说明执行失败 */
        char* error_message;
        if (asprintf(&error_message, "exec(\"%s\")", cmd) == -1) error_message = "exec()";
        perror(error_message);
        _exit(1);
    }
}

/*
 * Class:     com_kftest_jni_KFJni
 * Method:    createProotProcess
 * Signature: (Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[III)I
 */
JNIEXPORT jint JNICALL Java_com_kftest_app_foundation_jni_KFJni_createProotProcess(
        JNIEnv* env,
        jclass KF_UNUSED(clazz),
        jstring cmd,
        jstring cwd,
        jobjectArray args,
        jobjectArray envVars,
        jintArray processIdArray,
        jint rows,
        jint columns)
{
    /* Build argv array */
    jsize size = args ? (*env)->GetArrayLength(env, args) : 0;
    char** argv = NULL;
    if (size > 0) {
        argv = (char**) malloc((size + 1) * sizeof(char*));
        if (!argv) return throw_runtime_exception(env, "Couldn't allocate argv array");
        for (int i = 0; i < size; ++i) {
            jstring arg_java_string = (jstring) (*env)->GetObjectArrayElement(env, args, i);
            char const* arg_utf8 = (*env)->GetStringUTFChars(env, arg_java_string, NULL);
            if (!arg_utf8) return throw_runtime_exception(env, "GetStringUTFChars() failed for argv");
            argv[i] = strdup(arg_utf8);
            (*env)->ReleaseStringUTFChars(env, arg_java_string, arg_utf8);
            (*env)->DeleteLocalRef(env, arg_java_string);
        }
        argv[size] = NULL;
    }

    /* Build envp array */
    size = envVars ? (*env)->GetArrayLength(env, envVars) : 0;
    char** envp = NULL;
    if (size > 0) {
        envp = (char**) malloc((size + 1) * sizeof(char *));
        if (!envp) {
            if (argv) { for (char** tmp = argv; *tmp; ++tmp) free(*tmp); free(argv); }
            return throw_runtime_exception(env, "malloc() for envp array failed");
        }
        for (int i = 0; i < size; ++i) {
            jstring env_java_string = (jstring) (*env)->GetObjectArrayElement(env, envVars, i);
            char const* env_utf8 = (*env)->GetStringUTFChars(env, env_java_string, 0);
            if (!env_utf8) return throw_runtime_exception(env, "GetStringUTFChars() failed for env");
            envp[i] = strdup(env_utf8);
            (*env)->ReleaseStringUTFChars(env, env_java_string, env_utf8);
            (*env)->DeleteLocalRef(env, env_java_string);
        }
        envp[size] = NULL;
    }

    int procId = 0;
    char const* cmd_cwd = cwd ? (*env)->GetStringUTFChars(env, cwd, NULL) : NULL;
    char const* cmd_utf8 = (*env)->GetStringUTFChars(env, cmd, NULL);
    int ptm = create_subprocess(env, cmd_utf8, cmd_cwd, argv, envp, &procId, rows, columns);
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf8);
    if (cmd_cwd) (*env)->ReleaseStringUTFChars(env, cwd, cmd_cwd);

    /* Free argv */
    if (argv) {
        for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
        free(argv);
    }
    /* Free envp */
    if (envp) {
        for (char** tmp = envp; *tmp; ++tmp) free(*tmp);
        free(envp);
    }

    /* Return process ID via int array */
    if (processIdArray) {
        int* pProcId = (int*) (*env)->GetPrimitiveArrayCritical(env, processIdArray, NULL);
        if (!pProcId) return throw_runtime_exception(env, "GetPrimitiveArrayCritical(processIdArray) failed");
        *pProcId = procId;
        (*env)->ReleasePrimitiveArrayCritical(env, processIdArray, pProcId, 0);
    }

    return ptm;
}

/*
 * Class:     com_kftest_jni_KFJni
 * Method:    setPtyWindowSize
 * Signature: (III)V
 */
JNIEXPORT void JNICALL Java_com_kftest_app_foundation_jni_KFJni_setPtyWindowSize(
        JNIEnv* KF_UNUSED(env),
        jclass KF_UNUSED(clazz),
        jint fd,
        jint rows,
        jint cols)
{
    struct winsize sz;
    sz.ws_row = (unsigned short) rows;
    sz.ws_col = (unsigned short) cols;
    sz.ws_xpixel = (unsigned short) (cols * 8);
    sz.ws_ypixel = (unsigned short) (rows * 16);
    ioctl(fd, TIOCSWINSZ, &sz);
}

/*
 * Class:     com_kftest_jni_KFJni
 * Method:    waitFor
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_kftest_app_foundation_jni_KFJni_waitFor(
        JNIEnv* KF_UNUSED(env),
        jclass KF_UNUSED(clazz),
        jint pid)
{
    int status;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return -WTERMSIG(status);
    } else {
        return 0;
    }
}

/*
 * Class:     com_kftest_jni_KFJni
 * Method:    closeFd
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_com_kftest_app_foundation_jni_KFJni_closeFd(
        JNIEnv* KF_UNUSED(env),
        jclass KF_UNUSED(clazz),
        jint fileDescriptor)
{
    close(fileDescriptor);
}

/*
 * Class:     com_kftest_jni_KFJni
 * Method:    sendSignal
 * Signature: (II)Z
 */
JNIEXPORT jboolean JNICALL Java_com_kftest_app_foundation_jni_KFJni_sendSignal(
        JNIEnv* KF_UNUSED(env),
        jclass KF_UNUSED(clazz),
        jint pid,
        jint signalValue)
{
    return kill(pid, signalValue) == 0 ? JNI_TRUE : JNI_FALSE;
}
