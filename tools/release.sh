#!/bin/sh
# Perform an alpha or beta release of AnkiDroid
# Nicolas Raoul
# 2011/10/24
# Usage from AnkiDroid's root directory: tools/release.sh 1.0beta21
# If no option given, will guess the next version number.

# Suffix configuration
SUFFIX=""
#SUFFIX="-EXPERIMENTAL"

set -x

# Version number to use
PREVIOUS_VERSION=`grep android:versionName AndroidManifest.xml | sed -e 's/.*="//' | sed -e 's/".*//'`
if [ -n "$SUFFIX" ]; then
 PREVIOUS_VERSION=`echo $PREVIOUS_VERSION | sed -e "s/$SUFFIX//g"`
fi
GUESSED_VERSION=`echo $PREVIOUS_VERSION | gawk -f tools/lib/increase-version.awk`
VERSION=${1:-$GUESSED_VERSION$SUFFIX}

# Edit AndroidManifest.xml to bump version string
echo "Bumping version from $PREVIOUS_VERSION$SUFFIX to $VERSION"
sed -i -e s/$PREVIOUS_VERSION$SUFFIX/$VERSION/g AndroidManifest.xml

# Generate signed APK
ant clean release

# Upload APK to Google Project's downloads section
mv bin/AnkiDroid-release.apk /tmp/AnkiDroid-$VERSION.apk
GOOGLECODE_PASSWORD=`cat ~/src/googlecode-password.txt` # Can be found at https://code.google.com/hosting/settings
python tools/lib/googlecode_upload.py --summary "AnkiDroid $VERSION" --project ankidroid --user nicolas.raoul --password $GOOGLECODE_PASSWORD /tmp/AnkiDroid-$VERSION.apk

# Commit modified AndroidManifest.xml
git add AndroidManifest.xml
git commit -m "Bumped version to $VERSION"
git push
