package pragma.domain

import pragma.domain.utils._
import scala.util.matching.Regex
import java.io.File

sealed trait PValue {
  val ptype: PType
}

case class PStringValue(value: String) extends PValue {
  final val ptype = PString
}

case class PIntValue(value: Long) extends PValue {
  final val ptype = PInt
}

case class PFloatValue(value: Double) extends PValue {
  final val ptype = PFloat
}

case class PBoolValue(value: Boolean) extends PValue {
  final val ptype = PBool
}

case class PDateValue(value: Date) extends PValue {
  final val ptype = PDate
}

case class PArrayValue(values: List[PValue], elementType: PType)
    extends PValue {
  final val ptype = PArray(elementType)
}

case class PFileValue(value: File, ptype: PFile) extends PValue

case class PModelValue(value: Map[String, PValue], ptype: PModel) extends PValue

case class PInterfaceValue(value: Map[String, PValue], ptype: PInterface)
    extends PValue

trait PFunctionValue extends PValue {
  val ptype = PAny
}

case class ExternalFunction(id: String, filePath: String)
    extends PFunctionValue
    with Identifiable

case class POptionValue(value: Option[PValue], valueType: PType)
    extends PValue {
  final val ptype = POption(valueType)
}

case class RegexLiteral(payload: Regex, position: Option[PositionRange])
    extends Positioned
