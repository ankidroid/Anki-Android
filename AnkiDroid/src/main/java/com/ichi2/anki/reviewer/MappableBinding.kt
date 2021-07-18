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

import java.util.*

/**
 * Binding + additional contextual information
 * Also defines equality over bindings.
 * https://stackoverflow.com/questions/5453226/java-need-a-hash-map-where-one-supplies-a-function-to-do-the-hashing
 */
class MappableBinding(private val mBinding: Binding, private val mSide: CardSide) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val mappableBinding = other as MappableBinding
        val binding = mappableBinding.mBinding
        return if (mSide !== CardSide.BOTH && mappableBinding.mSide !== CardSide.BOTH && mSide !== mappableBinding.mSide) {
            false
        } else binding.getKeycode() == binding.getKeycode() &&
            binding.getUnicodeCharacter() == binding.getUnicodeCharacter() &&
            binding.getGesture() == binding.getGesture() &&
            modifierEquals(binding.getModifierKeys())
    }

    override fun hashCode(): Int {
        // don't include the modifierKeys or mSide
        return Objects.hash(mBinding.getKeycode(), mBinding.getUnicodeCharacter(), mBinding.getGesture())
    }

    private fun modifierEquals(keys: Binding.ModifierKeys?): Boolean {
        // equals allowing subclasses
        val thisKeys = mBinding.getModifierKeys()
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

    companion object {
        fun fromGesture(b: Binding): MappableBinding = MappableBinding(b, CardSide.BOTH)
    }
}
