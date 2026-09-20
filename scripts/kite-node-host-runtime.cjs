'use strict';

// 该预载层只拥有 Node 子进程的运行通道路由，不处理业务软件或模块文件。
const childProcess = require('node:child_process');
const fs = require('node:fs');
const { syncBuiltinESMExports } = require('node:module');
const path = require('node:path');
const { fileURLToPath } = require('node:url');

const launcher = process.env.KITE_NODE_HOST_LAUNCHER || '';
const nodeBinary = process.env.KITE_NODE_HOST_BINARY || '';
const loader = process.env.KITE_NODE_HOST_LOADER || '';
const libraryPath = process.env.KITE_NODE_HOST_LIBRARY_PATH || '';
const compatLibrary = process.env.KITE_NODE_HOST_COMPAT_LIBRARY || '';
const prootArgv = decodeJson('KITE_NODE_HOST_PROOT_ARGV_B64', []);
const prootEnv = decodeJson('KITE_NODE_HOST_PROOT_ENV_B64', {});
const guestTmp = normalizedPath(process.env.KITE_NODE_HOST_GUEST_TMP || '');
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
  stat: fs.stat,
  statSync: fs.statSync,
  lstat: fs.lstat,
  lstatSync: fs.lstatSync,
  mkdir: fs.mkdir,
  mkdirSync: fs.mkdirSync,
  open: fs.open,
  openSync: fs.openSync,
  rename: fs.rename,
  renameSync: fs.renameSync,
  unlink: fs.unlink,
  unlinkSync: fs.unlinkSync,
  rm: fs.rm,
  rmSync: fs.rmSync,
  rmdir: fs.rmdir,
  rmdirSync: fs.rmdirSync,
  access: fs.access,
  accessSync: fs.accessSync,
  realpath: fs.realpath,
  realpathSync: fs.realpathSync,
};

/*
 * 宿主车道没有根级 /tmp。部分软件把锁/临时目录硬编码为 "/tmp"，既不走
 * TMPDIR 也无配置口。KITE_NODE_HOST_GUEST_TMP 注入后，把 fs 入口里 "/tmp"
 * 与 "/tmp/..." 形式的路径重写到该目录；与 C 兼容层（KITE_GLIBC_HOST_
 * GUEST_TMP）指向同一目录，覆盖 glibc 与 libuv 直连 syscall 两条路径。
 * 未注入时零行为。
 */
function mapGuestTmpPath(value) {
  if (!guestTmp || typeof value !== 'string' || value.length === 0) return value;
  if (value === '/tmp') return guestTmp;
  if (value.startsWith('/tmp/')) {
    const suffix = value.slice('/tmp/'.length).split('/').filter((part) => part.length > 0 && part !== '.');
    return path.join(guestTmp, ...suffix);
  }
  return value;
}

function isGuestTmpPath(value) {
  return guestTmp && typeof value === 'string' && (value === '/tmp' || value.startsWith('/tmp/')) && mapGuestTmpPath(value) !== value;
}

function pathCallback(args, mapper) {
  let changed = false;
  const mapped = args.map((argument) => {
    if (isGuestTmpPath(argument)) {
      changed = true;
      return mapper(argument);
    }
    return argument;
  });
  return changed ? mapped : args;
}

function wrapFsPathFunction(target, name, mapper) {
  const delegate = target[name];
  if (typeof delegate !== 'function') return;
  const wrapped = function wrappedFsPathFunction(...args) {
    return delegate.apply(this, pathCallback(args, mapper));
  };
  // 保留原函数的自有属性（statSync/lstatSync/realpathSync 的 .native 变体等）。
  for (const key of Object.getOwnPropertyNames(delegate)) {
    if (key !== 'length' && key !== 'name' && !(key in wrapped)) {
      try {
        Object.defineProperty(wrapped, key, Object.getOwnPropertyDescriptor(delegate, key));
      } catch {
        // 只读属性复制失败不影响主路径。
      }
    }
  }
  target[name] = wrapped;
}

