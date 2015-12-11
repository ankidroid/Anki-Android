# API Documentation

*Audience: This page is for Android app developers. AnkiDroid users can ignore it.*

## Instant-Add API
Starting From AnkiDroid v2.5, notes can be added directly to AnkiDroid's database without sending any intents via a simple API library released on the LGPL license ([click here to read the javadoc](https://ankidroid.org/apidoc/com/ichi2/anki/api/AddContentApi.html)). This is advantageous for developers and end-users, as it allows quickly adding cards to AnkiDroid in bulk, without any user intervention. Additionally, an app-specific custom model can be used, so that developers can ensure that their content will be formatted into sensible and beautiful flashcards.

### Gradle dependency
First things first, you should add the following dependency to your module's `build.gradle` file:

```Gradle
dependencies {
    compile 'com.ichi2.anki:api:1.0.0alpha2'
}
```

### Simplest Example
Here is a very simple example of adding a new note to AnkiDroid. See the sample app for a more complete / detailed example.

```java
// Instantiate the API (you should do some checks first; see the sample app)
final AddContentApi api = new AddContentApi(context);
// Add new deck if one doesn't already exist
Long did = api.findDeckIdByName("My app name");
if (did == null) {
    did = api.addNewDeck("My app name");
}
// Add a new model if one doesn't already exist
Long mid = api.findModelIdByName("com.something.myapp", 2);
if (mid == null) {
    // This will add a basic two-field / one card model with no special formatting
    // See the sample app for an example using a fully-customized model
    mid = api.addNewBasicModel("com.something.myapp");
}
// Add new note
api.addNewNote(mid, did, new String[] {"日の出", "sunrise"}, "optional_tag");
```

** Note: your app must check that the API is available before using it, and provide a fallback if not. See the testing section below. **

