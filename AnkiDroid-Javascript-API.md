## JavaScript API for calling native AnkiDroid functions inside WebView

*Audience: This page is for deck developer.*

This api allow deck developer to add functionality to cards that can call native functions defined in AnkiDroid. This can be used to design whole new layout for buttons, cards info, top bar card counts, mark, flag etc. 
<br> View this files also for additional information.
<br>[AbstractFlashcardViewer.java](https://github.com/ankidroid/Anki-Android/blob/master/AnkiDroid/src/main/java/com/ichi2/anki/AbstractFlashcardViewer.java#L3404)
<br>[card.js](https://github.com/ankidroid/Anki-Android/blob/master/AnkiDroid/src/main/assets/scripts/card.js#L78)

## API return values

In all cases where an API defined below will return a value, it is possible the values are effectively "undefined" because they don't make sense in the context. A specific example is that an "ETA" for study time does not make sense in the Previewer. In these cases the return value will always be *type-safe* for use in following code to protect against javascript programming errors, but you should always check the return values themselves to ensure they make sense. In those cases, the "default" return values you will receive are "unknown" for string, -1 for number, and false for boolean. Arrays and Objects will be empty ([] and {} respectively). 

## Initialize 
If api is not initialized, then Calling functions ```markCard``` ```toggleFlag``` will not work. Initialize js api using ```AnkiDroidJS.init()```

* #### Name
```javascript
AnkiDroidJS.init()
```
* #### Info
For using these functions, api version and developer contact must be provided.
The current api version is ```0.0.1```.

* #### Type of return value
```String```
<br>All the available functions with ```enabled/disabled``` status of available functions that can be called using JavaScript.

* #### Usage 

```javascript
<script>
   var jsApi = {"version" : "0.0.1", "developer" : "dev@mail.com"};

   var apiStatus = AnkiDroidJS.init(JSON.stringify(jsApi));
   console.log(apiStatus);
   
   api = JSON.parse(apiStatus);
   
   if (api['markCard']) {
      console.log("API mark card available");
   }   

   if (api['toggleFlag']) {
      console.log("API toggle flag available");
   }   
</script>
```

## Show Answer
* #### Name
```showAnswer()```
* #### Info
When a card is shown, only the question is shown at first. So use this to perform show answer click through JS.
* #### Usage 
```javascript
<button onclick="showAnswer();">Show Answer From JavaScript</button>
```
## Again/Hard/Good/Easy
The following buttons can be called when available, there is case where only ```Again``` and ```Hard``` present so that can also be handled using JavaScript code.
The following function will be called when buttons available on screen.

## Again
* #### Name
```buttonAnswerEase1()```
* #### Info
Perform ```Again``` button click
* #### Usage
```javascript
<button onclick="buttonAnswerEase1();">Again From JS</button>
```


## Hard
* #### Name
```buttonAnswerEase2()```
* #### Info
Perform ```Hard``` button click
* #### Usage
```javascript
<button onclick="buttonAnswerEase2();">Hard From JS</button>
```


## Good
* #### Name
```buttonAnswerEase3()```
* #### Info
Perform ```Good``` button click
* #### Usage
```javascript
<button onclick="buttonAnswerEase3();">Good From JS</button>
```


## Easy
* #### Name
```buttonAnswerEase4()```
* #### Info
Perform ```Easy``` button click
* #### Usage
```javascript
<button onclick="buttonAnswerEase4();">Easy From JS</button>
```


## Mark / Unmark current card
* #### Name
```ankiMarkCard()```
* #### Info
Adds a tag called "Marked" the current note, so it can be easily found in a search.
* #### Usage
```javascript
<button onclick="ankiMarkCard();">Mark</button>
```


## Flag / Remove flag in current card
* #### Name 
```ankiToggleFlag()```
* #### Info
Adds a colored marker to the card, or toggles it off. Flags will appear during study, and it can be easily found in a search.
<br>Pass the arguments ```"none"```, ```"red"```, ```"orange"```, ```"green"```, ```"blue"``` for flagging respective flag.
<br>Numerical value can also be passed. 
Number from ```0...4``` can be used to flag.
```
0 - none,
1 - red,
2 - orange,
3 - green,
4 - blue
```
For flagging <b>red</b> in current card.
* #### Usage
```javascript
<button onclick="ankiToggleFlag("red");">Red Flag</button>
```
For flagging <b>green</b> in current card
```javascript
<button onclick="ankiToggleFlag(3);">Green Flag</button>
```


## Bury Cards
* #### Name
```js
AnkiDroidJS.ankiBuryCard();
```
* ### Return
`boolean`

* #### Info
Bury current Cards showing in reviewer UI

* #### Usage
```html
<button onclick="AnkiDroidJS.ankiBuryCard();">Bury Card</button>
```

## Bury Notes
* #### Name
```js
AnkiDroidJS.ankiBuryNote();
```
* #### Info
Bury current Notes showing in reviewer UI

* ### Return
`boolean`

* #### Usage
```html
<button onclick="AnkiDroidJS.ankiBuryNote();">Bury Note</button>
```

## Suspend Cards
* #### Name
```js
AnkiDroidJS.ankiSuspendCard();
```
* ### Return
`boolean`

* #### Info
Suspend current Cards showing in reviewer UI

* #### Usage
```html
<button onclick="AnkiDroidJS.ankiSuspendCard();">Suspend Card</button>
```

## Suspend Notes
* #### Name
```js
AnkiDroidJS.ankiSuspendNote();
```

* ### Return
`boolean`

* #### Info
Suspend current Notes showing in reviewer UI

* #### Usage
```html
<button onclick="AnkiDroidJS.ankiSuspendNote();">Suspend Note</button>
```

## Tag Cards
* #### Name
```js
AnkiDroidJS.ankiAddTagToCard();
```

* #### Info
Open tags dialog to add tags to current Notes

* #### Usage
```html
<button onclick="AnkiDroidJS.ankiAddTagToCard();">Show Tag Dialog to add tag</button>
```


## Show options menu using JavaScript
* #### Name
```ankiShowOptionsMenu()```
* #### Info
In full screen, a button can be added to show options menu.
* #### Usage
```javascript
<button onclick="ankiShowOptionsMenu()">Show Options Menu</button>
```
## Show navigation drawer using JavaScript
* #### Name
```ankiShowNavDrawer()```
* #### Info
In full screen, a button can be added to show side navigation drawer.
* #### Usage
```javascript
<button onclick="ankiShowNavDrawer()">Show Navigation Drawer</button>
```

## Show Toast
* #### Name
```ankiShowToast(message)```
* #### Info
Show Android Toast message 
* #### Usage
```javascript
ankiShowToast("This message will shown in reveiwer");
```


## Get status if showing question or answer
* #### Name
```AnkiDroidJS.ankiIsDisplayingAnswer()```
* #### Type of return value
```Boolean```
* #### Info
Return `true` if reviewer showing answer else `false`
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiIsDisplayingAnswer());
```


## Get fullscreen status
* #### Name
```AnkiDroidJS.ankiIsInFullscreen()```
* #### Type of return value
```Boolean```
* #### Info
Return fullscreen status in webview
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiIsInFullscreen());
```

## Get show / hide status of topbar
* #### Name
```AnkiDroidJS.ankiIsTopbarShown()```
* #### Type of return value
```Boolean```
* #### Info
It can be used to show / hide custom topbar design. See [#6747](https://github.com/ankidroid/Anki-Android/pull/6747) for more.
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiIsTopbarShown());
```

## Night mode mode status
* #### Name
```AnkiDroidJS.ankiIsInNightMode()```
* #### Type of return value
```Boolean```
* #### Info
Get night mode status. It can used to toggle between custom ```css``` based on the status.
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiIsInNightMode());
```

## Get status about metered connection
* #### Name
```AnkiDroidJS.ankiIsActiveNetworkMetered()```
* #### Type of return value
```Boolean```
* #### Info
Status about device connected to metered connection. It can be used stop loading heavy assets.
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiIsActiveNetworkMetered());
```


## Available information about current cards in WebView 
Add functions to ```Front / Back side``` of card to get info. 

## New card count
* #### Name 
```AnkiDroidJS.ankiGetNewCardCount()```
* #### Type of return value
```String```
* #### Info
Return number of new card count
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetNewCardCount());
```

