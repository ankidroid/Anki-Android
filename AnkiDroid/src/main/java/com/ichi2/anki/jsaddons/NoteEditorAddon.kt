/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>                                       *
 *                                                                                      *
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

package com.ichi2.anki.jsaddons

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Editable
import com.evgenii.jsevaluator.JsEvaluator
import com.evgenii.jsevaluator.interfaces.JsCallback
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.ichi2.anki.*
import com.ichi2.anki.jsaddons.NpmUtils.NOTE_EDITOR_ADDON
import com.ichi2.anki.noteeditor.Toolbar
import com.ichi2.utils.JSONArray
import com.ichi2.utils.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.*

class NoteEditorAddon(private val activity: NoteEditor) {
    private val mContext: Context = activity.applicationContext
    private lateinit var mEditFields: LinkedList<FieldEditText>

    fun listEnabledAddonsFromDir(toolbar: Toolbar) {
        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(mContext)
        val addonsHomeDir = File(currentAnkiDroidDirectory, "addons")

        if (!addonsHomeDir.exists()) {
            addonsHomeDir.mkdirs()
        }

        val preferences = AnkiDroidApp.getSharedPrefs(mContext)
        val noteEditorEnabledAddonSet = preferences.getStringSet(NOTE_EDITOR_ADDON, HashSet())
        val jsEvaluator = JsEvaluator(mContext)

        for (enabledAddon in noteEditorEnabledAddonSet!!) {
            try {
                // AnkiDroid/addons/js-addons/package/index.js
                // here enabledAddon is id of npm package which may not contain ../ or other bad path
                val joinedPath = StringJoiner("/")
                    .add(currentAnkiDroidDirectory)
                    .add("addons")
                    .add(enabledAddon)
                    .add("package")
                    .toString()

                // user removed content from folder and prefs not updated then remove it
                val addonsPackageJson = File(joinedPath, "package.json")
                val addonsIndexJs = File(joinedPath, "index.js")
                val mapper = ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                val addonModel = mapper.readValue(addonsPackageJson, AddonModel::class.java)
                Timber.i("Addon::%s", addonModel.name)

                if (!addonsPackageJson.exists() || !addonsIndexJs.exists()) {
                    // skip mContext and list next addon
                    continue
                }

                val bmp: Drawable = toolbar.createDrawableForString(addonModel.icon)
                toolbar.insertItem(0, bmp, runJsCode(jsEvaluator, addonsIndexJs))
                // v.setOnLongClickListener { discard: View? -> true }
            } catch (e: IOException) {
                Timber.w(e)
            }
        }
    }

    fun runJsCode(jsEvaluator: JsEvaluator, indexJs: File?): Runnable {
        return Runnable {
            val content = StringBuilder()

            try {
                val br = BufferedReader(FileReader(indexJs))
                var line: String?

                while (br.readLine().also { line = it } != null) {
                    content.append(line)
                    content.append('\n')
                }
                br.close()

                val noteData: NoteEditor.NoteData = activity.noteData
                mEditFields = noteData.editFields

                val noteType: String = noteData.noteType
                val deckName: String = noteData.deckName
                val fieldsNameList: List<String?> = noteData.fieldsNameList
                val selectedText = getSelectedText()
                val fieldsCount = fieldsNameList.size

                val jsonObject = JSONObject()
                jsonObject.put("noteType", noteType)
                jsonObject.put("deckName", deckName)
                jsonObject.put("fieldsName", JSONArray(fieldsNameList))
                jsonObject.put("fieldsCount", fieldsCount)
                jsonObject.put("selectedText", selectedText)

                jsEvaluator.callFunction(
                    content.toString(),
                    object : JsCallback {
                        override fun onResult(result: String) {
                            jsAddonParseResult(result)
                        }

                        override fun onError(errorMessage: String) {
                            UIUtils.showThemedToast(mContext, "Error calling addon function\n$errorMessage", false)
                        }
                    },
                    "AnkiJSFunction", jsonObject.toString()
                ) // name in index.js in addon folder must be AnkiJSFunction
            } catch (e: IOException) {
                Timber.e(e.localizedMessage)
            }
        }
    }

    private fun jsAddonParseResult(result: String) {
        /*
         * parse result in json format
         *
         * { "changedText": "some changed text...",
         *   "addToFields": { "0": "some text to field one",
         *   "2": "some text to field two"
         *   "3": "some text to field three"
         *   ...
         *   ... },
         *   "changeOption": "replace"
         *  }
         *
         *  change option - replace, append, clear, default - with selected text
         **/

        val jsonObject = JSONObject(result)
        val changedText = jsonObject.optString("changedText", "")
        val changeType = jsonObject.optString("changeType", "")
        val addToFields = jsonObject.optJSONObject("addToFields")
        Timber.d("js addon: %s", jsonObject.toString())

        if (addToFields != null) {
            val size = addToFields.names()?.length()

            for (i in 0 until size!!) {
                val keyIndex = Objects.requireNonNull(addToFields.names()).getString(i)
                val value = addToFields.optString(Objects.requireNonNull(addToFields.names()).getString(i))
                Timber.d("js addon key::value : %s :: %s", keyIndex, value)
                val field: FieldEditText = mEditFields.get(keyIndex.toInt())
                field.setText(value)
            }
        }
        changeEditFieldWithSelectedText(changedText, changeType)
    }

    private fun getSelectedText(): String {
        var startSelection: Int
        var endSelection: Int
        var selectedText = ""

        for (e in mEditFields) {
            if (e.isFocused) {
                startSelection = e.selectionStart
                endSelection = e.selectionEnd
                selectedText = Objects.requireNonNull<Editable?>(e.text).toString().substring(startSelection, endSelection)
                break
            }
        }
        return selectedText
    }

    private fun changeEditFieldWithSelectedText(changedText: String, changeType: String) {
        var startSelection: Int
        var endSelection: Int
        var selectedText: String

        for (e in mEditFields) {
            if (e.isFocused) {
                startSelection = e.selectionStart
                endSelection = e.selectionEnd
                selectedText = Objects.requireNonNull<Editable?>(e.text).toString().substring(startSelection, endSelection)
                var editTextStr = e.text.toString()
                when (changeType) {
                    "replace" -> {
                        editTextStr = editTextStr.replace(selectedText, changedText)
                        e.setText(editTextStr)
                    }
                    "append" -> {
                        val appendText = selectedText + changedText
                        editTextStr = editTextStr.replace(selectedText, appendText)
                        e.setText(editTextStr)
                    }
                    "clear" -> {
                        editTextStr = editTextStr.replace(selectedText, "")
                        e.setText(editTextStr)
                    }
                    else -> {
                    }
                }
                break
            }
        }
    }
}
