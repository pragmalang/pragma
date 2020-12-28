[![Tweet](https://img.shields.io/twitter/url/http/shields.io.svg?style=social)](https://twitter.com/intent/tweet?text=Hi%20everyone,%20I%20just%20found%20this%20cool%20project%20called%20@pragmalang.%0a%0a@apollographql&url=https://github.com/pragmalang/pragma&hashtags=GraphQL,code,javascript,react,reactjs,reactnative,apollo)
[![Pragma](https://raw.githubusercontent.com/pragmalang/pragma/master/website/static/img/full-logo.svg)](https://pragmalang.com)
[![Discord](https://img.shields.io/discord/719970258903105558?label=Discord&logo=discord&style=for-the-badge)](https://discord.gg/gbhDnfC)
[![Reddit](https://img.shields.io/reddit/subreddit-subscribers/pragmalang?style=for-the-badge&logo=Reddit&logoColor=E17334&color=E17334)](https://reddit.com/r/pragmalang)
![Tests](https://img.shields.io/github/workflow/status/pragmalang/pragma/Test/master?style=for-the-badge&label=tests)
![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=for-the-badge)


# Table of Contents
- [Table of Contents](#table-of-contents)
- [Introduction](#introduction)
  - [What is Pragma?](#what-is-pragma)
  - [Why Pragma?](#why-pragma)
  - [Tell People About This](#tell-people-about-this)
  - [Documentation](#documentation)
- [Install Pragma](#install-pragma)
  - [Linux](#linux)
  - [macOS](#macos)
  - [Windows](#windows)
- [Getting Started](#getting-started)
- [Community & Support](#community--support)
- [Contributing](#contributing)

# Introduction

## What is Pragma?

Pragma is a language for building beautiful and extensible GraphQL APIs ***in no time***. Within a single file, you can define your **data models** and **authorization rules (permissions and roles)**, and **import serverless functions** for data validation, transformation, authorization or any custom logic. Then with a single command, Pragma generates a fully functional API ready to be used from your front-end application.

## Why Pragma?

You want to focus on building user-facing features instead of dealing with resolvers, endpoints, migrations, authentication, authorization, scaling, queries, and all the headache that comes with building and maintaining an API to be consumed from the front end.

Pragma helps you build for your users, deliver a lot faster (10-100x faster), iterate and try new ideas with minimal technical cost.

## Tell People About This

Hyped enough?

Great! You can [tweet about this project](https://twitter.com/intent/tweet?text=Hi%20everyone,%20I%20just%20found%20this%20cool%20project%20called%20@pragmalang.%0a%0a@apollographql&url=https://github.com/pragmalang/pragma&hashtags=GraphQL,code,javascript,react,reactjs,reactnative,apollo) and tell your followers how interesting this is.

You can also [join our Discord server](https://discord.gg/gbhDnfC) to meet other developers, get community support, and have fun!

## Documentation

Visit [the documentation](https://docs.pragmalang.com) and learn about how Pragma can make your developer experience fun and help you build and deliver a lot faster.

# Install Pragma

> Pragma is currently under heavy development, and should not be used in a production setting. All Pragma APIs are subject to breaking change.

## Linux

### Requirements
- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/).

To make sure you have them and that they work, run:
```sh
docker run hello-world
```
```sh
docker-compose --help
```

If any of the above commands fail, make sure it works before proceeding with the installation of Pragma.

### Installation

Now to install Pragma, run:
```sh
sudo curl https://pragmalang.github.io/pragma/install/linux/install.sh | sh
```

This script will download the Pragma binary, change it to become executable, and place it in `/usr/local/bin`. It also places a `pragma-docker-compose.yml` file in `/usr/local/bin`, and runs it.

### Run Pragma

Once Pragma is downloaded and installed, you can see if it works by running the following command:
```
pragma help
```

## macOS

### Requirements

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/).
- [Java](https://java.com/en/download/help/linux_install.html)

### Installation

First, we need to install the Pragma CLI:

> **Note:** When [installing Java](https://www.oracle.com/java/technologies/javase-jdk15-downloads.html), make sure to use the **macOS Installer**. The macOS version of Pragma is the only one that doesn't come with a bundled Java runtime, due to the latest security features in macOS Catalina+.

### Run Pragma

Once Pragma is downloaded and installed, you can see if it works by running the following command from the terminal:
```
pragma help
```

## Windows

### Requirements

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/).
- [Java](https://java.com/en/download/help/linux_install.html)

### Installation

First, we need to install the Pragma CLI:

- [Download the installer (`pragma.msi`)](https://github.com/pragmalang/pragma/releases/download/v0.1.0/pragma.msi)
- Run `pragma.msi` and follow the installation wizard

> *Note*: If Microsoft Defender tells you it prevented an unrecognized app from starting, click on "__More info__", then click on **"Run anyway"**.

After the CLI is installed, we need to install and run the Pragma Daemon (`pragmad`): 

- Create a `docker-compose.yml` file with the contents of [this docker-compose file](https://github.com/pragmalang/pragma/blob/master/cli/src/main/resources/docker-compose.yml)
- Run it with `docker-compose up -d`


> *Note*: The name of the docker-compose file *must* be `docker-compose.yml`, and you must run `docker-compose up -d` from the folder where you created the `docker-compose.yml` file.

### Run Pragma

Once Pragma is downloaded and installed, you can see if it works by running the following command from PowerShell:
```
pragma help
```

# Getting Started

After Pragma has been successfully installed, visit [this page of the documentation](https://docs.pragmalang.com/docs/getting-started/basic-todo-app) for a step-by-step tutorial on building a simple Todo application.

# Community & Support

If you have any questions or feedback, you can join our [Discord server](https://discord.gg/gbhDnfC) or post to [r/pragmalang](https://www.reddit.com/r/pragmalang/). We would love to hear from you!

# Contributing

Pragma is a Scala 2.13 project built using SBT. It's divided into three subprojects:
* `core`: where the domain abstractions and parsing logic is kept alongside any shared logic between other subprojects. All other subprojects depend on the `core`.
* `daemon`: where the serving and project management logic lives. It needs instances for Postgres and Openwhisk to be running; it's meant to be running alongside them in the background while using Pragma during development and in production.
* `cli`: where the communication with the `daemon` and the loading of user files is done.

It's highly recommended to be familiar with [Cats](https://typelevel.org/cats/) and [Cats Effect](https://typelevel.org/cats-effect/) before starting to work on the daemon. 

## Setup
Pragma has been developed using VSCode with the Metals extension on Linux. For it all to work, it requires a JDK, Scala, SBT, and Ammonite for some scripts. Use this script to quickly install them:
```sh
curl -Lo cs https://git.io/coursier-cli-linux && chmod +x cs && ./cs setup
```
Docker and Docker Compose are also used for conveniently running Postgres and Openwhisk locally.

## Testing
The recommended way to work on the daemon is to run it using SBT and run Postgres and Openwhisk from `docker-compose`.

To run Postgres and Openwhisk:
```sh
cd daemon/src/main/resources/ && docker-compose up
```
> Make sure to run this before running `sbt 'daemon/test'`.

Running the daemon using `sbt 'daemon/run'` requires the following environment variables to be exported:
```sh
export DAEMON_HOSTNAME=localhost && \
export DAEMON_PORT=9584 && \
export DAEMON_PG_HOST='localhost' && \
export DAEMON_PG_PORT=5433 && \
export DAEMON_PG_DB_NAME='test' && \
export DAEMON_PG_USER='test'  && \
export DAEMON_WSK_API_URL='http://localhost:3233'  && \
export DAEMON_WSK_AUTH_TOKEN='23bc46b1-71f6-4ed5-8c54-816aa4f8c502:123zO3xZCLrMN6v2BKK1dXYFpXlPkccOFqm12CdAsMgRU4VrNZ9lyGVCGuMDGIwP'  && \
export DAEMON_PG_PASSWORD='test'  && \
export DAEMON_WSK_API_VERSION=1
```

You can run the daemon alongside Postgres and Openwhisk with:
```sh
# In the root of the project
docker-compose up
```

> NOTE: If the docker containers cannot be started it's most likely because the port 5433 is already in use. Run `docker ps` and then run `docker kill <postgres-containe-id>` to kill the Postgres container to fix it.

## Docker Build
Docker builds are performed using [SBT Native Packager](https://www.scala-sbt.org/sbt-native-packager/formats/docker.html). To build the Pragma daemon Docker image:
```
sbt "daemon/docker:publishLocal"
```

## GraalVM Native Image Generation
The native image build is performed using [SBT Native Packager](https://www.scala-sbt.org/sbt-native-packager/formats/graalvm-native-image.html), so the `native-image` version needs to be installed locally.

Currently, only the CLI can be compiled to a native image. Run:
```sh
sbt "cli/graalvm-native-image:packageBin"
```

To generate META-INF (for trying to generate a native image from the daemon):
```

To build macOS `.dmg`:
```sh
sbt 'cli/universal:packageOsxDmg'
```
See:
* https://www.graalvm.org/reference-manual/native-image/Configuration/#assisted-configuration-of-native-image-builds
* https://noelwelsh.com/posts/2020-02-06-serverless-scala-services.html

> NOTE: Make sure everything else other than this process is canceled. It needs all the memory it can get.

## Apache Bench benchmark
Run the ammonite script in `test/benchmark`:
```
amm PragmaBench.sc
```
Make sure the daemon is running before running the benchmark (run `docker-compose up` in the root of the project).

> NOTE: Apache Bench must be installed:
>```
>sudo apt install apache2-utils
>```

## Documentation
The user documentation lives in `website/docs` and is built using Docusaurus 2. It's hosted on http://docs.pragmalang.com.

# License

Pragma is licensed under the [GNU GPLv3 License](https://github.com/pragmalang/pragma/blob/master/LICENSE).
