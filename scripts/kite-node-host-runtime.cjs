'use strict';

// 该预载层只拥有 Node 子进程的运行通道路由，不处理业务软件或模块文件。
const childProcess = require('node:child_process');
const fs = require('node:fs');
const { syncBuiltinESMExports } = require('node:module');
const path = require('node:path');
const { fileURLToPath } = require('node:url');

const launcher = process.env.KITE_NODE_HOST_LAUNCHER || '';
const nodeBinary = process.env.KITE_NODE_HOST_BINARY || '';
const prootArgv = decodeJson('KITE_NODE_HOST_PROOT_ARGV_B64', []);
const prootEnv = decodeJson('KITE_NODE_HOST_PROOT_ENV_B64', {});
const hostWorkspace = normalizedPath(process.env.KITE_NODE_HOST_WORKSPACE || '');
const hostControl = normalizedPath(process.env.KITE_NODE_HOST_CONTROL || '');
const hostRootfs = normalizedPath(process.env.KITE_NODE_HOST_ROOTFS || '');
const requiredHostEnvironmentNames = Object.keys(process.env).filter((name) =>
  name.startsWith('KITE_NODE_HOST_') || name === 'NODE_OPTIONS');

if (launcher) {
  Object.defineProperty(process, 'execPath', {
    configurable: true,
    enumerable: true,
    writable: false,
    value: launcher,
  });
}

const original = {
  spawn: childProcess.spawn,
  spawnSync: childProcess.spawnSync,
  execFile: childProcess.execFile,
  execFileSync: childProcess.execFileSync,
  exec: childProcess.exec,
  execSync: childProcess.execSync,
  fork: childProcess.fork,
};

function decodeJson(name, fallback) {
  const encoded = process.env[name];
  if (!encoded) return fallback;
  try {
    return JSON.parse(Buffer.from(encoded, 'base64').toString('utf8'));
  } catch {
    return fallback;
  }
}

function normalizedPath(value) {
  if (!value) return '';
  try {
    return path.resolve(value);
  } catch {
    return '';
  }
}

function pathFromCwd(cwd) {
  if (cwd instanceof URL) {
    try {
      return fileURLToPath(cwd);
    } catch {
      return '';
    }
  }
  if (Buffer.isBuffer(cwd)) return cwd.toString();
  return typeof cwd === 'string' ? cwd : '';
}

function isInside(candidate, root) {
  if (!candidate || !root) return false;
  return candidate === root || candidate.startsWith(`${root}${path.sep}`);
}

function mapHostPathToContainer(value) {
  if (typeof value !== 'string' || value.length === 0) return value;
  if (!value.includes('/') && !value.includes('\\')) return value;
  const normalized = normalizedPath(value);
  if (isInside(normalized, hostControl)) {
    const suffix = normalized.slice(hostControl.length).split(path.sep).join('/');
    return `/workspace/.kf${suffix}`;
  }
  if (isInside(normalized, hostWorkspace)) {
    const suffix = normalized.slice(hostWorkspace.length).split(path.sep).join('/');
    return `/workspace${suffix}`;
  }
  if (isInside(normalized, hostRootfs)) {
    const suffix = normalized.slice(hostRootfs.length).split(path.sep).join('/');
    return suffix || '/';
  }
  return value;
}

function mapHostTextToContainer(value) {
  if (typeof value !== 'string') return value;
  let mapped = value;
  if (hostControl) {
    mapped = mapped.split(`${hostControl}${path.sep}`).join('/workspace/.kf/');
    mapped = mapped.split(hostControl).join('/workspace/.kf');
  }
  if (hostWorkspace) {
    mapped = mapped.split(`${hostWorkspace}${path.sep}`).join('/workspace/');
    mapped = mapped.split(hostWorkspace).join('/workspace');
  }
  if (hostRootfs) {
    mapped = mapped.split(`${hostRootfs}${path.sep}`).join('/');
    mapped = mapped.split(hostRootfs).join('');
  }
  return mapped;
}

function mapContainerPathToHost(value) {
  if (typeof value !== 'string' || value.length === 0) return value;
  const physical = normalizedPath(value);
  if (path.isAbsolute(value) && [hostControl, hostWorkspace, hostRootfs].some((root) => isInside(physical, root))) {
    return physical;
  }
  if (value === '/workspace/.kf') return hostControl || value;
  if (value.startsWith('/workspace/.kf/') && hostControl) {
    return path.join(hostControl, ...value.slice('/workspace/.kf/'.length).split('/'));
  }
  if (value === '/workspace') return hostWorkspace || value;
  if (value.startsWith('/workspace/') && hostWorkspace) {
    return path.join(hostWorkspace, ...value.slice('/workspace/'.length).split('/'));
  }
  if (value === '/root' && hostRootfs) return path.join(hostRootfs, 'root');
  if (value.startsWith('/root/') && hostRootfs) {
    return path.join(hostRootfs, 'root', ...value.slice('/root/'.length).split('/'));
  }
  if (value.startsWith('/') && hostRootfs) {
    return path.join(hostRootfs, ...value.slice(1).split('/'));
  }
  return value;
}