## Learn card count
* #### Name 
```AnkiDroidJS.ankiGetLrnCardCount()```
* #### Type of return value
```String```
* #### Info
Return number of learn card count
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetLrnCardCount());
```


## Review card count
* #### Name 
```AnkiDroidJS.ankiGetRevCardCount()```
* #### Type of return value
```String```
* #### Info
Return number of review card count
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetRevCardCount());
```


## ETA
* #### Name 
```AnkiDroidJS.ankiGetETA()```
* #### Type of return value
```int```
* #### Info
Return remaining time to complete review
* #### Usage 
```javascript
console.log(AnkiDroidJS.ankiGetETA());
```


## Mark status
* #### Name
```AnkiDroidJS.ankiGetCardMark()```
* #### Type of return value
```true/false```
* #### Info 
Return current card marked or not. Return boolean value of mark status, true for marked, false for unmarked
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardMark());
```


## Flag status
* #### Name
```AnkiDroidJS.ankiGetCardFlag()```
* #### Type of return value
```int```
* #### Info
Return int value of flag 
```
0-none, 
1-red, 
2-orange, 
3-green, 
4-blue
```
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardFlag());
```

## Card ID
* #### Name 
```AnkiDroidJS.ankiGetCardId()```
* #### Type of return value
```long```
* #### Info
Returns the ID of the card. Example: ```1477380543053```
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardId());
```

## Notes ID
* #### Name 
```AnkiDroidJS.ankiGetCardNid()```
* #### Type of return value
```long```
* #### Info
Returns the ID of the note which generated the card. Example: ```1590418157630```
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardNid());
```

