package cpuss

import chisel3._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.amba.apb.APBParameters
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.system.BaseConfig

//============================================================
// Config
//============================================================

/** One Rocket core that boots Linux: RV64GC with Sv39, the FPU and the BTB,
  * 16 KB L1 caches (4 ways) with a non-blocking data cache (2 MSHRs), and the
  * CLINT. No L2: a broadcast hub keeps the L1 coherent with memory. No PLIC
  * and no external interrupts.
  *
  * Memory is 2 GB at 0x8000_0000 on one AXI4 port. Within it, 32 MB at
  * 0x8200_0000 is not cached, so the host and the core see the same bytes
  * there (a console in memory, tohost).
  */
class LinuxRocketConfig extends Config(
  new WithMiniAPBDebugRegs ++
  new freechips.rocketchip.subsystem.WithDebugAPB ++
  new WithLinuxMemPort(0x0080000000L, 0x0080000000L) ++
  new WithUncachedWindow(0x0082000000L, 0x0002000000L) ++
  new WithNoMMIOPort ++
  new WithNoSlavePort ++
  new WithLinuxNoPLIC ++
  new WithRTCDivider(10) ++
  new WithNonblockingL1(2) ++
  new WithNBigCores(1) ++
  new WithCoherentBusTopology ++
  new BaseConfig
)

class WithLinuxMemPort(b: BigInt, n: BigInt) extends Config((site, here, up) => {
  case ExtMem => up(ExtMem, site).map(
    x => x.copy(master = x.master.copy(base = b, size = n, idBits = 6)))
})

/** Part of memory that the core does not cache. */
case class UncachedWindow(base: BigInt, size: BigInt)
case object UncachedWindowKey extends Field[Option[UncachedWindow]](None)

class WithUncachedWindow(b: BigInt, n: BigInt) extends Config((site, here, up) => {
  case UncachedWindowKey => Some(UncachedWindow(b, n))
})

// The CLINT stays. No PLIC and no external interrupts.
class WithLinuxNoPLIC extends Config((site, here, up) => {
  case NExtTopInterrupts => 0
  case PLICKey => None
})

/** mtime counts at 1/n of the core clock, whatever the clock is. The RTC
  * divides the pbus frequency by the timebase; both are nominal here (the
  * device tree for Linux is written by hand, for the real clock).
  */
class WithRTCDivider(n: Int) extends Config((site, here, up) => {
  case PeripheryBusKey => up(PeripheryBusKey, site).copy(dtsFrequency = Some(BigInt(n) * 1000000))
  case DTSTimebase => BigInt(1000000)
})

//============================================================
// Memory port with an uncached window
//============================================================

/** CanHaveMasterAXI4MemPort (rocket-chip v1.6), one channel, no incoherent
  * alias, and with the window of UncachedWindowKey taken out of the cached
  * memory. The window is VOLATILE: the broadcast hub only tracks UNCACHED
  * regions, so the core does not cache it, and the host may change it.
  */
trait CanHaveLinuxMemPort { this: BaseSubsystem =>
  private val memParams = p(ExtMem).get.master
  private val window = p(UncachedWindowKey).get
  private val idBits = memParams.idBits
  private val device = new MemoryDevice

  require(window.base >= memParams.base &&
    window.base + window.size <= memParams.base + memParams.size)

  private val windowSets = AddressSet.misaligned(window.base, window.size)
  private val cachedSets =
    AddressSet.misaligned(memParams.base, window.base - memParams.base) ++
    AddressSet.misaligned(window.base + window.size,
      memParams.base + memParams.size - (window.base + window.size))

