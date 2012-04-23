package com.ichi2.filters;

import android.content.SharedPreferences;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remove unnecessary information from Google Translate.
 *
 * @author evgenij.kozhevnikov@gmail.com
 * */
public class GoogleTranslaterFilter extends AbstractCardFilter{

    public static final String CHECK_PATTERN = "Google";

    private static final String SEARCH_PATTERN = "(?:\\):\\s*)(.*)(?:\\s|\\b)";

    public CardFilterMessage filter(CardFilterMessage message, SharedPreferences preferences) {
        Pattern pattern = Pattern.compile(SEARCH_PATTERN);
        Matcher matcher = pattern.matcher(getSearchText(message));
        if (isCanBeExecuted(message, preferences) && matcher.find()) {
            String translate = matcher.group(1);
            if (matcher.find()) {
                message = new CardFilterMessage(matcher.group(1), translate);
            }
        }
        return message;
    }

    /**
     * Check conditions to running current filter.
     *
     * @param messages
     *          original messages.
     * @param preferences
     *          program settings.
     * @return true, if filter could be run, otherwise false.
     * */
    private boolean isCanBeExecuted(CardFilterMessage messages, SharedPreferences preferences) {
        return useFilter(preferences) && messages.subject.contains(CHECK_PATTERN);
    }

    /**
     * Forming full text message for search.
     *
     * @param messages
     *      original messages.
     * @return full text message for search.
     * */
    private String getSearchText(CardFilterMessage messages) {
        return new StringBuilder(messages.subject)
                .append(messages.text)
                .append(' ')
                .toString();
    }

}
