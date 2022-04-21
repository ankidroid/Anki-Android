This page contains a collection of tips for formatting your cards on AnkiDroid. The document assumes that you know how to edit card templates. Please check [this tutorial video](https://www.youtube.com/watch?v=F1j1Zx0mXME) for an introduction to card editing on Anki Desktop.

Advanced users may [attach Chrome's dev tools to Android](https://github.com/ankidroid/Anki-Android/wiki/Development-Guide#html-javascript-inspection) for a better template development experience.

### Make flashcards vertically center aligned
The recommended way to center-align your cards is to add the following code to the `.card` class in the "Styling" section of you card templates

```css
.card {
  ...
  position: absolute;
  width: 100%;
  height: 100%;
  display: -webkit-box;
  -webkit-box-align: stretch;
  -webkit-box-pack: center;
  -webkit-box-orient: vertical;
}
```

If you only want the cards to be centered on AnkiDroid and don't care about consistency with other clients, a simpler way is to go to "Settings -> Reviewing -> Center align" and enable the check box.

### Make long words "wrap"

Since Android 4.4 Kit-Kat, Android no longer automatically breaks up long words so that they don't stretch past the edge of the screen. This can be particularly troublesome if you have gestures enabled, because you'll sometimes have to swipe left twice (once to scroll to the edge of the Webview, and again to actually get the swipe to register).

You can fix this issue by adding the following to the `.card` class in the "Styling" section of you card templates:

```css
.card {
 ...
 word-wrap: break-word;
}
```

### Use formatting like bold and italic with custom fonts

Some custom fonts (especially for Asian languages like Japanese) have separate font files for applying bold, etc. You can get these multiple files working using the  official "font-face" method, by using the sample below for the "Styling" section of the card template:


```css
.card {
 ...
 font-family: NotoSansJP;
}

@font-face { font-family: "NotoSansJP"; src: url('_NotoSansJP-Regular.otf'); }
@font-face { font-family: "NotoSansJP"; src: url('_NotoSansJP-Bold.otf'); font-weight: bold; }
```


### Remove the audio play button from flashcards
If you prefer to use the "replay audio" action in the menu, and have the play button hidden on the flashcards, add the following to the bottom of the "Styling" section of your card template:

```css
.replaybutton {display: none;}
```

### Customize night-mode colors
AnkiDroid contains a very basic color inverter that e.g. changes white to black and black to white when night mode is enabled. If you prefer to disable the inverter and setup your own colors, you should include a `.night_mode` class in your styling. For example, the following will use a dark grey background instead of black when night mode is enabled. Note that contrary to the other examples on this page [there should *not* be a space between the `.card` and `.night_mode` classes](https://css-tricks.com/multiple-class-id-selectors/):

```css
.card.night_mode {
 color: white;
 background-color: #303030;
}
```
### Auto change color in night-mode
In style.css
```css
.card {
  --text-color1: black;
}

.card.night_mode {
  --text-color1: white;
}

.title {
   color: var(--text-color1);
}
```
In Front/Back side of card
```html
<div class="title">Question 1</div>
```
Now turning on night mode automatically change the text color. 

#### To access color in Front/Back side of card

```css
.card {
   --text-color1: black;
}

.card.night_mode {
   --text-color1: white;
}

.text {
   color: var(--text-color1);
}
```

Now in JavaScript (Front/Back side of card)
```html
<div class="text">Some Text</div>

<script>

var text_color = getComputedStyle(document.querySelector(".text")).color;
console.log(text_color);

</script>
```

### Invert color of images in night-mode
The default color inversion algorithm does not affect images, with the consequence that images which have a transparent background and black lines will end up appearing invisible when night-mode is enabled. If you would like the color of images to be inverted when using night mode, you can set the following CSS selectors in addition to the `.card.night_mode` styling mentioned above. Note that these filters will generally only work on Android 4.4+.

```css
.night_mode img {
 filter: invert(1); -webkit-filter:invert(1);
}
```

Alternatively, if you only want images with a certain class to be inverted (for instance LaTeX images), then you can specify that class name instead of `img`:

```css
.night_mode .latex {
 filter: invert(1); -webkit-filter:invert(1);
}
```

### Hide the input box on cards using the "type in the answer" feature
```css
.mobile input#typeans{
display: none;
}
```

### Remove the margin when displaying images
Add the [following CSS](https://groups.google.com/d/topic/anki-android/TjakbVGJLmk/discussion) to your card template

```css
.card img {max-width: 100%; max-height: none;}
.mobile .card img {max-width: 100%; max-height: none;}
.mobile .card {margin: 0.5ex 0em;}
.mobile #content {margin: 0.5ex 0em;}
```

### Drawing area on the front card (good for Kanji practice!)

With some JavaScript it's possible to draw shapes on the front card and display what was drawn on the back card for reference. This is quite convenient to practice writing Kanji!

A third party AnkiDroid user made his code open source: you can get it, along with an example deck on [anki-canvas' project page](https://github.com/pigoz/anki-canvas)

### Detect if card is running under AnkiDroid (JS)

```javascript
document.documentElement.classList.contains("android"); //AnkiDroid
```

See: https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/assets/card_template.html#L2 or https://github.com/ankidroid/Anki-Android/issues/6886

### Hide content during execution of `onUpdateHook`

With version 2.15, AnkiDroid supports the `onUpdateHook`, to schedule JavaScript before MathJax renders.
This is useful, if you want to make *text replacements* in the content of the card.

But in this case, you will also want to hide the content of the card, until all text replacements finished.
AnkiDesktop will hide it automatically, but AnkiDroid will only do so, if you also happen to use MathJax.
If you want to hide the content of the card until after the `onUpdateHook` executes, regardless of whether MathJax executes, you can use the following:

```javascript
.android .card:not(.mathjax-rendered) {
    visibility: hidden;
}
```

### Designing custom cards layout for buttons
See [AnkiDroid Javascript API](https://github.com/ankidroid/Anki-Android/wiki/AnkiDroid-Javascript-API)