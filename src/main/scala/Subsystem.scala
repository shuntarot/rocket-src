package cpuss

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile._
import freechips.rocketchip.subsystem._

class MiniRocketSubsystem(implicit p: Parameters) extends BaseSubsystem
  with HasTiles
{
  def coreMonitorBundles = tiles.map {
    case r: RocketTile => r.module.core.rocketImpl.coreMonitorBundle
  }.toList

  override lazy val module = new MiniRocketSubsystemModuleImp(this)
}

class MiniRocketSubsystemModuleImp[+L <: MiniRocketSubsystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
  with HasTilesModuleImp
