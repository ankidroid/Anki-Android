#!/bin/bash
# Perform a release of AnkiDroid
# Nicolas Raoul 2011/10/24
# Timothy Rae 2014/11/10
# Mike Hardy 2020/05/15
# Usage from AnkiDroid's root directory:
# tools/release.sh # For an alpha or beta release
# tools/release.sh public # For a public (non alpha/beta) release


# Basic expectations
# - tools needed: sed, gawk, github-release, git
# - authority needed: ability to commit/tag/push directly to master in AnkiDroid, ability to create releases
# - ankidroiddocs checked out in a sibling directory (that is, '../ankidroiddocs' should exist with 'upstream' remote set correctly)

# Suffix configuration
SUFFIX=""
PUBLIC=$1

# Check basic expectations
for UTILITY in sed gawk github-release asciidoctor; do
  if ! command -v "$UTILITY" >/dev/null 2>&1; then echo "$UTILITY" missing; exit 1; fi
done
if ! [ -f ../ankidroiddocs/changelog.asc ]; then
  echo "Could not find ../ankidroiddocs/changelog.asc?"
  exit 1
fi

# Define the location of the manifest file
SRC_DIR="./AnkiDroid/src/main"
MANIFEST="$SRC_DIR/AndroidManifest.xml"
CHANGELOG="$SRC_DIR/assets/changelog.html"

if ! VERSION=$(grep android:versionName $MANIFEST | sed -e 's/.*="//' | sed -e 's/".*//')
then
  echo "Unable to get current version. Is sed installed?"
  exit 1
fi

if [ "$PUBLIC" = "public" ]; then
  echo "About to perform a public release. Please first:"
  echo "- Edit the version in AndroidManifest.xml manually but do not commit it."
  echo "- Author and merge a PR to ankidroiddocs/changelog.asc with details for the current version"
  echo "Press Enter to continue."
  read -r

  # Render the new changelog
  if ! asciidoctor ../ankidroiddocs/changelog.asc -o "$CHANGELOG"
  then
    echo "Failed to render changelog?"
    exit 1
  fi
  if ! grep "Version $VERSION " "$CHANGELOG"
  then
    echo "Could not find entry for version $VERSION in rendered $CHANGELOG ?"
    exit 1
  fi

else
  echo "Performing testing release."
fi

if [ "$PUBLIC" != "public" ]; then
  # Increment version name
  # Ex: 2.1beta7 to 2.1beta8
  PREVIOUS_VERSION=$VERSION
  if [ -n "$SUFFIX" ]; then
    PREVIOUS_VERSION="${PREVIOUS_VERSION//$SUFFIX/}"
  fi
  if ! GUESSED_VERSION=$(echo "$PREVIOUS_VERSION" | gawk -f tools/lib/increase-version.awk)
  then
    echo "Unable to guess next version. Is gawk installed?";
    exit 1;
  fi
  VERSION=${1:-$GUESSED_VERSION$SUFFIX}

  # Increment version code
  # It is an integer in AndroidManifest that nobody actually sees.
  # Ex: 72 to 73
  PREVIOUS_CODE=$(grep android:versionCode $MANIFEST | sed -e 's/.*="//' | sed -e 's/".*//')
  GUESSED_CODE=$((PREVIOUS_CODE + 1))

  # Edit AndroidManifest.xml to bump version string
  echo "Bumping version from $PREVIOUS_VERSION$SUFFIX to $VERSION (and code from $PREVIOUS_CODE to $GUESSED_CODE)"
  sed -i -e s/"$PREVIOUS_VERSION"$SUFFIX/"$VERSION"/g $MANIFEST
  sed -i -e s/versionCode=\""$PREVIOUS_CODE"/versionCode=\""$GUESSED_CODE"/g $MANIFEST
fi

# Read the key passwords
read -rsp "Enter keystore password: " KSTOREPWD; echo
read -rsp "Enter key password: " KEYPWD; echo
export KSTOREPWD
export KEYPWD
# Build signed APK using Gradle and publish to Play 
# Configuration for pushing to Play specified in build.gradle 'play' task
if ! ./gradlew publishReleaseApk
then
  # APK contains problems, abort release
  git checkout -- $MANIFEST # Revert version change
  exit
fi

# Copy exported file to cwd
cp AnkiDroid/build/outputs/apk/release/AnkiDroid-release.apk AnkiDroid-"$VERSION".apk

# Commit modified AndroidManifest.xml (and changelog.html if it changed)
git add $MANIFEST $CHANGELOG
git commit -m "Bumped version to $VERSION
@branch-specific"

# Tag the release
git tag v"$VERSION"

# Push both commits and tag
git push
git push --tags

# Push to Github Releases.
GITHUB_TOKEN=$(cat ~/src/my-github-personal-access-token)
export GITHUB_TOKEN
export GITHUB_USER="ankidroid"
export GITHUB_REPO="Anki-Android"

if [ "$PUBLIC" = "public" ]; then
  PRE_RELEASE=""
else
  PRE_RELEASE="--pre-release"
fi

github-release release --tag v"$VERSION" --name "AnkiDroid $VERSION" $PRE_RELEASE
github-release upload --tag v"$VERSION" --name AnkiDroid-"$VERSION".apk --file AnkiDroid-"$VERSION".apk

if [ "$PUBLIC" = "public" ]; then
  ./gradlew publishToAmazonAppStore
  echo "Remember to add release notes and submit on Amazon: https://developer.amazon.com/apps-and-games/console/app/list"
fi
