# Derived from rocket-chip bootrom/Makefile (Apache-2.0 / BSD-3-Clause).

bootrom_img = bootrom.img

GCC=riscv64-unknown-elf-gcc
OBJCOPY=riscv64-unknown-elf-objcopy
OBJDUMP=riscv64-unknown-elf-objdump

all: $(bootrom_img)

%.img: %.bin
	dd if=$< of=$@ bs=256 count=1

%.bin: %.elf
	$(OBJCOPY) -O binary $< $@

%.elf: %.S linker.ld
	$(GCC) -Tlinker.ld $< -nostdlib -static -Wl,--no-gc-sections -o $@

%.dis: %.elf
	$(OBJDUMP) -d $< > $@

clean:
	-rm *.elf *.dis
