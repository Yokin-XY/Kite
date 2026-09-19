import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

// KITE_NODE_HOST_GUEST_TMP 注入后，预载层把 fs 入口里 "/tmp" 前缀路径重写到
// 该目录；未注入时零行为。真机上该目录与 C 兼容层（KITE_GLIBC_HOST_GUEST_TMP）
// 指向同一位置，覆盖 glibc 与 libuv 直连 syscall 两条路径。

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const preload = path.join(scriptDirectory, 'kite-node-host-runtime.cjs');
const guestTmp = fs.mkdtempSync(path.join(os.tmpdir(), 'kite-guest-tmp-'));

process.env.KITE_NODE_HOST_GUEST_TMP = guestTmp;
process.env.KITE_NODE_HOST_LAUNCHER = process.execPath;
process.env.KITE_NODE_HOST_BINARY = process.execPath;
process.env.KITE_NODE_HOST_LOADER = process.execPath;
process.env.KITE_NODE_HOST_LIBRARY_PATH = '/opt/fake/lib';
process.env.NODE_OPTIONS = `--require=${preload}`;

createRequire(import.meta.url)(preload);

// 同步族
fs.mkdirSync('/tmp/kite-smoke-dir');
assert.ok(fs.statSync(path.join(guestTmp, 'kite-smoke-dir')).isDirectory());
assert.ok(fs.existsSync('/tmp/kite-smoke-dir'));
assert.equal(fs.statSync('/tmp/kite-smoke-dir').isDirectory(), true);

// 文件写入 + 重命名 + 删除
fs.writeFileSync('/tmp/kite-smoke-file', 'payload');
assert.equal(fs.readFileSync('/tmp/kite-smoke-file', 'utf8'), 'payload');
fs.renameSync('/tmp/kite-smoke-file', '/tmp/kite-smoke-file2');
assert.ok(fs.lstatSync('/tmp/kite-smoke-file2').isFile());
fs.unlinkSync('/tmp/kite-smoke-file2');
assert.equal(fs.existsSync('/tmp/kite-smoke-file2'), false);
fs.rmdirSync('/tmp/kite-smoke-dir');
assert.equal(fs.existsSync('/tmp/kite-smoke-dir'), false);

// .native 变体：realpathSync.native 在各版本都存在；stat/lstat 的随版本可选。
assert.equal(typeof fs.realpathSync.native, 'function');
assert.ok(typeof fs.statSync.native === 'function' || fs.statSync.native === undefined);
assert.ok(typeof fs.lstatSync.native === 'function' || fs.lstatSync.native === undefined);
fs.mkdirSync('/tmp/kite-native-dir');
if (typeof fs.statSync.native === 'function') {
  assert.equal(fs.statSync.native('/tmp/kite-native-dir').isDirectory(), true);
} else {
  assert.equal(fs.statSync('/tmp/kite-native-dir').isDirectory(), true);
}
assert.equal(fs.realpathSync.native('/tmp/kite-native-dir'), path.join(guestTmp, 'kite-native-dir'));
fs.rmdirSync('/tmp/kite-native-dir');

// promises 族
await fs.promises.mkdir('/tmp/kite-promises-dir');
assert.ok((await fs.promises.stat('/tmp/kite-promises-dir')).isDirectory());
await fs.promises.rmdir('/tmp/kite-promises-dir');

// 非 /tmp 前缀路径不受影响（相对路径、/workspace、其它绝对路径原样）
assert.throws(() => fs.statSync('/kite-definitely-missing-path'), /ENOENT/);
assert.throws(() => fs.accessSync('kite-relative-missing'), /ENOENT/);
// /tmpfoo 等前缀巧合路径保持原样（本机 /tmpfoo 不存在 → ENOENT，而不是建到 guest 目录）
assert.throws(() => fs.statSync('/tmpfoo-missing'), /ENOENT/);
assert.equal(fs.existsSync(path.join(guestTmp, 'foo-missing')), false);

fs.rmSync(guestTmp, { recursive: true, force: true });
console.log('kite-node-host-fs-guest-tmp: ok');
