

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