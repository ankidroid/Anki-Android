
package com.ichi2.anki.dialogs;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ichi2.anki.R;
import com.ichi2.themes.StyledDialog;

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
    public StyledDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getActivity().getResources();
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
                confirm();
            }
        });
        // Set cancel action
        builder.setNegativeButton(res.getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                cancel();
            }
        });
        return builder.create();
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
