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

package com.ichi2.anki.multimediacard.fields;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.multimediacard.activity.LoadPronounciationActivity;
import com.ichi2.anki.multimediacard.activity.PickStringDialogFragment;
import com.ichi2.anki.multimediacard.activity.TranslationActivity;
import com.ichi2.compat.CompatHelper;
import com.ichi2.ui.FixedEditText;
import com.ichi2.ui.FixedTextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * One of the most powerful controllers - creates UI and works with the field of textual type.
 * <p>
 * Controllers work with the edit field activity and create UI on it to edit a field.
 */
public class BasicTextFieldController extends FieldControllerBase implements IFieldController,
        DialogInterface.OnClickListener {

    // Additional activities are started to perform translation/pronunciation search and
    // so on, here are their request codes, to differentiate, when they return.
    private static final int REQUEST_CODE_TRANSLATE_GLOSBE = 101;
    private static final int REQUEST_CODE_PRONOUNCIATION = 102;
    private static final int REQUEST_CODE_TRANSLATE_COLORDICT = 103;

    private EditText mEditText;

    // This is used to copy from another field value to this field
    private ArrayList<String> mPossibleClones;


    @Override
    public void createUI(Context context, LinearLayout layout) {
        mEditText = new FixedEditText(mActivity);
        mEditText.setMinLines(3);
        mEditText.setText(mField.getText());
        layout.addView(mEditText, LayoutParams.MATCH_PARENT);

        LinearLayout layoutTools = new LinearLayout(mActivity);
        layoutTools.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(layoutTools);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);

        createCloneButton(layoutTools, p);
        createClearButton(layoutTools, p);
        // search label
        TextView searchLabel = new FixedTextView(mActivity);
        searchLabel.setText(R.string.multimedia_editor_text_field_editing_search_label);
        layout.addView(searchLabel);
        // search buttons
        LinearLayout layoutTools2 = new LinearLayout(mActivity);
        layoutTools2.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(layoutTools2);
        createTranslateButton(layoutTools2, p);
        createPronounceButton(layoutTools2, p);
    }


    private String gtxt(int id) {
        return mActivity.getText(id).toString();
    }



    private void createClearButton(LinearLayout layoutTools, LayoutParams p) {
        Button clearButton = new Button(mActivity);
        clearButton.setText(gtxt(R.string.multimedia_editor_text_field_editing_clear));
        layoutTools.addView(clearButton, p);

        clearButton.setOnClickListener(v -> mEditText.setText(""));
    }


    /**
     * @param layoutTools to create the button
     * @param p Button to load pronunciation from Beolingus
     */
    private void createPronounceButton(LinearLayout layoutTools, LayoutParams p) {
        Button btnPronounce = new Button(mActivity);
        btnPronounce.setText(gtxt(R.string.multimedia_editor_text_field_editing_say));
        btnPronounce.setOnClickListener(v -> {
            String source = mEditText.getText().toString();

            if (source.length() == 0) {
                showToast(gtxt(R.string.multimedia_editor_text_field_editing_no_text));
                return;
            }

            Intent intent = new Intent(mActivity, LoadPronounciationActivity.class);
            intent.putExtra(LoadPronounciationActivity.EXTRA_SOURCE, source);
            mActivity.startActivityForResultWithoutAnimation(intent, REQUEST_CODE_PRONOUNCIATION);
        });

        layoutTools.addView(btnPronounce, p);
    }


    // Here is all the functionality to provide translations
    private void createTranslateButton(LinearLayout layoutTool, LayoutParams ps) {
        Button btnTranslate = new Button(mActivity);
        btnTranslate.setText(gtxt(R.string.multimedia_editor_text_field_editing_translate));
        btnTranslate.setOnClickListener(v -> {
            String source = mEditText.getText().toString();

            // Checks and warnings
            if (source.length() == 0) {
                showToast(gtxt(R.string.multimedia_editor_text_field_editing_no_text));
                return;
            }

            if (source.contains(" ")) {
                showToast(gtxt(R.string.multimedia_editor_text_field_editing_many_words));
            }

            // Pick from two translation sources
            PickStringDialogFragment fragment = new PickStringDialogFragment();

            final ArrayList<String> translationSources = new ArrayList<>(2);
            translationSources.add("Glosbe.com");
            // Chromebooks do not support dependent apps yet.
            if (!CompatHelper.isChromebook()) {
                translationSources.add("ColorDict");
            }

            fragment.setChoices(translationSources);
            fragment.setOnclickListener((dialog, which) -> {
                String translationSource = translationSources.get(which);
                if ("Glosbe.com".equals(translationSource)) {
                    startTranslationWithGlosbe();
                } else if ("ColorDict".equals(translationSource)) {
                    startTranslationWithColorDict();
                }
            });

            fragment.setTitle(gtxt(R.string.multimedia_editor_trans_pick_translation_source));

            fragment.show(mActivity.getSupportFragmentManager(), "pick.translation.source");
        });

        layoutTool.addView(btnTranslate, ps);

        // flow continues in Start Translation with...

    }


    /**
     * @param layoutTools This creates a button, which will call a dialog, allowing to pick from another note's fields
     *            one, and use it's value in the current one.
     * @param p layout params
     */
    private void createCloneButton(LinearLayout layoutTools, LayoutParams p) {
        // Makes sense only for two and more fields
        if (mNote.getNumberOfFields() > 1) {
            // Should be more than one text not empty fields for clone to make
            // sense

            mPossibleClones = new ArrayList<>(mNote.getNumberOfFields());

            int numTextFields = 0;
            for (int i = 0; i < mNote.getNumberOfFields(); ++i) {
                // Sort out non text and empty fields
                IField curField = mNote.getField(i);
                if (curField == null) {
                    continue;
                }

                if (curField.getType() != EFieldType.TEXT) {
                    continue;
                }

                if (curField.getText() == null) {
                    continue;
                }

                if (curField.getText().length() == 0) {
                    continue;
                }

                // as well as the same field
                if (curField.getText().contentEquals(mField.getText())) {
                    continue;
                }

                // collect clone sources
                mPossibleClones.add(curField.getText());
                ++numTextFields;
            }

            // Nothing to clone from
            if (numTextFields < 1) {
                return;
            }

            Button btnOtherField = new Button(mActivity);
            btnOtherField.setText(gtxt(R.string.multimedia_editor_text_field_editing_clone));
            layoutTools.addView(btnOtherField, p);

            final BasicTextFieldController controller = this;

            btnOtherField.setOnClickListener(v -> {
                PickStringDialogFragment fragment = new PickStringDialogFragment();

                fragment.setChoices(mPossibleClones);
                fragment.setOnclickListener(controller);
                fragment.setTitle(gtxt(R.string.multimedia_editor_text_field_editing_clone_source));

                fragment.show(mActivity.getSupportFragmentManager(), "pick.clone");

                // flow continues in the onClick function

            });

        }

    }


    /*
     * (non-Javadoc)
     * @see com.ichi2.anki.IFieldController#onActivityResult(int, int, android.content.Intent) When activity started
     * from here returns, the MultimediaEditFieldActivity passes control here back. And the results from the started before
     * activity are received.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_TRANSLATE_GLOSBE && resultCode == Activity.RESULT_OK) {
            // Translation returned.
            try {
                String translation = data.getExtras().get(TranslationActivity.EXTRA_TRANSLATION).toString();
                mEditText.setText(translation);
            } catch (Exception e) {
                Timber.w(e);
                showToast(gtxt(R.string.multimedia_editor_something_wrong));
            }
        } else if (requestCode == REQUEST_CODE_PRONOUNCIATION && resultCode == Activity.RESULT_OK) {
            try {
                String pronuncPath = data.getExtras().get(LoadPronounciationActivity.EXTRA_PRONUNCIATION_FILE_PATH)
                        .toString();
                File f = new File(pronuncPath);
                if (!f.exists()) {
                    showToast(gtxt(R.string.multimedia_editor_pron_pronunciation_failed));
                }

                AudioField af = new AudioRecordingField();
                af.setAudioPath(pronuncPath);
                // This is done to delete the file later.
                af.setHasTemporaryMedia(true);
                mActivity.handleFieldChanged(af);
            } catch (Exception e) {
                Timber.w(e);
                showToast(gtxt(R.string.multimedia_editor_pron_pronunciation_failed));
            }
        } else if (requestCode == REQUEST_CODE_TRANSLATE_COLORDICT && resultCode == Activity.RESULT_OK) {
            // String subject = data.getStringExtra(Intent.EXTRA_SUBJECT);
            String text = data.getStringExtra(Intent.EXTRA_TEXT);

            mEditText.setText(text);

        }
    }

    @Override
    public void onFocusLost() {
        // do nothing
    }


    /**
     * @param context context with the PackageManager
     * @param intent intent for state data
     * @return Needed to check, if the Color Dict is installed
     */
    private static boolean isIntentAvailable(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<?> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return !list.isEmpty();
    }


    // When Done button is clicked
    @Override
    public void onDone() {
        mField.setText(mEditText.getText().toString());
    }


    // This is when the dialog for clone ends
    @Override
    public void onClick(DialogInterface dialog, int which) {
        mEditText.setText(mPossibleClones.get(which));
    }


    /**
     * @param text A short cut to show a toast
     */
    private void showToast(CharSequence text) {
        UIUtils.showThemedToast(mActivity, text, true);
    }


    // Only now not all APIs are used, may be later, they will be.
    @SuppressWarnings("unused")
    private void startTranslationWithColorDict() {
        final String PICK_RESULT_ACTION = "colordict.intent.action.PICK_RESULT";
        final String SEARCH_ACTION = "colordict.intent.action.SEARCH";
        final String EXTRA_QUERY = "EXTRA_QUERY";
        final String EXTRA_FULLSCREEN = "EXTRA_FULLSCREEN";
        final String EXTRA_HEIGHT = "EXTRA_HEIGHT";
        final String EXTRA_WIDTH = "EXTRA_WIDTH";
        final String EXTRA_GRAVITY = "EXTRA_GRAVITY";
        final String EXTRA_MARGIN_LEFT = "EXTRA_MARGIN_LEFT";
        final String EXTRA_MARGIN_TOP = "EXTRA_MARGIN_TOP";
        final String EXTRA_MARGIN_BOTTOM = "EXTRA_MARGIN_BOTTOM";
        final String EXTRA_MARGIN_RIGHT = "EXTRA_MARGIN_RIGHT";

        Intent intent = new Intent(PICK_RESULT_ACTION);
        intent.putExtra(EXTRA_QUERY, mEditText.getText().toString()); // Search
                                                                      // Query
        intent.putExtra(EXTRA_FULLSCREEN, false); //
        // intent.putExtra(EXTRA_HEIGHT, 400); //400pixel, if you don't specify,
        // fill_parent"
        intent.putExtra(EXTRA_GRAVITY, Gravity.BOTTOM);
        // intent.putExtra(EXTRA_MARGIN_LEFT, 100);
        if (!isIntentAvailable(mActivity, intent)) {
            showToast(gtxt(R.string.multimedia_editor_trans_install_color_dict));
            return;
        }
        mActivity.startActivityForResultWithoutAnimation(intent, REQUEST_CODE_TRANSLATE_COLORDICT);
    }


    private void startTranslationWithGlosbe() {
        String source = mEditText.getText().toString();

        Intent intent = new Intent(mActivity, TranslationActivity.class);
        intent.putExtra(TranslationActivity.EXTRA_SOURCE, source);
        mActivity.startActivityForResultWithoutAnimation(intent, REQUEST_CODE_TRANSLATE_GLOSBE);
    }


    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub

    }
}
