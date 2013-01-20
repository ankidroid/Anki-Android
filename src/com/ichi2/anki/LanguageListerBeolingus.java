package com.ichi2.anki;

import android.content.Context;

/**
 * @author zaur
 *
 *      This one listers services in beolingus.
 *      
 *      It is used to load pronunciation.
 *
 */
public class LanguageListerBeolingus extends LanguageListerBase
{
    
    public LanguageListerBeolingus(Context context)
    {
        super();
        
        addLanguage(context.getString(R.string.multimedia_editor_languages_english), "en-de");
        addLanguage(context.getString(R.string.multimedia_editor_languages_german), "deen");
        addLanguage(context.getString(R.string.multimedia_editor_languages_spanish), "es-de");
        addLanguage(context.getString(R.string.multimedia_editor_languages_portuguese), "pt-de");
    }

}
