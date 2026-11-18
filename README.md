## Purpose

Generate rocket-chip (v1.6) tops for FPGA:

- `MiniRocket.v`, for the uharness example
  [examples/hw/rocket](https://github.com/shuntarot/uharness/tree/main/examples/hw/rocket):
  one RV64IMAC core, no VM, FPU or data cache.
- `LinuxRocket.v`: one core that boots Linux (below).
- `cflush/LinuxRocket.v`: the same, with a blocking data cache that
  `cflush.d.l1` writes back, for checkpoint and restore tests (below).

```
git submodule update --init --recursive
make                          # all (needs a JDK)
make outputs/LinuxRocket.v    # or one
```

## LinuxRocket

| | |
|---|---|
| Core | one Rocket, RV64GC (`rv64imafdc`), Sv39, FPU, BTB |
| L1 | I 16 KB, D 16 KB, 4 ways, 64-byte lines; the data cache is non-blocking (2 MSHRs) |
| L2 | none; a broadcast hub keeps the L1 coherent |
| Memory | 2 GB at 0x8000_0000 on one 64-bit AXI4 port (`mem_axi4_0`) |
| Uncached window | 32 MB at 0x8200_0000, inside memory: the core does not cache it, so a host sees what the core writes there |
| Devices | CLINT at 0x200_0000, boot ROM at 0x1_0000, debug module on APB (as MiniRocket) |
| Interrupts | timer and software (CLINT) only; no PLIC |
| Timer | mtime counts at 1/10 of the core clock |

The core has no `time` CSR: reading it traps to M-mode, where the firmware
must emulate it from the CLINT's mtime.

The device tree in the boot ROM is rocket-chip's and says nothing of the
uncached window; give Linux its own.

## LinuxRocket with cflush

`cflush/LinuxRocket.v` (`LinuxRocketCFlushConfig`) is LinuxRocket with two
changes, so that a debugger can get the data cache out to memory before it
saves or restores the state:

- The data cache is blocking (no MSHRs), as in rocket-chip's default.
- `cflush.d.l1 x0` (`0xfc000073`) writes back and invalidates the whole data
  cache. It runs in M-mode, and from the debug module's program buffer.

The non-blocking data cache takes `cflush.d.l1` as a no-op, which is why the
cache changes. A blocking cache waits on every miss, so measure performance
on `LinuxRocket.v`, not on this one. The top module and its ports are the
same.

Licensed under Apache-2.0.
