/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2014 Timothy rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki

import java.util.regex.Pattern

val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mpg", "mpeg", "mkv", "avi")

// NICE_TO_HAVE: Abstract, then add tests for #6111
/**
 * Parses, loads and plays sound & video files
 * Called `Sound` Anki uses `[sound:]` for both audio and video
 */
class Sound {
    /**
     * The subset of sounds to involve
     * @param int Used for serialisation
     */
    enum class SoundSide(val int: Int) {
        QUESTION(0), ANSWER(1), QUESTION_AND_ANSWER(2);
    }

    /** Sounds for the question/answer of a card */
    // Stops code paths where QUESTION_AND_ANSWER is invalid
    enum class SingleSoundSide {
        QUESTION, ANSWER;

        fun toSoundSide(): SoundSide = when (this) {
            QUESTION -> SoundSide.QUESTION
            ANSWER -> SoundSide.ANSWER
        }
    }

    companion object {
        /**
         * Pattern used to identify the markers for sound files
         */
        val SOUND_PATTERN: Pattern = Pattern.compile("\\[sound:([^\\[\\]]*)]")

        // TODO join with SOUND_PATTERN
        val SOUND_RE = SOUND_PATTERN.toRegex()

        /**
         * expandSounds takes content with embedded sound file placeholders and expands them to reference the actual media
         * file
         *
         * @param content -- card content to be rendered that may contain embedded audio
         * @return -- the same content but in a format that will render working play buttons when audio was embedded
         */
        fun expandSounds(content: String): String {
            return avRefsToPlayIcons(content)
        }
    }
}
