// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.lifecycle.SavedStateHandle
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <T> SavedStateHandle.require(key: String): T =
    checkNotNull(get(key)) {
        "Wrong type or key '$key' not found"
    }

/** Reads and writes [key] directly. The property is null when the key has no value. */
internal fun <T> SavedStateHandle.value(key: String): ReadWriteProperty<Any?, T?> = valueDelegate(key, defaultValue = null)

/** Reads and writes [key] directly, using [defaultValue] when the key has no value. */
internal fun <T : Any> SavedStateHandle.value(
    key: String,
    defaultValue: T,
): ReadWriteProperty<Any?, T> = valueDelegate(key, defaultValue)

private fun <T> SavedStateHandle.valueDelegate(
    key: String,
    defaultValue: T,
): ReadWriteProperty<Any?, T> =
    object : ReadWriteProperty<Any?, T> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>,
        ): T = get<T>(key) ?: defaultValue

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: T,
        ) {
            set(key, value)
        }
    }
