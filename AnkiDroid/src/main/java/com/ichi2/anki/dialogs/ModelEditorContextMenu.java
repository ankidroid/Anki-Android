package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

import androidx.annotation.RequiresApi;

public class ModelEditorContextMenu extends AnalyticsDialogFragment {

    public final static int FIELD_REPOSITION = 0;
    public final static int SORT_FIELD = 1;
    public final static int FIELD_RENAME = 2;
    public final static int FIELD_DELETE = 3;
    public final static int FIELD_TOGGLE_STICKY = 4;
    @RequiresApi(api = Build.VERSION_CODES.N)
    public final static int FIELD_ADD_LANGUAGE_HINT = 5;


    private static MaterialDialog.ListCallback mContextMenuListener;

    public static ModelEditorContextMenu newInstance(String label, MaterialDialog.ListCallback contextMenuListener) {
        ModelEditorContextMenu n = new ModelEditorContextMenu();
        mContextMenuListener = contextMenuListener;
        Bundle b = new Bundle();
        b.putString("label", label);
        mContextMenuListener = contextMenuListener;
        n.setArguments(b);
        return n;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String[] entries = new String[getEntryCount()];
        entries[FIELD_REPOSITION] = getResources().getString(R.string.model_field_editor_reposition_menu);
        entries[SORT_FIELD] = getResources().getString(R.string.model_field_editor_sort_field);
        entries[FIELD_RENAME] = getResources().getString(R.string.model_field_editor_rename);
        entries[FIELD_DELETE] = getResources().getString(R.string.model_field_editor_delete);
        entries[FIELD_TOGGLE_STICKY] = getResources().getString(R.string.model_field_editor_toggle_sticky);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            entries[FIELD_ADD_LANGUAGE_HINT] = getResources().getString(R.string.model_field_editor_language_hint);
        }

        return new MaterialDialog.Builder(getActivity())
                .title(getArguments().getString("label"))
                .items(entries)
                .itemsCallback(mContextMenuListener)
                .build();
    }


    private int getEntryCount() {
        int entryCount = 5;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            entryCount++;
        }
        return entryCount;
    }
}
