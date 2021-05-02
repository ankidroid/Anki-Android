/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.ichi2.anki.dialogs.DeckSelectionDialog;
import com.ichi2.anki.widgets.DeckDropDownAdapter;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.stats.Stats;
import com.ichi2.utils.DeckComparator;
import com.ichi2.utils.FunctionalInterfaces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import timber.log.Timber;

import static com.ichi2.anki.CardBrowser.clearLastDeckId;

public class DeckSpinnerSelection {

    private long mDeckId;
    private ArrayList<Long> mAllDeckIds;
    private Spinner mSpinner;
    private AnkiActivity mContext;
    private List<Deck> mDropDownDecks;
    private DeckDropDownAdapter mDeckDropDownAdapter;
    private boolean mNoteEditorSpinner;
    private static final long ALL_DECKS_ID = 0L;
    private static final String PERSISTENT_STATE_FILE = "DeckPickerState";
    private static final String LAST_DECK_ID_KEY = "lastDeckId";


    public DeckSpinnerSelection(@NonNull AnkiActivity context, @NonNull int spinnerId, @NonNull boolean noteEditorSpinner) {
        this.mContext = context;
        this.mNoteEditorSpinner = noteEditorSpinner;
        ActionBar actionBar = mContext.getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        mSpinner = mContext.findViewById(spinnerId);
    }

    public void initializeActionBarDeckSpinner() {

        // Add drop-down menu to select deck to action bar.
        mDropDownDecks = mContext.getCol().getDecks().allSorted();

        mAllDeckIds = new ArrayList<>(mDropDownDecks.size());
        for (Deck d : mDropDownDecks) {
            long thisDid = d.getLong("id");
            mAllDeckIds.add(thisDid);
        }

        mDeckDropDownAdapter = new DeckDropDownAdapter(mContext, mDropDownDecks);

        mSpinner.setAdapter(mDeckDropDownAdapter);

        setSpinnerListner();

    }

    public void initializeNoteEditorDeckSpinner(@NonNull Card currentEditedCard, @NonNull boolean addNote) {
        Collection col = mContext.getCol();
        mDropDownDecks = col.getDecks().allSorted();
        final ArrayList<String> deckNames = new ArrayList<>(mDropDownDecks.size());
        mAllDeckIds = new ArrayList<>(mDropDownDecks.size());
        for (Deck d : mDropDownDecks) {
            // add current deck and all other non-filtered decks to deck list
            long thisDid = d.getLong("id");
            String currentName = d.getString("name");
            String lineContent = null;
            if (d.isStd()) {
                lineContent = currentName ;
            } else if (!addNote && currentEditedCard != null && currentEditedCard.getDid() == thisDid) {
                lineContent = mContext.getApplicationContext().getString(R.string.current_and_default_deck, currentName, col.getDecks().name(currentEditedCard.getODid()));
            } else {
                continue;
            }
            mAllDeckIds.add(thisDid);
            deckNames.add(lineContent);
        }

        ArrayAdapter<String> noteDeckAdapter = new ArrayAdapter<String>(mContext, android.R.layout.simple_spinner_item, deckNames) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {

                // Cast the drop down items (popup items) as text view
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);

                // If this item is selected
                if (position == mSpinner.getSelectedItemPosition()) {
                    tv.setBackgroundColor(ContextCompat.getColor(mContext, R.color.note_editor_selected_item_background));
                    tv.setTextColor(ContextCompat.getColor(mContext, R.color.note_editor_selected_item_text));
                }

                // Return the modified view
                return tv;
            }
        };

        mSpinner.setAdapter(noteDeckAdapter);
        setSpinnerListner();
    }

    public void setSpinnerListner() {
        mSpinner.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                displayDeckOverrideDialog(mContext.getCol());
            }
            return true;
        });

        setSpinnerVisibility(View.VISIBLE);
    }

    public void updateDeckPosition() {
        int position = mAllDeckIds.indexOf(mDeckId);
        if (position != -1) {
            selectDropDownItem(position);
        } else {
            Timber.e("updateDeckPosition() error :: mCurrentDid=%d, position=%d", mDeckId, position);
        }
    }

    public void notifyDataSetChanged() {
        mDeckDropDownAdapter.notifyDataSetChanged();
    }

    public void setEnabledActionBarSpinner(boolean enabled) {
        mSpinner.setEnabled(enabled);
    }

    public void setSpinnerVisibility(int view) {
        mSpinner.setVisibility(view);
    }

    public List<Deck> getDropDownDecks() {
        return mDropDownDecks;
    }

    public void setDeckId(Long deckId) {
        this.mDeckId = deckId;
    }

    public Long getDeckId() {
        return mDeckId;
    }

    public Spinner getSpinner() {
        return mSpinner;
    }

    // Iterates the drop down decks, and selects the one matching the given id
    public boolean selectDeckById(long deckId) {
        if (deckId == ALL_DECKS_ID) {
            selectAllDecks();
            return true;
        }
        for (int dropDownDeckIdx = 0; dropDownDeckIdx < mAllDeckIds.size(); dropDownDeckIdx++) {
            if (mAllDeckIds.get(dropDownDeckIdx) == deckId) {
                int position = mNoteEditorSpinner ? dropDownDeckIdx : dropDownDeckIdx + 1;
                selectDropDownItem(position);
                if (!mNoteEditorSpinner) {
                    saveLastDeckId(deckId);
                }
                return true;
            }
        }
        return false;
    }

    void selectAllDecks() {
        selectDropDownItem(0);
        saveLastDeckId(Stats.ALL_DECKS_ID);
    }

    private void saveLastDeckId(Long id) {
        if (id == null) {
            clearLastDeckId();
            return;
        }
        mContext.getSharedPreferences(PERSISTENT_STATE_FILE, 0).edit().putLong(LAST_DECK_ID_KEY, id).apply();
    }

    public void selectDropDownItem(int position) {
        mSpinner.setSelection(position);
        if (position == 0) {
            mDeckId = Stats.ALL_DECKS_ID;
        } else {
            mDeckId = mAllDeckIds.get(position - 1);
        }
    }

    public void displayDeckOverrideDialog(Collection col) {
        FunctionalInterfaces.Filter<Deck> nonDynamic = (d) -> !Decks.isDynamic(d);
        List<DeckSelectionDialog.SelectableDeck> decks = DeckSelectionDialog.SelectableDeck.fromCollection(col, nonDynamic);

        if (!mNoteEditorSpinner) {
            decks.add(new DeckSelectionDialog.SelectableDeck(ALL_DECKS_ID, "All Decks"));
        }

        DeckSelectionDialog dialog = DeckSelectionDialog.newInstance(mContext.getString(R.string.search_deck), null, false, decks);
        AnkiActivity.showDialogFragment(mContext, dialog);
    }
}
