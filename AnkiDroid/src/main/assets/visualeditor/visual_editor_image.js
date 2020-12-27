/* global getTargetByGuid */

var deleteImage = function(guid) {
    var target = getTargetByGuid(guid);
    //TODO
    $("#summernote").summernote("removeMedia");
};