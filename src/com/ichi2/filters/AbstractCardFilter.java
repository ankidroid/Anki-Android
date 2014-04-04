
package com.ichi2.filters;

import android.content.SharedPreferences;

/**
 * Abstract implementation for card filter.
 *
 * @author evgenij.kozhevnikov@gmail.com
 */
public abstract class AbstractCardFilter implements CardFilter {

    /**
     * Check filter setting.
     *
     * @param preferences program settings.
     * @return true, if filter could be run, otherwise false.
     */
    protected boolean useFilter(SharedPreferences preferences) {
        String settingName = this.getClass().getName();
        return preferences.getBoolean(settingName, false);
    }

}
