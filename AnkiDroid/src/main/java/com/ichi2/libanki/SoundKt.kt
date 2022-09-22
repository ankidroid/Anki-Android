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
 *
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/2.1.34/pylib/anki/sound.py
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

@file:Suppress("unused")

package com.ichi2.libanki

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.utils.KotlinCleanup

@KotlinCleanup("combine file with Sound.kt if done in libAnki")
/**
 * Records information about a text to speech tag.
 */
data class TTSTag(
    val fieldText: str,
    /**
     * Language may be empty if coming from AnkiDroid reading the whole card
     */
    val lang: str,
    val voices: List<str>,
    val speed: Float,
    /** each arg should be in the form 'foo=bar' */
    val otherArgs: List<str>
) : AvTag()

/**
 * Contains the filename inside a [sound:...] tag.
 *
 * Video files also use [sound:...].
 */
data class SoundOrVideoTag(val filename: str) : AvTag()

/** In python, this is a union of [TTSTag] and [SoundOrVideoTag] */
open class AvTag

/* Methods */

val AV_REF_RE = Regex("\\[anki:(play:(.):(\\d+))]")
val AV_PLAYLINK_RE = Regex("playsound:(.):(\\d+)")

fun strip_av_refs(text: str) = AV_REF_RE.replace("", text)

fun addPlayIcons(content: String): String {
    return AV_REF_RE.replace(content) { match ->
        val groups = match.groupValues
        val side = groups[2]
        val index = groups[3]
        val playsound = "playsound:$side:$index"
        """<a class='replay-button replaybutton' href="$playsound"><span>
                    <svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="29" fill = "lightgrey"/>
                    <path d="M56.502,32.301l-37.502,20.101l0.329,-40.804l37.173,20.703Z" fill=black />Replay</svg>
                    </span></a>"""
    }
}

/** Extract av tag from playsound:q:x link */
suspend fun getAvTag(card: Card, url: String): AvTag? {
    return AV_PLAYLINK_RE.matchEntire(url)?.let {
        val values = it.groupValues
        val questionSide = values[1] == "q"
        val index = values[2].toInt()
        val tags = withCol {
            if (questionSide) { card.questionAvTags() } else { card.answerAvTags() }
        }
        if (index < tags.size) {
            tags[index]
        } else {
            null
        }
    }
}
