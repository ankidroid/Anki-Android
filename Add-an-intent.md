# Intent to Add a Card to an AnkiDroid Deck

<i>Audience: This page is for Android apps developers. AnkiDroid users can ignore it.</i>

## CREATE\_FLASHCARD intent
AnkiDroid's add card intent follows the approach suggested here: http://www.openintents.org/en/node/720

To implement an add-card-to-AnkiDroid-deck-option into your app, use this intent action:

```
org.openintents.action.CREATE_FLASHCARD
```

Submit your information with intent extras SOURCE\_TEXT and TARGET\_TEXT. You can set TARGET\_LANGUAGE and SOURCE\_LANGUAGE, but this is not (yet) used by AnkiDroid.

For an example, checkout https://github.com/nicolas-raoul/Indiclash/tree/master/IndiclashDictionaryApp.

## SEND intent
Although it is not recommended, you can use this intent action

```
android.intent.action.SEND
```

It will work too, but you will not see exclusively the flashcards applications.

In case you use this action, transmit your information with Intent.EXTRA\_SUBJECT and Intent.EXTRA\_TEXT

## Adding directly via ContentProvider

From AnkiDroid v2.5, notes can be added directly to AnkiDroid by external applications via the ContentProvider. See the source code for the [contract file](https://github.com/ankidroid/Anki-Android/blob/develop/AnkiDroid/src/main/java/com/ichi2/anki/provider/FlashCardsContract.java) and the [unit test](https://github.com/ankidroid/Anki-Android/blob/develop/AnkiDroid/src/androidTest/java/com.ichi2.anki.tests/ContentProviderTest.java) for implementation details. There is also a rudimentary [example app](https://github.com/federvieh/AnkiDroidProviderTest) which may give some hints on how to set up the ContentProvider.