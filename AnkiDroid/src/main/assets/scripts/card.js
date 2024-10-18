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

/* Reload card.html */
function reloadPage() {
    window.location.href = "signal:reload_card_html";
}

/* Inform the app of the current 'type in the answer' value */
function taChange(itag) {
    //#5944 - percent wasn't encoded, but Mandarin was.
    var encodedVal = encodeURI(itag.value);
    window.location.href = "typechangetext:" + encodedVal;
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

function _runHook(arr) {
    var promises = [];

    for (var i = 0; i < arr.length; i++) {
        promises.push(arr[i]());
    }

    return Promise.all(promises);
}

var onUpdateHook = [];
var onShownHook = [];

var onPageFinished = function () {
    var card = document.querySelector(".card");

    var typedElement = document.getElementsByName("typed")[0];

    _runHook(onUpdateHook)
        .then(() => {
            if (window.MathJax != null) {
                /* Anki-Android adds mathjax-needs-to-render" as a class to the card when
                   it detects both \( and \) or \[ and \].

                   This does not control *loading* MathJax, but rather controls whether or not MathJax
                   renders content.  We hide all the content until MathJax renders, because otherwise
                   the content loads, and has to reflow after MathJax renders, and it's unsightly.
                   However, if we hide all the content every time, folks don't like the repainting after
                   every question or answer.  This is a middle-ground, where there is no repainting due to
                   MathJax on non-MathJax cards, and on MathJax cards, there is a small flicker, but there's
                   no reflowing because the content only shows after MathJax has rendered. */

                if (card.classList.contains("mathjax-needs-to-render")) {
                    return MathJax.startup.promise
                        .then(() => MathJax.typesetPromise([card]))
                        .then(() => card.classList.remove("mathjax-needs-to-render"));
                }
            }
        })
        .then(() => card.classList.add("mathjax-rendered"))
        .then(() => {
            // Focus if the element contains the attribute
            if (typedElement && typedElement.getAttribute("data-focus")) {
                typedElement.focus();
            }
        })
        .then(_runHook(onShownHook));
};

/* Add function 2 hook to function 1.
 * Function 2 should be `(arg: Object) => void`;  `arg` will be an Object returned from `JSON.parse`
 */
function addHook(fn1, fn2) {
    if (fn1 === "ankiSearchCard") {
        searchCardHook.push(fn2);
    }
    if (fn1 === "ankiSttResult") {
        speechToTextHook.push(fn2);
    }
}

let searchCardHook = [];
function ankiSearchCard(result) {
    if (!searchCardHook) {
        return;
    }

    result = JSON.parse(result);
    for (var i = 0; i < searchCardHook.length; i++) {
        searchCardHook[i](result);
    }
}

// hook for getting speech to text result in callback method
let speechToTextHook = [];
function ankiSttResult(result) {
    if (!speechToTextHook) {
        return;
    }
    result = JSON.parse(result);
    result.value = JSON.parse(result.value);
    for (var i = 0; i < speechToTextHook.length; i++) {
        speechToTextHook[i](result);
    }
}

function showHint() {
    var hints = document.querySelectorAll("a.hint");
    for (var i = 0; i < hints.length; i++) {
        if (hints[i].style.display != "none") {
            hints[i].click();
            break;
        }
    }
}

function showAllHints() {
    document.querySelectorAll("a.hint").forEach(el => {
        el.click();
    });
}

function userAction(number) {
    try {
        let userJs = globalThis[`userJs${number}`];
        if (userJs != null) {
            userJs();
        } else {
            window.location.href = `missing-user-action:${number}`;
        }
    } catch (e) {
        alert(e);
    }
}
