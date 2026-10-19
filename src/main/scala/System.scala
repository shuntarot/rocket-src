package cpuss

import chisel3._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.amba.apb.APBParameters
import freechips.rocketchip.util._
import freechips.rocketchip.system._
import freechips.rocketchip.tilelink.{TLBusWrapperConnection, TLBusWrapperTopology}

//============================================================
// Config
//============================================================

class MiniRocketConfig extends Config(
  new WithMiniAPBDebugRegs ++
  new freechips.rocketchip.subsystem.WithDebugAPB ++
  new WithMiniNoCache ++
  new WithMiniMemPort (0x0080000000L, 0x0080000000L) ++
  new WithMiniMMIOPort(0x20000L, 0x10000L) ++
  new WithNoSlavePort ++
  new WithMiniNoInterrupts ++
  new WithNSmallCores(1) ++
  new WithMiniBusTopology ++
  new BaseConfig
)

// The debug module on APB carries a block of extra registers at 0xF00. Its
// default is empty, and rocket-chip v1.6 fails to build an empty register
// map, so one read-only word (0) is put there. The DMI registers are at
// 0x000 - 0x1FF and are not touched.
class WithMiniAPBDebugRegs extends Config((site, here, up) => {
  case freechips.rocketchip.devices.debug.APBDebugRegistersKey =>
    Map(0 -> Seq(freechips.rocketchip.regmapper.RegField.r(32, 0.U(32.W))))
})

class WithMiniMemPort(b: BigInt, n: BigInt) extends Config((site, here, up) => {
  case ExtMem => up(ExtMem, site).map(
    x => x.copy(master = x.master.copy(base = b, size = n, idBits = 6)))
})

class WithMiniMMIOPort(b: BigInt, n: BigInt) extends Config((site, here, up) => {
  case ExtBus => up(ExtBus, site).map(x => x.copy(base = b, size = n))
})

// No data cache. Without a coherence manager the dcache must be a DTIM
// (scratchpad), and all other data accesses are uncached. ICache is kept.
class WithMiniNoCache extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r => r.copy(
    dcache = r.dcache.map(_.copy(nSets = 64, nWays = 1, scratch = Some(0x40000000L)))) // 4KB DTIM
  }
})

// No external interrupts, no PLIC and no CLINT
class WithMiniNoInterrupts extends Config((site, here, up) => {
  case NExtTopInterrupts => 0
  case PLICKey => None
  case CLINTKey => None
})

// sbus + cbus only (no fbus, pbus and mbus)
case class MiniBusTopologyParams(
  sbus: SystemBusParams,
  cbus: PeripheryBusParams,
  sbusToCbusXType: ClockCrossingType = NoCrossing
) extends TLBusWrapperTopology(
  instantiations = List((SBUS, sbus), (CBUS, cbus)),
  connections = List((SBUS, CBUS, TLBusWrapperConnection.crossTo(sbusToCbusXType, Some(true))))
)

class WithMiniBusTopology extends Config((site, here, up) => {
  case TLNetworkTopologyLocated(InSubsystem) => List(
    MiniBusTopologyParams(
      sbus = site(SystemBusKey),
      cbus = site(ControlBusKey),
      sbusToCbusXType = site(SbusToCbusXTypeKey)))
})

//============================================================
// System
//============================================================

class MiniRocketSystem(implicit p: Parameters) extends MiniRocketSubsystem
    with CanHaveMasterAXI4MemPort
    with CanHaveMasterAXI4MMIOPort
{
  val bootROM  = p(BootROMLocated(location)).map { MiniRocketBootROM.attach(_, this, CBUS) }

  val resetVectorSource = BundleBridgeSource[UInt]()
  tileResetVectorNexusNode := resetVectorSource // override reset_vector

  override lazy val module = new MiniRocketSystemModuleImp(this)
}

class MiniRocketSystemModuleImp[+L <: MiniRocketSystem](_outer: L) extends MiniRocketSubsystemModuleImp(_outer)
    with DontTouch
{
    // boot_mode 0: jump to DRAM, 1: wait for debugger in BootROM loop
    val boot_mode = IO(Input(Bool()))
    outer.resetVectorSource.bundle := Mux(boot_mode, 0x10040.U, 0x10000.U)
}

//============================================================
// Top for FPGA
//============================================================

/** MiniRocketSystem as a top-level module.
  * Port names are the same as MiniRocketSystem. Only the debug resets,
  * which are abstract resets in rocket-chip, are made concrete (Bool).
  *
  * The debug module is on APB (WithDebugAPB): its DMI register n is at byte
  * address n * 4, on 12 address bits, and it runs on debug_apb_clock and
  * debug_apb_reset. Only the signals a plain APB master drives are ports:
  * pprot and pstrb are fixed, and pslverr is left out (the debug module
  * reports its errors in its own registers).
  */
class MiniRocket(implicit p: Parameters) extends Module {
  val ldut = LazyModule(new MiniRocketSystem)
  val dut = Module(ldut.module)

  val mem_axi4 = IO(chiselTypeOf(ldut.mem_axi4.getWrappedValue))
  mem_axi4 <> ldut.mem_axi4
  val mmio_axi4 = IO(chiselTypeOf(ldut.mmio_axi4.getWrappedValue))
  mmio_axi4 <> ldut.mmio_axi4

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

  val meip = IO(chiselTypeOf(dut.meip.get))
  dut.meip.get := meip
  val boot_mode = IO(Input(Bool()))
  dut.boot_mode := boot_mode
}
