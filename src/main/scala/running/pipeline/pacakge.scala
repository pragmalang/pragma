package running.pipeline

import scala.util.Try

trait PipelineInput

trait PipelineOutput

trait PiplineFunction[I <: PipelineInput, O <: Try[PipelineOutput]] {
  def apply(input: I): O
}