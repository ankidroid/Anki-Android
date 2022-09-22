#!/bin/bash

##################################################################################################
# This script sets up the backend library which is required to run tests on Apple ARM devices #
##################################################################################################

# Extracting AnkiDroid backend version from build.gradle (project)
RELEASE_TAG=$(< build.gradle grep ankidroid_backend_version | cut -d"'" -f2)

# No need to change this in most of the cases
ANKIDROID_BACKEND_DIRECTORY=$HOME/.ankidroid/backend

# Confirm path where to download the dylib file
printf '\n'
echo "*************************************************************************"
echo "* Dylib file will be downloaded at $HOME/.ankidroid/backend"
echo "* Enter y to confirm, or a custom path (please use full path)"
echo "*************************************************************************"
read -r -p "Confirm (y or path): " CONFIRMATION_OR_PATH
if [[ $CONFIRMATION_OR_PATH != "y" ]]; then
    ANKIDROID_BACKEND_DIRECTORY=$CONFIRMATION_OR_PATH
fi
# Creating backend directory in $HOME if does not exists
if [ ! -d "$ANKIDROID_BACKEND_DIRECTORY" ]; then
    echo "Creating anki backend directory at $ANKIDROID_BACKEND_DIRECTORY"
    mkdir -p "$ANKIDROID_BACKEND_DIRECTORY"
else
    echo "Anki backend directory exists!"
fi

# Path to the dylib file
ANKIDROID_BACKEND_PATH="$ANKIDROID_BACKEND_DIRECTORY/librsdroid-arm64.dylib"
# Download the latest release in $ANKIDROID_BACKEND_PATH
echo "Downloading dylib library file..."
wget -O "$ANKIDROID_BACKEND_PATH"  https://github.com/ankidroid/Anki-Android-Backend/releases/download/$RELEASE_TAG/librsdroid-arm64.dylib

# Export environment variables to .zshrc
printf '\n'
echo "*************************************************************************"
echo "* Environment variable wil be written into .zshrc"
echo "* Please Enter y to confirm, any other key to abort (requires envs to be set manually)"
echo "*************************************************************************"
read -r -p 'Confirm (y/n): ' CONFIRMATION
if [[ $CONFIRMATION != "y" ]]; then
    echo "Status: Exit"
    echo "You have chosen to set environment variables manually!"
    exit 1
fi

# Removing any previously existing environment variables to avoid conflicts
sed -i '' "/export ANKIDROID_BACKEND_PATH/d" "$HOME/.zshrc"
sed -i '' "/export ANKIDROID_BACKEND_VERSION/d" "$HOME/.zshrc"
# Adding new environment variables
echo "export ANKIDROID_BACKEND_PATH=$ANKIDROID_BACKEND_PATH" >> "$HOME/.zshrc"
echo "export ANKIDROID_BACKEND_VERSION=$RELEASE_TAG" >> "$HOME/.zshrc"

# Inform user
echo "Written environment variable in .zshrc"
echo "export ANKIDROID_BACKEND_PATH=$ANKIDROID_BACKEND_PATH"
echo "export ANKIDROID_BACKEND_VERSION=$RELEASE_TAG"

printf '\n'
echo 'Status: Successful :)'
echo 'Please log out and log back in again, then start Android Studio again for the changes to take effect!'