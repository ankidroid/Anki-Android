/****************************************************************************************
 * Copyright (c) 2022 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
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
package com.ichi2.anki.notetype

import android.content.Intent
import anki.notetypes.NotetypeNameId
import com.ichi2.libanki.NoteTypeId

/**
 * Contains the required information for [ManageNotetypes].
 * @param isProcessing true if there's ongoing work happening and [ManageNotetypes] should show a
 * "loading" ui while this takes place
 * @param destination if non null then [ManageNotetypes] should navigate to the screen referenced by
 * the destination
 */
data class NotetypesUiState(
    val notetypes: List<NotetypeItemUiState> = emptyList(),
    val isProcessing: Boolean = true,
    val destination: NotetypesDestination? = null,
)

/**
 * Encapsulate the possible destination to go from [ManageNotetypes].
 * @param extras the optional extras that [ManageNotetypes] might want to send through an [Intent]
 * to screens that it starts
 */
sealed class NotetypesDestination(
    val extras: Map<String, Any>,
) {
    data class CardTemplateEditor(
        val templateExtras: Map<String, Any>,
    ) : NotetypesDestination(templateExtras)

    data class Fields(
        val fieldsExtras: Map<String, Any>,
    ) : NotetypesDestination(fieldsExtras)
}

/**
 * Contains the data to display a single note type in [ManageNotetypes]'s list of notetypes.
 * @param useCount the number of note using this note type
 */
data class NotetypeItemUiState(
    val id: NoteTypeId,
    val name: String,
    val useCount: Int,
    val onNavigateTo: (NotetypesDestination) -> Unit,
)

/**
 * Contains the data to display a single note type in [AddNewNotesType]'s list of notetypes.
 * @param isStandard true if this is a note type provided by Anki by default. If false, this is one
 * of the note types currently in this collection (potentially a clone of a standard note type)
 */
data class AddNotetypeUiModel(
    val id: NoteTypeId,
    val name: String,
    val isStandard: Boolean = false,
)

/**
 * A note type from current collection as a [AddNotetypeUiModel].
 */
fun NotetypeNameId.toUiModel(): AddNotetypeUiModel = AddNotetypeUiModel(id, name, false)
