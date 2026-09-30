// Derived from rocket-chip bootrom/bootrom.S (Apache-2.0 / BSD-3-Clause).
// Changed: _hang removed, _debug_loop added.

#define DRAM_BASE   0x80000000

.section .text.start, "ax", @progbits
.option norvc
.globl _start
_start:
  csrwi 0x7c1, 0 // disable chicken bits
  li s0, DRAM_BASE
  csrr a0, mhartid
  la a1, _dtb
  jr s0

.section .text.debug_loop, "ax", @progbits
.globl _debug_loop
_debug_loop:
  j _debug_loop

.section .rodata.dtb, "a", @progbits
.globl _dtb
.align 5, 0
_dtb:
.ascii "DTB goes here"
