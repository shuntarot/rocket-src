package cpuss

import chisel3._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.system._
import freechips.rocketchip.tilelink.{TLBusWrapperConnection, TLBusWrapperTopology}

//============================================================
// Config
//============================================================

class HNSRocketConfig extends Config(
  new WithHNSNoCache ++
  new WithHNSMemPort (0x0080000000L, 0x0080000000L) ++
  new WithHNSMMIOPort(0x20000L, 0x10000L) ++
  new WithNoSlavePort ++
  new WithHNSNoInterrupts ++
  new WithNSmallCores(1) ++
  new WithHNSBusTopology ++
  new BaseConfig
)

class WithHNSMemPort(b: BigInt, n: BigInt) extends Config((site, here, up) => {
  case ExtMem => up(ExtMem, site).map(
    x => x.copy(master = x.master.copy(base = b, size = n, idBits = 6)))
})

class WithHNSMMIOPort(b: BigInt, n: BigInt) extends Config((site, here, up) => {
  case ExtBus => up(ExtBus, site).map(x => x.copy(base = b, size = n))
})

// No data cache. Without a coherence manager the dcache must be a DTIM
// (scratchpad), and all other data accesses are uncached. ICache is kept.
class WithHNSNoCache extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r => r.copy(
    dcache = r.dcache.map(_.copy(nSets = 64, nWays = 1, scratch = Some(0x40000000L)))) // 4KB DTIM
  }
})

// No external interrupts, no PLIC and no CLINT
class WithHNSNoInterrupts extends Config((site, here, up) => {
  case NExtTopInterrupts => 0
  case PLICKey => None
  case CLINTKey => None
})

// sbus + cbus only (no fbus, pbus and mbus)
case class HNSBusTopologyParams(
  sbus: SystemBusParams,
  cbus: PeripheryBusParams,
  sbusToCbusXType: ClockCrossingType = NoCrossing
) extends TLBusWrapperTopology(
  instantiations = List((SBUS, sbus), (CBUS, cbus)),
  connections = List((SBUS, CBUS, TLBusWrapperConnection.crossTo(sbusToCbusXType, Some(true))))
)

class WithHNSBusTopology extends Config((site, here, up) => {
  case TLNetworkTopologyLocated(InSubsystem) => List(
    HNSBusTopologyParams(
      sbus = site(SystemBusKey),
      cbus = site(ControlBusKey),
      sbusToCbusXType = site(SbusToCbusXTypeKey)))
})

//============================================================
// System
//============================================================

class HNSRocketSystem(implicit p: Parameters) extends HNSSubsystem
    with CanHaveMasterAXI4MemPort
    with CanHaveMasterAXI4MMIOPort
{
  val bootROM  = p(BootROMLocated(location)).map { HNSBootROM.attach(_, this, CBUS) }

  val resetVectorSource = BundleBridgeSource[UInt]()
  tileResetVectorNexusNode := resetVectorSource // override reset_vector

  override lazy val module = new HNSRocketSystemModuleImp(this)
}

class HNSRocketSystemModuleImp[+L <: HNSRocketSystem](_outer: L) extends HNSSubsystemModuleImp(_outer)
    with DontTouch
{
    // boot_mode 0: jump to DRAM, 1: wait for debugger in BootROM loop
    val boot_mode = IO(Input(Bool()))
    outer.resetVectorSource.bundle := Mux(boot_mode, 0x10040.U, 0x10000.U)
}

//============================================================
// Top for FPGA
//============================================================

/** HNSRocketSystem as a top-level module.
  * Port names are the same as HNSRocketSystem. Only the debug resets,
  * which are abstract resets in rocket-chip, are made concrete (Bool).
  */
class HNSCore(implicit p: Parameters) extends Module {
  val ldut = LazyModule(new HNSRocketSystem)
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
  val debug_clockeddmi_dmi = IO(Flipped(chiselTypeOf(debug.clockeddmi.get.dmi)))
  val debug_clockeddmi_dmiClock = IO(Input(Clock()))
  val debug_clockeddmi_dmiReset = IO(Input(Bool()))
  val debug_ndreset = IO(Output(Bool()))
  val debug_dmactive = IO(Output(Bool()))
  val debug_dmactiveAck = IO(Input(Bool()))
  debug.clock := debug_clock
  debug.reset := debug_reset
  debug.clockeddmi.get.dmi <> debug_clockeddmi_dmi
  debug.clockeddmi.get.dmiClock := debug_clockeddmi_dmiClock
  debug.clockeddmi.get.dmiReset := debug_clockeddmi_dmiReset
  debug_ndreset := debug.ndreset
  debug_dmactive := debug.dmactive
  debug.dmactiveAck := debug_dmactiveAck

  val meip = IO(chiselTypeOf(dut.meip.get))
  dut.meip.get := meip
  val boot_mode = IO(Input(Bool()))
  dut.boot_mode := boot_mode
}
