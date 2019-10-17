package running
import pdi.jwt.{Jwt, JwtAlgorithm, JwtHeader, JwtClaim, JwtOptions}
import spray.json._
import spray.json.DefaultJsonProtocol._
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
    Jwt.encode(s"${jwtPayload.toJson}", secret, JwtAlgorithm.HS256)
}
