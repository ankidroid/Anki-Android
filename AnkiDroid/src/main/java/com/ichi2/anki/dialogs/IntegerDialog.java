
package com.ichi2.anki.dialogs;

import android.os.Bundle;
import android.text.InputType;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class IntegerDialog extends AnalyticsDialogFragment {

    private IntRunnable callbackRunnable;

    //TODO: Why isn't this a consumer accepting an int?
    public static abstract class IntRunnable implements Runnable {
        private int mInt;
        public void setInt(int intArg) {
            mInt = intArg;
        }
        public int getInt() {
            return mInt;
        }
        public abstract void run();
    }

    public void setCallbackRunnable(IntRunnable callbackRunnable) {
        this.callbackRunnable = callbackRunnable;
    }

    public void setArgs(String title, String prompt, int digits) {
        setArgs(title, prompt, digits, null);
    }

    public void setArgs(String title, String prompt, int digits, @Nullable String content) {
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("prompt", prompt);
        args.putInt("digits", digits);
        args.putString("content", content);
        setArguments(args);
    }

    @Override
    public @NonNull MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(getArguments().getString("title"))
                .positiveText(getResources().getString(R.string.dialog_ok))
                .negativeText(R.string.cancel)
                .inputType(InputType.TYPE_CLASS_NUMBER)
                .inputRange(1, getArguments().getInt("digits"))
                .input(getArguments().getString("prompt"), "", (dialog, text) -> {
                    callbackRunnable.setInt(Integer.parseInt(text.toString()));
                    callbackRunnable.run();
                });
        //content is marked as @NotNull
        //We can't use "" as that creates padding, and want to respect the contract, so only set if not null
        String content = getArguments().getString("content");
        if (content != null) {
            builder = builder.content(content);
        }
        return builder.show();
    }
    }
