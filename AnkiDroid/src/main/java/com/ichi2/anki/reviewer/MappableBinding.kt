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
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ViewerCommand
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList

/**
 * Binding + additional contextual information
 * Also defines equality over bindings.
 * https://stackoverflow.com/questions/5453226/java-need-a-hash-map-where-one-supplies-a-function-to-do-the-hashing
 */
class MappableBinding(val binding: Binding, private val screen: Screen) {
    val isKey: Boolean get() = binding.isKey
    val isKeyCode: Boolean get() = binding.isKeyCode

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val otherMappableBinding = other as MappableBinding

        val otherBinding = otherMappableBinding.binding
        val bindingEquals = binding.keycode == otherBinding.keycode &&
            binding.unicodeCharacter == otherBinding.unicodeCharacter &&
            binding.gesture == otherBinding.gesture &&
            modifierEquals(otherBinding.modifierKeys)

        if (!bindingEquals) {
            return false
        }

        return screen.screenEquals(otherMappableBinding.screen)
    }

    override fun hashCode(): Int {
        // don't include the modifierKeys or mSide
        return Objects.hash(binding.keycode, binding.unicodeCharacter, binding.gesture, screen.prefix)
    }

    private fun modifierEquals(keys: Binding.ModifierKeys?): Boolean {
        // equals allowing subclasses
        val thisKeys = binding.modifierKeys
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
        return screen.toDisplayString(context, binding)
    }

    fun toPreferenceString(): String? {
        return screen.toPreferenceString(binding)
    }

    abstract class Screen private constructor(val prefix: Char) {
        abstract fun toPreferenceString(binding: Binding): String?
        abstract fun toDisplayString(context: Context, binding: Binding): String
        abstract fun screenEquals(otherScreen: Screen): Boolean

        class Reviewer(val side: CardSide) : Screen('r') {
            override fun toPreferenceString(binding: Binding): String? {
                if (!binding.isValid) {
                    return null
                }
                val s = StringBuilder()
                s.append(prefix)
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

            override fun toDisplayString(context: Context, binding: Binding): String {
                val formatString = when (side) {
                    CardSide.QUESTION -> context.getString(R.string.display_binding_card_side_question)
                    CardSide.ANSWER -> context.getString(R.string.display_binding_card_side_answer)
                    else -> context.getString(R.string.display_binding_card_side_both) // intentionally no prefix
                }
                return String.format(formatString, binding.toDisplayString(context))
            }

            override fun screenEquals(otherScreen: Screen): Boolean {
                val other: Reviewer = otherScreen as? Reviewer ?: return false

                return side === CardSide.BOTH ||
                    other.side === CardSide.BOTH ||
                    side === other.side
            }

            companion object {
                @CheckResult
                fun fromGesture(b: Binding): MappableBinding = MappableBinding(b, Reviewer(CardSide.BOTH))
                fun fromString(s: String): MappableBinding {
                    val binding = s.substring(0, s.length - 1)
                    val b = Binding.fromString(binding)
                    val side = when (s[s.length - 1]) {
                        '0' -> CardSide.QUESTION
                        '1' -> CardSide.ANSWER
                        else -> CardSide.BOTH
                    }
                    return MappableBinding(b, Reviewer(side))
                }
            }
        }
    }

    /** the serialisation version */
    enum class Version {
        ONE
    }

    companion object {
        const val PREF_SEPARATOR = '|'

        @CheckResult
        fun fromGesture(gesture: Gesture): MappableBinding = MappableBinding(Binding(gesture), Screen.Reviewer(CardSide.BOTH))

        @CheckResult
        fun fromGestureBinding(b: Binding): MappableBinding = MappableBinding(b, Screen.Reviewer(CardSide.BOTH))

        @CheckResult
        fun List<MappableBinding>.toPreferenceString(): String = this.mapNotNull { it.toPreferenceString() }
            .joinToString(prefix = "1/", separator = PREF_SEPARATOR.toString())

        @Suppress("UNUSED_PARAMETER")
        @CheckResult
        fun fromString(s: String, v: Version = Version.ONE): MappableBinding? {
            if (s.isEmpty()) {
                return null
            }
            return try {
                // the prefix of the serialized
                when (s[0]) {
                    'r' -> Screen.Reviewer.fromString(s.substring(1))
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
        fun fromPreference(prefs: SharedPreferences, command: ViewerCommand): MutableList<MappableBinding> {
            val value = prefs.getString(command.preferenceKey, null) ?: return command.defaultValue.toMutableList()
            return fromPreferenceString(value)
        }

        @CheckResult
        fun allMappings(prefs: SharedPreferences): MutableList<Pair<ViewerCommand, MutableList<MappableBinding>>> {
            return ViewerCommand.values().map {
                Pair(it, fromPreference(prefs, it))
            }.toMutableList()
        }
    }
}
