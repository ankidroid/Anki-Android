/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat

class EditNotifications : AnkiActivity() {
    private lateinit var mode: Mode

    /**
     * Mode: The possible modes the EditNotifications activity can be triggered in
     */
    private enum class Mode {
        GLOBAL_APP_WIDE,
        GLOBAL_DECK_SPECIFIC,
        SINGLE_DECK_SPECIFIC,
    }

    // The following are only used for SINGLE_DECK_SPECIFIC mode:
    private var did = -1L
    private var deckName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_notifications)
        val toolbar = enableToolbar()
        toolbar.setDisplayHomeAsUpEnabled(true)

        // Retrieve extras, update toolbar accordingly
        val extras = intent.extras
        mode = extras!!.getSerializableCompat<Mode>("mode") as Mode

        toolbar.title =
            when (mode) {
                Mode.GLOBAL_APP_WIDE -> "App-wide notifications"
                Mode.GLOBAL_DECK_SPECIFIC -> "Deck-specific notifications"
                Mode.SINGLE_DECK_SPECIFIC -> "Edit notifications"
            }

        if (mode == Mode.SINGLE_DECK_SPECIFIC) {
            did = extras.getLong("did")
            launchCatchingTask {
                deckName =
                    withCol {
                        decks.name(did)
                    }
                // Set subtitle, too
                toolbar.subtitle = deckName
            }
        }
    }

    companion object {
        fun getIntentForGlobalAppWideEditing(context: Context): Intent {
            val intent = Intent(context, EditNotifications::class.java)
            intent.putExtra("mode", Mode.GLOBAL_APP_WIDE)
            return intent
        }

        fun getIntentForGlobalDeckSpecificEditing(context: Context): Intent {
            val intent = Intent(context, EditNotifications::class.java)
            intent.putExtra("mode", Mode.GLOBAL_DECK_SPECIFIC)
            return intent
        }

        fun getIntentForSingleDeckSpecificEditing(
            context: Context,
            did: Long,
        ): Intent {
            val intent = Intent(context, EditNotifications::class.java)
            intent.putExtra("mode", Mode.SINGLE_DECK_SPECIFIC)
            intent.putExtra("did", did)
            return intent
        }
    }
}
