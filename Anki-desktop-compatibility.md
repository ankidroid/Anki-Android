This page contains a list of tips, tricks and workarounds that can be used to manage compatibility with some of Anki desktop's features. 

## Enabling MathJax rendering in AnkiDroid
AnkiDroid, as of 2.8, does not support yet MathJax rendering, although it supports LaTeX (see [this](https://github.com/ankidroid/Anki-Android/issues/4915) issue (#4915)). Nevertheless, there is still a way to manage MathJax rendering in AnkiDroid, by using a custom script. This script is using the same MathJax delimiters as the ones used by Anki desktop, which allows you to preserve existing cards. This script is (at the time of writing) compatible with Anki 2.1 and AnkiDroid 2.8. 

### Adding the script in AnkiDroid : 
To add this script in AnkiDroid, add a card or edit an existing one which is using the template you will use for your formulas (for example, the 'Basic' template). When on the `Add/Edit` screen, tap the `Cards : ` button, below the `Tags` one. Multiple text boxes will appear (depending on the number of fields every template has, the 'Basic' one should have only 3). One says 'Style' : this one can be ignored. Then, paste the following script **after** what is already written in the text boxes. Repeat for every text box saying "[...] template". This will allow MathJax to render both the formulas in the front fields, but also in the back fields. 

```
<script type="text/x-mathjax-config">
    MathJax.Hub.processSectionDelay = 0;
    MathJax.Hub.Config({
        messageStyle:"none",
        showProcessingMessages:false,
        tex2jax:{
            inlineMath: [ ['$','$'], ['\\(','\\)'] ],
            displayMath: [ ['$$','$$'], ['\\[','\\]'] ],
            processEscapes:true
        }
});
</script>
<script type="text/javascript">
(function() {
  if (window.MathJax != null) {
    var card = document.querySelector('.card');
    MathJax.Hub.Queue(['Typeset', MathJax.Hub, card]);
    return;
  }
  var script = document.createElement('script');
  script.type = 'text/javascript';
  script.src = 'https://cdnjs.cloudflare.com/ajax/libs/mathjax/2.7.1/MathJax.js?config=TeX-AMS_SVG-full';
  document.body.appendChild(script);
})();
</script>
```
This script first of all specifies the MathJax configuration : this section tells MathJax to render without delay (`MathJax.Hub.processSectionDelay = 0`), and also specifies the delimiters for MathJax formulas (i.e. which part of the text fields to interpret). The second part of the script loads MathJax (if it isn't already loaded) from CloudFlare's CDNs (this part requires an internet connection, although, once you started reviewing cards using the script, Android's WebView will keep the script in cache until the application is closed). 

### Adding the script in Anki desktop and syncing it to AnkiDroid
You can also change the templates directly in Anki desktop, and then sync the template modifications with AnkiDroid, which will then be able to render MathJax. Add or edit an existing card which is using the template you want to integrate the script with, then, on the edit screen, click 'Cards...', and in the 'Template' sections, paste the script above, after what is already written. Then, once back on the main screen, click Sync, and do the same on AnkiDroid. Cards using MathJax with the same template should now render on AnkiDroid.
