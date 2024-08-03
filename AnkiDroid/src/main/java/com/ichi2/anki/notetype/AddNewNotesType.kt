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

import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import anki.notetypes.StockNotetype
import anki.notetypes.copy
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
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton

class AddNewNotesType(private val activity: ManageNotetypes) {
    private lateinit var dialogView: View
    suspend fun showAddNewNotetypeDialog() {
        dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_new_note_type, null)
        val (allOptions, currentNames) = activity.withProgress {
            withCol {
                val standardNotetypesModels = StockNotetype.Kind.entries
                    .filter { it != StockNotetype.Kind.UNRECOGNIZED }
                    .map {
                        val stockNotetype = BackendUtils.fromJsonBytes(getStockNotetypeLegacy(it))
                        AddNotetypeUiModel(
                            id = it.number.toLong(),
                            name = stockNotetype.get("name") as String,
                            isStandard = true
                        )
                    }
                val foundNotetypes = getNotetypeNames()
                Pair(
                    mutableListOf<AddNotetypeUiModel>().apply {
                        addAll(standardNotetypesModels)
                        addAll(foundNotetypes.map { it.toUiModel() })
                    },
                    foundNotetypes.map { it.name }
                )
            }
        }
        val dialog = AlertDialog.Builder(activity).apply {
            customView(dialogView, paddingLeft = 32, paddingRight = 32, paddingTop = 64, paddingBottom = 64)
            positiveButton(R.string.dialog_ok) { _ ->
                val newName =
                    dialogView.findViewById<EditText>(R.id.notetype_new_name).text.toString()
                val selectedPosition =
                    dialogView.findViewById<Spinner>(R.id.notetype_new_type).selectedItemPosition
                if (selectedPosition == AdapterView.INVALID_POSITION) return@positiveButton
                val selectedOption = allOptions[selectedPosition]
                if (selectedOption.isStandard) {
                    addStandardNotetype(newName, selectedOption)
                } else {
                    cloneStandardNotetype(newName, selectedOption)
                }
            }
            negativeButton(R.string.dialog_cancel)
        }.show()
        dialog.initializeViewsWith(allOptions, currentNames)
    }

    private fun AlertDialog.initializeViewsWith(
        optionsToDisplay: List<AddNotetypeUiModel>,
        currentNames: List<String>
    ) {
        val addPrefixStr = context.resources.getString(R.string.model_browser_add_add)
        val clonePrefixStr = context.resources.getString(R.string.model_browser_add_clone)
        val nameInput = dialogView.findViewById<EditText>(R.id.notetype_new_name)
        nameInput.addTextChangedListener { editableText ->
            val currentName = editableText?.toString() ?: ""
            positiveButton.isEnabled =
                currentName.isNotEmpty() && !currentNames.contains(currentName)
        }
        dialogView.findViewById<Spinner>(R.id.notetype_new_type).apply {
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(av: AdapterView<*>?, rv: View?, index: Int, id: Long) {
                    val selectedNotetype = optionsToDisplay[index]
                    nameInput.setText(randomizeName(selectedNotetype.name))
                    nameInput.setSelection(nameInput.text.length)
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
            nameInput.requestFocus()
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }

    private fun addStandardNotetype(newName: String, selectedOption: AddNotetypeUiModel) {
        activity.launchCatchingTask {
            activity.runAndRefreshAfter {
                val kind = StockNotetype.Kind.forNumber(selectedOption.id.toInt())
                val updatedStandardNotetype =
                    BackendUtils.fromJsonBytes(getStockNotetypeLegacy(kind)).apply {
                        set("name", newName)
                    }
                addNotetypeLegacy(BackendUtils.toJsonBytes(updatedStandardNotetype))
            }
        }
    }

    private fun cloneStandardNotetype(newName: String, model: AddNotetypeUiModel) {
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
