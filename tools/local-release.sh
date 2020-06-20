#!/bin/bash
#
# This script assumes a few things -
#
# 1) you are in the main directory of the Anki source code (e.g. 'Anki-Android' is the current working directory)
# 2) you have a Java keystore at the file path $home/src/android-keystore
# 3) In that java keystore you have the key alias 'nrkeystorealias'

# It will ask you for your keystore and key password

# Read the key passwords
read -sp "Enter keystore password: " KSTOREPWD; echo
read -sp "Enter key password: " KEYPWD; echo
export KSTOREPWD
export KEYPWD
./gradlew assembleRelease -Duniversal-apk=true
./tools/parallel-package-release.sh TEST
