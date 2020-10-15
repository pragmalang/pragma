# Working On Pragma

Pragma is a Scala 2.13 project built using SBT. It's divided into three subprojects:
* `core`: where the domain abstractions and parsing logic is kept alongside any shared logic between other subprojects. All other subprojects depend `core`.
* `daemon`: where the serving and project management logic lives. It needs instances for Postgres and Openwhisk to be running; it's meant to be running alongside in the background them while using Pragma.
* `cli`: where the communication with the `daemon`, and the loading of user files is done.

It's highly recommended to be familiar with [Cats](https://typelevel.org/cats/) and [Cats Effect](https://typelevel.org/cats-effect/) before starting to work on the daemon. 

### Setup
Pragma has been developed using VSCode with the Metals extension on Linux. For it all to work, it requires a JDK, Scala, SBT, and Ammonite for some scripts. Use this script to quickly install them:
```sh
curl -Lo cs https://git.io/coursier-cli-linux && chmod +x cs && ./cs setup
```

## Testing
Running the daemon using `sbt run` requires the following environment variables:
```sh
export DAEMON_HOSTNAME=localhost && \
export DAEMON_PORT=3040 && \
export DAEMON_PG_URI='jdbc:postgresql://localhost:5432/test'  && \
export DAEMON_PG_USER='test'  && \
export DAEMON_WSK_API_HOST='localhost:3233'  && \
export DAEMON_WSK_AUTH_TOKEN='23bc46b1-71f6-4ed5-8c54-816aa4f8c502:123zO3xZCLrMN6v2BKK1dXYFpXlPkccOFqm12CdAsMgRU4VrNZ9lyGVCGuMDGIwP'  && \
export DAEMON_PG_PASSWORD='test'  && \
export DAEMON_WSK_API_VERSION=1
```
You can run the daemon using `docker-compose` alongside Openwhisk with:
```sh
docker-compose up
```

* To run tests within a Docker container (for Postgres and Openwhisk):
```
sbt dockerComposeTest
```
See https://github.com/Tapad/sbt-docker-compose
> NOTE: If the docker containers cannot be started it's most likely because the port 5433 is already in use. Run `docker ps` and then run `docker kill <postgres-containe-id>` to kill the postgres container to fix it.

## Docker Build
Docker builds are performed using [SBT Native Packager](https://www.scala-sbt.org/sbt-native-packager/formats/docker.html). To build the Pragma daemon Docker image:
```
sbt "daemon/docker:publishLocal"
```

## GraalVM Native Image Generation
The native image build is performed using [SBT Native Packager](https://www.scala-sbt.org/sbt-native-packager/formats/graalvm-native-image.html) in Docker, so make sure it's installed.
Currently, only the CLI can compile to a native image. Run:
```
cli/graalvm-native-image:packageBin"
```

To generate META-INF:
```
java -agentlib:native-image-agent=config-merge-dir="./src/main/resources/META-INF/native-image/",config-write-initial-delay-secs=0 -jar "./target/scala-2.13/<jarfile>" <arguments-for-jar>
```
See:
* https://www.graalvm.org/reference-manual/native-image/Configuration/#assisted-configuration-of-native-image-builds
* https://noelwelsh.com/posts/2020-02-06-serverless-scala-services.html

> NOTE: Make sure everthing else other than this process is canceled. It needs all the memory it can get.

## Apache Bench benchmark
Run the ammonite script in `test/benchmark`:
```
amm PragmaBench.sc
```
Make sure to have the server and the database running before running the benchmark:
```
dockerComposeUp;run "dev" "./src/test/benchmark/montajlink.pragma"
```

> NOTE: Apache Bench must be installed:
>```
>sudo apt install apache2-utils
>```