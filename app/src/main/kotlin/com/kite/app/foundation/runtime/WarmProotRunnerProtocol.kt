package com.kite.app.foundation.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream

internal data class WarmProotJobRequest(
    val jobId: String,
    val argv: List<String>,
    val workingDirectory: String = "/workspace",
    val environment: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 10_000L,
    val maxOutputBytesPerStream: Int = 64 * 1024,
)

internal enum class WarmProotOutputStream { STDOUT, STDERR }

internal sealed interface WarmProotRunnerFrame {
    data class Ready(val runnerPid: Long) : WarmProotRunnerFrame
    data class Started(
        val jobId: String,
        val rootPid: Long,
        val processGroupId: Long,
        val systemSessionId: Long,
    ) : WarmProotRunnerFrame

    data class Output(
        val jobId: String,
        val stream: WarmProotOutputStream,
        val bytes: ByteArray,
    ) : WarmProotRunnerFrame

    data class Exited(
        val jobId: String,
        val exitCode: Int,
        val termSignal: Int,
        val cancelled: Boolean,
        val timedOut: Boolean,
    ) : WarmProotRunnerFrame

    data class Error(val jobId: String, val code: String, val message: String) : WarmProotRunnerFrame
    data class Pong(val runnerPid: Long) : WarmProotRunnerFrame
}

internal class WarmProotRunnerProtocolException(message: String) : IllegalStateException(message)

/** Android 与温热 runner 的版本化、有界二进制帧协议。 */
internal object WarmProotRunnerProtocol {
    private val MAGIC = byteArrayOf('K'.code.toByte(), 'F'.code.toByte(), 'R'.code.toByte(), '1'.code.toByte())
    private const val VERSION = 1
    private const val HEADER_BYTES = 12
    private const val MAX_FRAME_BYTES = 256 * 1024
    private const val MAX_JOB_ID_BYTES = 96
    private const val MAX_STRING_BYTES = 16 * 1024
    private const val MAX_ARGS = 64
    private const val MAX_ENV = 32

    private const val RUN = 1
    private const val CANCEL = 2
    private const val SHUTDOWN = 3
    private const val PING = 4
    private const val READY = 100
    private const val STARTED = 101
    private const val STDOUT = 102
    private const val STDERR = 103
    private const val EXITED = 104
    private const val ERROR = 105
    private const val PONG = 106

    fun encodeRun(request: WarmProotJobRequest): ByteArray {
        validate(request)
        return frame(RUN) {
            writeString(request.jobId)
            writeString(request.workingDirectory)
            writeInt(request.timeoutMs.toInt())
            writeShort(request.argv.size)
            request.argv.forEach { argument -> writeString(argument) }
            writeShort(request.environment.size)
            request.environment.toSortedMap().forEach { (key, value) ->
                writeString(key)
                writeString(value)
            }
        }
    }

    fun encodeCancel(jobId: String): ByteArray {
        validateJobId(jobId)
        return frame(CANCEL) { writeString(jobId) }
    }

    fun encodeShutdown(): ByteArray = frame(SHUTDOWN) {}

    fun encodePing(): ByteArray = frame(PING) {}

    @Throws(EOFException::class, WarmProotRunnerProtocolException::class)
    fun readFrame(input: InputStream): WarmProotRunnerFrame {
        val header = ByteArray(HEADER_BYTES)
        DataInputStream(input).readFully(header)
        if (!header.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw WarmProotRunnerProtocolException("runner_protocol_magic_mismatch")
        }
        val version = header[4].toInt() and 0xff
        if (version != VERSION) throw WarmProotRunnerProtocolException("runner_protocol_version_$version")
        val type = header[5].toInt() and 0xff
        val length = DataInputStream(ByteArrayInputStream(header, 8, 4)).readInt()
        if (length !in 0..MAX_FRAME_BYTES) {
            throw WarmProotRunnerProtocolException("runner_protocol_frame_length_$length")
        }
        val payload = ByteArray(length)
        DataInputStream(input).readFully(payload)
        return decode(type, payload)
    }

    fun validate(request: WarmProotJobRequest) {
        validateJobId(request.jobId)
        require(request.argv.isNotEmpty() && request.argv.size <= MAX_ARGS) { "runner_argv_count_invalid" }
        request.argv.forEach { value -> requireWireString(value, "runner_argv_invalid") }
        require(isAllowedWorkingDirectory(request.workingDirectory)) { "runner_workdir_not_allowed" }
        require(request.timeoutMs in 1L..600_000L) { "runner_timeout_invalid" }
        require(request.environment.size <= MAX_ENV) { "runner_env_count_invalid" }
        request.environment.forEach { (key, value) ->
            require(isAllowedEnvironmentKey(key)) { "runner_env_key_not_allowed:$key" }
            requireWireString(value, "runner_env_value_invalid")
        }
        require(request.maxOutputBytesPerStream in 1..(4 * 1024 * 1024)) { "runner_output_limit_invalid" }
    }