## Deck ID
* #### Name 
```AnkiDroidJS.ankiGetCardDid()```
* #### Type of return value
```long```
* #### Info
Returns the ID of the deck which contains the card. Example: ```1595967594978```
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardDid());
```


## Get deck name
* #### Name
```AnkiDroidJS.ankiGetDeckName()```
* #### Type of return value
```String```
* #### Info
Return deck name currently showing in webview/reviewer
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetDeckName());
```

## Last modified time of card
* #### Name 
```AnkiDroidJS.ankiGetCardMod()```
* #### Type of return value
```long```
* #### Info
Returns the last modified time as a Unix timestamp in seconds. Example: ```1477384099```
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardMod());
```


## Card Type
* #### Name 
```AnkiDroidJS.ankiGetCardType()```
* #### Type of return value
```int```
* #### Info
Returns card type
```
0 = new
1 = learning 
2 = review 
3 = relearning
```
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardType());
```

## Queue
* #### Name
```AnkiDroidJS.ankiGetCardQueue()```
* #### Type of return value
```int```
* #### Info
```
 -3 = user buried(In scheduler 2),
 -2 = sched buried (In scheduler 2), 
 -2 = buried(In scheduler 1),
 -1 = suspended,
  0 = new, 1=learning, 2=review (as for type)
  3 = in learning, next rev in at least a day after the previous review
  4 = preview
```
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardQueue());
```

## Card Left
* #### Name 
```AnkiDroidJS.ankiGetCardLeft()```
* #### Type of return value
```int```
* #### Info
```
-- of the form a*1000+b, with:
-- b the number of reps left till graduation
-- a the number of reps left today
```
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardLeft());
```

## Due
* #### Name 
```AnkiDroidJS.ankiGetCardDue()```
* #### Type of return value
```long```
* #### Info
Due is used differently for different card types: 
```
new: note id or random int
due: integer day, relative to the collection's creation time
learning: integer timestamp
```
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardDue());
```

## Interval
* #### Name
```AnkiDroidJS.ankiGetCardInterval()```
* #### Type of return value
```int```
* #### Info
interval (used in SRS algorithm). Negative = seconds, positive = days
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardInterval());
```

## Card Ease Factor
* #### Name 
```AnkiDroidJS.ankiGetCardFactor()```
* #### Type of return value
```int```
* #### Info
Return ease factor of the card in permille (parts per thousand)
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardFactor());
```

## Review 
* #### Name
```AnkiDroidJS.ankiGetCardReps()```
* #### Type of return value
```int```
* #### Info
Return number of reviews made on current card
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardReps());
```

