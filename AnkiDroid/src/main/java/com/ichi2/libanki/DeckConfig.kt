/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 Copyright (c) 2020 Arthur Milchior <Arthur@Milchior.fr>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki

import androidx.annotation.VisibleForTesting
import com.ichi2.libanki.utils.set
import com.ichi2.utils.JSONObjectHolder
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Creates a copy from [JSONObject] and use it as a string
 *
 * The [jsonObject] parameter will be edited. Create a copy first if you need not to modify the parameter.
 *
 */
@JvmInline
value class DeckConfig(
    @VisibleForTesting
    override val jsonObject: JSONObject,
) : JSONObjectHolder {
    constructor(s: String) : this(
        JSONObject(s),
    )

    var conf: Long
        get() = jsonObject.getLong(CONF)
        set(value) {
            jsonObject.put(CONF, value)
        }

    var id: DeckConfigId
        get() = jsonObject.getLong(ID)
        set(value) {
            jsonObject.put(ID, value)
        }

    var name: String
        get() = jsonObject.getString(NAME)
        set(value) {
            jsonObject.put(NAME, value)
        }

    val waitForAudio: Boolean
        get() = jsonObject.optBoolean(WAIT_FOR_AUDIO, true)

    @VisibleForTesting(VisibleForTesting.NONE)
    fun removeAnswerAction() {
        jsonObject.remove(ANSWER_ACTION)
    }

    val stopTimerOnAnswer: Boolean
        get() = jsonObject.getBoolean(STOP_TIME_ON_ANSWER)

    var secondsToShowQuestion: Double
        get() = jsonObject.optDouble(SECONDS_TO_SHOW_QUESTION, 0.0)
        set(value) {
            jsonObject.set(SECONDS_TO_SHOW_QUESTION, value)
        }

    var secondsToShowAnswer: Double
        get() = jsonObject.optDouble(SECONDS_TO_SHOW_ANSWER, 0.0)
        set(value) {
            jsonObject.set(SECONDS_TO_SHOW_ANSWER, value)
        }

    /**
     * Time limit for answering in milliseconds.
     */
    val maxTaken: Int
        get() = jsonObject.getInt(MAX_TAKEN)

    var autoplay: Boolean
        get() = jsonObject.optBoolean(AUTOPLAY, true)

        @VisibleForTesting(VisibleForTesting.NONE)
        set(value) {
            jsonObject.put(AUTOPLAY, value)
        }

    var replayq: Boolean
        get() = jsonObject.getBoolean(REPLAY_Q)

        @VisibleForTesting(VisibleForTesting.NONE)
        set(value) {
            jsonObject.put(REPLAY_Q, value)
        }

    val timer: Boolean
        get() =
            // Note: Card.py used != 0, DeckOptions used == 1
            try {
                // #6089 - Anki 2.1.24 changed this to a bool, reverted in 2.1.25.
                jsonObject.getInt(TIMER) != 0
            } catch (e: Exception) {
                Timber.w(e)
                try {
                    jsonObject.getBoolean(TIMER)
                } catch (ex: Exception) {
                    Timber.w(ex)
                    true
                }
            }

    val new: New
        get() = New(jsonObject.getJSONObject(NEW))

    @JvmInline
    value class New(
        override val jsonObject: JSONObject,
    ) : JSONObjectHolder {
        var perDay: Int
            get() = jsonObject.getInt(PER_DAY)
            set(value) {
                jsonObject.put(PER_DAY, value)
            }

        @VisibleForTesting
        var delays: JSONArray
            get() = jsonObject.getJSONArray(DELAYS)
            set(value) {
                jsonObject.put(DELAYS, value)
            }

        /**
         * Whether sibling of reviewed cards get buried.
         */
        @VisibleForTesting
        var bury: Boolean
            get() = jsonObject.getBoolean(BURY)
            set(value) {
                jsonObject.put(BURY, value)
            }
    }

    val lapse: Lapse
        get() = Lapse(jsonObject.getJSONObject(LAPSE))

    @JvmInline
    value class Lapse(
        override val jsonObject: JSONObject,
    ) : JSONObjectHolder {
        @VisibleForTesting
        var delays: JSONArray
            get() = jsonObject.getJSONArray(DELAYS)
            set(value) {
                jsonObject.put(DELAYS, value)
            }

        @VisibleForTesting
        var leechAction: Int
            get() = jsonObject.getInt(LEECH_ACTION)
            set(value) {
                jsonObject.put(LEECH_ACTION, value)
            }

        @VisibleForTesting
        var mult: Double
            get() = jsonObject.getDouble(MULT)
            set(value) {
                jsonObject.put(MULT, value)
            }
    }

    val rev: Rev
        get() = Rev(jsonObject.getJSONObject(REV))

    @JvmInline
    value class Rev(
        override val jsonObject: JSONObject,
    ) : JSONObjectHolder {
        @VisibleForTesting
        var delays: JSONArray
            get() = jsonObject.getJSONArray(DELAYS)
            set(value) {
                jsonObject.put(DELAYS, value)
            }

        @VisibleForTesting
        var hardFactor: Int
            get() = jsonObject.getInt(HARD_FACTOR)
            set(value) {
                jsonObject.put(HARD_FACTOR, value)
            }
    }

    companion object {
        const val CONF = "conf"
        const val ID = "id"
        const val NAME = "name"
        const val WAIT_FOR_AUDIO = "waitForAudio"
        const val STOP_TIME_ON_ANSWER = "stopTimerOnAnswer"
        const val QUESTION_ACTION = "questionAction"
        const val DELAYS = "delays"
        const val SECONDS_TO_SHOW_QUESTION = "secondsToShowQuestion"
        const val SECONDS_TO_SHOW_ANSWER = "secondsToShowAnswer"
        const val MAX_TAKEN = "maxTaken"
        const val AUTOPLAY = "autoplay"
        const val REPLAY_Q = "replayq"
        const val TIMER = "timer"
        const val NEW = "new"
        const val HARD_FACTOR = "hardFactor"
        const val BURY = "bury"
        const val LAPSE = "lapse"
        const val LEECH_ACTION = "leechAction"
        const val MULT = "mult"
        const val REV = "rev"
        const val PER_DAY = "perDay"
        const val ANSWER_ACTION = "answerAction"
    }
}
