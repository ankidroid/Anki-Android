
package com.ichi2.anki.dialogs;

import android.os.Bundle;
import android.text.InputType;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.NoteEditor;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

public class NoteEditorRepositionCard extends AnalyticsDialogFragment {
    public static NoteEditorRepositionCard newInstance() {
        return new NoteEditorRepositionCard();
    }


    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        return new MaterialDialog.Builder(getActivity())
                .title(R.string.reposition_card_dialog_title)
                .positiveText(getResources().getString(R.string.dialog_ok))
                .negativeText(R.string.cancel)
                .inputType(InputType.TYPE_CLASS_NUMBER)
                .inputRange(1, 5) // max 5 characters (i.e., 99999)
                .input(R.string.reposition_card_dialog_message, R.string.empty_string, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(MaterialDialog dialog, CharSequence text) {
                        int position = Integer.parseInt(text.toString());
                        ((NoteEditor) getActivity()).onRepositionCard(position);

                    }
                })
                .show();
    }
    }
