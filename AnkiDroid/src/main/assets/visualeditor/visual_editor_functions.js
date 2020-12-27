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