package com.ichi2.filters;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter-facade execute all filtering operations.
 *
 * @author evgenij.kozhevnikov@gmail.com
 * */
public class FilterFacade {

    /* Setting name */
    public static final String FIX_EXTERNAL_DATA = "fixExternalData";

    /* Context for checking preferences */
    private Context context;

    /* All filters, that are will running */
    private final List<CardFilter> filters = new ArrayList<CardFilter>() {{
        add(new GoogleTranslaterFilter());
    }};

    public FilterFacade(Context context) {
        this.context = context;
    }

    /**
     * Run all filters processes. Messages in params will be updated.
     *
     * @param  messages
     *      data, received from external application, where first attribute is the SUBJECT information and second
     *      attribute is the TEXT information.
     * */
    public Pair<String, String> filter(Pair<String, String> messages) {
        Pair<String, String> result = new Pair<String, String>(messages.first, messages.second);
        if (useFilters()) {
            for (CardFilter cardFilter : filters) {
                result = cardFilter.filter(result);
            }
        }
        return result;
    }

    /**
     * Check preferenses for fixExternalData option.
     *
     * @return true, if options is on, else - false.
     * */
    private boolean useFilters() {
        SharedPreferences preferences = PrefSettings
                .getSharedPrefs(context);
        return preferences.getBoolean(FIX_EXTERNAL_DATA, false);
    }

}
