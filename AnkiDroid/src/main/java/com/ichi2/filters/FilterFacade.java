
package com.ichi2.filters;

import android.content.Context;
import android.util.Pair;

import com.ichi2.anki.AnkiDroidApp;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter-facade execute all filtering operations.
 * 
 * @author evgenij.kozhevnikov@gmail.com
 */
public class FilterFacade {

    /* Context for checking preferences */
    private Context context;

    /* All filters, that are will running */
    private final List<CardFilter> filters = new ArrayList<CardFilter>() {
        {
            add(new GoogleTranslaterFilter());
        }
    };


    public FilterFacade(Context context) {
        this.context = context;
    }


    /**
     * Run all filters processes. Messages in params will be updated.
     * 
     * @param messages data, received from external application, where first attribute is the SUBJECT information and
     *            second attribute is the TEXT information.
     */
    public Pair<String, String> filter(Pair<String, String> messages) {
        Pair<String, String> result = new Pair<String, String>(messages.first, messages.second);
        for (CardFilter cardFilter : filters) {
            result = cardFilter.filter(result, AnkiDroidApp.getSharedPrefs(context));
        }
        return result;
    }

}
