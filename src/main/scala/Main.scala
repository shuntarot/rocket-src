package cpuss

import firrtl.stage.RunFirrtlTransformAnnotation

object Emit {
    // Emit plain verilog without randomization preambles and source locators
    def apply(gen: => chisel3.RawModule, args: Array[String]): Unit = {
      val emitArgs = Array(
        "--emission-options=disableMemRandomization,disableRegisterRandomization"
      )
      (new chisel3.stage.ChiselStage).emitVerilog(
        gen,
        emitArgs ++ args,
        Seq(
          RunFirrtlTransformAnnotation(new RemoveSimOnly),
          RunFirrtlTransformAnnotation(new RemoveSourceInfo)
        )
      )
    }
}

object Main extends App {
    implicit val p = new MiniRocketConfig()
    Emit(new MiniRocket(), args)
}

object LinuxMain extends App {
    implicit val p = new LinuxRocketConfig()
    Emit(new LinuxRocket(), args)
}
