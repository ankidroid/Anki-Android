/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer

import android.widget.EditText
import androidx.annotation.CheckResult
import com.ichi2.libanki.Model
import com.ichi2.libanki.ModelManager
import com.ichi2.utils.JSONObject
import timber.log.Timber
import java.util.*

/**
 * The language that a keyboard should open with when an [EditText] is selected
 * Used for a workflow improvement when adding a note
 */
typealias LanguageHint = Locale

object LanguageHintService {
    @JvmStatic
    @CheckResult
    fun getLanguageHintForField(field: JSONObject): LanguageHint? {
        if (!field.has("ad-hint-locale")) {
            return null
        }
        return Locale.forLanguageTag(field.getString("ad-hint-locale"))
    }

    @JvmStatic
    fun setLanguageHintForField(models: ModelManager, model: Model, fieldPos: Int, selectedLocale: Locale) {
        val field = model.getField(fieldPos)
        field.put("ad-hint-locale", selectedLocale.toLanguageTag())
        models.save(model)

        Timber.i("Set field locale to %s", selectedLocale)
    }
}
