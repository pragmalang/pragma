package parsing.utils

import domain.{SyntaxTree, PModelField, PReference}

case class DependencyGraph(st: SyntaxTree) {

  val pairs = for {
    model <- st.models
    // Only matches HReference, not HOption[HReference]
    PModelField(_, PReference(refId), _, _, _) <- model.fields
  } yield (model.id, refId)

  def depsOf(id: String) = pairs collect {
    case (m, n) if m == id => n
  }

  val circularDeps = pairs filter {
    case (model1, model2) => depsOf(model2) contains model1
  }

}
