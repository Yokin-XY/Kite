package com.kite.app.foundation.devicebridge

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.os.IBinder
import android.os.ParcelFileDescriptor
import rikka.shizuku.Shizuku
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

object ShizukuUserServiceProcessClient {
    private const val CONNECT_TIMEOUT_MS = 10_000L
    private const val USER_SERVICE_TAG = "kite-device-bridge-v1"

    private val connectionLock = ReentrantLock()
    private val connectionChanged = connectionLock.newCondition()

    @Volatile
    private var service: IKiteDeviceBridgeService? = null

    @Volatile
    private var binding = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            connectionLock.withLock {
                service = binder?.let(IKiteDeviceBridgeService.Stub::asInterface)
                binding = false
                connectionChanged.signalAll()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connectionLock.withLock {
                service = null
                binding = false
                connectionChanged.signalAll()
            }
        }
    }

    fun startShell(context: Context, command: String): RemoteProcess = startProcess(
        context = context,
        argv = arrayOf("/system/bin/sh", "-c", command),
        environment = emptyArray(),
        workingDirectory = "/"
    )

    fun startProcess(
        context: Context,
        argv: Array<String>,
        environment: Array<String>,
        workingDirectory: String
    ): RemoteProcess {
        val remote = requireService(context.applicationContext)
        check(remote.protocolVersion == DeviceBridgeContract.PROTOCOL_VERSION) {
            "Kite Device Bridge protocol mismatch: remote=${remote.protocolVersion} " +
                "local=${DeviceBridgeContract.PROTOCOL_VERSION}"
        }

        // 结束状态已有独立 status pipe；普通 pipe 可以避免可靠管道在对端正常关闭时把 EOF
        // 误报成 "Remote side is dead"，同时仍保持所有大块数据不经过 Binder。
        val stdinPipe = ParcelFileDescriptor.createPipe()
        val stdoutPipe = ParcelFileDescriptor.createPipe()
        val stderrPipe = ParcelFileDescriptor.createPipe()
        val statusPipe = ParcelFileDescriptor.createPipe()
        val requestId = UUID.randomUUID().toString()

        val accepted = try {
            remote.startProcess(
                requestId,
                argv,
                environment,
                workingDirectory,
                stdinPipe[0],
                stdoutPipe[1],
                stderrPipe[1],
                statusPipe[1]
            )
        } finally {
            closeQuietly(stdinPipe[0])
            closeQuietly(stdoutPipe[1])
            closeQuietly(stderrPipe[1])
            closeQuietly(statusPipe[1])
        }

        if (accepted != DeviceBridgeContract.EXIT_OK) {
            closeQuietly(stdinPipe[1])
            closeQuietly(stdoutPipe[0])
            closeQuietly(stderrPipe[0])
            closeQuietly(statusPipe[0])
            error("Kite Device Bridge rejected process: exit=$accepted")
        }

        return RemoteProcess(
            requestId = requestId,
            remote = remote,
            outputStream = stdinPipe[1],
            inputStream = stdoutPipe[0],
            errorStream = stderrPipe[0],
            statusStream = statusPipe[0]
        )
    }

    private fun requireService(context: Context): IKiteDeviceBridgeService = connectionLock.withLock {
        service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        service = null

        if (!binding) {
            val state = ShizukuBridgeStateOwner.current()
            check(state.lifecycle == DeviceBridgeLifecycleStatus.Ready) {
                "Shizuku backend is not ready: ${state.lifecycle}"
            }
            binding = true
            runCatching {
                Shizuku.bindUserService(userServiceArgs(context), connection)
            }.onFailure {
                binding = false
                connectionChanged.signalAll()
                throw it
            }
        }

        var remainingNanos = TimeUnit.MILLISECONDS.toNanos(CONNECT_TIMEOUT_MS)
        while (remainingNanos > 0L) {
            service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
            remainingNanos = connectionChanged.awaitNanos(remainingNanos)
        }
        error("Timed out while binding Kite Shizuku UserService")
    }

    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return Shizuku.UserServiceArgs(ComponentName(context, KiteShizukuUserService::class.java))
            .daemon(false)
            .tag(USER_SERVICE_TAG)
            .version(DeviceBridgeContract.PROTOCOL_VERSION)
            .debuggable(debuggable)
            .processNameSuffix("kite_device")
    }

    class RemoteProcess internal constructor(
        private val requestId: String,
        private val remote: IKiteDeviceBridgeService,
        val outputStream: ParcelFileDescriptor,
        val inputStream: ParcelFileDescriptor,
        val errorStream: ParcelFileDescriptor,
        statusStream: ParcelFileDescriptor
    ) {
        private val completed = CountDownLatch(1)

        @Volatile
        private var finalStatus = KiteDeviceProcessStatus.transportFailure()

        init {
            thread(start = true, isDaemon = true, name = "kite-device-status-$requestId") {
                finalStatus = runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(statusStream).bufferedReader().use { reader ->
                        KiteDeviceProcessStatus.parse(reader.readText())
                            ?: KiteDeviceProcessStatus.transportFailure()
                    }
                }.getOrDefault(KiteDeviceProcessStatus.transportFailure())
                completed.countDown()
            }
        }

        fun waitForTimeout(timeoutMs: Long): Boolean = completed.await(timeoutMs, TimeUnit.MILLISECONDS)

        fun exitValue(): Int {
            check(completed.count == 0L) { "Kite Device process is still running" }
            return finalStatus.exitCode
        }

        fun destroy() {
            runCatching { remote.cancelProcess(requestId) }
            closeQuietly(outputStream)
        }
    }

    private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
        runCatching { descriptor?.close() }
    }
}
