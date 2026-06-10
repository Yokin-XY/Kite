#!/usr/bin/env python3
"""
PRoot device calibration P0 runner, container-side edition.

This script is intended to run inside KF Ubuntu/PRoot:

    python3 /workspace/.kf/kf-proot-device-calibration-p0.py

It does not need Windows paths or PowerShell. It talks to the Android control
plane through the container-visible .kf/adb-bridge request/response protocol.
Every probe step writes PLAN_DECLARED before executing, so a reboot or app death
still leaves the last intended step in JSONL.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import statistics
import sys
import time
import uuid
from pathlib import Path
from typing import Any


SCHEMA = "proot_device_calibration_p0_runner_v0"
CALIBRATION_METHOD = "device_agnostic_single_proot_standard_task_throughput_curve_v4"
DEFAULT_PROBE_MODE = "seek"
DEFAULT_START_TARGET = 1
DEFAULT_MIN_TARGET = 1
DEFAULT_MAX_TARGET = 256
DEFAULT_WORKER_DURATION_SECONDS = 30.0
DEFAULT_UNITS_PER_WORKER = 4
DEFAULT_UNIT_ITERATIONS = 20000
DEFAULT_STEP_TIMEOUT_SECONDS = 120.0
DEFAULT_POST_PEAK_CONFIRM_ROUNDS = 10
RESOURCE_FAILURE_RESULTS = {
    "FAIL_RUNTIME_HEALTH",
    "FAIL_RSS_PRESSURE",
    "FAIL_WORKER_START",
    "FAIL_WORKER_EXITED",
    "FAIL_WORKER_TIMEOUT",
    "FAIL_STANDARD_TASK",
}
MODEL_GUARD_STATES = {"BURST", "DEGRADED"}
MODEL_GUARD_STABILITY_STATES = {"PRESSURE_HOLD"}
WORKSPACE = Path("/workspace/.kf")
ENV_PATH = WORKSPACE / "runtime-pressure.env"
BRIDGE_ROOT = WORKSPACE / "adb-bridge"
PLAN_LOG = WORKSPACE / "proot-device-calibration-plan.jsonl"
SUMMARY_PATH = WORKSPACE / "proot-device-calibration-p0-summary.json"
CHECKPOINT_PATH = WORKSPACE / "proot-device-calibration-p0-checkpoint.json"
OVERLAY_PATH = WORKSPACE / "proot-device-calibration.json"
WORKER_COST_LOG = WORKSPACE / "pt-worker-cost-curve-v1.jsonl"
PKG = "com.kftest.app"
ACTIVITY = "com.kftest.app/.ui.main.MainActivity"


def now_ms() -> int:
    return int(time.time() * 1000)


def append_jsonl(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n")


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")


def read_env(path: Path = ENV_PATH) -> dict[str, str]:
    if not path.exists():
        return {}
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not raw or raw.startswith("#") or "=" not in raw:
            continue
        key, value = raw.split("=", 1)
        values[key] = value
    return values


def env_int(env: dict[str, str], key: str, default: int = 0) -> int:
    try:
        return int(env.get(key, ""))
    except ValueError:
        return default


def bridge_request(command: str, timeout_s: float = 15.0) -> dict[str, Any]:
    request_id = f"req-{now_ms()}-{os.getpid()}-{uuid.uuid4().hex[:6]}"
    requests = BRIDGE_ROOT / "requests"
    responses = BRIDGE_ROOT / "responses"
    requests.mkdir(parents=True, exist_ok=True)
    responses.mkdir(parents=True, exist_ok=True)

    request_text = "\n".join(
        [
            f"id={request_id}",
            "kind=shell",
            "command_b64=" + base64.b64encode(command.encode("utf-8")).decode("ascii"),
            "",
        ]
    )
    tmp = requests / f"{request_id}.tmp"
    req = requests / f"{request_id}.req"
    tmp.write_text(request_text, encoding="utf-8")
    tmp.rename(req)

    response = responses / f"{request_id}.resp"
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        if response.exists():
            return parse_bridge_response(response)
        time.sleep(0.2)
    return {
        "id": request_id,
        "exit_code": 124,
        "stdout": "",
        "stderr": f"timeout waiting for bridge response: {request_id}",
        "command": command,
    }


def parse_bridge_response(path: Path) -> dict[str, Any]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if "=" not in raw:
            continue
        key, value = raw.split("=", 1)
        values[key] = value
    stdout = base64.b64decode(values.get("stdout_b64", "") or b"").decode("utf-8", errors="replace")
    stderr = base64.b64decode(values.get("stderr_b64", "") or b"").decode("utf-8", errors="replace")
    return {
        "id": values.get("id", path.stem),
        "exit_code": int(values.get("exit_code", "125")),
        "stdout": stdout,
        "stderr": stderr,
    }


def start_kf(timeout_s: float) -> dict[str, Any]:
    return bridge_request(f"am start -n {ACTIVITY}", timeout_s=timeout_s)


def runtime_action(action: str, target: int, nonce_name: str, nonce: str, timeout_s: float) -> dict[str, Any]:
    command = (
        f"am start -n {ACTIVITY} "
        f"--es runtime_action {action} "
        f"--ei probe_target_live_tracees {target} "
        f"--es {nonce_name} {nonce}"
    )
    return bridge_request(command, timeout_s=timeout_s)


def bridge_exit_code(bridge: dict[str, Any]) -> int:
    try:
        return int(bridge.get("exit_code", 125))
    except (TypeError, ValueError):
        return 125


def bridge_failed(bridge: dict[str, Any]) -> bool:
    return bridge_exit_code(bridge) != 0


def bridge_failure_result(prefix: str, bridge: dict[str, Any]) -> str:
    code = bridge_exit_code(bridge)
    if code == 124:
        return f"{prefix}_TIMEOUT"
    return f"{prefix}_EXIT_{code}"


def sample_env(phase: str, step_id: str, target: int, extra: dict[str, Any] | None = None) -> dict[str, Any]:
    env = read_env()
    payload: dict[str, Any] = {
        "schema": SCHEMA,
        "runId": RUN_ID,
        "phase": phase,
        "stepId": step_id,
        "targetLiveTracees": target,
        "atMs": now_ms(),
        "evaluatedAtMs": env.get("evaluated_at_ms", ""),
        "lifecycle": env.get("background_decay_lifecycle_state", ""),
        "telemetryHealth": env.get("proot_telemetry_health_state", ""),
        "telemetryBlocker": env.get("proot_telemetry_health_blocker", ""),
        "pressureState": env.get("pressure_consumer_state", ""),
        "pressureScore": env_int(env, "pressure_consumer_proot_score"),
        "pressureStability": env.get("pressure_stability_state", env.get("runtime_pressure_stability_state", "")),
        "rssPressure": env.get("pressure_consumer_rss_pressure", ""),
        "liveTracees": env_int(env, "pressure_consumer_live_tracees"),
        "forkExecWindow": env_int(env, "pressure_consumer_fork_exec_events_in_window"),
        "eventsWindow": env_int(env, "pressure_consumer_events_in_window"),
        "budget": env.get("budget_pressure_overall_state", ""),
        "poolDecision": env.get("proot_pool_plan_resource_equation_decision", ""),
        "poolRisk": env_int(env, "proot_pool_plan_resource_equation_risk_percent"),
        "poolBottleneck": env.get("proot_pool_plan_resource_equation_bottleneck_axis", ""),
        "calibrationState": env.get("proot_device_calibration_state", ""),
        "calibrationCanRun": env.get("proot_device_calibration_can_run_now", ""),
        "calibrationBlocker": env.get("proot_device_calibration_blocker", ""),
    }
    if extra:
        payload.update(extra)
    append_jsonl(PLAN_LOG, payload)
    return payload


def declare_plan(step_id: str, action: str, target: int) -> None:
    append_jsonl(
        PLAN_LOG,
        {
            "schema": SCHEMA,
            "runId": RUN_ID,
            "phase": "PLAN_DECLARED",
            "stepId": step_id,
            "action": action,
            "targetLiveTracees": target,
            "atMs": now_ms(),
            "crashRecoveryKey": "read_last_PLAN_DECLARED_before_reboot",
        },
    )


def classify(sample: dict[str, Any]) -> str:
    budget = str(sample.get("budget", ""))
    rss_pressure = str(sample.get("rssPressure", ""))
    if budget in {"HARD_PRESSURE", "THREATENING_KF", "REPEAT_OFFENDER", "QUARANTINED"}:
        return "FAIL_RUNTIME_HEALTH"
    if rss_pressure in {"HIGH", "CRITICAL"}:
        return "FAIL_RSS_PRESSURE"
    return "PASS"


def model_guard_triggered(sample: dict[str, Any]) -> bool:
    pressure = str(sample.get("pressureState", ""))
    score = int(sample.get("pressureScore", 0) or 0)
    stability = str(sample.get("pressureStability", ""))
    return pressure in MODEL_GUARD_STATES or score >= 70 or stability in MODEL_GUARD_STABILITY_STATES


def policy_guard_triggered(sample: dict[str, Any]) -> bool:
    decision = str(sample.get("poolDecision", ""))
    return any(token in decision for token in ("STOP", "HOLD", "NO_CAPACITY"))


def is_resource_failure(sample: dict[str, Any]) -> bool:
    return str(sample.get("result", "")) in RESOURCE_FAILURE_RESULTS


def worker_start_rates(results: list[dict[str, Any]]) -> list[tuple[int, float]]:
    rates: list[tuple[int, float]] = []
    for item in results:
        if item.get("result") != "PASS":
            continue
        try:
            target = int(item.get("targetLiveTracees", 0) or 0)
            rate = float(item.get("workerStartRatePerSecond", 0.0) or 0.0)
        except (TypeError, ValueError):
            continue
        if target > 0 and rate > 0:
            rates.append((target, rate))
    return rates


def throughput_samples(results: list[dict[str, Any]]) -> list[tuple[int, float, float]]:
    samples: list[tuple[int, float, float]] = []
    for item in results:
        if item.get("result") != "PASS":
            continue
        try:
            target = int(item.get("targetLiveTracees", 0) or 0)
            throughput = float(item.get("throughputUnitsPerSecond", 0.0) or 0.0)
            avg_ms = float(item.get("avgMsPerUnit", 0.0) or 0.0)
        except (TypeError, ValueError):
            continue
        if target > 0 and throughput > 0:
            samples.append((target, throughput, avg_ms))
    return samples


def throughput_peak(samples: list[tuple[int, float, float]]) -> tuple[int, float, int]:
    if not samples:
        return 0, 0.0, -1
    best_index = 0
    best_target, best_value, _ = samples[0]
    for index, (target, throughput, _) in enumerate(samples):
        if throughput > best_value:
            best_index = index
            best_target = target
            best_value = throughput
    return best_target, best_value, best_index


def throughput_stop_reason(results: list[dict[str, Any]], post_peak_rounds: int) -> str:
    samples = throughput_samples(results)
    best_target, best_value, best_index = throughput_peak(samples)
    if best_index < 0:
        return ""
    rounds_after_peak = len(samples) - best_index - 1
    if rounds_after_peak >= post_peak_rounds:
        current_target, current_value, _ = samples[-1]
        return (
            "throughput_peak_confirmed:"
            f"bestN_{best_target},best_{best_value:.3f},"
            f"after_{rounds_after_peak},currentN_{current_target},current_{current_value:.3f}"
        )
    return ""


def pass_zone(sample: dict[str, Any]) -> str:
    if sample.get("result") != "PASS":
        return "FAILED_RUNTIME_BOUND"
    if model_guard_triggered(sample):
        return "MODEL_GUARD_EXCEEDED_STILL_RUNNING"
    if sample.get("budget") == "SOFT_PRESSURE":
        return "RUNTIME_SOFT_PRESSURE_STILL_RUNNING"
    if sample.get("budget") == "HEALTHY" and sample.get("pressureStability") == "STABLE_NOW":
        return "COMFORT_STABLE"
    if sample.get("budget") == "NEAR_BUDGET" or sample.get("pressureStability") == "WATCHING":
        return "BUSY_SAFE"
    return "PASS_OBSERVED"


def prepare(target: int, settle_s: float, timeout_s: float) -> tuple[dict[str, Any], dict[str, Any]]:
    step_id = f"tracee-{target}"
    declare_plan(step_id, "prepare_real_worker_baseline", target)
    time.sleep(min(settle_s, 1.0))
    bridge = {
        "exit_code": 0,
        "stdout": "skipped: real-worker calibration runs inside KF container",
        "stderr": "",
    }
    sample = sample_env(
        "BASELINE_AFTER_PREPARE",
        step_id,
        target,
        {
            "bridge": bridge,
            "probeBackend": "standard_task_workers",
            "baselineRequirement": "kf_env_readable_not_synthetic_live_tracee_zero",
        },
    )
    return bridge, sample


def worker_command(units_per_worker: int, unit_iterations: int) -> list[str]:
    code = (
        "import hashlib,json,os,resource,signal,sys,time\n"
        "signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(SystemExit(0)))\n"
        "units=int(sys.argv[1]); iterations=int(sys.argv[2])\n"
        "payload=(str(os.getpid()) + ':kf-standard-task').encode('utf-8') * 64\n"
        "started=time.perf_counter(); completed=0\n"
        "for unit in range(units):\n"
        "    data=payload\n"
        "    for index in range(iterations):\n"
        "        data=hashlib.sha256(data).digest()\n"
        "    completed += 1\n"
        "elapsed=time.perf_counter() - started\n"
        "usage=resource.getrusage(resource.RUSAGE_SELF)\n"
        "print(json.dumps({\n"
        "    'pid': os.getpid(),\n"
        "    'completedUnits': completed,\n"
        "    'elapsedSeconds': elapsed,\n"
        "    'maxRssKb': usage.ru_maxrss,\n"
        "    'userCpuSeconds': usage.ru_utime,\n"
        "    'systemCpuSeconds': usage.ru_stime,\n"
        "}, sort_keys=True), flush=True)\n"
    )
    return [sys.executable or "python3", "-c", code, str(units_per_worker), str(unit_iterations)]


def start_workers(
    target: int,
    units_per_worker: int,
    unit_iterations: int,
    step_id: str,
) -> tuple[list[subprocess.Popen[Any]], list[str], int]:
    workers: list[subprocess.Popen[Any]] = []
    errors: list[str] = []
    command = worker_command(units_per_worker, unit_iterations)
    started_at = time.monotonic()
    for index in range(target):
        try:
            workers.append(
                subprocess.Popen(
                    command,
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    close_fds=True,
                )
            )
        except Exception as error:  # noqa: BLE001 - log exact platform failure.
            errors.append(f"worker_{index + 1}:{type(error).__name__}:{error}")
            break
        started = index + 1
        if started == target or started % 10 == 0:
            elapsed_ms = int((time.monotonic() - started_at) * 1000)
            append_jsonl(
                PLAN_LOG,
                {
                    "schema": SCHEMA,
                    "runId": RUN_ID,
                    "phase": "WORKER_START_PROGRESS",
                    "stepId": step_id,
                    "targetLiveTracees": target,
                    "startedWorkerCount": started,
                    "workerStartElapsedMs": elapsed_ms,
                    "workerStartRatePerSecond": round(started / max(elapsed_ms / 1000.0, 0.001), 3),
                    "atMs": now_ms(),
                },
            )
    return workers, errors, int((time.monotonic() - started_at) * 1000)


def alive_workers(workers: list[subprocess.Popen[Any]]) -> int:
    return sum(1 for worker in workers if worker.poll() is None)


def collect_workers(
    workers: list[subprocess.Popen[Any]],
    step_timeout_s: float,
) -> tuple[list[dict[str, Any]], list[str], bool]:
    deadline = time.monotonic() + max(step_timeout_s, 1.0)
    while time.monotonic() < deadline and any(worker.poll() is None for worker in workers):
        time.sleep(0.05)
    timed_out = any(worker.poll() is None for worker in workers)
    if timed_out:
        for worker in workers:
            if worker.poll() is None:
                worker.kill()

    results: list[dict[str, Any]] = []
    errors: list[str] = []
    for index, worker in enumerate(workers):
        try:
            stdout, stderr = worker.communicate(timeout=1.0)
        except subprocess.TimeoutExpired:
            worker.kill()
            stdout, stderr = worker.communicate(timeout=1.0)
            errors.append(f"worker_{index + 1}:communicate_timeout")
        if worker.returncode not in (0, None):
            errors.append(f"worker_{index + 1}:exit_{worker.returncode}:{(stderr or '').strip()[:200]}")
        line = (stdout or "").strip().splitlines()[-1:] or []
        if not line:
            errors.append(f"worker_{index + 1}:missing_result")
            continue
        try:
            results.append(json.loads(line[0]))
        except json.JSONDecodeError as error:
            errors.append(f"worker_{index + 1}:bad_json:{error}")
    return results, errors, timed_out


def cleanup_workers(workers: list[subprocess.Popen[Any]]) -> None:
    for worker in workers:
        if worker.poll() is None:
            worker.terminate()
    deadline = time.time() + 2.0
    while time.time() < deadline and any(worker.poll() is None for worker in workers):
        time.sleep(0.05)
    for worker in workers:
        if worker.poll() is None:
            worker.kill()
    for worker in workers:
        try:
            worker.wait(timeout=1.0)
        except subprocess.TimeoutExpired:
            pass


def inject(
    target: int,
    settle_s: float,
    timeout_s: float,
    units_per_worker: int,
    unit_iterations: int,
    step_timeout_s: float,
) -> dict[str, Any]:
    step_id = f"tracee-{target}"
    step_started_at = time.monotonic()
    declare_plan(step_id, "run_standard_task_throughput", target)
    workers: list[subprocess.Popen[Any]] = []
    start_errors: list[str] = []
    start_elapsed_ms = 0
    start_rate = 0.0
    child_results: list[dict[str, Any]] = []
    child_errors: list[str] = []
    timed_out = False
    try:
        workers, start_errors, start_elapsed_ms = start_workers(target, units_per_worker, unit_iterations, step_id)
        start_rate = round(len(workers) / max(start_elapsed_ms / 1000.0, 0.001), 3)
        append_jsonl(
            PLAN_LOG,
            {
                "schema": SCHEMA,
                "runId": RUN_ID,
                "phase": "WORKERS_STARTED",
                "stepId": step_id,
                "targetLiveTracees": target,
                "probeBackend": "standard_task_workers",
                "unitsPerWorker": units_per_worker,
                "unitIterations": unit_iterations,
                "expectedUnits": target * units_per_worker,
                "workerStartElapsedMs": start_elapsed_ms,
                "workerStartRatePerSecond": start_rate,
                "startedWorkerCount": len(workers),
                "workerPids": [worker.pid for worker in workers],
                "workerStartErrors": start_errors,
                "atMs": now_ms(),
            },
        )
        child_results, child_errors, timed_out = collect_workers(workers, step_timeout_s)
        workload_elapsed_ms = int((time.monotonic() - step_started_at) * 1000)
        completed_units = sum(int(item.get("completedUnits", 0) or 0) for item in child_results)
        expected_units = target * units_per_worker
        throughput = round(completed_units / max(workload_elapsed_ms / 1000.0, 0.001), 3)
        avg_ms = round(workload_elapsed_ms / max(completed_units, 1), 3)
        append_jsonl(
            PLAN_LOG,
            {
                "schema": SCHEMA,
                "runId": RUN_ID,
                "phase": "STANDARD_TASK_RESULT",
                "stepId": step_id,
                "targetLiveTracees": target,
                "completedUnits": completed_units,
                "expectedUnits": expected_units,
                "workloadElapsedMs": workload_elapsed_ms,
                "throughputUnitsPerSecond": throughput,
                "avgMsPerUnit": avg_ms,
                "childErrors": child_errors,
                "timedOut": timed_out,
                "atMs": now_ms(),
            },
        )
        if settle_s > 0:
            time.sleep(min(settle_s, 1.0))
        alive = alive_workers(workers)
        sample = sample_env(
            "SAMPLE_AFTER_WORKERS",
            step_id,
            target,
            {
                "probeBackend": "standard_task_workers",
                "unitsPerWorker": units_per_worker,
                "unitIterations": unit_iterations,
                "expectedUnits": expected_units,
                "completedUnits": completed_units,
                "workloadElapsedMs": workload_elapsed_ms,
                "throughputUnitsPerSecond": throughput,
                "avgMsPerUnit": avg_ms,
                "workerStartElapsedMs": start_elapsed_ms,
                "workerStartRatePerSecond": start_rate,
                "stepElapsedMs": int((time.monotonic() - step_started_at) * 1000),
                "startedWorkerCount": len(workers),
                "aliveWorkerCount": alive,
                "workerPids": [worker.pid for worker in workers],
                "workerStartErrors": start_errors,
                "childErrors": child_errors,
                "childResults": child_results,
                "timedOut": timed_out,
            },
        )
        if start_errors or len(workers) < target:
            sample["result"] = "FAIL_WORKER_START"
            sample["calibrationZone"] = "FAILED_RUNTIME_BOUND"
        elif timed_out:
            sample["result"] = "FAIL_WORKER_TIMEOUT"
            sample["calibrationZone"] = "FAILED_RUNTIME_BOUND"
        elif child_errors or completed_units < expected_units:
            sample["result"] = "FAIL_STANDARD_TASK"
            sample["calibrationZone"] = "FAILED_RUNTIME_BOUND"
        else:
            telemetry_blocker = str(sample.get("telemetryBlocker", ""))
            sample["telemetryWarning"] = telemetry_blocker not in {"", "none"}
            sample["telemetryWarningIgnoredForWorkerLimit"] = bool(sample["telemetryWarning"])
            sample["policyGuardTriggered"] = policy_guard_triggered(sample)
            sample["policyGuardIgnoredForCalibration"] = bool(sample["policyGuardTriggered"])
            sample["modelGuardTriggered"] = model_guard_triggered(sample)
            sample["modelGuardIgnoredForRuntimeLimit"] = bool(sample["modelGuardTriggered"])
            sample["result"] = classify(sample)
            sample["calibrationZone"] = pass_zone(sample)
    finally:
        cleanup_workers(workers)
        append_jsonl(
            PLAN_LOG,
            {
                "schema": SCHEMA,
                "runId": RUN_ID,
                "phase": "WORKERS_CLEANED",
                "stepId": step_id,
                "targetLiveTracees": target,
                "remainingAliveWorkerCount": alive_workers(workers),
                "atMs": now_ms(),
            },
        )
    if "sample" not in locals():
        sample = sample_env(
            "SAMPLE_AFTER_WORKERS",
            step_id,
            target,
            {
                "probeBackend": "standard_task_workers",
                "unitsPerWorker": units_per_worker,
                "unitIterations": unit_iterations,
                "expectedUnits": target * units_per_worker,
                "completedUnits": sum(int(item.get("completedUnits", 0) or 0) for item in child_results),
                "workerStartElapsedMs": start_elapsed_ms,
                "workerStartRatePerSecond": start_rate,
                "stepElapsedMs": int((time.monotonic() - step_started_at) * 1000),
                "startedWorkerCount": len(workers),
                "aliveWorkerCount": alive_workers(workers),
                "workerStartErrors": start_errors or ["worker_start_unknown_failure"],
                "childErrors": child_errors,
                "timedOut": timed_out,
                "result": "FAIL_WORKER_TIMEOUT" if timed_out else "FAIL_WORKER_START",
                "calibrationZone": "FAILED_RUNTIME_BOUND",
            },
        )
    if "policyGuardTriggered" not in sample:
        sample["policyGuardTriggered"] = policy_guard_triggered(sample)
        sample["policyGuardIgnoredForCalibration"] = bool(sample["policyGuardTriggered"])
        sample["modelGuardTriggered"] = model_guard_triggered(sample)
        sample["modelGuardIgnoredForRuntimeLimit"] = bool(sample["modelGuardTriggered"])
    append_jsonl(PLAN_LOG, {**sample, "phase": "STEP_RESULT"})
    return sample


def baseline_clean(sample: dict[str, Any]) -> bool:
    return (
        sample.get("telemetryBlocker") == "none"
        and int(sample.get("liveTracees", -1)) == 0
        and int(sample.get("pressureScore", -1)) == 0
    )


def derive_memory_worker_rss_kb() -> tuple[int, str]:
    if not WORKER_COST_LOG.exists():
        return 98_304, "safe_default_no_worker_cost_log"
    values: list[int] = []
    for raw in WORKER_COST_LOG.read_text(encoding="utf-8", errors="replace").splitlines():
        try:
            item = json.loads(raw)
        except json.JSONDecodeError:
            continue
        if item.get("mode") != "mem" or item.get("phase") != "RESULT":
            continue
        peaks = item.get("workerResourcePeaks") or {}
        peak = int(peaks.get("peakWorkerRssKb") or 0)
        workers = int(item.get("workers") or 0)
        if peak > 0 and workers > 0:
            values.append(round(peak / workers))
        for child in item.get("childResults") or []:
            rss = int((child.get("selfUsageFinal") or {}).get("maxRssKb") or 0)
            if rss > 0:
                values.append(rss)
    if not values:
        return 98_304, "safe_default_no_mem_result"
    return int(statistics.median(values)), "pt_worker_cost_curve_v1_median"


def build_summary(sequence: list[int], results: list[dict[str, Any]]) -> dict[str, Any]:
    passes = [int(x["targetLiveTracees"]) for x in results if x.get("result") == "PASS"]
    fails = [int(x["targetLiveTracees"]) for x in results if is_resource_failure(x)]
    infra_blocks = [x for x in results if x.get("result") and x.get("result") != "PASS" and not is_resource_failure(x)]
    last_pass = max(passes) if passes else 0
    first_fail = min(fails) if fails else 0
    healthy_stable_passes = [
        int(x["targetLiveTracees"]) for x in results
        if x.get("result") == "PASS"
        and x.get("budget") == "HEALTHY"
        and x.get("pressureStability") == "STABLE_NOW"
    ]
    model_guard_targets = [
        int(x["targetLiveTracees"]) for x in results
        if model_guard_triggered(x)
    ]
    busy_budget_targets = [
        int(x["targetLiveTracees"]) for x in results
        if x.get("budget") in {"NEAR_BUDGET", "SOFT_PRESSURE", "HARD_PRESSURE", "THREATENING_KF"}
        or x.get("pressureStability") in {"WATCHING", "PRESSURE_HOLD"}
    ]
    healthy_stable_cap = max(healthy_stable_passes) if healthy_stable_passes else max(1, min(last_pass, 8))
    budget_knee = min(busy_budget_targets) if busy_budget_targets else 0
    model_guard_knee = min(model_guard_targets) if model_guard_targets else 0
    practical_stop_items = [x for x in results if x.get("practicalStop")]
    practical_stop = bool(practical_stop_items)
    practical_stop_reason = str(practical_stop_items[-1].get("practicalStopReason", "")) if practical_stop else ""
    rates = worker_start_rates(results)
    rate_values = [rate for _, rate in rates]
    efficiency_reference_rate = statistics.median(rate_values[:min(5, len(rate_values))]) if rate_values else 0.0
    current_efficiency_rate = rate_values[-1] if rate_values else 0.0
    throughput_curve = throughput_samples(results)
    best_throughput_tracees, best_throughput, best_throughput_index = throughput_peak(throughput_curve)
    best_avg_ms = throughput_curve[best_throughput_index][2] if best_throughput_index >= 0 else 0.0
    upper_bound_measured = first_fail > 0
    reached_configured_max = bool(sequence) and last_pass >= max(sequence)
    calibration_complete = bool(last_pass and not infra_blocks and (upper_bound_measured or reached_configured_max or practical_stop))
    calibration_blocked = bool(infra_blocks)
    runtime_hard_cap = (first_fail - 1) if upper_bound_measured else last_pass
    measured_max = best_throughput_tracees or runtime_hard_cap
    memory_kb, memory_source = derive_memory_worker_rss_kb()
    single_proot_peak = measured_max
    single_proot_overflow_percent = 25
    overflow_percent_base = "single_proot_peak_multiplier"
    if single_proot_peak:
        overflow_headroom = max(0, ((single_proot_peak * single_proot_overflow_percent) + 99) // 100)
        queue_until = single_proot_peak + overflow_headroom
    else:
        queue_until = 1
        overflow_headroom = 0
    second_proot_trigger = queue_until + 1
    soft = queue_until
    hard = runtime_hard_cap or single_proot_peak
    return {
        "schema": "proot_device_calibration_p0_summary_v0",
        "runId": RUN_ID,
        "calibrationMethod": CALIBRATION_METHOD,
        "calibrationGoal": "measure_single_proot_standard_task_throughput_curve_and_peak",
        "probeBackend": "standard_task_workers",
        "calibrationComplete": calibration_complete,
        "calibrationBlocked": calibration_blocked,
        "infraBlockedCount": len(infra_blocks),
        "traceeSequence": sequence,
        "planLog": str(PLAN_LOG),
        "resultCount": len(results),
        "classificationRule": "spawn_real_workers_ignore_stale_telemetry_policy_guard_and_model_pressure_score_measure_runtime_health_loss",
        "policyGuardedPassCount": sum(
            1 for item in results
            if item.get("result") == "PASS" and item.get("policyGuardTriggered")
        ),
        "modelGuardedPassCount": sum(
            1 for item in results
            if item.get("result") == "PASS" and item.get("modelGuardTriggered")
        ),
        "staleTelemetryIgnoredPassCount": sum(
            1 for item in results
            if item.get("result") == "PASS" and item.get("telemetryWarning")
        ),
        "healthyStableTraceeCap": healthy_stable_cap,
        "budgetKneeTracees": budget_knee,
        "budgetKneeUsedForCapacity": False,
        "budgetKneePolicy": "advisory_budget_observation_not_capacity_trigger",
        "modelGuardKneeTracees": model_guard_knee,
        "safeTestedMaxTracees": last_pass,
        "runtimeHardCapTracees": runtime_hard_cap,
        "throughputBestTracees": best_throughput_tracees,
        "throughputBestUnitsPerSecond": round(best_throughput, 3),
        "throughputBestAvgMsPerUnit": round(best_avg_ms, 3),
        "throughputCurve": [
            {
                "targetLiveTracees": target,
                "throughputUnitsPerSecond": round(throughput, 3),
                "avgMsPerUnit": round(avg_ms, 3),
            }
            for target, throughput, avg_ms in throughput_curve
        ],
        "upperBoundMeasured": upper_bound_measured,
        "reachedConfiguredMax": reached_configured_max,
        "measuredLimitKind": (
            "exact_first_runtime_failure" if upper_bound_measured
            else "throughput_peak_after_decline_confirmed" if practical_stop
            else "tested_lower_bound_reached_configured_max"
        ),
        "practicalStop": practical_stop,
        "practicalStopReason": practical_stop_reason,
        "workerStartEfficiency": {
            "referenceRatePerSecond": round(efficiency_reference_rate, 3),
            "lastRatePerSecond": round(current_efficiency_rate, 3),
            "samples": [
                {"targetLiveTracees": target, "workerStartRatePerSecond": round(rate, 3)}
                for target, rate in rates
            ],
        },
        "needsExtendedUpperBoundProbe": bool(last_pass and not upper_bound_measured and not reached_configured_max and not practical_stop),
        "measuredMaxTracees": measured_max,
        "lastPassTracees": last_pass,
        "firstFailTracees": first_fail,
        "singleProotPeakTracees": single_proot_peak,
        "singleProotPreferredCap": single_proot_peak,
        "singleProotLimitTracees": queue_until,
        "queueUntilTracees": queue_until,
        "overflowHeadroomTracees": overflow_headroom,
        "secondProotTriggerTracees": second_proot_trigger,
        "singleProotOverflowPercent": single_proot_overflow_percent,
        "queueHeadroomPercent": single_proot_overflow_percent,
        "secondProotTriggerHeadroomPercent": single_proot_overflow_percent,
        "overflowPercentBase": overflow_percent_base,
        "queueStrategyPercentBase": overflow_percent_base,
        "queueStrategy": {
            "mode": "single_proot_peak_multiplier_then_second_proot",
            "rule": "fill_one_resident_proot_until_peak_times_multiplier_then_start_second_proot",
            "percentBase": overflow_percent_base,
            "singleProotOverflowPercent": single_proot_overflow_percent,
            "queueHeadroomPercent": single_proot_overflow_percent,
            "secondProotTriggerHeadroomPercent": single_proot_overflow_percent,
            "percentProfileBandsUsed": False,
        },
        "memoryCostSource": memory_source,
        "policyBands": {
            "comfortStable": {
                "maxTracees": healthy_stable_cap,
                "meaning": "healthy_budget_and_stable_pressure",
            },
            "modelGuardExceeded": {
                "fromTracees": model_guard_knee,
                "meaning": "internal_pressure_score_guard_exceeded_but_not_a_runtime_failure",
            },
            "busySafe": {
                "fromTracees": budget_knee,
                "toTracees": last_pass if last_pass else 0,
                "meaning": "near_budget_or_watching_but_not_failed",
            },
            "failedUpperBound": {
                "firstFailTracees": first_fail,
                "meaning": "first_observed_runtime_health_failure",
            },
        },
        "recommendedOverlay": {
            "schema": "proot_device_calibration_v0",
            "valid": calibration_complete,
            "status": "complete_ready_to_review_apply" if calibration_complete else "partial_curve_only_not_final_overlay",
            "source": "container_p0_runner_via_android_control_plane_bridge",
            "calibrationMethod": CALIBRATION_METHOD,
            "upperBoundMeasured": upper_bound_measured,
            "practicalStop": practical_stop,
            "practicalStopReason": practical_stop_reason,
            "healthyStableTraceeCap": healthy_stable_cap,
            "budgetKneeTracees": budget_knee,
            "budgetKneeUsedForCapacity": False,
            "budgetKneePolicy": "advisory_budget_observation_not_capacity_trigger",
            "modelGuardKneeTracees": model_guard_knee,
            "safeTestedMaxTracees": last_pass,
            "runtimeHardCapTracees": runtime_hard_cap,
            "throughputBestTracees": best_throughput_tracees,
            "throughputBestUnitsPerSecond": round(best_throughput, 3),
            "throughputBestAvgMsPerUnit": round(best_avg_ms, 3),
            "measuredMaxTracees": measured_max,
            "traceeMaxCap": measured_max,
            "traceeSoftCap": soft,
            "traceeHardCap": hard,
            "singleProotPeakTracees": single_proot_peak,
            "singleProotPreferredCap": single_proot_peak,
            "singleProotLimitTracees": queue_until,
            "queueUntilTracees": queue_until,
            "overflowHeadroomTracees": overflow_headroom,
            "secondProotTriggerTracees": second_proot_trigger,
            "singleProotOverflowPercent": single_proot_overflow_percent,
            "queueHeadroomPercent": single_proot_overflow_percent,
            "secondProotTriggerHeadroomPercent": single_proot_overflow_percent,
            "overflowPercentBase": overflow_percent_base,
            "queueStrategyPercentBase": overflow_percent_base,
            "memoryWorkerRssKb": memory_kb,
            "queueStrategy": {
                "mode": "single_proot_peak_multiplier_then_second_proot",
                "throughputPeakTracees": single_proot_peak,
                "singleProotPreferredCap": single_proot_peak,
                "singleProotLimitTracees": queue_until,
                "queueUntilTracees": queue_until,
                "overflowHeadroomTracees": overflow_headroom,
                "secondProotTriggerTracees": second_proot_trigger,
                "percentBase": overflow_percent_base,
                "singleProotOverflowPercent": single_proot_overflow_percent,
                "queueHeadroomPercent": single_proot_overflow_percent,
                "secondProotTriggerHeadroomPercent": single_proot_overflow_percent,
                "rule": "fill_one_resident_proot_until_peak_times_multiplier_then_start_second_proot",
                "percentProfileBandsUsed": False,
            },
            "profileLimitPolicy": "deprecated_profile_bands_replaced_by_single_peak_multiplier_v1",
            "runtimeRule": "measured_throughput_peak_times_single_multiplier_drives_second_proot_no_low_balanced_high_bands",
            "modelGuardRule": "pressure_score_70_is_recorded_as_model_guard_not_runtime_failure",
        },
        "results": results,
    }


def parse_sequence(raw: str) -> list[int]:
    out: list[int] = []
    for part in raw.split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            start_raw, end_raw = part.split("-", 1)
            start = max(1, int(start_raw.strip()))
            end = max(start, int(end_raw.strip()))
            out.extend(range(start, end + 1))
            continue
        out.append(max(1, int(part)))
    return sorted(dict.fromkeys(out))


def write_checkpoint(
    sequence: list[int],
    results: list[dict[str, Any]],
    next_target: int | None,
    probe_mode: str,
    complete: bool,
) -> None:
    summary = build_summary(sequence, results)
    checkpoint = {
        "schema": "proot_device_calibration_p0_checkpoint_v0",
        "runId": RUN_ID,
        "atMs": now_ms(),
        "probeMode": probe_mode,
        "complete": complete,
        "nextTargetLiveTracees": next_target,
        "lastPassTracees": summary.get("lastPassTracees", 0),
        "firstFailTracees": summary.get("firstFailTracees", 0),
        "measuredMaxTracees": summary.get("measuredMaxTracees", 0),
        "calibrationComplete": summary.get("calibrationComplete", False),
        "calibrationBlocked": summary.get("calibrationBlocked", False),
        "recommendedOverlay": summary.get("recommendedOverlay", {}),
    }
    overlay = summary.get("recommendedOverlay") or {}
    if isinstance(overlay, dict) and overlay.get("valid"):
        write_json(
            OVERLAY_PATH,
            {
                **overlay,
                "appliedAtMs": now_ms(),
                "appliedBy": "proot_device_calibration_p0_checkpoint",
                "sourceSummary": str(SUMMARY_PATH),
                "sourceCheckpoint": str(CHECKPOINT_PATH),
            },
        )
        checkpoint["checkpointOverlayWritten"] = True
        checkpoint["overlayPath"] = str(OVERLAY_PATH)
    write_json(CHECKPOINT_PATH, checkpoint)
    append_jsonl(PLAN_LOG, {**checkpoint, "phase": "CHECKPOINT"})


def maybe_write_overlay(summary: dict[str, Any]) -> dict[str, Any]:
    overlay = summary.get("recommendedOverlay") or {}
    if not isinstance(overlay, dict) or not overlay.get("valid"):
        return {
            "overlayWriteStatus": "skipped_overlay_not_valid",
            "overlayPath": str(OVERLAY_PATH),
        }
    payload = {
        **overlay,
        "appliedAtMs": now_ms(),
        "appliedBy": "proot_device_calibration_p0_runner",
        "sourceSummary": str(SUMMARY_PATH),
    }
    write_json(OVERLAY_PATH, payload)
    append_jsonl(PLAN_LOG, {
        "schema": SCHEMA,
        "runId": RUN_ID,
        "phase": "OVERLAY_WRITTEN",
        "atMs": now_ms(),
        "overlayPath": str(OVERLAY_PATH),
        "traceeMaxCap": payload.get("traceeMaxCap", 0),
        "traceeSoftCap": payload.get("traceeSoftCap", 0),
        "traceeHardCap": payload.get("traceeHardCap", 0),
    })
    return {
        "overlayWriteStatus": "written",
        "overlayPath": str(OVERLAY_PATH),
    }


def run_target(
    target: int,
    settle_s: float,
    timeout_s: float,
    units_per_worker: int,
    unit_iterations: int,
    step_timeout_s: float,
) -> dict[str, Any]:
    bridge, base = prepare(target, settle_s, timeout_s)
    if bridge_failed(bridge):
        base["result"] = bridge_failure_result("FAIL_BRIDGE_PREPARE", bridge)
        append_jsonl(PLAN_LOG, {**base, "phase": "STEP_RESULT"})
        return base
    return inject(target, settle_s, timeout_s, units_per_worker, unit_iterations, step_timeout_s)


def run_sequence_mode(
    sequence: list[int],
    settle_s: float,
    timeout_s: float,
    units_per_worker: int,
    unit_iterations: int,
    step_timeout_s: float,
) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for target in sequence:
        sample = run_target(target, settle_s, timeout_s, units_per_worker, unit_iterations, step_timeout_s)
        results.append(sample)
        write_checkpoint(sequence, results, None, "sequence", complete=False)
        if sample.get("result") != "PASS":
            break
    return results


def run_seek_mode(
    start: int,
    min_target: int,
    max_target: int,
    settle_s: float,
    timeout_s: float,
    units_per_worker: int,
    unit_iterations: int,
    step_timeout_s: float,
    post_peak_confirm_rounds: int,
) -> tuple[list[int], list[dict[str, Any]]]:
    sequence: list[int] = []
    results: list[dict[str, Any]] = []
    tested: set[int] = set()

    target = max(min_target, min(start, max_target))
    first = run_target(target, settle_s, timeout_s, units_per_worker, unit_iterations, step_timeout_s)
    sequence.append(target)
    tested.add(target)
    results.append(first)

    if first.get("result") == "PASS":
        next_target = target + 1
        write_checkpoint(sequence, results, next_target if next_target <= max_target else None, "seek_max_up", complete=False)
        while next_target <= max_target:
            sample = run_target(next_target, settle_s, timeout_s, units_per_worker, unit_iterations, step_timeout_s)
            sequence.append(next_target)
            tested.add(next_target)
            results.append(sample)
            if sample.get("result") != "PASS":
                write_checkpoint(sequence, results, None, "seek_max_up", complete=True)
                return sequence, results
            reason = throughput_stop_reason(results, post_peak_confirm_rounds)
            if reason:
                sample["practicalStop"] = True
                sample["practicalStopReason"] = reason
                append_jsonl(
                    PLAN_LOG,
                    {
                        "schema": SCHEMA,
                        "runId": RUN_ID,
                        "phase": "THROUGHPUT_PEAK_CONFIRMED",
                        "stepId": sample.get("stepId", f"tracee-{next_target}"),
                        "targetLiveTracees": next_target,
                        "reason": reason,
                        "throughputUnitsPerSecond": sample.get("throughputUnitsPerSecond", 0),
                        "avgMsPerUnit": sample.get("avgMsPerUnit", 0),
                        "atMs": now_ms(),
                    },
                )
                write_checkpoint(sequence, results, None, "seek_throughput_peak_confirmed", complete=True)
                return sequence, results
            next_target += 1
            write_checkpoint(sequence, results, next_target if next_target <= max_target else None, "seek_max_up", complete=False)
        write_checkpoint(sequence, results, None, "seek_max_up", complete=False)
        return sequence, results

    if is_resource_failure(first):
        next_target = target - 1
        write_checkpoint(sequence, results, next_target if next_target >= min_target else None, "seek_max_down", complete=False)
        while next_target >= min_target:
            sample = run_target(next_target, settle_s, timeout_s, units_per_worker, unit_iterations, step_timeout_s)
            sequence.append(next_target)
            tested.add(next_target)
            results.append(sample)
            if sample.get("result") == "PASS":
                write_checkpoint(sequence, results, None, "seek_max_down", complete=True)
                return sequence, results
            if not is_resource_failure(sample):
                write_checkpoint(sequence, results, None, "seek_max_down", complete=False)
                return sequence, results
            next_target -= 1
            write_checkpoint(sequence, results, next_target if next_target >= min_target else None, "seek_max_down", complete=False)
    else:
        write_checkpoint(sequence, results, None, "seek_max_blocked", complete=False)

    return sequence, results


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["seek", "sequence"], default=DEFAULT_PROBE_MODE)
    parser.add_argument("--sequence", default="")
    parser.add_argument("--start-target", type=int, default=DEFAULT_START_TARGET)
    parser.add_argument("--min-target", type=int, default=DEFAULT_MIN_TARGET)
    parser.add_argument("--max-target", type=int, default=DEFAULT_MAX_TARGET)
    parser.add_argument("--settle", type=float, default=4.0)
    parser.add_argument("--worker-duration", type=float, default=DEFAULT_WORKER_DURATION_SECONDS)
    parser.add_argument("--units-per-worker", type=int, default=DEFAULT_UNITS_PER_WORKER)
    parser.add_argument("--unit-iterations", type=int, default=DEFAULT_UNIT_ITERATIONS)
    parser.add_argument("--step-timeout", type=float, default=DEFAULT_STEP_TIMEOUT_SECONDS)
    parser.add_argument("--bridge-timeout", type=float, default=20.0)
    parser.add_argument("--post-peak-confirm-rounds", type=int, default=DEFAULT_POST_PEAK_CONFIRM_ROUNDS)
    parser.add_argument("--summary", default=str(SUMMARY_PATH))
    args = parser.parse_args()

    PLAN_LOG.parent.mkdir(parents=True, exist_ok=True)
    start_result = {
        "exit_code": 0,
        "stdout": "skipped: p0 real-worker calibration is already running inside KF container",
        "stderr": "",
    }
    append_jsonl(PLAN_LOG, {
        "schema": SCHEMA,
        "runId": RUN_ID,
        "phase": "RUN_START",
        "calibrationMethod": CALIBRATION_METHOD,
        "probeBackend": "standard_task_workers",
        "workerDurationSeconds": args.worker_duration,
        "unitsPerWorker": args.units_per_worker,
        "unitIterations": args.unit_iterations,
        "stepTimeoutSeconds": args.step_timeout,
        "postPeakConfirmRounds": args.post_peak_confirm_rounds,
        "probeMode": args.mode,
        "sequence": args.sequence,
        "startTarget": args.start_target,
        "minTarget": args.min_target,
        "maxTarget": args.max_target,
        "throughputStopRule": "continue_10_rounds_after_latest_peak_then_select_peak",
        "atMs": now_ms(),
        "startBridge": start_result,
    })

    if args.mode == "sequence" or args.sequence:
        sequence = parse_sequence(args.sequence or f"{args.min_target}-{args.max_target}")
        results = run_sequence_mode(
            sequence,
            args.settle,
            args.bridge_timeout,
            args.units_per_worker,
            args.unit_iterations,
            args.step_timeout,
        )
    else:
        sequence, results = run_seek_mode(
            args.start_target,
            args.min_target,
            args.max_target,
            args.settle,
            args.bridge_timeout,
            args.units_per_worker,
            args.unit_iterations,
            args.step_timeout,
            args.post_peak_confirm_rounds,
        )

    summary = build_summary(sequence, results)
    summary.update(maybe_write_overlay(summary))
    summary_path = Path(args.summary)
    write_json(summary_path, summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


RUN_ID = "p0-" + uuid.uuid4().hex[:10]


if __name__ == "__main__":
    raise SystemExit(main())
