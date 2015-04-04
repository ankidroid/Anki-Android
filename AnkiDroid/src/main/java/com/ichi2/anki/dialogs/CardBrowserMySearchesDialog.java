package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class CardBrowserMySearchesDialog extends DialogFragment {

    private static MaterialDialog.ListCallback mMySearchesDialogListener;

    public static CardBrowserMySearchesDialog newInstance(ArrayList<String> savedFilters,
                                                          MaterialDialog.ListCallback mySearchesDialogListener) {
        mMySearchesDialogListener = mySearchesDialogListener;
        CardBrowserMySearchesDialog m = new CardBrowserMySearchesDialog();
        Bundle args = new Bundle();
        args.putStringArrayList("savedFilters", savedFilters);
        m.setArguments(args);
        return m;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();

        ArrayList<String> savedFilters = getArguments().getStringArrayList("savedFilters");

        Collections.sort(savedFilters, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                return lhs.compareToIgnoreCase(rhs);
            }
        });

        CharSequence[] items = new CharSequence[savedFilters.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = savedFilters.get(i);
        }

        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
        if (items.length > 0) {
            builder.title(res.getString(R.string.card_browser_list_my_searches_title))
                    .items(items)
                    .itemsCallback(mMySearchesDialogListener);
        } else {
            builder.title(res.getString(R.string.card_browser_list_my_searches_empty_title))
                    .content(res.getString(R.string.card_browser_list_my_searches_empty))
                    .positiveText(res.getString(R.string.ok));
        }
        return builder.build();
    }
}
