import ammonite.ops._
import $ivy.{`com.lihaoyi::requests:0.2.0`, `com.lihaoyi::ujson:0.7.5`}

val apiUrl = "http://localhost:3030/graphql"

val gqlHeaders = List(
  "Content-Length" -> "2661",
  "accept" -> "*/*",
  "User-Agent" -> "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.102 Safari/537.36",
  "content-type" -> "application/json",
  "Origin" -> "http://localhost:3030",
  "Sec-Fetch-Site" -> "same-origin",
  "Sec-Fetch-Mode" -> "cors",
  "Sec-Fetch-Dest" -> "empty",
  "Referer" -> apiUrl,
  "Accept-Encoding" -> "zip, deflate, br",
  "Accept-Language" -> "en-US,en;q=0.9,ar;q=0.8"
)

println("Preparing for benchmark...")

val prepQueryBody = read(pwd / "prep-query.json")

try requests.post(apiUrl, headers = gqlHeaders, data = prepQueryBody)
catch {
  case e => {
    Console.err.println(
      "Unable to prepare server for benchmark:" + e.getMessage
    )
    sys.exit(1)
  }
}

val abQuery = read(pwd / "ab-query.json")

println("Benchmark query:")
println(ujson.read(abQuery).obj("query").str)

println("Benchmark query response:")
val benchQueryRes = requests.post(
  apiUrl,
  headers = gqlHeaders,
  data = abQuery
)
println(ujson.read(benchQueryRes.text).render(2))

println("Starting Apache Bench...")

val abCmd: Array[String] =
  Array("ab", "-p", "ab-query.json", "-T", "application/json") ++
    gqlHeaders.toArray.flatMap { case (h, v) => Array("-H", s"'$h:$v'") } ++
    Array("-n", "50000", "-c", "1000", apiUrl)

%(Shellable.SeqShellable(abCmd))(pwd)
