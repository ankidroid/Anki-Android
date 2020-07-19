## JavaScript API for calling native AnkiDroid functions inside WebView

*Audience: This page is for deck developer.*

This api allow deck developer to add functionality to cards that can call native functions defined in AnkiDroid. This can be used to design whole new layout for buttons, cards info, top bar card counts, mark, flag etc. 
<br> View this files also for additional information.
<br>[AbstractFlashcardViewer.java](https://github.com/ankidroid/Anki-Android/blob/master/AnkiDroid/src/main/java/com/ichi2/anki/AbstractFlashcardViewer.java#L3404)
<br>[card.js](https://github.com/ankidroid/Anki-Android/blob/master/AnkiDroid/src/main/assets/scripts/card.js#L78)
<!-- The PR is in progress. (#6521)
To initialize the JavaScript API usage.
## Initialize 
Calling functions using JS will not work, if api are not initialized.

### Name
```javascript
AnkiDroidJS.init()
```
### Info
For using these functions, api version and developer contact must be provided.
The current api version is ```1.0.0```.

### Type of return value
```String```
<br>All the available functions with ```enabled/disabled``` status of available functions that can be called using JavaScript.

### Usage 
```javascript
<script>
var jsApi = { "version" : "1.0.0", "developer" : "username@gmail.com" }

var apiData = AnkiDroidJS.init(JSON.stringify(jsApi));

var apiStatus = JSON.parse(apiData);
console.log(apiStatus);
</script>
```
-->
## Show Answer
### Name
```showAnswer()```
### Info
When a card is shown, only the question is shown at first. So use this to perform show answer click through JS.
### Usage 
```javascript
<button onclick="showAnswer();">Show Answer From JavaScript</button>
```
## Again/Hard/Good/Easy
The following buttons can be called when available, there is case where only ```Again``` and ```Hard``` present so that can also be handled using JavaScript code.
The following function will be called when buttons available on screen.

### Again
### Name
```buttonAnswerEase1()```
### Info
Perform ```Again``` button click
### Usage
```javascript
<button onclick="buttonAnswerEase1();">Again From JS</button>
```


## Hard
### Name
```buttonAnswerEase2()```
### Info
Perform ```Hard``` button click
### Usage
```javascript
<button onclick="buttonAnswerEase2();">Hard From JS</button>
```


## Good
### Name
```buttonAnswerEase3()```
### Info
Perform ```Good``` button click
### Usage
```javascript
<button onclick="buttonAnswerEase3();">Good From JS</button>
```


## Easy
### Name
```buttonAnswerEase4()```
### Info
Perform ```Easy``` button click
### Usage
```javascript
<button onclick="buttonAnswerEase4();">Easy From JS</button>
```


## Mark / Unmark current card
### Name
```ankiMarkCard()```
### Info
Adds a tag called "Marked" the current note, so it can be easily found in a search.
### Usage
```javascript
<button onclick="ankiMarkCard();">Mark</button>
```


## Flag / Remove flag in current card
### Name 
```ankiToggleFlag()```
### Info
Adds a colored marker to the card, or toggles it off. Flags will appear during study, and it can be easily found in a search.
<br>Pass the arguments ```none```, ```"red"```, ```"orange"```, ```"green"```, ```"blue"``` for flagging respective flag.
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
### Usage
```javascript
<button onclick="ankiToggleFlag("red");">Red Flag</button>
```
For flagging <b>green</b> in current card
```javascript
<button onclick="ankiToggleFlag(3);">Green Flag</button>
```

## Show options menu using JavaScript
### Name
```ankiShowOptionsMenu()```
### Info
In full screen, a button can be added to show options menu.
### Usage
```javascript
<button onclick="ankiShowOptionsMenu()">Show Options Menu</button>
```
## Show navigation drawer using JavaScript
### Name
```ankiShowNavDrawer()```
### Info
In full screen, a button can be added to show side navigation drawer.
### Usage
```javascript
<button onclick="ankiShowNavDrawer()">Show Navigation Drawer</button>
```


## Available information about current cards in WebView 
Add functions to ```Front / Back side``` of card to get info. 

## New card count
### Name 
```AnkiDroidJS.ankiGetNewCardCount()```
### Type of return value
```String```
### Info
Return number of new card count
### Usage
```javascript
console.log(AnkiDroidJS.ankiGetNewCardCount());
```


## Learn card count
### Name 
```AnkiDroidJS.ankiGetLrnCardCount()```
### Type of return value
```String```
### Info
Return number of learn card count
### Usage
```javascript
console.log(AnkiDroidJS.ankiGetLrnCardCount());
```


## Review card count
### Name 
```AnkiDroidJS.ankiGetRevCardCount()```
### Type of return value
```String```
### Info
Return number of review card count
### Usage
```javascript
console.log(AnkiDroidJS.ankiGetRevCardCount());
```


## ETA
### Name 
```AnkiDroidJS.ankiGetETA()```
### Type of return value
```int```
### Info
Return remaining time to complete review
### Usage 
```javascript
console.log(AnkiDroidJS.ankiGetETA());
```


## Mark status
### Name
```AnkiDroidJS.ankiGetCardMark()```
### Type of return value
```true/false```
### Info 
Return current card marked or not. Return boolean value of mark status, true for marked, false for unmarked
### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardMark());
```


## Flag status
### Name
```AnkiDroidJS.ankiGetCardFlag()```
### Type of return value
```int```
### Info
Return int value of flag 
```
0-none, 
1-red, 
2-orange, 
3-green, 
4-blue
```
### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardFlag());
```


## Review 
### Name
```AnkiDroidJS.ankiGetCardReps()```
### Type of return value
```int```
### Info
Return number of reviews made on current card
### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardReps());
```


## Lapses
### Name
```AnkiDroidJS.ankiGetCardLapses()```
### Type of return value
```int```
### Info 
Return number of times the card went from a "was answered correctly" 
### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardLapses());
```


## Interval
### Name
```AnkiDroidJS.ankiGetCardInterval()```
### Type of return value
```int```
### Info
interval (used in SRS algorithm). Negative = seconds, positive = days
### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardInterval());
```


## Due
### Name 
```AnkiDroidJS.ankiGetCardDue()```
### Type of return value
```long```
### Info
Due is used differently for different card types: 
```
new: note id or random int
due: integer day, relative to the collection's creation time
learning: integer timestamp
```
### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardDue());
```


## Queue
### Name
```AnkiDroidJS.ankiGetCardQueue()```
### Type of return value
```int```
### Info
```
 -3 = user buried(In scheduler 2),
 -2 = sched buried (In scheduler 2), 
 -2 = buried(In scheduler 1),
 -1 = suspended,
  0 = new, 1=learning, 2=review (as for type)
  3 = in learning, next rev in at least a day after the previous review
  4 = preview
```
### Usage
```javascript
console.log(AnkiDroidJS.ankiGetCardQueue());
```

## Some tips to improve card / deck development 
If want to hide card's button / text in current card when reviewing on Anki Desktop / AnkiMobile then adding all code to ```if``` block can hide the things.
```javascript
 var isMobile = /Android/i.test(navigator.userAgent); 
 if (isMobile) { 
  
  // Here all AnkiDroid defined functions call.
  // It will be hidden or not called on AnkiDesktop / AnkiMobile
  
 }
```

## Linked issues & PR
[#6521](https://github.com/ankidroid/Anki-Android/pull/6521)
<br>[#6377](https://github.com/ankidroid/Anki-Android/pull/6377) 
<br>[#6307](https://github.com/ankidroid/Anki-Android/pull/6307) 
<br>[#6388](https://github.com/ankidroid/Anki-Android/pull/6388) 
<br>[#6393](https://github.com/ankidroid/Anki-Android/pull/6393) 

## Sample Decks 
The implementation of above functionality can be found in this github repo.
[Anki Custom Card Layout](https://github.com/infinyte7/Anki-Custom-Card-Layout)