function installGuestTmpRouting() {
  if (!guestTmp) return;
  const mapper = mapGuestTmpPath;
  for (const name of [
    'stat', 'statSync', 'lstat', 'lstatSync',
    'mkdir', 'mkdirSync', 'open', 'openSync',
    'rename', 'renameSync', 'unlink', 'unlinkSync',
    'rm', 'rmSync', 'rmdir', 'rmdirSync',
    'access', 'accessSync', 'realpath', 'realpathSync',
    'existsSync', 'truncate', 'truncateSync',
    'readFile', 'readFileSync', 'writeFile', 'writeFileSync',
    'appendFile', 'appendFileSync', 'copyFile', 'copyFileSync',
    'readlink', 'readlinkSync', 'symlink', 'symlinkSync',
    'link', 'linkSync', 'readdir', 'readdirSync',
    'opendir', 'opendirSync', 'chmod', 'chmodSync',
    'utimes', 'utimesSync', 'lutimes', 'lutimesSync',
    'createReadStream', 'createWriteStream',
  ]) {
    wrapFsPathFunction(fs, name, mapper);
  }
  // .native 变体挂在 wrap 后函数的复制属性上；再包一层路径重写。
  for (const name of ['statSync', 'lstatSync', 'realpathSync']) {
    const owner = fs[name];
    const nativeDelegate = owner && typeof owner.native === 'function' ? owner.native : null;
    if (!nativeDelegate) continue;
    Object.defineProperty(owner, 'native', {
      configurable: true,
      writable: true,
      value: function wrappedFsNative(...args) {
        return nativeDelegate.apply(this, pathCallback(args, mapper));
      },
    });
  }
  // fs.promises 家族：
  const promises = fs.promises && typeof fs.promises === 'object' ? fs.promises : null;
  if (promises) {
    for (const name of [
      'stat', 'lstat', 'mkdir', 'open',
      'rename', 'unlink', 'rm', 'rmdir',
      'access', 'realpath', 'truncate',
      'readFile', 'writeFile', 'appendFile', 'copyFile',
      'readlink', 'symlink', 'link', 'readdir',
      'opendir', 'chmod', 'utimes', 'lutimes',
    ]) {
      wrapFsPathFunction(promises, name, mapper);
    }
  }
}

/*
 * node:sqlite 的 DatabaseSync 在 native 层打开数据库文件，路径不经
 * node:fs 表面（JS 劫持与 glibc 符号拦截都覆盖不到）。宿主车道下把
 * 构造参数里的 "/tmp" 前缀路径翻译到 guest 目录，语义与 fs 层一致。
 */
function installGuestTmpSqliteRouting() {
  if (!guestTmp) return;
  let sqlite;
  try {
    sqlite = require('node:sqlite');
  } catch {
    return;
  }
  const DatabaseSync = sqlite && sqlite.DatabaseSync;
  if (typeof DatabaseSync !== 'function') return;
  sqlite.DatabaseSync = function KiteDatabaseSync(path, ...rest) {
    if (path && typeof path === 'object' && typeof path.path === 'string') {
      // node:sqlite 允许 (optionsObject) 单参形态。
      return new DatabaseSync(mapGuestTmpPath(path.path), ...rest);
    }
    return new DatabaseSync(mapGuestTmpPath(path), ...rest);
  };
  try {
    Object.defineProperty(sqlite.DatabaseSync, 'name', { value: 'DatabaseSync', configurable: true });
  } catch {
    // 非关键。
  }
}

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

// 预构建原生工具（rg/fd/jq 等）位于 control 目录下的 toolchains/native-tools/。
// 它们是 Android 内核可直接执行的 ELF：动态链接的走修补 glibc loader 车道，
// 静态链接的直接 exec。识别基于目录与 ELF 头，不按工具名白名单。
function nativeToolDirectory() {
  return hostControl ? path.join(hostControl, 'toolchains', 'native-tools') : '';
}

function elfProgramInterpreter(fd) {
  // ELF64 小端。读 program headers 找 PT_INTERP，返回其描述的字符串或 ''。
  const header = Buffer.alloc(64);
  if (fs.readSync(fd, header, 0, 64, 0) !== 64) return '';
  if (header[0] !== 0x7f || header[1] !== 0x45 || header[2] !== 0x4c || header[3] !== 0x46) return '';
  if (header[4] !== 2 /* ELFCLASS64 */ || header[5] !== 1 /* ELFDATA2LSB */) return '';
  const e_phoff = Number(header.readBigUInt64LE(32));
  const e_phentsize = header.readUInt16LE(54);
  const e_phnum = header.readUInt16LE(56);
  if (e_phnum === 0 || e_phentsize < 56) return '';
  const phdrs = Buffer.alloc(e_phentsize * e_phnum);
  if (fs.readSync(fd, phdrs, 0, phdrs.length, e_phoff) !== phdrs.length) return '';
  for (let index = 0; index < e_phnum; index += 1) {
    const entry = phdrs.subarray(index * e_phentsize, (index + 1) * e_phentsize);
    if (entry.readUInt32LE(0) !== 3 /* PT_INTERP */) continue;
    const offset = Number(entry.readBigUInt64LE(8));
    const length = Number(entry.readBigUInt64LE(32));
    const buffer = Buffer.alloc(length);
    if (fs.readSync(fd, buffer, 0, length, offset) !== length) return '';
    return buffer.toString('utf8').replace(/\0.*$/s, '');
  }
  return '';
}

