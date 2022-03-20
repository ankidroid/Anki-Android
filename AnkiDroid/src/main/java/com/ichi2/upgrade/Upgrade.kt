//noinspection MissingCopyrightHeader #8659
package com.ichi2.upgrade

import com.ichi2.libanki.Collection
import com.ichi2.utils.JSONException
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

object Upgrade {
    @JvmStatic
    @KotlinCleanup("make name non-null + rename `val` variable")
    fun upgradeJSONIfNecessary(col: Collection, name: String?, defaultValue: Boolean): Boolean {
        var `val` = defaultValue
        try {
            `val` = col.get_config_boolean(name!!)
        } catch (e: JSONException) {
            Timber.w(e)
            // workaround to repair wrong values from older libanki versions
            try {
                col.set_config(name!!, `val`)
            } catch (e1: JSONException) {
                Timber.w(e1)
                // do nothing
            }
            col.save()
        }
        return `val`
    }
}