### Permissions
The API requires the permission `com.ichi2.anki.permission.READ_WRITE_DATABASE` which is defined by the main AnkiDroid app. This permission will automatically be merged into your manifest if you included the Gradle dependency above. In order to support Android 6 and workaround [this Android limitation](https://code.google.com/p/android/issues/detail?id=25906) on older versions, you should always check that you have the required permission at runtime before using the API. See the sample app for more details on how to do this.

### Sample app
A very simple example application is [available here](https://github.com/ankidroid/apisample), which gives an expected real-world implementation of the API in the form of a prototype Japanese-English dictionary. Long-press on an item in the list to send one or more cards to AnkiDroid via the share button on the contextual toolbar.

### Testing
Perform the following manual tests to check that your app is working correctly. If your app fails to pass any of these tests, please refer to the sample app again for a reference implementation that passes all of the below tests.

**Test 1: Basic testing**

0. Install the [latest version of AnkiDroid](https://github.com/ankidroid/Anki-Android/releases/latest)
0. Send some flashcards from your app to AnkiDroid via the API using a deck "Your App Name"
0. Open AnkiDroid, check that a deck called "Your App Name" is there, and click on it to start studying the cards.
0. Flip through a few cards and check that they are formatted correctly
0. Open the Card Browser and check that all of the cards which you sent are there
0. Open the AnkiDroid settings, go to Advanced, and *uncheck* "Enable AnkiDroid API"
0. Try to add a card from your app again. It should correctly detect that the API is unavailable (due to disabling it in the previous step), and fallback on an intent based approach (see section on intents below).
0. Check that the front and back of the flashcard are sent to AnkiDroid in the correct order when using intents
0. Uninstall AnkiDroid
0. Check that your app does not give the user the option to send cards to AnkiDroid (since it's not installed)

This is the minimum amount of testing you should do. We **strongly** recommend performing the following additional tests to check compliance with the `com.ichi2.anki.permission.READ_WRITE_DATABASE` permission. If you do not do this, your app will break with a future release of AnkiDroid.

**Test 2: Permissions test with Android Lollipop**

0. On a device or emulator running Android 5.1 or lower, make sure both your app and AnkiDroid have been **uninstalled**
0. Install [this version of AnkiDroid](https://github.com/ankidroid/apisample/releases/download/v1/AnkiDroid-2.5.2apitest.apk) which requires the `READ_WRITE_DATABASE` permission in order to use the API
0. Try to add cards to AnkiDroid from your app via the API
0. Your app should detect that the API is unavailable (due to not having the permission) and fallback on the intent based approach
0. Uninstall and reinstall your app
0. Try to add cards to AnkiDroid from your app via the API again
0. This time it should work since your app received the permission at install time

**Test 3: Permissions test with Android Marshmallow**

0. On a device or emulator running Android 6.0 or higher, make sure both your app and AnkiDroid have been **uninstalled**
0. Install [this version of AnkiDroid](https://github.com/ankidroid/apisample/releases/download/v1/AnkiDroid-2.5.2apitest.apk) which has the permission enabled (same version as Lollipop tests above)
0. Try to add cards to AnkiDroid from your app
0. Android should prompt the user whether or not they want to grant your app the `READ_WRITE_DATABASE` permission
0. Choose "deny"
0. Your app should correctly fallback on adding via intents
0. Try to add cards to AnkiDroid from your app again
0. This time choose to grant the permission
0. Check that the cards were added to AnkiDroid correctly as per previous tests

## Sending cards to AnkiDroid via intent
While we strongly recommend using the Instant-Add API, if the user has a version of AnkiDroid that doesn't support the API, or if they've denied permission to your app to access it, you should fall back on an intent-based approach, which is supported by all versions of AnkiDroid. 

The disadvantage of the intent-based approach compared with the API, is that you can't send multiple cards at once, you leave the user on their own to format your content into flashcards, and most importantly -- the user has to go from your app to AnkiDroid, and then press some buttons to complete the add before they can resume what they were doing in your app, which detracts from the user experience. This is why we only recommend using it as a fall-back when the API is not available.

### ACTION_SEND intent
The `ACTION_SEND` intent is a universal intent for sharing data with other apps in Android. AnkiDroid expects the front text to be in the subject, and the back text to be in the main content. Use [`ShareCompat`](http://developer.android.com/reference/android/support/v4/app/ShareCompat.html) to build the intent so that information about your app is automatically sent to AnkiDroid with the intent:

```java
    Intent shareIntent = ShareCompat.IntentBuilder.from(context)
            .setType("text/plain")
            .setText("Sunrise")
            .setSubject("日の出")
            .getIntent();
    if (shareIntent.resolveActivity(context.getPackageManager()) != null) {
        context.startActivity(shareIntent);
    }
```

### ~~CREATE_FLASHCARD intent~~

*Note: CREATE_FLASHCARD is deprecated from AnkiDroid 2.5. Please use ACTION_SEND instead.*

Another intent which is currently supported by AnkiDroid for backwards compatibility is the `org.openintents.action.CREATE_FLASHCARD` intent. Fields are submitted with intent extras `SOURCE_TEXT` and `TARGET_TEXT` for the front and back respectively:

```java
Intent intent = new Intent();
intent.setAction("org.openintents.indiclash.CREATE_FLASHCARD");
intent.putExtra("SOURCE_TEXT", "日の出");
intent.putExtra("TARGET_TEXT", "Sunrise");
startActivity(intent);
```

# Sync Intent
AnkiDroid v2.5 supports background syncing via an experimental intent which can be sent from [Tasker](http://tasker.dinglisch.net/). Note: a "server is busy" error will be shown if you try to sync more often than once every 5 minutes.

```
Sync Intent [ 
 Action:com.ichi2.anki.DO_SYNC
 Cat:Default 
 Mime Type: 
 Data: 
 Extra: 
 Extra: 
 Package:
 Class: 
 Target:Service ]
```

As an example, if you import [this XML file](https://raw.githubusercontent.com/ankidroid/apisample/master/AnkiDroid_Sync.prf.xml) into a Tasker profile, it will trigger AnkiDroid to sync when the power is plugged in. See the [Tasker FAQ](http://tasker.dinglisch.net/userguide/en/faqs/faq-how.html#q) for instructions on how to import profiles.