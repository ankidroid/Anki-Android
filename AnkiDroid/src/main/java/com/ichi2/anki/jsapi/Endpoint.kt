/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.con>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.jsapi

/**
 * Represents a JavaScript API Endpoint.
 *
 * It should be structured as `service-base/endpoint`
 */
sealed interface Endpoint {
    /** Base path of the service */
    val base: String
    val value: String

    enum class Android(
        override val value: String,
    ) : Endpoint {
        SHOW_SNACKBAR("show-snackbar"),
        IS_SYSTEM_IN_DARK_MODE("is-system-in-dark-mode"),
        IS_NETWORK_METERED("is-network-metered"),
        ;

        override val base: String = "android"
    }

    enum class Card(
        override val value: String,
    ) : Endpoint {
        GET_ID("get-id"),
        GET_FLAG("get-flag"),
        GET_REPS("get-reps"),
        GET_INTERVAL("get-interval"),
        GET_FACTOR("get-factor"),
        GET_MOD("get-mod"),
        GET_NID("get-nid"),
        GET_TYPE("get-type"),
        GET_DID("get-did"),
        GET_LEFT("get-left"),
        GET_O_DID("get-o-did"),
        GET_O_DUE("get-o-due"),
        GET_QUEUE("get-queue"),
        GET_LAPSES("get-lapses"),
        GET_DUE("get-due"),
        GET_QUESTION("get-question"),
        GET_ANSWER("get-answer"),
        IS_MARKED("is-marked"),
        BURY("bury"),
        SUSPEND("suspend"),
        UNBURY("unbury"),
        UNSUSPEND("unsuspend"),
        RESET_PROGRESS("reset-progress"),
        TOGGLE_FLAG("toggle-flag"),
        GET_REVIEW_LOGS("get-review-logs"),
        ;

        override val base = "card"
    }

    enum class Collection(
        override val value: String,
    ) : Endpoint {
        UNDO("undo"),
        REDO("redo"),
        IS_UNDO_AVAILABLE("is-undo-available"),
        IS_REDO_AVAILABLE("is-redo-available"),
        FIND_CARDS("find-cards"),
        FIND_NOTES("find-notes"),
        ;

        override val base = "collection"
    }

    enum class Deck(
        override val value: String,
    ) : Endpoint {
        GET_ID("get-id"),
        GET_NAME("get-name"),
        IS_FILTERED("is-filtered"),
        ;

        override val base = "deck"
    }

    enum class Note(
        override val value: String,
    ) : Endpoint {
        GET_ID("get-id"),
        GET_NOTE_TYPE_ID("get-note-type-id"),
        GET_CARD_IDS("get-card-ids"),
        BURY("bury"),
        SUSPEND("suspend"),
        GET_TAGS("get-tags"),
        SET_TAGS("set-tags"),
        TOGGLE_MARK("toggle-mark"),
        ;

        override val base = "note"
    }

    enum class NoteType(
        override val value: String,
    ) : Endpoint {
        GET_ID("get-id"),
        GET_NAME("get-name"),
        IS_IMAGE_OCCLUSION("is-image-occlusion"),
        IS_CLOZE("is-cloze"),
        GET_FIELD_NAMES("get-field-names"),
        ;

        override val base = "note-type"
    }

    enum class StudyScreen(
        override val value: String,
    ) : Endpoint {
        GET_NEW_COUNT("get-new-count"),
        GET_LEARNING_COUNT("get-learning-count"),
        GET_TO_REVIEW_COUNT("get-to-review-count"),
        SHOW_ANSWER("show-answer"),
        ANSWER("answer"),
        IS_SHOWING_ANSWER("is-showing-answer"),
        GET_NEXT_TIME("get-next-time"),
        GET_NEXT_TIMES("get-next-times"),
        OPEN_CARD_INFO("open-card-info"),
        OPEN_NOTE_EDITOR("open-note-editor"),
        DELETE_NOTE("delete-note"),
        ;

        override val base = "study-screen"
    }

    enum class Tts(
        override val value: String,
    ) : Endpoint {
        SPEAK("speak"),
        SET_LANGUAGE("set-language"),
        SET_PITCH("set-pitch"),
        SET_SPEECH_RATE("set-speech-rate"),
        IS_SPEAKING("is-speaking"),
        STOP("stop"),
        ;

        override val base = "tts"
    }

    companion object {
        /**
         * A map of all possible endpoints, indexed by a pair of their base and value strings.
         */
        private val allEndpoints by lazy {
            Endpoint::class
                .sealedSubclasses
                .flatMap { it.java.enumConstants?.asList() ?: emptyList() }
                .associateBy { it.base to it.value }
        }

        /**
         * Retrieves a specific Endpoint enum constant based on its base and value.
         *
         * @param base The base string of the endpoint (e.g., "card").
         * @param value The value string of the endpoint (e.g., "get-id").
         * @return The matching [Endpoint], or `null` if no match is found.
         */
        fun from(
            base: String,
            value: String,
        ): Endpoint? = allEndpoints[base to value]
    }
}
