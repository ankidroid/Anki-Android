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
 *
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/9600f033f745bfae4e00dd9fa43e44d3b30c22d2/qt/aqt/tts.py
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

package com.ichi2.libanki

data class TtsVoice(
    val name: String,
    val lang: String
) {

    override fun toString(): String {
        var out = "{{tts $lang voices=$name}}"
        if (unavailable()) {
            out += " (unavailable)"
        }
        return out
    }

    fun unavailable(): Boolean = false
}

data class TtsVoiceMatch(val voice: TtsVoice, val rank: Int)
