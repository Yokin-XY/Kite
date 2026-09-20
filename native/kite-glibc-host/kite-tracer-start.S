/*
 * kite-tracer-start.S —— 诊断用途（配合 patched libc.a）：
 * glibc 的 set_robust_list 已被改为 getpid；此处逐个试探 glibc 静态 init
 * 其余依赖的系统调用，定位下一个被 App 域 zygote filter KILL 的号。
 */
    .text

.macro RAW_WRITE msg, len
    mov     x8, #64
    mov     x0, #2
    adr     x1, \msg
    mov     x2, #\len
    svc     #0
.endm

    .p2align 2
    .global _start
    .type _start, %function
_start:
    RAW_WRITE .Lm0, 7          /* [try0] */

    /* getrandom(278) */
    sub     sp, sp, #16
    mov     x8, #278
    mov     x0, sp
    mov     x1, #1
    mov     x2, xzr
    svc     #0
    add     sp, sp, #16
    RAW_WRITE .Lm1, 7          /* [try1] */

    /* rseq(293)：x0=0 注销探测 */
    mov     x8, #293
    mov     x0, xzr
    mov     x1, xzr
    mov     x2, xzr
    mov     x3, xzr
    svc     #0
    RAW_WRITE .Lm2, 7          /* [try2] */

    /* prlimit64(261) */
    mov     x8, #261
    mov     x0, xzr
    mov     x1, #3
    mov     x2, xzr
    mov     x3, xzr
    svc     #0
    RAW_WRITE .Lm3, 7          /* [try3] */

    /* futex(98)：FUTEX_WAKE(1) on 零页安全地址 */
    sub     sp, sp, #16
    str     xzr, [sp]
    mov     x8, #98
    mov     x0, sp
    mov     x1, #1
    mov     x2, xzr
    svc     #0
    add     sp, sp, #16
    RAW_WRITE .Lm4, 7          /* [try4] */

    /* madvise(233) */
    mov     x8, #233
    sub     x0, sp, #4096
    mov     x1, #4096
    mov     x2, xzr
    svc     #0
    RAW_WRITE .Lm5, 7          /* [try5] */

    /* clock_gettime(113) */
    sub     sp, sp, #16
    mov     x8, #113
    mov     x0, #0
    mov     x1, sp
    svc     #0
    add     sp, sp, #16
    RAW_WRITE .Lm6, 7          /* [try6] */

    /* mmap(222) 一页匿名 */
    mov     x8, #222
    mov     x0, xzr
    mov     x1, #4096
    mov     x2, #3
    mov     x3, #0x22
    mov     x4, #-1
    mov     x5, xzr
    svc     #0
    RAW_WRITE .Lm7, 7          /* [try7] */

    /* __libc_start_main(main, argc, argv, init=0, fini=0, rtld_fini=0, sp) */
    ldr     x0, =main
    ldp     x1, x2, [sp]
    mov     x3, xzr
    mov     x4, xzr
    mov     x5, xzr
    mov     x6, sp
    bl      __libc_start_main
    mov     x8, #93
    mov     x0, #127
    svc     #0

.Lm0: .ascii "[try0]\n"
.Lm1: .ascii "[try1]\n"
.Lm2: .ascii "[try2]\n"
.Lm3: .ascii "[try3]\n"
.Lm4: .ascii "[try4]\n"
.Lm5: .ascii "[try5]\n"
.Lm6: .ascii "[try6]\n"
.Lm7: .ascii "[try7]\n"
    .size _start, .-_start

    .section .rodata
    .p2align 3
    .global _DYNAMIC
_DYNAMIC:
    .quad 0

    .text
    .p2align 2
    .global _init
    .type _init, %function
_init:
    ret
    .size _init, .-_init
    .global _fini
    .type _fini, %function
_fini:
    ret
    .size _fini, .-_fini

    .section .note.GNU-stack,"",@progbits
