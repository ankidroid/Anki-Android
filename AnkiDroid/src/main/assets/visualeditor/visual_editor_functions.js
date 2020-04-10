var cloze = function(clozeId) {
    //The Anki Desktop cloze functionality appears to work as follows:
    //Ignore the HTML and insert the text at the start and end of the cursor.
    //TODO: Confirm that this allows undo.
    var selected = $('#summernote').summernote('createRange');
    var startPoint = selected.getStartPoint();
    var startContext = startPoint.node.textContent;
    var startOffset = startPoint.offset;
    var clozeInsert = "{{c" + clozeId + "::";
    startPoint.node.textContent = startContext.substring(0, startOffset) + clozeInsert + startContext.substring(startOffset);

    var endPoint = selected.getEndPoint();
    var endContext = endPoint.node.textContent;
    var endOffset = endPoint.offset + clozeInsert.length;
    endPoint.node.textContent = endContext.substring(0, endOffset) + "}}" + endContext.substring(endOffset);
    //TODO: Select something better
};