# Install Pragma

> Pragma is currently under heavy development, and should not be used in a production setting. All Pragma APIs are subject to breaking change.

## Requirements
Pragma requires [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/) on the `PATH`. To make sure you have them and that they work, run:
```sh
docker run hello-world

docker-compose --help
```
If either command fails, make sure it works before proceeding with the installation of Pragma.

## Installation

### Linux
To install Pragma on Linux, run:
```sh
sudo curl https://pragmalang.github.io/releases/linux/install.sh | sh
```

This script will download the Pragma binary, change it to become executable, and place it in `/usr/local/bin`. It also places a `pragma-docker-compose.yml` file in `/usr/local/bin`, and runs it.

### Windows
To install Pragma on Windows, [download the installer (`pragma.msi`)](https://pragmalang.github.io/releases/windows/pragma.msi), and follow the installation wizard. This will install the Pragma CLI.

> *Note*: If Microsoft Defender tells you it prevented an unrecognized app from starting, click on "__More info__", then click on **"Run anyway"**.

After the CLI is installed, create a `docker-compose.yml` file with the contents of [this `docker-compose` file](https://github.com/pragmalang/pragma/blob/master/cli/src/main/resources/docker-compose.yml), and run it with `docker-compose up -d`. This will run the pragma daemon in the background.

> *Note*: The name of the docker-compose file *must* be `docker-compose.yml`, and you must run `docker-compose up -d` in the folder where you created the `docker-compose.yml` file.

---

Once Pragma is downloaded and installed, you can see if it works by running `pragma help`.
