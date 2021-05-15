#!/bin/bash

# Installs the Pragma CLI universal package on Unix-like systems

set -e

DOWNLOAD_URL='https://github.com/pragmalang/pragma/releases/download/1.0.0RC/pragma.zip'

sudo rm -rf /usr/local/bin/pragma /tmp/pragma.zip

echo 'Downloading Pragma CLI...'
curl -H 'Cache-Control: no-cache' -Lo /tmp/pragma.zip $DOWNLOAD_URL

echo 'Extracting files...'
unzip -d /tmp/pragma /tmp/pragma.zip

PRAGMA_DIR_NAME=$(ls /tmp/pragma)

echo "Installing Pragma in /usr/local/lib/$PRAGMA_DIR_NAME..."
sudo chmod +x /tmp/pragma/$PRAGMA_DIR_NAME/pragma
sudo mv /tmp/pragma/$PRAGMA_DIR_NAME/ /usr/local/lib/

echo "Creating symbolic link /usr/local/lib/$PRAGMA_DIR_NAME/bin/pragma -> /usr/local/bin/pragma"
sudo ln -s /usr/local/lib/$PRAGMA_DIR_NAME/pragma /usr/local/bin/pragma

echo 'Pragma has been successfully installed! Run `pragma help` to see what you can do.'
