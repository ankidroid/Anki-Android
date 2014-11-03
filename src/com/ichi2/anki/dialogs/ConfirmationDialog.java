
package com.ichi2.anki.dialogs;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.themes.StyledDialog;

public class ConfirmationDialog extends DialogFragment {
    /**
     * Listener interface for confirmation and cancel
     */
    public interface ConfirmationDialogListener {
        public void confirm();
        public void cancel();
    }
    /**
     * Set default listeners to null
     */
    private ConfirmationDialogListener mConfirmListener;

    public static ConfirmationDialog newInstance(String message) {
        return newInstance("" , message);
    }

    public static ConfirmationDialog newInstance(String title, String message) {
        ConfirmationDialog f = new ConfirmationDialog();
        Bundle args = new Bundle();
        args.putString("message", message);
        args.putString("title", title);
        f.setArguments(args);
        return f;
    }


    @Override
    public StyledDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = AnkiDroidApp.getAppResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());
        // Set title if specified
        String title = getArguments().getString("title");
        if (!title.equals("")) {
            builder.setTitle(title);
        }
        // Set confirmation message
        builder.setMessage(getArguments().getString("message"));
        // Set confirmation action
        builder.setPositiveButton(res.getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mConfirmListener.confirm();
            }
        });
        // Set cancel action
        builder.setNegativeButton(res.getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mConfirmListener.cancel();
            }
        });
        return builder.create();
    }

    public void setListener(ConfirmationDialogListener listener) {
        mConfirmListener = listener;
    }
}
