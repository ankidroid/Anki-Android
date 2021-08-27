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
import android.text.TextUtils
import androidx.annotation.CheckResult
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.ViewerCommand
import timber.log.Timber
import java.util.*

/**
 * Binding + additional contextual information
 * Also defines equality over bindings.
 * https://stackoverflow.com/questions/5453226/java-need-a-hash-map-where-one-supplies-a-function-to-do-the-hashing
 */
class MappableBinding(private val binding: Binding, private val side: CardSide) {
    val isKey: Boolean get() = binding.isKey
    val isKeyCode: Boolean get() = binding.isKeyCode

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val mappableBinding = other as MappableBinding
        val binding = mappableBinding.binding
        return if (side !== CardSide.BOTH && mappableBinding.side !== CardSide.BOTH && side !== mappableBinding.side) {
            false
        } else binding.getKeycode() == binding.getKeycode() &&
            binding.getUnicodeCharacter() == binding.getUnicodeCharacter() &&
            binding.getGesture() == binding.getGesture() &&
            modifierEquals(binding.getModifierKeys())
    }

    override fun hashCode(): Int {
        // don't include the modifierKeys or mSide
        return Objects.hash(binding.getKeycode(), binding.getUnicodeCharacter(), binding.getGesture())
    }

    private fun modifierEquals(keys: Binding.ModifierKeys?): Boolean {
        // equals allowing subclasses
        val thisKeys = binding.getModifierKeys()
        if (thisKeys === keys) {
            return true
        }
        // one is null
        return if (keys == null || thisKeys == null) {
            false
        } else (thisKeys.shiftMatches(true) == keys.shiftMatches(true) || thisKeys.shiftMatches(false) == keys.shiftMatches(false)) &&
            (thisKeys.ctrlMatches(true) == keys.ctrlMatches(true) || thisKeys.ctrlMatches(false) == keys.ctrlMatches(false)) &&
            (thisKeys.altMatches(true) == keys.altMatches(true) || thisKeys.altMatches(false) == keys.altMatches(false))

        // Perf: Could get a slight improvement if we check that both instances are not subclasses

        // allow subclasses to work - a subclass which overrides shiftMatches will return true on one of the tests
    }

    fun toDisplayString(context: Context): String {
        val formatString = when (side) {
            CardSide.QUESTION -> context.getString(R.string.display_binding_card_side_question)
            CardSide.ANSWER -> context.getString(R.string.display_binding_card_side_answer)
            else -> context.getString(R.string.display_binding_card_side_both) // intentionally no prefix
        }
        return String.format(formatString, binding.toDisplayString(context))
    }

    fun toPreferenceString(): String? {
        val s = StringBuilder()
        s.append(binding.toString())
        // don't serialise problematic bindings
        if (s.isEmpty()) {
            return null
        }
        when (side) {
            CardSide.QUESTION -> s.append('0')
            CardSide.ANSWER -> s.append('1')
            CardSide.BOTH -> s.append('2')
        }
        return s.toString()
    }

    companion object {
        const val PREF_SEPARATOR = '|'

        @CheckResult
        fun fromGesture(b: Binding): MappableBinding = MappableBinding(b, CardSide.BOTH)

        @CheckResult
        fun List<MappableBinding>.toPreferenceString(): String = TextUtils.join(PREF_SEPARATOR.toString(), this.mapNotNull { it.toPreferenceString() })

        @CheckResult
        fun fromString(s: String): MappableBinding? {
            return try {
                val binding = s.substring(0, s.length - 1)
                val b = Binding.fromString(binding)
                val side = when (s[s.length - 1]) {
                    '0' -> CardSide.QUESTION
                    '1' -> CardSide.ANSWER
                    else -> CardSide.BOTH
                }
                MappableBinding(b, side)
            } catch (e: Exception) {
                Timber.w(e, "failed to deserialize binding")
                null
            }
        }

        @CheckResult
        fun fromPreferenceString(string: String?): MutableList<MappableBinding> {
            if (TextUtils.isEmpty(string)) return ArrayList()
            return string!!.split(PREF_SEPARATOR).mapNotNull { fromString(it) }.toMutableList()
        }

        @CheckResult
        fun fromPreference(prefs: SharedPreferences, command: ViewerCommand): MutableList<MappableBinding> {
            val value = prefs.getString(command.preferenceKey, "") ?: return mutableListOf()
            return fromPreferenceString(value)
        }
    }
}
