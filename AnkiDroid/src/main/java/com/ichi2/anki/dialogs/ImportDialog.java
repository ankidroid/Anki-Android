
package com.ichi2.anki.dialogs;

import android.content.res.Resources;
import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;
import com.ichi2.libanki.Utils;

import java.io.File;
import java.util.List;

public class ImportDialog extends AnalyticsDialogFragment {

    public static final int DIALOG_IMPORT_HINT = 0;
    public static final int DIALOG_IMPORT_SELECT = 1;
    public static final int DIALOG_IMPORT_ADD_CONFIRM = 2;
    public static final int DIALOG_IMPORT_REPLACE_CONFIRM = 3;

    public interface ImportDialogListener {
        void showImportDialog(int id, String message);
        void showImportDialog(int id);
        void importAdd(String importPath);
        void importReplace(String importPath);
        void dismissAllDialogFragments();
    }


    /**
     * A set of dialogs which deal with importing a file
     * 
     * @param dialogType An integer which specifies which of the sub-dialogs to show
     * @param dialogMessage An optional string which can be used to show a custom message
     * or specify import path
     */
    public static ImportDialog newInstance(int dialogType, String dialogMessage) {
        ImportDialog f = new ImportDialog();
        Bundle args = new Bundle();
        args.putInt("dialogType", dialogType);
        args.putString("dialogMessage", dialogMessage);
        f.setArguments(args);
        return f;
    }


    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int mType = getArguments().getInt("dialogType");
        Resources res = getResources();
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
        builder.cancelable(true);

        switch (mType) {
            case DIALOG_IMPORT_HINT: {
                // Instruct the user that they need to put their APKG files into the AnkiDroid directory
                return builder.title(res.getString(R.string.import_title))
                        .content(res.getString(R.string.import_hint, CollectionHelper.getCurrentAnkiDroidDirectory(getActivity())))
                        .positiveText(res.getString(R.string.dialog_ok))
                        .negativeText(res.getString(R.string.dialog_cancel))
                        .onPositive((dialog, which) -> ((ImportDialogListener) getActivity()).showImportDialog(DIALOG_IMPORT_SELECT))
                        .onNegative((dialog, which) -> dismissAllDialogFragments())
                        .show();
            }
            case DIALOG_IMPORT_SELECT: {
                // Allow user to choose from the list of available APKG files
                List<File> fileList = Utils.getImportableDecks(getActivity());
                if (fileList.size() == 0) {
                    UIUtils.showThemedToast(getActivity(),
                            getResources().getString(R.string.upgrade_import_no_file_found, "'.apkg'"), false);
                    return builder.showListener(dialog -> dialog.cancel()).show();
                } else {
                    String[] tts = new String[fileList.size()];
                    final String[] importValues = new String[fileList.size()];
                    for (int i = 0; i < tts.length; i++) {
                        tts[i] = fileList.get(i).getName().replace(".apkg", "");
                        importValues[i] = fileList.get(i).getAbsolutePath();
                    }
                    return builder.title(res.getString(R.string.import_select_title))
                            .items(tts)
                            .itemsCallback((materialDialog, view, i, charSequence) -> {
                                String importPath = importValues[i];
                                // If the apkg file is called "collection.apkg", we assume the collection will be replaced
                                if (filenameFromPath(importPath).equals("collection.apkg")) {
                                    ((ImportDialogListener) getActivity()).showImportDialog(DIALOG_IMPORT_REPLACE_CONFIRM, importPath);
                                    // Otherwise we add the file since exported decks / shared decks can't be imported via replace anyway
                                } else {
                                    ((ImportDialogListener) getActivity()).showImportDialog(DIALOG_IMPORT_ADD_CONFIRM, importPath);
                                }
                            })
                            .show();
                }
            }
            case DIALOG_IMPORT_ADD_CONFIRM: {
                return builder.title(res.getString(R.string.import_title))
                        .content(res.getString(R.string.import_message_add_confirm, filenameFromPath(getArguments().getString("dialogMessage"))))
                        .positiveText(res.getString(R.string.import_message_add))
                        .negativeText(res.getString(R.string.dialog_cancel))
                        .onPositive((dialog, which) -> {
                            ((ImportDialogListener) getActivity()).importAdd(getArguments().getString("dialogMessage"));
                            dismissAllDialogFragments();
                        })
                        .show();
            }
            case DIALOG_IMPORT_REPLACE_CONFIRM: {
                return builder.title(res.getString(R.string.import_title))
                        .content(res.getString(R.string.import_message_replace_confirm, getArguments().getString("dialogMessage")))
                        .positiveText(res.getString(R.string.dialog_positive_replace))
                        .negativeText(res.getString(R.string.dialog_cancel))
                        .onPositive((dialog, which) -> {
                            ((ImportDialogListener) getActivity()).importReplace(getArguments().getString("dialogMessage"));
                            dismissAllDialogFragments();
                        })
                        .show();
            }
            default:
                return null;
        }
    }
    
    public void dismissAllDialogFragments() {
        ((ImportDialogListener) getActivity()).dismissAllDialogFragments();        
    }

    private static String filenameFromPath (String path) {
        return path.split("/")[path.split("/").length - 1];
    }
}
