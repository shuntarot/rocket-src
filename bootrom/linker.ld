/* Derived from rocket-chip bootrom/linker.ld (Apache-2.0 / BSD-3-Clause).
   Changed: .text.debug_loop added. */

SECTIONS
{
    ROM_BASE = 0x10000; /* ... but actually position independent */

    . = ROM_BASE;
    .text.start : { *(.text.start) }
    . = ROM_BASE + 0x40;
    .text.debug_loop : { *(.text.debug_loop) }
    . = ROM_BASE + 0x100;
    .rodata.dtb : { *(.rodata.dtb) }
}
