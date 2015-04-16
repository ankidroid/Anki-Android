
package com.ichi2.anki.dialogs;

import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

public class SimpleMessageDialog extends AsyncDialogFragment {

    public interface SimpleMessageDialogListener {
        public void dismissSimpleMessageDialog(boolean reload);
    }


    public static SimpleMessageDialog newInstance(String message, boolean reload) {
        return newInstance("" , message, reload);
    }


    public static SimpleMessageDialog newInstance(String title, String message, boolean reload) {
        SimpleMessageDialog f = new SimpleMessageDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("message", message);
        args.putBoolean("reload", reload);
        f.setArguments(args);
        return f;
    }


    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        return new MaterialDialog.Builder(getActivity())
                .title(getNotificationTitle())
                .content(getNotificationMessage())
                .positiveText(res().getString(R.string.dialog_ok))
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        ((SimpleMessageDialogListener) getActivity())
                                .dismissSimpleMessageDialog(getArguments().getBoolean(
                                        "reload"));
                    }
                })
                .show();
    }


    public String getNotificationTitle() {
        String title = getArguments().getString("title");
        if (!title.equals("")) {
            return title;
        } else {
            return AnkiDroidApp.getAppResources().getString(R.string.app_name);
        }
    }


    public String getNotificationMessage() {
        return getArguments().getString("message");
    }
}
