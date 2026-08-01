#include <asm/unistd.h>

/*
 * 保留 x0-x6 中调用者真实传入的 syscall 参数，只使用 caller-saved x8/x9 做分流。
 * 普通 syscall 尾跳到 glibc 原实现，避免 C 可变参数包装读取不存在参数的未定义行为。
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
    adrp x8, :got:kite_real_syscall
    ldr x8, [x8, :got_lo12:kite_real_syscall]
    ldr x9, [x8]
    cbz x9, kite_syscall_enosys
    br x9
    .size syscall, .-syscall

    .section .note.GNU-stack,"",%progbits
