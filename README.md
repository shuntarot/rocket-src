## Purpose

Generate two rocket-chip (v1.6) tops for FPGA:

- `MiniRocket.v`, for the uharness example
  [examples/hw/rocket](https://github.com/shuntarot/uharness/tree/main/examples/hw/rocket):
  one RV64IMAC core, no VM, FPU or data cache.
- `LinuxRocket.v`: one core that boots Linux (below).

```
git submodule update --init --recursive
make                          # both (needs a JDK)
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

Licensed under Apache-2.0.
