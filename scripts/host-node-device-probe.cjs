'use strict';

const { lookup } = require('node:dns');
const { spawn } = require('node:child_process');
const { Worker } = require('node:worker_threads');

const mode = process.argv[2] || 'all';

function runWorker() {
  return new Promise((resolve, reject) => {
    const worker = new Worker('process.exit(0)', { eval: true });
    worker.once('error', reject);
    worker.once('exit', (code) => {
      console.log(`worker-exit=${code}`);
      if (code === 0) resolve();
      else reject(new Error(`worker exited with ${code}`));
    });
  });
}

function runDns() {
  return new Promise((resolve, reject) => {
    lookup('localhost', (error, address) => {
      if (error) reject(error);
      else {
        console.log(`dns-address=${address}`);
        resolve();
      }
    });
  });
}

function runSpawn() {
  return new Promise((resolve, reject) => {
    const child = spawn('true');
    child.once('error', reject);
    child.once('exit', (code) => {
      console.log(`spawn-exit=${code}`);
      if (code === 0) resolve();
      else reject(new Error(`spawn exited with ${code}`));
    });
  });
}

async function main() {
  if (mode === 'basic' || mode === 'all') process.stdout.write('basic-ok\n');
  if (mode === 'worker' || mode === 'all') await runWorker();
  if (mode === 'dns' || mode === 'all') await runDns();
  if (mode === 'spawn' || mode === 'all') await runSpawn();
  if (!['all', 'basic', 'worker', 'dns', 'spawn'].includes(mode)) {
    throw new Error(`unknown-mode=${mode}`);
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
