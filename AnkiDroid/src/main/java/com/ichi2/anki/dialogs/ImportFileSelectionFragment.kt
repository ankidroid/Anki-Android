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
import com.ichi2.anki.dialogs.HelpDialog.FunctionItem
import com.ichi2.annotations.NeedsTest
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

@NeedsTest("Selecting APKG allows multiple files")
@NeedsTest("Selecting COLPKG does not allow multiple files")
@NeedsTest("Restore backup dialog does not allow multiple files")
class ImportFileSelectionFragment {
    companion object {
        @JvmStatic
        @KotlinCleanup("convert importItems to java ArrayList")
        fun createInstance(@Suppress("UNUSED_PARAMETER") context: DeckPicker): RecursivePictureMenu {
            // this needs a deckPicker for now. See use of PICK_APKG_FILE

            // This is required for serialization of the lambda
            class OpenFilePicker(var multiple: Boolean = false) : FunctionItem.ActivityConsumer {
                override fun consume(activity: AnkiActivity) {
                    openImportFilePicker(activity, multiple)
                }
            }

            val importItems = arrayListOf<RecursivePictureMenu.Item>(
                FunctionItem(
                    R.string.import_deck_package,
                    R.drawable.ic_manual_black_24dp,
                    UsageAnalytics.Actions.IMPORT_APKG_FILE,
                    OpenFilePicker(true)
                ),
                FunctionItem(
                    R.string.import_collection_package,
                    R.drawable.ic_manual_black_24dp,
                    UsageAnalytics.Actions.IMPORT_COLPKG_FILE,
                    OpenFilePicker()
                ),
            )
            return RecursivePictureMenu.createInstance(ArrayList(importItems), R.string.menu_import)
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
