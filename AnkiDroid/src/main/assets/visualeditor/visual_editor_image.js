/* global getTargetByGuid, VisualEditor */

var deleteImage = function(guid) {
    var target = getTargetByGuid(guid);
    //TODO
    $("#summernote").summernote("removeMedia");
};

var cut = function() {
    let jq = $("<div>");
    let sel = window.getSelection();

    for (let i = 0; i < sel.rangeCount; i++) {
        let range = sel.getRangeAt(i);
        let fragment = sel.getRangeAt(i).cloneContents();
        range.deleteContents();
        jq.append(fragment);
    }

    VisualEditor.setClipboard(jq.html());

    //we're left with a single selection with no content if we have an image selected. Remove it.
    window.getSelection().removeAllRanges();
};