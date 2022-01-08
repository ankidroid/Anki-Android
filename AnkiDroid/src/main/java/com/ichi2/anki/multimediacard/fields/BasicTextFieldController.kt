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
import android.content.pm.PackageManager
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.multimediacard.activity.LoadPronounciationActivity
import com.ichi2.anki.multimediacard.activity.PickStringDialogFragment
import com.ichi2.anki.multimediacard.activity.TranslationActivity
import com.ichi2.compat.CompatHelper
import com.ichi2.ui.FixedEditText
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.io.File
import java.util.*

/**
 * One of the most powerful controllers - creates UI and works with the field of textual type.
 * <p>
 * Controllers work with the edit field activity and create UI on it to edit a field.
 */
@KotlinCleanup("lateinit")
class BasicTextFieldController : FieldControllerBase(), IFieldController, DialogInterface.OnClickListener {
    private var mEditText: EditText? = null

    // This is used to copy from another field value to this field
    private var mPossibleClones: ArrayList<String>? = null
    override fun createUI(context: Context?, layout: LinearLayout?) {
        mEditText = FixedEditText(mActivity)
        mEditText!!.minLines = 3
        mEditText!!.setText(mField.text)
        layout!!.addView(mEditText, LinearLayout.LayoutParams.MATCH_PARENT)
        val layoutTools = LinearLayout(mActivity)
        layoutTools.orientation = LinearLayout.HORIZONTAL
        layout.addView(layoutTools)
        val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1F)
        createCloneButton(layoutTools, p)
        createClearButton(layoutTools, p)
        // search label
        val searchLabel: TextView = FixedTextView(mActivity)
        searchLabel.setText(R.string.multimedia_editor_text_field_editing_search_label)
        layout.addView(searchLabel)
        // search buttons
        val layoutTools2 = LinearLayout(mActivity)
        layoutTools2.orientation = LinearLayout.HORIZONTAL
        layout.addView(layoutTools2)
        createTranslateButton(layoutTools2, p)
        createPronounceButton(layoutTools2, p)
    }

    private fun gtxt(id: Int): String {
        return mActivity.getText(id).toString()
    }

    private fun createClearButton(layoutTools: LinearLayout, p: LinearLayout.LayoutParams) {
        val clearButton = Button(mActivity)
        clearButton.text = gtxt(R.string.multimedia_editor_text_field_editing_clear)
        layoutTools.addView(clearButton, p)
        clearButton.setOnClickListener { mEditText!!.setText("") }
    }

    /**
     * @param layoutTools to create the button
     * @param p Button to load pronunciation from Beolingus
     */
    private fun createPronounceButton(layoutTools: LinearLayout, p: LinearLayout.LayoutParams) {
        val btnPronounce = Button(mActivity)
        btnPronounce.text = gtxt(R.string.multimedia_editor_text_field_editing_say)
        btnPronounce.setOnClickListener {
            val source = mEditText!!.text.toString()
            if (source.isEmpty()) {
                showToast(gtxt(R.string.multimedia_editor_text_field_editing_no_text))
                return@setOnClickListener
            }
            val intent = Intent(mActivity, LoadPronounciationActivity::class.java)
            intent.putExtra(LoadPronounciationActivity.EXTRA_SOURCE, source)
            mActivity.startActivityForResultWithoutAnimation(intent, REQUEST_CODE_PRONOUNCIATION)
        }
        layoutTools.addView(btnPronounce, p)
    }

    // Here is all the functionality to provide translations
    private fun createTranslateButton(layoutTool: LinearLayout, ps: LinearLayout.LayoutParams) {
        val btnTranslate = Button(mActivity)
        btnTranslate.text = gtxt(R.string.multimedia_editor_text_field_editing_translate)
        btnTranslate.setOnClickListener {
            val source = mEditText!!.text.toString()

            // Checks and warnings
            if (source.isEmpty()) {
                showToast(gtxt(R.string.multimedia_editor_text_field_editing_no_text))
                return@setOnClickListener
            }
            if (source.contains(" ")) {
                showToast(gtxt(R.string.multimedia_editor_text_field_editing_many_words))
            }

            // Pick from two translation sources
            val fragment = PickStringDialogFragment()
            val translationSources = ArrayList<String>(2)
            translationSources.add("Glosbe.com")
            // Chromebooks do not support dependent apps yet.
            if (!CompatHelper.isChromebook()) {
                translationSources.add("ColorDict")
            }
            fragment.setChoices(translationSources)
            fragment.setOnclickListener { _: DialogInterface?, which: Int ->
                val translationSource = translationSources[which]
                if ("Glosbe.com" == translationSource) {
                    startTranslationWithGlosbe()
                } else if ("ColorDict" == translationSource) {
                    startTranslationWithColorDict()
                }
            }
            fragment.setTitle(gtxt(R.string.multimedia_editor_trans_pick_translation_source))
            fragment.show(mActivity.supportFragmentManager, "pick.translation.source")
        }
        layoutTool.addView(btnTranslate, ps)

        // flow continues in Start Translation with...
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
                if (curField.text == null) {
                    continue
                }
                if (curField.text.isNullOrEmpty()) {
                    continue
                }

                // as well as the same field
                if (curField.text?.contentEquals(mField.text) == true) {
                    continue
                }

                // collect clone sources
                mPossibleClones!!.add(curField.text)
                ++numTextFields
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
        if (requestCode == REQUEST_CODE_TRANSLATE_GLOSBE && resultCode == Activity.RESULT_OK) {
            // Translation returned.
            try {
                val translation = data!!.extras!![TranslationActivity.EXTRA_TRANSLATION].toString()
                mEditText!!.setText(translation)
            } catch (e: Exception) {
                Timber.w(e)
                showToast(gtxt(R.string.multimedia_editor_something_wrong))
            }
        } else if (requestCode == REQUEST_CODE_PRONOUNCIATION && resultCode == Activity.RESULT_OK) {
            try {
                val pronuncPath = data!!.extras!![LoadPronounciationActivity.EXTRA_PRONUNCIATION_FILE_PATH]
                    .toString()
                val f = File(pronuncPath)
                if (!f.exists()) {
                    showToast(gtxt(R.string.multimedia_editor_pron_pronunciation_failed))
                }
                val af: AudioField = AudioRecordingField()
                af.audioPath = pronuncPath
                // This is done to delete the file later.
                af.setHasTemporaryMedia(true)
                mActivity.handleFieldChanged(af)
            } catch (e: Exception) {
                Timber.w(e)
                showToast(gtxt(R.string.multimedia_editor_pron_pronunciation_failed))
            }
        } else if (requestCode == REQUEST_CODE_TRANSLATE_COLORDICT && resultCode == Activity.RESULT_OK) {
            // String subject = data.getStringExtra(Intent.EXTRA_SUBJECT);
            val text = data!!.getStringExtra(Intent.EXTRA_TEXT)
            mEditText!!.setText(text)
        }
    }

    override fun onFocusLost() {
        // do nothing
    }

    // When Done button is clicked
    override fun onDone() {
        mField.text = mEditText!!.text.toString()
    }

    // This is when the dialog for clone ends
    override fun onClick(dialog: DialogInterface, which: Int) {
        mEditText!!.setText(mPossibleClones!![which])
    }

    /**
     * @param text A short cut to show a toast
     */
    private fun showToast(text: CharSequence) {
        showThemedToast(mActivity, text, true)
    }

    // Only now not all APIs are used, may be later, they will be.
    @Suppress("Unused_Variable")
    private fun startTranslationWithColorDict() {
        val PICK_RESULT_ACTION = "colordict.intent.action.PICK_RESULT"
        val SEARCH_ACTION = "colordict.intent.action.SEARCH"
        val EXTRA_QUERY = "EXTRA_QUERY"
        val EXTRA_FULLSCREEN = "EXTRA_FULLSCREEN"
        val EXTRA_HEIGHT = "EXTRA_HEIGHT"
        val EXTRA_WIDTH = "EXTRA_WIDTH"
        val EXTRA_GRAVITY = "EXTRA_GRAVITY"
        val EXTRA_MARGIN_LEFT = "EXTRA_MARGIN_LEFT"
        val EXTRA_MARGIN_TOP = "EXTRA_MARGIN_TOP"
        val EXTRA_MARGIN_BOTTOM = "EXTRA_MARGIN_BOTTOM"
        val EXTRA_MARGIN_RIGHT = "EXTRA_MARGIN_RIGHT"
        val intent = Intent(PICK_RESULT_ACTION)
        intent.putExtra(EXTRA_QUERY, mEditText!!.text.toString()) // Search
        // Query
        intent.putExtra(EXTRA_FULLSCREEN, false) //
        // intent.putExtra(EXTRA_HEIGHT, 400); //400pixel, if you don't specify,
        // fill_parent"
        intent.putExtra(EXTRA_GRAVITY, Gravity.BOTTOM)
        // intent.putExtra(EXTRA_MARGIN_LEFT, 100);
        if (!isIntentAvailable(mActivity, intent)) {
            showToast(gtxt(R.string.multimedia_editor_trans_install_color_dict))
            return
        }
        mActivity.startActivityForResultWithoutAnimation(intent, REQUEST_CODE_TRANSLATE_COLORDICT)
    }

    private fun startTranslationWithGlosbe() {
        val source = mEditText!!.text.toString()
        val intent = Intent(mActivity, TranslationActivity::class.java)
        intent.putExtra(TranslationActivity.EXTRA_SOURCE, source)
        mActivity.startActivityForResultWithoutAnimation(intent, REQUEST_CODE_TRANSLATE_GLOSBE)
    }

    override fun onDestroy() {
        // TODO Auto-generated method stub
    }

    companion object {
        // Additional activities are started to perform translation/pronunciation search and
        // so on, here are their request codes, to differentiate, when they return.
        private const val REQUEST_CODE_TRANSLATE_GLOSBE = 101
        private const val REQUEST_CODE_PRONOUNCIATION = 102
        private const val REQUEST_CODE_TRANSLATE_COLORDICT = 103

        /**
         * @param context context with the PackageManager
         * @param intent intent for state data
         * @return Needed to check, if the Color Dict is installed
         */
        private fun isIntentAvailable(context: Context, intent: Intent): Boolean {
            val packageManager = context.packageManager
            val list: List<*> = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            return list.isNotEmpty()
        }
    }
}
