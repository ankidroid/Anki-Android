/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.compat

import android.annotation.TargetApi
import android.icu.util.ULocale
import com.ichi2.utils.isRobolectric
import timber.log.Timber
import java.util.Locale

/** Implementation of [Compat] for SDK level 24 and higher. Check [Compat]'s for more detail.  */
@TargetApi(24)
open class CompatV24 : CompatV23(), Compat {
    override fun normalize(locale: Locale): Locale {
        // ULocale isn't currently handled by Robolectric
        if (isRobolectric) {
            return super.normalize(locale)
        }
        return try {
            val uLocale = ULocale(locale.language, locale.country, locale.variant)
            Locale(uLocale.language, uLocale.country, uLocale.variant)
        } catch (e: Exception) {
            Timber.w("Failed to normalize locale %s", locale, e)
            locale
        }
    }
}
