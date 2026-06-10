package com.kftest.app.foundation.jni

object KFJni {
    init {
        System.loadLibrary("kfjni")
    }

    /**
     * 创建一个带 PTY 的子进程，用于承载 proot/bash 交互会话。
     */
    external fun createProotProcess(
        cmd: String,
        cwd: String,
        args: Array<String>,
        envVars: Array<String>,
        processIdArray: IntArray,
        rows: Int,
        columns: Int
    ): Int

    external fun setPtyWindowSize(fd: Int, rows: Int, cols: Int)
    external fun waitFor(pid: Int): Int
    external fun sendSignal(pid: Int, signal: Int): Boolean
    external fun closeFd(fd: Int)
}
