package cpuss

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile._
import freechips.rocketchip.subsystem._

class HNSSubsystem(implicit p: Parameters) extends BaseSubsystem
  with HasTiles
{
  def coreMonitorBundles = tiles.map {
    case r: RocketTile => r.module.core.rocketImpl.coreMonitorBundle
  }.toList

  override lazy val module = new HNSSubsystemModuleImp(this)
}

class HNSSubsystemModuleImp[+L <: HNSSubsystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
  with HasTilesModuleImp
