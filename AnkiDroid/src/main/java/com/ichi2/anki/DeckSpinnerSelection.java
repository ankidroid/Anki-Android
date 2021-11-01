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

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.ichi2.anki.dialogs.DeckSelectionDialog;
import com.ichi2.anki.servicelayer.DeckService;
import com.ichi2.anki.widgets.DeckDropDownAdapter;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.stats.Stats;
import com.ichi2.utils.FragmentManagerUtilsKt;
import com.ichi2.utils.FunctionalInterfaces;
import com.ichi2.utils.WithFragmentManager;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import timber.log.Timber;

public class DeckSpinnerSelection {

    private long mDeckId;
    /**
     * All of the decks shown to the user.
     */
    private ArrayList<Long> mAllDeckIds;
    /**
     * The spinner displayed in the activity.
     * Empty at construction. After initialization, it contains in this order:
     * * "All decks" if mShowAllDecks is true
     * * then it contains all decks from mAllDeckIds.
     */
    @NonNull
    private final Spinner mSpinner;
    @NonNull
    private final WithFragmentManager mWithFragmentManager;
    private final Context mContext;
    @NonNull
    private final Collection mCollection;
    private List<Deck> mDropDownDecks;
    private DeckDropDownAdapter mDeckDropDownAdapter;
    private final boolean mShowAllDecks;
    private static final long ALL_DECKS_ID = 0L;
    /** Whether to show the default deck if it is not visible in the Deck Picker */
    private final boolean mAlwaysShowDefault;


    /**
     * @param spinner Currently empty Spinner. Used to access the Android view.
     */
    public DeckSpinnerSelection(@NonNull AnkiActivity context, @NonNull Collection collection, @NonNull Spinner spinner, boolean showAllDecks, boolean alwaysShowDefault) {
        this.mContext = context;
        this.mCollection = collection;
        this.mSpinner = spinner;
        this.mWithFragmentManager = FragmentManagerUtilsKt.toFragmentManager(context);
        this.mShowAllDecks = showAllDecks;
        this.mAlwaysShowDefault = alwaysShowDefault;
    }

    public void initializeActionBarDeckSpinner(@NonNull ActionBar actionBar) {
        actionBar.setDisplayShowTitleEnabled(false);

        // Add drop-down menu to select deck to action bar.
        mDropDownDecks = computeDropDownDecks();

        mAllDeckIds = new ArrayList<>(mDropDownDecks.size());
        for (Deck d : mDropDownDecks) {
            long thisDid = d.getLong("id");
            mAllDeckIds.add(thisDid);
        }

        mDeckDropDownAdapter = new DeckDropDownAdapter(mContext, mDropDownDecks);

        mSpinner.setAdapter(mDeckDropDownAdapter);

        setSpinnerListener();

    }

