#!/usr/bin/env python3
"""kf-runner stdio protocol smoke/contract test for a host-compiled binary."""

import os
import struct
import subprocess
import sys
import time

MAGIC = b"KFR1"
VERSION = 1
RUN, CANCEL, SHUTDOWN = 1, 2, 3
READY, STARTED, STDOUT, STDERR, EXITED, ERROR = 100, 101, 102, 103, 104, 105


def string(value: str) -> bytes:
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def frame(frame_type: int, payload: bytes = b"") -> bytes:
    return MAGIC + bytes((VERSION, frame_type)) + b"\0\0" + struct.pack(">I", len(payload)) + payload


def run_frame(job_id: str, argv: list[str], cwd: str = "/", timeout_ms: int = 3000,
              env: dict[str, str] | None = None) -> bytes:
    payload = string(job_id) + string(cwd) + struct.pack(">IH", timeout_ms, len(argv))
    payload += b"".join(string(value) for value in argv)
    items = sorted((env or {}).items())
    payload += struct.pack(">H", len(items))
    payload += b"".join(string(key) + string(value) for key, value in items)
    return frame(RUN, payload)


def cancel_frame(job_id: str) -> bytes:
    return frame(CANCEL, string(job_id))


def read_exact(stream, length: int) -> bytes:
    result = b""
    while len(result) < length:
        chunk = stream.read(length - len(result))
        if not chunk:
            raise AssertionError("runner EOF")
        result += chunk
    return result


def read_frame(process: subprocess.Popen) -> tuple[int, bytes]:
    header = read_exact(process.stdout, 12)
    assert header[:4] == MAGIC and header[4] == VERSION
    length = struct.unpack(">I", header[8:12])[0]
    assert length <= 256 * 1024
    return header[5], read_exact(process.stdout, length)


def read_string(payload: bytes, offset: int = 0) -> tuple[str, int]:
    length = struct.unpack(">H", payload[offset:offset + 2])[0]
    start = offset + 2
    end = start + length
    return payload[start:end].decode("utf-8"), end


def expect_error(process: subprocess.Popen, expected_job_id: str, expected_code: str) -> None:
    frame_type, payload = read_frame(process)
    assert frame_type == ERROR
    job_id, offset = read_string(payload)
    code, offset = read_string(payload, offset)
    _, offset = read_string(payload, offset)
    assert offset == len(payload)
    assert job_id == expected_job_id and code == expected_code, (job_id, code)


def collect_job(process: subprocess.Popen, job_id: str) -> tuple[bytes, bytes, tuple[int, int, int]]:
    stdout = bytearray()
    stderr = bytearray()
    started = False
    while True:
        frame_type, payload = read_frame(process)
        if frame_type == STARTED:
            current, offset = read_string(payload)
            assert current == job_id and len(payload[offset:]) == 12
            started = True
        elif frame_type in (STDOUT, STDERR):
            current, offset = read_string(payload)
            assert current == job_id
            (stdout if frame_type == STDOUT else stderr).extend(payload[offset:])
        elif frame_type == EXITED:
            current, offset = read_string(payload)
            assert current == job_id and started
            exit_code, signal_number = struct.unpack(">ii", payload[offset:offset + 8])
            return bytes(stdout), bytes(stderr), (exit_code, signal_number, payload[offset + 8])
        elif frame_type == ERROR:
            current, offset = read_string(payload)
            code, offset = read_string(payload, offset)
            message, _ = read_string(payload, offset)
            raise AssertionError(f"runner error for {current}: {code}: {message}")
        else:
            raise AssertionError(f"unexpected frame {frame_type}")