## Lapses
* #### Name
```AnkiDroidJS.ankiGetCardLapses()```
* #### Type of return value
```int```
* #### Info 
Return number of times the card went from a "was answered correctly" 
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardLapses());
```

## Original Due
* #### Name 
```AnkiDroidJS.ankiGetCardODue()```
* #### Type of return value
```long```
* #### Info
```
original due: In filtered decks, it's the original due date that the card had before moving to filtered.
                    -- If the card lapsed in scheduler1, then it's the value before the lapse. (This is used when switching to scheduler 2. At this time, cards in learning becomes due again, with their previous due date)
                    -- In any other case it's 0.
```
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardODue());
```

## Deck ID of home deck if filtered
* #### Name 
```AnkiDroidJS.ankiGetCardODid()```
* #### Type of return value
```long```
* #### Info
Returns the ID of the home deck for the card if it is filtered, or ```0``` if not filtered. Example: ```1595967594978```
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardODid());
```

## Get next time for review in WebView

* #### Name 
```js
AnkiDroidJS.ankiGetNextTime1()
AnkiDroidJS.ankiGetNextTime2()
AnkiDroidJS.ankiGetNextTime3()
AnkiDroidJS.ankiGetNextTime4()
```
* #### Type of return value
```String```
* #### Info
Return time for next review. Time at answer buttons (Again/Hard/Good/Easy/). It can be used to hide button if returned string is empty.
* #### Usage
```javascript
console.log(AnkiDroidJS.ankiGetNextTime1());
console.log(AnkiDroidJS.ankiGetNextTime2());
console.log(AnkiDroidJS.ankiGetNextTime3());
console.log(AnkiDroidJS.ankiGetNextTime4());
```

## Open card browser and search with query
* #### Name
```js
AnkiDroidJS.ankiSearchCard(query)
```
* #### Info
From reviewer UI, open Card browser and search for cards using the query

* #### Usage
```html
<button onclick="AnkiDroidJS.ankiSearchCard('deck:\"test\" {{hanzi}}')">Search this in Card browser</button>
```
View more examples in the PR [#9247](https://github.com/ankidroid/Anki-Android/pull/9247)




# Text to Speech 
Synthesizes speech from text for immediate playback. The JS API is implemented on top of [Android Text to Speech](https://developer.android.com/reference/android/speech/tts/TextToSpeech).

## Select TTS language
* #### Name
```AnkiDroidJS.ankiTtsSetLanguage(Locale)```

* #### Parameters
`loc`

Locale: The locale describing the language to be used

* #### Type of return value
`int`

Code indicating the support status for the locale

``` 
0 Denotes the language is available for the language by the locale, but not the country and variant.
1 Denotes the language is available for the language and country specified by the locale, but not the variant.
2 Denotes the language is available exactly as specified by the locale.
-1 Denotes the language data is missing.
-2 Denotes the language is not supported.
```

* #### Info
Read more [TextToSpeech#setLanguage](https://developer.android.com/reference/android/speech/tts/TextToSpeech#setLanguage(java.util.Locale))

* #### Usage
```javascript
AnkiDroidJS.ankiTtsSetLanguage('en-US');
```
Change `en-US` to desired language.

## Speak
* #### Name
`AnkiDroidJS.ankiTtsSpeak(text)`

* #### Parameters
`text`

CharSequence: The string of text to be spoken. No longer than `getMaxSpeechInputLength()` characters 


`queueMode`

int: The queuing strategy to use, `QUEUE_ADD` or `QUEUE_FLUSH`

* #### Type of return value
`int`

```
-1 ERROR
0 SUCCESS
```

* #### Info
Speaks the text using the specified queuing strategy and speech parameters.

Read More [TextToSpeech#speak(str)](https://developer.android.com/reference/android/speech/tts/TextToSpeech#speak(java.lang.CharSequence,%20int,%20android.os.Bundle,%20java.lang.String))

* #### Usage
```js
AnkiDroidJS.ankiTtsSpeak(text)
```

If you add `1` to the second argument, it will wait until the previous Speak ends before speaking
```js
AnkiDroidJS.ankiTtsSpeak(text, 1)
```


## Set TTS speed
* #### Name
`AnkiDroidJS.ankiTtsSetSpeechRate(float)`

* #### Parameters
`speechRate`	

float: Speech rate. 1.0 is the normal speech rate, lower values slow down the speech (0.5 is half the normal speech rate), greater values accelerate it (2.0 is twice the normal speech rate).

* #### Type of return value
`int`

```
-1 ERROR
0 SUCCESS
```

* #### Info
Read More [TextToSpeech#setSpeechRate(float)](https://developer.android.com/reference/android/speech/tts/TextToSpeech#setSpeechRate(float))

* #### Usage
```js
AnkiDroidJS.ankiTtsSetSpeechRate(0.8)
```

## Set TTS pitch
* #### Name 
`AnkiDroidJS.ankiTtsSetPitch(float)`

* #### Parameters
`pitch`	

float: Speech pitch. 1.0 is the normal pitch, lower values lower the tone of the synthesized voice, greater values increase it.

* #### Type of return value
`int`

```
-1 ERROR
0 SUCCESS
```

* #### Info
Read more [TextToSpeech#setPitch(float)](https://developer.android.com/reference/android/speech/tts/TextToSpeech#setPitch(float))

* #### Usage
```js
AnkiDroidJS.ankiTtsSetPitch(1.1)
```

## Checks whether the TTS engine is busy speaking
* #### Name 
`AnkiDroidJS.ankiTtsIsSpeaking()`

* #### Type of return value
`boolean`

* #### Info
`true` if the TTS engine is speaking

Read more[TextToSpeech#isSpeaking()](https://developer.android.com/reference/android/speech/tts/TextToSpeech#isSpeaking())

* #### Usage
```js
let isSpeaking = AnkiDroidJS.ankiTtsIsSpeaking();
```

## Stop speech
* #### Name 
`AnkiDroidJS.ankiTtsStop()`

* #### Type of return value
`int`

```
-1 ERROR
0 SUCCESS
```

* #### Info
Interrupts the current utterance (whether played or rendered to file) and discards other utterances in the queue

Read more [TextToSpeech#stop()](https://developer.android.com/reference/android/speech/tts/TextToSpeech#stop())

* #### Usage
```js
AnkiDroidJS.ankiTtsStop()
```





## Some tips to improve card / deck development
If want to hide card's button / text in current card when reviewing on Anki Desktop / AnkiMobile then adding all code to ```if``` block can hide the things.
<br>**Note: Using this may give some problem when using AnkiWeb in Android Chrome, so to make available some functionality only to AnkiDroid app then ```wv``` in ```navigator.userAgent``` can be used.**
```javascript
 var UA = navigator.userAgent;

 var isMobile = /Android/i.test(UA);
 var isAndroidWebview = /wv/i.test(UA);

 if (isMobile) {
  // Here all AnkiDroid defined functions call. 
  // It will be hidden or not called on AnkiDesktop / AnkiMobile
  
   if (isAndroidWebview) {
     // Available only to AnkiDroid app only.

   } else {
     // Available to Chrome only (AnkiWeb opened in Android Chrome)
 
   }  
 }
