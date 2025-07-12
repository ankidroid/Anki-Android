/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.ui

import android.widget.CheckBox
import android.widget.EditText
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/*
 * Kotlin delegated properties simplify accessing common View properties:
 *
 * `private var description by textOf { deckDescriptionInput }`
 *
 * simplifies:
 *
 * ```
 *  private var description: String
 *      get() = deckDescriptionInput.text.toString()
 *      set(value) {
 *          deckDescriptionInput.setText(value)
 *      }
 * ```
 */

/**
 * Delegates to the value of [EditText.text]
 *
 * sample:
 *
 * ```kotlin
 * private var description by textOf { editText }
 * ```
 */
fun textOf(editText: () -> EditText): ReadWriteProperty<Any?, String> =
    object : ReadWriteProperty<Any?, String> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>,
        ) = editText().text.toString()

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: String,
        ) {
            editText().setText(value)
        }
    }

/**
 * Delegates to the value of [CheckBox.isChecked]
 *
 * sample:
 *
 * ```kotlin
 * private var enabled by isCheckedState { checkBox }
 * ```
 */
fun isCheckedState(checkBox: () -> CheckBox): ReadWriteProperty<Any?, Boolean> =
    object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>,
        ) = checkBox().isChecked

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Boolean,
        ) {
            checkBox().isChecked = value
        }
    }
