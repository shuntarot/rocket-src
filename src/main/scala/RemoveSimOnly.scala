package cpuss

import firrtl._
import firrtl.annotations.ModuleTarget
import firrtl.ir._
import firrtl.options.Dependency
import firrtl.transforms.OptimizableExtModuleAnnotation

/** Remove printf / stop (chisel printf and assert) and verification statements
  * so that the emitted verilog contains only synthesizable logic.
  * Logic used only by the removed statements is cleaned up by DCE.
  * plusarg_reader is marked optimizable so that DCE can remove it once unused.
  */
class RemoveSimOnly extends Transform with DependencyAPIMigration {
  override def prerequisites = firrtl.stage.Forms.LowForm
  override def optionalPrerequisiteOf = Seq(Dependency[firrtl.transforms.DeadCodeElimination])
  override def invalidates(a: Transform) = false

  private val optimizableExtModules = Set("plusarg_reader")

  private def onStmt(s: Statement): Statement = s match {
    case _: Print | _: Stop | _: Verification => EmptyStmt
    case other => other.mapStmt(onStmt)
  }

  def execute(state: CircuitState): CircuitState = {
    val circuit = state.circuit.mapModule(_.mapStmt(onStmt))
    val annos = circuit.modules.collect {
      case m: ExtModule if optimizableExtModules(m.defname) =>
        OptimizableExtModuleAnnotation(ModuleTarget(circuit.main, m.name))
    }
    state.copy(circuit = circuit, annotations = state.annotations ++ annos)
  }
}
