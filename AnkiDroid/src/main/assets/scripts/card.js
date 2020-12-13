var resizeDone = false;

/*
  Handle image resizing if the image exceeds the window dimensions.

  If we are using the Chrome engine, add the "chrome" class to the
  body and defer image resizing to pure CSS. The Chrome engine can
  handle image resizing on its own, but for older versions of WebView,
  we do it here.

  If we are resizing with JavasSript, we also account for the CSS zoom
  level applied to the image. If an image is scaled with CSS zoom, the
  dimensions given to us by the browser will not be scaled
  accordingly, giving us only the original dimensions. We have to
  fetch the zoom value and scale the dimensions with it before
  checking if the image exceeds the window bounds.

  If the WebView loads too early on Android <= 2.3 (which happens on
  the first card or regularly with WebView switching enabled), then
  the window dimensions returned to us are 0x0. In this case, we skip
  image resizing and try again after we know the window has fully
  loaded with a method call initiated from Java (onPageFinished).
*/
var resizeImages = function() {
    if (navigator.userAgent.indexOf("Chrome") > -1) {
        document.body.className = document.body.className + " chrome";
    } else {
        if (window.innerWidth === 0 || window.innerHeight === 0) {
            return;
        }
        var maxWidth = window.innerWidth * 0.90;
        var maxHeight = window.innerHeight * 0.90;
        var ratio = 0;
        var images = document.getElementsByTagName('img');
        for (var i = 0; i < images.length; i++) {
            var img = images[i];
            var scale = 1;
            var zoom = window.getComputedStyle(img).getPropertyValue("zoom");
            if (!isNaN(zoom)) {
                scale = zoom;
            }
            var width = img.width * scale;
            var height = img.height * scale;
            if (width > maxWidth) {
                img.width = maxWidth;
                img.height = height * (maxWidth / width);
                width = img.width;
                height = img.height;
                img.style.zoom = 1;
            }
            if (height > maxHeight) {
                img.width = width * (maxHeight / height);
                img.height = maxHeight;
                img.style.zoom = 1;
            }
        }
    }
    resizeDone = true;
};

/* Tell the app that we no longer want to focus the WebView and should instead return keyboard
 * focus to a native answer input method.
 * Naming subject to change.
 */
function _relinquishFocus() {
    // Clicking on a hint set the Android mouse cursor to a text entry bar, even after navigating
    // away. This fixes the issue.
    document.body.style.cursor = "default";
    window.location.href = "signal:relinquishFocus";
}

/* Tell the app that the input box got focus. See also
 * AbstractFlashcardViewer and CompatV15 */
function taFocus() {
    window.location.href = "signal:typefocus";
}

/*  Call displayCardAnswer() and answerCard() from anki deck template using javascript
 *  See also AbstractFlashcardViewer.
 */
function showAnswer() {
    window.location.href = "signal:show_answer";
}
function buttonAnswerEase1() {
    window.location.href = "signal:answer_ease1";
}
function buttonAnswerEase2() {
    window.location.href = "signal:answer_ease2";
}
function buttonAnswerEase3() {
    window.location.href = "signal:answer_ease3";
}
function buttonAnswerEase4() {
    window.location.href = "signal:answer_ease4";
}
// Show options menu
function ankiShowOptionsMenu() {
    window.location.href = "signal:anki_show_options_menu";
}

// Show Navigation Drawer
function ankiShowNavDrawer() {
    window.location.href = "signal:anki_show_navigation_drawer";
}

/* Reload card.html */
function reloadPage() {
    window.location.href = "signal:reload_card_html";
}

// Mark current card
function ankiMarkCard() {
    window.location.href = "signal:mark_current_card";
}

/* Toggle flag on card from AnkiDroid Webview using JavaScript
    Possible values: "none", "red", "orange", "green", "blue"
    See AnkiDroid Manual for Usage
*/
function ankiToggleFlag(flag) {
    var flagVal = Number.isInteger(flag);

    if (flagVal) {
        switch (flag) {
            case 0: window.location.href = "signal:flag_none"; break;
            case 1: window.location.href = "signal:flag_red"; break;
            case 2: window.location.href = "signal:flag_orange"; break;
            case 3: window.location.href = "signal:flag_green"; break;
            case 4: window.location.href = "signal:flag_blue"; break;
            default: console.log('No Flag Found'); break;
        }
    } else {
        window.location.href = "signal:flag_" + flag;
    }
}

// Show toast using js
function ankiShowToast(message) {
    var msg = encodeURI(message);
    window.location.href = "signal:anki_show_toast:" + msg;
}

/* Tell the app the text in the input box when it loses focus */
function taBlur(itag) {
    //#5944 - percent wasn't encoded, but Mandarin was.
    var encodedVal = encodeURI(itag.value);
    window.location.href = "typeblurtext:" + encodedVal;
}

/* Look at the text entered into the input box and send the text on a return */
function taKey(itag, e) {
    var keycode;
    if (window.event) {
        keycode = window.event.keyCode;
    } else if (e) {
        keycode = e.which;
    } else {
        return true;
    }

    if (keycode == 13) {
        //#5944 - percent wasn't encoded, but Mandarin was.
        var encodedVal = encodeURI(itag.value);
        window.location.href = "typeentertext:" + encodedVal;
        return false;
    } else {
        return true;
    }
}

window.onload = function() {
    /* If the WebView loads too early on Android <= 4.3 (which happens
       on the first card or regularly with WebView switching enabled),
       the window dimensions returned to us will be default built-in
       values. In this case, issuing a scroll event will force the
       browser to recalculate the dimensions and give us the correct
       values, so we do this every time. This lets us resize images
       correctly. */
    window.scrollTo(0,0);
    resizeImages();
    window.location.href = "#answer";
};

function _runHook(arr) {
    var promises = [];

    for (var i = 0; i < arr.length; i++) {
        promises.push(arr[i]());
    }

    return Promise.all(promises);
}

var onUpdateHook = [];
var onShownHook = [];

var onPageFinished = function() {
    if (!resizeDone) {
        resizeImages();
        /* Re-anchor to answer after image resize since the point changes */
        window.location.href = "#answer";
    }

    var card = document.querySelector('.card');

    _runHook(onUpdateHook)
        .then(() => {
            if (window.MathJax != null) {
                /* Anki-Android adds mathjax-needs-to-render" as a class to the card when
                   it detects both \( and \) or \[ and \].

                   This does not control *loading* MathJax, but rather controls whether or not MathJax
                   renders content.  We hide all the content until MathJax renders, because otherwise
                   the content loads, and has to reflow after MathJax renders, and it's unsightly.
                   However, if we hide all the content every time, folks don't like the repainting after
                   every question or answer.  This is a middleground, where there is no repainting due to
                   MathJax on non-MathJax cards, and on MathJax cards, there is a small flicker, but there's
                   no reflowing because the content only shows after MathJax has rendered. */

                if (card.classList.contains("mathjax-needs-to-render"))
                {
                    return MathJax.startup.promise
                        .then(() => MathJax.typesetPromise([card]))
                        .then(() => card.classList.remove("mathjax-needs-to-render"));
                }
            }
        })
        .then(() => card.classList.add("mathjax-rendered"))
        .then(_runHook(onShownHook))
}
