This document contains a list of frequently asked questions.

# Installing AnkiDroid

### After installing the latest version of AnkiDroid, it starts crashing
Please first try uninstalling / re-installing the application, as this often fixes such problems. If the issue persists, see the [bug reports help page](https://ankidroid.org/docs/help.html).

Also see: https://github.com/ankidroid/Anki-Android/wiki/FAQ#i-dont-like-the-last-update-of-ankidroid-how-can-i-return-to-a-previous-version

### I cannot find AnkiDroid on Google Play. Is the app restricted in some way?

No, AnkiDroid is not restricted in any way, see the [[installation guide|Installation]] for help with installing.

### I don't like the last update of AnkiDroid, how can I return to a previous version?

All versions are available as APK files, see the [[installation guide|Installation]]. Don't forget to [let us know](http://ankidroid.org/help.html) what bothers you, and we will try to fix it.

Our current release series is 2.15.x, but you may install a "parallel" version of the previous stable release 2.14.6 if you are experiencing a problem and we are working on a fix for you. We suggest parallel "A") side by side which you will find here: https://github.com/ankidroid/Anki-Android/releases/tag/v2.14.6

### Is there a version for Android Wear?
Yes, there is an open source 3rd party app called [AnkiWear for AnkiDroid](https://github.com/wlky/AnkiDroid-Wear) that offers support for Android Wear.

### How do I upgrade from version 1.x?
If you wish to upgrade from an old (2012 and older) 1.x version of AnkiDroid, please read these [1.x upgrading instructions](http://android.stackexchange.com/questions/116414/how-to-upgrade-my-ankidroid-flashcards-from-1-x-to-2-0).

# Using AnkiDroid
### Is there a manual?
Yes, starting from v2.3, tap the "Help" item in the side drawer in AnkiDroid to open the manual on your device, or you can open it directly [here](http://ankidroid.org/manual.html)

### How can I swap the front and back of my flashcards?
See the [AnkiDroid user manual](http://ankidroid.org/manual.html#reverseCards)

### How to study only reverse cards?
To study only reverse cards, you can create a filtered deck. Tap the menu button in the upper right corner > Create filtered deck > choose a name > Seach > type `deck:your_deck_name card:2`.

#### Как повторять только обратные карточки?
Чтобы повторять только обратные карточки, можно создать фильтрованную колоду. Коснитесь кнопки меню (три точки) в верхнем правом углу > Создать фильтрованную колоду > придумайте название > Запрос > введите `deck:your_deck_name card:2`.

### How can I study my cards exactly when I want to?!
By design, AnkiDroid uses a [spaced repetition algorithm](https://en.wikipedia.org/wiki/Spaced_repetition) to minimize the amount of wasted study time.

You can create [custom study sessions](https://ankidroid.org/docs/manual.html#filtered) in AnkiDroid, where you can do extra study in addition to the cards recommended by the scheduling algorithm (for example if you have an upcoming test that you want to cram for), however if you find yourself using these features too often, you are probably not fully utilizing the power of spaced repetition.

You can also select "Card Browser", then "Preview" to quickly view all of the cards in your collection.

From the Anki website:

> The main purpose of a simple flashcard program is to allow you to enter some content and then flip through the cards, like you could with paper flashcards.

> People looking for a simple flashcard program to help them study a few cards sometimes stumble across Anki. But Anki doesn't really fit the description of a simple flashcard program. Anki is built to implement spaced repetition, a system of scheduling the next reviews of information right before the information is likely to be forgotten, making learning much more efficient. It happens that flashcards are the best way to study using spaced repetition.

> People who are only looking for the “flashcards” part and not the “spaced repetition” part are likely to be frustrated by Anki. Spaced repetition is fairly simple on its face, but studying with Anki is different from what most people are used to, and the additional complexity will probably be unwelcome at first.

[Read more here](https://anki.tenderapp.com/kb/problems/ankis-not-showing-me-all-my-cards)

### Is there an auto-sync feature?
Yes, it's disabled by default though so please see the [section in the manual](https://ankidroid.org/docs/manual.html#_ankidroid) about the automatic synchronization preference.

### Why do my cards display incorrectly?
The most common reason for this is an incorrect card [template](https://apps.ankiweb.net/docs/manual.html#cards-and-templates). This might happen if a user inadvertently edits an existing template, the wrong template is used at the time of card creation, or in rare cases a user's database might get corrupted.

A basic understanding of how Anki works is required to fix this problem, as editing card templates is not particularly beginner friendly. The actual editing is most easily done using Anki Desktop, so please carefully check through [the basics](https://apps.ankiweb.net/docs/manual.html#the-basics) section and the [intro videos](https://apps.ankiweb.net/docs/manual.html#intro-videos) in the Anki Desktop manual. If you don't have a computer handy where you can install Anki Desktop, it is also possible to [edit your templates from AnkiDroid](https://docs.ankidroid.org/manual.html#customizingCardLayout), but it might be difficult on a small handheld device.

Note: If you are simply having issues showing foreign characters or images, see the [custom fonts](https://github.com/ankidroid/Anki-Android/wiki/FAQ#instead-of-the-correct-character-to-display-a-square-is-shown-why-is-that) and [media](https://github.com/ankidroid/Anki-Android/wiki/FAQ#why-doesnt-my-sound-or-image-work-on-ankidroid) section instead.

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
External SD cards are not officially supported by AnkiDroid, and we highly recommend using the storage built into your device. If you really must use an external SD card, [this workaround](https://groups.google.com/forum/#!topic/anki-android/Eu7AXSQa6Es) may work for you. Also see [this issue on github](https://github.com/ankidroid/Anki-Android/issues/3106).

### How do I make changes to my collection with Anki Desktop?
See the [AnkiDroid user manual](http://ankidroid.org/manual.html#AnkiDesktop)

### How can I remove unwanted tags from AnkiDroid?
You can remove tags that are not currently in use by any card by running "check database" from the overflow menu in the AnkiDroid deck list. If you want to remove tags that are used by some cards, you'll need to remove the tags from those cards before running check database. See [this forum thread](https://groups.google.com/d/topic/anki-android/Kuu2UFnKZNQ/discussion) for more information on doing that.

### I cannot change preferences / they get reset after some time

This is an Android problem and AnkiDroid needs to be reset. Go to system preferences --> applications --> manager applications --> AnkiDroid and choose "delete data".

### How to use different Anki profiles
On Anki desktop, if several people want to use the same computer (or if one person wants to separate their collection into several groups), they can use different profiles. AnkiDroid doesn't currently officially support using Anki profiles (though it's a [commonly requested feature](https://github.com/ankidroid/Anki-Android/issues/2545)), however you can achieve a similar effect by installing several parallel versions of AnkiDroid besides the normal AnkiDroid and setting a unique collection path in each one. 

Official parallel versions of AnkiDroid are made available from time to time, with most stable release versions having a set of parallel releases attached. You may find them below:

* Latest Stable parallel release: https://github.com/ankidroid/Anki-Android/releases/latest
* Unstable parallel releases: https://github.com/ankidroid/Anki-Android/releases

Note that you need to update these manually and we will not provide any support for these pre-built parallel versions, but they should work well and are in daily use by many.

### Lost my cards
Does it appear like your cards have disappeared? Don't burn your phone yet: AnkiDroid saves regular backups to your device, so you should be able to recover everything in the unlikely event that this occurs. If you know the reason why your cards are gone, please see the [manual](http://ankidroid.org/docs/manual.html#backups) for info on how to restore from a backup. Otherwise you should check below to understand some common reasons why users may mistakenly think that their cards have been 'deleted' by the app:

1) Misunderstanding of how spaced repetition works

Some users that are unfamiliar with how Anki's spaced repetition algorithm works may mistake the fact that the cards stop appearing in the study screen to mean that their cards have been 'deleted' by the app. Before assuming that your cards have disappeared, please open the [card browser](https://ankidroid.org/docs/manual.html#browser), make sure that 'all decks' is selected and confirm there that your cards are really not there. If the cards *are* in fact there, you may like to read about the spaced repetition scheduling algorithm in the [Anki manual](http://ankisrs.net/docs/manual.html#introduction) and [this FAQ](https://github.com/ankidroid/Anki-Android/wiki/FAQ#how-can-i-study-my-cards-exactly-when-i-want-to).

2) User error when using the cloud sync service

Sometimes when using the [AnkiWeb cloud sync service](https://ankidroid.org/docs/manual.html#AnkiDesktop), situations can arise where Anki is unable to automatically resolve conflicts between your local and online collections. In this case AnkiDroid may ask you to manually resolve the conflict by downloading from or uploading to AnkiWeb. If you accidentally pushed the wrong button and lost some cards then you should [restore the previous version from a backup](https://ankidroid.org/docs/manual.html#backups) and refer to the section in the manual about [resolving conflicts](https://ankidroid.org/docs/manual.html#AnkiWebConflicts).

3) Data corruption

In some very rare cases the AnkiDroid database can get corrupted, usually due to some error with the file system, or users trying to use a removable SD card (which is **not** officially supported). In such cases AnkiDroid will guide you through the steps that you can take to restore your data, but if you make a mistake here by choosing the wrong option you can always restore from one of the [automatic backups](https://ankidroid.org/docs/manual.html#backups).

4) Change in device storage configuration

By default, AnkiDroid stores its data in your device's default *user storage area* (the same place where your photos and downloads go). Note that this location is different from where your applications are installed, so uninstalling and reinstalling AnkiDroid will generally **not** have any effect on your flashcard data. In the event that the AnkiDroid directory is changed to a new place without any existing flashcard data, a new empty collection will be created there, which may appear to you as if all of your flashcards have been lost.

While unlikely, it's possible that somehow the storage configuration on your device was changed such that the default storage location mentioned above changed, and that your flashcard data is still remaining in the old location. You may also have inadvertently manually changed the AnkiDroid directory in the past (e.g. to a removable SD card?), which could be causing you problems now.

In any case, if you have multiple partitions in your device, you might like to take a look around with a file explorer to see if you can find two folders in different locations named "AnkiDroid". If you do find a second location, it may have your flashcard data in it, and you can restore one of the backups from there. Note that we strongly recommend **not to** change it, but you can find the current location that AnkiDroid expects to find your data in `AnkiDroid settings > Advanced > AnkiDroid directory`. 

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

### How do I remove my DeckPicker background?

Delete `deckPickerBackground.png` in the AnkiDroid folder

### Error: copyFileToCache() failed (possibly out of storage space)

Older versions of AnkiDroid do not correctly handle long Unicode file names. Try renaming the file to English and trying again. [#6137](https://github.com/ankidroid/Anki-Android/issues/6137)

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
Unfortunately there is a limitation on the number of files which can be stored in one folder on the FAT filesystem used by default on older Android devices and many removable storage cards. You may notice this if you have many thousand media files in your collection. 

The best solution, if your device supports it and you have an external storage card, is to format your SD card  as ExFAT. You may need to take the SD card out and format it on your PC.

If your device does not support ExFAT, one possible workaround for this problem (requires root access), is to use a 3rd party app capable of mounting a different filesystem (for example from [Paragon](https://play.google.com/store/apps/details?id=com.paragon.mounter)). NTFS and Ext4 formats should also work.

While not officially supported, another possible solution may be to organize your media files into subfolders. Of course it is also necessary to update your notes to include the reference to the subfolder.

### I have many media files and syncing them to my device takes too long

If you have a large number of media files where syncing through AnkiWeb takes too long, it is possible to manually sync the media collection files in AnkiDroid with the following process:

1. Temporarily disable syncing media in AnkiDroid via `Settings` -> `AnkiDroid - General settings` -> `Fetch media on sync`. This is to allow you to open AnkiDroid for later steps without getting stuck downloading the media if you uploaded it to AnkiWeb already
1. Locate your media collection on your phone in `AnkiDroid/collection.media`
1. Transfer the desired media from your other Anki client into the `AnkiDroid/collection.media` folder. You can do this over USB or over SFTP with various software like FileZilla and an Android SFTP application of your choosing
1. After you have transferred all of your files, open up AnkiDroid and in the `...` menu, press `Check media`. This will update AnkiDroid's database with the media you added into `AnkiDroid/collection.media` as manually transferring the files there does not automatically update the database
1. Once completed, you can re-enable `Fetch media on sync` if you don't want to manually manage your media moving forward. Note that keeping this enabled will require that you do a full media sync to AnkiWeb from AnkiDroid unless you do a full media sync from your other Anki client

### Why doesn't my sound or image work on AnkiDroid

#### Shared Decks

If this issue appeared from a shared deck which was loaded into AnkiDroid, then it is a problem with the card/deck that was imported. Please contact the deck author, or edit the note, select "Cards" and remove the reference to the missing files.

#### Syncing

If you're syncing a deck via AnkiWeb Sync:

Please check if the media works correctly on AnkiWeb. 
* If the media does not work on AnkiWeb, then there was a problem with uploading the media. If the upload was from Anki Desktop, please read the Anki Desktop manual, or ask on the [Anki Desktop support site](http://ankisrs.net/docs/help.html) for help resolving this problem.
* If the media works on AnkiWeb, then
   * Use a file manager (see below) to ensure that the file exists in `AnkiDroid/collection.media`
       * Ensure that `Settings - AnkiDroid - Fetch media on sync` is selected and the media sync fully completed (this will take some time on the first sync)
       * Media sync errors are typically temporary problems with your internet connection, browse to `AnkiDroid/collection.media` and then perform a sync. If more files are added to this folder, then please sync until the sync completes successfully.
       * As a last resort, copy the media files from Anki Desktop's `collection.media` folder.
   * If the file exists in `collection.media` and is still not displaying:
       * Ensure that it is encoded in one of Android's supported codecs (see below).
       * Please [contact us](https://groups.google.com/g/anki-android) if issues still occur.

#### External content

If the files are loaded from the internet over `http`, load them via `https` URLs. If HTTPS is not available, you can automatically convert these links to local files via an Anki Desktop Addon: https://ankiweb.net/shared/info/1293255374

tags: `net::err_CLEARTEXT_NOT_PERMITTED`

### Which file manager should I use?
Most devices using Android 6 and above come with a built-in file explorer (something like: `settings -> storage and memory -> Explore`). If your device does not have a built-in explorer, you can use any file manager that you like, for example see [here](https://www.tomsguide.com/us/pictures-story/518-best-android-file-managers.html) for a list of various recommended file explorers. We personally recommend using a free and open source (FOSS) file manager like [Simple Explorer](http://forum.xda-developers.com/showthread.php?t=2330864).

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

<!-- Do not modify this heading, linked from AnkiDroid -->
### Why do my images need a protocol

Due to technical reasons, loading an image using `src="//"` without specifying `https` is not supported on AnkiDroid. [#6102](https://github.com/ankidroid/Anki-Android/issues/6102)

Please edit the card and add `https`. For example, change:

`<img src="//upload.wikimedia.org/wikipedia/commons/thumb/5/50/6.1_Russian_road_sign.svg/50px-6.1_Russian_road_sign.svg.png" />`

to

`<img src="https://upload.wikimedia.org/wikipedia/commons/thumb/5/50/6.1_Russian_road_sign.svg/50px-6.1_Russian_road_sign.svg.png" />`


### Why don't I get notifications?

In the 2.8 version of AnkiDroid the reminder notifications are connected to the widget. In order to receive notifications you have to place the widget in your launcher home screen somewhere. After installing the widget, if you configure reminders in the preferences, you should start receiving notifications.

In the 2.9 version notifications should work with or without the widget. Additionally, there is an option to enable "per deck group" notifications. Unlike the general notifications which will be triggered upon a user-set minimum number of due card, the per-deck notifications are a one-time alarm type notification set at a specific time of day. To enable these notifications:

1. Open the deck list
2. Long-press on the deck you want notifications for
3. Go to Options->Reminders and you can enable/disable this feature as well as set the time for the reminder

Some users have noted [issues](https://github.com/ankidroid/Anki-Android/issues/5535) with notifications on version 2.9. As of version 2.10.2, these issues appear to be resolved. If having issues, be sure to update to the most recent version. 

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

### TTS / Text-to-speech is not speaking!

In AnkiDroid 2.9 we migrated AnkiDroid to the new Android System APIs for text-to-speech. Some users have found that with this change their text to speech is no longer speaking. This is a problem with the general system TTS on the device, not with AnkiDroid

Make sure it is working in general first: System Preferences -> Languages & Input -> Text-to-speech output -> Play (to run a test)

Some users have found that uninstalling updates to the TTS engine can work: System Preferences -> Apps & Notifications -> Google Text-to-speech Engine -> options menu -> Uninstall updates

Some users have found that re-downloading the TTS packages for their language works for them: System Preferences -> System -> Languages & Input -> Text-to-speech output -> Preferred engine settings -> install voice data -> choose the affected voice -> delete it, re-download it

### To use TTS on AnkiDesktop and AnkiDroid
For field ```Front```

```html
{{Front}}

<div id="anki_tts">{{tts en_US voices=Apple_Samantha,Microsoft_Zira speed=1.0:Front}}</div>

<div id="ankidroid_tts" style="display:none;">
    <tts id="tts_tag" service="android" voice="en_US">{{Front}}</tts>
</div>

<script>
 if (document.documentElement.classList.contains("android")) {
       document.getElementById("anki_tts").innerHTML = "";
 } else {
       document.getElementById("ankidroid_tts").innerHTML = "";
 }
</script>
```


### Sync is not working

There are few reasons this can happen.

* Make absolutely sure you do not have firewalls blocking the ports between you and AnkiWeb (or your sync server). Some users in some nations have national firewalls for instance, and they can block the connection
* Make sure you can log in to your AnkiWeb account directly at https://ankiweb.net/account/login
* Try on a network you know is reliable (your home, your office, but not a public wifi point)
* If your device is using Android 4.x or 5.x, you must be using a version of AnkiDroid that works well with [AnkiWeb's February requirement to use encryption of TLS1.2 or higher](https://github.com/ankidroid/Anki-Android/issues/5623). Currently AnkiDroid 2.9.2 or newer, or 2.10alpha23 or higher [contains support](https://github.com/ankidroid/Anki-Android/pull/5658). The stable version is generally available as of February 3rd 2020, or you may get the alpha following [the beta testing instructions here](https://docs.ankidroid.org/manual.html#betaTesting). Note that Android 4.0.x (API15) from the year 2011 does not contain support for TLS1.2 at all and can no longer sync with AnkiWeb, though you may be able to use [a custom sync server](https://github.com/tsudoko/anki-sync-server) if you cannot use a newer version of Android.

### How do I create sub-decks or folders?

**AnkiDroid 2.11**: You can long-press a deck and choose 'Create subdeck' from the context menu

**Before Anki 2.11**

AnkiDroid supports sub-decks / folders very well except creating them is a little bit of a trick.

What you do is use a special naming style for the child deck name, like this 'Parent::Child'.

As a specific example, if you have what folders to learn 'English', you would create a deck named 'English', then you could have 'English::Verbs', 'English::Nouns', and so on. And you would see 'Verbs' and 'Nouns' as child decks of the English parent deck.

_search terms: subdeck, sub deck_

# I want Add-Ons in AnkiDroid

We understand AnkiDesktop supports Add-Ons. However, AnkiDroid does not and cannot support Add-Ons in the same way. Mobile operating systems do not let applications download and run code that was not packaged with the application. Neither iOS nor Android allow this, and if you think about the disaster that is desktop security (with viruses and crypto-ransoms etc), you will understand. The only exception is HTML and Javascript, so AnkiDroid is working on support for Javascript Add-Ons. They are still in development but you may find more information on the [AnkiDroid Javscript API here](https://github.com/ankidroid/Anki-Android/wiki/AnkiDroid-Javascript-API).

Some Anki Add-ons can be made using [genanki](https://github.com/kerrickstaley/genanki) and [pyodide](https://github.com/iodide-project/pyodide) for AnkiDroid as web app.
<br>For e.g.
- [image occlusion in browser](https://github.com/infinyte7/image-occlusion-in-browser)
- [ocloze: cloze overlapper in browser](https://github.com/infinyte7/ocloze)

If interested in creating such Add-ons as web app for AnkiDroid then view this.
<br>[Create Addon in browser for Anki](https://github.com/infinyte7/Create-Addon-in-browser-for-Anki)

# Advanced formatting tips
The [formatting wiki page](https://github.com/ankidroid/Anki-Android/wiki/Advanced-formatting) gives examples of how to achieve advanced flashcard formatting such as:

* Center align cards
* Hide replay audio button
* Customize colors in night-mode
* [Remove automatic night mode color inversion](https://github.com/ankidroid/Anki-Android/wiki/Advanced-formatting#customize-night-mode-colors)
* and much more

search terms: _nightmode night-mode, invert, colour_
# Forgotten AnkiWeb Email Instructions

**keywords**: username

## If you're logged in on Anki Desktop

In Anki, your email is listed under `Tools - Preferences - Network`.

## Otherwise

Search all your email accounts for an email from `ankiweb.net`. The query typically looks like `from:ankiweb.net`, but depends on your email provider. If you find an email, then you signed up to AnkiWeb using that email address.


# Other questions

Please refer to the main [Help](http://ankidroid.org/docs/help.html) page.