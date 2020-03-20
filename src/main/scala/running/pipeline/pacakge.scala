package running.pipeline

import akka.stream.scaladsl.Source

trait PipelineInput

trait PipelineOutput

trait PiplineFunction[I <: PipelineInput, O <: Source[PipelineOutput, _]] {
  def apply(input: I): O
}