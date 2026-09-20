#include <asm/unistd.h>

/*
 * 保留 x0-x6 中调用者真实传入的 syscall 参数，只使用 caller-saved x8/x9 做分流。
 * 普通 syscall 尾跳到 glibc 原实现，避免 C 可变参数包装读取不存在参数的未定义行为。
 *
 * 带路径参数的 syscall（mkdirat/openat/newfstatat 等）先进 C 重写层：宿主车道
 * 进程里 Rust/静态二进制的文件操作直接内联 syscall，不经 glibc 符号；这些调用
 * 的 "/tmp" 前缀路径必须与 C 符号拦截层（KITE_GLIBC_HOST_GUEST_TMP）同样重写。
 * C 层按 syscall 号精确转发（参数个数由号决定，不做盲读）。
 */

    .text
    .p2align 2
    .global syscall
    .type syscall, %function
syscall:
#ifdef __NR_io_uring_setup
    mov x8, #__NR_io_uring_setup
    cmp x0, x8
    b.eq kite_syscall_enosys
#endif
#ifdef __NR_clone3
    mov x8, #__NR_clone3
    cmp x0, x8
    b.eq kite_syscall_enosys
#endif
    /* 路径型 syscall 分流：x1 是相对/绝对路径指针（dirfd 在 x0 语义之外的首参）。 */
    mov x9, x0                      /* x9 = syscall 号 */
    cmp x9, #__NR_mkdirat
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_mknodat
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_unlinkat
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_symlinkat
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_linkat
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_renameat
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_renameat2
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_openat
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_openat2
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_newfstatat
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_readlinkat
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_faccessat
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_faccessat2
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_statx
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_fchmodat
    b.eq kite_syscall_path_bridge
    cmp x9, #__NR_fchownat
    b.eq kite_syscall_path_bridge
1:
    adrp x8, :got:kite_real_syscall
    ldr x8, [x8, :got_lo12:kite_real_syscall]
    ldr x9, [x8]
    cbz x9, kite_syscall_enosys
    br x9
    .size syscall, .-syscall

/*
 * 入口约定：x0 = syscall 号，x1..x5 = 原 syscall a1..a5，与 C 调用约定一致，
 * 直接尾跳 kite_syscall_path（number, a1..a5）；C 侧重写路径后转发真实 syscall。
 */
kite_syscall_path_bridge:
    b kite_syscall_path
    .size kite_syscall_path_bridge, .-kite_syscall_path_bridge

    .section .note.GNU-stack,"",@progbits