    private fun decode(type: Int, payload: ByteArray): WarmProotRunnerFrame {
        val source = DataInputStream(ByteArrayInputStream(payload))
        val decoded = when (type) {
            READY -> WarmProotRunnerFrame.Ready(source.readUnsignedInt())
            STARTED -> WarmProotRunnerFrame.Started(
                jobId = source.readString(),
                rootPid = source.readUnsignedInt(),
                processGroupId = source.readUnsignedInt(),
                systemSessionId = source.readUnsignedInt(),
            )
            STDOUT, STDERR -> WarmProotRunnerFrame.Output(
                jobId = source.readString(),
                stream = if (type == STDOUT) WarmProotOutputStream.STDOUT else WarmProotOutputStream.STDERR,
                bytes = source.readBytes(),
            )
            EXITED -> {
                val jobId = source.readString()
                val exitCode = source.readInt()
                val termSignal = source.readInt()
                val flags = source.readUnsignedByte()
                WarmProotRunnerFrame.Exited(
                    jobId = jobId,
                    exitCode = exitCode,
                    termSignal = termSignal,
                    cancelled = flags and 1 != 0,
                    timedOut = flags and 2 != 0,
                )
            }
            ERROR -> WarmProotRunnerFrame.Error(source.readString(), source.readString(), source.readString())
            PONG -> WarmProotRunnerFrame.Pong(source.readUnsignedInt())
            else -> throw WarmProotRunnerProtocolException("runner_protocol_unknown_frame_$type")
        }
        if (source.available() != 0) {
            throw WarmProotRunnerProtocolException("runner_protocol_trailing_payload_$type")
        }
        return decoded
    }

    private fun frame(type: Int, payloadWriter: DataOutputStream.() -> Unit): ByteArray {
        val payloadBytes = ByteArrayOutputStream().use { payload ->
            DataOutputStream(payload).use { output -> output.payloadWriter() }
            payload.toByteArray()
        }
        require(payloadBytes.size <= MAX_FRAME_BYTES) { "runner_frame_too_large" }
        return ByteArrayOutputStream(HEADER_BYTES + payloadBytes.size).use { frame ->
            DataOutputStream(frame).use { output ->
                output.write(MAGIC)
                output.writeByte(VERSION)
                output.writeByte(type)
                output.writeShort(0)
                output.writeInt(payloadBytes.size)
                output.write(payloadBytes)
            }
            frame.toByteArray()
        }
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES && bytes.size <= 0xffff) { "runner_string_too_large" }
        writeShort(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val length = readUnsignedShort()
        if (length > MAX_STRING_BYTES || length > available()) {
            throw WarmProotRunnerProtocolException("runner_protocol_string_length_$length")
        }
        return ByteArray(length).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun DataInputStream.readUnsignedInt(): Long = readInt().toLong() and 0xffffffffL

    private fun validateJobId(jobId: String) {
        val bytes = jobId.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= MAX_JOB_ID_BYTES) { "runner_job_id_invalid" }
        require(jobId.all { character -> character.isLetterOrDigit() || character in "-_.:@" }) {
            "runner_job_id_invalid"
        }
    }

    private fun requireWireString(value: String, error: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require('\u0000' !in value && bytes.size <= MAX_STRING_BYTES) { error }
    }

    private fun isAllowedWorkingDirectory(path: String): Boolean =
        isNormalizedAbsolutePath(path) && (
            path == "/" || path == "/workspace" || path.startsWith("/workspace/") ||
                path == "/tmp" || path.startsWith("/tmp/") || path == "/root" || path.startsWith("/root/")
            )

    private fun isAllowedEnvironmentKey(key: String): Boolean = key.matches(Regex("[A-Z_][A-Z0-9_]{0,63}")) && (key in setOf(
        "LANG", "LC_ALL", "LC_CTYPE", "TERM", "COLORTERM", "TZ", "NO_COLOR", "FORCE_COLOR", "CI"
    ) || key.startsWith("KF_JOB_"))

    private fun isNormalizedAbsolutePath(path: String): Boolean {
        if (!path.startsWith('/')) return false
        if (path == "/") return true
        return path.removePrefix("/").split('/').all { component ->
            component.isNotBlank() && component != "." && component != ".."
        }
    }
}