```
For more view [Window.navigator](https://developer.mozilla.org/en-US/docs/Web/API/Window/navigator) and [Navigator userAgent Property](https://www.w3schools.com/jsref/prop_nav_useragent.asp)

#### Other way to know if in AnkiDroid or other platform
```js
 if (document.documentElement.classList.contains("android")) {
  // Hidden on AnkiDesktop / AnkiMobile

 } else {
  // Available to AnkiDesktop / AnkiMobile

 }
```

## Adding progress bar to card template
**Note: After AnkiDroid 2.12**. Turn on fullscreen and also hide topbar from reviewer settings. More better implementation can be done for this.

Front/Back HTML
```html

<div class="progress" id="progress">
    <div class="progress-bar" id="bar"></div>
</div>

<!-- anki-persistence -->
<script>
    // v0.5.2 - https://github.com/SimonLammer/anki-persistence/blob/62463a7f63e79ce12f7a622a8ca0beb4c1c5d556/script.js
    if (void 0 === window.Persistence) { var _persistenceKey = "github.com/SimonLammer/anki-persistence/", _defaultKey = "_default"; if (window.Persistence_sessionStorage = function () { var e = !1; try { "object" == typeof window.sessionStorage && (e = !0, this.clear = function () { for (var e = 0; e < sessionStorage.length; e++) { var t = sessionStorage.key(e); 0 == t.indexOf(_persistenceKey) && (sessionStorage.removeItem(t), e--) } }, this.setItem = function (e, t) { void 0 == t && (t = e, e = _defaultKey), sessionStorage.setItem(_persistenceKey + e, JSON.stringify(t)) }, this.getItem = function (e) { return void 0 == e && (e = _defaultKey), JSON.parse(sessionStorage.getItem(_persistenceKey + e)) }, this.removeItem = function (e) { void 0 == e && (e = _defaultKey), sessionStorage.removeItem(_persistenceKey + e) }) } catch (e) { } this.isAvailable = function () { return e } }, window.Persistence_windowKey = function (e) { var t = window[e], i = !1; "object" == typeof t && (i = !0, this.clear = function () { t[_persistenceKey] = {} }, this.setItem = function (e, i) { void 0 == i && (i = e, e = _defaultKey), t[_persistenceKey][e] = i }, this.getItem = function (e) { return void 0 == e && (e = _defaultKey), t[_persistenceKey][e] || null }, this.removeItem = function (e) { void 0 == e && (e = _defaultKey), delete t[_persistenceKey][e] }, void 0 == t[_persistenceKey] && this.clear()), this.isAvailable = function () { return i } }, window.Persistence = new Persistence_sessionStorage, Persistence.isAvailable() || (window.Persistence = new Persistence_windowKey("py")), !Persistence.isAvailable()) { var titleStartIndex = window.location.toString().indexOf("title"), titleContentIndex = window.location.toString().indexOf("main", titleStartIndex); titleStartIndex > 0 && titleContentIndex > 0 && titleContentIndex - titleStartIndex < 10 && (window.Persistence = new Persistence_windowKey("qt")) } }
