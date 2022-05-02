/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.dialogs

import android.content.Intent
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.RecursiveMenuDialog.Companion.createInstance
import com.ichi2.anki.dialogs.RecursiveMenuItemAction.*
import com.ichi2.annotations.NeedsTest
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

@NeedsTest("Selecting APKG allows multiple files")
@NeedsTest("Selecting COLPKG does not allow multiple files")
@NeedsTest("Restore backup dialog does not allow multiple files")
class ImportFileSelectionFragment {

    companion object {
        @JvmStatic
        fun createInstance(@Suppress("UNUSED_PARAMETER") context: DeckPicker): RecursiveMenuDialog {
            // this needs a deckPicker for now. See use of PICK_APKG_FILE
            val menuItems = arrayOf(
                RecursiveMenuItem(R.string.import_deck_package, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.IMPORT_APKG_FILE, 1, null, true, Importer(true)),
                RecursiveMenuItem(R.string.import_collection_package, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.IMPORT_COLPKG_FILE, 2, null, true, Importer())
            )
            @KotlinCleanup(
                "refactor this method to use its own dialog(maybe PickStringDialogFragment?) " +
                    "and let RecursiveMenuDialog to handle only the help/support menu dialogs)"
            )
            return createInstance(menuItems, R.string.menu_import)
        }

        // needs to be static for serialization
        @JvmStatic
        fun openImportFilePicker(activity: AnkiActivity, multiple: Boolean = false) {
            Timber.d("openImportFilePicker() delegating to file picker intent")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
            intent.putExtra("android.content.extra.FANCY", true)
            intent.putExtra("android.content.extra.SHOW_FILESIZE", true)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            activity.startActivityForResultWithoutAnimation(intent, DeckPicker.PICK_APKG_FILE)
        }
    }
}
