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

import anki.config.ConfigKey
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.utils.KotlinCleanup

@KotlinCleanup("combine file with Sound.kt if done in libAnki")
/**
 * Records information about a text to speech tag.
 */
data class TTSTag(
    val fieldText: String,
    /**
     * Language may be empty if coming from AnkiDroid reading the whole card
     */
    val lang: String,
    val voices: List<String>,
    val speed: Float,
    /** each arg should be in the form 'foo=bar' */
    val otherArgs: List<String>,
) : AvTag()

/**
 * Contains the filename inside a [sound:...] tag.
 */
data class SoundOrVideoTag(val filename: String) : AvTag()

/** In python, this is a union of [TTSTag] and [SoundOrVideoTag] */
open class AvTag

// Methods

val AV_REF_RE = Regex("\\[anki:(play:(.):(\\d+))]")
val AV_PLAYLINK_RE = Regex("playsound:(.):(\\d+)")

fun stripAvRefs(text: String) = AV_REF_RE.replace(text, "")

/** Return card text with play buttons added, or stripped. */
suspend fun addPlayButtons(text: String): String {
    return if (withCol { config.getBool(ConfigKey.Bool.HIDE_AUDIO_PLAY_BUTTONS) }) {
        stripAvRefs(text)
    } else {
        avRefsToPlayIcons(text)
    }
}

/** Add play icons into the HTML */
fun avRefsToPlayIcons(text: String): String {
    return AV_REF_RE.replace(text) { match ->
        val groups = match.groupValues
        val side = groups[2]
        val index = groups[3]
        val playsound = "playsound:$side:$index"
        """<a class="replay-button soundLink" href=$playsound><span>
    <svg class="playImage" viewBox="0 0 64 64" version="1.1">
        <circle cx="32" cy="32" r="29" fill="lightgrey"/>
        <path d="M56.502,32.301l-37.502,20.101l0.329,-40.804l37.173,20.703Z" fill="black"/>Replay
    </svg>
</span></a>"""
    }
}

/**
 * Replaces [anki:play:q:0] with [sound:...]
 */
fun replaceWithSoundTags(
    content: String,
    renderOutput: TemplateRenderOutput,
): String {
    return AV_REF_RE.replace(content) { match ->
        val groups = match.groupValues

        val index = groups[3].toIntOrNull() ?: return@replace match.value

        val tag =
            when (groups[2]) {
                "q" -> renderOutput.questionAvTags.getOrNull(index)
                "a" -> renderOutput.answerAvTags.getOrNull(index)
                else -> null
            }
        if (tag !is SoundOrVideoTag) {
            return@replace match.value
        } else {
            return@replace "[sound:${tag.filename}]"
        }
    }
}

/** Extract av tag from playsound:q:x link */
suspend fun getAvTag(
    card: Card,
    url: String,
): AvTag? {
    return AV_PLAYLINK_RE.matchEntire(url)?.let {
        val values = it.groupValues
        val questionSide = values[1] == "q"
        val index = values[2].toInt()
        val tags =
            withCol {
                if (questionSide) {
                    card.questionAvTags()
                } else {
                    card.answerAvTags()
                }
            }
        if (index < tags.size) {
            tags[index]
        } else {
            null
        }
    }
}
