
package com.ichi2.anki.dialogs;

import android.content.DialogInterface;
import android.os.Bundle;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.themes.StyledDialog;

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
    public StyledDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());
        String title = getArguments().getString("title");
        if (!title.equals("")) {
            builder.setTitle(title);
        }
        builder.setMessage(getArguments().getString("message"));
        builder.setPositiveButton(res().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((SimpleMessageDialogListener) getActivity()).dismissSimpleMessageDialog(getArguments().getBoolean(
                        "reload"));
            }
        });
        return builder.create();
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
