package running

package object errors {
  class QueryError(message: String) extends Exception(s"QueryError: $message")
}
