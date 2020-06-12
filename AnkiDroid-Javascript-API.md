## JavaScript API for calling native AnkiDroid functions inside WebView

For using these functions, api version and developer contact must be provided.
The current api version is ```1.0.0```.
To initialize the JavaScript API usage.

```javascript
<script>
var jsApi = { "version" : "1.0.0", "developer" : "username@gmail.com" }

var apiData = AnkiDroidJS.init(JSON.stringify(jsApi));

var apiStatus = JSON.parse(apiData);
console.log(apiStatus);
</script>
```

All the available functions with ```enabled/disabled``` status.

### Show Answer
#### Name
```showAnswer()```
#### Info
When a card is shown, only the question is shown at first. So use this to perform show answer click through JS.
#### Usage 
```javascript
<button onclick="showAnswer();">Show Answer From JavaScript</button>
```
#### Again/Hard/Good/Easy
The following buttons can be called when available, there is case where only ```Again``` and ```Hard``` present so that can also be handled using JavaScript code.
The following function will be called when buttons available on screen.

#### Again
#### Name
```buttonAnswerEase1()```
#### Info
Perform ```Again``` button click
#### Usage
```javascript
<button onclick="buttonAnswerEase1();">Again From JS</button>
```

#### Hard
#### Name
```buttonAnswerEase2()```
#### Info
Perform ```Hard``` button click
#### Usage
```javascript
<button onclick="buttonAnswerEase2();">Hard From JS</button>
```

#### Good
#### Name
```buttonAnswerEase3()```
#### Info
Perform ```Good``` button click
#### Usage
```javascript
<button onclick="buttonAnswerEase3();">Good From JS</button>
```

#### Easy
#### Name
```buttonAnswerEase4()```
#### Info
Perform ```Easy``` button click
#### Usage
```javascript
<button onclick="buttonAnswerEase4();">Easy From JS</button>
```

#### Mark / Unmark current card
#### Name
```ankiMarkCard()```
#### Info
Adds a tag called "Marked" the current note, so it can be easily found in a search.
#### Usage
```javascript
<button onclick="ankiMarkCard();">Mark</button>
```

#### Flag / Remove flag in current card
#### Name 
```ankiToggleCard()```
#### Info
Adds a colored marker to the card, or toggles it off. Flags will appear during study, and it can be easily found in a search.
Pass the arguments ```none```, ```"red"```, ```"orange"```, ```"green"```, ```"blue"``` for flagging respective flag.
Numerical value can also be passed. 
Number from ```0...4``` can be used to flag.
0 - none,
1 - red,
2 - orange,
3 - green,
4 - blue
For flagging <b>red</b> in current card.
#### Usage
```javascript
<button onclick="ankiToggleCard("red");">Red Flag</button>
```
For flagging <b>green</b> in current card
```javascript
<button onclick="ankiToggleCard(3);">Green Flag</button>
```

### Available information about current cards in WebView ```Front / Back side of Card```

#### New card count
#### Name 
```AnkiDroidJS.ankiGetNewCardCount()```
#### Type of return value

#### Info
Return number of new card count
#### Usage
```javascript
console.log(AnkiDroidJS.ankiGetNewCardCount());
```
#### Learn card count
<b>Name:</b> ```AnkiDroidJS.ankiGetLrnCardCount()```
#### Type of return value
<b>Info:</b> Return number of learn card count
<b>Usage:</b>
```javascript
console.log(AnkiDroidJS.ankiGetLrnCardCount());
```
#### Review card count
<b>Name:</b> ```AnkiDroidJS.ankiGetRevCardCount()```
#### Type of return value
<b>Info:</b> Return number of review card count in String type
<b>Usage:</b>
```javascript
console.log(AnkiDroidJS.ankiGetRevCardCount());
```

#### ETA
<b>Name:</b> ```AnkiDroidJS.ankiGetETA()```
#### Type of return value
<b>Info:</b> Return remaining time to complete review in int type
<b>Usage:</b> 
```javascript
console.log(AnkiDroidJS.ankiGetETA());
```

