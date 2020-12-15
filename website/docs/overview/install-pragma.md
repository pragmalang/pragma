---
id: install-pragma
title: Install Pragma
slug: /install
---

:::caution Info
Pragma is currently under heavy development, and should not be used in a production setting. All Pragma APIs are subject to breaking change.
:::
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
curl https://pragmalang.github.io/pragma/scripts/install-universal.sh | sh
```
The script will ask for root access, so make sure to enter your password when prompted.

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

- [Download the installer (`pragma.msi`)](https://github.com/pragmalang/pragma/releases/download/v0.1.0/pragma.msi)
- Run `pragma.msi` and follow the installation wizard

> *Note*: If Microsoft Defender tells you it prevented an unrecognized app from starting, click on "__More info__", then click on **"Run anyway"**.

### Run Pragma

Once Pragma is downloaded and installed, you can see if it works by running the following command from PowerShell:
```
pragma help
```

# Support

If you have any questions or feedback you can join our [Discord server](https://discord.gg/gbhDnfC) or post to [r/pragmalang](https://www.reddit.com/r/pragmalang/).