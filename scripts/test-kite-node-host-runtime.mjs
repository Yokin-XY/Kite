import assert from 'node:assert/strict';
import childProcess from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const scriptPath = fileURLToPath(import.meta.url);
const scriptDirectory = path.dirname(scriptPath);

if (process.argv[2] === '--proot-shim') {
  const routed = process.argv.slice(3);
  const workdirIndex = routed.indexOf('-w');
  const containerCwd = workdirIndex >= 0 ? routed[workdirIndex + 1] : '';
  const commandOffset = workdirIndex >= 0 ? workdirIndex + 2 : 0;
  const file = routed[commandOffset];
  const args = routed.slice(commandOffset + 1);
  if (file === 'kite-fail') process.exit(7);
  if (file === 'kite-wait') {
    process.on('SIGTERM', () => process.exit(0));
    setInterval(() => {}, 1_000);
  } else {
    process.stdout.write(`${JSON.stringify({ file, args, containerCwd, sample: process.env.SAMPLE || '' })}\n`);
  }
} else if (process.argv[2] === '--fork-child') {
  process.send?.({ lane: process.env.KITE_NODE_HOST_LANE, execPath: process.execPath });
} else if (process.argv[2] === '--node-spawn-child') {
  process.stdout.write(`node-child:${process.env.KITE_NODE_HOST_LANE}`);
} else {
  const preload = path.join(scriptDirectory, 'kite-node-host-runtime.cjs');
  const controlDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'kite-node-host-'));
  const encode = (value) => Buffer.from(JSON.stringify(value), 'utf8').toString('base64');
  process.env.KITE_NODE_HOST_LAUNCHER = process.execPath;
  process.env.KITE_NODE_HOST_BINARY = process.execPath;
  process.env.KITE_NODE_HOST_WORKSPACE = scriptDirectory;
  process.env.KITE_NODE_HOST_CONTROL = controlDirectory;
  process.env.KITE_NODE_HOST_ROOTFS = path.dirname(scriptDirectory);
  process.env.KITE_NODE_HOST_LANE = 'test-direct';
  process.env.KITE_NODE_HOST_PROOT_ARGV_B64 = encode([
    process.execPath,
    scriptPath,
    '--proot-shim',
    '-w',
    '/workspace',
  ]);
  process.env.KITE_NODE_HOST_PROOT_ENV_B64 = encode({ PATH: process.env.PATH || '', BASELINE: 'proot' });
  process.env.NODE_OPTIONS = `--require=${preload}`;

  const managedBin = path.join(controlDirectory, 'bin');
  fs.mkdirSync(managedBin, { recursive: true });
  const managedNodeCommand = path.join(managedBin, 'arbitrary-managed-cli');
  fs.writeFileSync(
    managedNodeCommand,
    "#!/usr/bin/env node\nprocess.stdout.write(`managed-node:${process.env.KITE_NODE_HOST_LANE}:${process.argv[2]}`);\n",
  );
  const managedShellCommand = path.join(managedBin, 'arbitrary-shell-cli');
  fs.writeFileSync(managedShellCommand, '#!/usr/bin/env sh\necho shell\n');
  const providerScript = path.join(controlDirectory, 'software', 'provider', 'wrapped.cjs');
  fs.mkdirSync(path.dirname(providerScript), { recursive: true });
  fs.writeFileSync(
    providerScript,
    "#!/usr/bin/env node\nprocess.stdout.write(`managed-wrapper:${process.env.KITE_NODE_HOST_LANE}:${process.argv[2]}`);\n",
  );
  const directProviderWrapper = path.join(managedBin, 'arbitrary-provider-wrapper');
  fs.writeFileSync(
    directProviderWrapper,
    '#!/usr/bin/env sh\nexport LD_LIBRARY_PATH="/workspace/.kf/software/provider/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"\nexec "/workspace/.kf/software/provider/wrapped.cjs" "$@"\n',
  );
  const nodeProviderWrapper = path.join(managedBin, 'arbitrary-node-wrapper');
  fs.writeFileSync(
    nodeProviderWrapper,
    '#!/usr/bin/env sh\nexec node "/workspace/.kf/software/provider/wrapped.cjs" "$@"\n',
  );

  await import('./kite-node-host-runtime.cjs');

  const direct = childProcess.spawnSync('node', ['-e', "process.stdout.write('direct-node')"], {
    encoding: 'utf8',
  });
  assert.equal(direct.status, 0, direct.stderr);
  assert.equal(direct.stdout, 'direct-node');

  const external = childProcess.spawnSync(
    'kite-external',
    ['arg with space', scriptPath, path.join(controlDirectory, 'state.json')],
    {
    cwd: scriptDirectory,
    env: { ...process.env, SAMPLE: 'yes' },
    encoding: 'utf8',
    },
  );
  assert.equal(external.status, 0);
  const externalResult = JSON.parse(external.stdout.trim());
  assert.equal(externalResult.file, 'kite-external');
  assert.deepEqual(externalResult.args, [
    'arg with space',
    '/workspace/test-kite-node-host-runtime.mjs',
    '/workspace/.kf/state.json',
  ]);
  assert.equal(externalResult.containerCwd, '/workspace');
  assert.equal(externalResult.sample, 'yes');

  const nodeChild = childProcess.spawnSync(
    'node',
    ['/workspace/test-kite-node-host-runtime.mjs', '--node-spawn-child'],
    { encoding: 'utf8', cwd: '/workspace' },
  );
  assert.equal(nodeChild.status, 0);
  assert.equal(nodeChild.stdout, 'node-child:test-direct');

  const managedNodeChild = childProcess.spawnSync(
    'arbitrary-managed-cli',
    ['value with spaces'],
    { encoding: 'utf8', cwd: '/workspace' },
  );
  assert.equal(managedNodeChild.status, 0, managedNodeChild.stderr);
  assert.equal(managedNodeChild.stdout, 'managed-node:test-direct:value with spaces');

  for (const wrapper of ['arbitrary-provider-wrapper', 'arbitrary-node-wrapper']) {
    const wrappedChild = childProcess.spawnSync(wrapper, ['nested value'], {
      encoding: 'utf8',
      cwd: '/workspace',
    });
    assert.equal(wrappedChild.status, 0, wrappedChild.stderr);
    assert.equal(wrappedChild.stdout, 'managed-wrapper:test-direct:nested value');
  }

  const managedShellChild = childProcess.spawnSync(
    'arbitrary-shell-cli',
    ['shell-arg'],
    { encoding: 'utf8', cwd: '/workspace' },
  );
  assert.equal(managedShellChild.status, 0);
  const managedShellResult = JSON.parse(managedShellChild.stdout.trim());
  assert.equal(managedShellResult.file, 'arbitrary-shell-cli');
  assert.deepEqual(managedShellResult.args, ['shell-arg']);

  assert.throws(
    () => childProcess.execFileSync('kite-fail'),
    (error) => error.status === 7,
  );

  const execResult = await new Promise((resolve, reject) => {
    childProcess.exec(`printf host-proxy ${scriptPath}`, { encoding: 'utf8' }, (error, stdout) => {
      if (error) reject(error);
      else resolve(JSON.parse(stdout.trim()));
    });
  });
  assert.equal(execResult.file, '/bin/sh');
  assert.deepEqual(execResult.args, ['-c', 'printf host-proxy /workspace/test-kite-node-host-runtime.mjs']);

  const execFileResult = await new Promise((resolve, reject) => {
    childProcess.execFile('kite-external', ['async'], { encoding: 'utf8' }, (error, stdout) => {
      if (error) reject(error);
      else resolve(JSON.parse(stdout.trim()));
    });
  });
  assert.deepEqual(execFileResult.args, ['async']);

  const forked = childProcess.fork(scriptPath, ['--fork-child'], { silent: true });
  const forkExit = once(forked, 'exit');
  const [message] = await once(forked, 'message');
  assert.equal(message.lane, 'test-direct');
  assert.equal(message.execPath, process.execPath);
  await forkExit;

  const waiting = childProcess.spawn('kite-wait', [], { stdio: 'ignore' });
  await new Promise((resolve) => setTimeout(resolve, 150));
  assert.equal(waiting.kill('SIGTERM'), true);
  const [exitCode, signal] = await once(waiting, 'exit');
  assert.ok(exitCode === 0 || signal === 'SIGTERM');

  fs.rmSync(controlDirectory, { recursive: true, force: true });
  process.stdout.write('kite-node-host-runtime: ok\n');
}
