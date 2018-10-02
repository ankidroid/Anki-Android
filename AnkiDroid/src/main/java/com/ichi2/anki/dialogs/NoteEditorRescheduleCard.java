
package com.ichi2.anki.dialogs;

import android.os.Bundle;
import android.text.InputType;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.NoteEditor;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

public class NoteEditorRescheduleCard extends AnalyticsDialogFragment {
    public static NoteEditorRescheduleCard newInstance() {
        return new NoteEditorRescheduleCard();
    }


    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        return new MaterialDialog.Builder(getActivity())
                .title(R.string.reschedule_card_dialog_title)
                .positiveText(getResources().getString(R.string.dialog_ok))
                .negativeText(R.string.cancel)
                .inputType(InputType.TYPE_CLASS_NUMBER)
                .inputRange(1, 4) // max 4 characters (i.e., 9999)
                .input(R.string.reschedule_card_dialog_message, R.string.empty_string, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(MaterialDialog dialog, CharSequence text) {
                        int days = Integer.parseInt(text.toString());
                        ((NoteEditor) getActivity()).onRescheduleCard(days);
                    }
                })
                .show();
    }
    }
