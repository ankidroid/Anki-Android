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
package com.ichi2.anki.libanki

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.common.json.JSONObjectHolder
import com.ichi2.anki.libanki.utils.set
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
        get() = jsonObject.getLong("conf")
        set(value) {
            jsonObject.put("conf", value)
        }

    var id: DeckConfigId
        get() = jsonObject.getLong("id")
        set(value) {
            jsonObject.put("id", value)
        }

    var name: String
        get() = jsonObject.getString("name")
        set(value) {
            jsonObject.put("name", value)
        }

    val waitForAudio: Boolean
        get() = jsonObject.optBoolean("waitForAudio", true)

    @VisibleForTesting(VisibleForTesting.NONE)
    fun removeAnswerAction() {
        jsonObject.remove(ANSWER_ACTION)
    }

    val stopTimerOnAnswer: Boolean
        get() = jsonObject.getBoolean("stopTimerOnAnswer")

    var secondsToShowQuestion: Double
        get() = jsonObject.optDouble("secondsToShowQuestion", 0.0)
        set(value) {
            jsonObject.set("secondsToShowQuestion", value)
        }

    var secondsToShowAnswer: Double
        get() = jsonObject.optDouble("secondsToShowAnswer", 0.0)
        set(value) {
            jsonObject.set("secondsToShowAnswer", value)
        }

    /**
     * Time limit for answering in milliseconds.
     */
    val maxTaken: Int
        get() = jsonObject.getInt("maxTaken")

    var autoplay: Boolean
        get() = jsonObject.optBoolean("autoplay", true)

        @VisibleForTesting(VisibleForTesting.NONE)
        set(value) {
            jsonObject.put("autoplay", value)
        }

    var replayq: Boolean
        get() = jsonObject.getBoolean("replayq")

        @VisibleForTesting(VisibleForTesting.NONE)
        set(value) {
            jsonObject.put("replayq", value)
        }

    val timer: Boolean
        get() =
            // Note: Card.py used != 0, DeckOptions used == 1
            try {
                // #6089 - Anki 2.1.24 changed this to a bool, reverted in 2.1.25.
                jsonObject.getInt("timer") != 0
            } catch (e: Exception) {
                Timber.w(e)
                try {
                    jsonObject.getBoolean("timer")
                } catch (ex: Exception) {
                    Timber.w(ex)
                    true
                }
            }

    val new: New
        get() = New(jsonObject.getJSONObject("new"))

    @JvmInline
    value class New(
        override val jsonObject: JSONObject,
    ) : JSONObjectHolder {
        var perDay: Int
            get() = jsonObject.getInt("perDay")
            set(value) {
                jsonObject.put("perDay", value)
            }

        @VisibleForTesting
        var delays: JSONArray
            get() = jsonObject.getJSONArray("delays")
            set(value) {
                jsonObject.put("delays", value)
            }

        /**
         * Whether sibling of reviewed cards get buried.
         */
        @VisibleForTesting
        var bury: Boolean
            get() = jsonObject.getBoolean("bury")
            set(value) {
                jsonObject.put("bury", value)
            }
    }

    val lapse: Lapse
        get() = Lapse(jsonObject.getJSONObject("lapse"))

    @JvmInline
    value class Lapse(
        override val jsonObject: JSONObject,
    ) : JSONObjectHolder {
        @VisibleForTesting
        var delays: JSONArray
            get() = jsonObject.getJSONArray("delays")
            set(value) {
                jsonObject.put("delays", value)
            }

        @VisibleForTesting
        var leechAction: Int
            get() = jsonObject.getInt("leechAction")
            set(value) {
                jsonObject.put("leechAction", value)
            }

        @VisibleForTesting
        var mult: Double
            get() = jsonObject.getDouble("mult")
            set(value) {
                jsonObject.put("mult", value)
            }
    }

    val rev: Rev
        get() = Rev(jsonObject.getJSONObject("rev"))

    @JvmInline
    value class Rev(
        override val jsonObject: JSONObject,
    ) : JSONObjectHolder {
        @VisibleForTesting
        var delays: JSONArray
            get() = jsonObject.getJSONArray("delays")
            set(value) {
                jsonObject.put("delays", value)
            }

        @VisibleForTesting
        var hardFactor: Int
            get() = jsonObject.getInt("hardFactor")
            set(value) {
                jsonObject.put("hardFactor", value)
            }
    }

    companion object {
        const val QUESTION_ACTION = "questionAction"
        const val ANSWER_ACTION = "answerAction"
    }
}
