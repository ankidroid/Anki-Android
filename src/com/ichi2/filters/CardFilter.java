
package com.ichi2.filters;

import android.content.SharedPreferences;
import android.util.Pair;


/**
 * Card filter for garbage information. One card filter for one message type from one application. Card filters are
 * based on regular expressions, and when reg exp didn't find garbage information, method must return origin messages.
 * Because all filters run on a chain.
 * 
 * @author evgenij.kozhevnikov@gmail.com
 */
public interface CardFilter {

    /**
     * Run filter process.
     * 
     * @param messages data, received from external application, where first attribute is the SUBJECT information and
     *            second attribute is the TEXT information.
     * @param preferences program settings.
     * @return clean for current filter data.
     */
    public Pair<String, String> filter(Pair<String, String> messages, SharedPreferences preferences);

}
