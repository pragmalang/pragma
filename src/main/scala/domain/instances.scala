package domain
import cats._, implicits._
import cats.Monoid

package object instances {

  implicit def tenantMonoid = new Monoid[Tenant] {
    override def combine(x: Tenant, y: Tenant): Tenant =
      Tenant(x.id, x.rules ++ y.rules, x.roles ++ y.roles, None)
    override def empty: Tenant = Tenant("root", Nil, Nil, None)
  }

  implicit def permissionsMonoid = new Monoid[Permissions] {
    override def combine(x: Permissions, y: Permissions): Permissions =
      Permissions(
        x.globalTenant.combine(y.globalTenant),
        x.tenants ++ y.tenants
      )
    override def empty: Permissions = Permissions.empty
  }

  implicit def pconfigMonoid = new Monoid[PConfig] {
    override def combine(x: PConfig, y: PConfig): PConfig =
      PConfig(x.values ++ y.values, None)
    override def empty: PConfig = PConfig(Nil, None)
  }

  implicit def syntaxTreeMonoid = new Monoid[SyntaxTree] {
    override def combine(x: SyntaxTree, y: SyntaxTree): SyntaxTree = SyntaxTree(
      x.imports ++ y.imports,
      x.models ++ y.models,
      x.enums ++ y.enums,
      x.permissions.combine(y.permissions),
      for {
        xConfig <- x.config
        yConfig <- y.config
      } yield xConfig.combine(yConfig)
    )
    override def empty: SyntaxTree = SyntaxTree.empty
  }

  implicit def pmodelEq = new Eq[PModel] {
    override def eqv(x: PModel, y: PModel): Boolean = x.equals(y)
  }

  implicit def syntaxTreeEq = new Eq[SyntaxTree] {
    override def eqv(x: SyntaxTree, y: SyntaxTree): Boolean =
      x.imports == y.imports && x.models == y.models && x.enums == y.enums && x.permissions == y.permissions && x.config == y.config
  }

}
