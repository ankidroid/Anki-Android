package com.ichi2.filters;

import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remove unnecessary information from Google Translate.
 *
 * @author evgenij.kozhevnikov@gmail.com
 * */
public class GoogleTranslaterFilter implements CardFilter{
    
    private static final String SEARCH_PATTERN = "(?:\\):\\s*)(.*)(?:(\\b|\\s))";

    public Pair<String, String> filter(Pair<String, String> messages) {
        Pair<String, String> result = new Pair<String, String>(messages.first, messages.second);
        Pattern pattern = Pattern.compile(SEARCH_PATTERN);
        Matcher matcher = pattern.matcher(messages.first + messages.second);
        if (matcher.find()) {
            String translate = matcher.group(1);
            if (matcher.find()) {
                result = new Pair<String, String>(matcher.group(1), translate);
            }
        }
        return result;
    }

}
