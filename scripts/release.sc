/* Releases the Pragma daemon docker image and the CLI binary
   (Requires you to login to Dockerhub and Github)
 */

import os._
import scala.io.StdIn

@main
def release(
    d: Boolean @doc(
      "Deletes the Git tag extracted from build.sbt if it exists"
    ) = false
) = {
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

  if (version.matches(versionRegex)) {
    if (d) {
      println(
        s"Make sure to delete both the $version release and tag from GitHub"
      )
      val yes = (StdIn.readLine("Did you delete them? (y/n): ")) match {
        case "y" => true
        case "Y" => true
        case _   => false
      }

      if(yes) {
        println(s"Deleting tag $version locally")
        proc("git", "tag", "-d", version).call(pwd)
      } else {
        println("Go delete them before you run the script next time. Unbelievable! Kidding, not mad, I just can't do it unless you lie.")
        sys.exit(1)
      }
    } else ()
    println(s"Creating tag $version locally")
    proc("git", "tag", "-a", version, "-m", "\"Automated release\"").call(pwd)
    println(s"Pushing tag $version to origin (origin/$version)")
    println(s"git push origin $version")
    proc("git", "push", "origin", version).call(pwd)
    println(s"git push origin master")
    proc("git", "push", "origin", "master").call(pwd)
    ()
  } else {
    println(
      s"Invalid version `${version.tail}`, probably a bug with release.sc, please investigate it."
    )
    sys.exit(1)
  }
}
