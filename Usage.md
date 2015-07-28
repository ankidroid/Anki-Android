# Decks - Create them by ourself or just download them
First of all you need a collection of stuff you want to learn. In AnkiDroid and Anki this collection is stored in a "deck". The easiest way to get them is by creating/downloading them on [Anki Desktop](http://ankisrs.net/), but you can also create them and download shared decks with recent AnkiDroid versions.

  1. Download the desktop Anki application [here](http://ankisrs.net).
  1. Run it and use "File > New" to create a new deck. Insert new stuff or import them.
  1. more information about importing information: http://ankisrs.net/docs/FileImport.html
  1. You can also download shared decks provided by other users: "File > Download > Shared deck". There are tons of them, you will find everything.
  1. The decks are now stored under the name "yourdeck.anki" on Windows in "My Documents", on Linux under "/home/user/.anki".
  1. The best way to synchronise your decks:
    * Create an account: "settings > options > network"
    * Open a deck, choose "settings > deck properties > synchronisation > synchronise this deck", enter a name and choose "file > synchronise".
    * Now this deck is also on the AnkiWeb server.
  1. To get deck to your Android device:
    * choose Download Deck and then Personal Deck.
    * The first time you try this, you'll be asked to login to your AnkiWeb account first.
    * Choose the deck you from your list of personal decks to download it to your device.
    * Once on your device, you can simply choose to synchronise it (see next paragraph) like you would do from Anki desktop, to keep it up to date.

# Syncing decks and media
Use Anki (the Desktop version) to create your deck with media. Upload it to ankiweb. Follow the guide in the docs enable DropBox media syncing: http://ankisrs.net/docs/SyncingMedia.html
**Hint: Use the current stable release of Anki from here http://www.ankisrs.net/ .
Linux distributions usually do not ship the most current version.**

Once you have media working in ankiweb, you can sync them to your Android device:

Go to "Preferences -> General -> Synchronization " and check "Fetch media on sync" From now on AnkiDroid automatically fetches media that has changed. To download all missing media files, go to the "All stacks" view. Then touch the stack for 2 sec and select "Download missing media".

Open the stack and select synchronize from the menu, just to be on the safe side. From there on images and other media should work and sync automagically.

## In case images do not show

  1. Download the deck
  1. Unzip the "deckname.zip" (found in /AnkiDroid/tmp/) to a new "deckname.media" folder (with file manager app of your choice)
  1. Move the image files from "/deckname.media/shared.media/" to its parent "/deckname.media/"
  1. Move the "deckname.media" folder (from "/AnkiDroid/tmp/") to it's parent "/AnkiDroid/"

# Alternative to sync: Manually copying your decks to the phone

If you don't want to use synchronisation, you may just copy your .anki deck(s) into the "AnkiDroid" directory on the SD card:

<a href='http://www.youtube.com/watch?feature=player_embedded&v=QHfQH-UeDpM' target='_blank'><img src='http://img.youtube.com/vi/QHfQH-UeDpM/0.jpg' width='425' height=344 /></a>

Don't forget to copy the media folders too, if your decks use images or sounds.

# Learning

After opening AnkiDroid the deck you learned last time will be directly opened. If you want change deck, press "Menu", "Open Deck" and click on the specific deck you want to open.

A click on "Start Reviewing" start the learning mode.

# Deleting a Deck

In order to delete a deck:
  * Go to the "All decks" screen (Deck picker).
  * Long press the deck that you want to delete.
  * Choose "Delete deck" from the context menu.