# Working On Pragma

## Testing
Running the daemon natively requires the following environment variables:
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
You can run it using `docker-compose` alongside Openwhisk with:
```sh
cd /cli/src/graal
docker-compose up
```

* To run tests within a Docker container (for Postgres):
```
sbt dockerComposeTest
```
See https://github.com/Tapad/sbt-docker-compose
> NOTE: If the docker containers cannot be started it's most likely because the port 5433 is already in use. Run `docker ps` and then run `docker kill <postgres-containe-id>` to kill the postgres container to fix it.

## Docker

* To build the Pragma daemon Docker image:
```
sbt "daemon/docker:publishLocal"
```

## GraalVM Native Image Generation
The native image build is performed in Docker, so make sure it's installed.
Currently, only the CLI can compile to a native image. Run:
Run `sbt "cli/graalvm-native-image:packageBin"` to generate native binary
See: https://www.scala-sbt.org/sbt-native-packager/index.html

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
`sudo apt install apache2-utils`