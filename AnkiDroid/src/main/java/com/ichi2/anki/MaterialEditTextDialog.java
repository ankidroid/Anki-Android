package com.ichi2.anki;

import android.content.Context;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.utils.AndroidUiUtils;

import java.util.Objects;

import androidx.annotation.NonNull;

public class MaterialEditTextDialog extends MaterialDialog {
    protected MaterialEditTextDialog(Builder builder) {
        super(builder);
    }

    public static class Builder extends MaterialDialog.Builder {

        public EditText editText;

        public Builder(@NonNull Context context, EditText editText) {
            super(context);
            this.editText = editText;
        }

        @Override
        public MaterialDialog show() {
            MaterialDialog materialDialog = super.show();
            displayKeyboard(editText, materialDialog);

            return materialDialog;
        }

        /**
         * Open keyboard when dialog shows.
         */
        public void displayKeyboard(EditText editText, MaterialDialog materialDialog) {
            AndroidUiUtils.setFocusAndOpenKeyboard(editText, Objects.requireNonNull(materialDialog.getWindow()));
        }
    }
}
