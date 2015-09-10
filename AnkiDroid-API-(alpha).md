# API Documentation

<i>Audience: This page is for Android apps developers. AnkiDroid users can ignore it.</i>

## Instant-Add API (alpha version)
Starting From AnkiDroid v2.5, notes can be added directly to AnkiDroid's database without sending any intents via a simple API ([click here to read the javadoc](https://ankidroid.org/apidoc/AddContentApi.html)). This is advantageous for developers and end-users, as it allows quickly adding cards to AnkiDroid in bulk, without any user intervention. Additionally, an app-specific model can be used, so that developers can ensure that their content will be formatted into sensible and beautiful flashcards.

### Gradle dependency
First things first, you should add the following dependency to your module's `build.gradle` file:

```Gradle
dependencies {
    compile 'PLACEHOLDER' // The API hasn't been uploaded to JCenter yet
}
```

### Simplest Example
Here is a very simple example of adding a new note to AnkiDroid. See the sample app for a more complete / detailed example.

```java
// Instantiate the API
final AddContentApi api = new AddContentApi(myActivityInstance);
// Add new deck if one doesn't already exist
Long did = api.findDeckIdByName(myActivityInstance, "My app name");
if (did == null) {
    did = api.addNewDeck(myActivityInstance, "My app name");
}
// Add a new model if one doesn't already exist
Long mid = api.findModelIdByName(myActivityInstance, "com.something.myapp", 2);
if (mid == null) {
    // This will add a basic two-field / one card model with no special formatting
    // See the sample app for a complicated example
    mid = api.addNewBasicModel(myActivityInstance, "com.something.myapp");
}
// Add new note
api.addNewNote(mid, did, flds, "my_app_name_instant_add");
```

### Example project
A very simple example Japanese-English sample dictionary is [available here](https://github.com/ankidroid/apisample), which gives an expected real-world implementation of the API. You can long-press on an item in the list to send one or more cards to AnkiDroid via the share button on the contextual toolbar.

## Sending cards to AnkiDroid via intent
While we strongly recommend using the Instant-Add API, it is also possible (and a bit less work) to send simple flashcards to AnkiDroid one at a time via Intents. The disadvantage of this, is that you can't send multiple cards at once, you leave the user on their own to format your content into flashcards, and most importantly -- the user has to go from your app to AnkiDroid, and then press some buttons to complete the add before they can resume what they were doing in your app, which detracts from the user experience.

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