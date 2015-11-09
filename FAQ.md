This document contains a list of frequently asked questions, it is intended to give 

# Installing AnkiDroid

### After installing the latest version of AnkiDroid, it starts crashing
Please first try uninstalling / re-installing the application, as this often fixes such problems. If the issue persists, see the [bug reports help page](https://ankidroid.org/docs/help.html).

### I cannot find AnkiDroid on Android Market. Is the app restricted in some way?

No AnkiDroid is not restricted in any way, see the [[installation guide|Installation]] for help with installing.

### I don't like the last update of AnkiDroid, how can I return to a previous version?

All versions are available as APK files, see the [[installation guide|Installation]]. Don't forget to [let us know](http://ankidroid.org/help.html) what bothers you, and we will try to fix it.

### How do I upgrade from version 1.x?
If you wish to upgrade from an old (2012 and older) 1.x version of AnkiDroid, please read these [1.x upgrading instructions](http://android.stackexchange.com/questions/116414/how-to-upgrade-my-ankidroid-flashcards-from-1-x-to-2-0).

# Using AnkiDroid
### Is there a manual?
Yes, starting from v2.3, tap the "Help" item in the side drawer in AnkiDroid to open the manual on your device, or you can open it directly [here](http://ankidroid.org/manual.html)

### How can I swap the front and back of my flashcards?
See the [AnkiDroid user manual](http://ankidroid.org/manual.html#reverseCards)

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

### How can I use custom fonts?
See the [AnkiDroid manual](http://ankidroid.org/manual.html#customFonts) for the new method of adding fonts.

The [[old method of using custom fonts|AnkiDroid-1.x-custom-fonts]] from AnkiDroid 1.x is no longer officially suppported because it is not robust, however it does generally still work, and users who wish to use it may continue to do so.


### How can I use selective study, e.g. to review certain chapters of a textbook?
The easiest way is to organize your material into subdecks using the browser in [Anki Desktop](FAQ#do-i-need-anki-desktop-too). You can create a new subdeck in AnkiDroid by clicking "create deck" then using the syntax "PARENTDECKNAME::NEWSUBDECKNAME", or alternatively create the deck on Anki Desktop and drag and drop it onto an existing deck.

Alternatively, in AnkiDroid you can study cards of only a particular tag by opening a deck, tapping the "custom study" button, and choosing "Limit to particular tags". This will automatically create a new [filtered deck](http://ankisrs.net/docs/manual.html#filtered) with the cards of the tag you specified.

### Changing the study options of a deck changes the options of my other decks?!

The new version of Anki Desktop introduced an "[option grouping feature](http://ankisrs.net/docs/manual.html#deck-options)". If you want different decks to have different study options, it is currently necessary to change the option group assigned to each deck ("Configuration set" in the AnkiDroid deck options). To create and delete option groups, it is currently necessary to use [Anki Desktop](FAQ#do-i-need-anki-desktop-too).


### How do I type the answer, not type the answer, hide the correct answer?
See the [AnkiDroid user manual](http://ankidroid.org/manual.html#typeInAnswer)


### My AnkiDroid files are stored on a SD card, how can I point AnkiDroid to them?
External SD cards are not officially supported by AnkiDroid, and we highly recommend using the storage built into your device. If you really must use an external SD card, please see [this issue on github](https://github.com/ankidroid/Anki-Android/issues/3106).

### How do I make changes to my collection with Anki Desktop?
See the [AnkiDroid user manual](http://ankidroid.org/manual.html#AnkiDesktop)

### How can I remove unwanted tags from AnkiDroid?
You can remove tags that are not currently in use by any card by running "check collection" from the overflow menu in the AnkiDroid deck list. If you want to remove tags that are used by some cards, you'll need to remove the tags from those cards before running check collection. See [this forum thread](https://groups.google.com/d/topic/anki-android/Kuu2UFnKZNQ/discussion) for more information on doing that.

### I cannot change preferences / they get reset after some time

This is an Android problem and AnkiDroid needs to be reset. Go to system preferences --> applications --> manager applications --> AnkiDroid and choose "delete data".

### How to use different Anki profiles
If several people use the same device, they can use a different Anki profile, by installing parallel versions of AnkiDroid besides the normal AnkiDroid and setting a different collection path. Parallel versions of AnkiDroid can be found here (note: this is no longer maintained): https://github.com/trashcutter/Anki-Android/releases The settings, sync account, and decks will be kept separate.

To use 2 profiles, install the normal AnkiDroid AND flavor1. To use 3 profiles, install the normal AnkiDroid AND flavor1 AND flavor2. Etc.

You can build your own parallel versions of AnkiDroid by following the [instructions in the Development Wiki](https://github.com/ankidroid/Anki-Android/wiki/Development-Guide#making-parallel-builds)

### Lost my decks
It appears like your decks have disappeared? Don't burn your phone yet: AnkiDroid does regular backups, so you will probably be able to recover your decks. See the [backups section of the manual](http://ankidroid.org/docs/manual.html#backups) for more info.

### Help! My collection is corrupted!
Corruption happens mostly when users copy the main collection.anki2 file while it is still opened. AnkiDroid has a backup system which saves your collection file every day into the subfolder "backup". If your collection becomes corrupted, you can always [restore from one of the these backups](FAQ#lost-my-decks).

Also see the main Anki documentation for more info: http://ankisrs.net/docs/manual.html#_corrupt_collections

### How can I use Zeemote?
While we do not officially support the [Zeemote JS1 Controller](http://zeemote.com/), some users have noted that they could successfully pair AnkiDroid with the Zeemote using a 3rd party app called  [Bluez IME](https://play.google.com/store/apps/details?id=com.hexad.bluezime).

A very old development version of AnkiDroid did once have native support for Zeemote, however it was removed as the libraries were not open source, and therefore incompatible with AnkiDroid's GPL License. There was an effort to add support for Zeemote and other controllers via plugins. Unfortunately at the time it involved some work merging with the new version, which never got done due to time constraints, and now it's slipped even further away.

If someone would like to continue that work:
[Here is the unmerged commit](https://www.google.com/url?q=https%3A%2F%2Fgithub.com%2Fankidroid%2FAnki-Android%2Fcommit%2Ff899cb98f6fb90c1b4d5d48aff786e813adcf237&sa=D&sntz=1&usg=AFQjCNGmi_WBEvhq55r7DGHEwwmTb-JaWA) for the plugin interface for controllers
and [here is the plugin for the Zeemote controller](https://www.google.com/url?q=https%3A%2F%2Fgithub.com%2Finiju%2FAnkiDroid-Zeemote&sa=D&sntz=1&usg=AFQjCNF8ASmfUyI_oM-1mMq1lhBYbqGfRQ)

# Media

### How can I use media files on AnkiDroid?
The recommended way is to use Anki Desktop, which has a comprehensive media editor function. Please see the [above FAQ](FAQ#how-do-i-make-changes-to-my-collection-with-anki-desktop) on how to edit your collection on Anki Desktop.

Starting from AnkiDroid v2.2, it is also possible to add pictures and sounds to your deck directly from AnkiDroid, by editing a card and tapping the media button on the right of a field. Although the video is a bit outdated, some of the main features of this new media editor are shown in [this youtube video](http://youtu.be/bVh2xEYKvRI).

Note that when you attach media to a card on Anki Desktop, the program puts the media files into the collection.media folder (see [Anki Desktop manual for more info](http://ankisrs.net/docs/manual.html#media)). It is therefore necessary to ensure the option "Synchronize audio and images too" is enabled in both Anki and AnkiDroid if using Anki Web.

### I have too many media files and can't copy them all to my device
Unfortunately there is a limitation on the number of files which can be stored in one folder on the FAT filesystem used by default on most Android devices.

The best solution, if your device supports it, is to format your SD card  as ExFAT. You may need to take the SD card out and format it on your PC.

If your device does not support ExFAT, one possible workaround for this problem (requires root access), is to use a 3rd party app capable of mounting a different filesystem (for example from [Paragon](https://play.google.com/store/apps/details?id=com.paragon.mounter)). NTFS and Ext4 formats should also work.

While not officially supported, another possible solution may be to organize your media files into subfolders. Of course it is also necessary to update your notes to include the reference to the subfolder.

### Why doesn't my sound or image work on AnkiDroid
The most likely reason is that the media files are not in the main AnkiDroid `collection.media` folder. If you are using sync then please make sure that the "fetch media on sync" preference is enabled in all of your Anki clients, and if not then re-sync from all your clients.

If that doesn't work then please check if the media works correctly on both AnkiWeb and Anki Desktop. If you synced correctly with media sync enabled, and the media does not play on either AnkiWeb or Anki Desktop, then there is probably a problem with the cards themselves. Please read the Anki Desktop manual, or ask on the [Anki Desktop support site](http://ankisrs.net/docs/help.html) for help resolving this problem.

If you have checked that you have correctly done a media sync, and that the media is playing correctly on both Anki Desktop and AnkiWeb but not on AnkiDroid, then use a file manager like "ES File Explorer" to check that the media files exist in your `AnkiDroid/collection.media` folder. If the media files do not exist then obviously AnkiDroid cannot play them. You can try manually copying them from Anki Desktop as a last resort.

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

# Displaying special characters from other languages

### Instead of the correct character to display, a square is shown. Why is that?

Because, by default, Android does not have complete support of the full Unicode character set so it does not know how to display these characters. The part of Unicode that Android supports depends on the specific device and on where that device is distributed. If you would like to add support for some specific language, see the [custom fonts section of the user manual](https://ankidroid.org/docs/manual.html#customFonts).

# Advanced formatting tips
The [formatting wiki page](https://github.com/ankidroid/Anki-Android/wiki/Advanced-formatting) gives examples of how to achieve advanced flashcard formatting such as:

* Center align cards
* Hide replay audio button
* Customize colors in night-mode
* and much more

# Other questions

Please refer to the main [Help](http://ankidroid.org/docs/help.html) page.