</script>
<!----------->

<script>
        var cardCount = parseInt(AnkiDroidJS.ankiGetNewCardCount()) + parseInt(AnkiDroidJS.ankiGetLrnCardCount()) + parseInt(AnkiDroidJS.ankiGetRevCardCount());

        var totalCardCount = 1;
        if (Persistence.isAvailable()) {
            totalCardCount = Persistence.getItem("total");
            if (totalCardCount == null) {
                totalCardCount = cardCount;    // count set to total card count at first card, it will not change for current session
                Persistence.setItem("total", totalCardCount);
            }
        }

        // progress bar percentage
        var per = Math.trunc(100 - cardCount * 100 / totalCardCount);
        document.getElementById("bar").style.width = per + "%";
</script>
```
Card CSS
```css
.progress {
    width: 100%;
    border-radius: 2px;
    background-color: #ddd;
}

.progress-bar {
    width: 1%;
    height: 12px;
    border-radius: 2px;
    background-color: limegreen;
}
```

## Custom topbar design
**Note: After AnkiDroid 2.12**. Turn on fullscreen and also hide topbar from reviewer settings.

<img src="https://user-images.githubusercontent.com/12841290/88459635-6404b580-cec9-11ea-9a90-b3bc01556ff0.PNG" height="500" width="241"></img>

Add this to front/Back Side
```html
<div style="display:inline;" class="card-count">
        <table style="width:28%; margin: 0 auto;">
            <tbody>
                <tr>
                <tr id="card_count_dot" class="card-count-dot">
                    <td style="color:#2196f3;">&#8226;</td>
                    <td style="color:#ea2322;">&#8226;</td>
                    <td style="color:#4caf50;">&#8226;</td>
                </tr>
                <tr class="card-count-num">
                    <td style="color:#2196f3;" id="newCard"></td>
                    <td style="color:#ea2322;" id="learnCard"></td>
                    <td style="color:#4caf50;" id="reviewCard"></td>
                </tr>
            </tbody>
        </table>
</div>
<div id="deck_title" class="title">{{Subdeck}}</div>
<div class="time-left" id="timeID"> </div>

