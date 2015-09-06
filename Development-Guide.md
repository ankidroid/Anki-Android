# Source code
First, register here on github, and follow the [forking instructions](https://help.github.com/articles/fork-a-repo/) for the Anki-Android repository. If you want to be notified about each new improvement/bugfix, please subscribe to the [commits feed for the development branch](https://github.com/ankidroid/Anki-Android/commits/develop.atom).

The project has been configured to work "out of the box" with Android Studio, though you first need to set up the Android SDK as follows.

## Setting up the SDK
Before you can do anything with AnkiDroid, you should install [Android Studio and the Android SDK](https://developer.android.com/sdk/index.html), and make sure you have the following items checked in the Android SDK manager:

  * Android SDK Build-tools and Android SDK platform (version must match "buildToolsVersion" and "compileSdkVersion" in the project's [build.gradle file](https://github.com/ankidroid/Anki-Android/blob/develop/AnkiDroid/build.gradle))
  * Android Support Repository (latest version)
  * Android Support Library (latest version)

## Opening in Android Studio
After forking and cloning the Anki-Android git repository, open Android Studio and choose "Open Project", then select the folder which you cloned the github repository to (we will refer to this folder as `%AnkiDroidRoot%`). The project should start without error, and build automatically.


## Files: Where to find what
 * `/AnkiDroid/src/main/` is the root directory for the main project (`%root`)
 * `%root/java/com/ichi2/anki/` is the directory containing the main AnkiDroid source code.
 * `%root/java/com/ichi2/anki/DeckPicker.java` is the code which runs when first booting into AnkiDroid.
 * `%root/java/com/ichi2/libanki/` contains the underlying Anki code which is common to all Anki clients. It is ported directly from the [Anki Desktop python code](https://github.com/dae/anki/tree/master/anki) into Java.
 * `%root/assets/flashcard.css` contains the CSS file included with each flash card
 * `%root/res/` contains the [Android resource XML files](http://developer.android.com/guide/topics/resources/providing-resources.html) for the project.
 * `%root/res/values/` contains app strings, whiteboard colors, and a basic HTML template for flashcards.
 * `%root/res/layout/` contains the GUI layouts for most screens.
 * `%root/res/drawable-****/` contains the icons used throughout the app at [various resolutions](https://www.google.com/design/spec/style/icons.html).

## Running AnkiDroid from within Android studio
Connect your device to your computer, or [setup an emulator](https://developer.android.com/tools/devices/managing-avds.html), then select `Run -> Run 'AnkiDroid'` from the menu in Android Studio. This will compile a version of AnkiDroid suitable for testing (i.e. signed with a debug key), and pop up a window where you can select the device or emulator that you want to install and run the code on. See the main [Android developer documentation](https://developer.android.com/tools/building/building-studio.html) for more detailed information.

An apk file signed with a standard "debug" key will be generated named `"AnkiDroid-debug.apk"` in:
`%AnkiDroidRoot%/AnkiDroid/build/outputs/apk/`

## Making "parallel" builds
If you want to run several different versions of AnkiDroid side by side, you can do this by performing the following 4 steps:

* Change `applicationId` in the `AnkiDroid/build.gradle` from `"com.ichi2.anki"` to something else, like `"com.ichi2.a.anki"`
*  Open the `%root/AndroidManifest.xml` file, search for "authorities", and change the line that says `android:authorities="com.ichi2.anki.flashcards"` to `android:authorities="com.ichi2.a.anki.flashcards"` where `com.ichi2.a.anki` is the value you set for applicationId in step 1.
* Open the file `%root/res/values/constants.xml` and change the line `<string name="app_name">AnkiDroid</string>` to whatever you want the application name to be - for example `<string name="app_name">A.AnkiDroid</string>`
* Do a global search in `%root` for `android:targetPackage="com.ichi2.anki"` and replace with e.g. `android:targetPackage="com.ichi2.a.anki"`. You can do the search and replace with e.g. [grepwin](http://sourceforge.net/projects/grepwin/)

Compile and run the code as usual. Note that third party apps will probably not be able to use these alternate builds with the API.

## Submit improvements

Once you have improved the code, commit it and send a pull request to [AnkiDroid Github Repository](https://github.com/ankidroid/Anki-Android). It will then be accepted after the code has been reviewed, and the enhanced application will be available on the Android Market on the next release. See [the branching model section](https://github.com/ankidroid/Anki-Android/wiki/Release-procedure#development-lifecycle) if you are unsure which branch to push to.

If you have trouble with Git, you can paste the changed files as text to the [forum](http://groups.google.com/group/anki-android) or github issue tracker.

## Running unit tests
Several unit tests are defined in the `AnkiDroid/androidTest` folder. You can run the tests from within Android Studio by simply right clicking on the test and running it (be sure to choose the icon with the Android symbol if there are multiple options shown), or from the command line using
```
./gradlew connectedCheck
```

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

A tool like "SQLite Database Browser" is very useful to understand how your "collection.anki2" file is made, and to test SQL queries. To install it on Ubuntu:
```
sudo apt-get install sqlitebrowser
```

Binaries for Windows and Mac can be found [here](https://github.com/sqlitebrowser/sqlitebrowser/releases).


# Checking database modifications

On Ubuntu Linux:

1. Install sqlite3 and meld: sudo apt-get install sqlite3 meld.
1. Make sure my desktop and android have about the same clock time.
1. Copy country-capitals.anki to both.
1. Perform the same review sequence on both at the same time.
1. Copy the modified decks for comparison.
1. Run the following command and check that times are not too different, and that are no other differences:
```bash
echo .dump | sqlite3 desktop_collection.anki2 > desktop.dump
echo .dump | sqlite3 android_collection.anki2 > android.dump
diff desktop.dump android.dump
```

# To do from time to time
In addition to <a href='http://code.google.com/p/ankidroid/issues'>bugs and enhancements</a>, here are a few things that someone or another should perform once in a while, maybe every month or so.

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