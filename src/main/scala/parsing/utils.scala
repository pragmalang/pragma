package parsing.utils

import domain.{SyntaxTree, PModelField, PReference}
import domain.PModel

case class DependencyGraph(st: SyntaxTree) {

  val pairs = for {
    model <- st.models.values.toList
    // Only matches HReference, not HOption[HReference]
    PModelField(_, PReference(refId), _, _, _) <- model.fields
  } yield (model.id, refId)

  def depsOf(modelId: String): List[String] = pairs collect {
    case (m, n) if m == modelId => n
  }

  def depsOf(model: PModel): List[String] = depsOf(model.id)

  val circularDeps = pairs filter {
    case (model1, model2) => depsOf(model2) contains model1
  }

}
