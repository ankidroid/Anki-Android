package com.ichi2.anki.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class CardBrowserMySearchesDialog extends DialogFragment {

    public static int CARD_BROWSER_MY_SEARCHES_TYPE_LIST = 0; //list searches dialog
    public static int CARD_BROWSER_MY_SEARCHES_TYPE_SAVE = 1; //save searches dialog

    public interface MySearchesDialogListener {
        public void OnSelection(String searchName);
        public void OnRemoveSearch(String searchName);
        public void OnSaveSearch(String searchName, String searchTerms);
    }

    private static MySearchesDialogListener mMySearchesDialogListener;

    private MySearchesArrayAdapter mSearchesAdapter;
    private HashMap<String, String> mSavedFilters;
    private String mCurrentSearchTerms;

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
            mSearchesAdapter = new MySearchesArrayAdapter(activity, new ArrayList<>(mSavedFilters.keySet()));
            mSearchesAdapter.notifyDataSetChanged(); //so the values are sorted.
            builder.title(res.getString(R.string.card_browser_list_my_searches_title))
                    .adapter(mSearchesAdapter, new MaterialDialog.ListCallback() {
                        @Override
                        public void onSelection(MaterialDialog dialog, View itemView, int which, CharSequence text) {
                            mMySearchesDialogListener.OnSelection(mSearchesAdapter.getItem(which));
                            dialog.dismiss();
                        }
                    });
        } else if (type == CARD_BROWSER_MY_SEARCHES_TYPE_SAVE) {
            mCurrentSearchTerms = getArguments().getString("currentSearchTerms");
            builder.title(getString(R.string.card_browser_list_my_searches_save))
                   .positiveText(getString(android.R.string.ok))
                   .negativeText(getString(R.string.cancel))
                   .input(R.string.card_browser_list_my_searches_new_name, R.string.empty_string, new MaterialDialog.InputCallback() {
                       @Override
                       public void onInput(MaterialDialog dialog, CharSequence text) {
                           mMySearchesDialogListener.OnSaveSearch(text.toString(), mCurrentSearchTerms);
                       }
                   });
        }
        MaterialDialog dialog = builder.build();
        if (dialog.getListView() != null) {
            dialog.getListView().setDivider(new ColorDrawable(ContextCompat.getColor(activity, R.color.material_grey_600)));
            dialog.getListView().setDividerHeight(1);
            //adjust padding to use dp as seen here: http://stackoverflow.com/a/9685690/1332026
            float scale = res.getDisplayMetrics().density;
            int dpAsPixels = (int) (5*scale + 0.5f);
            dialog.getView().setPadding(dpAsPixels, 0, dpAsPixels, dpAsPixels);
        }

        return dialog;
    }

    private void removeSearch(final String searchName) {
        if (mSearchesAdapter.getPosition(searchName) >= 0) {
            Resources res = getResources();
            new MaterialDialog.Builder(getActivity())
                    .content(res.getString(R.string.card_browser_list_my_searches_remove_content, searchName))
                    .positiveText(res.getString(android.R.string.ok))
                    .negativeText(res.getString(R.string.cancel))
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            mMySearchesDialogListener.OnRemoveSearch(searchName);
                            mSavedFilters.remove(searchName);
                            mSearchesAdapter.remove(searchName);
                            mSearchesAdapter.notifyDataSetChanged();
                            dialog.dismiss();
                            if (mSavedFilters.size() == 0) {
                                getDialog().dismiss();
                            }
                        }
                    }).show();
        }
    }

    //using View Holder pattern for faster ListView scrolling.
    static class ViewHolder {
        private TextView mSearchName;
        private TextView mSearchTerms;
        private ImageButton mRemoveButton;
    }

    public class MySearchesArrayAdapter extends ArrayAdapter<String> {
        private Context mContext;
        private ArrayList<String> mSavedFiltersNames;

        public MySearchesArrayAdapter(Context context, ArrayList<String> savedFiltersNames) {
            super(context, R.layout.card_browser_item_my_searches_dialog, savedFiltersNames);
            mContext = context;
            mSavedFiltersNames = savedFiltersNames;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder = null;
            if (convertView == null) {
                viewHolder = new ViewHolder();
                LayoutInflater inflater = (LayoutInflater) mContext
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.card_browser_item_my_searches_dialog, parent, false);
                viewHolder.mSearchName = (TextView) convertView.findViewById(R.id.card_browser_my_search_name_textview);
                viewHolder.mSearchTerms = (TextView) convertView.findViewById(R.id.card_browser_my_search_terms_textview);
                viewHolder.mRemoveButton = (ImageButton) convertView.findViewById(R.id.card_browser_my_search_remove_button);

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            viewHolder.mSearchName.setText(getItem(position));
            viewHolder.mSearchTerms.setText(mSavedFilters.get(getItem(position)));
            viewHolder.mRemoveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    CardBrowserMySearchesDialog.this.removeSearch(getItem(position));
                }
            });
            return convertView;
        }

        @Override
        public void notifyDataSetChanged() {
            Collections.sort(mSavedFiltersNames, new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    return lhs.compareToIgnoreCase(rhs);
                }
            });
            super.notifyDataSetChanged();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }
    }
}
