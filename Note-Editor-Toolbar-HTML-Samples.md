HTML is a powerful language which allows almost limitless customization of your notes. If this page doesn't list what you require, Google can likely provide the answers. Styling advice can also be requested on our [mailing list](https://groups.google.com/g/anki-android).

## Conventions

### Wrapping Text

If the instructions state to wrap text in `<mark></mark>`:

* Place `<mark>` in the "HTML Before Selection" field
* Place `</mark>` in the "HTML After Selection" field

### Colors

Colors in HTML are specified in a hexadecimal format: (similar to: `#000000`). Many color pickers are available online. If you need one, try:  https://nodtem66.github.io/nextion-hmi-color-convert/index.html

## HTML Samples

### Text Color

* Wrap the text in `<span style="color:#0000FF"></span>` where #0000FF is the color that you want.

### Text Highlighting

A simple highlight can be performed via wrapping the text in `<mark></mark>`

A more complex highlight can be performed by wrapping the text with `<mark style="background:#FF0000"></mark>`

### Lists

Use two toolbar buttons: one for each item, and a second for the list

* Wrap each item of the list in `<li></li>`
* After adding all items, wrap them in either:  
   * `<ul></ul>` for a bulleted list
   * `<ol></ol>` for a numbered list 

### superscript and subscript

Wrap text in `<sup></sup>` for superscript text, or `<sub></sub>` for subscript text