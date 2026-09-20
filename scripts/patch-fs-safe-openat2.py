#!/usr/bin/env python3
"""fs-safe 安卓域雷补丁：把 openat2(437) 调用现场翻译为 openat(56)。

背景（docs/architecture/ubuntu-simulation-doctrine.md 雷库 GUEST-SYSCALL-02）：
App 域 seccomp 对未知 syscall 号（含 437 openat2）直接 KILL。fs-safe 原生
模块（Rust 内联直发 syscall，不经 glibc PLT，兼容层拦不到）对 ENOSYS 也
没有降级——实测直接把错误码当 fd 用（Gateway failed to start）。

补丁方式：openclaw 2.0 的 fs-safe 两个 openat2 调用点都是同构序列：

    mov w3, #24          ; openat2 的 size 参数（openat 语义下是 mode）
    orr w8, ...          ; flags 组合（32 位，结果零扩展到 x8）
    str/stur x8, [...]   ; 把 flags 写进栈上 struct open_how
    mov w8, #437
    svc #0

    x0=dirfd、x1=pathname、x2=open_how* 在此前已就绪。

改写为（零控制流改动，指令等长替换）：

    mov w3, #0o644       ; openat 的 mode（O_CREAT 建锁文件常规权限）
    orr w8, ...          ; 不动
    mov x2, x8           ; flags 直接从寄存器进 openat 参数位
    mov x8, #56          ; __NR_openat
    svc #0

安全降级（95/5 原则，记录在纲领）：RESOLVE_BENEATH/RESOLVE_NO* 硬化字段
被丢弃，路径逃逸防护退回 openat 级别；App 域单容器边界内可接受。
open_how.mode 若非 0 则被固定 0644 取代（锁文件场景实测无影响）。

用法：python3 patch-fs-safe-openat2.py <fs-safe-native.node> [expected]
expected 缺省 2（openclaw 2.0 制品实测 2 处）。锚点校验失败立即退出不改文件。
幂等：已补丁文件计数为 0。
"""
import struct
import sys

MOV_W8_437 = 0x528036A8          # mov w8, #437
SVC_ZERO = 0xD4000001            # svc #0
MOV_W3_24 = 0x52800303           # mov w3, #24        （待替换）
STR_X8_SP = 0xF90003E8           # str  x8, [sp]      （点1 待替换）
STUR_X8_X29 = 0xF81E83A8         # stur x8, [x29,#-24]（点2 待替换）
MOV_W3_0644 = 0x52803483         # mov w3, #0o644
MOV_X2_X8 = 0xAA0803E2           # mov x2, x8
MOV_X8_56 = 0xD2800708           # mov x8, #56


def read32(data: bytes, off: int) -> int:
    return struct.unpack_from("<I", data, off)[0]


def write32(data: bytearray, off: int, ins: int) -> None:
    struct.pack_into("<I", data, off, ins)


def patch(path: str, expected: int) -> int:
    data = bytearray(open(path, "rb").read())
    count = 0
    pos = 0
    while True:
        i = struct.pack_from("<I", data, pos).hex() if False else None
        # 定位 mov w8,#437 + svc
        found = -1
        for off in range(pos, len(data) - 12, 4):
            if read32(data, off) == MOV_W8_437 and read32(data, off + 4) == SVC_ZERO:
                found = off
                break
        if found < 0:
            break
        # 布局（两变体）：[sub x2][-16|mov w3,#24][-12|mov w3,#24] … str/stur x8(-4) mov w8,#437 svc
        prev2 = read32(data, found - 4)   # str/stur x8（flags 落栈）
        w3_off = found - 16 if read32(data, found - 16) == MOV_W3_24 else (
            found - 12 if read32(data, found - 12) == MOV_W3_24 else -1)
        if prev2 not in (STR_X8_SP, STUR_X8_X29) or w3_off < 0:
            print(
                f"!! 调用点 {hex(found)} 前序指令与预期锚点不符 "
                f"(w3_off={w3_off} prev2={prev2:#x})，制品可能已更新，拒绝补丁",
                file=sys.stderr,
            )
            sys.exit(1)
        write32(data, w3_off, MOV_W3_0644)
        write32(data, found - 4, MOV_X2_X8)
        write32(data, found, MOV_X8_56)
        count += 1
        pos = found + 8
    if count:
        open(path, "wb").write(bytes(data))
    if count != expected:
        print(f"!! 计数不符: expected={expected} actual={count}", file=sys.stderr)
        sys.exit(1)
    print(f"{path}: {count} 处 openat2 已现场翻译为 openat")
    return count


def main() -> None:
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    expected = int(sys.argv[2]) if len(sys.argv) > 2 else 2
    if patch(sys.argv[1], expected) == 0:
        print("(无命中：文件可能已补丁，或制品已更新——请人工核对调用点)")


if __name__ == "__main__":
    main()
