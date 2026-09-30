// Derived from rocket-chip src/main/scala/devices/tilelink/BootROM.scala
// (SiFive, Apache-2.0). Changed: the reset vector is not driven here.

package cpuss

import chisel3._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.subsystem.{BaseSubsystem, HasTiles, TLBusWrapperLocation}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink.{TLROM, BootROMParams}
import freechips.rocketchip.prci.ClockSinkDomain

import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}

object HNSBootROM {
  /** Replace reset vector in rocketchip BootROM.attach
    * reset vector is selected by boot_mode pin
    */
  def attach(params: BootROMParams, subsystem: BaseSubsystem with HasTiles, where: TLBusWrapperLocation)
            (implicit p: Parameters): TLROM = {
    val tlbus = subsystem.locateTLBusWrapper(where)
    val bootROMDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    bootROMDomainWrapper.clockNode := tlbus.fixedClockNode

    lazy val contents = {
      val romdata = Files.readAllBytes(Paths.get(params.contentFileName))
      val rom = ByteBuffer.wrap(romdata)
      rom.array() ++ subsystem.dtb.contents
    }

    val bootrom = bootROMDomainWrapper {
      LazyModule(new TLROM(params.address, params.size, contents, true, tlbus.beatBytes))
    }

    bootrom.node := tlbus.coupleTo("bootrom"){ TLFragmenter(tlbus) := _ }
    bootrom
  }
}
