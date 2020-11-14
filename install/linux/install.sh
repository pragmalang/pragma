#!/bin/bash

# Download the latest Pragma CLI binary
BIN_URL=$(curl -s https://api.github.com/repos/pragmalang/pragma/releases/latest | grep "browser_download_url.*linux" | cut -d : -f 2,3 | tr -d \")
sudo curl -H 'Cache-Control: no-cache' -Lo /usr/local/bin/pragma $BIN_URL
sudo chmod +x /usr/local/bin/pragma

# Download and run the Pragma daemon in Docker Compose

COMP_URL=$(curl -s https://api.github.com/repos/pragmalang/pragma/releases/latest | grep "browser_download_url.*docker-compose.yml" | cut -d : -f 2,3 | tr -d \")
COMP_FILE=/usr/local/bin/pragma-docker-compose.yml

curl -Lo $COMP_FILE $COMP_URL
docker-compose -f $COMP_FILE stop
docker-compose -f $COMP_FILE rm -f
docker-compose -f $COMP_FILE pull pragmad
docker-compose -f $COMP_FILE up -d

echo 'Pragma installation complete.'