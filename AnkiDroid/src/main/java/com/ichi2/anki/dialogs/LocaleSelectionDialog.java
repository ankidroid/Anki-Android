/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;
import com.ichi2.ui.RecyclerSingleTouchAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Locale selection dialog. Note: this must be dismissed onDestroy if not called from an activity implementing LocaleSelectionDialogHandler */
public class LocaleSelectionDialog extends AnalyticsDialogFragment {

    private LocaleSelectionDialogHandler mDialogHandler;

    public interface LocaleSelectionDialogHandler {
        void onSelectedLocale(@NonNull Locale selectedLocale);
        void onLocaleSelectionCancelled();
    }



    /**
     * @param handler Marker interface to enforce the convention the caller implementing LocaleSelectionDialogHandler
     */
    @SuppressWarnings("unused")
    @NonNull
    public static LocaleSelectionDialog newInstance(@NonNull LocaleSelectionDialogHandler handler) {
        LocaleSelectionDialog t = new LocaleSelectionDialog();
        t.mDialogHandler = handler;
        Bundle args = new Bundle();
        t.setArguments(args);

        return t;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(true);
    }


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (mDialogHandler == null) {
                if (!(context instanceof LocaleSelectionDialogHandler)) {
                    throw new IllegalArgumentException("Calling activity must implement LocaleSelectionDialogHandler");
                }
                this.mDialogHandler = (LocaleSelectionDialogHandler) context;
            }
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Activity activity = requireActivity();

        View tagsDialogView = LayoutInflater.from(activity)
                .inflate(R.layout.locale_selection_dialog, activity.findViewById(R.id.root_layout), false);


        LocaleListAdapter mAdapter = new LocaleListAdapter(Locale.getAvailableLocales());
        setupRecyclerView(activity, tagsDialogView, mAdapter);

        inflateMenu(tagsDialogView, mAdapter);
        //Only show a negative button, use the RecyclerView for positive actions
        MaterialDialog.Builder builder = new MaterialDialog.Builder(activity)
                .negativeText(getString(R.string.dialog_cancel))
                .customView(tagsDialogView, false)
                .onNegative((dialog, which) -> mDialogHandler.onLocaleSelectionCancelled());

        Dialog mDialog = builder.build();

        Window window = mDialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return mDialog;
    }


    private void setupRecyclerView(@NonNull Activity activity, @NonNull View tagsDialogView, LocaleListAdapter adapter) {
        RecyclerView recyclerView = tagsDialogView.findViewById(R.id.locale_dialog_selection_list);
        recyclerView.requestFocus();
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(activity);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);


        recyclerView.addOnItemTouchListener(new RecyclerSingleTouchAdapter(activity, (view, position) -> {
            Locale l = adapter.getLocaleAtPosition(position);
            LocaleSelectionDialog.this.mDialogHandler.onSelectedLocale(l);
        }));
    }


    private void inflateMenu(@NonNull View tagsDialogView, @NonNull final LocaleListAdapter adapter) {
        Toolbar mToolbar = tagsDialogView.findViewById(R.id.locale_dialog_selection_toolbar);
        mToolbar.setTitle(R.string.locale_selection_dialog_title);

        mToolbar.inflateMenu(R.menu.locale_dialog_search_bar);

        MenuItem searchItem = mToolbar.getMenu().findItem(R.id.locale_dialog_action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return false;
            }
        });
    }




    public static class LocaleListAdapter extends RecyclerView.Adapter<LocaleListAdapter.TextViewHolder> implements Filterable {
        private final List<Locale> mCurrentlyVisibleLocales;
        private final List<Locale> mSelectableLocales;

        public static class TextViewHolder extends RecyclerView.ViewHolder {
            @NonNull
            private final TextView mTextView;

            public TextViewHolder(@NonNull TextView textView) {
                super(textView);
                mTextView = textView;
            }


            public void setText(@NonNull String text) {
                mTextView.setText(text);
            }


            public void setLocale(@NonNull Locale locale) {
                String displayValue = locale.getDisplayName();
                mTextView.setText(displayValue);
            }
        }

        public LocaleListAdapter(@NonNull Locale[] locales) {
            mSelectableLocales = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(locales)));
            mCurrentlyVisibleLocales = new ArrayList<>(Arrays.asList(locales));
        }

        @NonNull
        @Override
        public TextViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                 int viewType) {
            TextView v = (TextView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.locale_dialog_fragment_textview, parent, false);

            return new TextViewHolder(v);
        }


        @Override
        public void onBindViewHolder(@NonNull TextViewHolder holder, int position) {
            holder.setLocale(mCurrentlyVisibleLocales.get(position));
        }


        @Override
        public int getItemCount() {
            return mCurrentlyVisibleLocales.size();
        }


        @NonNull
        public Locale getLocaleAtPosition(int position) {
            return mCurrentlyVisibleLocales.get(position);
        }

        @NonNull
        @Override
        public Filter getFilter() {
            final List<Locale> selectableLocales = mSelectableLocales;
            final List<Locale> visibleLocales = mCurrentlyVisibleLocales;

            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    if (TextUtils.isEmpty(constraint)) {
                        FilterResults filterResults = new FilterResults();
                        filterResults.values = selectableLocales;
                        return filterResults;
                    }

                    String normalisedConstraint = constraint.toString().toLowerCase(Locale.getDefault());
                    ArrayList<Locale> locales = new ArrayList<>(selectableLocales.size());
                    for (Locale l : selectableLocales) {
                        if (l.getDisplayName().toLowerCase(Locale.getDefault()).contains(normalisedConstraint)) {
                            locales.add(l);
                        }
                    }

                    FilterResults filterResults = new FilterResults();
                    filterResults.values = locales;
                    return filterResults;
                }


                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    visibleLocales.clear();
                    //noinspection unchecked
                    Collection<? extends Locale> values = (Collection<? extends Locale>) results.values;
                    visibleLocales.addAll(values);
                    notifyDataSetChanged();
                }
            };
        }
    }
}
