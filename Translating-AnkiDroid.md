Even if you prefer to use AnkiDroid in English, other people in your country might prefer to use it in their own language. Translating AnkiDroid to your native language means your country's AnkiDroid will grow much faster, leading to better shared decks in your language.

# Translation systems

AnkiDroid has translations defined in two separate areas. 

1) We use "crowdin.com" for **Android-specific** strings - this is where most of the translations are currently
2) The whole Anki ecosystem (AnkiWeb, AnkiMobile, Anki Desktop, and AnkiDroid) share some translations using the "Pontoon" system, with more instructions at https://translating.ankiweb.net/

# AnkiDroid specific translations on Crowdin:

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

**Context**

To see where texts are used and check your translation, you can see screenshot of the original text. 
* Click on context 
* Then click on the image
* And you'll see where the image is used in AnkiDroid

<img src="https://user-images.githubusercontent.com/12841290/111070556-3bd7ce80-850d-11eb-89c4-c94e0d9086d6.gif" height=300></img>


**Tips:**

* Many terms such as "note" or "leech" and some other messages should be consistent with Anki Desktop. You can copy the translations from Anki Desktop via it's [translation website](https://i18n.ankiweb.net/projects/anki-desktop/) (or more rarely [this one](https://i18n.ankiweb.net/projects/anki-core/)). Click on a _language code_ file (e.g. "ja_JP" for Japanese) to view a single language, and use your browser's search feature to search for the word or sentence you are looking for.

* Some translation strings may have many plural forms in your language. These are represented by labels like `one`, `few`, `many`, etc. in Crowdin.
You can find the meanings of these terms for your language [here](https://unicode-org.github.io/cldr-staging/charts/37/supplemental/language_plural_rules.html).

# AnkiDroid specific translations on Pontoon:

  It is a recently-developed system that makes it easier for translators to deal with plurals, and makes it easier for developers to provide useful comments to help with translation. Steps to follow : 
  * Firstly , you need to create an account in order to make changes on https://anki.tenderapp.com/discussions/private with  the following information:
    1. The language or languages you'd like to translate.
    2. Your email address. Please note that the email address you provide will be visible to people browsing GitHub.
    3. The username you'd like to use (for example, 'bob5')
    4. Please include the following text in your message: "I license any translations I contribute under the 3-clause BSD license."
  * You must now visit to https://i18n.ankiweb.net/ and sign in to your account . 
  * Choose a language.
  * Select one of the project(core,desktop,mobile).
  * Choose one of the resourse . 
  * Selece any of the filter acoording to the translation status.(by default = all)
  * Green bullet=translated, Grey bullet= Missing .  
  * Terms like "%s", "%1$d" are placeholders for strings or numbers which will be filled later by AnkiDroid. They must not be changed, e.g. reversed ("1%d2") or filled with  spaces ("% s").
 

For each grey bullet, translate the English text to your language. 
Here is an eg of simple replacement in pontoon .
**Simple Replacement**

<img src="https://translating.ankiweb.net/anki/simple-replacement@2x.png" height=300></img>
* When the text is a simple string, all you need to do is write the text in your native language and click Save (or press the Enter key).
* Under the English text, many strings will contain a comment to help you understand where the string is being used, or to give an example of how it appears.
* "Context" is the short name for this string, and may sometimes give you a hint as to where it is used or what it is trying to represent.


**Tips:**
* Many languages change words depending on number. For example, English uses "1 cat", but "3 cats".
You can find the meanings of these terms for your language [here](https://translating.ankiweb.net/intro.html).

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