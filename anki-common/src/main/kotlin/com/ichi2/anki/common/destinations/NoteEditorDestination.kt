// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.ichi2.anki.libanki.DeckId

/** Opens the Add Note/Note Editor screen. */
sealed class NoteEditorDestination : Destination() {
    /**
     * Opens the Note Editor to add a new note.
     * @property deckId Optional deck to pre-select for the new note.
     */
    data class AddNote(
        val deckId: DeckId? = null,
    ) : NoteEditorDestination()

    /**
     * Forwards an incoming intent's payload to the Note Editor.
     *
     * Relays `ACTION_SEND` (or similar) intent to the Note Editor, preserving the
     * originating intent's [action], [data], and [type] alongside the [arguments] bundle.
     *
     * @property arguments Extras to forward to the Note Editor.
     * @property action Optional [Intent.setAction] value.
     * @property data Optional [Intent.setDataAndType] data.
     * @property type Optional [Intent.setDataAndType] MIME type.
     */
    data class PassArguments(
        val arguments: Bundle,
        val action: String? = null,
        val data: Uri? = null,
        val type: String? = null,
    ) : NoteEditorDestination() {
        companion object {
            /**
             * Snapshots [intent]'s action, data, and type alongside the [extras]
             * for forwarding to the Note Editor.
             */
            fun from(
                intent: Intent,
                // extras should come from the intent, have the caller perform the null-check
                extras: Bundle,
            ): PassArguments =
                PassArguments(
                    arguments = extras,
                    action = intent.action,
                    data = intent.data,
                    type = intent.type,
                )
        }
    }
}
