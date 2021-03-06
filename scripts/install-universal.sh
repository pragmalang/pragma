#!/bin/bash

# Installs the Pragma CLI universal package on Unix-like systems

set -e

DOWNLOAD_URL=$(curl -s https://api.github.com/repos/pragmalang/pragma/releases/latest | grep "browser_download_url.*universal" | cut -d : -f 2,3 | tr -d \")

sudo rm -rf /usr/local/bin/pragma /tmp/pragma.zip

echo 'Downloading Pragma CLI...'
curl -H 'Cache-Control: no-cache' -Lo /tmp/pragma.zip $DOWNLOAD_URL

echo 'Extracting files...'
unzip -d /tmp/pragma /tmp/pragma.zip

PRAGMA_DIR_NAME=$(ls /tmp/pragma)

echo "Installing Pragma in /usr/local/lib/$PRAGMA_DIR_NAME..."
sudo chmod +x /tmp/pragma/$PRAGMA_DIR_NAME/bin/pragma
sudo mv /tmp/pragma/$PRAGMA_DIR_NAME/ /usr/local/lib/

echo "Creating symbolic link /usr/local/lib/$PRAGMA_DIR_NAME/bin/pragma -> /usr/local/bin/pragma"
sudo ln -s /usr/local/lib/$PRAGMA_DIR_NAME/bin/pragma /usr/local/bin/pragma

echo 'Pragma has been successfully installed! Run `pragma help` to see what you can do.'
