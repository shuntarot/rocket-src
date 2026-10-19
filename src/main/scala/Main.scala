package cpuss

import firrtl.stage.RunFirrtlTransformAnnotation

object Main extends App {
    // Emit plain verilog without randomization preambles and source locators
    val emitArgs = Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
    implicit val p = new MiniRocketConfig()
    (new chisel3.stage.ChiselStage).emitVerilog(
      new MiniRocket(),
      emitArgs ++ args,
      Seq(
        RunFirrtlTransformAnnotation(new RemoveSimOnly),
        RunFirrtlTransformAnnotation(new RemoveSourceInfo)
      )
    )
}
