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

- [Download the Pragma DMG file (`pragma.dmg`)](https://github.com/pragmalang/pragma/releases/download/v0.1.0/pragma.dmg)
- Run `pragma.dmg`

After the CLI is installed, we need to install and run the Pragma Daemon (`pragmad`): 

- Create a `docker-compose.yml` file with the contents of [this docker-compose file](https://github.com/pragmalang/pragma/blob/master/cli/src/main/resources/docker-compose.yml)
- Run it with `docker-compose up -d`

> *Note*: The name of the docker-compose file *must* be `docker-compose.yml`, and you must run `docker-compose up -d` from the directory where you created the `docker-compose.yml` file.

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

# Support

If you have any questions or feedback you can join our [Discord server](https://discord.gg/gbhDnfC) or post to [r/pragmalang](https://www.reddit.com/r/pragmalang/).