  val memAXI4Node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    slaves = Seq(
      AXI4SlaveParameters(
        address       = cachedSets,
        resources     = device.reg,
        regionType    = RegionType.UNCACHED, // cacheable
        executable    = true,
        supportsWrite = TransferSizes(1, mbus.blockBytes),
        supportsRead  = TransferSizes(1, mbus.blockBytes),
        interleavedId = Some(0)),
      AXI4SlaveParameters(
        address       = windowSets,
        regionType    = RegionType.VOLATILE, // not cached
        executable    = false,
        supportsWrite = TransferSizes(1, mbus.blockBytes),
        supportsRead  = TransferSizes(1, mbus.blockBytes),
        interleavedId = Some(0))),
    beatBytes = memParams.beatBytes)))

  mbus.coupleTo("memory_controller_port_named_axi4") {
    (memAXI4Node
      := AXI4UserYanker()
      := AXI4IdIndexer(idBits)
      := TLToAXI4()
      := TLWidthWidget(mbus.beatBytes)
      := _
    )
  }

  val mem_axi4 = InModuleBody { memAXI4Node.makeIOs() }
}

//============================================================
// System
//============================================================

class LinuxRocketSystem(implicit p: Parameters) extends MiniRocketSubsystem
    with CanHaveLinuxMemPort
{
  val bootROM  = p(BootROMLocated(location)).map { MiniRocketBootROM.attach(_, this, CBUS) }

  val resetVectorSource = BundleBridgeSource[UInt]()
  tileResetVectorNexusNode := resetVectorSource // override reset_vector

  override lazy val module = new LinuxRocketSystemModuleImp(this)
}

class LinuxRocketSystemModuleImp[+L <: LinuxRocketSystem](_outer: L) extends MiniRocketSubsystemModuleImp(_outer)
    with HasRTCModuleImp
    with DontTouch
{
    // boot_mode 0: jump to DRAM, 1: wait for debugger in BootROM loop
    val boot_mode = IO(Input(Bool()))
    outer.resetVectorSource.bundle := Mux(boot_mode, 0x10040.U, 0x10000.U)
}

//============================================================
// Top for FPGA
//============================================================

/** LinuxRocketSystem as a top-level module, with the same ports as
  * MiniRocket less the MMIO port and the interrupt inputs (no external
  * interrupts: meip and seip are tied to 0).
  */
class LinuxRocket(implicit p: Parameters) extends Module {
  val ldut = LazyModule(new LinuxRocketSystem)
  val dut = Module(ldut.module)

  val mem_axi4 = IO(chiselTypeOf(ldut.mem_axi4.getWrappedValue))
  mem_axi4 <> ldut.mem_axi4

  val resetctrl = IO(chiselTypeOf(dut.resetctrl.get))
  dut.resetctrl.get <> resetctrl

  val debug = dut.debug.get
  val debug_clock = IO(Input(Clock()))
  val debug_reset = IO(Input(Bool()))
  val debug_ndreset = IO(Output(Bool()))
  val debug_dmactive = IO(Output(Bool()))
  val debug_dmactiveAck = IO(Input(Bool()))
  debug.clock := debug_clock
  debug.reset := debug_reset
  debug_ndreset := debug.ndreset
  debug_dmactive := debug.dmactive
  debug.dmactiveAck := debug_dmactiveAck

  val apb = debug.apb.get
  val debug_apb_clock = IO(Input(Clock()))
  val debug_apb_reset = IO(Input(Bool()))
  val debug_apb_psel = IO(Input(Bool()))
  val debug_apb_penable = IO(Input(Bool()))
  val debug_apb_pwrite = IO(Input(Bool()))
  val debug_apb_paddr = IO(Input(UInt(12.W)))
  val debug_apb_pwdata = IO(Input(UInt(32.W)))
  val debug_apb_prdata = IO(Output(UInt(32.W)))
  val debug_apb_pready = IO(Output(Bool()))
  apb.clock := debug_apb_clock
  apb.reset := debug_apb_reset
  apb.psel := debug_apb_psel
  apb.penable := debug_apb_penable
  apb.pwrite := debug_apb_pwrite
  apb.paddr := debug_apb_paddr
  apb.pprot := APBParameters.PROT_DEFAULT
  apb.pwdata := debug_apb_pwdata
  apb.pstrb := ~0.U(4.W)
  debug_apb_prdata := apb.prdata
  debug_apb_pready := apb.pready

  dut.meip.foreach(_.foreach(_ := false.B))
  dut.seip.foreach(_.foreach(_ := false.B))
  val boot_mode = IO(Input(Bool()))
  dut.boot_mode := boot_mode
}
