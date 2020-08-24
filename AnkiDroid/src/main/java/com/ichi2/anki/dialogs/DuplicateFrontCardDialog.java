package com.ichi2.anki.dialogs;

import android.content.Context;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;

public class DuplicateFrontCardDialog {
    public static MaterialDialog.Builder getDefault(Context context) {
        return new MaterialDialog.Builder(context)
                .content(R.string.note_editor_dialog_duplicate)
                .positiveText(R.string.dialog_positive_add)
                .negativeText(R.string.dialog_cancel);
    }
}
