# Working On Pragma

## Testing
To run tests within a Docker container (for Postgres):
```
sbt dockerComposeTest
```
See https://github.com/Tapad/sbt-docker-compose
> NOTE: If the docker containers cannot be started it's most likely because the port 5433 is already in use. Run `docker ps` and then run `docker kill <postgres-containe-id>` to kill the postgres container to fix it.

## Docker

* To build the Pragma daemon:
```
sbt "project daemon;clean;docker:publishLocal"
```

* To start a local Openwhisk instance:
```
sbt "project daemon;clean;docker:publishLocal" && docker-compose up
```
Or
```
sbt "project daemon;clean;docker:publishLocal" && bash pragma-env.sh
```

## GraalVM Native Image Generation
Requires `native-image` utility from Graal
Run `gu install native-image` to install it (`gu` comes with Graal)
Run `sbt graalvm-native-image:packageBin` to generate native binary
See: https://www.scala-sbt.org/sbt-native-packager/index.html

To generate META-INF:
```
java -agentlib:native-image-agent=config-merge-dir="./src/main/resources/META-INF/native-image/",config-write-initial-delay-secs=0 -jar "./target/scala-2.13/<pragma-jar>" dev <pragmafile>
```
See:
* https://www.graalvm.org/reference-manual/native-image/Configuration/#assisted-configuration-of-native-image-builds
* https://noelwelsh.com/posts/2020-02-06-serverless-scala-services.html

Also: Make sure everthing else other than this process is canceled. It needs all the memory it can get.

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