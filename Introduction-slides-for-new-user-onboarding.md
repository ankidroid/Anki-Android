## Library which is being used

  https://github.com/AppIntro/AppIntro

## Way to add a new slide

   Visit `IntroductionActivity` and create a new object of `IntroductionResources`.

   Example using an image:
   
   ```
   val welcomeSlide = IntroductionResources(
       R.string.collection_load_welcome_request_permissions_title,
       R.string.introduction_desc,
       R.drawable.ankidroid_logo
   )
   ```

   The first two parameters represent the title and description respectively while the third one is the drawable resource which needs to be displayed.

   Example using an HTML page:
   ```
   val decksSlide = IntroductionResources(
       R.string.decks_intro,
       R.string.decks_intro_desc,
       mWebPage = "DeckList.html",
       mLocalization = listOf(
           "app-name" to R.string.app_name,
           "due-time" to R.string.due_time,
           "deck-1" to R.string.deck_example_name_1,
           "deck-2" to R.string.deck_example_name_2,
           "card-status" to R.string.deck_picker_footer_text
       )
   )
   ```

   The four parameters represent the title, description, HTML file name and list of ID and string resource mappings respectively.

   Here, IDs used in the HTML file are being mapped to string resources so that proper translations can be used.

The next step would be to add these new slides to the `slidesList`.

```
val slidesList = listOf(welcomeSlide, decksSlide)
```

This list includes all the slides which have to be displayed during the new user onboarding.