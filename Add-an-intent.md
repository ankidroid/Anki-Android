# Intent to Add a Card to an AnkiDroid Deck

<i>Audience: This page is for Android apps developers. AnkiDroid users can ignore it.</i>

AnkiDroid's add card intent follows the approach suggested here: http://www.openintents.org/en/node/720

To implement an add-card-to-AnkiDroid-deck-option into your app, use this intent action:

```
org.openintents.action.CREATE_FLASHCARD
```

Submit your information with intent extras SOURCE\_TEXT and TARGET\_TEXT. You can set TARGET\_LANGUAGE and SOURCE\_LANGUAGE, but this is not (yet) used by AnkiDroid.

For an example, checkout https://github.com/nicolas-raoul/Indiclash/tree/master/IndiclashDictionaryApp.

## Proposed new Intent
A new intent is currently being proposed which will allow sending of multiple flashcards, and a larger number of fields than just the front and back of a flashcard. The proposed specification is below:

```
org.openintents.action.CREATE_FLASHCARDS
```

The intent should contain the following "extra" parameters:
  * `NOTES` -> Type `ArrayList<HashMap<String, Serializable>>` (required) main data structure
  * `VERSION` -> Type `int` (required) version number of intent to allow changes in the future. The current version of this specification is 1.
  * `DEFAULT_NOTE_TYPE` -> Type `String` (optional) default name of model to choose for adding the cards.
_Note: `DEFAULT_NOTE_TYPE` should either be user specifiable in the app sending the intent, or left `null`. It will be ignored by AnkiDroid if there is no note type with this name._

The NOTES structure has the following key / values:

  * "SOURCE\_TEXT" -> Type `String` (required) must make sense when on front of flashcard
  * "TARGET\_TEXT" -> Type `String` (required) must make sense when on back of flashcard
  * "OPTIONAL\_PARAMETERS" -> Type `String[]` (optional) arbitrary number of optional fields which will be filled one by one into the available fields of the chosen model in AnkiDroid

## Alternative
Although it is not recommended, you can use this intent action

```
android.intent.action.SEND
```

It will work too, but you will not see exclusively the flashcards applications.

In case you use this action, transmit your information with Intent.EXTRA\_SUBJECT and Intent.EXTRA\_TEXT