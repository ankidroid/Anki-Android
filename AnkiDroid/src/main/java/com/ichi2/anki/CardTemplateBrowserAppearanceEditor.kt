/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.annotation.CheckResult
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.utils.JSONObject
import com.ichi2.utils.KotlinCleanup
import org.jetbrains.annotations.Contract
import timber.log.Timber

/** Allows specification of the Question and Answer format of a card template in the Card Browser
 * This is known as "Browser Appearance" in Anki
 * We do not allow the user to change fonts as Android only has a handful
 * We do not allow the user to change the font size as this can be done in the Appearance settings.
 */
class CardTemplateBrowserAppearanceEditor : AnkiActivity() {
    private lateinit var mQuestionEditText: EditText
    private lateinit var mAnswerEditText: EditText
    @KotlinCleanup("Use ?:")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        var bundle = savedInstanceState
        if (bundle == null) {
            bundle = intent.extras
        }
        if (bundle == null) {
            UIUtils.showThemedToast(this, getString(R.string.card_template_editor_card_browser_appearance_failed), true)
            finishActivityWithFade(this)
            return
        }
        initializeUiFromBundle(bundle)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.card_template_browser_appearance_editor, menu)
        return true
    }

    @KotlinCleanup("use when")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_confirm) {
            Timber.i("Save pressed")
            saveAndExit()
            return true
        } else if (item.itemId == R.id.action_restore_default) {
            Timber.i("Restore Default pressed")
            showRestoreDefaultDialog()
            return true
        } else if (item.itemId == android.R.id.home) {
            Timber.i("Back Pressed")
            closeWithDiscardWarning()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        Timber.i("Back Button Pressed")
        closeWithDiscardWarning()
    }

    private fun closeWithDiscardWarning() {
        if (hasChanges()) {
            Timber.i("Changes detected - displaying discard warning dialog")
            showDiscardChangesDialog()
        } else {
            discardChangesAndClose()
        }
    }

    private fun showDiscardChangesDialog() {
        DiscardChangesDialog
            .getDefault(this)
            .onPositive { _, _ -> discardChangesAndClose() }
            .show()
    }

    @KotlinCleanup("remove variable and call .show() directly on builder")
    private fun showRestoreDefaultDialog() {
        val builder: MaterialDialog.Builder = MaterialDialog.Builder(this)
            .positiveText(R.string.dialog_ok)
            .negativeText(R.string.dialog_cancel)
            .content(R.string.card_template_browser_appearance_restore_default_dialog)
            .onPositive { _, _ -> restoreDefaultAndClose() }
        builder.show()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(INTENT_QUESTION_FORMAT, questionFormat)
        outState.putString(INTENT_ANSWER_FORMAT, answerFormat)
        super.onSaveInstanceState(outState)
    }

    private fun initializeUiFromBundle(bundle: Bundle) {
        setContentView(R.layout.card_browser_appearance)

        mQuestionEditText = findViewById(R.id.question_format)
        mQuestionEditText.setText(bundle.getString(INTENT_QUESTION_FORMAT))

        mAnswerEditText = findViewById(R.id.answer_format)
        mAnswerEditText.setText(bundle.getString(INTENT_ANSWER_FORMAT))

        enableToolbar()
    }

    private fun answerHasChanged(intent: Intent): Boolean {
        return intent.getStringExtra(INTENT_ANSWER_FORMAT) != answerFormat
    }

    private fun questionHasChanged(intent: Intent): Boolean {
        return intent.getStringExtra(INTENT_QUESTION_FORMAT) != questionFormat
    }

    private val questionFormat: String
        get() = getTextValue(mQuestionEditText)
    private val answerFormat: String
        get() = getTextValue(mAnswerEditText)

    @KotlinCleanup("make editText non-null")
    private fun getTextValue(editText: EditText?): String {
        return editText!!.text.toString()
    }

    private fun restoreDefaultAndClose() {
        Timber.i("Restoring Default and Closing")
        mQuestionEditText.setText(VALUE_USE_DEFAULT)
        mAnswerEditText.setText(VALUE_USE_DEFAULT)
        saveAndExit()
    }

    private fun discardChangesAndClose() {
        Timber.i("Closing and discarding changes")
        setResult(RESULT_CANCELED)
        finishActivityWithFade(this)
    }

    private fun saveAndExit() {
        Timber.i("Save and Exit")
        val data = Intent()
        data.putExtra(INTENT_QUESTION_FORMAT, questionFormat)
        data.putExtra(INTENT_ANSWER_FORMAT, answerFormat)
        setResult(RESULT_OK, data)
        finishActivityWithFade(this)
    }

    @KotlinCleanup("remove redundant intent variable")
    fun hasChanges(): Boolean {
        return try {
            val intent = intent
            questionHasChanged(intent) || answerHasChanged(intent)
        } catch (e: Exception) {
            Timber.w(e, "Failed to detect changes. Assuming true")
            true
        }
    }

    class Result private constructor(question: String?, answer: String?) {
        val question: String = question ?: VALUE_USE_DEFAULT
        val answer: String = answer ?: VALUE_USE_DEFAULT

        fun applyTo(template: JSONObject) {
            template.put("bqfmt", question)
            template.put("bafmt", answer)
        }

        companion object {
            @JvmStatic
            @Contract("null -> null")
            fun fromIntent(intent: Intent?): Result? {
                return if (intent == null) {
                    null
                } else try {
                    val question = intent.getStringExtra(INTENT_QUESTION_FORMAT)
                    val answer = intent.getStringExtra(INTENT_ANSWER_FORMAT)
                    Result(question, answer)
                } catch (e: Exception) {
                    Timber.w(e, "Could not read result from intent")
                    null
                }
            }
        }
    }

    companion object {
        const val INTENT_QUESTION_FORMAT = "bqfmt"
        const val INTENT_ANSWER_FORMAT = "bafmt"

        /** Specified the card browser should use the default template formatter  */
        const val VALUE_USE_DEFAULT = ""
        @JvmStatic
        @CheckResult
        fun getIntentFromTemplate(context: Context, template: JSONObject): Intent {
            val browserQuestionTemplate = template.getString("bqfmt")
            val browserAnswerTemplate = template.getString("bafmt")
            return getIntent(context, browserQuestionTemplate, browserAnswerTemplate)
        }

        @CheckResult
        fun getIntent(context: Context, questionFormat: String, answerFormat: String): Intent {
            val intent = Intent(context, CardTemplateBrowserAppearanceEditor::class.java)
            intent.putExtra(INTENT_QUESTION_FORMAT, questionFormat)
            intent.putExtra(INTENT_ANSWER_FORMAT, answerFormat)
            return intent
        }
    }
}
