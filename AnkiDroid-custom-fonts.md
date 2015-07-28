# <font color='red'>Support for font in AnkiDroid 2.x is based on the support in Anki 2.x, as described here:</font>
http://ankisrs.net/docs/manual.html#installing-fonts

The following is keep only for **historic** purposes (but it _should_ still work).

# AnkiDroid 0.7+ custom fonts

This page describes how to use custom fonts with version 0.7 and higher of AnkiDroid.

**Note**: If you change the font on desktop for a deck, you might need to restart Anki on the device (force stop or reboot the phone) before the updated font is used.

## Uploading the fonts to the SD card

You can use any font file. [Here are some freely distributable fonts](FreelyDistributableFonts.md).

In order to use custom fonts with AnkiDroid, you will first need to copy the fonts to the SD card so that AnkiDroid can find them.

The fonts must be located in a directory named "fonts" under your anki directory on the SD card, usually named "AnkiDroid". Create the "fonts" directory if it does not exist already. The fonts must be in any format supported by Android, most commonly used one is True Type (ending in .ttf). For example, a font might be located in:
```
  /sdcard/AnkiDroid/fonts/Verdana.ttf
```

You can connect your device via USB to your computer and copy the fonts to the device.

The name of the font file must match the name of the font used in your card layouts.

For example, if the font is called "Verdana", the file should be named "Verdana.ttf".

**Note**: If the font name contains spaces, e.g., "`Droid Sans`", the file name must also contain the spaces, e.g., it should be "`Droid Sans.ttf`" not "`DroidSans.ttf`". The name of the font is the name that shows up when selecting the font in the card layout dialog, which may not match the file name on the desktop.

**Note**: If the font name contains foreign characters, e.g., "`EPSON 行書体Ｍ`", the file name must also contain the foreign characters, e.g., it should be named "`EPSON 行書体Ｍ.ttf`", regardless of how the font file is named on the desktop. The name of the font is the name that shows up when selecting the font in the card layout dialog, which may not match the file name on the desktop.

**Note**: When you have different styles of a font in different files, copy all styles to the font directory and make sure the style is part of the file name. For example, when you have regular, bold and italic versions of a font called "Pfennig", the files should be named "`Pfennig.ttf`", "`PfennigBold.ttf`" and "`PfennigItalic.ttf`".

## Setting the font for a field

You can edit the font used by a field in the card layout in Anki (on the desktop) by going to:
  * "`Settings -> Deck Properties...`"
  * Click on "`Edit`" after selecting one of the models
  * Click on "`Card Layout`"
  * Open the "`Fields`" tab
  * Edit the "`Font`" for one of the fields

## Setting the font using CSS

It is also possible to use CSS to specify the default font for a card:
  * "`Settings -> Deck Properties...`"
  * Click on "`Edit`" after selecting one of the models
  * Click on "`Card Layout`"
  * Open the "`Card Templates`" tab

You can then add HTML/CSS formatting to the card template. For example:
```
  <style>BODY { font-family: Verdana; }</style>
```
will set the font to Verdana for the unformatted text on the card.

**Note:** Each field has a font defined and will be rendered using that font by default.

## Default font
Text that is not part of a field is rendered using the default font. Usually the font used depends on the system, unless it has been specified using CSS as described above.

For example, if the card layout contains:
```
  What is the capital {{Country}}?
```
then "What is the capital" and "?" will be formatted using the default font.

If you copied custom fonts on the SD card as described above, it is possible to change the default font used to render the text parts of the card template by changing "`Other -> Default font`" under AnkiDroid's preferences.

Note that changing this font will not apply to fields, like `{{Country}}` above.

However, if you wrote your card layout to contain an unformatted field, e.g., `{{{Capital}}}` then the default font will also be applied to that field.

## Open the deck in AnkiDroid

Open the deck in AnkiDroid. You will need to either copy the deck again to the SD card or synchronize the deck again if you had to modify the card layout in the previous step.

The custom font should be correctly shown for the fields as you specified in the card layout (it might take a bit of time the first time around).