package setup
import domain.SyntaxTree
import sangria.schema._
import scala.util.Try
import sangria.parser.QueryParser
import scala.language.implicitConversions
import sangria.execution.Executor
import sangria.schema.Schema
import sangria.renderer.{SchemaRenderer, SchemaFilter}
import sangria.schema.StringType
import scala.util.{Success, Failure}
import domain.HEnum
import domain.HShape
import domain.HModel
import domain.HInterface
import domain.HType
import domain.primitives.`package`.PrimitiveType
import domain.primitives.HArray
import domain.primitives.HBool
import domain.primitives.HDate
import sangria.ast.NamedType
import sangria.validation.ValueCoercionViolation
import com.github.nscala_time.time.Imports._
import sangria.marshalling.DateSupport
import org.joda.time.format.ISODateTimeFormat
import sangria.ast.StringValue
import domain.primitives.HFloat
import domain.primitives.HInteger
import domain.primitives.HString
import domain.primitives.HFunction
import domain.primitives.HOption
import sangria.schema.{ListType, OptionType}
import domain.primitives.`package`.HFile
import domain.primitives.`package`.HFileValue
import domain.utils.`package`.TypeMismatchException
import domain.HReference

trait Migrator {
  def apply(schema: Schema[Any, Any]): Try[Unit]
}

case class GraphQlDefinitionsIR(
    query: ObjectType[Any, Any],
    mutation: Option[ObjectType[Any, Any]] = None,
    subscription: Option[ObjectType[Any, Any]] = None,
    additionalTypes: List[Type with Named] = Nil,
    directives: List[Directive] = BuiltinDirectives
)
object Setup {
  implicit def parseQuery(query: String) = QueryParser.parse(query).get
  def syntaxTreeToGraphQlSchema(
      syntaxTree: SyntaxTree,
      queryType: ObjectType[Any, Any] = ObjectType(
        "Query",
        fields[Any, Any](
          Field("query", StringType, resolve = _ => "")
        )
      ),
      mutationType: Option[ObjectType[Any, Any]] = None,
      subscriptionType: Option[ObjectType[Any, Any]] = None
  ) = {
    val definitions = SyntaxTreeGraphQlConverter(
      syntaxTree,
      queryType,
      mutationType,
      subscriptionType
    ).definitions

    Schema(
      query = definitions.query,
      mutation = definitions.mutation,
      subscription = definitions.subscription
    )
  }

  def apply(
      syntaxTree: SyntaxTree,
      migrator: Migrator
  ) = {
    migrator(syntaxTreeToGraphQlSchema(syntaxTree))
    apiSchema(syntaxTree)
  }

  def apiSchema(syntaxTree: SyntaxTree): Schema[Any, Any] = ???

  def executor(schema: Schema[Any, Any]) = (query: String) => ???

  def dummyDemo = {
    val schemaAst = QueryParser.parse(
      "type User { name: String!, age: Int! }"
    )
    schemaAst match {
      case Success(value) =>
        Schema
          .buildDefinitions(value)
          .map(_ match {
            case ot: ObjectType[_, _] => ot.fieldsFn().map(_.name).toString
            case t                    => t.toString
          })
      case Failure(e) => s"Error: ${e.getMessage()}"
    }
  }
}

case class SyntaxTreeGraphQlConverter(
    syntaxTree: SyntaxTree,
    queryType: ObjectType[Any, Any],
    mutationType: Option[ObjectType[Any, Any]] = None,
    subscriptionType: Option[ObjectType[Any, Any]] = None
) {

  def definitions =
    GraphQlDefinitionsIR(
      query = queryType,
      mutation = mutationType,
      subscription = subscriptionType,
      additionalTypes = types
    )

  def types = syntaxTree.models.map(hshape(_)) ++ syntaxTree.enums.map(henum(_))

  def htype(ht: HType): OutputType[Any] = ht match {
    case pt: PrimitiveType => primitiveType(pt)
    case s: HShape         => hshape(s)
    case HReference(id) =>
      htype(syntaxTree.models.find(model => model.id == id).get)
    case e: HEnum => henum(e)
  }

  def henum(e: HEnum) =
    EnumType(
      name = e.id,
      values = e.values.map(v => EnumValue(name = v, value = v))
    )

  def hshape(s: HShape): ObjectType[Any, Any] = s match {
    case model: HModel         => ???
    case interface: HInterface => ???
  }

  def primitiveType(t: PrimitiveType): OutputType[Any] = t match {
    case HArray(ht)  => ListType(htype(ht))
    case HBool       => BooleanType
    case HDate       => DateTimeType
    case HFloat      => FloatType
    case HInteger    => IntType
    case HString     => StringType
    case HOption(ht) => OptionType(htype(ht))
    case HFile(_, _) => StringType
    case HFunction(args, returnType) =>
      throw new TypeMismatchException(
        HBool :: HDate :: HFloat :: HInteger :: HString :: Nil,
        HFunction(args, returnType)
      )
  }

  case object DateCoercionViolation
      extends ValueCoercionViolation("Date value expected")

  def parseDate(s: String) = Try(new DateTime(s, DateTimeZone.UTC)) match {
    case Success(date) ⇒ Right(date)
    case Failure(_) ⇒ Left(DateCoercionViolation)
  }

  val DateTimeType = ScalarType[DateTime](
    "DateTime",
    coerceOutput = (d, caps) ⇒
      if (caps.contains(DateSupport)) d.toDate
      else ISODateTimeFormat.dateTime().print(d),
    coerceUserInput = {
      case s: String ⇒ parseDate(s)
      case _ ⇒ Left(DateCoercionViolation)
    },
    coerceInput = {
      case StringValue(s, _, _, _, _) ⇒ parseDate(s)
      case _ ⇒ Left(DateCoercionViolation)
    }
  )
  case object FileCoercionViolation
      extends ValueCoercionViolation("File value expected")
}
