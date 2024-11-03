/****************************************************************************************
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

object Consts {
    // Queue types
    enum class QueueType {
        MANUALLY_BURIED,
        SIBLING_BURIED,
        SUSPENDED,
        NEW,
        LRN,
        REV,
        DAY_LEARN_RELEARN,
        PREVIEW;

        /**
         * Whether this card can be reviewed.
         */
        fun reviewable() =
            when (this) {
                MANUALLY_BURIED, SIBLING_BURIED, SUSPENDED -> false
                NEW, LRN, REV, DAY_LEARN_RELEARN, PREVIEW -> true
            }

        fun toInt() = ordinal - 3

        companion object {
            fun Int.toQueueType() =
                QueueType.entries[this + 3]
        }
    }

    // Card types
    enum class CardType {
        NEW, LRN, REV, RELEARNING;
        companion object {
            fun Int.toCardType() = CardType.entries[this]
        }
    }

    // dynamic deck order
    /**
     * The priority order for filtered deck.
     * @param code The integer encoding this value in json.
     */
    enum class Dyn(val code: Int) {
        OLDEST(0),
        RANDOM(1),
        SMALLINT(2),
        BIGINT(3),
        LAPSES(4),
        ADDED(5),
        DUE(6),
        REVADDED(7),
        DUEPRIORITY(8),
        MAX_SIZE(99999);

        companion object {
            fun Int.toDyn() = Dyn.entries.first { it.code == this }
        }
    }

    // model types
    enum class ModelType {
        STD,
        CLOZE;
        companion object {
            fun Int.toModelType() = ModelType.entries[this]
        }
    }

    const val STARTING_FACTOR = 2500

    /** Only used by the dialog shown to user */
    const val BACKEND_SCHEMA_VERSION = 18

    const val SYNC_VER = 10

    // Leech actions
    const val LEECH_SUSPEND = 0

    // The labels defined in consts.py are in AnkiDroid's resources files.
    const val DEFAULT_DECK_ID: Long = 1

    val FIELD_SEPARATOR = "${'\u001f'}"

    /** Time duration for toast **/
    const val SHORT_TOAST_DURATION: Long = 2000
}
