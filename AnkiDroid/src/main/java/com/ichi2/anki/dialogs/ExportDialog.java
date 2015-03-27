
package com.ichi2.anki.dialogs;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;

public class ExportDialog extends DialogFragment {

    public interface ExportDialogListener {

        public void exportApkg(String path, Long did, boolean includeSched, boolean includeMedia);


        public void dismissAllDialogFragments();
    }

    private final int INCLUDE_SCHED = 0;
    private final int INCLUDE_MEDIA = 1;
    private boolean mIncludeSched = true;
    private boolean mIncludeMedia = false;


    /**
     * A set of dialogs which deal with importing a file
     * 
     * @param did An integer which specifies which of the sub-dialogs to show
     * @param dialogMessage An optional string which can be used to show a custom message or specify import path
     */
    public static ExportDialog newInstance(String dialogMessage, Long did) {
        ExportDialog f = new ExportDialog();
        Bundle args = new Bundle();
        args.putLong("did", did);
        args.putString("dialogMessage", dialogMessage);
        f.setArguments(args);
        return f;
    }


    public static ExportDialog newInstance(String dialogMessage) {
        ExportDialog f = new ExportDialog();
        Bundle args = new Bundle();
        args.putString("dialogMessage", dialogMessage);
        f.setArguments(args);
        return f;
    }


    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        final Long did = getArguments().getLong("did", -1L);
        mIncludeSched = did == -1L;
        final String[] items = { res.getString(R.string.export_include_schedule),
                res.getString(R.string.export_include_media) };
        final boolean[] checked = { mIncludeSched, mIncludeMedia };
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(res.getString(R.string.export))
                .content(getArguments().getString("dialogMessage"))
                .positiveText(res.getString(R.string.ok))
                .negativeText(res.getString(R.string.cancel))
                .cancelable(true)
                .items(items)
                .itemsCallbackMultiChoice(new Integer[] {INCLUDE_SCHED}, new MaterialDialog.ListCallbackMulti() {
                    @Override
                    public void onSelection(MaterialDialog materialDialog, Integer[] integers,
                            CharSequence[] charSequences) {
                        for (int i = 0; i < integers.length; i++) {
                            switch (integers[i]) {
                                case INCLUDE_SCHED:
                                    mIncludeSched = !mIncludeSched;
                                    break;
                                case INCLUDE_MEDIA:
                                    mIncludeMedia = !mIncludeMedia;
                                    break;
                            }
                        }
                    }
                })
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        ((ExportDialogListener) getActivity())
                                .exportApkg(null, did != -1L ? did : null, mIncludeSched,
                                        mIncludeMedia);
                        dismissAllDialogFragments();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        dismissAllDialogFragments();
                    }
                });
        return builder.show();
    }


    public void dismissAllDialogFragments() {
        ((ExportDialogListener) getActivity()).dismissAllDialogFragments();
    }

}
