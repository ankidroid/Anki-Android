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

import android.app.Dialog;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;
import com.ichi2.anki.exception.FilteredAncestor;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Deck;
import com.ichi2.utils.FunctionalInterfaces;
import com.ichi2.utils.FilterResultsUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import timber.log.Timber;

public class DeckSelectionDialog extends AnalyticsDialogFragment {

    private MaterialDialog mDialog;


    /**
     * A dialog which handles selecting a deck
     */
    @NonNull
    public static DeckSelectionDialog newInstance(@NonNull String title, @Nullable String summaryMessage, @NonNull boolean keepRestoreDefaultButton, @NonNull List<SelectableDeck> decks) {
        DeckSelectionDialog f = new DeckSelectionDialog();
        Bundle args = new Bundle();
        args.putString("summaryMessage", summaryMessage);
        args.putString("title", title);
        args.putBoolean("keepRestoreDefaultButton", keepRestoreDefaultButton);
        args.putParcelableArrayList("deckNames", new ArrayList<>(decks));
        f.setArguments(args);
        return f;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(true);
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View dialogView = LayoutInflater.from(getActivity())
                .inflate(R.layout.deck_picker_dialog, null, false);


        TextView summary = dialogView.findViewById(R.id.deck_picker_dialog_summary);

        Bundle arguments = requireArguments();

        if (getSummaryMessage(arguments) == null) {
            summary.setVisibility(View.GONE);
        } else {
            summary.setVisibility(View.VISIBLE);
            summary.setText(getSummaryMessage(arguments));
        }

        RecyclerView recyclerView = dialogView.findViewById(R.id.deck_picker_dialog_list);
        recyclerView.requestFocus();

        RecyclerView.LayoutManager deckLayoutManager = new LinearLayoutManager(requireActivity());
        recyclerView.setLayoutManager(deckLayoutManager);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dividerItemDecoration);

        List<SelectableDeck> decks = getDeckNames(arguments);
        DecksArrayAdapter adapter = new DecksArrayAdapter(decks);
        recyclerView.setAdapter(adapter);

        adjustToolbar(dialogView, adapter);

        MaterialDialog.Builder builder = new MaterialDialog.Builder(requireActivity())
                .neutralText(R.string.dialog_cancel)
                .customView(dialogView, false);

        if (arguments.getBoolean("keepRestoreDefaultButton")) {
            builder = builder.negativeText(R.string.restore_default).onNegative((dialog, which) -> onDeckSelected(null));
        }

