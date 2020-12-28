import os._
import scala.io.StdIn

/** Releases the Pragma daemon docker image and CLI packages
  * (Requires logging into Dockerhub and Github)
  */
@main def release(
    d: Boolean @doc("Deletes the Git tag extracted from build.sbt if it exists") = false
) = {
  if (!exists(pwd / "build.sbt")) {
    println(
      "Please run me from the root of the Pragma project (i.e. `amm scripts/release.sc`)"
    )
    sys.exit(1)
  }

  println("Getting Pragma's version from SBT ...")

  val versionRegex = "\\d{1,2}\\.\\d{1,2}\\.\\d{1,3}".r

  val version = versionRegex.findFirstIn {
    proc("sbt", "core/version").call(pwd).out.lines().init.last
  } match {
    case Some(v) => v
    case None => {
      println(
        s"Invalid version, probably a bug with release.sc. Please investigate it."
      )
      sys.exit(1)
    }
  }

  if (d) {
    println(
      s"Make sure to delete both the $version release and tag from GitHub"
    )

    val yes =
      StdIn.readLine("Did you delete them? (y/n): ").toLowerCase() == "y"

    if (yes) {
      println(s"Deleting tag $version locally")
      proc("git", "tag", "-d", version).call(pwd)
    } else {
      println(
        "Go delete them before you run the script next time. Unbelievable! Kidding, not mad. I just can't do it unless you lie."
      )
      sys.exit(1)
    }
  }

  println(s"Creating tag $version locally")
  proc("git", "tag", "-a", version, "-m", "\"Automated release\"").call(pwd)

  println(s"Pushing tag $version to origin (origin/$version)")
  println(s"git push origin $version")
  proc("git", "push", "origin", version).call(pwd)

  println(s"git push origin master")
  proc("git", "push", "origin", "master").call(pwd)

  println("Done pushing to master.")
}
