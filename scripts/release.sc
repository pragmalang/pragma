/* Releases the Pragma daemon docker image and the CLI binary
   (Requires you to login to Dockerhub and Github)
 */

import os._

if (!exists(pwd / "build.sbt")) {
  println(
    "Please run me from the root of the Pragma project (i.e. `amm scripts/release.sc`)"
  )
  sys.exit(1)
}

println("Getting Pragma's version from SBT ...")

val version = "v" + proc(
  "sbt",
  "version"
).call(cwd = pwd)
  .out
  .lines()
  .mkString("\n")
  .reverse
  .stripTrailing
  .stripLeading
  .stripLineEnd
  .take(10)
  .reverse
  .stripLineEnd
  .take(5)

val versionRegex = "[v]{1}\\d{1,2}\\.\\d{1,2}\\.\\d{1,3}"

if(version.matches(versionRegex)) {
  println(s"Creating tag $version locally")
  proc("git", "tag", "-a", version, "-m", "\"Automated release\"").call(pwd)
  println(s"Pushing tag $version to origin (origin/$version)")
  println(s"git push origin $version")
  proc("git", "push", "origin", version).call(pwd)
  println(s"git push origin master")
  proc("git", "push", "origin", "master").call(pwd)
} else {
  println(s"Invalid version `${version.tail}`, probably a bug with release.sc, please investigate it.")
  sys.exit(1)
}