This page describes how to release AnkiDroid. It can be interesting as an insight to understand the project better, or to improve the procedure.

# Development lifecycle
There are three main phases the project alternates between (alpha, beta, stable).

We use the [gitflow branching model](http://nvie.com/posts/a-successful-git-branching-model/), so "develop" (the default branch) contains the latest development code, whereas "master" contains the code for the latest stable release. When we move into the "beta" phase of the development cycle, we implement a feature freeze, and a temporary branch "release-N.n" (N.n being the version code) is created which is only for important bug fixes. During this period, changes to "release-N.n" are regularly merged back into the "develop" branch so that it doesn't fall behind. If an urgent bug is discovered shortly after a major release, a special "hotfix-N.n" branch will be created from master.

In most cases you should base your code on and send pull requests to the default "develop" branch. However, if you are working on a critical bug fix during the feature freeze period or for a hot-fix, you should use the "release-N.n" or "hotfix-N.n" branch. If you are unsure which branch to use, please ask on the forum.

## Alpha phase
Commits are liberally merged into develop, provided the code compiles.

## Beta phase
Recognizable by AndroidManifest.xml containg the word "beta" in the versionName attribute. The code is "frozen", meaning that only the following commits are merged into develop:
  * Bug fixes
  * Translations
  * Development tools

# Alpha or beta release procedure
  * Always use this repository: https://github.com/ankidroid/Anki-Android
  * Checkout the branch to release ("develop" for an alpha, or for instance "release-0.6" for a beta)
  * In AndroidManifest.xml change android:versionName from 0.6beta11 to 0.6beta12 (for instance), and change android:versionCode accordingly.
  * Build an APK using `./gradlew assembleRelease` or Android Studio
  * Upload the APK to github
  * Upload the APK to Google Play alpha or beta
  * Commit and push
The tools/release.sh script can perform some of those steps effortlessly.

# Stable release procedure

## Build
  * Always use this repository: https://github.com/ankidroid/Anki-Android
  * Switch to the branch to release, for instance "release-0.6"
  * Change icons to blue by reverting the first icon commit of this version ( tools/change-icons-to-blue.sh )
  * In AndroidManifest.xml change android:versionName from 0.6beta13 to 0.6 (for instance) and change android:versionCode accordingly.
  * Commit, push.
  * Tag the version: git tag v0.6
  * Push: git push --tags
  * Build a signed APK using `./gradlew clean assembleRelease`. Rename it from bin/AnkiDroid-release.apk to AnkiDroid-0.6.apk for instance
  * Go to https://github.com/ankidroid/Anki-Android/tags click "Edit release notes" drop APK over drop zone, press "Update release".
Running `./tools/release.sh public` can perform some of those steps effortlessly.

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