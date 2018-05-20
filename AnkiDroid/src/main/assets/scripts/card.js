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

/* Tell the app that the input box got focus. See also
 * AbstractFlashcardViewer and CompatV15 */
function taFocus() {
    window.location.href = "signal:typefocus";
}

/* Tell the app the text in the input box when it loses focus */
function taBlur(itag) {
    window.location.href = "typeblurtext:" + itag.value;
}

/* Look at the text enterend into the input box and send the text on a return */
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
        window.location.href = "typeentertext:" + itag.value;
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

var onPageFinished = function() {
    if (!resizeDone) {
        resizeImages();
        /* Re-anchor to answer after image resize since the point changes */
        window.location.href = "#answer";
    }
}
