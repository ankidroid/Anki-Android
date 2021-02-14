/* global RTextEditorView */

/**
To understand summernote, look at the API surface of document.execCommand:
https://developer.mozilla.org/en-US/docs/Web/API/document/execCommand#Commands
This is the API surface which is being built upon
*/

function insertAtPoint(startPoint, text, offset) {
    var startNode = startPoint.node;
    var range = document.createRange();
    range.setStart(startNode, startPoint.offset + offset);
    range.setEnd(startNode, startPoint.offset + offset);
    var sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);

    document.execCommand("insertText", true, text);
}

var cloze = function(clozeId) {
    //The Anki Desktop cloze functionality works as follows:
    //Ignore the HTML and insert the text at the start and end of the cursor.

    //I'm being lazy, avoiding summernote and interracting with the browser directly.
    //This might be possible:
    //https://stackoverflow.com/questions/6139107/programmatically-select-text-in-a-contenteditable-html-element
    var clozePrefix = "{{c" + clozeId + "::";
    var clozeSuffix = "}}";

    var selected = $("#summernote").summernote("createRange");
    var startPoint = selected.getStartPoint();
    var endPoint = selected.getEndPoint();

    insertAtPoint(startPoint, clozePrefix, 0);
    insertAtPoint(endPoint, clozeSuffix, clozePrefix.length);
};

var pasteHTML = function(data) {
    $("#summernote").summernote("pasteHTML", data);
};

/**
https://gist.github.com/jed/982883
https://gist.github.com/jed/982883#file-license-txt
*/
function createGuid(a){return a?(a^Math.random()*16>>a/4).toString(16):([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g,createGuid);}


/**
I want to serialise a reference to the currently selected image to avoid race conditions.
This means we need an ID to pass in
*/
function getGuid(target) {
    var guid =  target.getAttribute("data-ankidroid-guid");
    if (guid) {
        return guid;
    }
    guid = createGuid();
    target.setAttribute("data-ankidroid-guid", guid);
    return guid;
}

function escape(str) {
    return str.replace(/[^0-9a-z]/gi, "");
}

function getTargetByGuid(guid) {
    return $("*[data-ankidroid-guid='" + escape(guid) + "']").first();
}

function sendMouseDownToClient(e) {
    if (e.target.nodeName.toUpperCase() === "IMG") {
        RTextEditorView.onImageSelection(getGuid(e.target), e.target.src);
        return;
    }
    RTextEditorView.onRegularSelection();
}

/** Taken from card.js */

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
        var images = document.getElementsByTagName("img");
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