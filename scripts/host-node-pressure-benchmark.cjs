#!/usr/bin/env node
'use strict';

const childProcess = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const { once } = require('node:events');
const { performance } = require('node:perf_hooks');
const { Worker } = require('node:worker_threads');

const mode = process.argv[2] || '--matrix';
const workspace = process.env.KITE_NODE_HOST_WORKSPACE || process.cwd();
const containerScript = '/workspace/host-node-pressure-benchmark.cjs';
const managedCommand = 'kite-node-pressure-benchmark';

async function smallFiles(token) {
  const base = path.join(process.cwd(), '.kf', 'cache', 'host-node-pressure', token);
  fs.mkdirSync(base, { recursive: true });
  try {
    await Promise.all(Array.from({ length: 48 }, async (_, index) => {
      const file = path.join(base, `file-${index}.txt`);
      await fs.promises.writeFile(file, `sample-${index}`);
      const [content, stat] = await Promise.all([
        fs.promises.readFile(file, 'utf8'),
        fs.promises.stat(file),
      ]);
      if (content !== `sample-${index}` || stat.size <= 0) throw new Error('small_file_mismatch');
      await fs.promises.unlink(file);
    }));
  } finally {
    fs.rmSync(base, { recursive: true, force: true });
  }
}

async function workerThread() {
  const worker = new Worker(
    'let total = 0; for (let i = 0; i < 600000; i += 1) total = (total + i) % 1000003; process.exit(total < 0 ? 1 : 0);',
    { eval: true },
  );
  const [code] = await once(worker, 'exit');
  if (code !== 0) throw new Error(`worker_exit_${code}`);
}

async function runWorker() {
  const workload = process.argv[3];
  const token = process.argv[4] || `${process.pid}`;
  const started = performance.now();
  if (workload === 'small_files') await smallFiles(token);
  else if (workload === 'worker_thread') await workerThread();
  else if (workload !== 'noop') throw new Error(`unknown_workload_${workload}`);
  process.stdout.write(`${JSON.stringify({ ok: true, workload, durationMs: performance.now() - started })}\n`);
}

function spawnOne(lane, workload, token) {
  const command = lane === 'host_node'
    ? { file: managedCommand, args: ['--worker', workload, token] }
    : {
        file: '/bin/sh',
        args: ['-c', `exec node ${containerScript} --worker ${workload} ${token}`],
      };
  const child = childProcess.spawn(command.file, command.args, {
    cwd: workspace,
    env: process.env,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let stdout = '';
  let stderr = '';
  child.stdout.on('data', (chunk) => { stdout += chunk; });
  child.stderr.on('data', (chunk) => { stderr += chunk; });
  return once(child, 'close').then(([code, signal]) => {
    if (code !== 0) throw new Error(`${lane}_${workload}_exit=${code}_signal=${signal}_stderr=${stderr}`);
    const result = JSON.parse(stdout.trim().split(/\r?\n/).at(-1));
    if (!result.ok) throw new Error(`${lane}_${workload}_not_ok`);
    return result;
  });
}

function percentile(values, ratio) {
  const ordered = [...values].sort((left, right) => left - right);
  return ordered[Math.min(ordered.length - 1, Math.ceil((ordered.length - 1) * ratio))];
}

function summary(kind, lane, load, samples, failureReasons) {
  return {
    kind,
    lane,
    load,
    rounds: samples.length + failureReasons.length,
    p50Ms: Number(percentile(samples, 0.5).toFixed(3)),
    p95Ms: Number(percentile(samples, 0.95).toFixed(3)),
    failures: failureReasons.length,
    failureReasons,
  };
}

async function measureParallel(workload, lane, concurrency, rounds) {
  const samples = [];
  const failureReasons = [];
  for (let round = 0; round < rounds; round += 1) {
    const started = performance.now();
    try {
      await Promise.all(Array.from({ length: concurrency }, (_, index) =>
        spawnOne(lane, workload, `${workload}-${lane}-${concurrency}-${round}-${index}`)));
      samples.push(performance.now() - started);
    } catch (error) {
      failureReasons.push(String(error && error.message ? error.message : error));
      process.stderr.write(`${error.stack || error}\n`);
    }
  }
  if (samples.length === 0) throw new Error(`${workload}_${lane}_${concurrency}_no_samples`);
  return summary(workload, lane, concurrency, samples, failureReasons);
}

async function measureStorm(lane, count, rounds) {
  const samples = [];
  const failureReasons = [];
  for (let round = 0; round < rounds; round += 1) {
    const started = performance.now();
    try {
      for (let index = 0; index < count; index += 1) {
        await spawnOne(lane, 'noop', `noop-${lane}-${count}-${round}-${index}`);
      }
      samples.push(performance.now() - started);
    } catch (error) {
      failureReasons.push(String(error && error.message ? error.message : error));
      process.stderr.write(`${error.stack || error}\n`);
    }
  }
  if (samples.length === 0) throw new Error(`storm_${lane}_${count}_no_samples`);
  return summary('node_process_storm', lane, count, samples, failureReasons);
}

async function runCase() {
  const workload = process.argv[3];
  const lane = process.argv[4];
  const load = Number(process.argv[5]);
  const rounds = Number(process.argv[6]);
  if (!['small_files', 'worker_thread', 'node_process_storm'].includes(workload)) {
    throw new Error('invalid_case_workload');
  }
  if (!['host_node', 'proot'].includes(lane)) throw new Error('invalid_case_lane');
  if (!Number.isInteger(load) || load < 1 || load > 100) throw new Error('invalid_case_load');
  if (!Number.isInteger(rounds) || rounds < 1 || rounds > 20) throw new Error('invalid_case_rounds');
  const result = workload === 'node_process_storm'
    ? await measureStorm(lane, load, rounds)
    : await measureParallel(workload, lane, load, rounds);
  process.stdout.write(`${JSON.stringify({ ok: result.failures === 0, result })}\n`);
}

async function runMatrix() {
  if (!process.env.KITE_NODE_HOST_LANE) throw new Error('host_node_environment_missing');
  const roundsIndex = process.argv.indexOf('--rounds');
  const rounds = roundsIndex >= 0 ? Number(process.argv[roundsIndex + 1]) : 3;
  if (!Number.isInteger(rounds) || rounds < 1 || rounds > 10) throw new Error('invalid_rounds');
  const results = [];
  for (const workload of ['small_files', 'worker_thread']) {
    for (const lane of ['host_node', 'proot']) {
      for (const concurrency of [1, 4, 8, 16]) {
        const result = await measureParallel(workload, lane, concurrency, rounds);
        results.push(result);
        process.stdout.write(`${JSON.stringify(result)}\n`);
      }
    }
  }
  for (const lane of ['host_node', 'proot']) {
    for (const count of [1, 10, 100]) {
      const result = await measureStorm(lane, count, rounds);
      results.push(result);
      process.stdout.write(`${JSON.stringify(result)}\n`);
    }
  }
  process.stdout.write(`${JSON.stringify({ ok: true, rounds, results })}\n`);
}

Promise.resolve(
  mode === '--worker' ? runWorker() : mode === '--case' ? runCase() : runMatrix()
).catch((error) => {
  process.stderr.write(`${error.stack || error}\n`);
  process.exitCode = 1;
});
