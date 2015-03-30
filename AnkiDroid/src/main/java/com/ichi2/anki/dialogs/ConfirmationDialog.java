
package com.ichi2.anki.dialogs;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;

/**
 * This is a reusable convenience class which makes it easy to show a confirmation dialog as a DialogFragment.
 * To use, create a new instance which overrides the confirm() method, call setArgs(...), and then show
 * the DialogFragment via the fragment manager as usual.
 */
public abstract class ConfirmationDialog extends DialogFragment {

    public void setArgs(String message) {
        setArgs("" , message);
    }

    public void setArgs(String title, String message) {
        Bundle args = new Bundle();
        args.putString("message", message);
        args.putString("title", title);
        setArguments(args);
    }


    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getActivity().getResources();
        String title = getArguments().getString("title");
        return new MaterialDialog.Builder(getActivity())
                .title(title.equals("") ? res.getString(R.string.app_name) : title)
                .content(getArguments().getString("message"))
                .positiveText(res.getString(R.string.dialog_ok))
                .negativeText(res.getString(R.string.dialog_cancel))
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        confirm();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        cancel();
                    }
                })
                .show();
    }


    /**
     * Must override this method to handle confirmation event
     */
    public abstract void confirm();


    /**
     * Optionally override this method to do something special when operation cancelled
     */
    public void cancel() {
    }
}
