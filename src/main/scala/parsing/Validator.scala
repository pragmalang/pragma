package parsing
import domain._
import scala.util._

class Validator(val st: List[HConstruct]) {
  def checkFieldValueType: Try[Unit] = Try {
    for {
      construct <- st
      if construct.isInstanceOf[HModel]
    } for {
      field <- construct.asInstanceOf[HModel].fields
      default <- field.defaultValue
    } if (field.htype != default.htype)
      throw new Exception(
        s"Invalid default value of type ${default.htype} for field of type ${field.htype}"
      )
  }
}
