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

fun strip_av_refs(text: str) = AV_REF_RE.replace("", text)