function mapOptionPath(value, mapper) {
  if (typeof value !== 'string') return value;
  const separator = value.indexOf('=');
  if (separator > 0) {
    const mapped = mapper(value.slice(separator + 1));
    return `${value.slice(0, separator + 1)}${mapped}`;
  }
  return mapper(value);
}

function isNodeCommand(file) {
  if (!launcher || typeof file !== 'string') return false;
  if (file === launcher || file === nodeBinary) return true;
  return file === 'node';
}

function withinRuntimeRoots(candidate) {
  return [hostControl, hostWorkspace, hostRootfs].some((root) => isInside(candidate, root));
}

function commandCandidate(file, options) {
  if (typeof file !== 'string' || file.length === 0) return '';
  if (!file.includes('/') && !file.includes('\\')) {
    return hostControl ? path.join(hostControl, 'bin', file) : '';
  }
  const mapped = mapContainerPathToHost(file);
  if (path.isAbsolute(mapped)) return normalizedPath(mapped);
  const requestedCwd = pathFromCwd(options && options.cwd) || process.cwd();
  return normalizedPath(path.resolve(mapContainerPathToHost(requestedCwd), mapped));
}

function resolveManagedNodeInvocation(file, options) {
  let current = commandCandidate(file, options);
  if (!current) return null;
  for (let depth = 0; depth < 12; depth += 1) {
    current = normalizedPath(current);
    if (!withinRuntimeRoots(current)) return null;
    let stat;
    try {
      stat = fs.lstatSync(current);
    } catch {
      return null;
    }
    if (stat.isSymbolicLink()) {
      let target;
      try {
        target = fs.readlinkSync(current);
      } catch {
        return null;
      }
      current = target.startsWith('/')
        ? mapContainerPathToHost(target)
        : path.resolve(path.dirname(current), target);
      continue;
    }
    if (!stat.isFile()) return null;
    let content = '';
    try {
      const descriptor = fs.openSync(current, 'r');
      try {
        const buffer = Buffer.alloc(1024);
        const length = fs.readSync(descriptor, buffer, 0, buffer.length, 0);
        content = buffer.subarray(0, length).toString('utf8');
      } finally {
        fs.closeSync(descriptor);
      }
    } catch {
      return null;
    }
    const firstLine = content.split(/\r?\n/, 1)[0];
    if (/^#!\s*(?:\/usr\/bin\/env\s+(?:-S\s+)?node|\/(?:usr\/bin|bin)\/node)(?:\s|$)/.test(firstLine)) {
      return { entry: current };
    }
    const wrapperTarget = strictManagedWrapperTarget(content);
    if (!wrapperTarget) return null;
    if (wrapperTarget.interpreter === 'node') {
      current = mapContainerPathToHost(wrapperTarget.target);
    } else {
      current = wrapperTarget.target.startsWith('/')
        ? mapContainerPathToHost(wrapperTarget.target)
        : path.resolve(path.dirname(current), wrapperTarget.target);
    }
  }
  return null;
}

function strictManagedWrapperTarget(content) {
  const lines = content.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  if (lines.length < 2 || !/^#!\s*\/usr\/bin\/env\s+sh$/.test(lines[0])) return null;
  const setupLines = lines.slice(1, -1);
  if (!setupLines.every((line) => /^export\s+LD_LIBRARY_PATH=/.test(line))) return null;
  const execLine = lines[lines.length - 1];
  const nodeMatch = execLine.match(
    /^exec\s+(?:node|"node"|'node')\s+(?:"([^"]+)"|'([^']+)'|(\S+))\s+(?:"\$@"|'\$@')$/,
  );
  if (nodeMatch) {
    return { interpreter: 'node', target: nodeMatch[1] || nodeMatch[2] || nodeMatch[3] };
  }
  const directMatch = execLine.match(
    /^exec\s+(?:"([^"]+)"|'([^']+)'|(\S+))\s+(?:"\$@"|'\$@')$/,
  );
  if (!directMatch) return null;
  return { interpreter: 'direct', target: directMatch[1] || directMatch[2] || directMatch[3] };
}

function mappedContainerCwd(cwd) {
  const requested = pathFromCwd(cwd) || process.cwd();
  const mapped = mapHostPathToContainer(requested);
  return mapped !== requested || requested.startsWith('/') ? mapped : '/workspace';
}

function prootPrefix(cwd) {
  if (!Array.isArray(prootArgv) || prootArgv.length === 0) {
    throw Object.assign(new Error('Kite PRoot child-process contract is unavailable'), {
      code: 'KITE_PROOT_UNAVAILABLE',
    });
  }
  const prefix = [...prootArgv];
  const workingDirectory = mappedContainerCwd(cwd);
  for (let index = 0; index + 1 < prefix.length; index += 1) {
    if (prefix[index] === '-w') {
      prefix[index + 1] = workingDirectory;
      break;
    }
  }
  return prefix;
}

