package setup.schemaGenerator

import domain._
import sangria.ast._
import sangria.macros._

trait ApiSchemaGenerator {
  import ApiSchemaGenerator._
  def build(as: SchemaBuildOutput): Either[SyntaxTree, Document]
}

object ApiSchemaGenerator {
  sealed trait InputKind
  object ObjectInput extends InputKind
  object ReferenceInput extends InputKind
  object OptionalInput extends InputKind

  sealed trait BuiltinGraphQlType
  object EqInput extends BuiltinGraphQlType
  object WhereInput extends BuiltinGraphQlType
  object OrderByInput extends BuiltinGraphQlType
  object OrderEnum extends BuiltinGraphQlType
  object RangeInput extends BuiltinGraphQlType
  object LogicalFilterInput extends BuiltinGraphQlType
  object FilterInput extends BuiltinGraphQlType
  object MultiRecordEvent extends BuiltinGraphQlType
  object SingleRecordEvent extends BuiltinGraphQlType
  object AnyScalar extends BuiltinGraphQlType
  object IntScalar extends BuiltinGraphQlType
  object FloatScalar extends BuiltinGraphQlType
  object StringScalar extends BuiltinGraphQlType
  object BooleanScalar extends BuiltinGraphQlType
  object IDScalar extends BuiltinGraphQlType

  case class TypeFromSchema(
      typeDef: TypeDefinition,
      isEmptyList: Boolean,
      isNonEmptyList: Boolean,
      isOptional: Boolean,
      tpe: Type
  )

  trait SchemaBuildOutput
  case object AsDocument extends SchemaBuildOutput
  case object AsSyntaxTree extends SchemaBuildOutput

  def default(syntaxTree: SyntaxTree) = DefaultApiSchemaGenerator(syntaxTree)

  def typeBuilder[T](
      typeNameCallback: T => String
  )(
      t: T,
      isOptional: Boolean,
      isList: Boolean
  ) =
    isOptional match {
      case true if isList  => ListType(NamedType(typeNameCallback(t)))
      case true if !isList => NamedType(typeNameCallback(t))
      case false if isList =>
        NotNullType(ListType(NamedType(typeNameCallback(t))))
      case false if !isList => NotNullType(NamedType(typeNameCallback(t)))
    }

  def builtinTypeName(t: BuiltinGraphQlType): String = t match {
    case MultiRecordEvent   => "MultiRecordEvent"
    case OrderEnum          => "OrderEnum"
    case SingleRecordEvent  => "SingleRecordEvent"
    case WhereInput         => "WhereInput"
    case EqInput            => "EqInput"
    case OrderByInput       => "OrderByInput"
    case FilterInput        => "FilterInput"
    case LogicalFilterInput => "LogicalFilterInput"
    case RangeInput         => "RangeInput"
    case AnyScalar          => "Any"
    case IntScalar          => "Int"
    case StringScalar       => "String"
    case BooleanScalar      => "Boolean"
    case FloatScalar        => "Float"
    case IDScalar           => "ID"
  }

  def builtinType(
      t: BuiltinGraphQlType,
      isOptional: Boolean = false,
      isList: Boolean = false
  ): Type = typeBuilder(builtinTypeName)(t, isOptional, isList)

  def outputType(
      model: HModel,
      isOptional: Boolean = false,
      isList: Boolean = false,
      nameTransformer: String => String = identity
  ): Type =
    typeBuilder((model: HModel) => nameTransformer(model.id))(
      model,
      isOptional,
      isList
    )

  def inputKindSuffix(kind: InputKind) = kind match {
    case ObjectInput    => "ObjectInput"
    case OptionalInput  => "OptionalInput"
    case ReferenceInput => "ReferenceInput"
  }

  def notificationTypeName(modelId: String): String =
    s"${modelId.capitalize}Notification"
  def notificationTypeName(model: HModel): String =
    notificationTypeName(model.id)

  def inputTypeName(modelId: String)(kind: InputKind): String =
    modelId.capitalize + inputKindSuffix(kind)
  def inputTypeName(model: HModel)(kind: InputKind): String =
    inputTypeName(model.id)(kind)

  lazy val buitlinGraphQlTypeDefinitions =
    gql"""
      input EqInput {
        field: String!
        value: Any!
      }
      
      input WhereInput {
        filter: LogicalFilterInput
        orderBy: OrderByInput
        range: RangeInput
        first: Int
        last: Int
        skip: Int
      }
      
      input OrderByInput {
        field: String!
        order: OrderEnum
      }
      
      enum OrderEnum {
        DESC
        ASC
      }
      
      input RangeInput {
        before: ID!
        after: ID!
      }
    
      input LogicalFilterInput {
        AND: [LogicalFilterInput]
        OR: [LogicalFilterInput]
        predicate: FilterInput
      }
      
      input FilterInput {
        eq: EqInput
      }
      
      enum MultiRecordEvent {
        CREATE
        UPDATE
        READ
        DELETE
      }
      
      enum SingleRecordEvent {
        UPDATE
        READ
        DELETE
      }
      
      scalar Any
      scalar Int
      scalar Float
      scalar String
      scalar Boolean
      scalar ID
      """.definitions.toList
}
