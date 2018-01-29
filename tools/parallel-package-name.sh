#!/bin/bash
# Change the package and application name of AnkiDroid for parallel install
# Written by Tim Rae 18-11-2015

# Input arguments are packageId and app name
NEW_ID=$1		# e.g. com.ichi2.anki.a
NEW_NAME=$2		# e.g. AnkiDroid.A

ROOT="AnkiDroid/src/main/"
MANIFEST="AndroidManifest.xml"
CONSTANTS="res/values/constants.xml"

OLD_ID=`grep applicationId AnkiDroid/build.gradle | sed "s/.*applicationId \"//" | sed "s/\"//"`
OLD_NAME=`grep "name=\"app_name\"" $ROOT$CONSTANTS | sed "s/.*name=\"app_name\">//" | sed "s/<\/string>//"`
perl -pi -e "s/name=\"app_name\">$OLD_NAME/name=\"app_name\">$NEW_NAME/g" $ROOT$CONSTANTS
perl -pi -e "s/applicationId \"$OLD_ID/applicationId \"$NEW_ID/g" AnkiDroid/build.gradle
perl -pi -e "s/android:authorities=\"$OLD_ID/android:authorities=\"$NEW_ID/g" $ROOT$MANIFEST
perl -pi -e "s/permission android:name=\"$OLD_ID.permission/permission android:name=\"$NEW_ID.permission/g" $ROOT$MANIFEST
find $ROOT/res/xml -type f -exec perl -pi -e "s/android:targetPackage=\"$OLD_ID\"/android:targetPackage=\"$NEW_ID\"/g" {} \;
