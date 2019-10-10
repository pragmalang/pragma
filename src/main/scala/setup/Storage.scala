package setup
import sangria.ast.Document

sealed trait Storage[Request] {
  val schema: Document
  def runQuery(query: Document): Request
}

case class PrismaServer[Request](schema: Document) extends Storage[Request] {
  def runQuery(query: Document): Request = ???
}
