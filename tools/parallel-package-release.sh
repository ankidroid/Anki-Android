#!/bin/bash
#
# This script assumes a few things -
#
# 1) you are in the main directory of the Anki source code (e.g. 'Anki-Android' is the current working directory)
# 2) you have a Java keystore at the file path $home/src/android-keystore
# 3) In that java keystore you have the key alias 'nrkeystorealias'
# 4) you have no local changes in your working directory (e.g. "git reset --hard && git clean -f")
# If those assumptions are met, this script will generate 3 parallel builds that should be the same as your current checkout
# They will be placed in the parent directory ('..') as 'AnkiDroid-<version>.parallel.<A B or C>.apk'

TAG=$1
STOREPASS=$2
KEYPASS=$3

export KEYPWD=$KEYPASS
export KSTOREPWD=$STOREPASS

# Get on to the tag requested
#git checkout $TAG

BUILDNAMES='A B C'
for BUILD in $BUILDNAMES; do
    git reset --hard
    git clean -f
    LCBUILD=`tr '[:upper:]' '[:lower:]' <<< $BUILD`
    ./tools/parallel-package-name.sh com.ici2.anki.$LCBUILD AnkiDroid.$BUILD
    ./gradlew assembleRelease
    cp AnkiDroid/build/outputs/apk/AnkiDroid-release.apk ../AnkiDroid-2.9alpha16.parallel.$BUILD.apk
done
