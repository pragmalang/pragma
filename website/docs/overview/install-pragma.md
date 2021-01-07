---
id: install-pragma
title: Install Pragma
slug: /install
---

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
- [Java](https://www.oracle.com/java/technologies/javase-jdk15-downloads.html)

To make sure you have them and that they work, run:
```sh
docker run hello-world
```
```sh
docker-compose --help
```
```sh
java -version
```

If any of the above commands fail, make sure it works before proceeding with the installation of Pragma.

:::note
When [installing Java](https://www.oracle.com/java/technologies/javase-jdk15-downloads.html), make sure to use the **macOS Installer**. The macOS version of Pragma is the only one that doesn't come with a bundled Java runtime, due to the latest security features in macOS Catalina+.
:::

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

- Download the latest MSI (`.msi`) installer from [GitHub releases](https://github.com/pragmalang/pragma/releases/latest)
- Run the installer and follow the installation wizard

> *Note*: If Microsoft Defender tells you it prevented an unrecognized app from starting, click on "__More info__", then click on **"Run anyway"**.

### Run Pragma

Once Pragma is downloaded and installed, you can see if it works by running the following command from PowerShell:
```
pragma help
```

# Support

If you have any questions or feedback you can join our [Discord server](https://discord.gg/gbhDnfC) or post to [r/pragmalang](https://www.reddit.com/r/pragmalang/).