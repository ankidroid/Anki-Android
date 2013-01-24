package com.ichi2.anki;

import android.content.Context;


/**
 * @author zaur
 *
 *      This language lister is used to call glosbe.com translation services.
 *      Encodings of languages here correspond to the ISO 693-3 codes.
 *      It can be extended freely here, to support more languages.
 *      
 */
public class LanguagesListerGlosbe extends LanguageListerBase
{
    LanguagesListerGlosbe(Context context)
    {
        super();
        
        addLanguage(context.getString(R.string.multimedia_editor_languages_mandarin), "cmn");
        addLanguage(context.getString(R.string.multimedia_editor_languages_spanish), "spa");
        addLanguage(context.getString(R.string.multimedia_editor_languages_english), "eng");
        addLanguage(context.getString(R.string.multimedia_editor_languages_nepali), "nep");
        addLanguage(context.getString(R.string.multimedia_editor_languages_russian), "rus");
        addLanguage(context.getString(R.string.multimedia_editor_languages_german), "deu");
        addLanguage(context.getString(R.string.multimedia_editor_languages_slovak), "slk");
        addLanguage(context.getString(R.string.multimedia_editor_languages_portuguese), "por");
        
        
        //Add more here, should just work. 
    }

   

}
