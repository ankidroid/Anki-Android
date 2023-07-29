//noinspection MissingCopyrightHeader #8659
package com.ichi2.upgrade

import com.ichi2.libanki.Collection
import org.json.JSONException
import timber.log.Timber

/**
 * Ensure the configuration [name] is set to a value that can be cast to a Boolean.
 * @param name the configuration key's name
 * @return current value if it exists and can be cast to Boolean, or default value
 */
fun Collection.upgradeJSONIfNecessary(name: String, defaultValue: Boolean) =
    try {
        get_config_boolean(name)
    } catch (e: JSONException) {
        Timber.w(e)
        // workaround to repair wrong values from older libanki versions
        try {
            set_config(name, defaultValue)
        } catch (e1: JSONException) {
            Timber.w(e1)
            // do nothing
        }
        defaultValue
    }
