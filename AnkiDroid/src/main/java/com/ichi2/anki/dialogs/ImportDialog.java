
package com.ichi2.anki.dialogs;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.Themes;

import java.io.File;
import java.util.List;

public class ImportDialog extends DialogFragment {
    private int mType = 0;
    
    public static final int DIALOG_IMPORT_HINT = 0;
    public static final int DIALOG_IMPORT_SELECT = 1;
    public static final int DIALOG_IMPORT_ADD_CONFIRM = 2;
    public static final int DIALOG_IMPORT_REPLACE_CONFIRM = 3;

    public interface ImportDialogListener {
        public void showImportDialog(int id, String message);
        
        public void showImportDialog(int id);

        public void importAdd(String importPath);

        public void importReplace(String importPath);
        
        public void dismissAllDialogFragments();
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
    public StyledDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mType = getArguments().getInt("dialogType");
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());
        setCancelable(true);

        switch (mType) {
            case DIALOG_IMPORT_HINT:
                // Instruct the user that they need to put their APKG files into the AnkiDroid directory
                builder.setTitle(res.getString(R.string.import_title));
                builder.setMessage(res.getString(R.string.import_hint, CollectionHelper.getCurrentAnkiDroidDirectory(getActivity())));
                builder.setPositiveButton(res.getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((ImportDialogListener) getActivity()).showImportDialog(DIALOG_IMPORT_SELECT);
                    }
                });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), clearAllDialogsClickListener);
                return builder.create();

            case DIALOG_IMPORT_SELECT:
                // Allow user to choose from the list of available APKG files
                builder.setTitle(res.getString(R.string.import_select_title));
                StyledDialog dialog = builder.create();
                List<File> fileList = Utils.getImportableDecks(getActivity());
                if (fileList.size() == 0) {
                    Themes.showThemedToast(getActivity(),
                            getResources().getString(R.string.upgrade_import_no_file_found, "'.apkg'"), false);
                }
                dialog.setEnabled(fileList.size() != 0);
                // Make arrays for the filenames and the full absolute paths of all importable APKGs
                String[] tts = new String[fileList.size()];
                final String[] importValues = new String[fileList.size()];
                for (int i = 0; i < tts.length; i++) {
                    tts[i] = fileList.get(i).getName().replace(".apkg", "");
                    importValues[i] = fileList.get(i).getAbsolutePath();
                }
                dialog.setItems(tts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String importPath = importValues[which];
                        // If the apkg file is called "collection.apkg", we assume the collection will be replaced
                        if (filenameFromPath(importPath).equals("collection.apkg")) {
                            ((ImportDialogListener) getActivity()).showImportDialog(DIALOG_IMPORT_REPLACE_CONFIRM, importPath);
                            // Otherwise we add the file since exported decks / shared decks can't be imported via replace anyway
                        } else {
                            ((ImportDialogListener) getActivity()).showImportDialog(DIALOG_IMPORT_ADD_CONFIRM, importPath);
                        }
                    }
                });
                return dialog;
                
            case DIALOG_IMPORT_ADD_CONFIRM:
                builder.setTitle(res.getString(R.string.import_title));
                builder.setMessage(res.getString(R.string.import_message_add_confirm, filenameFromPath(getArguments().getString("dialogMessage"))));
                builder.setPositiveButton(res.getString(R.string.import_message_add),
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((ImportDialogListener) getActivity()).importAdd(getArguments().getString("dialogMessage"));
                                dismissAllDialogFragments();
                            }

                        });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                return builder.create();

            case DIALOG_IMPORT_REPLACE_CONFIRM:
                builder.setTitle(res.getString(R.string.import_title));
                builder.setMessage(res.getString(R.string.import_message_replace_confirm, getArguments().getString("dialogMessage")));
                builder.setPositiveButton(res.getString(R.string.dialog_positive_replace),
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((ImportDialogListener) getActivity()).importReplace(getArguments().getString("dialogMessage"));
                                dismissAllDialogFragments();
                            }

                        });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                return builder.create();                

            default:
                return null;
        }
    }
    
    // Listener for cancel button which clears ALL previous dialogs on the back stack
    // Supply null instead of this listener in cases where we prefer to go back to last dialog
    private DialogInterface.OnClickListener clearAllDialogsClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dismissAllDialogFragments();
        }
    };
    
    public void dismissAllDialogFragments() {
        ((ImportDialogListener) getActivity()).dismissAllDialogFragments();        
    }

    private static String filenameFromPath (String path) {
        return path.split("/")[path.split("/").length - 1];
    }
}
