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

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.multimediacard.activity.LoadPronunciationActivity
import com.ichi2.anki.multimediacard.activity.PickStringDialogFragment
import com.ichi2.ui.FixedEditText
import timber.log.Timber
import java.io.File

/**
 * One of the most powerful controllers - creates UI and works with the field of textual type.
 * <p>
 * Controllers work with the edit field activity and create UI on it to edit a field.
 */
class BasicTextFieldController : FieldControllerBase(), IFieldController, DialogInterface.OnClickListener {
    private lateinit var mEditText: EditText

    // This is used to copy from another field value to this field
    private lateinit var mPossibleClones: ArrayList<String>
    override fun createUI(context: Context, layout: LinearLayout) {
        mEditText = FixedEditText(mActivity)
        mEditText.minLines = 3
        mEditText.setText(mField.text)
        layout.addView(mEditText, LinearLayout.LayoutParams.MATCH_PARENT)
        val layoutTools = LinearLayout(mActivity)
        layoutTools.orientation = LinearLayout.HORIZONTAL
        layout.addView(layoutTools)
        val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1F)
        createCloneButton(layoutTools, p)
        createClearButton(layoutTools, p)
        // search buttons
        val layoutTools2 = LinearLayout(mActivity)
        layoutTools2.orientation = LinearLayout.HORIZONTAL
        layout.addView(layoutTools2)
        createPronounceButton(layoutTools2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun gtxt(id: Int): String {
        return mActivity.getText(id).toString()
    }

    private fun createClearButton(layoutTools: LinearLayout, p: LinearLayout.LayoutParams) {
        val clearButton = Button(mActivity)
        clearButton.text = gtxt(R.string.multimedia_editor_text_field_editing_clear)
        layoutTools.addView(clearButton, p)
        clearButton.setOnClickListener { mEditText.setText("") }
    }

    /**
     * @param layoutTools to create the button
     * @param p Button to load pronunciation from Beolingus
     */
    private fun createPronounceButton(layoutTools: LinearLayout, p: LinearLayout.LayoutParams) {
        val btnPronounce = Button(mActivity)
        btnPronounce.text = gtxt(R.string.multimedia_editor_text_field_editing_say)
        btnPronounce.setOnClickListener {
            val source = mEditText.text.toString()
            if (source.isEmpty()) {
                showToast(gtxt(R.string.multimedia_editor_text_field_editing_no_text))
                return@setOnClickListener
            }
            val intent = Intent(mActivity, LoadPronunciationActivity::class.java)
            intent.putExtra(LoadPronunciationActivity.EXTRA_SOURCE, source)
            mActivity.startActivityForResultWithoutAnimation(intent, REQUEST_CODE_PRONUNCIATION)
        }
        layoutTools.addView(btnPronounce, p)
    }

    /**
     * @param layoutTools This creates a button, which will call a dialog, allowing to pick from another note's fields
     *            one, and use it's value in the current one.
     * @param p layout params
     */
    private fun createCloneButton(layoutTools: LinearLayout, p: LinearLayout.LayoutParams) {
        // Makes sense only for two and more fields
        if (mNote.numberOfFields > 1) {
            // Should be more than one text not empty fields for clone to make
            // sense
            mPossibleClones = ArrayList(mNote.numberOfFields)
            var numTextFields = 0
            for (i in 0 until mNote.numberOfFields) {
                // Sort out non text and empty fields
                val curField = mNote.getField(i) ?: continue
                if (curField.type !== EFieldType.TEXT) {
                    continue
                }
                val currFieldText = curField.text ?: continue
                if (currFieldText.isEmpty() || currFieldText.contentEquals(mField.text)) {
                    continue
                }
                // collect clone sources
                mPossibleClones.add(currFieldText)
                numTextFields++
            }

            // Nothing to clone from
            if (numTextFields < 1) {
                return
            }
            val btnOtherField = Button(mActivity)
            btnOtherField.text = gtxt(R.string.multimedia_editor_text_field_editing_clone)
            layoutTools.addView(btnOtherField, p)
            val controller = this
            btnOtherField.setOnClickListener {
                val fragment = PickStringDialogFragment()
                fragment.setChoices(mPossibleClones)
                fragment.setOnclickListener(controller)
                fragment.setTitle(gtxt(R.string.multimedia_editor_text_field_editing_clone_source))
                fragment.show(mActivity.supportFragmentManager, "pick.clone")
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see com.ichi2.anki.IFieldController#onActivityResult(int, int, android.content.Intent) When activity started
     * from here returns, the MultimediaEditFieldActivity passes control here back. And the results from the started before
     * activity are received.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_PRONUNCIATION && resultCode == Activity.RESULT_OK) {
            try {
                val pronouncePath = data!!.extras!!.getString(LoadPronunciationActivity.EXTRA_PRONUNCIATION_FILE_PATH)!!
                val f = File(pronouncePath)
                if (!f.exists()) {
                    showToast(gtxt(R.string.multimedia_editor_pron_pronunciation_failed))
                }
                val af: AudioField = AudioRecordingField()
                af.audioPath = pronouncePath
                // This is done to delete the file later.
                af.hasTemporaryMedia = true
                mActivity.handleFieldChanged(af)
            } catch (e: Exception) {
                Timber.w(e)
                showToast(gtxt(R.string.multimedia_editor_pron_pronunciation_failed))
            }
        }
    }

    override fun onFocusLost() {
        // do nothing
    }

    // When Done button is clicked
    override fun onDone() {
        mField.text = mEditText.text.toString()
    }

    // This is when the dialog for clone ends
    override fun onClick(dialog: DialogInterface, which: Int) {
        mEditText.setText(mPossibleClones[which])
    }

    /**
     * @param text A short cut to show a toast
     */
    private fun showToast(text: CharSequence) {
        showThemedToast(mActivity, text, true)
    }

    override fun onDestroy() {
        // TODO Auto-generated method stub
    }

    companion object {
        // code to identify the request to fetch pronunciations
        private const val REQUEST_CODE_PRONUNCIATION = 102
    }
}
