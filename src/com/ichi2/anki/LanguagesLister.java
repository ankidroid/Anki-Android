package com.ichi2.anki;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class LanguagesLister
{
    HashMap<String, String> mLanguageMap;

    LanguagesLister()
    {
        mLanguageMap = new HashMap<String, String>();

        mLanguageMap.put("Mandarin", "cmn");
        mLanguageMap.put("Spanish", "spa");
        mLanguageMap.put("English", "eng");
        mLanguageMap.put("Nepali", "nep");
        mLanguageMap.put("Russian", "rus");
        mLanguageMap.put("German", "deu");
        mLanguageMap.put("Slovak", "slk");

    }

    public String getCodeFor(String Language)
    {
        if (mLanguageMap.containsKey(Language))
        {
            return mLanguageMap.get(Language);
        }

        return null;
    }

    public ArrayList<String> getLanguages()
    {
        ArrayList<String> res = new ArrayList<String>();
        res.addAll(mLanguageMap.keySet());
        Collections.sort(res, new Comparator<String>()
        {
            @Override
            public int compare(String text1, String text2)
            {
                return text1.compareToIgnoreCase(text2);
            }
        });
        return res;
    }

}
