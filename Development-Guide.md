### Getting Started

We always welcome any contributors, and you can start contributing immediately if you desire so. Feel free to join our Discord: [#dev-ankidroid](https://discord.gg/qjzcRTx) to join the community, or if you need help with the below.  We are all [volunteers](https://github.com/ankidroid/Anki-Android/wiki/OpenCollective-Payment-Process). 

This page contains information on how you can contribute back to the AnkiDroid project. You do not have to read all of it, just come back when you need an answer to a commonly asked question. 

### Table of Contents
* [**Getting Started**](#getting-started)
  - [Community](#community)
  - [Issues to get started with](#issues-to-get-started-with)
  - [Common tasks leading to accepted Pull Requests](#common-tasks-to-get-started)
  - [Submitting feature requests](#submitting-feature-requests)
* [**Source code**](#Source-code)
  - [Android Studio](#Android-Studio)
  - [AnkiDroid source code overview: Where to find what](#ankidroid-source-code-overview-where-to-find-what)
  - [Issues to get started with](#issues-to-get-started-with)
  - [Submit Improvements](#submit-improvements)
  - [Git workflow](#git-workflow)
  - [Initial setup (one time)](#initial-setup-one-time)
  - [Making a new pull request](#making-a-new-pull-request)
  - [Dealing with merge conflicts](#dealing-with-merge-conflicts)
  - [Running automated tests](#running-automated-tests)
  - [Compiling from the command line](#compiling-from-the-command-line)
  - [Adding translations](#adding-translations)
  - [Handling translations](#handling-translations)
  - [Making parallel builds](#making-parallel-builds)
  - [Anki database structure](#anki-database-structure)
  - [Branching Model](#branching-model)
  - [Localization Administration](#localization-administration)
  - [Code Coverage](#code-coverage)
* [Other development tools](#other-development-tools)
  - [SQLite browser](#sqlite-browser)
  - [Dependabot](#dependabot)
  - [HTML javascript inspection](#html-javascript-inspection)
  - [Custom Search Engines](#custom-search-engines)
* [Checking database modifications](#checking-database-modifications)
* [To do from time to time](#to-do-from-time-to-time)
  - [Licenses](#licenses)
  - [Download localized strings](#download-localized-strings)
  - [Alternative markets](#download-localized-strings)
---

# Getting Started

## Community

Feel free to join our Discord Server: [Anki](https://discord.gg/qjzcRTx) to join our community. This is shared between the Anki Desktop app and Mobile apps

A few relevant channels are:
* `#mobile-apps` - Help for using AnkiDroid and AnkiMobile
* `#dev-ankidroid` - General AnkiDroid Development discussions
* `#ankidroid-gsoc` - Discussions/introductions related to onboarding and the Google Summer of Code program
* `#help` - General Anki help

If it's unclear exactly what needs to be done, or how to do it, please don't hesitate to ask for clarification or help, ensuring that you skim our guides beforehand to see if your question has already been asked.

## Issues to get started with
If you are a new developer looking to contribute something to AnkiDroid, please take a look and see if there's anything that you'd like to work on in the [issue tracker](https://github.com/ankidroid/Anki-Android/issues). 

[The "Good First Issue" label](https://github.com/ankidroid/Anki-Android/labels/Good%20First%20Issue%21) has been added to any tasks that seem like a good way to get started working with the codebase.

If you are looking to make a lasting impact on the project from the get-go, [issues with the label "Help Wanted"](https://github.com/ankidroid/Anki-Android/issues?q=is%3Aopen+label%3A%22Help+Wanted%22) are tasks that we'd really like to have done but don't yet have time to do ourselves. 

Please let us know that you're working on an open issue and let us assign it to you, this ensures that two people aren't working on the same issue, and ensures that all effort is valuable to the project. If an issue hasn't seen activity in a couple of weeks, feel free to ping the assignee to see if they're working on it, or ask a maintainer to reassign the issue to you.

If you can't work on an issue any more, please post another message to let us know. 

If it's unclear exactly what needs to be done, or how to do it, please don't hesitate to ask for clarification or help!

## Common tasks to get started

Adding code documentation is helpful, and easy to get started with. This includes adding Javadocs to classes, members, and methods, and adding annotations such as `@Nullable` and `@NotNull` to Java, or `@CheckResult` to both Kotlin or Java code.

Increasing [test coverage](https://github.com/ankidroid/Anki-Android/wiki/Development-Guide#code-coverage) is extremely welcomed, as it will considerably improve the future of the codebase.

## Submitting feature requests

If you want to create your own feature, please post a feature request [via an issue](https://github.com/ankidroid/Anki-Android/issues/new?assignees=&labels=&template=feature_request.md&title=) so that the core contributors can confirm whether it would be accepted. 

# Source code
First, register here on GitHub, and follow the [instructions](https://help.github.com/articles/fork-a-repo/) on GitHub on the Anki-Android repository to fork and clone the code. If you want to be notified about each new improvement/bugfix, please subscribe to the [commits feed for the master branch](https://github.com/ankidroid/Anki-Android/commits/master.atom).

## Android Studio
The next step is to install [Android Studio and the Android SDK](https://bit.ly/3wFIesQ). Open Android Studio and choose "Open Project", then select the folder where you earlier cloned the Github repository to (we will refer to this folder as `%AnkiDroidRoot%`).

On opening the project it should start to build and should eventually prompt you to install the following missing SDK components. Install them one by one as you get prompted:

  * Android SDK Build-tools and Android SDK platform (version must match "buildToolsVersion" and "compileSdkVersion" in the project's [build.gradle file](https://github.com/ankidroid/Anki-Android/blob/master/AnkiDroid/build.gradle))
  * Android Support Repository (latest version)
  * Android Support Library (latest version)

After installing all these dependencies, the project should build successfully.

## Installing an emulator for testing
Provided you are able to use hardware acceleration for the Android emulators, [setting up an emulator](https://developer.android.com/tools/devices/managing-avds.html) is a very effective method for testing code and running the connected android tests. If possible, verify your work periodically against both the newest version of the emulator available and the oldest version we support that you can run (at this time API18 is the oldest emulator easy to run though [it is possible to run all the way down to API15 on linux at least with some effort](https://issuetracker.google.com/issues/37138030#comment13)).

## Connecting your device to Android studio via adb to use for testing
In order to run a custom build of AnkiDroid on your device or attach the debugger, you'll need to connect your device over the adb protocol. Open your Android device, and [enable](https://developer.android.com/studio/debug/dev-options.html) developer options and `USB debugging`. Finally when you connect your device to your computer over USB, it should get recognized by Android Studio and you should start to see output from it in the logcat section of Android studio.

If you don't want to test on your actual collection, you just have to rename the `AnkiDroid` folder to any other name, such as `AnkiDroidBack` and restart AnkiDroid. Since AnkiDroid won't find an `AnkiDroid` folder, it will create one and assume it's a new collection. Note however that it will still have your AnkiWeb login and some preference settings. When you are done testing, you can rename this folder `AnkiDroidBack` to `AnkiDroid`.

## Running AnkiDroid from within Android studio
Connect your device to your computer, or [setup an emulator](https://developer.android.com/tools/devices/managing-avds.html), then select `Run -> Run 'AnkiDroid'` from the menu in Android Studio. This will compile a version of AnkiDroid suitable for testing (i.e. signed with a debug key), and pop up a window where you can select the device or emulator that you want to install and run the code on. See the main [Android developer documentation](https://developer.android.com/tools/building/building-studio.html) for more detailed information. 

Make sure that your Run Configuration for the AnkiDroid Project is set to use the APK from the app bundle instead of the Default APK (`Run -> Edit Configurations -> Deploy (select 'APK from app bundle')`. 

An apk file signed with a standard "debug" key will be generated named `"AnkiDroid-debug.apk"` in:
`%AnkiDroidRoot%/AnkiDroid/build/outputs/apk/`

## AnkiDroid source code overview: Where to find what
 * `/AnkiDroid/src/main/` is the root directory for the main project (`%root`)
 * `%root/java/com/ichi2/anki/` is the directory containing the main AnkiDroid source code.
 * `%root/java/com/ichi2/anki/DeckPicker.java` is the code which runs when starting AnkiDroid from the Android launcher.
 * `%root/java/com/ichi2/libanki/` contains the underlying Anki code which is common to all Anki clients. It is ported directly from the [Anki Desktop python code](https://github.com/ankitects/anki/tree/main/pylib/anki) into Java.
   * Because `libanki` code comes upstream from the common Anki code, edit it only when necessary. Keeping the code consistent with the common core makes it easier to integrate future upgrades.
 * `%root/assets/ ` contains the flashcard.css and HTML templates included with each flash card
 * `%root/res/` contains the [Android resource XML files](http://developer.android.com/guide/topics/resources/providing-resources.html) for the project.
 * `%root/res/values/` contains app strings, whiteboard colors, and a basic HTML template for flashcards.
 * `%root/res/layout/` contains the GUI layouts for most screens.
 * `%root/res/drawable-****/` contains the icons used throughout the app at [various resolutions](https://www.google.com/design/spec/style/icons.html).

## Submit improvements

Once you have improved the code, commit it and send a pull request to [AnkiDroid Github Repository](https://github.com/ankidroid/Anki-Android). It will then be accepted after the code has been reviewed, and the enhanced application will be available on the Android Market on the next release. See [the branching model section](https://github.com/ankidroid/Anki-Android/wiki/Release-procedure#development-lifecycle) if you are unsure which branch to push to.

If you have trouble with Git, you can paste the changed files as text to the [forum](http://groups.google.com/group/anki-android) or github issue tracker.

## Git workflow
Git can be a bit complicated to use in the beginning. This section describes the workflow that we recommend for regular contributors. The following assumes that you're using either linux, or the [linux subsystem for Windows](https://msdn.microsoft.com/en-us/commandline/wsl/about). We also assume you're [using SSH to authenticate with github](https://help.github.com/articles/generating-a-new-ssh-key/) (highly recommended) and that you've already forked the AnkiDroid repository on github.

### Initial setup (one time)
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
There are two kinds of test in AnkiDroid. Unit test and on-device integration test. The second one need a connected device or an emulated device. You can run both test set simultaneously from the command line with
```
./gradlew jacocoTestReport
```

### Unit tests
There are unit tests defined in the `AnkiDroid/src/test` directory, with [an extendable test superclass available using the Robolectric framework](https://github.com/ankidroid/Anki-Android/blob/master/AnkiDroid/src/test/java/com/ichi2/anki/RobolectricTest.java) to make standard Android services available, including sqlite so you can operate on Anki Collections in your tests - each Collection created on the fly prior to each test method and deleted afterwards for isolation. You can run these tests by selecting them directly from AndroidStudio for individual tests or all tests from one file, or you can run them from the command line and generate a coverage report to verify the effect of your testing from the command line using:
```
./gradlew jacocoUnitTestReport
```

Afterwards you should find the coverage report in `%AnkiDroidRoot%/AnkiDroid/build/reports/jacoco/jacocoUnitTestReport/html/index.html`

#### Run single unit test
Change `com.ichi2.anki.NoteEditorTest` with test class name.
```
./gradlew AnkiDroid:testPlayDebugUnitTest --tests com.ichi2.anki.NoteEditorTest
```

Afterwards you should find the coverage report in `%AnkiDroidRoot%/AnkiDroid/build/reports/tests/testPlayDebugUnitTest/index.html`

### On-device integration tests
In addition to the unit tests, several integration tests are defined in the `AnkiDroid/src/androidTest` folder. You can run the tests from within Android Studio by simply right clicking on the test and running it against the chosen connected Android device (be sure to choose the icon with the Android symbol if there are multiple options shown), or from the command line against all connected devices at once using
```
./gradlew jacocoAndroidTestReport
```
After this you should find a coverage report that integrates unit and integration test execution in `%AnkiDroidRoot%/AnkiDroid/build/reports/jacoco/jacocoTestReport/html/index.html`

**Note:** Some of the connected tests involve the deletion of models, which will force a full-sync, so it's not recommended to try running the tests on your main device.

### Lint

AnkiDroid defines custom lint rules to enforce code conventions and ensure that bugs are not introduced. We run these for every pull request on the `Code Quality Checks / Lint Release (pull_request)` check.

To run these manually, open a terminal (either in Android Studio, or in `%AnkiDroidRoot%`) and execute:

```
./gradlew lintRelease
```

### Ktlint

AnkiDroid uses Ktlint for ensuring coding style and standard for Kotlin files. Every pull request runs the Ktlint check.

To run it manually, open a terminal (either in Android Studio, or in `%AnkiDroidRoot%`) and execute:

```
./gradlew ktlintCheck
```

To format Kotlin files, run the following command in the terminal:

```
./gradlew ktlintFormat
```

Ensure that you run this command for formatting before sending a pull request.

### Troubleshooting step
If tests do not behave as expected, you can replace `./gradlew` by `./gradlew clean` to clean the directory before running test.

If on-device tests are slow or fail, it maybe because of conflict with your ankidroid collection. To avoid them, you can simply rename the "ankidroid" folder to "ankidroid_back" on your device when running test. You can then rename "ankidroid_back" to "ankidroid" when you are done

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

## Adding translations

AnkiDroid uses [string resources](https://developer.android.com/guide/topics/resources/string-resource) for all translatable strings. These are managed by a platform called [CrowdIn](https://crowdin.com/project/ankidroid).

A translation consists of a key (used to reference the translation from Java), and a value:

```xml
<string name="example_key">Example value</string>
```

1. Determine the correct resource XML file to edit ([Guide](https://github.com/ankidroid/Anki-Android/wiki/Translating-AnkiDroid#logic-of-the-separation-in-different-files)) and expand it in Android Studio
2. Add the string to the first file in the list (`/values`) and no other file.
3. Reference the string using the appropriate function 
   - Normally use `context.getString(R.string.example_key)`, some functions directly accept a string ID (`@StringRes int`).
   - If the item is a plural, see [Google's documentation for handling plurals](https://developer.android.com/guide/topics/resources/string-resource#Plurals)
4. Ignore any errors stating that these are missing translations after adding the string. If it compiles, it's fine. Maintainers will handle generating the strings after the pull request is completed.
5. Take a screenshot of the string in context and add it to the Pull Request
6. After the change is merged, a maintainer should add this screenshot to the CrowdIn string to help translators with context

<details><summary>Example of the correct file to modify</summary>

![demo](https://github.com/imaryandokania/catsapi/blob/master/Screenshot%202021-03-15%20at%2010.36.53%20AM.png)

</details>

### Translation Conventions

* Avoid ending a string with a full stop unless there are multiple sentences
* Plurals should use `<plurals`. Only define `one` and `other` for English as CrowdIn handles other plural types
* Keys names should be [lowercase using underscores](https://github.com/ankidroid/Anki-Android/wiki/Code-style#string-key-resources-must-be-all-lowercase-using-underscore-to-separate-words)
* Values should be unique in English. If the values are not unique, a `comment=""` attribute must be added to explain the differences to our translators. This is enforced by lint.

## Handling translations
As described in the [contributing wiki](https://github.com/ankidroid/Anki-Android/wiki/Contributing#translate-ankidroid), AnkiDroid localization is done through the Crowdin platform. Developers should basically ignore all resource folders that have non-English locales. Edit the English strings only, and one of the project owners will handle the syncing of the translations. The process works as follows:

* Developers can freely add, delete, or modify strings in English to the resources folder (`values/`) and commit to git.
  * Renaming translation keys should be avoided if at all possible if the use is the same as it causes re-translation (appears as a delete-old/add-new to translators).
  * If you update the value of an existing key it *will not* be marked as needing translation. Existing translations are preserved. If you need to change a string significantly, meaning it needs re-translation, you must use a new key and remove the old key.
* A project owner will run a script that pushes those changes to the crowdin platform
* Translators will see any new untranslated strings on crowdin and submit their translations
* Before release a project owner will run another script which pulls all the current translations from crowdin and overwrites the existing files
* These new files are then committed and pushed to GitHub

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

## Code Coverage

In march 2021, only 36% of the code were covered. That is that all of our test only runs 36% of the lines written. Erroneous change in the remaining 64% of lines would not be detected automatically and would impact user experience. One of the best way to improve long-term evolution of the code base would be to increase the coverage. You can go to https://codecov.io/gh/ankidroid/Anki-Android/tree/master/AnkiDroid/src/main/java/com/ichi2 and find functions and part of codes which are not yet tested, and write test for them. 


Not 100% of the code have to be covered. In particular, it is useless to cover part of the code that should be rewritten, or ported to rust. Use your own judgement or discuss with maintainers if you have got a doubt. 

Coverage can also be generated on your computer. This is mostly useful if you want to check that you correctly covered the code you expected to cover. You can do it by running tests with `./gradlew jacocoTestReport`  and looking at the report on `%AnkiDroidRoot%/AnkiDroid/build/reports/jacoco/jacocoUnitTestReport/html/index.html` in your browser


# Other development tools

## SQLite browser
A tool like "SQLite Database Browser" is very useful to understand how your "collection.anki2" file is made, and to test SQL queries. To install it on Ubuntu:
```
sudo apt-add-repository ppa:linuxgndu/sqlitebrowser
sudo apt-get update
sudo apt-get install sqlitebrowser
```

Binaries for Windows and Mac can be found [here](https://github.com/sqlitebrowser/sqlitebrowser/releases).

## Dependabot

If you are maintaining the project, you should monitor the dependabot PRs that come in against the "dependency-updates" branch.

The flow for dependency updates is a circle 

- dependabot makes PRs to [the dependency-updates branch](https://github.com/ankidroid/Anki-Android/tree/dependency-updates)
- if they pass CI and look good, a maintainer will merge them to the dependency branch
- periodically a maintainer will [make a PR](https://github.com/ankidroid/Anki-Android/pulls?q=is%3Apr+dependency+updates) to the main development branch from the dependency-updates branch (now with a few dependency updates, most likely)
- if the PR to the main development branch passes, typically it will be squash-merged to main development branch
- the maintainer will take a local clone of the dependency-updates branch, rebase it to the main development branch
- the locally up-to-date dependency updates branch will be force-pushed back to the github dependency-updates branch
- the cycle repeats: dependabot makes PRs to the dependency-updates branch, etc

If dependabot needs configuration changes (to ignore a dependency or a version), the configuration should be done in the [`.github/dependabot.yaml`](https://github.com/ankidroid/Anki-Android/blob/master/.github/dependabot.yml) file, not via any UI interaction. The difference is that the YAML config is reviewable text while the UI modifies some opaque database and thus is not manageable or reviewable.

## HTML javascript inspection
Sometimes you need to look at the HTML and javascript generated by AnkiDroid as it displays cards. There are two ways to do this, either by looking at the raw HTML as a file or by watching it live on the device

First you'll need to enable `Settings - Advanced - HTML/Javascript Debugging`.

Then:

### Via Chrome Development Tools
To look at the HTML & JavaScript live you can use [Chrome WebView Remote debugging](https://developers.google.com/web/tools/chrome-devtools/remote-debugging/webviews). This also works on the [chromium-based Edge](https://docs.microsoft.com/en-us/microsoft-edge/devtools-guide-chromium/remote-debugging/webviews#open-an-android-webview-in-devtools). 

* Ensure that AnkiDroid's setting: **Advanced - HTML/Javascript Debugging** is enabled.
* [Enable USB Debugging](https://developer.android.com/studio/debug/dev-options) via the Android System Menu
* Connect your PC and phone via USB cable and approve the connection to your computer
* Open AnkiDroid and review the card you want to inspect
* Using Chrome on the same PC, browse to: `chrome://inspect` and you should see the WebView on your phone/emulator [for Edge, open:`edge://inspect/#devices`]
* Click `Inspect` for that WebView and you'll get a full Chrome remote debugging console. From this remote console, you can send JavaScript commands to AnkiDroid and edit HTML/CSS. 

### Via html file
To look at the HTML as a file, open the card you want to inspect in the reviewer, and at the same time browse to `/sdcard/AnkiDroid` - you should see `card.html`. The file will be overwritten fresh every time a question or answer is displayed in the reviewer.

`card.html` can be copied to your PC for inspection. You should also consider copying the `collection.media` folder to help diagnose missing media errors

### Via Eruda Console for Mobile Browsers
https://github.com/liriliri/eruda

This can be used inside the AnkiDroid to view console log like Chrome dev tools.
* Add following lines to Front/Back of card templates
```
<script src="https://cdn.jsdelivr.net/npm/eruda"></script>
<script>eruda.init();</script>
```
* Save the templates and open deck again.
* At bottom right corner, there will be button to open console log
* [View Demo](https://user-images.githubusercontent.com/12841290/103353056-c00bdf80-4ae2-11eb-941e-e652e05e8345.gif)

## Custom Search Engines

Many browsers support a "custom search engine" function. These save time while developing. 

Instructions to add a Search Engine to Chrome: https://zapier.com/blog/add-search-engine-to-chrome/

| keyword | url                                                                                                                | Description                           |
|---------|--------------------------------------------------------------------------------------------------------------------|---------------------------------------|
| issue   | https://github.com/ankidroid/Anki-Android/issues/%s                                                                | Jump to Issue/PR                      |
| commit  | https://github.com/ankidroid/Anki-Android/commit/%s                                                                | Jump to commit hash                   |
| gc      | https://github.com/ankidroid/Anki-Android/search?q=%22Originally+reported+on+Google+Code+with+ID+%s%22&type=issues | Jump to Google Code Issue ID (rare, somtimes documented in source code) |
| acra    | https://couchdb.ankidroid.org/acralyzer/_design/acralyzer/index.html#/reports-browser/user/%s                      | Search for ACRA user UUID             |
| wiki    | https://github.com/ankidroid/Anki-Android/search?q=%s&type=wikis                                                   | Search GitHub Wiki for keyword        |
| acrarep | https://couchdb.ankidroid.org/acralyzer/_design/acralyzer/index.html#/report-details/%s                            | Go to ACRA report (rare)              |


Usage: 

* F6/Select Search Bar
* Type in keyword
* Tab
* Type in search term
* Enter


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