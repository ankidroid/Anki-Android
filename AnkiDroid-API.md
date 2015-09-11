# API Documentation

<i>Audience: This page is for Android apps developers. AnkiDroid users can ignore it.</i>

The API is currently in alpha version; it requires AnkiDroid 2.5alpha56 or greater installed to run. Please report any bugs to the issue tracker.

## Instant-Add API (alpha version)
Starting From AnkiDroid v2.5, notes can be added directly to AnkiDroid's database without sending any intents via a simple API library released on the LGPL license ([click here to read the javadoc](https://ankidroid.org/apidoc/com/ichi2/anki/api/AddContentApi.html)). This is advantageous for developers and end-users, as it allows quickly adding cards to AnkiDroid in bulk, without any user intervention. Additionally, an app-specific custom model can be used, so that developers can ensure that their content will be formatted into sensible and beautiful flashcards.

### Gradle dependency
First things first, you should add the following dependency to your module's `build.gradle` file:

```Gradle
dependencies {
    compile 'com.ichi2.anki:api-1.0.0alpha2'
}
```

### Simplest Example
Here is a very simple example of adding a new note to AnkiDroid. See the sample app for a more complete / detailed example.

```java
// Instantiate the API
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
    // See the sample app for a complicated example
    mid = api.addNewBasicModel("com.something.myapp");
}
// Add new note
api.addNewNote(mid, did, new String[] {"日の出", "sunrise"}, "optional_tag");
```

### Permissions
The API requires the permission `com.ichi2.anki.permission.READ_WRITE_DATABASE` which is defined by the main AnkiDroid app. If you have included the API library in your app, then this permission will automatically be requested when your app installs. Therefore, you don't need to explicitly include it in your Manifest, but you should always check that you have the required permissions at runtime before using the API. See the sample app for more details on how to do this.

### Sample app
A very simple example application is [available here](https://github.com/ankidroid/apisample), which gives an expected real-world implementation of the API in the form of a prototype Japanese-English dictionary. Long-press on an item in the list to send one or more cards to AnkiDroid via the share button on the contextual toolbar.

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

### CREATE_FLASHCARD intent 

*Note: CREATE_FLASHCARD is deprecated from AnkiDroid 2.5*

Another intent which is supported by AnkiDroid for backwards compatibility is the `org.openintents.action.CREATE_FLASHCARD` intent. You can submit your information with intent extras `SOURCE_TEXT` and `TARGET_TEXT` for the front and back respectively:

```java
Intent intent = new Intent();
intent.setAction("org.openintents.indiclash.CREATE_FLASHCARD");
intent.putExtra("SOURCE_TEXT", "日の出");
intent.putExtra("TARGET_TEXT", "Sunrise");
startActivity(intent);
```