def main() -> None:
    binary = sys.argv[1]
    process = subprocess.Popen([binary, "--server"], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE)
    frame_type, payload = read_frame(process)
    assert frame_type == READY and len(payload) == 4

    process.stdin.write(run_frame("normal", ["/bin/sh", "-c", "printf out; printf err >&2"]))
    process.stdin.flush()
    stdout, stderr, exited = collect_job(process, "normal")
    assert stdout == b"out" and stderr == b"err" and exited == (0, 0, 0), (stdout, stderr, exited)

    process.stdin.write(run_frame("state-one", ["/bin/sh", "-c", "printf '%s|%s' \"$PWD\" \"$KF_JOB_TOKEN\""],
                                  cwd="/tmp", env={"KF_JOB_TOKEN": "one"}))
    process.stdin.flush()
    stdout, _, exited = collect_job(process, "state-one")
    assert stdout == b"/tmp|one" and exited == (0, 0, 0)

    process.stdin.write(run_frame("state-two", ["/bin/sh", "-c", "printf '%s|%s' \"$PWD\" \"${KF_JOB_TOKEN-unset}\""],
                                  cwd="/"))
    process.stdin.flush()
    stdout, _, exited = collect_job(process, "state-two")
    assert stdout == b"/|unset" and exited == (0, 0, 0)

    # Kotlin 侧会先做同样校验；native 仍须独立守住协议边界，不能信任调用方。
    process.stdin.write(run_frame("bad-cwd", ["/bin/true"], cwd="/workspace/../root"))
    process.stdin.flush()
    expect_error(process, "bad-cwd", "invalid_request")
    process.stdin.write(run_frame("bad-env", ["/bin/true"], env={"PATH": "/tmp"}))
    process.stdin.flush()
    expect_error(process, "bad-env", "invalid_request")

    # 拒绝非法请求后，同一个 runner 必须仍可继续服务。
    process.stdin.write(run_frame("after-invalid", ["/bin/printf", "ok"]))
    process.stdin.flush()
    stdout, stderr, exited = collect_job(process, "after-invalid")
    assert stdout == b"ok" and stderr == b"" and exited == (0, 0, 0)

    process.stdin.write(run_frame("cancel", ["/bin/sh", "-c", "sleep 30"], timeout_ms=30000))
    process.stdin.flush()
    frame_type, payload = read_frame(process)
    assert frame_type == STARTED and read_string(payload)[0] == "cancel"
    process.stdin.write(cancel_frame("cancel"))
    process.stdin.flush()
    stdout, stderr, exited = collect_job_after_started(process, "cancel")
    assert exited[2] & 1 == 1 and exited[2] & 2 == 0

    process.stdin.write(run_frame("timeout", ["/bin/sh", "-c", "sleep 30"], timeout_ms=100))
    process.stdin.flush()
    _, _, exited = collect_job(process, "timeout")
    assert exited[2] & 2 == 2

    process.stdin.write(run_frame("busy-one", ["/bin/sh", "-c", "sleep 30"], timeout_ms=30000))
    process.stdin.flush()
    frame_type, payload = read_frame(process)
    assert frame_type == STARTED and read_string(payload)[0] == "busy-one"
    process.stdin.write(run_frame("busy-two", ["/bin/true"]))
    process.stdin.flush()
    frame_type, payload = read_frame(process)
    assert frame_type == ERROR
    current, offset = read_string(payload)
    code, _ = read_string(payload, offset)
    assert current == "" and code == "runner_busy"
    process.stdin.write(cancel_frame("busy-one"))
    process.stdin.flush()
    _, _, exited = collect_job_after_started(process, "busy-one")
    assert exited[2] & 1 == 1

    process.stdin.write(frame(SHUTDOWN))
    process.stdin.flush()
    process.stdin.close()
    assert process.wait(timeout=3) == 0
    diagnostics = process.stderr.read().decode("utf-8", errors="replace")
    assert not diagnostics, diagnostics
    print("kf-runner protocol tests passed")


def collect_job_after_started(process: subprocess.Popen, job_id: str):
    stdout = bytearray()
    stderr = bytearray()
    while True:
        frame_type, payload = read_frame(process)
        if frame_type in (STDOUT, STDERR):
            current, offset = read_string(payload)
            assert current == job_id
            (stdout if frame_type == STDOUT else stderr).extend(payload[offset:])
        elif frame_type == EXITED:
            current, offset = read_string(payload)
            assert current == job_id
            exit_code, signal_number = struct.unpack(">ii", payload[offset:offset + 8])
            return bytes(stdout), bytes(stderr), (exit_code, signal_number, payload[offset + 8])
        elif frame_type == ERROR:
            raise AssertionError("unexpected error after started")


if __name__ == "__main__":
    main()
