/****************************************************************************************
 * Copyright (c) 2024 Neel Doshi <neeldoshi147@gmail.com>                               *
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

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.core.widget.addTextChangedListener
import anki.notetypes.StockNotetype
import anki.notetypes.copy
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.customview.customView
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.withProgress
import com.ichi2.libanki.Utils
import com.ichi2.libanki.addNotetype
import com.ichi2.libanki.addNotetypeLegacy
import com.ichi2.libanki.backend.BackendUtils
import com.ichi2.libanki.getNotetype
import com.ichi2.libanki.getNotetypeNames
import com.ichi2.libanki.getStockNotetypeLegacy
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.libanki.utils.set

class AddNewNotesType(private val activity: ManageNotetypes) {
    suspend fun showAddNewNotetypeDialog() {
        val optionsToDisplay = activity.withProgress {
            withCol {
                val standardNotetypesModels = StockNotetype.Kind.entries
                    .filter { it != StockNotetype.Kind.UNRECOGNIZED }
                    .map {
                        val stockNotetype = BackendUtils.from_json_bytes(getStockNotetypeLegacy(it))
                        NotetypeBasicUiModel(
                            id = it.number.toLong(),
                            name = stockNotetype.get("name") as String,
                            isStandard = true
                        )
                    }
                mutableListOf<NotetypeBasicUiModel>().apply {
                    addAll(standardNotetypesModels)
                    addAll(getNotetypeNames().map { it.toUiModel() })
                }
            }
        }
        val dialog = MaterialDialog(activity).show {
            customView(R.layout.dialog_new_note_type, horizontalPadding = true)
            positiveButton(R.string.dialog_ok) { dialog ->
                val newName =
                    dialog.view.findViewById<EditText>(R.id.notetype_new_name).text.toString()
                val selectedPosition =
                    dialog.view.findViewById<Spinner>(R.id.notetype_new_type).selectedItemPosition
                if (selectedPosition == AdapterView.INVALID_POSITION) return@positiveButton
                val selectedOption = optionsToDisplay[selectedPosition]
                if (selectedOption.isStandard) {
                    addStandardNotetype(newName, selectedOption)
                } else {
                    cloneStandardNotetype(newName, selectedOption)
                }
            }
            negativeButton(R.string.dialog_cancel)
        }
        dialog.initializeViewsWith(optionsToDisplay)
    }

    private fun MaterialDialog.initializeViewsWith(optionsToDisplay: List<NotetypeBasicUiModel>) {
        val addPrefixStr = context.resources.getString(R.string.model_browser_add_add)
        val clonePrefixStr = context.resources.getString(R.string.model_browser_add_clone)
        val nameInput = view.findViewById<EditText>(R.id.notetype_new_name)
        nameInput.addTextChangedListener { editableText ->
            val currentName = editableText?.toString() ?: ""
            getActionButton(WhichButton.POSITIVE).isEnabled =
                currentName.isNotEmpty() && !optionsToDisplay.map { it.name }.contains(currentName)
        }
        view.findViewById<Spinner>(R.id.notetype_new_type).apply {
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(av: AdapterView<*>?, rv: View?, index: Int, id: Long) {
                    val selectedNotetype = optionsToDisplay[index]
                    nameInput.setText(randomizeName(selectedNotetype.name))
                }

                override fun onNothingSelected(widget: AdapterView<*>?) {
                    nameInput.setText("")
                }
            }
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_list_item_1,
                android.R.id.text1,
                optionsToDisplay.map {
                    String.format(
                        if (it.isStandard) addPrefixStr else clonePrefixStr,
                        it.name
                    )
                }
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
    }

    private fun addStandardNotetype(newName: String, selectedOption: NotetypeBasicUiModel) {
        activity.launchCatchingTask {
            activity.runAndRefreshAfter {
                val kind = StockNotetype.Kind.forNumber(selectedOption.id.toInt())
                val updatedStandardNotetype =
                    BackendUtils.from_json_bytes(getStockNotetypeLegacy(kind)).apply {
                        set("name", newName)
                    }
                addNotetypeLegacy(BackendUtils.to_json_bytes(updatedStandardNotetype))
            }
        }
    }

    private fun cloneStandardNotetype(newName: String, model: NotetypeBasicUiModel) {
        activity.launchCatchingTask {
            activity.runAndRefreshAfter {
                val targetNotetype = getNotetype(model.id)
                val newNotetype = targetNotetype.copy {
                    id = 0
                    name = newName
                }
                addNotetype(newNotetype)
            }
        }
    }

    /**
     * Takes the current timestamp from [Collection] and appends it to the end of the new note
     * type to dissuade the user from reusing names(which are technically not unique however).
     */
    private fun randomizeName(currentName: String): String {
        return "$currentName-${Utils.checksum(TimeManager.time.intTimeMS().toString()).substring(0, 5)}"
    }
}