function resolveNativeToolInvocation(file, options) {
  const directory = nativeToolDirectory();
  if (!directory || !loader || !libraryPath || !compatLibrary) return null;
  let candidate = commandCandidate(file, options);
  if (!candidate) return null;
  candidate = normalizedPath(candidate);
  let stat;
  try {
    stat = fs.lstatSync(candidate);
  } catch {
    return null;
  }
  if (stat.isSymbolicLink()) {
    try {
      const resolved = normalizedPath(fs.realpathSync(candidate));
      const resolvedStat = fs.lstatSync(resolved);
      if (!resolvedStat.isFile()) return null;
      candidate = resolved;
    } catch {
      return null;
    }
  } else if (!stat.isFile()) {
    return null;
  }
  if (!isInside(candidate, directory)) return null;
  let fd;
  let interpreter = '';
  try {
    fd = fs.openSync(candidate, 'r');
    interpreter = elfProgramInterpreter(fd);
  } catch {
    return null;
  } finally {
    if (fd !== undefined) fs.closeSync(fd);
  }
  const requested = options && typeof options === 'object' ? options : {};
  const requestedEnv = requested.env && typeof requested.env === 'object' ? requested.env : process.env;
  const env = {};
  for (const [key, value] of Object.entries(requestedEnv || {})) {
    if (value === undefined) continue;
    if (key.startsWith('KITE_NODE_HOST_') || key === 'NODE_OPTIONS') continue;
    if (key === 'LD_PRELOAD' || key === 'LD_LIBRARY_PATH') continue;
    env[key] = value;
  }
  env['GLIBC_TUNABLES'] = 'glibc.pthread.rseq=0';
  const cwd = pathFromCwd(requested.cwd);
  const nativeOptions = {
    ...requested,
    cwd: cwd ? mapContainerPathToHost(cwd) : requested.cwd,
    shell: false,
    env,
  };
  if (interpreter === '') {
    // 静态 ELF：直接执行。
    return { file: candidate, prefix: [], options: nativeOptions };
  }
  // 动态 glibc ELF：loader --library-path … --preload compat target。
  return {
    file: loader,
    prefix: ['--library-path', libraryPath, '--preload', compatLibrary, candidate],
    options: nativeOptions,
  };
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
  // C 启动器形态：[loader, --library-path, X, [--preload, Y,] node, ...]。
  // node 自身 spawn(execPath) 拿到的是 loader 入口形态；npm 包装脚本 respawn
  // （spawnSync(process.execPath, [flag..., 脚本, ...])）也会走到这里。
  // 统一归一到启动器形态：loader 选项对丢弃（启动器重建），node execArgv
  // （以 - 开头的 flag）转入子进程 NODE_OPTIONS（--disable-warning 等
  // execArgv 均在 NODE_OPTIONS 白名单内，不能丢也不能交给启动器 argv），
  // 首个非 flag 的 node 真身跳过后，脚本与参数交给启动器。
  if (loader && file === loader) {
    let index = 0;
    const execFlags = [];
    while (index < normalizedArgs.length && String(normalizedArgs[index]).startsWith('-')) {
      const flag = String(normalizedArgs[index]);
      if (flag === '--library-path' || flag === '--preload') {
        index += 2;
        continue;
      }
      execFlags.push(flag);
      index += 1;
    }
    if (index < normalizedArgs.length
      && (normalizedArgs[index] === nodeBinary || String(normalizedArgs[index]).endsWith('/node'))) {
      index += 1;
    }
    const scriptArgs = normalizedArgs.slice(index);
    const rewrittenOptions = hostOptions(options);
    if (execFlags.length > 0) {
      const base = (rewrittenOptions.env && rewrittenOptions.env.NODE_OPTIONS)
        || process.env.NODE_OPTIONS || '';
      rewrittenOptions.env = {
        ...rewrittenOptions.env,
        NODE_OPTIONS: [...execFlags, base].filter(Boolean).join(' '),
      };
    }
    return {
      file: launcher,
      args: scriptArgs.map((value) => mapOptionPath(value, mapContainerPathToHost)),
      options: rewrittenOptions,
    };
  }
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
  const nativeTool = resolveNativeToolInvocation(file, options);
  if (nativeTool) {
    return {
      file: nativeTool.file,
      args: [
        ...nativeTool.prefix,
        ...normalizedArgs.map((value) => mapOptionPath(value, mapContainerPathToHost)),
      ],
      options: nativeTool.options,
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

const spawnDebug = process.env.KITE_NODE_HOST_SPAWN_DEBUG === '1';
childProcess.spawn = function kiteSpawn(file, args, options) {
  if (!Array.isArray(args)) {
    options = args;
    args = [];
  }
  const routed = options && options.shell
    ? shellRoute(shellCommand(file, args), options)
    : routeFile(file, args, options);
  if (spawnDebug) {
    process.stderr.write(`[kite-spawn] ${file} ${JSON.stringify(args).slice(0, 220)} -> ${routed.file} ${JSON.stringify(routed.args).slice(0, 220)}\n`);
  }
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
  if (spawnDebug) {
    process.stderr.write(`[kite-spawn-sync] ${file} ${JSON.stringify(args).slice(0, 220)} -> ${routed.file} ${JSON.stringify(routed.args).slice(0, 220)}\n`);
  }
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

installGuestTmpRouting();
installGuestTmpSqliteRouting();

syncBuiltinESMExports();
