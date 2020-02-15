package running

import pdi.jwt.{Jwt, JwtAlgorithm}
import spray.json._
import Implicits._

case class JwtPaylod(userId: String, role: String) {
  def encode() = JwtPaylod.encode(this)
}

object JwtPaylod {
  val secret = sys.env("HEAVENLYX_SECRET")
  def decode(token: String) =
    Jwt
      .decodeRawAll(token, secret, Seq(JwtAlgorithm.HS256))
      .map(_._2.parseJson.convertTo[JwtPaylod])

  def encode(jwtPayload: JwtPaylod) =
    Jwt.encode(jwtPayload.toJson.toString, secret, JwtAlgorithm.HS256)
}
