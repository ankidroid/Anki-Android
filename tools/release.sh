#!/bin/bash
# Perform a release of AnkiDroid
# Nicolas Raoul 2011/10/24
# Timothy Rae 2014/11/10
# Usage from AnkiDroid's root directory:
# tools/release.sh # For an alpha or beta release
# tools/release.sh public # For a public (non alpha/beta) release

# Suffix configuration
SUFFIX=""
#SUFFIX="-EXPERIMENTAL"

PUBLIC=$1

# Define the location of the manifest file
SRC_DIR="./AnkiDroid/src/main/"
MANIFEST="AndroidManifest.xml"

if [ "$PUBLIC" = "public" ]; then
  echo "About to perform a public release. Please first:"
  echo "- Run tools/comment-logs.sh and commit."
  echo "- Run tools/change-icons-to-blue.sh and commit."
  echo "- Edit the version in AndroidManifest.xml manually but do not commit it."
  echo "Press Enter to continue."
  read
else
  echo "Performing testing release."
fi

set -x

VERSION=`grep android:versionName $SRC_DIR$MANIFEST | sed -e 's/.*="//' | sed -e 's/".*//'`
echo $VERSION

if [ "$PUBLIC" != "public" ]; then
  # Increment version name
  # Ex: 2.1beta7 to 2.1beta8
  PREVIOUS_VERSION=$VERSION
  if [ -n "$SUFFIX" ]; then
    PREVIOUS_VERSION=`echo $PREVIOUS_VERSION | sed -e "s/$SUFFIX//g"`
  fi
  GUESSED_VERSION=`echo $PREVIOUS_VERSION | gawk -f tools/lib/increase-version.awk`
  VERSION=${1:-$GUESSED_VERSION$SUFFIX}

  # Increment version code
  # It is an integer in AndroidManifest that nobody actually sees.
  # Ex: 72 to 73
  PREVIOUS_CODE=`grep android:versionCode $SRC_DIR$MANIFEST | sed -e 's/.*="//' | sed -e 's/".*//'`
  GUESSED_CODE=`expr $PREVIOUS_CODE + 1`

  # Edit AndroidManifest.xml to bump version string
  echo "Bumping version from $PREVIOUS_VERSION$SUFFIX to $VERSION (and code from $PREVIOUS_CODE to $GUESSED_CODE)"
  sed -i -e s/$PREVIOUS_VERSION$SUFFIX/$VERSION/g $SRC_DIR$MANIFEST
  sed -i -e s/versionCode=\"$PREVIOUS_CODE/versionCode=\"$GUESSED_CODE/g $SRC_DIR$MANIFEST
fi

# Read the key passwords
read -sp "Enter keystore password: " KSTOREPWD; echo
read -sp "Enter key password: " KEYPWD; echo
export KSTOREPWD
export KEYPWD
# Build signed APK using Gradle
CMD="./gradlew publishApkRelease"
${CMD}
if [ $? -ne 0 ]; then
  # APK contains problems, abort release
  git checkout -- $SRC_DIR$MANIFEST # Revert version change
  exit
fi

# Copy exported file to cwd
cp AnkiDroid/build/outputs/apk/AnkiDroid-release.apk AnkiDroid-$VERSION.apk

# Commit modified AndroidManifest.xml
git add $SRC_DIR$MANIFEST
git commit -m "Bumped version to $VERSION
@branch-specific"

# Tag the release
git tag v$VERSION

# Push both commits and tag
#git push --follow-tags # Not working... wanted to do both in a single request, to make it faster
git push
git push --tags

# Push to Github Releases.
export GITHUB_TOKEN=`cat ~/src/my-github-personal-access-token`
export GITHUB_USER="ankidroid"
export GITHUB_REPO="Anki-Android"

if [ "$PUBLIC" = "public" ]; then
  PRE_RELEASE=""
else
  PRE_RELEASE="--pre-release"
fi

github-release release --tag v$VERSION --name "AnkiDroid $VERSION" $PRE_RELEASE
github-release upload --tag v$VERSION --name AnkiDroid-$VERSION.apk --file AnkiDroid-$VERSION.apk

# TODO: Push to Google Play
