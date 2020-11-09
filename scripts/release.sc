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

println("Publishing daemon Docker image and building CLI native image...")
proc(
  "sbt",
  "clean;daemon/docker:publish; cli/graalvm-native-image:packageBin"
).call(cwd = pwd)

println("Updating releases repo...")

val releaseDir = pwd / "target" / "pragma-release"

makeDir(releaseDir)

proc("git", "clone", "https://github.com/pragmalang/releases.git")
  .call(cwd = releaseDir)

copy(
  pwd / "cli" / "target" / "graalvm-native-image" / "pragma",
  releaseDir / "releases" / "linux" / "pragma",
  replaceExisting = true
)

copy(
  pwd / "cli" / "src" / "main" / "resources" / "docker-compose.yml",
  releaseDir / "releases" / "pragma-docker-compose.yml",
  replaceExisting = true
)

val releasesStatus =
  proc("git", "status", "-s").call(cwd = releaseDir / "releases").out.lines

if (!releasesStatus.isEmpty) {
  println("Committing the following changes to `releases`:")
  println(releasesStatus.mkString("\n"))

  proc("git", "commit", "-m", ".", "-a").call(cwd = releaseDir / "releases")
  proc("git", "push", "origin", "master").call(cwd = releaseDir / "releases")
} else
  println("No changes on `releases`. Nothing was committed or pushed.")

println("Deleting cloned releases repo")
remove.all(releaseDir)

println("Done")
