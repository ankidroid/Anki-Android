

This page describes how to release AnkiDroid. It can be interesting as an insight to understand the project better, or to improve the procedure.

# Development lifecycle

There are only two phases, the project is always alternating alpha->beta->alpha->beta etc.
When switching from alpha to beta, create a release branch, for instance release-0.6.

## Alpha phase
Commits are liberally merged into master, provided the code compiles.

## Beta phase
Recognizable by AndroidManifest.xml containg the word "beta" in the versionName attribute. The code is "freezed", meaning that only the following commits are merged into master:
  * Bug fixes
  * Translations
  * Development tools

# Alpha or beta release procedure
  * Always use this repository: https://github.com/ankidroid/Anki-Android
  * Checkout the branch to release ("develop" for an alpha, or for instance "release-0.6" for a beta)
  * In AndroidManifest.xml change android:versionName from 0.6beta11 to 0.6beta12 (for instance), and change android:versionCode accordingly.
  * Build an APK using "ant release" or Eclipse (be sure to refresh Eclipse's project).
  * Upload the APK to http://code.google.com/p/ankidroid/downloads
  * Upload the APK to Google Play alpha or beta
  * Commit and push
The tools/release.sh script can perform some of those steps effortlessly.

# Stable release procedure

## Build
  * Always use this repository: https://github.com/ankidroid/Anki-Android
  * Switch to the branch to release, for instance "release-0.6"
  * Run tools/comment-logs.sh
  * Try to compile. If it fails, fix any multiple-lines log line, run tools/uncomment-logs.sh, commit and start the whole procedure again.
  * Commit
  * change icons to blue by reverting the first icon commit of this version ( tools/change-icons-to-blue.sh )
  * In AndroidManifest.xml change android:versionName from 0.6beta13 to 0.6 (for instance) and change android:versionCode accordingly.
  * Commit, push.
  * Tag the version: git tag v0.6
  * Push: git push --tags
  * Build a signed APK using "ant clean release". Rename it from bin/AnkiDroid-release.apk to AnkiDroid-0.6.apk for instance
  * Go to https://github.com/ankidroid/Anki-Android/tags click "Edit release notes" drop APK over drop zone, press "Update release".
Running "tools/release.sh public" can perform some of those steps effortlessly.

## Android Market
Upload the APK to Android market. Archive the previous APK from the "Active" section, then publish.

Title and Description for each language:
  * Title: docs/marketing/localized description/android-titles.txt
  * Description: docs/marketing/localized description
  * New feature list: Run tools/humanize-new-features.sh

Send an email to the mailing list announcing the new version, link to APK, new features, localizations, and thanking the developers, translators, testers.

## Prepare for next cycle
  * Click http://code.google.com/p/ankidroid/issues/list?q=status%3AFixedInDev then click Select All, unselect the ones that are for a next version, then in "Actions" select "Bulk edit". Set status to "Fixed" and message "Fixed in version 0.6, available on Google Play", "Send email" checked.

## Merging from release or hotfix branch back into develop
The following procedure can be used to merge commits from a release branch back into develop:

```
# One time setup.
git clone git@github.com:ankidroid/Anki-Android.git
cd Anki-Android

# Every time setup.
RELEASE_BRANCH=hotfix-2.3.1  # Update this accordingly.
git fetch
git checkout $RELEASE_BRANCH
git pull --ff-only
git checkout develop
git pull --ff-only

# Pick one of the following to options:

# 1. Manual flow
./tools/gitflow-integrate  show $RELEASE_BRANCH develop
# This will tell you what the tool will do by default, merge or skip.

# Decide which action to take:
# a. Default action (shown above).
./tools/gitflow-integrate apply $RELEASE_BRANCH develop
# b. Force a merge.
./tools/gitflow-integrate  merge $RELEASE_BRANCH develop
# c. Force a skip.
./tools/gitflow-integrate  skip $RELEASE_BRANCH develop
# Skips should be applied to version number changes,
# release packaging changes (i.e., change to icons, commenting
# of logs, etc.), and cherry-picks from develop into a release
# branch.
#
# Repeat until nothing is left to merge.

# 2. Automated flow
while ./tools/gitflow-integrate apply $RELEASE_BRANCH develop; do
   echo;
done
# Note that this will always just do the default action for every commit.
# If @branch-specific is set in all the right places (see above for the
# common types of commit that should be skipped) then things should
# just work.

# NOTE: If a merge conflict arises, you will get a message like:
#   Automatic merge failed; fix conflicts and then commit the result.
#   Please resolve conflict and press ENTER
# You should resolve the conflict and press ENTER to continue.
# Remember to add 
# Make sure things still build.
./create-build-files.sh 
ant clean debug

# Generate a merge.log
git log --first-parent origin/develop..develop --format='%B' --reverse >$HOME/merge.log
# Push changes 
git push

# Send the merge log to anki-android@googlegroups.com
# If a thread for the logs already exist, add a message to that thread,
# otherwise create a new thread named:
#   Catching up develo to $RELEASE_BRANCH
```