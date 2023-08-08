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

import androidx.annotation.IntDef
import kotlin.annotation.Retention

object Consts {
    // Queue types
    const val QUEUE_TYPE_MANUALLY_BURIED = -3
    const val QUEUE_TYPE_SIBLING_BURIED = -2
    const val QUEUE_TYPE_SUSPENDED = -1
    const val QUEUE_TYPE_NEW = 0
    const val QUEUE_TYPE_LRN = 1
    const val QUEUE_TYPE_REV = 2
    const val QUEUE_TYPE_DAY_LEARN_RELEARN = 3
    const val QUEUE_TYPE_PREVIEW = 4

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(QUEUE_TYPE_MANUALLY_BURIED, QUEUE_TYPE_SIBLING_BURIED, QUEUE_TYPE_SUSPENDED, QUEUE_TYPE_NEW, QUEUE_TYPE_LRN, QUEUE_TYPE_REV, QUEUE_TYPE_DAY_LEARN_RELEARN, QUEUE_TYPE_PREVIEW)
    annotation class CARD_QUEUE

    // Card types
    const val CARD_TYPE_NEW = 0
    const val CARD_TYPE_LRN = 1
    const val CARD_TYPE_REV = 2
    const val CARD_TYPE_RELEARNING = 3

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(CARD_TYPE_NEW, CARD_TYPE_LRN, CARD_TYPE_REV, CARD_TYPE_RELEARNING)
    annotation class CARD_TYPE

    // dynamic deck order
    const val DYN_OLDEST = 0
    const val DYN_RANDOM = 1
    const val DYN_SMALLINT = 2
    const val DYN_BIGINT = 3
    const val DYN_LAPSES = 4
    const val DYN_ADDED = 5
    const val DYN_DUE = 6
    const val DYN_REVADDED = 7
    const val DYN_DUEPRIORITY = 8
    const val DYN_MAX_SIZE = 99999

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(DYN_OLDEST, DYN_RANDOM, DYN_SMALLINT, DYN_BIGINT, DYN_LAPSES, DYN_ADDED, DYN_DUE, DYN_REVADDED, DYN_DUEPRIORITY)
    annotation class DYN_PRIORITY

    // model types
    const val MODEL_STD = 0
    const val MODEL_CLOZE = 1

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(MODEL_STD, MODEL_CLOZE)
    annotation class MODEL_TYPE

    // deck types
    const val DECK_DYN = 1

    const val STARTING_FACTOR = 2500

    /** Only used by the dialog shown to user */
    const val BACKEND_SCHEMA_VERSION = 18

    const val SYNC_VER = 10

    // Leech actions
    const val LEECH_SUSPEND = 0

    // Buttons
    const val BUTTON_ONE = 1
    const val BUTTON_TWO = 2
    const val BUTTON_THREE = 3
    const val BUTTON_FOUR = 4

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(BUTTON_ONE, BUTTON_TWO, BUTTON_THREE, BUTTON_FOUR)
    annotation class BUTTON_TYPE

    // The labels defined in consts.py are in AnkiDroid's resources files.
    const val DEFAULT_DECK_ID: Long = 1

    val FIELD_SEPARATOR = Character.toString('\u001f')

    /** Time duration for toast **/
    const val SHORT_TOAST_DURATION: Long = 2000
}
