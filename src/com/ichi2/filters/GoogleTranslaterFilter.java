
package com.ichi2.filters;

import android.content.SharedPreferences;

import com.ichi2.anki.Pair;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remove unnecessary information from Google Translate.
 *
 * @author evgenij.kozhevnikov@gmail.com
 */
public class GoogleTranslaterFilter extends AbstractCardFilter {

    public static final String CHECK_PATTERN = "Google";

    private static final String SEARCH_PATTERN = "(?:\\):\\s*)(.*)(?:\\s|\\b)";


    public Pair<String, String> filter(Pair<String, String> messages, SharedPreferences preferences) {
        Pair<String, String> result = new Pair<String, String>(messages.first, messages.second);
        Pattern pattern = Pattern.compile(SEARCH_PATTERN);
        Matcher matcher = pattern.matcher(getSearchText(messages));
        if (isCanBeExecuted(messages, preferences) && matcher.find()) {
            String translate = matcher.group(1);
            if (matcher.find()) {
                result = new Pair<String, String>(matcher.group(1), translate);
            }
        }
        return result;
    }


    /**
     * Check conditions to running current filter.
     *
     * @param messages original messages.
     * @param preferences program settings.
     * @return true, if filter could be run, otherwise false.
     */
    private boolean isCanBeExecuted(Pair<String, String> messages, SharedPreferences preferences) {
        return useFilter(preferences) && messages.first.contains(CHECK_PATTERN);
    }


    /**
     * Forming full text message for search.
     *
     * @param messages original messages.
     * @return full text message for search.
     */
    private String getSearchText(Pair<String, String> messages) {
        return new StringBuilder(messages.first).append(messages.second).append(' ').toString();
    }

}
