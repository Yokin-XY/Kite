package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.logging.Logger
import java.io.File

/**
 * 终端 PATH shim 安装器（快速通道整改 P2）：
 * 在 shared 控制目录写 node/python 系命令名 shim（rootfs python3 客户端），
 * 终端 PATH 前置后，交互式终端里手敲的命令经 [HostExecBridge] 走宿主车道；
 * 桥不可用或被裁决拒绝时 shim 自动回退终端内原生命令。
 */
object HostTerminalShim {
    private const val TAG = "HostTerminalShim"
    internal const val SHIM_BIN_CONTAINER_PATH = "/workspace/.kf/system/host-shim/bin"

    /** 一期覆盖 node 系与 python 系命令名；交互式（REPL）由 shim 自身直通原生。 */
    private val SHIM_COMMANDS = listOf(
        "node", "nodejs", "npm", "npx",
        "python", "python3", "python3.11", "python3.12", "python3.13",
    )

    /** shebang 用 rootfs 绝对路径解释器：经 /usr/bin/env 会按 PATH 再命中 shim 自身（递归）。 */
    internal const val SHIM_SCRIPT = """#!/usr/bin/python3
# Kite host-lane terminal shim. Forwards to the host fast lane via abstract
# unix socket; falls back to the original command in-container when the lane
# is unavailable or the request is denied (availability first).
import sys, os, json, socket, base64, threading, shutil

SOCKET = "\0kite-host-exec"
SHIM_DIR = os.path.dirname(os.path.realpath(__file__))
COMMAND = os.path.basename(sys.argv[0])
ARGV = [COMMAND] + sys.argv[1:]
PASSTHROUGH = ("TERM", "COLORTERM", "NO_COLOR", "CLICOLOR_FORCE", "FORCE_COLOR", "LANG", "TZ")


def native_path():
    parts = [p for p in os.environ.get("PATH", "").split(os.pathsep) if os.path.realpath(p) != SHIM_DIR]
    return os.pathsep.join(parts)


def run_native():
    os.environ["PATH"] = native_path()
    real = shutil.which(COMMAND)
    if not real:
        sys.stderr.write("%s: command not found\\n" % COMMAND)
        raise SystemExit(127)
    os.execv(real, ARGV)


interactive = sys.stdin.isatty() and (len(ARGV) == 1 or "-i" in ARGV)
if interactive or os.environ.get("KITE_HOST_SHIM") == "0":
    run_native()

try:
    sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    sock.connect(SOCKET)
except OSError:
    run_native()

env = {k: os.environ[k] for k in PASSTHROUGH if k in os.environ}
request = json.dumps({"argv": ARGV, "cwd": os.getcwd(), "env": env}).encode() + b"\n"
sock.sendall(request)

started = {"done": False}
lock = threading.Lock()
exit_code = [None]


def socket_reader():
    buffer = b""
    try:
        while True:
            chunk = sock.recv(65536)
            if not chunk:
                break
            buffer += chunk
            while b"\n" in buffer:
                line, buffer = buffer.split(b"\n", 1)
                frame = json.loads(line.decode("utf-8", "replace"))
                kind = frame.get("s")
                if kind in ("out", "err"):
                    data = base64.b64decode(frame.get("d", ""))
                    stream = sys.stdout.buffer if kind == "out" else sys.stderr.buffer
                    stream.write(data)
                    stream.flush()
                elif kind == "denied":
                    with lock:
                        started["done"] = True
                    return "denied"
                elif kind == "started":
                    pass
                elif kind == "exit":
                    with lock:
                        exit_code[0] = frame.get("code", -1)
                    return "exit"
    except OSError:
        pass
    finally:
        with lock:
            started["done"] = True
    return "eof"


result = {"value": None}


def reader_main():
    result["value"] = socket_reader()
    try:
        sock.shutdown(socket.SHUT_RDWR)
    except OSError:
        pass


thread = threading.Thread(target=reader_main, daemon=True)
thread.start()

import signal as _signal


def forward_sig(name):
    try:
        sock.sendall((json.dumps({"s": "sig", "sig": name}) + "\n").encode())
    except OSError:
        pass


def on_int(signum, frame):
    forward_sig("INT")


_signal.signal(_signal.SIGINT, on_int)

try:
    while thread.is_alive():
        data = sys.stdin.buffer.read1(65536)
        if not data:
            try:
                sock.shutdown(socket.SHUT_WR)
            except OSError:
                pass
            while thread.is_alive():
                thread.join(0.2)
            break
        sock.sendall((json.dumps({"s": "in", "d": base64.b64encode(data).decode()}) + "\n").encode())
except KeyboardInterrupt:
    pass
except OSError:
    pass

thread.join(2.0)
if result["value"] == "denied":
    run_native()
raise SystemExit(exit_code[0] if exit_code[0] is not None else 1)
"""

    /** 幂等安装：内容变更时重写。返回 shim bin 目录（宿主路径）。 */
    fun ensureInstalled(workspaceControlDirectory: File): File {
        val binDirectory = File(workspaceControlDirectory, "system/host-shim/bin")
        if (!binDirectory.isDirectory) binDirectory.mkdirs()
        SHIM_COMMANDS.forEach { command ->
            val script = File(binDirectory, command)
            val want = SHIM_SCRIPT
            val current = runCatching { script.readText() }.getOrNull()
            if (current != want) {
                script.writeText(want)
                script.setExecutable(true, false)
            }
        }
        // 纯 JVM 单测环境没有 android.util.Log，静默跳过。
        runCatching {
            Logger.i(TAG, "host lane shim installed: dir=${binDirectory.absolutePath} cmds=${SHIM_COMMANDS.size}")
        }
        return binDirectory
    }

    /** 终端 PATH 前置注入（容器路径语义）。 */
    fun injectIntoPath(context: Context, currentPath: String): String {
        val controlDirectory = File(
            com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
                .resolveActiveWorkspaceEnvironment(
                    com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge.ensureDefaultContainer(context)
                ).workspacePath,
            ".kf"
        )
        runCatching { ensureInstalled(controlDirectory) }
        return "$SHIM_BIN_CONTAINER_PATH:$currentPath"
    }
}
