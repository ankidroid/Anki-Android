## Overview

Prior to AnkiDroid version 2.4, crash reports were collected by an app we called "Triage", and reports were publicly viewable at http://ankidroid-triage.appspot.com. However this tool had a number of shortcomings, and from version 2.4 we now use the Acra library to send the crash reports to our private server which is running Acralyzer.

Crash reports can be viewed here, however a password is required in order to protect the privacy of our users, since the crash reports can contain logcat and user comments where private information could potentially be revealed.

### Contributing
Non-core developers wishing to get a stacktrace or logcat for a specific issue should request this in the appropriate thread on the issue tracker, and someone from the AnkiDroid team will post the relevant information. Developers wishing to browse / search through the crash report database can request a password by emailing one of the core developers.

### Developing
Making changes to AnkiDroid's use of ACRA should be tested carefully since this affects our ability to help users and troubleshoot problems with the app.

Any time the library is updated or the usage is changed, the developer making those changes should take care to test what happens when the application crashes and verify that a report is posted to the crash report server and contains all the relevant details.

Note that if you are testing crashes you have a few things to do:

-  you need to disable advanced profiling in Android Studio for API < 26 for the Crash Dialog to work or you'll get [a masking error](https://stackoverflow.com/questions/49830593/null-pointer-exception-in-inputconnection-finishcomposingtext-method)
-  you need to alter the AnkiDroidApp#onCreate method so that the debug config is loaded            

            

### Setting up an Acralyzer instance for testing
You can create a free IBM Cloudant instance here https://console.bluemix.net/ (the lite plan, plenty for testing, is free), and then replicate a clean acralyzer install to it as described here https://github.com/ACRA/acralyzer/wiki/setup#easy-way---replication-of-remote-couchapps, but using the source database from here https://github.com/ACRA/acralyzer/issues/133#issuecomment-401992809 (to make sure it works, since the main source database has performance issues)

One [free test acralyzer instance](https://918f7f55-f238-436c-b34f-c8b5f1331fe5-bluemix.cloudant.com/acralyzer/_design/acralyzer/index.html#/dashboard/) has already been created with this method, and it allows report writes and dashboard reads without auth. To test ACRA you will also need to manually/temporarily change AnkiDroidApp#onCreate
            
            