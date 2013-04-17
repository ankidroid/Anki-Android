package com.ichi2.anki;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * @author zaur
 *
 *      This is some sort of tool, which translates from languages in a user readable form to a code,
 *      used to invoke some service. This code depends on service, of course.
 *      
 *      Specific language listers derive from this one.
 *
 */
public class LanguageListerBase
{

    protected HashMap<String, String> mLanguageMap;
    
    public LanguageListerBase()
    {
        mLanguageMap = new HashMap<String, String>();
    }
    
    /**
     * @param name
     * @param code
     * 
     * This one has to be used in constructor to fill the hash map.
     * 
     */
    protected void addLanguage(String name, String code)
    {
        mLanguageMap.put(name, code);
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
