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
# - authority needed: ability to commit/tag/push directly to main branch in AnkiDroid, ability to create releases
# - ankidroiddocs checked out in a sibling directory (that is, '../ankidroiddocs' should exist with 'upstream' remote set correctly)

# Suffix configuration
SUFFIX=""
PUBLIC=$1

# Make sure we can find our binaries
export PATH="~/bin:$PATH"

# Check basic expectations
echo "Checking basic utility installation status..."
for UTILITY in sed gawk github-release asciidoctor; do
  if ! command -v "$UTILITY" >/dev/null 2>&1; then echo "$UTILITY" missing; exit 1; fi
done
if [ "$PUBLIC" = "public" ] && ! [ -f ../ankidroiddocs/changelog.asc ]; then
  echo "Could not find ../ankidroiddocs/changelog.asc?"
  exit 1
fi

# Define the location of the manifest file
SRC_DIR="./AnkiDroid"
GRADLEFILE="$SRC_DIR/build.gradle"
CHANGELOG="$SRC_DIR/src/main/assets/changelog.html"

if ! VERSION=$(grep 'versionName=' $GRADLEFILE | sed -e 's/.*="//' | sed -e 's/".*//')
then
  echo "Unable to get current version. Is sed installed?"
  exit 1
fi

if [ "$PUBLIC" = "public" ]; then
  echo "About to perform a public release. Please first:"
  echo "- Edit the version in $GRADLEFILE manually but do not commit it."
  echo "- Author and merge a PR to ankidroiddocs/changelog.asc with details for the current version"
  echo "Press Enter to continue."
  read -r

  # Render the new changelog
  echo "Rendering changelog..."
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
  PREVIOUS_CODE=$(grep 'versionCode=' $GRADLEFILE | sed -e 's/.*=//')
  GUESSED_CODE=$((PREVIOUS_CODE + 1))

  # Edit AndroidManifest.xml to bump version string
  echo "Bumping version from $PREVIOUS_VERSION$SUFFIX to $VERSION (and code from $PREVIOUS_CODE to $GUESSED_CODE)"
  sed -i -e s/"$PREVIOUS_VERSION"$SUFFIX/"$VERSION"/g $GRADLEFILE
  sed -i -e s/versionCode="$PREVIOUS_CODE"/versionCode="$GUESSED_CODE"/g $GRADLEFILE
fi

# If any changes go in during the release process, pushing fails, so push immediately.
# Worst case this burns a version number despite a failure later, and we have a version/tag
# that never launched. That's better than having to manually patch up build.gradle and push a tag
# for a release that did launch, but the push failed
echo "Committing changelog and version bump via git"
git add $GRADLEFILE $CHANGELOG
git commit -m "Bumped version to $VERSION"
git tag v"$VERSION"

# Push both commits and tag
git push
git push --tags

# Read the key passwords if needed
if [ "$KSTOREPWD" == "" ]; then
  read -rsp "Enter keystore password: " KSTOREPWD; echo
  read -rsp "Enter key password: " KEYPWD; echo
  export KSTOREPWD
  export KEYPWD
fi

# Build signed APK using Gradle and publish to Play
# Configuration for pushing to Play specified in build.gradle 'play' task
#echo "Running 'publishPlayReleaseApk' gradle target"
#if ! ./gradlew publishPlayReleaseApk
#then
  # APK contains problems
  # Normally we want to abort the release but right now we know google will reject us until
  # we have targetSdkVersion 30, so ignore.
#  git checkout -- $GRADLEFILE # Revert version change  #API30
#  exit 1  #API30
#else  #API30
#  echo "Google has rejected the APK upload. Likely because targetSdkVersion < 30. Continuing..."  #API30
#fi  #API30
#fi  #API30

# Now build the universal release also
./gradlew --stop
echo "Running 'assembleFullRelease' target with universal APK flag"
if ! ./gradlew assembleFullRelease -Duniversal-apk=true
then
  echo "unable to build universal APK for full release"
  exit 1
fi

# Copy universal APK to cwd
ABIS='universal arm64-v8a x86 x86_64 armeabi-v7a'
for ABI in $ABIS; do
  cp AnkiDroid/build/outputs/apk/full/release/AnkiDroid-full-"$ABI"-release.apk AnkiDroid-"$VERSION"-"$ABI".apk
done

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

echo "Creating new Github release"
github-release release --tag v"$VERSION" --name "AnkiDroid $VERSION" --description "**For regular users:**<br/><br/>Install the main APK below, trying the 'universal' build first for new installs. If it refuses to install and run correctly or you have previously installed from the Play Store, you must pick the APK that matches CPU instruction set for your device.<br/><br/>This will be arm64-v8a for most phones from the last few years but [here is a guide to help you choose](https://www.howtogeek.com/339665/how-to-find-your-android-devices-info-for-correct-apk-downloads/)<br/><br/><br/>**For testers and multiple profiles users:**<br/><br/>The builds with letter codes below (A, B, etc) are universal parallel builds. They will install side-by-side with the main APK for testing, or to connect to a different AnkiWeb account in combination with changing the storage directory in preferences" $PRE_RELEASE

echo "Sleeping 30s to make sure the release exists, see issue 11746"
sleep 30

for ABI in $ABIS; do
  echo "Adding APK for $ABI to Github release"
  github-release upload --tag v"$VERSION" --name AnkiDroid-"$VERSION"-"$ABI".apk --file AnkiDroid-"$VERSION"-"$ABI".apk
done

if [ "$PUBLIC" = "public" ]; then
  ./gradlew --stop
  echo "Running 'assembleAmazonRelease' gradle target with universal APK flag"
  if ! ./gradlew assembleAmazonRelease  -Duniversal-apk=true
  then
    echo "Unable to build amazon release"
    exit 1
  fi
  echo "Running 'publishToAmazonAppStore' gradle target"
  if ! ./gradlew publishToAmazonAppStore
  then
    echo "Unable to publish to amazon app store"
    exit 1
  fi
  echo "Remember to add release notes and submit on Amazon: https://developer.amazon.com/apps-and-games/console/app/list"
fi

# Now that Git is clean and the main release is done, run the parallel release script and upload them
echo "Running parallel package build"
./gradlew --stop
./tools/parallel-package-release.sh "$VERSION"
if [ "$PUBLIC" = "public" ]; then
  BUILDNAMES='A B C D E' # For public builds we will post all parallels
else
  BUILDNAMES='A B' # For alpha releases just post a couple parallel builds
fi
for BUILD in $BUILDNAMES; do
  echo "Adding parallel build $BUILD to Github release"
  github-release upload --tag v"$VERSION" --name AnkiDroid-"$VERSION".parallel."$BUILD".apk --file AnkiDroid-"$VERSION".parallel."$BUILD".apk
done
