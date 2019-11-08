package setup

import domain.HModel
import domain.primitives.HStringValue
import scala.collection.mutable
import org.atteo.evo.inflector.English

object Pluralizer {
  private val cache = mutable.Map.empty[String, String]

  def pluralize(modelId: String): String = {
    val candidate = English.plural(modelId)
    val plural =
      if (modelId != candidate) candidate
      else if (modelId.endsWith("s")) modelId + "es"
      else modelId + "s"

    cache.getOrElseUpdate(key = modelId, op = plural)
  }

  def pluralize(model: HModel): String = {
    model.directives.find(d => d.id == "plural") match {
      case None => pluralize(model.id)
      case Some(value) =>
        value.args.value("name").asInstanceOf[HStringValue].value
    }
  }
}
