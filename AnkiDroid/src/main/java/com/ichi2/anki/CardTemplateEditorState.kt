/*
 * Copyright (c) 2025 Snowiee <xenonnn4w@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import com.ichi2.anki.libanki.CardOrdinal

/**
 * Which template editor view is currently displayed.
 * The View layer handles mapping to actual View IDs.
 */
enum class EditorViewType {
    FRONT,
    BACK,
    STYLING,
}

/** Encapsulates the entire state for [CardTemplateEditor] */
sealed class CardTemplateEditorState {
    /** Initial loading state, no data available yet */
    data object Loading : CardTemplateEditorState()

    /**
     * Successfully loaded, all data is valid.
     * This is the main working state where the user is editing templates.
     */
    data class Loaded(
        /** The currently selected template ordinal (0-based index) */
        val currentTemplateOrd: CardOrdinal = 0,
        /** The currently selected editor view (front/back/styling) */
        val currentEditorView: EditorViewType = EditorViewType.FRONT,
        /** Simple transient messages in response to user actions or null for no message */
        val message: UserMessage? = null,
    ) : CardTemplateEditorState()

    /** Error during loading */
    data class InitializationError(
        val exception: ReportableException,
    ) : CardTemplateEditorState()

    /** Finished, activity should close */
    data object Finished : CardTemplateEditorState()

    /** Simple message to be shown to the user */
    enum class UserMessage {
        CantDeleteLastTemplate,
        CantAddTemplateToDynamic,
        SaveSuccess,
        DeletionWouldOrphanNote,
    }

    /**
     * Wrapper around an exception produced in [CardTemplateEditor] with an extra flag about the
     * exception being reportable or not.
     */
    data class ReportableException(
        val source: Throwable,
        /** true if this exception should be sent to [com.ichi2.anki.CrashReportService] */
        val isReportable: Boolean = true,
    )
}
