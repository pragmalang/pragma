package domain

import cats._

package object instances {

  implicit def pmodelEq = new Eq[PModel] {
    override def eqv(x: PModel, y: PModel): Boolean = x.equals(y)
  }

  implicit def syntaxTreeEq = new Eq[SyntaxTree] {
    override def eqv(x: SyntaxTree, y: SyntaxTree): Boolean =
      x.imports == y.imports && x.models == y.models && x.enums == y.enums && x.permissions == y.permissions && x.config == y.config
  }

}
