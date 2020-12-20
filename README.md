[![Pragma](https://raw.githubusercontent.com/pragmalang/pragma/master/website/static/img/full-logo.svg)](https://pragmalang.com)
[![Discord](https://img.shields.io/discord/719970258903105558?label=Discord&logo=discord&style=for-the-badge)](https://discord.gg/gbhDnfC)
[![Reddit](https://img.shields.io/reddit/subreddit-subscribers/pragmalang?style=for-the-badge&logo=Reddit&logoColor=E17334&color=E17334)](https://reddit.com/r/pragmalang)
![Tests](https://img.shields.io/github/workflow/status/pragmalang/pragma/Test/master?style=for-the-badge&label=tests)
![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=for-the-badge)

Pragma is a language for building beautiful and extensibleGraphQL APIs *in no time*. Within a single file, you can define your data models and authorization rules, then import serverless functions to use for data validation/transformation/authorization. Pragma then generates a fully functional API ready to be used from your front-end application with a single command.

For more details, visit [the documentation](https://docs.pragmalang.com).

# Table of Contents
* [Install Pragma](#install-pragma)
    * [Linux](#linux)
    * [MacOS](#macos)
    * [Windows](#windows)
* [Getting Started](#getting-started)
* [Community](#community)
* [Contributing](#contributing)

# Install Pragma

> Pragma is currently under heavy development, and should not be used in a production setting. All Pragma APIs are subject to breaking change.

## Linux

### Requirements
- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/)

To make sure you have them and that they work, run:
```sh
docker run hello-world
```
```sh
docker-compose --help
```

If any of the above commands fail, make sure it works before proceeding with the installation of Pragma.

### Installation

To install Pragma, run:
```
curl https://raw.githubusercontent.com/pragmalang/pragma/master/scripts/install-universal.sh | sh
```
The script will ask for root access, so make sure to enter your password when prompted.

If you're on Ubuntu/Debian, you can download the `.deb` package from [releases](https://github.com/pragmalang/pragma/releases/latest).

### Run Pragma

Once Pragma is downloaded and installed, you can see if it works by running the following command:
```
pragma help
```

## MacOS

### Requirements

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/)

To make sure you have them and that they work, run:
```sh
docker run hello-world
```
```sh
docker-compose --help
```

If any of the above commands fail, make sure it works before proceeding with the installation of Pragma.

### Installation

To install the Pragma CLI using [Homebrew](https://brew.sh/):
```
brew install pragmalang/homebrew-tap/pragma
```

### Run Pragma

Once Pragma is downloaded and installed, you can see if it works by running the following command from the terminal:
```
pragma help
```

## Windows

### Requirements

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/)

To make sure you have them and that they work, run:
```sh
docker run hello-world
```
```sh
docker-compose --help
```

If any of the above commands fail, make sure it works before proceeding with the installation of Pragma.

### Installation

First, we need to install the Pragma CLI:

- [Download the installer](https://github.com/pragmalang/pragma/releases/download/0.2.0/pragma-0.2.0.msi)
- Run the installer and follow the installation wizard

> *Note*: If Microsoft Defender tells you it prevented an unrecognized app from starting, click on "__More info__", then click on **"Run anyway"**.

### Run Pragma

Once Pragma is downloaded and installed, you can see if it works by running the following command from PowerShell:
```
pragma help
```

# Getting Started

After Pragma has been successfully installed, visit [this page of the documentation](https://docs.pragmalang.com/docs/getting-started/basic-todo-app) for a step-by-step tutorial on building a simple Todo application.

# Community

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

> NOTE: If the docker containers cannot be started it's most likely because the port 5433 is already in use. Run `docker ps` and then run `docker kill <postgres-containe-id>` to kill the postgres container to fix it.

## Docker Build
Docker builds are performed using [SBT Native Packager](https://www.scala-sbt.org/sbt-native-packager/formats/docker.html). To build the Pragma daemon Docker image:
```
sbt "daemon/docker:publishLocal"
```

## CLI Packaging

> NOTE: Generating the packages for each platform requires running the build on that very platform, in addition to some dependencies installed locally. See the requirements of each platform's plugin.

These packages should **NOT** require a local JDK installation, or have any dependencies since the [Jlink plugin](https://www.scala-sbt.org/sbt-native-packager/archetypes/jlink_plugin.html) is used.

To build Linux packages:
```sh
sbt 'cli/debian:packageBin; cli/rpm:packageBin'
```

To build Windows installer (`.msi`):
```sh
sbt 'cli/windows:packageBin'
```

To build MacOS `.dmg`:
```sh
sbt 'cli/universal:packageOsxDmg'
```

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