function prootOptions(options) {
  const requested = options && typeof options === 'object' ? options : {};
  const requestedEnv = requested.env && typeof requested.env === 'object' ? requested.env : process.env;
  const env = { ...prootEnv };
  for (const [key, value] of Object.entries(requestedEnv || {})) {
    if (value === undefined || key.startsWith('KITE_NODE_HOST_')) continue;
    if (key === 'LD_PRELOAD' || key === 'LD_LIBRARY_PATH' || key === 'NODE_OPTIONS') continue;
    if (Object.hasOwn(prootEnv, key) && String(value) === process.env[key]) continue;
    env[key] = mapHostTextToContainer(String(value));
  }
  return { ...requested, cwd: undefined, shell: false, env };
}

function hostOptions(options) {
  const requested = options && typeof options === 'object' ? options : {};
  const env = { ...(requested.env && typeof requested.env === 'object' ? requested.env : process.env) };
  for (const name of requiredHostEnvironmentNames) {
    const required = process.env[name];
    if (required !== undefined) env[name] = required;
  }
  const cwd = pathFromCwd(requested.cwd);
  return {
    ...requested,
    cwd: cwd ? mapContainerPathToHost(cwd) : requested.cwd,
    shell: false,
    env,
  };
}

function routeFile(file, args, options) {
  const normalizedArgs = Array.isArray(args) ? args : [];
  if (isNodeCommand(file)) {
    return {
      file: launcher,
      args: normalizedArgs.map((value) => mapOptionPath(value, mapContainerPathToHost)),
      options: hostOptions(options),
    };
  }
  const managedNode = resolveManagedNodeInvocation(file, options);
  if (managedNode) {
    return {
      file: launcher,
      args: [
        managedNode.entry,
        ...normalizedArgs.map((value) => mapOptionPath(value, mapContainerPathToHost)),
      ],
      options: hostOptions(options),
    };
  }
  const prefix = prootPrefix(options && options.cwd);
  return {
    file: prefix[0],
    args: [
      ...prefix.slice(1),
      mapHostPathToContainer(file),
      ...normalizedArgs.map((value) => mapOptionPath(value, mapHostPathToContainer)),
    ],
    options: prootOptions(options),
  };
}

function shellRoute(command, options) {
  const prefix = prootPrefix(options && options.cwd);
  const requestedShell = options && typeof options.shell === 'string' ? options.shell : '/bin/sh';
  return {
    file: prefix[0],
    args: [...prefix.slice(1), mapHostPathToContainer(requestedShell), '-c', mapHostTextToContainer(command)],
    options: prootOptions(options),
  };
}

function shellCommand(file, args) {
  return [file, ...(Array.isArray(args) ? args : [])].join(' ');
}

childProcess.spawn = function kiteSpawn(file, args, options) {
  if (!Array.isArray(args)) {
    options = args;
    args = [];
  }
  const routed = options && options.shell
    ? shellRoute(shellCommand(file, args), options)
    : routeFile(file, args, options);
  return original.spawn(routed.file, routed.args, routed.options);
};

childProcess.spawnSync = function kiteSpawnSync(file, args, options) {
  if (!Array.isArray(args)) {
    options = args;
    args = [];
  }
  const routed = options && options.shell
    ? shellRoute(shellCommand(file, args), options)
    : routeFile(file, args, options);
  return original.spawnSync(routed.file, routed.args, routed.options);
};

childProcess.execFile = function kiteExecFile(file, args, options, callback) {
  if (!Array.isArray(args)) {
    callback = options;
    options = args;
    args = [];
  }
  if (typeof options === 'function') {
    callback = options;
    options = undefined;
  }
  const routed = options && options.shell
    ? shellRoute(shellCommand(file, args), options)
    : routeFile(file, args, options);
  return original.execFile(routed.file, routed.args, routed.options, callback);
};

childProcess.execFileSync = function kiteExecFileSync(file, args, options) {
  if (!Array.isArray(args)) {
    options = args;
    args = [];
  }
  const routed = options && options.shell
    ? shellRoute(shellCommand(file, args), options)
    : routeFile(file, args, options);
  return original.execFileSync(routed.file, routed.args, routed.options);
};

childProcess.exec = function kiteExec(command, options, callback) {
  if (typeof options === 'function') {
    callback = options;
    options = undefined;
  }
  const routed = shellRoute(command, options);
  return original.execFile(routed.file, routed.args, routed.options, callback);
};

childProcess.execSync = function kiteExecSync(command, options) {
  const routed = shellRoute(command, options);
  return original.execFileSync(routed.file, routed.args, routed.options);
};

childProcess.fork = function kiteFork(modulePath, args, options) {
  if (!Array.isArray(args)) {
    options = args;
    args = [];
  }
  const routedOptions = hostOptions(options);
  routedOptions.execPath = launcher;
  return original.fork(modulePath, args, routedOptions);
};

syncBuiltinESMExports();
