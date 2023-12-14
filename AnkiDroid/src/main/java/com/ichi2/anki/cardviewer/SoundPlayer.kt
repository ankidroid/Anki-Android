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

package com.ichi2.anki.cardviewer

import com.ichi2.libanki.SoundOrVideoTag
import com.ichi2.libanki.TTSTag

/**
 * Work in Progress
 *
 * Handles the two ways an Anki card defines sound:
 * * Regular Sound (file-based, mp3 etc..): [SoundOrVideoTag]
 * * Text to Speech [TTSTag]
 *
 * https://docs.ankiweb.net/templates/fields.html?highlight=tts#text-to-speech
 * No manual reference for [sound:], but this handles Sound or Video with a reference to the file
 * in the media directory.
 *
 * AnkiDroid also introduced a "tts" setting, which existed before Anki Desktop TTS.
 * This only allowed TTS if a setting was enabled,
 *
 * This class combines the above concerns behind an "adapter" interface in order to simplify complexity.
 *
 * I hope that we can then test and reduce the complexity of this class.
 */
@Suppress("unused")
class SoundPlayer
