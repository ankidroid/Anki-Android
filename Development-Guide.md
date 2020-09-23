### Getting Started

Feel free to join our Discord: [#dev-ankidroid](https://discord.gg/qjzcRTx) to join the community, or if you need help with the below.


### Table of Contents
* [**Source code**](#Source-code)
  - [Android Studio](#Android-Studio)
  - [AnkiDroid source code overview: Where to find what](#ankidroid-source-code-overview-where-to-find-what)
  - [Issues to get started with](#issues-to-get-started-with)
  - [Submit Improvements](#submit-improvements)
  - [Git workflow](#git-workflow)
  - [Inital setup (one time)](#inital-setup-one-time)
  - [Making a new pull request](#making-a-new-pull-request)
  - [Dealing with merge conflicts](#dealing-with-merge-conflicts)
  - [Running automated tests](#running-automated-tests)
  - [Compiling from the command line](#compiling-from-the-command-line)
  - [Handling translations](#handling-translations)
  - [Making parallel builds](#making-parallel-builds)
  - [Anki database structure](#anki-database-structure)
  - [Branching Model](#branching-model)
  - [Localization Administration](#localization-administration)
* [Other development tools](#other-development-tools)
  - [SQLite browser](#sqlite-browser)
  - [HTML javascript inspection](#html-javascript-inspection)
* [Checking database modifications](#checking-database-modifications)
* [To do from time to time](#to-do-from-time-to-time)
  - [Licenses](#licenses)
  - [Download localized strings](#download-localized-strings)
  - [Alternative markets](#download-localized-strings)
---

# Source code
First, register here on GitHub, and follow the [instructions](https://help.github.com/articles/fork-a-repo/) on GitHub on the Anki-Android repository to fork and clone the code. If you want to be notified about each new improvement/bugfix, please subscribe to the [commits feed for the master branch](https://github.com/ankidroid/Anki-Android/commits/master.atom).

## Android Studio
The next step is to install [Android Studio and the Android SDK](https://developer.android.com/sdk/index.html). Open Android Studio and choose "Open Project", then select the folder where you earlier cloned the github repository to (we will refer to this folder as `%AnkiDroidRoot%`).

On opening the project it should start to build and should eventually prompt you to install the following missing SDK components. Install them one by one as you get prompted:

  * Android SDK Build-tools and Android SDK platform (version must match "buildToolsVersion" and "compileSdkVersion" in the project's [build.gradle file](https://github.com/ankidroid/Anki-Android/blob/master/AnkiDroid/build.gradle))
  * Android Support Repository (latest version)
  * Android Support Library (latest version)

After installing all these dependencies, the project should build successfully.

## Installing an emulator for testing
Provided you are able to use hardware acceleration for the Android emulators, [setting up an emulator](https://developer.android.com/tools/devices/managing-avds.html) is a very effective method for testing code and running the connected android tests. If possible, verify your work periodically against both the newest version of the emulator available and the oldest version we support that you can run (at this time API18 is the oldest emulator easy to run though [it is possible to run all the way down to API15 on linux at least with some effort](https://issuetracker.google.com/issues/37138030#comment13).

## Connecting your device to Android studio via adb to use for testing
In order to run a custom build of AnkiDroid on your device or attach the debugger, you'll need to connect your device over the adb protocol. Open your Android device, and [enable](https://developer.android.com/studio/debug/dev-options.html) developer options and `USB debugging`. Finally when you connect your device to your computer over USB, it should get recognized by Android Studio and you should start to see output from it in the logcat section of Android studio.

## Running AnkiDroid from within Android studio
Connect your device to your computer, or [setup an emulator](https://developer.android.com/tools/devices/managing-avds.html), then select `Run -> Run 'AnkiDroid'` from the menu in Android Studio. This will compile a version of AnkiDroid suitable for testing (i.e. signed with a debug key), and pop up a window where you can select the device or emulator that you want to install and run the code on. See the main [Android developer documentation](https://developer.android.com/tools/building/building-studio.html) for more detailed information. 

Make sure that your Run Configuration for the AnkiDroid Project is set to use the APK from the app bundle instead of the Default APK (`Run -> Edit Configurations -> Deploy (select 'APK from app bundle')`. 

An apk file signed with a standard "debug" key will be generated named `"AnkiDroid-debug.apk"` in:
`%AnkiDroidRoot%/AnkiDroid/build/outputs/apk/`

## AnkiDroid source code overview: Where to find what
 * `/AnkiDroid/src/main/` is the root directory for the main project (`%root`)
 * `%root/java/com/ichi2/anki/` is the directory containing the main AnkiDroid source code.
 * `%root/java/com/ichi2/anki/DeckPicker.java` is the code which runs when starting AnkiDroid from the Android launcher.
 * `%root/java/com/ichi2/libanki/` contains the underlying Anki code which is common to all Anki clients. It is ported directly from the [Anki Desktop python code](https://github.com/dae/anki/tree/master/anki) into Java.
   * Because `libanki` code comes upstream from the common Anki code, edit it only when necessary. Keeping the code consistent with the common core makes it easier to integrate future upgrades.
 * `%root/assets/ ` contains the flashcard.css and HTML templates included with each flash card
 * `%root/res/` contains the [Android resource XML files](http://developer.android.com/guide/topics/resources/providing-resources.html) for the project.
 * `%root/res/values/` contains app strings, whiteboard colors, and a basic HTML template for flashcards.
 * `%root/res/layout/` contains the GUI layouts for most screens.
 * `%root/res/drawable-****/` contains the icons used throughout the app at [various resolutions](https://www.google.com/design/spec/style/icons.html).

## Issues to get started with
If you are a new developer looking to contribute something to AnkiDroid, but you don't know what work to get started with, please take a look and see if there's anything that you'd like to work on in the issue tracker. In particular, [issues with the label "Help Wanted"](https://github.com/ankidroid/Anki-Android/issues?q=is%3Aopen+label%3A%22Help+Wanted%22) or are tasks that have been specially highlighted as work that we'd really like to have done, but don't have time to do ourselves, and the similar "Good First Issue" label has been added to any tasks that seem like a good way to get started working with the codebase.

If it's unclear exactly what needs to be done, or how to do it, please don't hesitate to ask for clarification or help!

## Submit improvements

Once you have improved the code, commit it and send a pull request to [AnkiDroid Github Repository](https://github.com/ankidroid/Anki-Android). It will then be accepted after the code has been reviewed, and the enhanced application will be available on the Android Market on the next release. See [the branching model section](https://github.com/ankidroid/Anki-Android/wiki/Release-procedure#development-lifecycle) if you are unsure which branch to push to.

If you have trouble with Git, you can paste the changed files as text to the [forum](http://groups.google.com/group/anki-android) or github issue tracker.

## Git workflow
Git can be a bit complicated to use in the beginning. This section describes the workflow that we recommend for regular contributors. The following assumes that you're using either linux, or the [linux subsystem for Windows](https://msdn.microsoft.com/en-us/commandline/wsl/about). We also assume you're [using SSH to authenticate with github](https://help.github.com/articles/generating-a-new-ssh-key/) (highly recommended) and that you've already forked the AnkiDroid repository on github.

### Inital setup (one time)
First let's set up our local repository

```
git clone git@github.com:GITHUB_USERNAME/Anki-Android.git AnkiDroid
cd AnkiDroid
git remote add upstream https://github.com/ankidroid/Anki-Android.git
```

### Making a new pull request
Now if want to make a new change to the code base, we create a new 'feature branch' based off the latest version of the master branch:

```
git checkout master
git pull upstream master
git checkout -b my-feature-branch 
# make your changes to the source code
git push origin HEAD
```

On navigating to the [main AnkiDroid repository](https://github.com/ankidroid/Anki-Android), github will now pop up a message asking you if you want to create a new pull request based on the branch that you just pushed.

### Dealing with merge conflicts
If changes are made to the AnkiDroid repository that conflict with the changes in your pull request in-between creating the feature branch and your changes getting merged into the main repository, it may be necessary to rebase your code:

```
git checkout master
git pull upstream master
git checkout my-feature-branch # or just "git checkout -" to save typing
git rebase master
# it may be necessary to resolve merge conflicts here
# if you need to update the existing pull request, you should do a 'force' push
git push origin HEAD -f
```

## Running automated tests

### Unit tests
There are unit tests defined in the `AnkiDroid/src/test` directory, with [an extendable test superclass available using the Robolectric framework](https://github.com/ankidroid/Anki-Android/blob/master/AnkiDroid/src/test/java/com/ichi2/anki/RobolectricTest.java) to make standard Android services available, including sqlite so you can operate on Anki Collections in your tests - each Collection created on the fly prior to each test method and deleted afterwards for isolation. You can run these tests by selecting them directly from AndroidStudio for individual tests or all tests from one file, or you can run them from the command line and generate a coverage report to verify the effect of your testing from the command line using:
```
./gradlew jacocoUnitTestReport
```

Afterwards you should find the coverage report in `%AnkiDroidRoot%/AnkiAndroid/build/reports/jacoco/jacocoUnitTestReport/html/index.html`

### On-device integration tests
In addition to the unit tests, several integration tests are defined in the `AnkiDroid/src/androidTest` folder. You can run the tests from within Android Studio by simply right clicking on the test and running it against the chosen connected Android device (be sure to choose the icon with the Android symbol if there are multiple options shown), or from the command line against all connected devices at once using
```
./gradlew jacocoTestReport
```
After this you should find a coverage report that integrates unit and integration test execution in `%AnkiDroidRoot%/AnkiAndroid/build/reports/jacoco/jacocoTestReport/html/index.html`

**Note:** Some of the connected tests involve the deletion of models, which will force a full-sync, so it's not recommended to try running the tests on your main device.

## Compiling from the command line
If you have the Android SDK installed, you should be able to compile from the command line even without installing Android Studio.

**Windows:** Open a command prompt in `%AnkiDroidRoot%` and type:
```
gradlew.bat assembleDebug
```

**Linux & Mac:** Open a terminal in `%AnkiDroidRoot%` and type:
```
./gradlew assembleDebug
```

An apk file signed with a standard "debug" key will be generated named `"AnkiDroid-debug.apk"` in:
`%AnkiDroidRoot%/AnkiDroid/build/outputs/apk/`

## Handling translations
As described in the [contributing wiki](https://github.com/ankidroid/Anki-Android/wiki/Contributing#translate-ankidroid), AnkiDroid localization is done through the Crowdin platform. Developers should basically ignore all resource folders that have non-English locales. Edit the English strings only, and one of the project owners will handle the syncing of the translations. The process works as follows:

* Developers can freely add, delete, or modify strings in English to the resources folder (`values/`) and commit to git.
  * Renaming translation keys should be avoided if at all possible if the use is the same as it causes re-translation (appears as a delete-old/add-new to translators).
  * If you update the value of an existing key it *will not* be marked as needing translation. Existing translations are preserved. If you need to change a string significantly, meaning it needs re-translation, you must use a new key and remove the old key.
* A project owner will run a script that pushes those changes to the crowdin platform
* Translators will see any new untranslated strings on crowdin and submit their translations
* Before release a project owner will run another script which pulls all the current translations from crowdin and overwrites the existing files
* These new files are then committed and pushed to github

### Translation Conventions

* Avoid ending a string with a full stop unless there are multiple sentences
* Plurals should use `<plurals`. Only define `one` and `other` for English as CrowdIn handles other plural types

## Making "parallel" builds
If you want to run several different versions of AnkiDroid side by side (e.g. as described in the [FAQ on using profiles](https://github.com/ankidroid/Anki-Android/wiki/FAQ#how-to-use-different-anki-profiles)), you need to edit the package ID (from com.ichi2.anki) in the following places so that every version of AnkiDroid that you install has a unique ID:

* `applicationId` in `AnkiDroid/build.gradle`
*  `android:authorities="com.ichi2.anki.flashcards"` and `com.ichi2.anki.permission.READ_WRITE_DATABASE` in `%AnkiDroidRoot/AndroidManifest.xml`
* `android:targetPackage="com.ichi2.anki"` in `%AnkiDroidRoot/res/xml/*`
* and optionally the application name `<string name="app_name">A.AnkiDroid</string>` in `%AnkiDroidRoot/res/values/constants.xml`

There is a convenient bash script that automates this process, which you can run from a bash shell from the top level AnkiDroid directory as follows:

`./tools/parallel-package-name.sh com.ichi2.anki.a AnkiDroid.A`

After running the script you need to compile the source code to a .apk file as explained elsewhere in this guide. Note that these alternate builds will not work with the [AnkiDroid API](https://github.com/ankidroid/Anki-Android/wiki/AnkiDroid-API) as the API expects the standard ID.

## Anki database structure
See the [[separate wiki page|Database-Structure]] for a description of the database structure

## Branching Model
See the [[Release Procedure Wiki Page|Release-Procedure]]

## Localization Administration
Updating the master strings from Git to Crowdin is a pretty delicate thing. Uploading an empty string.xml for instance would delete all translations. And uploading changed strings delete as well all translations. This is the desired behavior in most cases, but when just some English typos are corrected this shouldn't destroy all translations.

In this case, it's necessary to:

  1. rebuild a download package at first (option "r" in script [currently broken])
  1. download all translations (update-translations.py)
  1. upload the changed strings
  1. reupload the translations (option "t" and language "all").

# Other development tools

## SQLite browser
A tool like "SQLite Database Browser" is very useful to understand how your "collection.anki2" file is made, and to test SQL queries. To install it on Ubuntu:
```
sudo apt-add-repository ppa:linuxgndu/sqlitebrowser
sudo apt-get update
sudo apt-get install sqlitebrowser
```

Binaries for Windows and Mac can be found [here](https://github.com/sqlitebrowser/sqlitebrowser/releases).

## HTML javascript inspection
Sometimes you need to look at the HTML and javascript generated by AnkiDroid as it displays cards. There are two ways to do this, either by looking at the raw HTML as a file or by watching it live on the device

First you'll need to enable **HTML/Javascript debugging**, by enabling HTML/Javascript debugging in Advanced Preferences.

Then:

### Via html file
To look at the HTML as a file, open the card you want to inspect in the reviewer, and at the same time browse to `/sdcard/AnkiDroid` - you should see `card.html`. The file will be overwritten fresh every time a question or answer is displayed in the reviewer.

`card.html` can be copied to your PC for inspection. You should also consider copying the `collection.media` folder to help diagnose missing media errors

### Via Chrome Development Tools
To look at the HTML & JavaScript live you can use [Chrome WebView Remote debugging](https://developers.google.com/web/tools/chrome-devtools/remote-debugging/webviews).

* Ensure that AnkiDroid's **HTML/Javascript debugging** is enabled.
* Open the reviewer with the card you want to inspect
* [Enable USB Debugging](https://developer.android.com/studio/debug/dev-options) via the Android System Menu
* Use USB to connect your PC and phone
* Using Chrome on the same PC, browse to: `chrome://inspect` and you should see the WebView on your phone/emulator. 
* Click `Inspect` for that WebView and you'll get a full Chrome remote debugging console.

# Checking database modifications

On Ubuntu Linux:

1. Install sqlite3 as above and meld: sudo apt-get install sqlite3 meld.
1. Make sure my desktop and android have about the same clock time.
1. Import the same shared deck to both.
1. Perform the same review sequence on both at the same time.
1. Copy the modified decks for comparison.
1. Run the following command and check that times are not too different, and that are no other differences:
```bash
echo .dump | sqlite3 desktop_collection.anki2 > desktop.dump
echo .dump | sqlite3 android_collection.anki2 > android.dump
diff desktop.dump android.dump
```

# To do from time to time
In addition to maintaining the issues tracker, here are a few things that someone should perform once in a while, maybe every few months or so.

## Licenses
1. Check that all files mention the GNU-GPL license.
1. Add it to those who don't.

## Download localized strings
1. From AnkiDroid's top directory, run ```./tools/update-localizations.py```.
1. To build a new package on Crowdin, the script needs the Crowdin API key. Ask on the mailing list and we will send it to you.
1. Commit and push.

## Alternative markets
1. Check whether the versions are AnkiDroid's latest release. If not, contact the person responsible for this Market.
1. Look for new alternative markets (especially in non-English languages) and upload there (please update the Wiki then).