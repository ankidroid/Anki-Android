package com.ichi2.anki.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;
import com.ichi2.ui.ButtonItemAdapter;

import java.util.ArrayList;
import java.util.HashMap;

import timber.log.Timber;

public class CardBrowserMySearchesDialog extends DialogFragment {

    public static int CARD_BROWSER_MY_SEARCHES_TYPE_LIST = 0; //list searches dialog
    public static int CARD_BROWSER_MY_SEARCHES_TYPE_SAVE = 1; //save searches dialog

    private static MySearchesDialogListener mMySearchesDialogListener;

    private ButtonItemAdapter mButtonItemAdapter;
    private HashMap<String, String> mSavedFilters;
    private ArrayList<String> mSavedFilterKeys;
    private String mCurrentSearchTerms;

    public interface MySearchesDialogListener {
        void onSelection(String searchName);
        void onRemoveSearch(String searchName);
        void onSaveSearch(String searchName, String searchTerms);
    }

    public static CardBrowserMySearchesDialog newInstance(HashMap<String, String> savedFilters,
                                                          MySearchesDialogListener mySearchesDialogListener,
                                                          String currentSearchTerms, int type) {
        mMySearchesDialogListener = mySearchesDialogListener;
        CardBrowserMySearchesDialog m = new CardBrowserMySearchesDialog();
        Bundle args = new Bundle();
        args.putSerializable("savedFilters", savedFilters);
        args.putInt("type", type);
        args.putString("currentSearchTerms", currentSearchTerms);
        m.setArguments(args);
        return m;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Resources res = getResources();

        Activity activity = getActivity();
        final MaterialDialog.Builder builder = new MaterialDialog.Builder(activity);

        int type = getArguments().getInt("type");
        if (type == CARD_BROWSER_MY_SEARCHES_TYPE_LIST) {
            mSavedFilters = (HashMap<String, String>) getArguments().getSerializable("savedFilters");
            mSavedFilterKeys = new ArrayList<>(mSavedFilters.keySet());

            mButtonItemAdapter = new ButtonItemAdapter(mSavedFilterKeys);
            mButtonItemAdapter.notifyAdapterDataSetChanged(); //so the values are sorted.
            mButtonItemAdapter.setCallbacks(
                    searchName -> {
                        Timber.d("item clicked: %s", searchName);
                        mMySearchesDialogListener.onSelection(searchName);
                        getDialog().dismiss();
                    },
                    searchName -> {
                        Timber.d("button clicked: %s", searchName);
                        removeSearch(searchName);
                    });

            builder.title(res.getString(R.string.card_browser_list_my_searches_title))
                    .adapter(mButtonItemAdapter, null);
        } else if (type == CARD_BROWSER_MY_SEARCHES_TYPE_SAVE) {
            mCurrentSearchTerms = getArguments().getString("currentSearchTerms");
            builder.title(getString(R.string.card_browser_list_my_searches_save))
                   .positiveText(getString(android.R.string.ok))
                   .negativeText(getString(R.string.cancel))
                   .input(R.string.card_browser_list_my_searches_new_name, R.string.empty_string, (dialog, text) -> {
                       Timber.d("Saving search with title/terms: %s/%s", text, mCurrentSearchTerms);
                       mMySearchesDialogListener.onSaveSearch(text.toString(), mCurrentSearchTerms);
                   });
        }
        MaterialDialog dialog = builder.build();
        if (dialog.getRecyclerView() != null) {
            LinearLayoutManager mLayoutManager = (LinearLayoutManager)dialog.getRecyclerView().getLayoutManager();
            DividerItemDecoration dividerItemDecoration =
                    new DividerItemDecoration(dialog.getRecyclerView().getContext(), mLayoutManager.getOrientation());
            float scale = res.getDisplayMetrics().density;
            int dpAsPixels = (int) (5*scale + 0.5f);
            dialog.getView().setPadding(dpAsPixels, 0, dpAsPixels, dpAsPixels);
            dialog.getRecyclerView().addItemDecoration(dividerItemDecoration);
        }

        return dialog;
    }

    private void removeSearch(String searchName) {

        Resources res = getResources();
        new MaterialDialog.Builder(getActivity())
                .content(res.getString(R.string.card_browser_list_my_searches_remove_content, searchName))
                .positiveText(res.getString(android.R.string.ok))
                .negativeText(res.getString(R.string.cancel))
                .onPositive((dialog, which) -> {
                    mMySearchesDialogListener.onRemoveSearch(searchName);
                    mSavedFilters.remove(searchName);
                    mSavedFilterKeys.remove(searchName);
                    mButtonItemAdapter.remove(searchName);
                    mButtonItemAdapter.notifyAdapterDataSetChanged();
                    dialog.dismiss();
                    if (mSavedFilters.size() == 0) {
                        getDialog().dismiss();
                    }
                }).show();
    }
}
