## Overview

AnkiDroid uses the Acra library to send crash reports to a private server which is running a forked version of [Acralyzer](https://github.com/ankidroid/acralyzer).

Crash reports can be viewed on acralyzer, however a password is required in order to protect the privacy of our users, since the crash reports can contain logcat and user comments where private information could potentially be revealed.

### Contributing
Non-core developers wishing to get a stacktrace or logcat for a specific issue should request this in the appropriate thread on the issue tracker, and someone from the AnkiDroid team will post the relevant information. Developers wishing to browse / search through the crash report database can request a password by emailing one of the core developers.

### Developing / Testing ACRA functionality
Making changes to AnkiDroid's use of ACRA should be tested carefully since this affects our ability to help users and troubleshoot problems with the app.

Any time the library is updated or the usage is changed, the developer making those changes should take care to test what happens when the application crashes and verify that a report is posted to the crash report server and contains all the relevant details.

## Steps to test:

1.  Open the Settings in the App, go to General Settings, and change the Error Reporting Mode to something other than "Never" (otherwise since it is a debug build, ACRA reports won't be generated)    
1.  Get access to an acralyzer application so you can see the reports. By default debug ACRA reports will go to the main acralyzer instance and you'll need to ask one of the maintainers for read access to the database. Or you may set up your own acralyzer with the instructions below, and configure your debug builds to use it.
1.  Open "advanced" settings in the app, scroll all the way to the bottom, you should see a "Trigger test crash" entry in debug builds. If you touch that, the app crashes with a unique exception trace each time so you can test that crash reporting is working on the report collection server. If it wasn't a unique exception only the first exception report would go through because of the ACRALimiter configuration
1.  If you need repeated crashes remember to reset "Reporting Mode" to something other than "Never" on each restart

You can read about the recent experience upgrading AnkiDroid's ACRA, as well as future ACRA testing directions, [in a related ACRA thread here](https://github.com/ACRA/acra/commit/05e9a5384a981f905913b524f323108838154fe7#commitcomment-29569186) if you are interested.

### Setting up an Acralyzer instance for more serious testing
If you are going to do a *lot* of ACRA work you may want personal control over the acralyzer instance.

You can create a free IBM Cloudant JSON couchdb instance here https://console.bluemix.net/ (the lite plan, plenty for testing, is free), and then replicate a clean acralyzer install to it as described here https://github.com/ACRA/acralyzer/wiki/setup#easy-way---replication-of-remote-couchapps, but using the source database from here https://github.com/ACRA/acralyzer/issues/133#issuecomment-401992809 (to make sure it works, since the main source database has performance issues)

To configure debug builds to use your acralyzer, you'll need to configure the URL in the [debug build section of the AnkiDroid gradle file](https://github.com/ankidroid/Anki-Android/blob/5ed908b024d4548f22c804f9bff6a6371a91b763/AnkiDroid/build.gradle#L78)