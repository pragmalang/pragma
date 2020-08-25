package running

import pdi.jwt.{Jwt, JwtAlgorithm}
import spray.json._
import RunningImplicits._

case class JwtPayload(userId: JsValue, role: String) {
  def encode = JwtPaylod.encode(this)
}

object JwtPaylod {
  val secret = sys.env("PRAGMA_SECRET")
  def decode(token: String) =
    Jwt
      .decodeRawAll(token, secret, Seq(JwtAlgorithm.HS256))
      .map(_._2.parseJson.convertTo[JwtPayload])

  def encode(jwtPayload: JwtPayload) =
    Jwt.encode(jwtPayload.toJson.toString, secret, JwtAlgorithm.HS256)
}