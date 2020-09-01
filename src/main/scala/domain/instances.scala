package domain

import cats._

package object instances {

  implicit val pmodelEq = new Eq[PModel] {
    override def eqv(x: PModel, y: PModel): Boolean = x.equals(y)
  }

  implicit val syntaxTreeEq = new Eq[SyntaxTree] {
    override def eqv(x: SyntaxTree, y: SyntaxTree): Boolean =
      x.imports == y.imports && x.models == y.models && x.enums == y.enums && x.permissions == y.permissions && x.config == y.config
  }

}
