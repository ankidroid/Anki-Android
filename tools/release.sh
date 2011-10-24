#!/bin/sh
# Perform an alpha or beta release of AnkiDroid
# Nicolas Raoul
# 2011/10/24
# Usage from AnkiDroid's root directory: tools/release.sh 1.0beta21
# If no option given, will guess the next version number.

set -x

# Version number to use
PREVIOUS_VERSION=`grep android:versionName AndroidManifest.xml | sed -e 's/.*="//' | sed -e 's/".*//'`
GUESSED_VERSION=`echo $PREVIOUS_VERSION | gawk -f tools/lib/increase-version.awk`
VERSION=${1:-$GUESSED_VERSION}

# Edit AndroidManifest.xml to bump version string
echo "Bumping version from $PREVIOUS_VERSION to $VERSION"
sed -i -e s/$PREVIOUS_VERSION/$VERSION/g AndroidManifest.xml
exit

# Generate signed APK
ant release

# Upload APK to Google Project's downloads section
mv bin/Anki-Android-release.apk /tmp/AnkiDroid-$VERSION.apk
GOOGLECODE_PASSWORD=`cat ~/src/googlecode-password.txt` # Can be found at https://code.google.com/hosting/settings
python tools/lib/googlecode_upload.py --summary "AnkiDroid $VERSION" --project ankidroid --user nicolas.raoul --password $GOOGLECODE_PASSWORD /tmp/AnkiDroid-$VERSION.apk

# Commit modified AndroidManifest.xml
git add AndroidManifest.xml
git commit -m "Bumped version $VERSION"
git push
