package domain

case class Relation[+K <: RelationKind](
    origin: (PModel, PModelField),
    target: (PModel, Option[PModelField]),
    name: Option[String],
    kind: RelationKind
) {
  import RelationKind._

  override def toString(): String = (name, target._2) match {
    case (Some(name), Some(toField)) =>
      s"${kind}(${name}): ${origin._1.id}/${origin._2.id} => ${target._1.id}/${toField.id})"
    case (Some(name), None) =>
      s"${kind}(${name}): ${origin._1.id}/${origin._2.id} => ${target._1.id})"
    case (None, Some(toField)) =>
      s"${kind}: ${origin._1.id}/${origin._2.id} => ${target._1.id}/${toField.id}"
    case (None, None) =>
      s"${kind}: ${origin._1.id}/${origin._2.id} => ${target._1.id}"
  }

  override def equals(that: Any): Boolean = that match {
    case that: Relation[_] => {
      val unidirectional =
        this.origin._1.id == that.origin._1.id &&
          this.origin._2.id == that.origin._2.id &&
          this.target._1.id == that.target._1.id &&
          this.target._2.map(_.id) == that.target._2.map(_.id) &&
          this.name == that.name

      val bidirectional = unidirectional ||
        this.origin._1.id == that.target._1.id &&
          Some(this.origin._2.id) == that.target._2.map(_.id) &&
          this.target._1.id == that.origin._1.id &&
          this.target._2.map(_.id) == Some(that.origin._2.id) &&
          this.name == that.name

      (this.kind, that.kind) match {
        case (OneToMany, OneToMany)                          => unidirectional
        case (ManyToMany, ManyToMany) | (OneToOne, OneToOne) => bidirectional
        case _                                               => false
      }
    }
    case _ => false
  }

  lazy val manyToManyTableName: String =
    s"${origin._2.id}_${target._2.map(_.id).get}_${name.get}"

  lazy val oneToManyFkName: String = s"${origin._1.id}_${origin._2.id}"

  lazy val originTableName = s"${'\"'}${origin._1.id}${'\"'}"

  lazy val targetTableName = s"${'\"'}${target._1.id}${'\"'}"
}

sealed trait RelationKind
object RelationKind {
  final case object ManyToMany extends RelationKind {
    override def toString() = "ManyToMany"
  }
  final case object OneToMany extends RelationKind {
    override def toString() = "OneToMany"
  }
  final case object OneToOne extends RelationKind {
    override def toString() = "OneToOne"
  }
}
object Relation {

  private def notPrimitiveNorEnum(t: PType): Boolean = t match {
    case PArray(ptype)  => notPrimitiveNorEnum(ptype)
    case POption(ptype) => notPrimitiveNorEnum(ptype)
    case _: PModel      => true
    case _: PReference  => true
    case _: PEnum       => false
    case _              => false
  }

  private def relationKind(
      from: PType,
      to: PType
  ): RelationKind = (from, to) match {
    case (PArray(_) | POption(PArray(_)), PArray(_) | POption(PArray(_))) =>
      RelationKind.ManyToMany
    case (PArray(_) | POption(PArray(_)), _) => RelationKind.OneToMany
    case (_, PArray(_) | POption(PArray(_))) => RelationKind.OneToMany
    case (_, _)                              => RelationKind.OneToOne
  }

  private def connectedFieldPath(
      relName: String,
      exclude: PModel
  )(syntaxTree: SyntaxTree): Option[(PModel, PModelField)] = {
    val modelOption = syntaxTree.models
      .filterNot(_.id == exclude.id)
      .find { model =>
        model.fields.exists(_.directives.find(_.id == "relation").isDefined)
      }

    val fieldOption = modelOption.flatMap { model =>
      model.fields.find(
        field =>
          field.directives
            .find(_.id == "relation")
            .map(_.args.value("name")) match {
            case Some(value) =>
              value.asInstanceOf[PStringValue].value == relName
            case None => false
          }
      )
    }

    (modelOption, fieldOption) match {
      case (Some(model), Some(field)) => Some(model -> field)
      case _                          => None
    }
  }

  private def innerModel(ptype: PType, syntaxTree: SyntaxTree): Option[PModel] =
    ptype match {
      case PArray(t)      => innerModel(t, syntaxTree)
      case POption(t)     => innerModel(t, syntaxTree)
      case PReference(id) => syntaxTree.modelsById.get(id)
      case model: PModel  => Some(model)
      case _              => None
    }

  private def relation[K <: RelationKind](
      relName: Option[String],
      ptype: PType,
      field: PModelField,
      model: PModel,
      syntaxTree: SyntaxTree
  ): Relation[K] = {
    val otherFieldPathOption =
      relName.flatMap(connectedFieldPath(_, model)(syntaxTree))

    (otherFieldPathOption, relName) match {
      case (Some((otherModel, otherField)), Some(relName)) =>
        Relation(
          (model, field),
          (otherModel, Some(otherField)),
          Some(relName),
          relationKind(ptype, otherField.ptype)
        ) // either `OneToOne` or `ManyToMany`.
      case _ =>
        Relation(
          (model, field),
          (innerModel(ptype, syntaxTree).get, None),
          None,
          relationKind(model, ptype)
        ) // either `OneToOne` or `OneToMany`.
    }
  }

  private def relations[K <: RelationKind](
      model: PModel,
      syntaxTree: SyntaxTree
  ): Vector[Relation[K]] =
    model.fields
      .map(field => {
        val relationName = field.directives
          .find(_.id == "relation")
          .flatMap(
            _.args
              .value("name") match {
              case PStringValue(value) => Some(value)
              case _                   => None
            }
          )
        (relationName, field, field.ptype)
      })
      .filter(pair => notPrimitiveNorEnum(pair._3))
      .map(pair => relation[K](pair._1, pair._3, pair._2, model, syntaxTree))
      .toVector

  def from[K <: RelationKind](syntaxTree: SyntaxTree): Vector[Relation[K]] =
    syntaxTree.models
      .flatMap(relations(_, syntaxTree))
      .foldLeft(Vector.empty[Relation[K]]) { // Remove equivalent `Relation`s
        case (acc, rel) if !acc.contains(rel) => acc :+ rel
        case (acc, _)                         => acc
      }
}
