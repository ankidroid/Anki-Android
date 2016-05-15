This document contains a list of frequently asked questions, it is intended to give 

# Installing AnkiDroid

### After installing the latest version of AnkiDroid, it starts crashing
Please first try uninstalling / re-installing the application, as this often fixes such problems. If the issue persists, see the [bug reports help page](https://ankidroid.org/docs/help.html).

### I cannot find AnkiDroid on Android Market. Is the app restricted in some way?

No AnkiDroid is not restricted in any way, see the [[installation guide|Installation]] for help with installing.

### I don't like the last update of AnkiDroid, how can I return to a previous version?

All versions are available as APK files, see the [[installation guide|Installation]]. Don't forget to [let us know](http://ankidroid.org/help.html) what bothers you, and we will try to fix it.

### Is there a version for Android Wear?
Yes, there is an open source 3rd party app called [AnkiWear for AnkiDroid](https://github.com/wlky/AnkiDroid-Wear) that offers support for Android Wear.

### How do I upgrade from version 1.x?
If you wish to upgrade from an old (2012 and older) 1.x version of AnkiDroid, please read these [1.x upgrading instructions](http://android.stackexchange.com/questions/116414/how-to-upgrade-my-ankidroid-flashcards-from-1-x-to-2-0).

# Using AnkiDroid
### Is there a manual?
Yes, starting from v2.3, tap the "Help" item in the side drawer in AnkiDroid to open the manual on your device, or you can open it directly [here](http://ankidroid.org/manual.html)

### How can I swap the front and back of my flashcards?
See the [AnkiDroid user manual](http://ankidroid.org/manual.html#reverseCards)

### How can I study my cards exactly when I want to?!
By design, AnkiDroid uses a [spaced repetition algorithm](https://en.wikipedia.org/wiki/Spaced_repetition) to minimize the amount of wasted study time. You can create [custom study sessions](https://ankidroid.org/docs/manual.html#filtered) in AnkiDroid, where you can do extra study in addition to the cards recommended by the scheduling algorithm (for example if you have an upcoming test that you want to cram for) and other similar tasks, however if you find yourself using these features too often, you are probably not fully utilizing the power of spaced repetition.

From the Anki website:

> The main purpose of a simple flashcard program is to allow you to enter some content and then flip through the cards, like you could with paper flashcards.

> People looking for a simple flashcard program to help them study a few cards sometimes stumble across Anki. But Anki doesn't really fit the description of a simple flashcard program. Anki is built to implement spaced repetition, a system of scheduling the next reviews of information right before the information is likely to be forgotten, making learning much more efficient. It happens that flashcards are the best way to study using spaced repetition.

> People who are only looking for the “flashcards” part and not the “spaced repetition” part are likely to be frustrated by Anki. Spaced repetition is fairly simple on its face, but studying with Anki is different from what most people are used to, and the additional complexity will probably be unwelcome at first.

[Read more here](https://anki.tenderapp.com/kb/anki-ecosystem/can-i-disable-the-scheduling-algorithm)

### Is there an auto-sync feature?
Yes, it's disabled by default though so please see the [section in the manual](https://ankidroid.org/docs/manual.html#_ankidroid) about the automatic synchronization preference.

### Why do I get an error about the timezone when I try to sync?
AnkiWeb requires both the time and the timezone to be set correctly in order to synchronize properly. For most users, enabling "Automatic date & time" and "Automatic time zone" in the main Android date & time settings is enough to ensure that sync works correctly.

If you have automatic time & timezone enabled and are still getting an error message like "the time of your device is different than that of the server by xxx sec", it probably means that the timezone definitions on your device are out of date. Updating your device to the latest available version of Android may fix this issue; if not there are two possible solutions:

**Use 3rd party tool to automatically update timezone definitions (root required)**

If you are willing to [root your device](https://duckduckgo.com/?q=why%20root%20your%20phone), you can use this [TimeZone Fixer tool](https://play.google.com/store/apps/details?id=com.force.timezonefixer) which should automatically fix the issue.

**Manually set the time on your device**

If you are unable to update the timezone definitions on your device, you'll have to manually set the time on your device. To do this, go to the main Android date & time settings, and disable automatic date & time as well as automatic timezone. Now manually change the time by the amount specified in the message (e.g. if the message says 3560 seconds, try changing the time by +/- 1hr) and then try syncing again with this new (but incorrect) time. Once syncing is working again with the incorrect time, you can find a timezone which gives you the correct time while still allowing you to sync.

### Do I need Anki Desktop too?
AnkiDroid is designed primarily as a tool for reviewing cards created with Anki Desktop, rather than as a complete replacement for it. As such you will need [Anki Desktop](http://ankisrs.net/) to perform some tasks, especially if you want to perform operations on multiple cards at once, like change the deck, apply tags, etc

See the FAQ on [how to sync Anki Desktop with AnkiDroid](FAQ#how-do-i-make-changes-to-my-collection-with-anki-desktop) if you are unsure how to use Anki Desktop.


### How can I add pictures and sounds to my deck?
See the [Media section](FAQ#media) below.

### How can I use selective study, e.g. to review certain chapters of a textbook?
The easiest way is to organize your material into subdecks using the browser in [Anki Desktop](FAQ#do-i-need-anki-desktop-too). You can create a new subdeck in AnkiDroid by clicking "create deck" then using the syntax "PARENTDECKNAME::NEWSUBDECKNAME", or alternatively create the deck on Anki Desktop and drag and drop it onto an existing deck.

Alternatively, in AnkiDroid you can study cards of only a particular tag by opening a deck, tapping the "custom study" button, and choosing "Limit to particular tags". This will automatically create a new [filtered deck](http://ankisrs.net/docs/manual.html#filtered) with the cards of the tag you specified.

### Changing the study options of a deck changes the options of my other decks?!

Anki has an "[option grouping feature](http://ankisrs.net/docs/manual.html#deck-options)" which allows you to share options between multiple decks, and by default all decks share the same option group. If you want different decks to have different study options, you need to change the option group assigned to each deck by going to the "Group management" section in the deck options.

We realize that this is quite unintuitive for new users, but unfortunately this is currently the way that Anki Desktop works, and consistency with Anki Desktop is a very high priority for AnkiDroid.

### How do I type the answer, not type the answer, hide the correct answer?
See the [AnkiDroid user manual](http://ankidroid.org/manual.html#typeInAnswer)

### My AnkiDroid files are stored on a SD card, how can I point AnkiDroid to them?
External SD cards are not officially supported by AnkiDroid, and we highly recommend using the storage built into your device. If you really must use an external SD card, please see [this issue on github](https://github.com/ankidroid/Anki-Android/issues/3106).

### How do I make changes to my collection with Anki Desktop?
See the [AnkiDroid user manual](http://ankidroid.org/manual.html#AnkiDesktop)

### How can I remove unwanted tags from AnkiDroid?
You can remove tags that are not currently in use by any card by running "check database" from the overflow menu in the AnkiDroid deck list. If you want to remove tags that are used by some cards, you'll need to remove the tags from those cards before running check database. See [this forum thread](https://groups.google.com/d/topic/anki-android/Kuu2UFnKZNQ/discussion) for more information on doing that.

### I cannot change preferences / they get reset after some time

This is an Android problem and AnkiDroid needs to be reset. Go to system preferences --> applications --> manager applications --> AnkiDroid and choose "delete data".

### How to use different Anki profiles
On Anki desktop, if several people want to use the same computer (or if one person wants to separate their collection into several groups), they can use different profiles. AnkiDroid doesn't currently officially support using Anki profiles (though it's a [commonly requested feature](https://github.com/ankidroid/Anki-Android/issues/2545)), however you can achieve a similar effect by installing several parallel versions of AnkiDroid besides the normal AnkiDroid and setting a unique collection path in each one. 

Parallel versions of AnkiDroid are not officially maintained, but can be built from source code fairly easily with a build script, as described in the [Development Wiki](https://github.com/ankidroid/Anki-Android/wiki/Development-Guide#making-parallel-builds). The settings, sync account, and decks will be kept separate in each build (after changing the path of the AnkiDroid folder). If you are unable to make your own builds, you can use the semi-official pre-built apks for [v2.5beta16](https://github.com/ankidroid/Anki-Android/releases/tag/v2.5beta16) in the releases section. 

Note that we will not provide any support for these pre-built parallel versions, they are only given as a proof of concept for the build script.

### Lost my cards
Does it appear like your cards have disappeared? Don't burn your phone yet: AnkiDroid does [regular backups](http://ankidroid.org/docs/manual.html#backups), so you will probably be able to recover everything in the unlikely event that this occurs. First of all though, here are some common reasons why users may mistakenly think that their cards have been 'deleted' by the app:

1) Misunderstanding of how spaced repetition works

Some users that are unfamiliar with how Anki's spaced repetition algorithm works may mistake the fact that the cards stop appearing in the study screen to mean that their cards have been 'deleted' by the app. Before assuming that your cards have disappeared, please open the [card browser](https://ankidroid.org/docs/manual.html#browser), make sure that 'all decks' is selected and confirm there that your cards are really not there. If the cards *are* in fact there, you may like to read about the spaced repetition scheduling algorithm in the [Anki manual](http://ankisrs.net/docs/manual.html#introduction) and [this FAQ](https://github.com/ankidroid/Anki-Android/wiki/FAQ#how-can-i-study-my-cards-exactly-when-i-want-to).

2) User error when using the cloud sync service

Sometimes when using the [AnkiWeb cloud sync service](https://ankidroid.org/docs/manual.html#AnkiDesktop), situations can arise where Anki is unable to automatically resolve conflicts between your local and online collections. In this case AnkiDroid may ask you to manually resolve the conflict by downloading from or uploading to AnkiWeb. If you accidentally pushed the wrong button and lost some cards then you should [restore the previous version from a backup](https://ankidroid.org/docs/manual.html#backups) and refer to the section in the manual about [resolving conflicts](https://ankidroid.org/docs/manual.html#AnkiWebConflicts).

3) Data corruption

In some very rare cases the AnkiDroid database can get corrupted, usually due to some error with the file system, or users trying to use a removable SD card (which is **not** officially supported). In such cases AnkiDroid will guide you through the steps that you can take to restore your data, but if you make a mistake here by choosing the wrong option you can always restore from one of the [automatic backups](https://ankidroid.org/docs/manual.html#backups).

### Help! My collection is corrupted!
Database corruption can occasionally happen for a small minority of users for reasons unknown to us. If this happens to you, we recommend taking the following course of action:

1. Make a backup copy of the AnkiDroid folder on your device ASAP. Copy it to your PC and/or Google Drive so that our support team can still help you if the below steps fail.

1. Choose the "Restore from backup" option from the menu in the main deck list and select the newest backup which has a time stamp before the problem started occurring.

1. If the restore completed successfully, run "check database" and see if that also completes successfully. If so, you are done. If not, try restoring from an even older backup.

If restoring from a backup is not possible, you can try to manually "fix" the database by copying the collection.anki2 file in the AnkiDroid folder to your computer, and following the [instructions in the 
main Anki documentation](http://ankisrs.net/docs/manual.html#_corrupt_collections).

### How can I copy my decks from an old device to a new device?
**Method 1: Use AnkiWeb cloud sync (recommended)**

Follow the [instructions in the manual](https://ankidroid.org/docs/manual.html#_via_cloud_sync) for syncing existing decks (on your old Android device) into a new AnkiDroid install.

**Method 2: Export > Import**

[Export your collection](https://ankidroid.org/docs/manual.html#exporting) as a .apkg file from your old device, copy it to your new device using any method you like, and then import this file into your new device according to the [importing instructions](https://ankidroid.org/docs/manual.html#importing) in the manual.

### How can I use Zeemote?
While we do not officially support the [Zeemote JS1 Controller](http://zeemote.com/), some users have noted that they could successfully pair AnkiDroid with the Zeemote using a 3rd party app called  [Bluez IME](https://play.google.com/store/apps/details?id=com.hexad.bluezime).

A very old development version of AnkiDroid did once have native support for Zeemote, however it was removed as the libraries were not open source, and therefore incompatible with AnkiDroid's GPL License. There was an effort to add support for Zeemote and other controllers via plugins. Unfortunately at the time it involved some work merging with the new version, which never got done due to time constraints, and now it's slipped even further away.

If someone would like to continue that work:
[Here is the unmerged commit](https://www.google.com/url?q=https%3A%2F%2Fgithub.com%2Fankidroid%2FAnki-Android%2Fcommit%2Ff899cb98f6fb90c1b4d5d48aff786e813adcf237&sa=D&sntz=1&usg=AFQjCNGmi_WBEvhq55r7DGHEwwmTb-JaWA) for the plugin interface for controllers
and [here is the plugin for the Zeemote controller](https://www.google.com/url?q=https%3A%2F%2Fgithub.com%2Finiju%2FAnkiDroid-Zeemote&sa=D&sntz=1&usg=AFQjCNF8ASmfUyI_oM-1mMq1lhBYbqGfRQ)

### Can I use a hardware controller?
Basic keyboard shortcuts that exist on Anki Desktop should already work with a bluetooth keyboard. It should be possible to use other controllers by using a third party app to remap the controller buttons to keyboard events. See [here](https://github.com/ankidroid/Anki-Android/issues/3600) and [here](https://github.com/ankidroid/Anki-Android/issues/3021#issuecomment-160243369) for more information.

### Why do I get an error message about the size limit for a String in a database?
Unfortunately Android puts a [2MB limit](http://stackoverflow.com/questions/21432556/android-java-lang-illegalstateexception-couldnt-read-row-0-col-0-from-cursorw) on the size that a String can take in an sqlite database (the format used to store Anki collections). On AnkiDroid version 2.6+ we detect when this limit is exceeded and show an error message. The default limit on other platforms in 2GB, so it's very much an Android specific issue. 

Since all note types are stored together as one big string in the database, it's possible to exceed this limit if you have a large number of note types with a lot of formatting. The same limit applies to the deck descriptions, though this is far less likely to be the culprit. If you run into this limit, the solution is to delete some of the note types in your collection using Anki desktop, sync it with AnkiWeb, and then choose "full sync from server" in AnkiDroid, which should resolve the error.

# Media

### How can I use media files on AnkiDroid?
The recommended way is to use Anki Desktop, which has a comprehensive media editor function. Please see the [above FAQ](FAQ#how-do-i-make-changes-to-my-collection-with-anki-desktop) on how to edit your collection on Anki Desktop.

Starting from AnkiDroid v2.2, it is also possible to add pictures and sounds to your deck directly from AnkiDroid, by editing a card and tapping the media button on the right of a field. Although the video is a bit outdated, some of the main features of this new media editor are shown in [this youtube video](http://youtu.be/bVh2xEYKvRI).

Note that when you attach media to a card on Anki Desktop, the program puts the media files into the collection.media folder (see [Anki Desktop manual for more info](http://ankisrs.net/docs/manual.html#media)). It is therefore necessary to ensure the option "Synchronize audio and images too" is enabled in both Anki and AnkiDroid if using Anki Web.

### How can I add pictures from Google Images?
Since November 2015, Google have disabled their [free image search API](https://developers.google.com/web-search/?csw=1) that we were using. **There is no free replacement API** from Google that we could use, and the paid "custom search" API which they offer is not feasible for us to use in AnkiDroid. Unfortunately there's nothing that we can do about this.

Until we find a better solution, you'll have to manually add the images as following:

0. Open images.google.com in your browser
0. Search for the image that you want to add and open it
0. Long tap on the image and choose save
0. From AnkiDroid add a new image from the gallery and select the image that you just downloaded

Note: you may need to configure your gallery app to show images that you downloaded in addition to your photos.

### After updating my Android version, Image Occlusion is no longer working
Old versions of the [image occlusion plugin](https://ankiweb.net/shared/info/2006541756) for Anki desktop had a [bug](https://github.com/tmbb/image-occlusion-2/pull/12) that generated invalid .svg images. These invalid images were displayed without issue in older versions of Android, however newer versions of Android (or the Android system Webview in particular) are no longer able to display these invalid images.

Please uninstall / re-install the plugin in order to update to the latest version and ensure that new images generated by the plugin are displayed correctly on Android. 

In order to fix existing images, there is a [python script](https://github.com/mrestko/image-occlusion-svg-fix) created by Matt Restko which you can run. Please ensure that you have [python installed](https://www.python.org/downloads/) (version 2.7.x is recommended), and follow the instructions in his github repository. When the script finishes, it should say: 

```
Found m Image Occlusion files
Fixed n files
```

If m and n are both greater than 0 then the script has worked, and it's just a matter of [copying the files to your Android device](https://github.com/ankidroid/Anki-Android/wiki/FAQ#why-doesnt-my-sound-or-image-work-on-ankidroid). Please kindly understand that we cannot directly provide support on the image occlusion add-on or fixing broken svg images.

### I have too many media files and can't copy them all to my device
Unfortunately there is a limitation on the number of files which can be stored in one folder on the FAT filesystem used by default on most Android devices.

The best solution, if your device supports it, is to format your SD card  as ExFAT. You may need to take the SD card out and format it on your PC.

If your device does not support ExFAT, one possible workaround for this problem (requires root access), is to use a 3rd party app capable of mounting a different filesystem (for example from [Paragon](https://play.google.com/store/apps/details?id=com.paragon.mounter)). NTFS and Ext4 formats should also work.

While not officially supported, another possible solution may be to organize your media files into subfolders. Of course it is also necessary to update your notes to include the reference to the subfolder.

### Why doesn't my sound or image work on AnkiDroid
The most likely reason is that the media files are not in the main AnkiDroid `collection.media` folder. If you are using sync then please make sure that the "fetch media on sync" preference is enabled in all of your Anki clients, and if not then re-sync from all your clients.

If that doesn't work then please check if the media works correctly on both AnkiWeb and Anki Desktop. If you synced correctly with media sync enabled, and the media does not play on either AnkiWeb or Anki Desktop, then there is probably a problem with the cards themselves. Please read the Anki Desktop manual, or ask on the [Anki Desktop support site](http://ankisrs.net/docs/help.html) for help resolving this problem.

If you have checked that you have correctly done a media sync, and that the media is playing correctly on both Anki Desktop and AnkiWeb but not on AnkiDroid, then use a file manager (see below) to check that the media files exist in your `AnkiDroid/collection.media` folder. If the media files do not exist then obviously AnkiDroid cannot play them. You can try manually copying them from Anki Desktop as a last resort.

### Which file manager should I use?
You can use any file manager that you like, but we highly recommend using a free and open source (FOSS) file manager like [Simple Explorer](http://forum.xda-developers.com/showthread.php?t=2330864) (requires Android 4.1+), which has a beautiful interface and a lot of valuable features. Another good FOSS file explorer, which works even on Android 2.3 is [OI File Manager](https://play.google.com/store/apps/details?id=org.openintents.filemanager). We have found some other file managers to include spam, so recommend using only FOSS, especially if your device is rooted!

### But still AnkiDroid does not play my media!

The Android platform has built-in support for the following image formats:
```
JPEG (.jpg), GIF (.gif), PNG (.png) and BMP (.bmp)
```

the following audio formats:
```
3GPP (.3gp), MPEG-4 (.mp4, .m4a), 3GPP (.3gp), MP3 (.mp3), Type 0 and 1 (.mid, .xmf, .mxmf), RTTTL/RTX (.rtttl, .rtx), OTA (.ota), iMelody (.imy), Ogg (.ogg) and WAVE (.wav). 
```

and the following video formats (example video deck can be found [here](https://github.com/ankidroid/Anki-Android/raw/develop/tools/test-decks/video.apkg)):
```
.3gp files with H.263 or MPEG-4 SP codec, .mp4 files with H.263 codec
```

The format of your media file has to be one of these, or be in the list of [Android Core Media Formats](http://developer.android.com/guide/appendix/media-formats.html#core) for your device.

If you have verified that the media files are physically on your device, that they are in one of the supported formats, and that they are playing properly on Anki Desktop, one possibility is that you are using an [unsupported feature](http://ankisrs.net/docs/manual.html#importing-media) of Anki Desktop, where you code a pattern for the path of the file into the template -- i.e. if you have something like `[sound:{{Word}}]` in your template, this will probably not work on AnkiDroid, where it's necessary to explicitly have `[sound:pathtosoundfile]` in the field itself.

If you must use media references to fields, for example if you have a large number of notes and use a specific naming convention based on the content of certain fields, you should use the [search and replace feature](http://ankisrs.net/docs/manual.html#importing-media), or alternatively there is an [add-on available for Anki Desktop](https://github.com/timrae/anki-replaceall) which allows you to batch render an expression such as `[sound:{{Word}}.mp3]` into a new field. Be sure to backup before attempting to use this add-on.

### Instead of the correct character to display, a square is shown. Why is that?

Because, by default, Android does not have complete support of the full Unicode character set so it does not know how to display these characters. The part of Unicode that Android supports depends on the specific device and on where that device is distributed. If you would like to add support for some specific language, see the custom fonts section below.

### How can I use custom fonts?
See the [AnkiDroid manual](http://ankidroid.org/manual.html#customFonts) for the new method of adding fonts.

The [[old method of using custom fonts|AnkiDroid-1.x-custom-fonts]] from AnkiDroid 1.x is no longer officially suppported because it is not robust, however it does generally still work, and users who wish to use it may continue to do so.

### I followed the instructions in the manual but I still can't get my custom font to work
Here are some tips for debugging your custom font issue.

1. Use Android version 4.4+ without any custom ROM for best results
1. Make sure that you are using the latest version of the [Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview) (46.x at the time of writing)
1. Use the method recommended in the [manual](http://ankidroid.org/manual.html#customFonts) (**not** the alternative method)
1. Go to AnkiDroid settings > Fonts and ensure that "Default font" is set to "System default", and "Default font applicability" is set to "When no font specified on flashcard"
1. Check that the font shows correctly on both Anki Desktop and AnkiWeb
1. Check that the font file correctly exists in your AnkiDroid/collection.media folder and that it has the same name as the one in your collection.media folder in Anki Desktop (if you have disabled "fetch media on sync" on either client then your font will *not* be automatically copied to AnkiDroid)
1. Some fonts may have the extension .ttf, but are in fact in a different format such as .ttc, which has been found to not work on some devices. We recommend only using official open source fonts, such as Google Noto.
1. Try uninstalling and reinstalling AnkiDroid
1. Try using a different font for testing purposes, especially an official Google font like [Google Noto](https://www.google.com/get/noto/)
1. If you can get Google Noto working, but not your custom font, then it means that your font is not supported by your device. You can try to edit your font to make it compatible using [this trick](https://groups.google.com/d/msg/anki-android/svgWDMukz1s/XQFk0057AFcJ). If that doesn't work, you probably need to use a [different font](https://github.com/ankidroid/Anki-Android/wiki/Freely-distributable-fonts).

If you tried all of the above and still couldn't get it to work, then please post a new issue here or on the [support forum](https://groups.google.com/forum/#!forum/anki-android) including the following information:

0. Your AnkiDroid version, Android version, Android system webview version, phone model number, and details of any modifications that you have made to it
0. A sample deck (.apkg file) exported from Anki Desktop including *one card* that reproduces the issue you're having
0. The name and a link to the font that you are trying to use
0. The result of each step in the above process, preferably with some screenshots

# Advanced formatting tips
The [formatting wiki page](https://github.com/ankidroid/Anki-Android/wiki/Advanced-formatting) gives examples of how to achieve advanced flashcard formatting such as:

* Center align cards
* Hide replay audio button
* Customize colors in night-mode
* and much more

# Other questions

Please refer to the main [Help](http://ankidroid.org/docs/help.html) page.