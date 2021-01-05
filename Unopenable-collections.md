# What should I do if AnkiDroid **crashes** every time I try to open it?

Try this first:
1. Uninstall AnkiDroid -> reboot your device -> reinstall AnkiDroid
1. Open AnkiDroid again
1. If the app still crashes then submit a crash report if prompted, and proceed with the steps below

If the above didn't work, then you will need to provide us with some information:
1. Open a [file manager](https://github.com/ankidroid/Anki-Android/wiki/FAQ#which-file-manager-should-i-use) and navigate to the `AnkiDroid` folder
1. Rename the `collection.anki2` file to `collection.anki2.bak`
1. Also make a *copy* of the `backup` folder (call it `backup_backup`) just in case something goes wrong
1. Open the app again (now it should not crash, but previously added cards will not be there)
1. Upload your `collection.anki2.bak` file to Google Drive
1. Create a [new issue on github](https://github.com/ankidroid/Anki-Android/issues/new), making sure to include a link to the file you uploaded to Google Drive, and also be sure to add your "debug info" from the app. If you're not comfortable to share your collection on github, you can request to email it directly to a developer.
1. Try to restore your collection from the oldest [automatic backup](https://docs.ankidroid.org/manual.html#backups) that you have. If this works, you can try progressively more recent ones. Make a note on your github issue about whether or not you were able to restore from backup.
1. Wait for a developer to look into your issue. If you don't get a response within a week or two, please post a reminder in the same github thread, as all developers are volunteers with jobs and busy lives.