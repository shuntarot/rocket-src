package cpuss

import firrtl._
import firrtl.ir._
import firrtl.options.Dependency

/** Remove source locators (`// @[File.scala line:col]`) from the emitted verilog.
  * They change whenever a Scala source line moves, even when the circuit does not.
  */
class RemoveSourceInfo extends Transform with DependencyAPIMigration {
  override def prerequisites = firrtl.stage.Forms.LowForm
  override def optionalPrerequisiteOf = Seq(Dependency[firrtl.transforms.DeadCodeElimination])
  override def invalidates(a: Transform) = false

  private def onStmt(s: Statement): Statement =
    s.mapInfo(_ => NoInfo).mapStmt(onStmt)

  private def onModule(m: DefModule): DefModule =
    m.mapInfo(_ => NoInfo).mapPort(_.copy(info = NoInfo)).mapStmt(onStmt)

  def execute(state: CircuitState): CircuitState =
    state.copy(circuit = state.circuit.copy(info = NoInfo).mapModule(onModule))
}
