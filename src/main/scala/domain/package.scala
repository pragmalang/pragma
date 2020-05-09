package domain

import utils._
import org.parboiled2.Position

trait PConstruct extends Positioned

case class PositionRange(start: Position, end: Position)

trait Positioned {
  val position: Option[PositionRange]
}

case class PImport(
    id: String,
    filePath: String,
    position: Option[PositionRange]
) extends PConstruct
    with Identifiable
    with Positioned

case class Directive(
    id: String,
    args: PInterfaceValue,
    kind: DirectiveKind,
    position: Option[PositionRange] = None
) extends Identifiable
    with Positioned

sealed trait DirectiveKind
case object FieldDirective extends DirectiveKind
case object ModelDirective extends DirectiveKind
case object ServiceDirective extends DirectiveKind

case class PConfig(values: List[ConfigEntry], position: Option[PositionRange])
    extends PConstruct {
  def getConfigEntry(key: String): Option[ConfigEntry] =
    values.find(configEntry => configEntry.key == key)
}

case class ConfigEntry(
    key: String,
    value: PValue,
    position: Option[PositionRange]
) extends Positioned
