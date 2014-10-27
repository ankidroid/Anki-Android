/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.multimediacard.language;

import java.util.HashMap;

import android.content.Context;

import com.ichi2.anki.R;

/**
 * This language lister is used to call glosbe.com translation services.
 * <p>
 * Encodings of languages here correspond to the ISO 693-3 codes.
 * <p>
 * It can be extended freely here, to support more languages.
 */
public class LanguagesListerGlosbe extends LanguageListerBase {
    public LanguagesListerGlosbe(Context context) {
        addLanguage(context.getString(R.string.multimedia_editor_languages_mandarin), "cmn");
        addLanguage(context.getString(R.string.multimedia_editor_languages_spanish), "spa");
        addLanguage(context.getString(R.string.multimedia_editor_languages_english), "eng");
        addLanguage(context.getString(R.string.multimedia_editor_languages_russian), "rus");
        addLanguage(context.getString(R.string.multimedia_editor_languages_german), "deu");
        addLanguage(context.getString(R.string.multimedia_editor_languages_slovak), "slk");
        addLanguage(context.getString(R.string.multimedia_editor_languages_portuguese), "por");        
        addLanguage(context.getString(R.string.multimedia_editor_languages_french), "fra");
        addLanguage(context.getString(R.string.multimedia_editor_languages_hindi), "hin");
        addLanguage(context.getString(R.string.multimedia_editor_languages_arabic), "ar");
        addLanguage(context.getString(R.string.multimedia_editor_languages_japaneese), "jpn");

    }

    private static HashMap<String, String> glosbe_code_map = null;
    
    public static String requestToResponseLangCode(String req){
    	
    	if(glosbe_code_map == null){
    		glosbe_code_map = new HashMap<String, String>();
        	
    		glosbe_code_map.put("cmn", "cmn");
    		glosbe_code_map.put("spa", "es");
    		glosbe_code_map.put("eng", "en");
    		glosbe_code_map.put("rus", "ru");
    		glosbe_code_map.put("deu", "de");
    		glosbe_code_map.put("slk", "sk");    		
    		glosbe_code_map.put("por", "pt");    		
    		glosbe_code_map.put("fra", "fr");
    		glosbe_code_map.put("hin", "hi");
    		glosbe_code_map.put("ar", "ar");
    		glosbe_code_map.put("jpn", "ja");
    		
    	}
      	
    	return glosbe_code_map.get(req);
    }
}
