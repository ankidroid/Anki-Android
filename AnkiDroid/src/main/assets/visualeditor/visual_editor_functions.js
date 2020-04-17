var cloze = function(clozeId) {
    //The Anki Desktop cloze functionality appears to work as follows:
    //Ignore the HTML and insert the text at the start and end of the cursor.
    //TODO: This isn't correct yet
    var selected = $('#summernote').summernote('createRange');
    var currentCloze = "{{c" + clozeId + "::" + selected + "}}";
    $('#summernote')confForDid.summernote('insertText', currentCloze);
};