    public void initializeNoteEditorDeckSpinner(@Nullable Card currentEditedCard, boolean addNote) {
        Collection col = mCollection;
        mDropDownDecks = computeDropDownDecks();
        final ArrayList<String> deckNames = new ArrayList<>(mDropDownDecks.size());
        mAllDeckIds = new ArrayList<>(mDropDownDecks.size());
        for (Deck d : mDropDownDecks) {
            // add current deck and all other non-filtered decks to deck list
            long thisDid = d.getLong("id");
            String currentName = d.getString("name");
            String lineContent;
            if (d.isStd()) {
                lineContent = currentName;
            } else {
                // We do not allow cards to be moved to dynamic deck.
                // That mean we do not list dynamic decks in the spinner, with one exception
                if (!addNote && currentEditedCard != null && currentEditedCard.getDid() == thisDid) {
                    // If the current card is in a dynamic deck, it can stay there. Hence current deck is added
                    // to the spinner, even if it is dynamic.
                    lineContent = mContext.getApplicationContext().getString(R.string.current_and_default_deck, currentName, col.getDecks().name(currentEditedCard.getODid()));
                } else {
                    continue;
                }
            }
            mAllDeckIds.add(thisDid);
            deckNames.add(lineContent);
        }

        ArrayAdapter<String> noteDeckAdapter = new ArrayAdapter<String>(mContext, R.layout.multiline_spinner_item, deckNames) {
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
        setSpinnerListener();

    }


    /**
     * @return All decks, except maybe default if it should be hidden.
     */
    @NonNull
    protected List<Deck> computeDropDownDecks() {
        List<Deck> decks = mCollection.getDecks().allSorted();
        if (shouldHideDefaultDeck()) {
            decks.removeIf(x -> x.getLong("id") == Consts.DEFAULT_DECK_ID);
        }
        return decks;
    }


    public void setSpinnerListener() {
        mSpinner.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                displayDeckOverrideDialog(mCollection);
            }
            return true;
        });

        setSpinnerVisibility(View.VISIBLE);
    }


    /**
     * Move the selected deck in the spinner to mDeckId.
     * Timber if mDeckId is not an id of a known deck.
     */
    public void updateDeckPosition(long deckId) {
        int position = mAllDeckIds.indexOf(deckId);
        if (position != -1) {
            mSpinner.setSelection(position);
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

    public boolean hasSpinner() {
        return mSpinner != null;
    }

    /**
     * Iterates the drop down decks, and selects the one matching the given id.
     * @param deckId The deck id to be selected.
     * @param setAsCurrentDeck If true, deckId will be set as the current deck id of Collection
     * (this means the deck selected here will continue to appear in any future Activity whose
     * display data is loaded from Collection's current deck). If false, deckId will not be set as
     * the current deck id of Collection.
     * @return True if selection succeeded.
     */
    public boolean selectDeckById(long deckId, boolean setAsCurrentDeck) {
        if (deckId == ALL_DECKS_ID) {
            return selectAllDecks();
        }
        return selectDeck(deckId, setAsCurrentDeck);
    }


    /**
     * select in the spinner deck with id
     * @param deckId The deck id to search (not ALL_DECKS_ID)
     * @param setAsCurrentDeck whether this deck should be selected in the collection (if it exists)
     * @return whether it was found
     */
    private boolean selectDeck(long deckId, boolean setAsCurrentDeck) {
        for (int dropDownDeckIdx = 0; dropDownDeckIdx < mAllDeckIds.size(); dropDownDeckIdx++) {
            if (mAllDeckIds.get(dropDownDeckIdx) == deckId) {
                int position = mShowAllDecks ? dropDownDeckIdx + 1 : dropDownDeckIdx;
                mSpinner.setSelection(position);
                if (setAsCurrentDeck) {
                    mCollection.getDecks().select(deckId);
                }
                return true;
            }
        }
        return false;
    }


    /**
     * Select all decks. Must be called only if mShowAllDecks.
     * @return whether selection was a success.
     */
    boolean selectAllDecks() {
        if (!mShowAllDecks) {
            AnkiDroidApp.sendExceptionReport("selectAllDecks was called while `mShowAllDecks is false`", "DeckSpinnerSelection:selectAllDecks");
            return false;
        }
        mSpinner.setSelection(0);
        return true;
    }


    public void displayDeckOverrideDialog(Collection col) {
        FunctionalInterfaces.Filter<Deck> nonDynamic = (d) -> !Decks.isDynamic(d);
        List<DeckSelectionDialog.SelectableDeck> decks = DeckSelectionDialog.SelectableDeck.fromCollection(col, nonDynamic);
        if (mShowAllDecks) {
            decks.add(new DeckSelectionDialog.SelectableDeck(ALL_DECKS_ID, mContext.getResources().getString(R.string.card_browser_all_decks)));
        }
        if (shouldHideDefaultDeck()) {
            decks.removeIf(x -> x.getDeckId() == Consts.DEFAULT_DECK_ID);
        }

        DeckSelectionDialog dialog = DeckSelectionDialog.newInstance(mContext.getString(R.string.search_deck), null, false, decks);
        AnkiActivity.showDialogFragment(mWithFragmentManager.getFragmentManager(), dialog);
    }


    /**
     * @return Whether default deck should appear in the list of deck
     */
    protected boolean shouldHideDefaultDeck() {
        return !mAlwaysShowDefault && !DeckService.shouldShowDefaultDeck(mCollection);
    }
}
