package domain

import domain.primitives._

case class Relationship(
    from: (PModel, PModelField),
    to: (PModel, Option[PModelField]),
    name: Option[String],
    kind: RelationshipKind
) {
  import RelationshipKind._

  override def toString(): String = (name, to._2) match {
    case (Some(name), Some(toField)) =>
      s"${kind}(${name}): ${from._1.id}/${from._2.id} => ${to._1.id}/${toField.id})"
    case (Some(name), None) =>
      s"${kind}(${name}): ${from._1.id}/${from._2.id} => ${to._1.id})"
    case (None, Some(toField)) =>
      s"${kind}: ${from._1.id}/${from._2.id} => ${to._1.id}/${toField.id}"
    case (None, None) =>
      s"${kind}: ${from._1.id}/${from._2.id} => ${to._1.id}"
  }

  override def equals(that: Any): Boolean = that match {
    case that: Relationship => {
      val unidirectional =
        this.from._1.id == that.from._1.id &&
          this.from._2.id == that.from._2.id &&
          this.to._1.id == that.to._1.id &&
          this.to._2.map(_.id) == that.to._2.map(_.id) &&
          this.name == that.name

      val bidirectional = unidirectional ||
        this.from._1.id == that.to._1.id &&
          Some(this.from._2.id) == that.to._2.map(_.id) &&
          this.to._1.id == that.from._1.id &&
          this.to._2.map(_.id) == Some(that.from._2.id) &&
          this.name == that.name

      (this.kind, that.kind) match {
        case (OneToMany, OneToMany)                          => unidirectional
        case (ManyToMany, ManyToMany) | (OneToOne, OneToOne) => bidirectional
        case _                                               => false
      }
    }
    case _ => false
  }
}

sealed trait RelationshipKind
object RelationshipKind {
  final case object ManyToMany extends RelationshipKind {
    override def toString() = "ManyToMany"
  }
  final case object OneToMany extends RelationshipKind {
    override def toString() = "OneToMany"
  }
  final case object OneToOne extends RelationshipKind {
    override def toString() = "OneToOne"
  }
}
object Relationship {

  private def notPrimitiveNorEnum(t: PType): Boolean = t match {
    case PArray(ptype)  => notPrimitiveNorEnum(ptype)
    case POption(ptype) => notPrimitiveNorEnum(ptype)
    case _: PModel      => true
    case _: PReference  => true
    case _: PEnum       => false
    case _              => false
  }

  private def relationshipKind(
      from: PType,
      to: PType
  ): RelationshipKind = (from, to) match {
    case (PArray(_) | POption(PArray(_)), PArray(_) | POption(PArray(_))) =>
      RelationshipKind.ManyToMany
    case (PArray(_) | POption(PArray(_)), _) => RelationshipKind.OneToMany
    case (_, PArray(_) | POption(PArray(_))) => RelationshipKind.OneToMany
    case (_, _)                              => RelationshipKind.OneToOne
  }

  private def connectedFieldPath(
      relName: String,
      exclude: PModel
  )(syntaxTree: SyntaxTree): Option[(PModel, PModelField)] = {
    val modelOption = syntaxTree.models
      .filterNot(_.id == exclude.id)
      .find { model =>
        model.fields.exists(_.directives.find(_.id == "connection").isDefined)
      }

    val fieldOption = modelOption.flatMap { model =>
      model.fields.find(
        field =>
          field.directives
            .find(_.id == "connection")
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
      case PReference(id) => Some(syntaxTree.models.find(_.id == id).get)
      case model: PModel  => Some(model)
      case _              => None
    }

  private def relationship(
      relName: Option[String],
      ptype: PType,
      field: PModelField,
      model: PModel,
      syntaxTree: SyntaxTree
  ): Relationship = {
    val otherFieldPathOption =
      relName.flatMap(connectedFieldPath(_, model)(syntaxTree))

    (otherFieldPathOption, relName) match {
      case (Some((otherModel, otherField)), Some(relName)) =>
        Relationship(
          (model, field),
          (otherModel, Some(otherField)),
          Some(relName),
          relationshipKind(ptype, otherField.ptype)
        ) // either `OneToOne` or `ManyToMany`.
      case _ =>
        Relationship(
          (model, field),
          (innerModel(ptype, syntaxTree).get, None),
          None,
          relationshipKind(model, ptype)
        ) // either `OneToOne` or `OneToMany`.
    }
  }

  private def relationships(
      model: PModel,
      syntaxTree: SyntaxTree
  ): Vector[Relationship] =
    model.fields
      .map(field => {
        val relationshipName = field.directives
          .find(_.id == "connection")
          .flatMap(
            _.args
              .value("name") match {
              case PStringValue(value) => Some(value)
              case _                   => None
            }
          )
        (relationshipName, field, field.ptype)
      })
      .filter(pair => notPrimitiveNorEnum(pair._3))
      .map(pair => relationship(pair._1, pair._3, pair._2, model, syntaxTree))
      .toVector

  def from(syntaxTree: SyntaxTree): Vector[Relationship] =
    syntaxTree.models
      .flatMap(relationships(_, syntaxTree))
      .foldLeft(Vector.empty[Relationship]) { // Remove equivalent `Relationship`s
        case (acc, rel) if !acc.contains(rel) => acc :+ rel
        case (acc, _)                         => acc
      }
}
