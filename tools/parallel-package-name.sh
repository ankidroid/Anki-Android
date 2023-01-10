#!/bin/bash
# Change the package and application name of AnkiDroid for parallel install
# Written by Tim Rae 18-11-2015

# Input arguments are packageId and app name
NEW_ID=$1		# e.g. com.ichi2.anki.a
NEW_NAME=$2		# e.g. AnkiDroid.A

ROOT="AnkiDroid/src/main/"
CONSTANTS="res/values/constants.xml"

OLD_ID=com.ichi2.anki

perl -pi -e "s/name=\"app_name\"(.*?)>.*?<\/string>/name=\"app_name\"\1>$NEW_NAME<\/string>/g" $ROOT$CONSTANTS
# use .+? to fix reruns of script breaking the applicationId
perl -pi -e "s/applicationId \".+?\"/applicationId \"$NEW_ID\"/g" AnkiDroid/build.gradle