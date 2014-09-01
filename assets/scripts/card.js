

var resizeDone = false;


//  Handle image resizing if the image exceeds the window width.
//
//  If we are using the Chrome engine, add the "chrome" class to the
//  body and defer image resizing to pure CSS. The Chrome engine can
//  handle image resizing on its own, but for older versions of WebView,
//  we do it here.
//
//  We ignore the CSS zoom level. Leaving that allone should make the
//  (pinch) zoom work out correctly: We start with the image filling the
//  screen, and when the user zooms in, the image does get bigger.
//
//  If the WebView loads too early on Android <= 2.3 (which happens on
//  the first card or regularly with WebView switching enabled), then
//  the window dimensions returned to us are 0x0. In this case, we skip
//  image resizing and try again after we know the window has fully
//  loaded with a method call initiated from Java (onPageFinished).


var resizeImages = function() {
    if (navigator.userAgent.indexOf("Chrome") > -1) {
        document.body.className = document.body.className + " chrome";
    } else {
        if (window.innerWidth === 0 || window.innerHeight === 0) {
            return;
        }
        var maxWidth = window.innerWidth * 0.90;
        var images = document.getElementsByTagName('img');
        for (var i = 0; i < images.length; i++) {
            var img = images[i];
            if (img.width > maxWidth) {
                img.width = maxWidth;
                img.height = height * (maxWidth / img.width);
            }
        }
    }
    resizeDone = true;
};

window.onload = function() {
    // If the WebView loads too early on Android <= 4.3 (which happens
    // on the first card or regularly with WebView switching enabled),
    // the window dimensions returned to us will be default built-in
    // values. In this case, issuing a scroll event will force the
    // browser to recalculate the dimensions and give us the correct
    // values, so we do this every time. This lets us resize images
    // correctly.
    window.scrollTo(0,0);
    resizeImages();
    window.location.href = "#answer";
};

var onPageFinished = function() {
    if (!resizeDone) {
        resizeImages();
        // Re-anchor to answer after image resize since the point changes
        window.location.href = "#answer";
    }
}
