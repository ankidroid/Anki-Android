# Translation
Here is a documentation on adding string resources , which will get translated by crowdin.

## Steps are:

1. Inside **/res/values** you'll see a number of folders.

2. Add the string to the first file in those folders.
   - To choose correct directory you can refer here:
        https://github.com/ankidroid/Anki-Android/wiki/Translating-AnkiDroid#logic-of-the-separation-in-different-files
   
   - The basic format is demonstrated here :
           https://github.com/ankidroid/Anki-Android/wiki/Code-style#string-key-resources-must-be-all-lowercase-using-underscore-to-separate-words
 
      *  For example, if you're adding to core, use the following file in the screenshot. 
               path:src/main/res/values

      ![demo](https://github.com/imaryandokania/catsapi/blob/master/Screenshot%202021-03-15%20at%2010.36.53%20AM.png)
 



3. You can then reference it as getString(R.string.id) in the Activity class you want.
4. It has an automated process to generate the other files.