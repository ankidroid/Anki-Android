Even if you prefer to use AnkiDroid in English, other people in your country might prefer to use it in their own language. Translating AnkiDroid to your native language means your country's AnkiDroid will grow much faster, leading to better shared decks in your language.

Translating is easy and fun:
  * Go to http://crowdin.net/project/ankidroid
  * Register, if you don't have an account yet
  * Click on the flag of your language
  * Click on a file (they are sorted by descending priority)
  * Click on "Sort"
  * Click on "Missing translation"
  * Grey bullet=missing, Green bullet=done
  * Terms like "%s", "%1$d" are placeholders for strings or numbers which will be filled later by AnkiDroid. They must not be changed, e.g. reversed ("1%d2") or filled with spaces ("% s").


For each grey bullet, translate the English text to your language.

** Context **

To see where texts are used and check your translation, you can see screenshot of the original text. 
* Click on context 
* Then click on the image
* And you'll see where the image is used in AnkiDroid

<img src="https://user-images.githubusercontent.com/12841290/111070556-3bd7ce80-850d-11eb-89c4-c94e0d9086d6.gif" height=300></img>


**Tips:**

* Many terms such as "note" or "leech" and some other messages should be consistent with Anki Desktop. You can copy the translations from Anki Desktop via it's [translation website](https://i18n.ankiweb.net/projects/anki-desktop/) (or more rarely [this one](https://i18n.ankiweb.net/projects/anki-core/)). Click on a _language code_ file (e.g. "ja_JP" for Japanese) to view a single language, and use your browser's search feature to search for the word or sentence you are looking for.

* Some translation strings may have many plural forms in your language. These are represented by labels like `one`, `few`, `many`, etc. in Crowdin.
You can find the meanings of these terms for your language [here](https://unicode-org.github.io/cldr-staging/charts/37/supplemental/language_plural_rules.html).

## Logic of the separation in different files

  * android\_market.xml : Text seen by Android users who see AnkiDroid in the Market, and are pondering whether to install it or not.
  * 01-core.xml : Most important strings (reviewer, studyoptions, deckpicker)
  * 02-strings.xml: Strings which are nice to have but are not immediately visible when learning
  * 03-dialogs.xml: Texts for dialogs (warnings, information etc.)
  * 04-network.xml: Strings for syncing and downloading decks
  * 05-feedback.xml: Strings for feedback system
  * 06-statistics.xml: Strings for all statistics
  * 07-cardbrowser.xml: Strings for card browser
  * 08-widget.xml: Strings for widget
  * 09-backup.xml: Strings for backup system
  * 10-preferences.xml: Strings which are used in the preferences screens
  * 11-arrays.xml: Array-Strings which are used in the preferences screens

## Switching the language of the AnkiDroid UI
  * Go to the decks list
  * Menu > Preferences (1st item) > Language (6th item) > Language (1st item)
  * Select the language you want

## Translating the AnkiDroid manual
The source for the AnkiDroid manual can be found on the [ankidroiddocs github page](https://github.com/ankidroid/ankidroiddocs). The manual is written in a plain text markup language called [asciidoctor](http://asciidoctor.org/docs/asciidoc-syntax-quick-reference/) which is very easy to use. The asciidoctor file is called "manual.txt" and it can be compiled to html by following the [instructions in the ankidroiddocs repository](https://github.com/ankidroid/ankidroiddocs).