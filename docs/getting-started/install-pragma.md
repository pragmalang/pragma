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
Pragma currently works only on Linux. To install it, run:
```sh
sudo curl https://pragmalang.github.io/releases/linux/install.sh | sh
```

This script will download the Pragma binary, change it to become executable, and place it in `/usr/local/bin`. It also places a `pragma-docker-compose.yml` file in `/usr/local/bin`, and runs it.

Once Pragma is downloaded and installed, you can see if it works by running `pragma help`.
