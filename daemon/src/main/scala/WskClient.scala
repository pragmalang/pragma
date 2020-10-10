import org.http4s._, client._

class WskClient[M[_]](val httpClient: Client[M]) {

  /**
    * Creates an OpenWhisk action
    *
    * @param actionName
    * @param actionCode Can be a base64-encoded string
    */
  def createAction(actionName: String, actionCode: String) = ???
}
