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

package com.ichi2.anki.reviewer

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.CheckResult
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.Binding.KeyBinding
import com.ichi2.utils.hash
import timber.log.Timber
import java.util.Objects

/**
 * Binding + additional contextual information
 */
open class MappableBinding(
    val binding: Binding,
) {
    val isKey: Boolean get() = binding is KeyBinding

    override fun equals(other: Any?): Boolean = this === other || (other is MappableBinding && other.binding == binding)

    override fun hashCode(): Int = Objects.hash(binding)

    open fun toDisplayString(context: Context): String = binding.toDisplayString(context)

    open fun toPreferenceString(): String? = binding.toString()

    companion object {
        const val PREF_SEPARATOR = '|'

        @CheckResult
        fun List<MappableBinding>.toPreferenceString(): String =
            this
                .mapNotNull { it.toPreferenceString() }
                .joinToString(prefix = "1/", separator = PREF_SEPARATOR.toString())

        @CheckResult
        private fun fromString(s: String): MappableBinding? {
            if (s.isEmpty()) {
                return null
            }
            return try {
                // the prefix of the serialized
                when (s[0]) {
                    ReviewerBinding.PREFIX -> ReviewerBinding.fromString(s.substring(1))
                    else -> null
                }
            } catch (e: Exception) {
                Timber.w(e, "failed to deserialize binding")
                null
            }
        }

        @CheckResult
        fun fromPreferenceString(string: String?): MutableList<MappableBinding> {
            if (string.isNullOrEmpty()) return ArrayList()
            try {
                val version = string.takeWhile { x -> x != '/' }
                val remainder = string.substring(version.length + 1) // skip the /
                if (version != "1") {
                    Timber.w("cannot handle version '$version'")
                    return ArrayList()
                }
                return remainder.split(PREF_SEPARATOR).mapNotNull { fromString(it) }.toMutableList()
            } catch (e: Exception) {
                Timber.w(e, "Failed to deserialize preference")
                return ArrayList()
            }
        }

        @CheckResult
        fun fromPreference(
            prefs: SharedPreferences,
            command: ViewerCommand,
        ): MutableList<MappableBinding> {
            val value = prefs.getString(command.preferenceKey, null) ?: return command.defaultValue.toMutableList()
            return fromPreferenceString(value)
        }
    }
}