<script>
 var UA = navigator.userAgent;

 var isMobile = /Android/i.test(UA);
 var isAndroidWebview = /wv/i.test(UA);

 if (isMobile) {
   // Here all AnkiDroid defined functions call. 
   // It will be hidden or not called on AnkiDesktop / AnkiMobile
   
   if (isAndroidWebview) {
        // Available only to AnkiDroid app only.

	document.getElementById("newCard").innerText = AnkiDroidJS.ankiGetNewCardCount();
        document.getElementById("learnCard").innerText = AnkiDroidJS.ankiGetLrnCardCount();
        document.getElementById("reviewCard").innerText = AnkiDroidJS.ankiGetRevCardCount();

        var t = AnkiDroidJS.ankiGetETA();
        document.getElementById("timeID").innerHTML = t + " mins.";
   }  
 } else {
	document.getElementById("card_count_dot").style.display = "none";
        document.getElementById("deck_title").style.display = "none";
}
</script>
```

Add this to Card CSS
```css
/*card counts, title, time*/

.card-count {
    top: 0;
    right: 4px;
    position: absolute;
    display: none;
}

.card-count-num {
    color: black;
    font-size: 18px;
}

.card-count-dot {
    font-weight: bold;
    font-size: 24px;
}

.card-count-text {
    font-weight: light;
    font-size: 14px;
}

.title {
    top: 14px;
    left: 14px;
    position: absolute;
    font-size: 20px;
    color: grey;
    font-weight: bold;
}

.time-left {
    top: 36px;
    left: 14px;
    position: absolute;
    font-size: 14px;
    text-align: left;
    font-weight: bold;
    color: teal;
}
```

## Sample Decks 
The implementation of above functionality can be found in this github repo.
[Anki Custom Card Layout](https://github.com/infinyte7/Anki-Custom-Card-Layout)

## Linked issues & PR
[#6521 apiVersioning and developerContact implementation for AnkiDroid functions call from WebView](https://github.com/ankidroid/Anki-Android/pull/6521)
<br>[#6377 apiVersioning and developerContact implementation for AndkiDroid functions call from WebView](https://github.com/ankidroid/Anki-Android/pull/6377) 
<br>[#6307 Make available current card's and deck's details in WebView](https://github.com/ankidroid/Anki-Android/pull/6307) 
<br>[#6388 Get next time for review in WebView](https://github.com/ankidroid/Anki-Android/pull/6388) 
<br>[#6393 Get Cards info (Review, Lapses, Interval, Due, Queue) in WebView](https://github.com/ankidroid/Anki-Android/pull/6393)
<br>[#6766 JS API: Add Remaining Card Properties](https://github.com/ankidroid/Anki-Android/pull/6766)
<br>[#6784 Javascript API: Add ankiIsActiveNetworkMetered](https://github.com/ankidroid/Anki-Android/pull/6784)
<br>[#6747 Get Topbar shown status in card](https://github.com/ankidroid/Anki-Android/pull/6747)
<br>[#6387 Show options menu & navigation drawer using WebView](https://github.com/ankidroid/Anki-Android/pull/6387)
<br>[#6567 Night mode status in Card](https://github.com/ankidroid/Anki-Android/pull/6567)
<br>[#6470 Get value of fullscreen status in JavaScript](https://github.com/ankidroid/Anki-Android/pull/6470)
<br>[#6886 Automatically flip card if there is no cloze hint + detect ankidroid](https://github.com/ankidroid/Anki-Android/issues/6886)
<br>[#6386 Show Toast using JavaScript function in WebView](https://github.com/ankidroid/Anki-Android/pull/6386)
<br>[#8199 JS API to know if answer is displaying or question](https://github.com/ankidroid/Anki-Android/pull/8199)
<br>[#8500 Get deck name using JS API](https://github.com/ankidroid/Anki-Android/pull/8500)
<br>[#9247 JS API to open card browser and search with query](https://github.com/ankidroid/Anki-Android/pull/9247)
<br>[#9245 New JS API for bury & suspend card and bury & suspend note and tag card](https://github.com/ankidroid/Anki-Android/pull/9245)
<br>[# New JavaScript api for TTS](https://github.com/ankidroid/Anki-Android/pull/8812)