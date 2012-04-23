package com.ichi2.filters;

import android.content.Context;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter-facade execute all filtering operations.
 *
 * @author evgenij.kozhevnikov@gmail.com
 * */
public class FilterFacade {

    /* Context for checking preferences */
    private Context context;

    /* All filters, that are will running */
    private final List<CardFilter> filters = new ArrayList<CardFilter>();

    public FilterFacade(Context context) {
        this.context = context;
        
        filters.add(new GoogleTranslaterFilter());
    }

    /**
     * Run all filters processes.
     *
     * @param  message subject and text received from external application
     */
    public CardFilterMessage filter(CardFilterMessage message) {
        for (CardFilter cardFilter : filters) {
            message = cardFilter.filter(message,  PrefSettings.getSharedPrefs(context));
        }
        return message;
    }

}
