#!/bin/bash

# Installs the Pragma CLI universal package on Unix-like systems

set -e

DOWNLOAD_URL='https://github.com/pragmalang/pragma/releases/download/1.0.0RC/pragma.zip'

sudo rm -rf /usr/local/bin/pragma
sudo rm -rf /usr/local/lib/pragma
sudo rm -rf /tmp/pragma.zip 
sudo rm -rf /tmp/pragma

echo 'Downloading Pragma CLI...'
curl -H 'Cache-Control: no-cache' -Lo /tmp/pragma.zip $DOWNLOAD_URL

echo 'Extracting files...'
unzip -d /tmp/pragma /tmp/pragma.zip

echo "Installing Pragma in /usr/local/lib/pragma..."
sudo chmod +x /tmp/pragma/pragma/pragma
sudo mv /tmp/pragma/pragma/ /usr/local/lib/

echo "Creating symbolic link /usr/local/lib/pragma/bin/pragma -> /usr/local/bin/pragma"
sudo ln -s /usr/local/lib/pragma/pragma /usr/local/bin/pragma

echo 'Pragma has been successfully installed! Run `pragma help` to see what you can do.'
