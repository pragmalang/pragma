package setup.utils

import domain.HModel
import domain.primitives.HStringValue
import scala.collection.mutable
import org.atteo.evo.inflector.English

object Pluralizer {
  private val cache = mutable.Map.empty[HModel, String]

  private def pluralize(modelId: String): String = {
    val candidate = English.plural(modelId)
    val plural =
      if (modelId != candidate) candidate
      else if (modelId.endsWith("s")) modelId + "es"
      else modelId + "s"
    plural
  }

  def pluralize(model: HModel): String = {
    cache.get(model) match {
      case None => model.directives.find(d => d.id == "plural") match {
        case None => cache.getOrElseUpdate(key = model, op = pluralize(model.id))
        case Some(value) =>
          cache.getOrElseUpdate(key = model, op = value.args.value("name").asInstanceOf[HStringValue].value)
      } 
      case Some(value) => value
    }
  }
}
