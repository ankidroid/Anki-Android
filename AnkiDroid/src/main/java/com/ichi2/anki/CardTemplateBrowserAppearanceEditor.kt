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

package com.ichi2.anki;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.dialogs.DiscardChangesDialog;
import com.ichi2.utils.JSONObject;

import org.jetbrains.annotations.Contract;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

/** Allows specification of the Question and Answer format of a card template in the Card Browser
 * This is known as "Browser Appearance" in Anki
 * We do not allow the user to change fonts as Android only has a handful
 * We do not allow the user to change the font size as this can be done in the Appearance settings.
 */
public class CardTemplateBrowserAppearanceEditor extends AnkiActivity {

    public static final String INTENT_QUESTION_FORMAT = "bqfmt";
    public static final String INTENT_ANSWER_FORMAT = "bafmt";

    /** Specified the card browser should use the default template formatter */
    public static final String VALUE_USE_DEFAULT = "";

    private EditText mQuestionEditText;
    private EditText mAnswerEditText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        super.onCreate(savedInstanceState);
        Bundle bundle = savedInstanceState;
        if (bundle == null) {
            bundle = getIntent().getExtras();
        }
        if (bundle == null) {
            UIUtils.showThemedToast(this, getString(R.string.card_template_editor_card_browser_appearance_failed), true);
            finishActivityWithFade(this);
            return;
        }
        initializeUiFromBundle(bundle);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.card_template_browser_appearance_editor, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_confirm) {
            Timber.i("Save pressed");
            saveAndExit();
            return true;
        } else if (item.getItemId() == R.id.action_restore_default) {
            Timber.i("Restore Default pressed");
            showRestoreDefaultDialog();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            Timber.i("Back Pressed");
            closeWithDiscardWarning();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onBackPressed() {
        Timber.i("Back Button Pressed");
        closeWithDiscardWarning();
    }


    private void closeWithDiscardWarning() {
        if (hasChanges()) {
            Timber.i("Changes detected - displaying discard warning dialog");
            showDiscardChangesDialog();
        } else {
            discardChangesAndClose();
        }
    }


    private void showDiscardChangesDialog() {
        DiscardChangesDialog
                .getDefault(this)
                .onPositive((dialog, which) -> discardChangesAndClose())
                .show();
    }


    private void showRestoreDefaultDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .positiveText(R.string.dialog_ok)
                .negativeText(R.string.dialog_cancel)
                .content(R.string.card_template_browser_appearance_restore_default_dialog)
                .onPositive((dialog, which) -> restoreDefaultAndClose());
        builder.show();
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(INTENT_QUESTION_FORMAT, getQuestionFormat());
        outState.putString(INTENT_ANSWER_FORMAT, getAnswerFormat());
        super.onSaveInstanceState(outState);
    }

    private void initializeUiFromBundle(@NonNull Bundle bundle) {
        setContentView(R.layout.card_browser_appearance);

        mQuestionEditText = findViewById(R.id.question_format);
        mQuestionEditText.setText(bundle.getString(INTENT_QUESTION_FORMAT));

        mAnswerEditText = findViewById(R.id.answer_format);
        mAnswerEditText.setText(bundle.getString(INTENT_ANSWER_FORMAT));

        enableToolbar();
    }

    private boolean answerHasChanged(Intent intent) {
        return !intent.getStringExtra(INTENT_ANSWER_FORMAT).equals(getAnswerFormat());
    }

    private boolean questionHasChanged(Intent intent) {
        return !intent.getStringExtra(INTENT_QUESTION_FORMAT).equals(getQuestionFormat());
    }

    private String getQuestionFormat() {
        return getTextValue(mQuestionEditText);
    }

    private String getAnswerFormat() {
        return getTextValue(mAnswerEditText);
    }

    private String getTextValue(EditText editText) {
        return editText.getText().toString();
    }

    private void restoreDefaultAndClose() {
        Timber.i("Restoring Default and Closing");
        mQuestionEditText.setText(VALUE_USE_DEFAULT);
        mAnswerEditText.setText(VALUE_USE_DEFAULT);
        saveAndExit();
    }

    private void discardChangesAndClose() {
        Timber.i("Closing and discarding changes");
        setResult(RESULT_CANCELED);
        finishActivityWithFade(this);
    }

    private void saveAndExit() {
        Timber.i("Save and Exit");
        Intent data = new Intent();
        data.putExtra(INTENT_QUESTION_FORMAT, getQuestionFormat());
        data.putExtra(INTENT_ANSWER_FORMAT, getAnswerFormat());
        setResult(RESULT_OK, data);
        finishActivityWithFade(this);
    }

    public boolean hasChanges() {
        try {
            Intent intent = getIntent();
            return questionHasChanged(intent) || answerHasChanged(intent);
        } catch (Exception e) {
            Timber.w(e, "Failed to detect changes. Assuming true");
            return true;
        }
    }

    @NonNull @CheckResult
    public static Intent getIntentFromTemplate(@NonNull Context context, @NonNull JSONObject template) {
        String browserQuestionTemplate = template.getString("bqfmt");
        String browserAnswerTemplate = template.getString("bafmt");
        return CardTemplateBrowserAppearanceEditor.getIntent(context, browserQuestionTemplate, browserAnswerTemplate);
    }

    @NonNull @CheckResult
    public static Intent getIntent(@NonNull Context context, @NonNull String questionFormat, @NonNull String answerFormat) {
        Intent intent = new Intent(context, CardTemplateBrowserAppearanceEditor.class);
        intent.putExtra(INTENT_QUESTION_FORMAT, questionFormat);
        intent.putExtra(INTENT_ANSWER_FORMAT, answerFormat);
        return intent;
    }

    public static class Result {
        @NonNull
        private final String mQuestion;
        @NonNull
        private final String mAnswer;

        private Result(String question, String answer) {
            this.mQuestion = question == null ? VALUE_USE_DEFAULT : question;
            this.mAnswer = answer == null ? VALUE_USE_DEFAULT : answer;
        }


        @Nullable
        @Contract("null -> null")
        @SuppressWarnings("WeakerAccess")
        public static Result fromIntent(@Nullable Intent intent) {
            if (intent == null) {
                return null;
            }
            try {
                String question = intent.getStringExtra(INTENT_QUESTION_FORMAT);
                String answer = intent.getStringExtra(INTENT_ANSWER_FORMAT);
                return new Result(question, answer);
            } catch (Exception e) {
                Timber.w(e, "Could not read result from intent");
                return null;
            }
        }

        @NonNull
        public String getQuestion() {
            return mQuestion;
        }

        @NonNull
        public String getAnswer() {
            return mAnswer;
        }


        @SuppressWarnings("WeakerAccess")
        public void applyTo(@NonNull JSONObject template) {
            template.put("bqfmt", getQuestion());
            template.put("bafmt", getAnswer());
        }
    }
}
