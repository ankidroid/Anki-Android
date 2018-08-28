## Overview

AnkiDroid uses the Acra library to send crash reports to a private server which is running a forked version of [Acralyzer](https://github.com/ankidroid/acralyzer).

Crash reports can be viewed on acralyzer, however a password is required in order to protect the privacy of our users, since the crash reports can contain logcat and user comments where private information could potentially be revealed.

### Contributing
Non-core developers wishing to get a stacktrace or logcat for a specific issue should request this in the appropriate thread on the issue tracker, and someone from the AnkiDroid team will post the relevant information. Developers wishing to browse / search through the crash report database can request a password by emailing one of the core developers.

### Developing / Testing ACRA functionality
Making changes to AnkiDroid's use of ACRA should be tested carefully since this affects our ability to help users and troubleshoot problems with the app.

Any time the library is updated or the usage is changed, the developer making those changes should take care to test what happens when the application crashes and verify that a report is posted to the crash report server and contains all the relevant details.

Note that if you are testing crashes you have a few things to do:

- Just until Android Studio 3.2 is released: you need to disable advanced profiling in Android Studio for API < 26 for the Crash Dialog to work or you'll get [a masking error](https://stackoverflow.com/questions/49830593/null-pointer-exception-in-inputconnection-finishcomposingtext-method)
-  you need to open the Settings in the App, go to General Settings, and change the Error Reporting Mode to something other than "Never" (otherwise since it is a debug build, ACRA reports won't be generated)    
-  you need some guaranteed crash bug (either add one, or use an existing one) so that you can trigger a crash at will. For debug builds it might make sense to add an advanced option that explicitly triggers a crash for testing purposes 
-  you need an acralyzer application set up somewhere you can see the reports, and you should configure debug builds to use it. Note that this work has been done as of 20180730 (and is linked below), it will hopefully stay up for a while at this address. If not, you can follow the instructions on setting up acralyzer below

You can read about the recent experience upgrading AnkiDroid's ACRA, as well as future ACRA testing directions, [in a related ACRA thread here](https://github.com/ACRA/acra/commit/05e9a5384a981f905913b524f323108838154fe7#commitcomment-29569186) if you are interested.

There are some other [future changes to ACRA](https://github.com/ACRA/acra/pull/680) which may help users of the library test, but they aren't scheduled to be available until ACRA 5.2.x

### Setting up an Acralyzer instance for testing
You can create a free IBM Cloudant instance here https://console.bluemix.net/ (the lite plan, plenty for testing, is free), and then replicate a clean acralyzer install to it as described here https://github.com/ACRA/acralyzer/wiki/setup#easy-way---replication-of-remote-couchapps, but using the source database from here https://github.com/ACRA/acralyzer/issues/133#issuecomment-401992809 (to make sure it works, since the main source database has performance issues)

One [free test acralyzer instance](https://918f7f55-f238-436c-b34f-c8b5f1331fe5-bluemix.cloudant.com/acralyzer/_design/acralyzer/index.html#/dashboard/) has already been created with this method, and it allows report writes and dashboard reads without auth.

To configure debug builds to use your acralyzer, you'll need to configure the URL in the [debug build section of the AnkiDroid gradle file](https://github.com/ankidroid/Anki-Android/blob/master/AnkiDroid/build.gradle#L27)