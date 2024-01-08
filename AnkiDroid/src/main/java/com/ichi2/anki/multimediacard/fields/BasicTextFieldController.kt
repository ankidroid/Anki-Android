/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.anki.multimediacard.fields

import android.content.Context
import android.content.DialogInterface
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.multimediacard.activity.PickStringDialogFragment
import com.ichi2.ui.FixedEditText

/**
 * One of the most powerful controllers - creates UI and works with the field of textual type.
 * <p>
 * Controllers work with the edit field activity and create UI on it to edit a field.
 */
class BasicTextFieldController : FieldControllerBase(), IFieldController, DialogInterface.OnClickListener {
    private lateinit var editText: EditText

    // This is used to copy from another field value to this field
    private lateinit var possibleClones: ArrayList<String>
    override fun createUI(context: Context, layout: LinearLayout) {
        editText = FixedEditText(_activity)
        editText.minLines = 3
        editText.setText(_field.text)
        layout.addView(editText, LinearLayout.LayoutParams.MATCH_PARENT)
        val layoutTools = LinearLayout(_activity)
        layoutTools.orientation = LinearLayout.HORIZONTAL
        layout.addView(layoutTools)
        val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1F)
        createCloneButton(layoutTools, p)
        createClearButton(layoutTools, p)
    }

    private fun gtxt(id: Int): String {
        return _activity.getText(id).toString()
    }

    private fun createClearButton(layoutTools: LinearLayout, p: LinearLayout.LayoutParams) {
        val clearButton = Button(_activity)
        clearButton.text = gtxt(R.string.multimedia_editor_text_field_editing_clear)
        layoutTools.addView(clearButton, p)
        clearButton.setOnClickListener { editText.setText("") }
    }

    /**
     * @param layoutTools This creates a button, which will call a dialog, allowing to pick from another note's fields
     *            one, and use it's value in the current one.
     * @param p layout params
     */
    private fun createCloneButton(layoutTools: LinearLayout, p: LinearLayout.LayoutParams) {
        // Makes sense only for two and more fields
        if (_note.numberOfFields > 1) {
            // Should be more than one text not empty fields for clone to make
            // sense
            possibleClones = ArrayList(_note.numberOfFields)
            var numTextFields = 0
            for (i in 0 until _note.numberOfFields) {
                // Sort out non text and empty fields
                val curField = _note.getField(i) ?: continue
                if (curField.type !== EFieldType.TEXT) {
                    continue
                }
                val currFieldText = curField.text ?: continue
                if (currFieldText.isEmpty() || currFieldText.contentEquals(_field.text)) {
                    continue
                }
                // collect clone sources
                possibleClones.add(currFieldText)
                numTextFields++
            }

            // Nothing to clone from
            if (numTextFields < 1) {
                return
            }
            val btnOtherField = Button(_activity)
            btnOtherField.text = gtxt(R.string.multimedia_editor_text_field_editing_clone)
            layoutTools.addView(btnOtherField, p)
            val controller = this
            btnOtherField.setOnClickListener {
                val fragment = PickStringDialogFragment()
                fragment.setChoices(possibleClones)
                fragment.setOnclickListener(controller)
                fragment.setTitle(gtxt(R.string.multimedia_editor_text_field_editing_clone_source))
                fragment.show(_activity.supportFragmentManager, "pick.clone")
            }
        }
    }

    override fun onFocusLost() {
        // do nothing
    }

    // When Done button is clicked
    override fun onDone() {
        _field.text = editText.text.toString()
    }

    // This is when the dialog for clone ends
    override fun onClick(dialog: DialogInterface, which: Int) {
        editText.setText(possibleClones[which])
    }

    /**
     * @param text A short cut to show a toast
     */
    private fun showToast(text: CharSequence) {
        showThemedToast(_activity, text, true)
    }

    override fun onDestroy() {
        // TODO Auto-generated method stub
    }
}