        mDialog = builder.build();
        return mDialog;
    }


    @Nullable
    private String getSummaryMessage(Bundle arguments) {
        return arguments.getString("summaryMessage");
    }


    @NonNull
    private ArrayList<SelectableDeck> getDeckNames(Bundle arguments) {
        return Objects.requireNonNull(arguments.getParcelableArrayList("deckNames"));
    }


    @NonNull
    private String getTitle() {
        return Objects.requireNonNull(requireArguments().getString("title"));
    }


    private void adjustToolbar(View dialogView, DecksArrayAdapter adapter) {
        Toolbar mToolbar = dialogView.findViewById(R.id.deck_picker_dialog_toolbar);

        mToolbar.setTitle(getTitle());

        mToolbar.inflateMenu(R.menu.deck_picker_dialog_menu);

        MenuItem searchItem = mToolbar.getMenu().findItem(R.id.deck_picker_dialog_action_filter);
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setQueryHint(getString(R.string.deck_picker_dialog_filter_decks));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return true;
            }
        });

        MenuItem addDecks = mToolbar.getMenu().findItem(R.id.deck_picker_dialog_action_add_deck);
        addDecks.setOnMenuItemClickListener(menuItem -> {
            // creating new deck without any parent deck
            showDeckDialog();
            return true;
        });
    }

    private void showSubDeckDialog(String parentDeckPath) {
        try {
            // create subdeck
            Long parentId = requireAnkiActivity().getCol().getDecks().id(parentDeckPath);
            CreateDeckDialog createDeckDialog = new CreateDeckDialog(requireActivity(), R.string.create_subdeck, CreateDeckDialog.DeckDialogType.SUB_DECK, parentId);
            createDeckDialog.setOnNewDeckCreated((id) -> {
                // a sub deck was created
                selectDeckWithDeckName(requireAnkiActivity().getCol().getDecks().name(id));
            });
            createDeckDialog.showDialog();
        } catch (FilteredAncestor filteredAncestor) {
            Timber.w(filteredAncestor);
        }
    }

    private void showDeckDialog() {
        CreateDeckDialog createDeckDialog =  new CreateDeckDialog(requireActivity(), R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null);
        createDeckDialog.setOnNewDeckCreated((id) -> {
            // a deck was created
            selectDeckWithDeckName(requireAnkiActivity().getCol().getDecks().name(id));
        });
        createDeckDialog.showDialog();
    }

    @NonNull
    protected AnkiActivity requireAnkiActivity() {
        return (AnkiActivity) requireActivity();
    }

    private void selectDeckWithDeckName(@NonNull String deckName) {
        try {
            Long id = requireAnkiActivity().getCol().getDecks().id(deckName);
            SelectableDeck dec = new SelectableDeck(id, deckName);
            selectDeckAndClose(dec);
        } catch (FilteredAncestor filteredAncestor) {
            UIUtils.showThemedToast(requireActivity(), getString(R.string.decks_rename_filtered_nosubdecks), false);
        }
    }


    protected void onDeckSelected(@Nullable SelectableDeck deck) {
        ((DeckSelectionListener) requireActivity()).onDeckSelected(deck);
    }

    protected void selectDeckAndClose(@NonNull SelectableDeck deck) {
        onDeckSelected(deck);
        mDialog.dismiss();
    }

    protected void displayErrorAndCancel() {
        mDialog.dismiss();
    }


    public class DecksArrayAdapter extends RecyclerView.Adapter<DecksArrayAdapter.ViewHolder> implements Filterable {
        public class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView mDeckTextView;
            public ViewHolder(@NonNull TextView ctv) {
                super(ctv);
                mDeckTextView = ctv;
                mDeckTextView.setOnClickListener(view -> {
                    String deckName = ctv.getText().toString();
                    selectDeckByNameAndClose(deckName);
                });

                mDeckTextView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        // creating sub deck with parent deck path
                        showSubDeckDialog(ctv.getText().toString());
                        return true;
                    }
                });
            }


            public void setDeck(@NonNull SelectableDeck deck) {
                mDeckTextView.setText(deck.getName());
            }
        }

        private final ArrayList<SelectableDeck> mAllDecksList = new ArrayList<>();
        private final ArrayList<SelectableDeck> mCurrentlyDisplayedDecks = new ArrayList<>();

        public DecksArrayAdapter(@NonNull List<SelectableDeck> deckNames) {
            mAllDecksList.addAll(deckNames);
            mCurrentlyDisplayedDecks.addAll(deckNames);
            Collections.sort(mCurrentlyDisplayedDecks);
        }

        protected void selectDeckByNameAndClose(@NonNull String deckName) {
            for (SelectableDeck d : mAllDecksList) {
                if (d.getName().equals(deckName)) {
                    selectDeckAndClose(d);
                    return;
                }
            }
            displayErrorAndCancel();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.deck_picker_dialog_list_item, parent, false);

            return new ViewHolder(v.findViewById(R.id.deck_picker_dialog_list_item_value));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SelectableDeck deck = mCurrentlyDisplayedDecks.get(position);
            holder.setDeck(deck);
        }

        @Override
        public int getItemCount() {
            return mCurrentlyDisplayedDecks.size();
        }


        @NonNull
        @Override
        public Filter getFilter() {
            return new DecksFilter();
        }

        /* Custom Filter class - as seen in http://stackoverflow.com/a/29792313/1332026 */
        private class DecksFilter extends Filter {
            private final ArrayList<SelectableDeck> mFilteredDecks;
            protected DecksFilter() {
                super();
                mFilteredDecks = new ArrayList<>();
            }

            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                mFilteredDecks.clear();
                ArrayList<SelectableDeck> allDecks = DecksArrayAdapter.this.mAllDecksList;
                if (constraint.length() == 0) {
                    mFilteredDecks.addAll(allDecks);
                } else {
                    final String filterPattern = constraint.toString().toLowerCase(Locale.getDefault()).trim();
                    for (SelectableDeck deck : allDecks) {
                        if (deck.getName().toLowerCase(Locale.getDefault()).contains(filterPattern)) {
                            mFilteredDecks.add(deck);
                        }
                    }
                }

                return FilterResultsUtils.fromCollection(mFilteredDecks);
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                ArrayList<SelectableDeck> currentlyDisplayedDecks = DecksArrayAdapter.this.mCurrentlyDisplayedDecks;
                currentlyDisplayedDecks.clear();
                currentlyDisplayedDecks.addAll(mFilteredDecks);
                Collections.sort(currentlyDisplayedDecks);
                notifyDataSetChanged();
            }
        }
    }


    public static class SelectableDeck implements Comparable<SelectableDeck>, Parcelable {
        private final long mDeckId;
        private final String mName;

        @NonNull
        public static List<SelectableDeck> fromCollection(@NonNull Collection c, @NonNull FunctionalInterfaces.Filter<Deck> filter) {
            List<Deck> all = c.getDecks().all();
            List<SelectableDeck> ret = new ArrayList<>(all.size());
            for (Deck d : all) {
                if (!filter.shouldInclude(d)) {
                    continue;
                }
                ret.add( new SelectableDeck(d));
            }
            return ret;
        }

        @SuppressWarnings("unused")
        @NonNull
        public static List<SelectableDeck> fromCollection(@NonNull Collection c) {
            return fromCollection(c, FunctionalInterfaces.Filters.allowAll());
        }


        public SelectableDeck(long deckId, @NonNull String name) {
            this.mDeckId = deckId;
            this.mName = name;
        }


        protected SelectableDeck(@NonNull Deck d) {
            this(d.getLong("id"), d.getString("name"));
        }


        protected SelectableDeck(@NonNull Parcel in) {
            mDeckId = in.readLong();
            mName = in.readString();
        }


        public long getDeckId() {
            return mDeckId;
        }


        @NonNull
        public String getName() {
            return mName;
        }


        @Override
        public int compareTo(@NonNull SelectableDeck o) {
            return this.mName.compareTo(o.mName);
        }


        @Override
        public int describeContents() {
            return 0;
        }


        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeLong(mDeckId);
            dest.writeString(mName);
        }



        @SuppressWarnings("unused")
        public static final Parcelable.Creator<SelectableDeck> CREATOR = new Parcelable.Creator<SelectableDeck>() {
            @Override
            public SelectableDeck createFromParcel(Parcel in) {
                return new SelectableDeck(in);
            }


            @Override
            public SelectableDeck[] newArray(int size) {
                return new SelectableDeck[size];
            }
        };
    }


    public interface DeckSelectionListener {
        void onDeckSelected(@Nullable SelectableDeck deck);
    }
}