#### Mark status
<b>Name:</b> ```AnkiDroidJS.ankiGetCardMark()```
#### Type of return value
<b>Info:</b> Return current card marked or not.
<b>Usage:</b> Return boolean value of mark status, true for marked, false for unmarked
```javascript
console.log(AnkiDroidJS.ankiGetCardMark());
```

#### Flag status
<b>Name:</b> ```AnkiDroidJS.ankiGetCardFlag()```
#### Type of return value
<b>Info:</b> Return int value of flag 0-none, 1-red, 2-orange, 3-green, 4-blue
<b>Usage:</b>
```javascript
console.log(AnkiDroidJS.ankiGetCardFlag());
```

#### Review 
<b>Name:</b> ```AnkiDroidJS.ankiGetCardReps()```
#### Type of return value
<b>Info:</b> Return number of reviews made on current card
<b>Usage:</b>
```javascript
console.log(AnkiDroidJS.ankiGetCardReps());
```

#### Lapses
<b>Name:</b> ```AnkiDroidJS.ankiGetCardLapses()```
#### Type of return value
<b>Info:</b> Return number of times the card went from a "was answered correctly" 
<b>Usage:</b>
```javascript
console.log(AnkiDroidJS.ankiGetCardLapses());
```

#### Interval
<b>Name:</b> ```AnkiDroidJS.ankiGetCardInterval()```
#### Type of return value
<b>Info:</b>
<b>Usage:</b>
```javascript
console.log(AnkiDroidJS.ankiGetCardInterval());
```

#### Due
<b>Name:</b> ```AnkiDroidJS.ankiGetCardDue()```
#### Type of return value
<b>Info:</b> Due is used differently for different card types: 
```
new: note id or random int
due: integer day, relative to the collection's creation time
learning: integer timestamp
```
<b>Usage:</b>
```javascript
console.log(AnkiDroidJS.ankiGetCardDue());
```

#### Queue
<b>Name:</b> ```AnkiDroidJS.ankiGetCardQueue()```
#### Type of return value<b>Info:</b>
```
 -3=user buried(In scheduler 2),
 -2=sched buried (In scheduler 2), 
 -2=buried(In scheduler 1),
 -1=suspended,
  0=new, 1=learning, 2=review (as for type)
  3=in learning, next rev in at least a day after the previous review
  4=preview
```
<b>Usage:</b>
```javascript
console.log(AnkiDroidJS.ankiGetCardQueue());
```


```javascript
showAnswer()
buttonAnswerEase1()
buttonAnswerEase2()
buttonAnswerEase3()
buttonAnswerEase4()
ankiMarkCard()
ankiToggleFlag()
```
<b>_showAnswer()_</b>
<br><b>Usage:</b> Perform Show Answer Click using this function

<b>_buttonAnswerEase1()_</b>
<br><b>Usage:</b> Perform Again Click using this function
```javascript
<button onclick="buttonAnswerEase1();">Again</button>
```
Same for other ```buttonAnswerEase```
```javascript
<button onclick="buttonAnswerEase1();">Again</button>
<button onclick="buttonAnswerEase2();">Hard</button>
<button onclick="buttonAnswerEase3();">Good</button>
<button onclick="buttonAnswerEase4();">Easy</button>
```


<b>Usage:</b> pass ```0 to 4``` in <b>ankiToggleCard()</b>

### Following information is available in Card
```
New Card Count    --> AnkiDroidJS.ankiGetNewCardCount()
Learn Card Count  --> AnkiDroidJS.ankiGetLrnCardCount()
Review Card Count --> AnkiDroidJS.ankiGetRevCardCount()

ETA  --> AnkiDroidJS.ankiGetETA()
Mark --> AnkiDroidJS.ankiGetCardMark()
Flag --> AnkiDroidJS.ankiGetCardFlag()
``` 


