/* Releases the Pragma daemon docker image and the CLI binary
   (Requires you to login to Dockerhub and Github)
 */

os.proc(
    "sbt",
    "clean;daemon/docker:publish; cli/graalvm-native-image:packageBin"
  )
  .call(cwd = os.pwd)

val releaseDir = os.pwd / "target" / "pragma-release"

os.makeDir(releaseDir)

os.proc("git", "clone", "https://github.com/pragmalang/releases.git")
  .call(cwd = releaseDir)

os.copy(
  os.pwd / "cli" / "target" / "graalvm-native-image" / "pragma",
  releaseDir / "releases" / "linux" / "pragma",
  replaceExisting = true
)

os.copy(
  os.pwd / "cli" / "src" / "main" / "resources" / "docker-compose.yml",
  releaseDir / "releases" / "pragma-docker-compose.yml",
  replaceExisting = true
)

val releasesStatus =
  os.proc("git", "status", "-s").call(cwd = releaseDir / "releases").out.lines

if (releasesStatus.length > 1) {
  os.proc("git", "commit", "-m", ".", "-a").call(cwd = releaseDir / "releases")
  os.proc("git", "push", "origin", "master").call(cwd = releaseDir / "releases")
} else
  println("No changes on `releases`. Nothing was committed or pushed.")

os.remove.all(releaseDir)
