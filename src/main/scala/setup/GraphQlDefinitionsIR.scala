package setup

import sangria.schema._

import domain._
import primitives._
import utils._

import sangria.validation.ValueCoercionViolation
import sangria.marshalling.DateSupport
import sangria.ast.StringValue

import com.github.nscala_time.time.Imports._
import org.joda.time.format.ISODateTimeFormat
import scala.util.{Success, Failure, Try}

case class GraphQlDefinitionsIR(syntaxTree: SyntaxTree) {

  def types = syntaxTree.models.map(hShape(_)) ++ syntaxTree.enums.map(hEnum(_))

  def hType(ht: HType): OutputType[Any] = ht match {
    case pt: PrimitiveType => primitiveType(pt)
    case s: HShape         => hShape(s)
    case HReference(id) =>
      hType(
        (syntaxTree.models ++ syntaxTree.enums)
          .find(model => model.id == id)
          .get
      )
    case e: HEnum => hEnum(e)
    // case HSelf => HSelf()
  }

  def hEnum(e: HEnum) =
    EnumType(
      name = e.id,
      values = e.values.map(v => EnumValue(name = v, value = v))
    )

  def hShape(s: HShape): ObjectType[Any, Any] = s match {
    case HModel(id, fields, _, _) =>
      ObjectType(
        name = id,
        fields = fields.map(hShapeField)
      )
    case HInterface(id, fields, _) =>
      ObjectType(
        name = id,
        fields = fields.map(hShapeField)
      )
  }

  def hShapeField(f: HShapeField): Field[Any, Any] = f match {
    case HModelField(id, htype, _, _, _) =>
      Field(
        name = id,
        fieldType = hType(htype),
        resolve = ctx => Value(ctx)
      )
    case HInterfaceField(id, htype, _) =>
      Field(
        name = id,
        fieldType = hType(htype),
        resolve = ctx => Value(ctx)
      )
  }

  def primitiveType(t: PrimitiveType): OutputType[Any] = t match {
    case HArray(ht)  => ListType(hType(ht))
    case HBool       => BooleanType
    case HDate       => DateTimeType
    case HFloat      => FloatType
    case HInteger    => IntType
    case HString     => StringType
    case HOption(ht) => OptionType(hType(ht))
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
