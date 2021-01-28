/****************************************************************************************
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.dialogs.CardBrowserMySearchesDialog;
import com.ichi2.anki.dialogs.CardBrowserOrderDialog;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.dialogs.IntegerDialog;
import com.ichi2.anki.dialogs.RescheduleDialog;
import com.ichi2.anki.dialogs.SimpleMessageDialog;
import com.ichi2.anki.dialogs.TagsDialog;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.widgets.DeckDropDownAdapter;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskListenerWithContext;
import com.ichi2.async.TaskManager;
import com.ichi2.compat.Compat;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.Deck;
import com.ichi2.themes.Themes;
import com.ichi2.upgrade.Upgrade;
import com.ichi2.utils.BooleanGetter;
import com.ichi2.utils.FunctionalInterfaces;
import com.ichi2.utils.LanguageUtil;
import com.ichi2.utils.PairWithBoolean;
import com.ichi2.utils.PairWithCard;
import com.ichi2.utils.Permissions;
import com.ichi2.widget.WidgetStatus;

import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import timber.log.Timber;

import static com.ichi2.anki.CardBrowser.Column.*;
import static com.ichi2.libanki.stats.Stats.SECONDS_PER_DAY;
import static com.ichi2.anim.ActivityTransitionAnimation.Direction.*;

public class CardBrowser extends NavigationDrawerActivity implements
        DeckDropDownAdapter.SubtitleListener {

    enum Column {
        QUESTION,
        ANSWER,
        FLAGS,
        SUSPENDED,
        MARKED,
        SFLD,
        DECK,
        TAGS,
        ID,
        CARD,
        DUE,
        EASE,
        CHANGED,
        CREATED,
        EDITED,
        INTERVAL,
        LAPSES,
        NOTE_TYPE,
        REVIEWS
    }

    /** List of cards in the browser.
    * When the list is changed, the position member of its elements should get changed.*/
    @NonNull
    private CardCollection<CardCache> mCards = new CardCollection<>();
    private ArrayList<Deck> mDropDownDecks;
    private ListView mCardsListView;
    private SearchView mSearchView;
    private MultiColumnListAdapter mCardsAdapter;
    private String mSearchTerms;
    private String mRestrictOnDeck;
    private int mCurrentFlag;

    private MenuItem mSearchItem;
    private MenuItem mSaveSearchItem;
    private MenuItem mMySearchesItem;
    private MenuItem mPreviewItem;

    private Snackbar mUndoSnackbar;

    public static Card sCardBrowserCard;

    // card that was clicked (not marked)
    private long mCurrentCardId;

    private int mOrder;
    private boolean mOrderAsc;
    private int mColumn1Index;
    private int mColumn2Index;

    //DEFECT: Doesn't need to be a local
    /** The next deck for the "Change Deck" operation */
    private long mNewDid;

    private static final int EDIT_CARD = 0;
    private static final int ADD_NOTE = 1;
    private static final int PREVIEW_CARDS = 2;

    private static final int DEFAULT_FONT_SIZE_RATIO = 100;
    // Should match order of R.array.card_browser_order_labels
    public static final int CARD_ORDER_NONE = 0;
    private static final String[] fSortTypes = new String[] {
        "",
        "noteFld",
        "noteCrt",
        "noteMod",
        "cardMod",
        "cardDue",
        "cardIvl",
        "cardEase",
        "cardReps",
        "cardLapses"};
    private static final Column[] COLUMN1_KEYS = {QUESTION, SFLD};

    // list of available keys in mCards corresponding to the column names in R.array.browser_column2_headings.
    // Note: the last 6 are currently hidden
    private static final Column[] COLUMN2_KEYS = {ANSWER,
        CARD,
        DECK,
        NOTE_TYPE,
        QUESTION,
        TAGS,
        LAPSES,
        REVIEWS,
        INTERVAL,
        EASE,
        DUE,
        CHANGED,
        CREATED,
        EDITED,
    };
    private long mLastRenderStart = 0;
    private DeckDropDownAdapter mDropDownAdapter;
    private Spinner mActionBarSpinner;
    private TextView mActionBarTitle;
    private boolean mReloadRequired = false;
    private boolean mInMultiSelectMode = false;
    private final Set<CardCache> mCheckedCards = Collections.synchronizedSet(new LinkedHashSet<>());
    private int mLastSelectedPosition;
    @Nullable
    private Menu mActionBarMenu;

    private static final int SNACKBAR_DURATION = 8000;


    // Values related to persistent state data
    private static final long ALL_DECKS_ID = 0L;
    private static final String PERSISTENT_STATE_FILE = "DeckPickerState";
    private static final String LAST_DECK_ID_KEY = "lastDeckId";

    public static final int CARD_NOT_AVAILABLE = -1;
    private long mOldCardId = 0;
    private int mOldCardTopOffset = 0;
    private boolean mShouldRestoreScroll = false;
    private boolean mPostAutoScroll = false;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private final MaterialDialog.ListCallbackSingleChoice mOrderDialogListener =
            (materialDialog, view, which, charSequence) -> {
                changeCardOrder(which);
                return true;
            };


    protected void changeCardOrder(int which) {
        if (which != mOrder) {
            mOrder = which;
            mOrderAsc = false;
            if (mOrder == 0) {
                // if the sort value in the card browser was changed, then perform a new search
                getCol().getConf().put("sortType", fSortTypes[1]);
                AnkiDroidApp.getSharedPrefs(getBaseContext()).edit()
                        .putBoolean("cardBrowserNoSorting", true)
                        .commit();
            } else {
                getCol().getConf().put("sortType", fSortTypes[mOrder]);
                AnkiDroidApp.getSharedPrefs(getBaseContext()).edit()
                        .putBoolean("cardBrowserNoSorting", false)
                        .commit();
            }
            getCol().getConf().put("sortBackwards", mOrderAsc);
            searchCards();
        } else if (which != CARD_ORDER_NONE) {
            // if the same element is selected again, reverse the order
            mOrderAsc = !mOrderAsc;
            getCol().getConf().put("sortBackwards", mOrderAsc);
            mCards.reverse();
            updateList();
        }
    }


    private RepositionCardHandler repositionCardHandler() {
        return new RepositionCardHandler(this);
    }

    private static class RepositionCardHandler extends TaskListenerWithContext<CardBrowser, Object, PairWithBoolean<Card[]>> {
        public RepositionCardHandler(CardBrowser browser) {
            super(browser);
        }

        @Override
        public void actualOnPreExecute(@NonNull CardBrowser browser) {
            Timber.d("CardBrowser::RepositionCardHandler() onPreExecute");
        }


        @Override
        public void actualOnPostExecute(@NonNull CardBrowser browser, PairWithBoolean<Card[]> cards) {
            Timber.d("CardBrowser::RepositionCardHandler() onPostExecute");
            browser.mReloadRequired = true;
            int cardCount = cards.other.length;
            UIUtils.showThemedToast(browser,
                    browser.getResources().getQuantityString(R.plurals.reposition_card_dialog_acknowledge, cardCount, cardCount), true);
            browser.reloadCards(cards.other);
            browser.supportInvalidateOptionsMenu();
        }
    }

    private ResetProgressCardHandler resetProgressCardHandler() {
        return new ResetProgressCardHandler(this);
    }
    private static class ResetProgressCardHandler extends TaskListenerWithContext<CardBrowser, Object, PairWithBoolean<Card[]>>{
        public ResetProgressCardHandler(CardBrowser browser) {
            super(browser);
        }

        @Override
        public void actualOnPreExecute(@NonNull CardBrowser browser) {
            Timber.d("CardBrowser::ResetProgressCardHandler() onPreExecute");
        }


        @Override
        public void actualOnPostExecute(@NonNull CardBrowser browser, PairWithBoolean<Card[]> cards) {
            Timber.d("CardBrowser::ResetProgressCardHandler() onPostExecute");
            browser.mReloadRequired = true;
            int cardCount = cards.other.length;
            UIUtils.showThemedToast(browser,
                    browser.getResources().getQuantityString(R.plurals.reset_cards_dialog_acknowledge, cardCount, cardCount), true);
            browser.reloadCards(cards.other);
            browser.supportInvalidateOptionsMenu();
        }
    }

    private RescheduleCardHandler rescheduleCardHandler() {
        return new RescheduleCardHandler(this);
    }
    private static class RescheduleCardHandler extends TaskListenerWithContext<CardBrowser, Card, PairWithBoolean<Card[]>>{
        public RescheduleCardHandler (CardBrowser browser) {
            super(browser);
        }

        @Override
        public void actualOnPreExecute(@NonNull CardBrowser browser) {
            Timber.d("CardBrowser::RescheduleCardHandler() onPreExecute");
        }


        @Override
        public void actualOnPostExecute(@NonNull CardBrowser browser, PairWithBoolean<Card[]> cards) {
            Timber.d("CardBrowser::RescheduleCardHandler() onPostExecute");
            browser.mReloadRequired = true;
            int cardCount = cards.other.length;
            UIUtils.showThemedToast(browser,
                    browser.getResources().getQuantityString(R.plurals.reschedule_cards_dialog_acknowledge, cardCount, cardCount), true);
            browser.reloadCards(cards.other);
            browser.supportInvalidateOptionsMenu();
        }
    }

    private final CardBrowserMySearchesDialog.MySearchesDialogListener mMySearchesDialogListener =
            new CardBrowserMySearchesDialog.MySearchesDialogListener() {
        @Override
        public void onSelection(String searchName) {
            Timber.d("OnSelection using search named: %s", searchName);
            JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
            Timber.d("SavedFilters are %s", savedFiltersObj.toString());
            if (savedFiltersObj != null) {
                mSearchTerms = savedFiltersObj.optString(searchName);
                Timber.d("OnSelection using search terms: %s", mSearchTerms);
                mSearchView.setQuery(mSearchTerms, false);
                mSearchItem.expandActionView();
                searchCards();
            }
        }

        @Override
        public void onRemoveSearch(String searchName) {
            Timber.d("OnRemoveSelection using search named: %s", searchName);
            JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
            if (savedFiltersObj != null && savedFiltersObj.has(searchName)) {
                savedFiltersObj.remove(searchName);
                getCol().getConf().put("savedFilters", savedFiltersObj);
                getCol().flush();
                if (savedFiltersObj.length() == 0) {
                    mMySearchesItem.setVisible(false);
                }
            }

        }

        @Override
        public void onSaveSearch(String searchName, String searchTerms) {
            if (TextUtils.isEmpty(searchName)) {
                UIUtils.showThemedToast(CardBrowser.this,
                        getString(R.string.card_browser_list_my_searches_new_search_error_empty_name), true);
                return;
            }
            JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
            boolean should_save = false;
            if (savedFiltersObj == null) {
                savedFiltersObj = new JSONObject();
                savedFiltersObj.put(searchName, searchTerms);
                should_save = true;
            } else if (!savedFiltersObj.has(searchName)) {
                savedFiltersObj.put(searchName, searchTerms);
                should_save = true;
            } else {
                UIUtils.showThemedToast(CardBrowser.this,
                                        getString(R.string.card_browser_list_my_searches_new_search_error_dup), true);
            }
            if (should_save) {
                getCol().getConf().put("savedFilters", savedFiltersObj);
                getCol().flush();
                mSearchView.setQuery("", false);
                mMySearchesItem.setVisible(true);
            }
        }
    };


    private void onSearch() {
        mSearchTerms = mSearchView.getQuery().toString();
        if (mSearchTerms.length() == 0) {
            mSearchView.setQueryHint(getResources().getString(R.string.deck_conf_cram_search));
        }
        searchCards();
    }

    private List<Long> getSelectedCardIds() {
        List<Long> ids = new ArrayList<>(mCheckedCards.size());
        for (CardCache cardPosition : mCheckedCards) {
            ids.add(cardPosition.getId());
        }
        return ids;
    }

    private boolean canPerformCardInfo() {
        return checkedCardCount() == 1;
    }

    private boolean canPerformMultiSelectEditNote() {
        //The noteId is not currently available. Only allow if a single card is selected for now.
        return checkedCardCount() == 1;
    }


    /**
     * Change Deck
     * @param deckPosition NOT the did. The index in the DISPLAYED Deck list to change the decks to.
     * grep: changeDeck
     */
    @VisibleForTesting
    void moveSelectedCardsToDeck(int deckPosition) {
        List<Long> ids = getSelectedCardIds();

        Deck selectedDeck = getValidDecksForChangeDeck().get(deckPosition);

        try {
            //#5932 - can't be dynamic
            if (Decks.isDynamic(selectedDeck)) {
                Timber.w("Attempted to change cards to dynamic deck. Cancelling operation.");
                displayCouldNotChangeDeck();
                return;
            }
        } catch (Exception e) {
            displayCouldNotChangeDeck();
            Timber.e(e);
            return;
        }

        mNewDid = selectedDeck.getLong("id");

        Timber.i("Changing selected cards to deck: %d", mNewDid);

        if (ids.isEmpty()) {
            endMultiSelectMode();
            mCardsAdapter.notifyDataSetChanged();
            return;
        }

        if (ids.contains(getReviewerCardId())) {
            mReloadRequired = true;
        }

        executeChangeCollectionTask(ids, mNewDid);
    }


    private void displayCouldNotChangeDeck() {
        UIUtils.showThemedToast(this, getString(R.string.card_browser_deck_change_error), true);
    }


    @VisibleForTesting
    Long getLastDeckId() {
        SharedPreferences state = getSharedPreferences(PERSISTENT_STATE_FILE,0);
        if (!state.contains(LAST_DECK_ID_KEY)) {
            return null;
        }
        return state.getLong(LAST_DECK_ID_KEY, -1);
    }

    public static void clearLastDeckId() {
        Context context = AnkiDroidApp.getInstance();
        context.getSharedPreferences(PERSISTENT_STATE_FILE,0).edit().remove(LAST_DECK_ID_KEY).apply();
    }

    private void saveLastDeckId(Long id) {
        if (id == null) {
            clearLastDeckId();
            return;
        }
        getSharedPreferences(PERSISTENT_STATE_FILE, 0).edit().putLong(LAST_DECK_ID_KEY, id).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        super.onCreate(savedInstanceState);
        Timber.d("onCreate()");
        if (wasLoadedFromExternalTextActionItem() && !Permissions.hasStorageAccessPermission(this)) {
            Timber.w("'Card Browser' Action item pressed before storage permissions granted.");
            UIUtils.showThemedToast(this, getString(R.string.intent_handler_failed_no_storage_permission), false);
            displayDeckPickerForPermissionsDialog();
            return;
        }
        setContentView(R.layout.card_browser);
        initNavigationDrawer(findViewById(android.R.id.content));
        startLoadingCollection();
    }


    // Finish initializing the activity after the collection has been correctly loaded
    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        Timber.d("onCollectionLoaded()");
        registerExternalStorageListener();

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());

        // Load reference to action bar title
        mActionBarTitle = findViewById(R.id.toolbar_title);

        // Add drop-down menu to select deck to action bar.
        mDropDownDecks = getCol().getDecks().allSorted();
        mDropDownAdapter = new DeckDropDownAdapter(this, mDropDownDecks);
        ActionBar mActionBar = getSupportActionBar();
        if (mActionBar != null) {
            mActionBar.setDisplayShowTitleEnabled(false);
        }
        mActionBarSpinner = findViewById(R.id.toolbar_spinner);
        mActionBarSpinner.setAdapter(mDropDownAdapter);
        mActionBarSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                deckDropDownItemChanged(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });
        mActionBarSpinner.setVisibility(View.VISIBLE);

        mOrder = CARD_ORDER_NONE;
        String colOrder = getCol().getConf().getString("sortType");
        for (int c = 0; c < fSortTypes.length; ++c) {
            if (fSortTypes[c].equals(colOrder)) {
                mOrder = c;
                break;
            }
        }
        if (mOrder == 1 && preferences.getBoolean("cardBrowserNoSorting", false)) {
            mOrder = 0;
        }
        //This upgrade should already have been done during
        //setConf. However older version of AnkiDroid didn't call
        //upgradeJSONIfNecessary during setConf, which means the
        //conf saved may still have this bug.
        mOrderAsc = Upgrade.upgradeJSONIfNecessary(getCol(), getCol().getConf(), "sortBackwards", false);

        mCards.reset();
        mCardsListView = findViewById(R.id.card_browser_list);
        // Create a spinner for column1
        Spinner cardsColumn1Spinner = findViewById(R.id.browser_column1_spinner);
        ArrayAdapter<CharSequence> column1Adapter = ArrayAdapter.createFromResource(this,
                R.array.browser_column1_headings, android.R.layout.simple_spinner_item);
        column1Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cardsColumn1Spinner.setAdapter(column1Adapter);
        mColumn1Index = AnkiDroidApp.getSharedPrefs(getBaseContext()).getInt("cardBrowserColumn1", 0);
        cardsColumn1Spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                // If a new column was selected then change the key used to map from mCards to the column TextView
                if (pos != mColumn1Index) {
                    mColumn1Index = pos;
                    AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit()
                            .putInt("cardBrowserColumn1", mColumn1Index).commit();
                    Column[] fromMap = mCardsAdapter.getFromMapping();
                    fromMap[0] = COLUMN1_KEYS[mColumn1Index];
                    mCardsAdapter.setFromMapping(fromMap);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do Nothing
            }
        });
        // Load default value for column2 selection
        mColumn2Index = AnkiDroidApp.getSharedPrefs(getBaseContext()).getInt("cardBrowserColumn2", 0);
        // Setup the column 2 heading as a spinner so that users can easily change the column type
        Spinner cardsColumn2Spinner = findViewById(R.id.browser_column2_spinner);
        ArrayAdapter<CharSequence> column2Adapter = ArrayAdapter.createFromResource(this,
                R.array.browser_column2_headings, android.R.layout.simple_spinner_item);
        column2Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cardsColumn2Spinner.setAdapter(column2Adapter);
        // Create a new list adapter with updated column map any time the user changes the column
        cardsColumn2Spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                // If a new column was selected then change the key used to map from mCards to the column TextView
                if (pos != mColumn2Index) {
                    mColumn2Index = pos;
                    AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit()
                            .putInt("cardBrowserColumn2", mColumn2Index).commit();
                    Column[] fromMap = mCardsAdapter.getFromMapping();
                    fromMap[1] = COLUMN2_KEYS[mColumn2Index];
                    mCardsAdapter.setFromMapping(fromMap);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do Nothing
            }
        });
        // get the font and font size from the preferences
        int sflRelativeFontSize = preferences.getInt("relativeCardBrowserFontSize", DEFAULT_FONT_SIZE_RATIO);
        String sflCustomFont = preferences.getString("browserEditorFont", "");
        Column[] columnsContent = {COLUMN1_KEYS[mColumn1Index], COLUMN2_KEYS[mColumn2Index]};
        // make a new list adapter mapping the data in mCards to column1 and column2 of R.layout.card_item_browser
        mCardsAdapter = new MultiColumnListAdapter(
                this,
                R.layout.card_item_browser,
                columnsContent,
                new int[] {R.id.card_sfld, R.id.card_column2},
                sflRelativeFontSize,
                sflCustomFont);
        // link the adapter to the main mCardsListView
        mCardsListView.setAdapter(mCardsAdapter);
        // make the items (e.g. question & answer) render dynamically when scrolling
        mCardsListView.setOnScrollListener(new RenderOnScroll());
        // set the spinner index
        cardsColumn1Spinner.setSelection(mColumn1Index);
        cardsColumn2Spinner.setSelection(mColumn2Index);


        mCardsListView.setOnItemClickListener((parent, view, position, id) -> {
            if (mInMultiSelectMode) {
                // click on whole cell triggers select
                CheckBox cb = view.findViewById(R.id.card_checkbox);
                cb.toggle();
                onCheck(position, view);
            } else {
                // load up the card selected on the list
                long clickedCardId = getCards().get(position).getId();
                saveScrollingState(position);
                openNoteEditorForCard(clickedCardId);
            }
        });
        mCardsListView.setOnItemLongClickListener((adapterView, view, position, id) -> {
            mLastSelectedPosition = position;
            saveScrollingState(position);
            loadMultiSelectMode();

            // click on whole cell triggers select
            CheckBox cb = view.findViewById(R.id.card_checkbox);
            cb.toggle();
            onCheck(position, view);
            recenterListView(view);
            mCardsAdapter.notifyDataSetChanged();
            return true;
        });

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // If a valid value for last deck exists then use it, otherwise use libanki selected deck
        if (getLastDeckId() != null && getLastDeckId() == ALL_DECKS_ID) {
            selectAllDecks();
        } else  if (getLastDeckId() != null && getCol().getDecks().get(getLastDeckId(), false) != null) {
            selectDeckById(getLastDeckId());
        } else {
            selectDeckById(getCol().getDecks().selected());
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // NOTE: These are all active when typing in the search - doesn't matter as all need CTRL

        switch (keyCode) {
            /* Ctrl+A - Select All */
            case KeyEvent.KEYCODE_A: {
                if (event.isCtrlPressed()) {
                    Timber.i("Ctrl+A - Select All");
                    onSelectAll();
                    return true;
                }
                break;
            }
            case KeyEvent.KEYCODE_E: {
                // Ctrl+Shift+E: Export (TODO)
                if (event.isCtrlPressed()) {
                    Timber.i("Ctrl+E: Add Note");
                    addNoteFromCardBrowser();
                    return true;
                }
                break;
            }
            case KeyEvent.KEYCODE_D: {
                if (event.isCtrlPressed()) {
                    Timber.i("Ctrl+D: Change Deck");
                    showChangeDeckDialog();
                    return true;
                }
                break;
            }
            case KeyEvent.KEYCODE_K: {
                if (event.isCtrlPressed()) {
                    Timber.i("Ctrl+K: Toggle Mark");
                    toggleMark();
                    return true;
                }
                break;
            }
            case KeyEvent.KEYCODE_R: {
                if (event.isCtrlPressed() && event.isAltPressed()) {
                    Timber.i("Ctrl+Alt+R - Reschedule");
                    rescheduleSelectedCards();
                    return true;
                }
                break;
            }
            case KeyEvent.KEYCODE_FORWARD_DEL: {
                Timber.i("Delete pressed - Delete Selected Note");
                deleteSelectedNote();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /** All the notes of the selected cards will be marked
     * If one or more card is unmarked, all will be marked,
     * otherwise, they will be unmarked */
    private void toggleMark() {
        if (!hasSelectedCards()) {
            Timber.i("Not marking cards - nothing selected");
            return;
        }

        TaskManager.launchCollectionTask(new CollectionTask.MarkNoteMulti(getSelectedCardIds()),
                markCardHandler());
    }


    @VisibleForTesting
    void selectAllDecks() {
        selectDropDownItem(0);
    }


    /** Opens the note editor for a card.
     * We use the Card ID to specify the preview target */
    public void openNoteEditorForCard(long cardId) {
        mCurrentCardId = cardId;
        sCardBrowserCard = getCol().getCard(mCurrentCardId);
        // start note editor using the card we just loaded
        Intent editCard = new Intent(this, NoteEditor.class);
        editCard.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_CARDBROWSER_EDIT);
        editCard.putExtra(NoteEditor.EXTRA_CARD_ID, sCardBrowserCard.getId());
        startActivityForResultWithAnimation(editCard, EDIT_CARD, LEFT);
        //#6432 - FIXME - onCreateOptionsMenu crashes if receiving an activity result from edit card when in multiselect
        endMultiSelectMode();
    }

    private void openNoteEditorForCurrentlySelectedNote() {
        try {
            //Just select the first one. It doesn't particularly matter if there's a multiselect occurring.
            openNoteEditorForCard(getSelectedCardIds().get(0));
        } catch (Exception e) {
            Timber.w(e, "Error Opening Note Editor");
            UIUtils.showThemedToast(this, getString(R.string.multimedia_editor_something_wrong), false);
        }
    }


    @Override
    protected void onStop() {
        Timber.d("onStop()");
        // cancel rendering the question and answer, which has shared access to mCards
        super.onStop();
        if (!isFinishing()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground();
        }
    }


    @Override
    protected void onDestroy() {
        Timber.d("onDestroy()");
        invalidate();
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            super.onBackPressed();
        } else if (mInMultiSelectMode) {
            endMultiSelectMode();
        } else {
            Timber.i("Back key pressed");
            Intent data = new Intent();
            if (mReloadRequired) {
                // Add reload flag to result intent so that schedule reset when returning to note editor
                data.putExtra("reloadRequired", true);
            }
            closeCardBrowser(RESULT_OK, data);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPostAutoScroll) {
            mPostAutoScroll = false;
        }
    }

    @Override
    protected void onResume() {
        Timber.d("onResume()");
        super.onResume();
        selectNavigationItem(R.id.nav_browser);
    }


    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        Timber.d("onCreateOptionsMenu()");
        mActionBarMenu = menu;
        if (!mInMultiSelectMode) {
            // restore drawer click listener and icon
            restoreDrawerIcon();
            getMenuInflater().inflate(R.menu.card_browser, menu);
            mSaveSearchItem = menu.findItem(R.id.action_save_search);
            mSaveSearchItem.setVisible(false); //the searchview's query always starts empty.
            mMySearchesItem = menu.findItem(R.id.action_list_my_searches);
            JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
            mMySearchesItem.setVisible(savedFiltersObj != null && savedFiltersObj.length() > 0);
            mSearchItem = menu.findItem(R.id.action_search);
            mSearchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    // SearchView doesn't support empty queries so we always reset the search when collapsing
                    mSearchTerms = "";
                    mSearchView.setQuery(mSearchTerms, false);
                    searchCards();
                    // invalidate options menu so that disappeared icons would appear again
                    supportInvalidateOptionsMenu();
                    return true;
                }
            });
            mSearchView = (SearchView) mSearchItem.getActionView();
            mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextChange(String newText) {
                    mSaveSearchItem.setVisible(!TextUtils.isEmpty(newText));
                    return true;
                }

                @Override
                public boolean onQueryTextSubmit(String query) {
                    onSearch();
                    mSearchView.clearFocus();
                    return true;
                }
            });
            mSearchView.setOnSearchClickListener(v -> {
                // Provide SearchView with the previous search terms
                mSearchView.setQuery(mSearchTerms, false);
            });
            // Fixes #6500 - keep the search consistent
            if (!TextUtils.isEmpty(mSearchTerms)) {
                mSearchItem.expandActionView();
                mSearchView.setQuery(mSearchTerms, false);
            }
        } else {
            // multi-select mode
            getMenuInflater().inflate(R.menu.card_browser_multiselect, menu);
            showBackIcon();
        }

        if (mActionBarMenu != null && mActionBarMenu.findItem(R.id.action_undo) != null) {
            MenuItem undo =  mActionBarMenu.findItem(R.id.action_undo);
            undo.setVisible(getCol().undoAvailable());
            undo.setTitle(getResources().getString(R.string.studyoptions_congrats_undo, getCol().undoName(getResources())));
        }

        // Maybe we were called from ACTION_PROCESS_TEXT.
        // In that case we already fill in the search.
        Intent intent = getIntent();
        Compat compat = CompatHelper.getCompat();
        if (compat.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            CharSequence search = intent.getCharSequenceExtra(compat.EXTRA_PROCESS_TEXT);
            if (search != null && search.length() != 0) {
                Timber.i("CardBrowser :: Called with search intent: %s", search.toString());
                mSearchView.setQuery(search, true);
                intent.setAction(Intent.ACTION_DEFAULT);
            }
        }

        mPreviewItem = menu.findItem(R.id.action_preview);
        onSelectionChanged();
        updatePreviewMenuItem();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onNavigationPressed() {
        if (mInMultiSelectMode) {
            endMultiSelectMode();
        } else {
            super.onNavigationPressed();
        }
    }


    private void displayDeckPickerForPermissionsDialog() {
        //TODO: Combine this with class: IntentHandler after both are well-tested
        Intent deckPicker = new Intent(this, DeckPicker.class);
        deckPicker.setAction(Intent.ACTION_MAIN);
        deckPicker.addCategory(Intent.CATEGORY_LAUNCHER);
        deckPicker.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivityWithAnimation(deckPicker, FADE);
        AnkiActivity.finishActivityWithFade(this);
        finishActivityWithFade(this);
        this.setResult(RESULT_CANCELED);
    }


    private boolean wasLoadedFromExternalTextActionItem() {
        Intent intent = this.getIntent();
        if (intent == null) {
            return false;
        }
        //API 23: Replace with Intent.ACTION_PROCESS_TEXT
        return "android.intent.action.PROCESS_TEXT".equalsIgnoreCase(intent.getAction());
    }

    private void updatePreviewMenuItem() {
        if (mPreviewItem == null) {
            return;
        }
        mPreviewItem.setVisible(getCardCount() > 0);
    }

    /** Returns the number of cards that are visible on the screen */
    public int getCardCount() {
        return getCards().size();
    }


    private void updateMultiselectMenu() {
        Timber.d("updateMultiselectMenu()");
        if (mActionBarMenu == null || mActionBarMenu.findItem(R.id.action_suspend_card) == null) {
            return;
        }

        if (!mCheckedCards.isEmpty()) {
            TaskManager.cancelAllTasks(CollectionTask.CheckCardSelection.class);
            TaskManager.launchCollectionTask(new CollectionTask.CheckCardSelection(getCards()),
                    mCheckSelectedCardsHandler);
        }

        mActionBarMenu.findItem(R.id.action_select_all).setVisible(!hasSelectedAllCards());
        //Note: Theoretically should not happen, as this should kick us back to the menu
        mActionBarMenu.findItem(R.id.action_select_none).setVisible(hasSelectedCards());
        mActionBarMenu.findItem(R.id.action_edit_note).setVisible(canPerformMultiSelectEditNote());
        mActionBarMenu.findItem(R.id.action_view_card_info).setVisible(canPerformCardInfo());
    }


    private boolean hasSelectedCards() {
        return !mCheckedCards.isEmpty();
    }

    private boolean hasSelectedAllCards() {
        return checkedCardCount() >= getCardCount(); //must handle 0.
    }


    private void flagTask (int flag) {
        TaskManager.launchCollectionTask(
                new CollectionTask.Flag(getSelectedCardIds(), flag),
                flagCardHandler());
    }

    /** Updates flag icon color and cards shown with given color */
    private void selectionWithFlagTask(int flag) {
        mCurrentFlag = flag;
        filterByFlag();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (getDrawerToggle().onOptionsItemSelected(item)) {
            return true;
        }

        // dismiss undo-snackbar if shown to avoid race condition
        // (when another operation will be performed on the model, it will undo the latest operation)
        if (mUndoSnackbar != null && mUndoSnackbar.isShown())
            mUndoSnackbar.dismiss();

        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            endMultiSelectMode();
            return true;
        } else if (itemId == R.id.action_add_note_from_card_browser) {
            addNoteFromCardBrowser();
            return true;
        } else if (itemId == R.id.action_save_search) {
            String searchTerms = mSearchView.getQuery().toString();
            showDialogFragment(CardBrowserMySearchesDialog.newInstance(null, mMySearchesDialogListener,
                    searchTerms, CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_SAVE));
            return true;
        } else if (itemId == R.id.action_list_my_searches) {
            JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
            HashMap<String, String> savedFilters;
            if (savedFiltersObj != null) {
                savedFilters = new HashMap<>(savedFiltersObj.length());
                for (String searchName : savedFiltersObj) {
                    savedFilters.put(searchName, savedFiltersObj.optString(searchName));
                }
            } else {
                savedFilters = new HashMap<>(0);
            }
            showDialogFragment(CardBrowserMySearchesDialog.newInstance(savedFilters, mMySearchesDialogListener,
                    "", CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_LIST));
            return true;
        } else if (itemId == R.id.action_sort_by_size) {
            showDialogFragment(CardBrowserOrderDialog
                    .newInstance(mOrder, mOrderAsc, mOrderDialogListener));
            return true;
        } else if (itemId == R.id.action_show_marked) {
            mSearchTerms = "tag:marked";
            mSearchView.setQuery("", false);
            mSearchView.setQueryHint(getResources().getString(R.string.card_browser_show_marked));
            searchCards();
            return true;
        } else if (itemId == R.id.action_show_suspended) {
            mSearchTerms = "is:suspended";
            mSearchView.setQuery("", false);
            mSearchView.setQueryHint(getResources().getString(R.string.card_browser_show_suspended));
            searchCards();
            return true;
        } else if (itemId == R.id.action_search_by_tag) {
            showTagsDialog();
            return true;
        } else if (itemId == R.id.action_flag_zero) {
            flagTask(0);
            return true;
        } else if (itemId == R.id.action_flag_one) {
            flagTask(1);
            return true;
        } else if (itemId == R.id.action_flag_two) {
            flagTask(2);
            return true;
        } else if (itemId == R.id.action_flag_three) {
            flagTask(3);
            return true;
        } else if (itemId == R.id.action_flag_four) {
            flagTask(4);
            return true;
        } else if (itemId == R.id.action_select_flag_zero) {
            selectionWithFlagTask(0);
            return true;
        } else if (itemId == R.id.action_select_flag_one) {
            selectionWithFlagTask(1);
            return true;
        } else if (itemId == R.id.action_select_flag_two) {
            selectionWithFlagTask(2);
            return true;
        } else if (itemId == R.id.action_select_flag_three) {
            selectionWithFlagTask(3);
            return true;
        } else if (itemId == R.id.action_select_flag_four) {
            selectionWithFlagTask(4);
            return true;
        } else if (itemId == R.id.action_delete_card) {
            deleteSelectedNote();
            return true;
        } else if (itemId == R.id.action_mark_card) {
            toggleMark();

            return true;
        } else if (itemId == R.id.action_suspend_card) {
            TaskManager.launchCollectionTask(new CollectionTask.SuspendCardMulti(getSelectedCardIds()),
                    suspendCardHandler());

            return true;
        } else if (itemId == R.id.action_change_deck) {
            showChangeDeckDialog();
            return true;
        } else if (itemId == R.id.action_undo) {
            Timber.w("CardBrowser:: Undo pressed");
            onUndo();
            return true;
        } else if (itemId == R.id.action_select_none) {
            onSelectNone();
            return true;
        } else if (itemId == R.id.action_select_all) {
            onSelectAll();
            return true;
        } else if (itemId == R.id.action_preview) {
            onPreview();
            return true;
        } else if (itemId == R.id.action_reset_cards_progress) {
            Timber.i("NoteEditor:: Reset progress button pressed");
            onResetProgress();
            return true;
        } else if (itemId == R.id.action_reschedule_cards) {
            Timber.i("CardBrowser:: Reschedule button pressed");
            rescheduleSelectedCards();
            return true;
        } else if (itemId == R.id.action_reposition_cards) {
            Timber.i("CardBrowser:: Reposition button pressed");

            // Only new cards may be repositioned
            List<Long> cardIds = getSelectedCardIds();
            for (long cardId : cardIds) {
                if (getCol().getCard(cardId).getQueue() != Consts.CARD_TYPE_NEW) {
                    SimpleMessageDialog dialog = SimpleMessageDialog.newInstance(
                            getString(R.string.vague_error),
                            getString(R.string.reposition_card_not_new_error),
                            false);
                    showDialogFragment(dialog);
                    return false;
                }
            }

            IntegerDialog repositionDialog = new IntegerDialog();
            repositionDialog.setArgs(
                    getString(R.string.reposition_card_dialog_title),
                    getString(R.string.reposition_card_dialog_message),
                    5);
            repositionDialog.setCallbackRunnable(position -> repositionCardsNoValidation(cardIds, position));
            showDialogFragment(repositionDialog);
            return true;
        } else if (itemId == R.id.action_edit_note) {
            openNoteEditorForCurrentlySelectedNote();


            return super.onOptionsItemSelected(item);
        } else if (itemId == R.id.action_view_card_info) {
            List<Long> selectedCardIds = getSelectedCardIds();
            if (selectedCardIds.size() > 0) {
                Intent intent = new Intent(this, CardInfo.class);
                intent.putExtra("cardId", selectedCardIds.get(0));
                startActivityWithAnimation(intent, FADE);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    protected void deleteSelectedNote() {
        if (!mInMultiSelectMode) {
            return;
        }
        TaskManager.launchCollectionTask(new CollectionTask.DeleteNoteMulti(getSelectedCardIds()),
                                            mDeleteNoteHandler);

        mCheckedCards.clear();
        endMultiSelectMode();
        mCardsAdapter.notifyDataSetChanged();
    }


    @VisibleForTesting
    void onUndo() {
        if (getCol().undoAvailable()) {
            TaskManager.launchCollectionTask(new CollectionTask.Undo(), mUndoHandler);
        }
    }


    protected void onResetProgress() {
        // Show confirmation dialog before resetting card progress
        ConfirmationDialog dialog = new ConfirmationDialog();
        String title = getString(R.string.reset_card_dialog_title);
        String message = getString(R.string.reset_card_dialog_message);
        dialog.setArgs(title, message);
        Runnable confirm = () -> {
            Timber.i("CardBrowser:: ResetProgress button pressed");
            resetProgressNoConfirm(getSelectedCardIds());
        };
        dialog.setConfirm(confirm);
        showDialogFragment(dialog);
    }


    @VisibleForTesting
    void resetProgressNoConfirm(List<Long> cardIds) {
        TaskManager.launchCollectionTask(new CollectionTask.ResetCards(cardIds), resetProgressCardHandler());
    }


    @VisibleForTesting
    void repositionCardsNoValidation(List<Long> cardIds, Integer position) {
        TaskManager.launchCollectionTask(new CollectionTask.RepositionCards(cardIds, position),
                                            repositionCardHandler());
    }


    protected void onPreview() {
        Intent previewer = getPreviewIntent();
        startActivityForResultWithoutAnimation(previewer, PREVIEW_CARDS);
    }


    @NonNull
    @VisibleForTesting
    Intent getPreviewIntent() {
        if (mInMultiSelectMode && checkedCardCount() > 1) {
            // Multiple cards have been explicitly selected, so preview only those cards
            int index = 0;
            return getPreviewIntent(index, Utils.toPrimitive(getSelectedCardIds()));
        } else {
            // Preview all cards, starting from the one that is currently selected
            int startIndex = mCheckedCards.isEmpty() ? 0 : mCheckedCards.iterator().next().getPosition();
            return getPreviewIntent(startIndex, getAllCardIds());
        }
    }


    @NonNull
    private Intent getPreviewIntent(int index, long[] selectedCardIds) {
        return Previewer.getPreviewIntent(CardBrowser.this, index, selectedCardIds);
    }


    private void rescheduleSelectedCards() {
        if (!hasSelectedCards()) {
            Timber.i("Attempted reschedule - no cards selected");
            return;
        }

        List<Long> selectedCardIds = getSelectedCardIds();
        FunctionalInterfaces.Consumer<Integer> consumer = newDays -> rescheduleWithoutValidation(selectedCardIds, newDays);
        RescheduleDialog rescheduleDialog;
        if (selectedCardIds.size() == 1) {
            long cardId = selectedCardIds.get(0);
            Card selected = getCol().getCard(cardId);
            rescheduleDialog = RescheduleDialog.rescheduleSingleCard(getResources(), selected, consumer);
        } else {
            rescheduleDialog = RescheduleDialog.rescheduleMultipleCards(getResources(),
                    consumer,
                    selectedCardIds.size());
        }
        showDialogFragment(rescheduleDialog);
    }


    @VisibleForTesting
    void rescheduleWithoutValidation(List<Long> selectedCardIds, Integer newDays) {
        TaskManager.launchCollectionTask(new CollectionTask.RescheduleCards(selectedCardIds, newDays),
            rescheduleCardHandler());
    }


    private void showChangeDeckDialog() {
        if (!hasSelectedCards()) {
            Timber.i("Not showing Change Deck - No Cards");
            return;
        }

        AlertDialog.Builder builderSingle = new AlertDialog.Builder(this);
        builderSingle.setTitle(getString(R.string.move_all_to_deck));

        //WARNING: changeDeck depends on this index, so any changes should be reflected there.
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, R.layout.dropdown_deck_item);
        for (Deck deck : getValidDecksForChangeDeck()) {
            try {
                arrayAdapter.add(deck.getString("name"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        builderSingle.setNegativeButton(getString(R.string.dialog_cancel), (dialog, which) -> dialog.dismiss());
        builderSingle.setAdapter(arrayAdapter, (dialog, which) -> moveSelectedCardsToDeck(which));
        builderSingle.show();
    }


    @VisibleForTesting
    Intent getAddNoteIntent() {
        Intent intent = new Intent(CardBrowser.this, NoteEditor.class);
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_CARDBROWSER_ADD);
        Long did = getLastDeckId();
        if (did != null && did > 0) {
            intent.putExtra(NoteEditor.EXTRA_DID, (long) did);
        }
        return intent;
    }

    private void addNoteFromCardBrowser() {
        startActivityForResultWithAnimation(getAddNoteIntent(), ADD_NOTE, LEFT);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // FIXME:
        Timber.d("onActivityResult(requestCode=%d, resultCode=%d)", requestCode, resultCode);
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
        }

        if (requestCode == EDIT_CARD && resultCode != RESULT_CANCELED) {
            Timber.i("CardBrowser:: CardBrowser: Saving card...");
            TaskManager.launchCollectionTask(new CollectionTask.UpdateNote(sCardBrowserCard, false, false),
                    updateCardHandler());
        } else if (requestCode == ADD_NOTE && resultCode == RESULT_OK) {
            if (mSearchView != null) {
                mSearchTerms = mSearchView.getQuery().toString();
                searchCards();
            } else {
                Timber.w("Note was added from browser and on return mSearchView == null");
            }
        }

        // Previewing can now perform an "edit", so it can pass on a reloadRequired
        if (requestCode == PREVIEW_CARDS && data != null
                && (data.getBooleanExtra("reloadRequired", false) || data.getBooleanExtra("noteChanged", false))) {
            searchCards();
            if (getReviewerCardId() == mCurrentCardId) {
                mReloadRequired = true;
            }
        }

        if (requestCode == EDIT_CARD &&  data != null &&
                (data.getBooleanExtra("reloadRequired", false) ||
                        data.getBooleanExtra("noteChanged", false))) {
            // if reloadRequired or noteChanged flag was sent from note editor then reload card list
            searchCards();
            mShouldRestoreScroll = true;
            // in use by reviewer?
            if (getReviewerCardId() == mCurrentCardId) {
                mReloadRequired = true;
            }
        }

        invalidateOptionsMenu();    // maybe the availability of undo changed
    }


    // We spawn CollectionTasks that may create memory pressure, this transmits it so polling isCancelled sees the pressure
    @Override
    public void onTrimMemory(int pressureLevel) {
        super.onTrimMemory(pressureLevel);
        TaskManager.cancelCurrentlyExecutingTask();
    }

    private long getReviewerCardId() {
        if (getIntent().hasExtra("currentCard")) {
            return getIntent().getExtras().getLong("currentCard");
        } else {
            return -1;
        }
    }

    private void showTagsDialog() {
        TagsDialog dialog = TagsDialog.newInstance(
                TagsDialog.TYPE_FILTER_BY_TAG, new ArrayList<>(0), new ArrayList<>(getCol().getTags().all()));
        dialog.setTagsDialogListener(this::filterByTag);
        showDialogFragment(dialog);
    }

    /** Selects the given position in the deck list */
    public void selectDropDownItem(int position) {
        mActionBarSpinner.setSelection(position);
        deckDropDownItemChanged(position);
    }

    /**
     * Performs changes relating to the Deck DropDown Item changing
     * Exists as mActionBarSpinner.setSelection() caused a loop in roboelectirc (calling onItemSelected())
     */
    private void deckDropDownItemChanged(int position) {
        if (position == 0) {
            mRestrictOnDeck = "";
            saveLastDeckId(ALL_DECKS_ID);
        } else {
            Deck deck = mDropDownDecks.get(position - 1);
            mRestrictOnDeck = "deck:\"" + deck.getString("name") + "\" ";
            saveLastDeckId(deck.getLong("id"));
        }
        searchCards();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    void selectDeckId(long targetDid) {
        for (int i = 0; i < mDropDownDecks.size(); i++) {
            if (mDropDownDecks.get(i).getLong("id") == targetDid) {
                deckDropDownItemChanged(i + 1);
                return;
            }
        }
        throw new IllegalStateException("Could not find did " + targetDid);
    }



    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save current search terms
        savedInstanceState.putString("mSearchTerms", mSearchTerms);
        savedInstanceState.putLong("mOldCardId", mOldCardId);
        savedInstanceState.putInt("mOldCardTopOffset", mOldCardTopOffset);
        savedInstanceState.putBoolean("mShouldRestoreScroll", mShouldRestoreScroll);
        savedInstanceState.putBoolean("mPostAutoScroll", mPostAutoScroll);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mSearchTerms = savedInstanceState.getString("mSearchTerms");
        mOldCardId = savedInstanceState.getLong("mOldCardId");
        mOldCardTopOffset = savedInstanceState.getInt("mOldCardTopOffset");
        mShouldRestoreScroll = savedInstanceState.getBoolean("mShouldRestoreScroll");
        mPostAutoScroll = savedInstanceState.getBoolean("mPostAutoScroll");
        searchCards();
    }

    private void invalidate() {
        TaskManager.cancelAllTasks(CollectionTask.SearchCards.class);
        TaskManager.cancelAllTasks(CollectionTask.RenderBrowserQA.class);
        TaskManager.cancelAllTasks(CollectionTask.CheckCardSelection.class);
        mCards.clear();
        mCheckedCards.clear();
    }

    /** Currently unused - to be used in #7676 */
    private void forceRefreshSearch() {
        searchCards();
    }


    private void searchCards() {
        // cancel the previous search & render tasks if still running
        invalidate();
        String searchText;
        if (mSearchTerms == null) {
            mSearchTerms = "";
        }
        if (!"".equals(mSearchTerms) && (mSearchView != null)) {
            mSearchView.setQuery(mSearchTerms, false);
            mSearchItem.expandActionView();
        }
        if (mSearchTerms.contains("deck:")) {
            searchText = mSearchTerms;
        } else {
            searchText = mRestrictOnDeck + mSearchTerms;
        }
        if (colIsOpen() && mCardsAdapter!= null) {
            // clear the existing card list
            mCards.reset();
            mCardsAdapter.notifyDataSetChanged();
            //  estimate maximum number of cards that could be visible (assuming worst-case minimum row height of 20dp)
            int numCardsToRender = (int) Math.ceil(mCardsListView.getHeight() /
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics())) + 5;
            // Perform database query to get all card ids
            TaskManager.launchCollectionTask(new CollectionTask.SearchCards(searchText,
                            (mOrder != CARD_ORDER_NONE),
                            numCardsToRender,
                            mColumn1Index,
                            mColumn2Index),
                    mSearchCardsHandler
            );
        }
    }


    private void updateList() {
        mCardsAdapter.notifyDataSetChanged();
        mDropDownAdapter.notifyDataSetChanged();
        onSelectionChanged();
        updatePreviewMenuItem();
    }

    /**
     * @return text to be used in the subtitle of the drop-down deck selector
     */
    public String getSubtitleText() {
        int count = getCardCount();
        return getResources().getQuantityString(R.plurals.card_browser_subtitle, count, count);
    }


    private static Map<Long, Integer> getPositionMap(CardCollection<CardCache> list) {
        Map<Long, Integer> positions = new HashMap<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            positions.put(list.get(i).getId(), i);
        }
        return positions;
    }

    // Iterates the drop down decks, and selects the one matching the given id
    private boolean selectDeckById(@NonNull Long deckId) {
        for (int dropDownDeckIdx = 0; dropDownDeckIdx < mDropDownDecks.size(); dropDownDeckIdx++) {
            if (mDropDownDecks.get(dropDownDeckIdx).getLong("id") == deckId) {
                selectDropDownItem(dropDownDeckIdx + 1);
                return true;
            }
        }
        return false;
    }

    // convenience method for updateCardsInList(...)
    private void updateCardInList(Card card) {
        List<Card> cards = new ArrayList<>(1);
        cards.add(card);
        updateCardsInList(cards);
    }

    /** Returns the decks which are valid targets for "Change Deck" */
    @VisibleForTesting
    List<Deck> getValidDecksForChangeDeck() {
        List<Deck> nonDynamicDecks = new ArrayList<>(mDropDownDecks.size());
        for (Deck d : mDropDownDecks) {
            if (Decks.isDynamic(d)) {
                continue;
            }
            nonDynamicDecks.add(d);
        }
        return nonDynamicDecks;
    }


    private void filterByTag(List<String> selectedTags, int option) {
        //TODO: Duplication between here and CustomStudyDialog:customStudyFromTags
        mSearchView.setQuery("", false);
        String tags = selectedTags.toString();
        mSearchView.setQueryHint(getResources().getString(R.string.CardEditorTags,
                tags.substring(1, tags.length() - 1)));
        StringBuilder sb = new StringBuilder();
        switch (option) {
            case 1:
                sb.append("is:new ");
                break;
            case 2:
                sb.append("is:due ");
                break;
            default:
                // Logging here might be appropriate : )
                break;
        }
        int i = 0;
        for (String tag : selectedTags) {
            if (i != 0) {
                sb.append("or ");
            } else {
                sb.append("("); // Only if we really have selected tags
            }
            // 7070: quote tags so brackets are properly escaped
            sb.append("tag:").append("'").append(tag).append("'").append(" ");
            i++;
        }
        if (i > 0) {
            sb.append(")"); // Only if we added anything to the tag list
        }
        mSearchTerms = sb.toString();
        searchCards();
    }


    /** Updates search terms to only show cards with selected flag. */
    private void filterByFlag() {
        mSearchView.setQuery("", false);
        String flagSearchTerm = "flag:" + mCurrentFlag;
        if (mSearchTerms.contains("flag:")) {
            mSearchTerms = mSearchTerms.replaceFirst("flag:.", flagSearchTerm);
        }
        else if (!mSearchTerms.isEmpty()) {
            mSearchTerms = flagSearchTerm + " " + mSearchTerms;
        } else {
            mSearchTerms = flagSearchTerm;
        }
        searchCards();
    }


    private static abstract class ListenerWithProgressBar<Progress, Result> extends TaskListenerWithContext<CardBrowser, Progress, Result>{
        public ListenerWithProgressBar(CardBrowser browser) {
            super(browser);
        }

        @Override
        public void actualOnPreExecute(@NonNull CardBrowser browser) {
            browser.showProgressBar();
        }
    }

    /** Does not leak Card Browser. */
    private static abstract class ListenerWithProgressBarCloseOnFalse<Progress, Result extends BooleanGetter> extends ListenerWithProgressBar<Progress, Result> {
        private final String mTimber;
        public ListenerWithProgressBarCloseOnFalse(String timber, CardBrowser browser) {
            super(browser);
            mTimber = timber;
        }

        public ListenerWithProgressBarCloseOnFalse(CardBrowser browser) {
            this(null, browser);
		}

        public void actualOnPostExecute(@NonNull CardBrowser browser, Result result) {
            if (mTimber != null) {
                Timber.d(mTimber);
            }
            if (result.getBoolean()) {
                actualOnValidPostExecute(browser, result);
            } else {
                browser.closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
        }

        protected abstract void actualOnValidPostExecute(CardBrowser browser, Result result);
    }

    /**
     * @param cards Cards that were changed
     */
    private void updateCardsInList(List<Card> cards) {
        CardCollection<CardCache> cardList = getCards();
        Map<Long, Integer> idToPos = getPositionMap(cardList);
        for (Card c : cards) {
            // get position in the mCards search results HashMap
            Integer pos = idToPos.get(c.getId());
            if (pos == null || pos >= getCardCount()) {
                continue;
            }
            // update Q & A etc
            cardList.get(pos).load(true, mColumn1Index, mColumn2Index);
        }

        updateList();
    }

    private UpdateCardHandler updateCardHandler() {
        return new UpdateCardHandler(this);
    }

    private static class UpdateCardHandler extends ListenerWithProgressBarCloseOnFalse<PairWithCard<String>, BooleanGetter> {
        public UpdateCardHandler(CardBrowser browser) {
            super("Card Browser - UpdateCardHandler.actualOnPostExecute(CardBrowser browser)", browser);
        }

        @Override
        public void actualOnProgressUpdate(@NonNull CardBrowser browser, PairWithCard<String> value) {
            browser.updateCardInList(value.getCard());
        }

        @Override
        protected void actualOnValidPostExecute(CardBrowser browser, BooleanGetter result) {
            browser.hideProgressBar();
        }
    }


    private ChangeDeckHandler changeDeckHandler() {
        return new ChangeDeckHandler(this);
    }
    private static class ChangeDeckHandler extends ListenerWithProgressBarCloseOnFalse<Object, PairWithBoolean<Card[]>> {
        public ChangeDeckHandler(CardBrowser browser) {
            super("Card Browser - changeDeckHandler.actualOnPostExecute(CardBrowser browser)", browser);
        }


        @Override
        protected void actualOnValidPostExecute(CardBrowser browser, PairWithBoolean<Card[]> result) {
            browser.hideProgressBar();

            browser.searchCards();
            browser.endMultiSelectMode();
            browser.mCardsAdapter.notifyDataSetChanged();
            browser.invalidateOptionsMenu();    // maybe the availability of undo changed

            if (!result.getBoolean()) {
                Timber.i("changeDeckHandler failed, not offering undo");
                browser.displayCouldNotChangeDeck();
                return;
            }
            // snackbar to offer undo
            String deckName = browser.getCol().getDecks().name(browser.mNewDid);
            browser.mUndoSnackbar = UIUtils.showSnackbar(browser, String.format(browser.getString(R.string.changed_deck_message), deckName), SNACKBAR_DURATION,
                                                         R.string.undo, v -> TaskManager.launchCollectionTask(new CollectionTask.Undo(), browser.mUndoHandler), browser.mCardsListView, null);
        }
    }


    @CheckResult
    private static String formatQA(String text, Context context) {
        boolean showFilenames = AnkiDroidApp.getSharedPrefs(context).getBoolean("card_browser_show_media_filenames", false);
        return formatQAInternal(text, showFilenames);
    }


    /**
     * @param txt The text to strip HTML, comments, tags and media from
     * @param showFileNames Whether [sound:foo.mp3] should be rendered as " foo.mp3 " or  " "
     * @return The formatted string
     */
    @VisibleForTesting
    @CheckResult
    static String formatQAInternal(String txt, boolean showFileNames) {
        /* Strips all formatting from the string txt for use in displaying question/answer in browser */
        String s = txt;
        s = s.replaceAll("<!--.*?-->", "");
        s = s.replace("<br>", " ");
        s = s.replace("<br />", " ");
        s = s.replace("<div>", " ");
        s = s.replace("\n", " ");
        s = showFileNames ? Utils.stripSoundMedia(s) : Utils.stripSoundMedia(s, " ");
        s = s.replaceAll("\\[\\[type:[^]]+]]", "");
        s = showFileNames ? Utils.stripHTMLMedia(s) : Utils.stripHTMLMedia(s, " ");
        s = s.trim();
        return s;
    }

    /**
     * Removes cards from view. Doesn't delete them in model (database).
     */
    private void removeNotesView(Card[] cards, boolean reorderCards) {
        List<Long> cardIds = new ArrayList<>(cards.length);
        for (Card c : cards) {
            cardIds.add(c.getId());
        }
        removeNotesView(cardIds, reorderCards);
    }

    /**
     * Removes cards from view. Doesn't delete them in model (database).
     * @param reorderCards Whether to rearrange the positions of checked items (DEFECT: Currently deselects all)
     */
    private void removeNotesView(java.util.Collection<Long> cardsIds, boolean reorderCards) {
        long reviewerCardId = getReviewerCardId();
        CardCollection<CardCache> oldMCards = getCards();
        Map<Long, Integer> idToPos = getPositionMap(oldMCards);
        Set<Long> idToRemove = new HashSet<>();
        for (Long cardId : cardsIds) {
            if (cardId == reviewerCardId) {
                mReloadRequired = true;
            }
            if (idToPos.containsKey(cardId)) {
                idToRemove.add(cardId);
            }
        }

        List<CardCache> newMCards = new ArrayList<>(oldMCards.size());
        int pos = 0;
        for (CardCache card: oldMCards) {
            if (!idToRemove.contains(card.getId())) {
                newMCards.add(new CardCache(card, pos++));
            }
        }
        mCards.replaceWith(newMCards);

        if (reorderCards) {
            //Suboptimal from a UX perspective, we should reorder
            //but this is only hit on a rare sad path and we'd need to rejig the data structures to allow an efficient
            //search
            Timber.w("Removing current selection due to unexpected removal of cards");
            onSelectNone();
        }

        updateList();
    }

    private SuspendCardHandler suspendCardHandler() {
        return new SuspendCardHandler(this);
    }

    private static class SuspendCardHandler extends ListenerWithProgressBarCloseOnFalse<Void, PairWithBoolean<Card[]>> {
        public SuspendCardHandler(CardBrowser browser) {
            super(browser);
        }

        @Override
        protected void actualOnValidPostExecute(CardBrowser browser, PairWithBoolean<Card[]> cards) {
            browser.updateCardsInList(Arrays.asList(cards.other));
            browser.hideProgressBar();
            browser.invalidateOptionsMenu();    // maybe the availability of undo changed
        }
    }


    private FlagCardHandler flagCardHandler(){
        return new FlagCardHandler(this);
    }
    private static class FlagCardHandler extends SuspendCardHandler {
        public FlagCardHandler(CardBrowser browser) {
            super(browser);
        }
    }


    private MarkCardHandler markCardHandler() {
        return new MarkCardHandler(this);
    }
    private static class MarkCardHandler extends ListenerWithProgressBarCloseOnFalse<Void, PairWithBoolean<Card[]>> {
        public MarkCardHandler(CardBrowser browser) {
            super(browser);
        }

        @Override
        protected void actualOnValidPostExecute(CardBrowser browser, PairWithBoolean<Card[]> cards) {
            browser.updateCardsInList(CardUtils.getAllCards(CardUtils.getNotes(Arrays.asList(cards.other))));
            browser.hideProgressBar();
            browser.invalidateOptionsMenu();    // maybe the availability of undo changed
        }
    }



    private final DeleteNoteHandler mDeleteNoteHandler = new DeleteNoteHandler(this);
    private static class DeleteNoteHandler extends ListenerWithProgressBarCloseOnFalse<Card[], BooleanGetter> {
        public DeleteNoteHandler(CardBrowser browser) {
            super(browser);
        }

        private int mCardsDeleted = -1;

        @Override
        public void actualOnPreExecute(@NonNull CardBrowser browser) {
            super.actualOnPreExecute(browser);
            browser.invalidate();
        }

        @Override
        public void actualOnProgressUpdate(@NonNull CardBrowser browser, Card[] cards) {
            //we don't need to reorder cards here as we've already deselected all notes,
            browser.removeNotesView(cards, false);
            mCardsDeleted = cards.length;
        }


        @Override
        protected void actualOnValidPostExecute(CardBrowser browser, BooleanGetter result) {
            browser.hideProgressBar();
            browser.mActionBarTitle.setText(Integer.toString(browser.checkedCardCount()));
            browser.invalidateOptionsMenu();    // maybe the availability of undo changed
            // snackbar to offer undo
            String deletedMessage = browser.getResources().getQuantityString(R.plurals.card_browser_cards_deleted, mCardsDeleted, mCardsDeleted);
            browser.mUndoSnackbar = UIUtils.showSnackbar(browser, deletedMessage, SNACKBAR_DURATION,
                    R.string.undo, v -> TaskManager.launchCollectionTask(new CollectionTask.Undo(), browser.mUndoHandler),
                    browser.mCardsListView, null);
            browser.searchCards();
        }
    }



    private final UndoHandler mUndoHandler = new UndoHandler(this);
    private static class UndoHandler extends ListenerWithProgressBarCloseOnFalse<Card, BooleanGetter> {
        public UndoHandler(CardBrowser browser) {
            super(browser);
        }

        @Override
        public void actualOnValidPostExecute(CardBrowser browser, BooleanGetter result) {
            Timber.d("Card Browser - mUndoHandler.actualOnPostExecute(CardBrowser browser)");
            browser.hideProgressBar();
            // reload whole view
            browser.forceRefreshSearch();
            browser.endMultiSelectMode();
            browser.mCardsAdapter.notifyDataSetChanged();
            browser.updatePreviewMenuItem();
            browser.invalidateOptionsMenu();    // maybe the availability of undo changed
        }
    }



    private final SearchCardsHandler mSearchCardsHandler = new SearchCardsHandler(this);
    private class SearchCardsHandler extends ListenerWithProgressBar<List<CardCache>, List<CardCache>> {
        public SearchCardsHandler(CardBrowser browser) {
            super(browser);
        }


        @Override
        public void actualOnProgressUpdate(@NonNull CardBrowser browser, List<CardCache> cards) {
            // Need to copy the list into a new list, because the original list is modified, and
            // ListAdapter crash
            mCards.replaceWith(new ArrayList<>(cards));
            updateList();
        }


        @Override
        public void actualOnPostExecute(@NonNull CardBrowser browser, List<CardCache> result) {
            if (result != null) {
                mCards.replaceWith(result);
                updateList();
                handleSearchResult();
            }
            if (mShouldRestoreScroll) {
                mShouldRestoreScroll = false;
                int newPosition = getNewPositionOfSelectedCard();
                boolean isRestorePossible = (newPosition != CARD_NOT_AVAILABLE);
                if (isRestorePossible) {
                    autoScrollTo(newPosition);
                }
            }
            updatePreviewMenuItem();
            hideProgressBar();
        }


        private void handleSearchResult() {
            Timber.i("CardBrowser:: Completed doInBackgroundSearchCards Successfully");
            updateList();
            
            if ((mSearchView == null) || mSearchView.isIconified()) {
                return;
            }

            if (hasSelectedAllDecks()) {
                UIUtils.showSimpleSnackbar(CardBrowser.this, getSubtitleText(), true);
                return;
            }

            //If we haven't selected all decks, allow the user the option to search all decks.
            String displayText;
            if (getCardCount() == 0) {
                displayText = getString(R.string.card_browser_no_cards_in_deck, getSelectedDeckNameForUi());
            } else {
                displayText = getSubtitleText();
            }
            View root = CardBrowser.this.findViewById(R.id.root_layout);
            UIUtils.showSnackbar(CardBrowser.this,
                    displayText,
                    SNACKBAR_DURATION,
                    R.string.card_browser_search_all_decks,
                    (v) -> searchAllDecks(),
                    root,
                    null);

        }

        @Override
        public void actualOnCancelled(@NonNull CardBrowser browser) {
            super.actualOnCancelled(browser);
            hideProgressBar();
        }
    }


    private void saveScrollingState(int position) {
        mOldCardId = getCards().get(position).getId();
        mOldCardTopOffset = calculateTopOffset(position);
    }

    private void autoScrollTo(int newPosition) {
        mCardsListView.setSelectionFromTop(newPosition, mOldCardTopOffset);
        mPostAutoScroll = true;
    }

    private int calculateTopOffset(int cardPosition) {
        int firstVisiblePosition = mCardsListView.getFirstVisiblePosition();
        View v = mCardsListView.getChildAt(cardPosition - firstVisiblePosition);
        return (v == null) ? 0 : v.getTop();
    }

    private int getNewPositionOfSelectedCard() {
        if (mCards.size() == 0) {
            return CARD_NOT_AVAILABLE;
        }
        for (CardCache card : mCards) {
            if (card.getId() == mOldCardId) {
                return card.mPosition;
            }
        }
        return CARD_NOT_AVAILABLE;
    }

    public boolean hasSelectedAllDecks() {
        Long lastDeckId = getLastDeckId();
        return lastDeckId != null && lastDeckId == ALL_DECKS_ID;
    }


    public void searchAllDecks() {
        //all we need to do is select all decks
        selectAllDecks();
    }

    /**
     * Returns the current deck name, "All Decks" if all decks are selected, or "Unknown"
     * Do not use this for any business logic, as this will return inconsistent data
     * with the collection.
     */
    public String getSelectedDeckNameForUi() {
        try {
            Long lastDeckId = getLastDeckId();
            if (lastDeckId == null) {
                return getString(R.string.card_browser_unknown_deck_name);
            }
            if (lastDeckId == ALL_DECKS_ID) {
                return getString(R.string.card_browser_all_decks);
            }
            return getCol().getDecks().name(lastDeckId);
        } catch (Exception e) {
            Timber.w(e, "Unable to get selected deck name");
            return getString(R.string.card_browser_unknown_deck_name);
        }
    }

    private final RenderQAHandler mRenderQAHandler = new RenderQAHandler(this);
    private static class RenderQAHandler extends TaskListenerWithContext<CardBrowser, Integer, Pair<CardCollection<CardBrowser.CardCache>, List<Long>>>{
        public RenderQAHandler(CardBrowser browser) {
            super(browser);
        }

        @Override
        public void actualOnProgressUpdate(@NonNull CardBrowser browser, Integer value) {
            // Note: This is called every time a card is rendered.
            // It blocks the long-click callback while the task is running, so usage of the task should be minimized
            browser.mCardsAdapter.notifyDataSetChanged();
        }


        @Override
        public void actualOnPreExecute(@NonNull CardBrowser browser) {
            Timber.d("Starting Q&A background rendering");
        }


        @Override
        public void actualOnPostExecute(@NonNull CardBrowser browser, Pair<CardCollection<CardBrowser.CardCache>, List<Long>> value) {
            List<Long> cardsIdsToHide = value.second;
            if (cardsIdsToHide != null) {
                    try {
                        if (cardsIdsToHide.size() > 0) {
                            Timber.i("Removing %d invalid cards from view", cardsIdsToHide.size());
                            browser.removeNotesView(cardsIdsToHide, true);
                        }
                    } catch (Exception e) {
                        Timber.e(e, "failed to hide cards");
                    }
                browser.hideProgressBar();
                browser.mCardsAdapter.notifyDataSetChanged();
                Timber.d("Completed doInBackgroundRenderBrowserQA Successfuly");
            } else {
                // Might want to do something more proactive here like show a message box?
                Timber.e("doInBackgroundRenderBrowserQA was not successful... continuing anyway");
            }
        }


        @Override
        public void actualOnCancelled(@NonNull CardBrowser browser) {
            browser.hideProgressBar();
        }
    }



    private final CheckSelectedCardsHandler mCheckSelectedCardsHandler = new CheckSelectedCardsHandler(this);
    private static class CheckSelectedCardsHandler extends ListenerWithProgressBar<Void, Pair<Boolean, Boolean>> {
        public CheckSelectedCardsHandler(CardBrowser browser) {
            super(browser);
        }

        @Override
        public void actualOnPostExecute(@NonNull CardBrowser browser, Pair<Boolean, Boolean> result) {
            if (result == null) {
                return;
            }
            browser.hideProgressBar();

            if (browser.mActionBarMenu != null) {
                boolean hasUnsuspended = result.first;
                boolean hasUnmarked = result.second;

                setMenuIcons(browser, hasUnsuspended, hasUnmarked, browser.mActionBarMenu);
            }
        }


        protected void setMenuIcons(@NonNull Context browser, boolean hasUnsuspended, boolean hasUnmarked, @NonNull Menu actionBarMenu) {
            int title;
            int icon;
            if (hasUnsuspended) {
                title = R.string.card_browser_suspend_card;
                icon = R.drawable.ic_action_suspend;
            } else {
                title = R.string.card_browser_unsuspend_card;
                icon = R.drawable.ic_action_unsuspend;
            }
            MenuItem suspend_item = actionBarMenu.findItem(R.id.action_suspend_card);
            suspend_item.setTitle(browser.getString(title));
            suspend_item.setIcon(icon);

            if (hasUnmarked) {
                title = R.string.card_browser_mark_card;
                icon = R.drawable.ic_star_outline_white_24dp;
            } else {
                title = R.string.card_browser_unmark_card;
                icon = R.drawable.ic_star_white_24dp;
            }
            MenuItem mark_item = actionBarMenu.findItem(R.id.action_mark_card);
            mark_item.setTitle(browser.getString(title));
            mark_item.setIcon(icon);
        }


        @Override
        public void actualOnCancelled(@NonNull CardBrowser browser) {
            super.actualOnCancelled(browser);
            browser.hideProgressBar();
        }
    }


    private void closeCardBrowser(int result) {
        closeCardBrowser(result, null);
    }

    private void closeCardBrowser(int result, Intent data) {
        // Set result and finish
        setResult(result, data);
        finishWithAnimation(RIGHT);
    }

    /**
     * Render the second column whenever the user stops scrolling
     */
    private final class RenderOnScroll implements AbsListView.OnScrollListener {
        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            // Show the progress bar if scrolling to given position requires rendering of the question / answer
            int lastVisibleItem = firstVisibleItem + visibleItemCount;
            CardCollection<CardCache> cards = getCards();
            // List is never cleared, only reset to a new list. So it's safe here.
            int size = cards.size();
            if ((size > 0) && (firstVisibleItem < size) && ((lastVisibleItem - 1) < size)) {
                boolean firstLoaded = cards.get(firstVisibleItem).isLoaded();
                // Note: max value of lastVisibleItem is totalItemCount, so need to subtract 1
                boolean lastLoaded = cards.get(lastVisibleItem - 1).isLoaded();
                if (!firstLoaded || !lastLoaded) {
                    if (!mPostAutoScroll) {
                        showProgressBar();
                    }
                    // Also start rendering the items on the screen every 300ms while scrolling
                    long currentTime = SystemClock.elapsedRealtime ();
                    if ((currentTime - mLastRenderStart > 300 || lastVisibleItem >= totalItemCount)) {
                        mLastRenderStart = currentTime;
                        TaskManager.cancelAllTasks(CollectionTask.RenderBrowserQA.class);
                        TaskManager.launchCollectionTask(renderBrowserQAParams(firstVisibleItem, visibleItemCount, cards), mRenderQAHandler);
                    }
                }
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView listView, int scrollState) {
            // TODO: Try change to RecyclerView as currently gets stuck a lot when using scrollbar on right of ListView
            // Start rendering the question & answer every time the user stops scrolling
            if (mPostAutoScroll) {
                mPostAutoScroll = false;
            }
            if (scrollState == SCROLL_STATE_IDLE) {
                int startIdx = listView.getFirstVisiblePosition();
                int numVisible = listView.getLastVisiblePosition() - startIdx;
                TaskManager.launchCollectionTask(renderBrowserQAParams(startIdx - 5, 2 * numVisible + 5, getCards()), mRenderQAHandler);
            }
        }
    }


    @NonNull
    protected CollectionTask.RenderBrowserQA renderBrowserQAParams(int firstVisibleItem, int visibleItemCount, CardCollection<CardCache> cards) {
        return new CollectionTask.RenderBrowserQA(cards, firstVisibleItem, visibleItemCount, mColumn1Index, mColumn2Index);
    }


    private final class MultiColumnListAdapter extends BaseAdapter {
        private final int mResource;
        private Column[] mFromKeys;
        private final int[] mToIds;
        private float mOriginalTextSize = -1.0f;
        private final int mFontSizeScalePcent;
        private Typeface mCustomTypeface = null;
        private final LayoutInflater mInflater;

        public MultiColumnListAdapter(Context context, int resource, Column[] from, int[] to,
                                      int fontSizeScalePcent, String customFont) {
            mResource = resource;
            mFromKeys = from;
            mToIds = to;
            mFontSizeScalePcent = fontSizeScalePcent;
            if (!"".equals(customFont)) {
                mCustomTypeface = AnkiFont.getTypeface(context, customFont);
            }
            mInflater = LayoutInflater.from(context);
        }


        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the main container view if it doesn't already exist, and call bindView
            View v;
            if (convertView == null) {
                v = mInflater.inflate(mResource, parent, false);
                final int count = mToIds.length;
                final View[] columns = new View[count];
                for (int i = 0; i < count; i++) {
                    columns[i] = v.findViewById(mToIds[i]);
                }
                v.setTag(columns);
            } else {
                v = convertView;
            }
            bindView(position, v);
            return v;
        }


        private void bindView(final int position, final View v) {
            // Draw the content in the columns
            View[] columns = (View[]) v.getTag();
            final CardCache card = getCards().get(position);
            for (int i = 0; i < mToIds.length; i++) {
                TextView col = (TextView) columns[i];
                // set font for column
                setFont(col);
                // set text for column
                col.setText(card.getColumnHeaderText(mFromKeys[i]));
            }
            // set card's background color
            final int backgroundColor = Themes.getColorFromAttr(CardBrowser.this, card.getColor());
            v.setBackgroundColor(backgroundColor);
            // setup checkbox to change color in multi-select mode
            final CheckBox checkBox = v.findViewById(R.id.card_checkbox);
            // if in multi-select mode, be sure to show the checkboxes
            if(mInMultiSelectMode) {
                checkBox.setVisibility(View.VISIBLE);
                checkBox.setChecked(mCheckedCards.contains(card));
                // this prevents checkboxes from showing an animation from selected -> unselected when
                // checkbox was selected, then selection mode was ended and now restarted
                checkBox.jumpDrawablesToCurrentState();
            } else {
                checkBox.setChecked(false);
                checkBox.setVisibility(View.GONE);
            }
            // change bg color on check changed
            checkBox.setOnClickListener(view -> onCheck(position, v));
        }

        private void setFont(TextView v) {
            // Set the font and font size for a TextView v
            float currentSize = v.getTextSize();
            if (mOriginalTextSize < 0) {
                mOriginalTextSize = v.getTextSize();
            }
            // do nothing when pref is 100% and apply scaling only once
            if (mFontSizeScalePcent != 100 && Math.abs(mOriginalTextSize - currentSize) < 0.1) {
                // getTextSize returns value in absolute PX so use that in the setter
                v.setTextSize(TypedValue.COMPLEX_UNIT_PX, mOriginalTextSize * (mFontSizeScalePcent / 100.0f));
            }

            if (mCustomTypeface != null) {
                v.setTypeface(mCustomTypeface);
            }
        }

        public void setFromMapping(Column[] from) {
            mFromKeys = from;
            notifyDataSetChanged();
        }


        public Column[] getFromMapping() {
            return mFromKeys;
        }


        @Override
        public int getCount() {
            return getCardCount();
        }


        @Override
        public Object getItem(int position) {
            return getCards().get(position);
        }


        @Override
        public long getItemId(int position) {
            return position;
        }

    }


    private void onCheck(int position, View cell) {
        CheckBox checkBox = cell.findViewById(R.id.card_checkbox);
        CardCache card = getCards().get(position);

        if (checkBox.isChecked()) {
            mCheckedCards.add(card);
        } else {
            mCheckedCards.remove(card);
        }

       onSelectionChanged();
    }

    private void onSelectAll() {
        mCheckedCards.addAll(mCards.unsafeGetWrapped());
        onSelectionChanged();
    }

    private void onSelectNone() {
        mCheckedCards.clear();
        onSelectionChanged();
    }

    private void onSelectionChanged() {
        Timber.d("onSelectionChanged()");
        try {
            if (!mInMultiSelectMode && !mCheckedCards.isEmpty()) {
                //If we have selected cards, load multiselect
                loadMultiSelectMode();
            } else if (mInMultiSelectMode && mCheckedCards.isEmpty()) {
                //If we don't have cards, unload multiselect
                endMultiSelectMode();
            }

            //If we're not in mutliselect, we can select cards if there are cards to select
            if (!mInMultiSelectMode && this.mActionBarMenu != null) {
                MenuItem selectAll = mActionBarMenu.findItem(R.id.action_select_all);
                selectAll.setVisible(mCards != null && cardCount() != 0);
            }

            if (!mInMultiSelectMode) {
                return;
            }

            updateMultiselectMenu();
            mActionBarTitle.setText(Integer.toString(checkedCardCount()));
        } finally {
            mCardsAdapter.notifyDataSetChanged();
        }
    }


    /**
     * Reloads the data of the cards, taking on their current values from the database.
     */
    protected void reloadCards(Card[] cards) {
        if (cards == null || cards.length == 0) {
            return;
        }

        Set<Long> cardIds = new HashSet<>();
        for (Card c : cards) {
            cardIds.add(c.getId());
        }

        for (CardCache props : mCards) {
            if (cardIds.contains(props.getId())) {
                props.reload();
            }
        }
        mCardsAdapter.notifyDataSetChanged();
    }

    private CardCollection<CardCache> getCards() {
        mCards.ensureValidValue();
        return mCards;
    }

    private long[] getAllCardIds() {
        long[] l = new long[mCards.size()];
        for (int i = 0; i < mCards.size(); i++) {
            l[i] = mCards.get(i).getId();
        }
        return l;
    }

    // This could be better: use a wrapper class PositionAware<T> to store the position so it's
    // no longer a responsibility of CardCache and we can guarantee it's consistent just by using this collection
    /** A position-aware collection to ensure consistency between the position of items and the collection */
    public static class CardCollection<T extends PositionAware> implements Iterable<T> {
        private List<T> mWrapped = new ArrayList<>(0);

        public int size() {
            return mWrapped.size();
        }

        public T get(int index) {
            return mWrapped.get(index);
        }


        public void reset() {
            mWrapped = new ArrayList<>(0);
        }


        public void replaceWith(List<T> value) {
            mWrapped = value;
        }

        public void reverse() {
            Collections.reverse(mWrapped);
            int position = 0;
            for (int i = 0; i < mWrapped.size(); i++) {
                mWrapped.get(i).setPosition(position++);
            }
        }


        @NonNull
        @Override
        public Iterator<T> iterator() {
            return mWrapped.iterator();
        }

        public java.util.Collection<T> unsafeGetWrapped() {
            return mWrapped;
        }


        public void ensureValidValue() {
            if (mWrapped == null) {
                reset();
            }
        }


        public void clear() {
            mWrapped.clear();
        }
    }

    @VisibleForTesting
    interface PositionAware {
        int getPosition();
        void setPosition(int value);
    }

    public static class CardCache extends Card.Cache implements PositionAware {
        private boolean mLoaded = false;
        private Pair<String, String> mQa = null;
        private int mPosition;

        public CardCache(long id, Collection col, int position) {
            super(col, id);
            mPosition = position;
        }

        protected CardCache(CardCache cache, int position) {
            super(cache);
            mLoaded = cache.mLoaded;
            mQa = cache.mQa;
            mPosition = position;
        }

        public int getPosition() {
            return mPosition;
        }


        @Override
        public void setPosition(int value) {
            mPosition = value;
        }


        /** clear all values except ID.*/
        public void reload() {
            super.reload();
            mLoaded = false;
            mQa = null;
        }

        /**
         * Get the background color of items in the card list based on the Card
         * @return index into TypedArray specifying the background color
         */
        private int getColor() {
            int flag = getCard().userFlag();
            switch (flag) {
                case 1:
                    return R.attr.flagRed;
                case 2:
                    return R.attr.flagOrange;
                case 3:
                    return R.attr.flagGreen;
                case 4:
                    return R.attr.flagBlue;
                default:
                    if (getCard().note().hasTag("marked")) {
                        return R.attr.markedColor;
                    } else {
                        if (getCard().getQueue() == Consts.QUEUE_TYPE_SUSPENDED) {
                            return R.attr.suspendedColor;
                        } else {
                            return android.R.attr.colorBackground;
                        }
                    }
            }
        }

        public String getColumnHeaderText(Column key) {
            switch (key) {
            case FLAGS:
                return (Integer.valueOf(getCard().userFlag())).toString();
            case SUSPENDED:
                return getCard().getQueue() == Consts.QUEUE_TYPE_SUSPENDED ? "True": "False";
            case MARKED:
                return getCard().note().hasTag("marked") ? "marked" : null;
            case SFLD:
                return getCard().note().getSFld();
            case DECK:
                return getCol().getDecks().name(getCard().getDid());
            case TAGS:
                return getCard().note().stringTags();
            case CARD:
                return getCard().template().optString("name");
            case DUE:
                return getCard().getDueString();
            case EASE:
                if (getCard().getType() == Consts.CARD_TYPE_NEW) {
                    return AnkiDroidApp.getInstance().getString(R.string.card_browser_interval_new_card);
                } else {
                    return (getCard().getFactor()/10)+"%";
                }
            case CHANGED:
                return LanguageUtil.getShortDateFormatFromS(getCard().getMod());
            case CREATED:
                return LanguageUtil.getShortDateFormatFromMs(getCard().note().getId());
            case EDITED:
                return LanguageUtil.getShortDateFormatFromS(getCard().note().getMod());
            case INTERVAL:
                switch (getCard().getType()) {
                case Consts.CARD_TYPE_NEW:
                    return AnkiDroidApp.getInstance().getString(R.string.card_browser_interval_new_card);
                case Consts.CARD_TYPE_LRN :
                    return AnkiDroidApp.getInstance().getString(R.string.card_browser_interval_learning_card);
                default:
                    return Utils.roundedTimeSpanUnformatted(AnkiDroidApp.getInstance(), getCard().getIvl()*SECONDS_PER_DAY);
                }
            case LAPSES:
                return Integer.toString(getCard().getLapses());
            case NOTE_TYPE:
                return getCard().model().optString("name");
            case REVIEWS:
                return Integer.toString(getCard().getReps());
            case QUESTION:
                updateSearchItemQA();
                return mQa.first;
            case ANSWER:
                updateSearchItemQA();
                return mQa.second;
            default:
                return null;
            }
        }

        /** pre compute the note and question/answer.  It can safely
            be called twice without doing extra work. */
        public void load(boolean reload, int column1Index, int column2Index) {
            if (reload) {
                reload();
            }
            getCard().note();
            if (
                COLUMN1_KEYS[column1Index] == QUESTION ||
                COLUMN2_KEYS[column2Index] == QUESTION ||
                COLUMN2_KEYS[column2Index] == ANSWER
                // First column can not be the answer. If it were to
                // change, this code should also be changed.
                ) {
                updateSearchItemQA();
            }
            mLoaded = true;
        }

        public boolean isLoaded() {
            return mLoaded;
        }

        /**
           Reload question and answer. Use browser format. If it's empty
           uses non-browser format. If answer starts by question, remove
           question.
        */
        public void updateSearchItemQA() {
            if (mQa != null) {
                return;
            }
            // render question and answer
            Map<String, String> qa = getCard()._getQA(true, true);
            // Render full question / answer if the bafmt (i.e. "browser appearance") setting forced blank result
            if ("".equals(qa.get("q")) || "".equals(qa.get("a"))) {
                HashMap<String, String> qaFull = getCard()._getQA(true, false);
                if ("".equals(qa.get("q"))) {
                    qa.put("q", qaFull.get("q"));
                }
                if ("".equals(qa.get("a"))) {
                    qa.put("a", qaFull.get("a"));
                }
            }
            // update the original hash map to include rendered question & answer
            String q = qa.get("q");
            String a = qa.get("a");
            // remove the question from the start of the answer if it exists
            if (a.startsWith(q)) {
                a = a.replaceFirst(Pattern.quote(q), "");
            }
            a = formatQA(a, AnkiDroidApp.getInstance());
            q = formatQA(q, AnkiDroidApp.getInstance());
            mQa = new Pair<>(q, a);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            return getId() == ((CardCache) obj).getId();
        }

        @Override
        public int hashCode() {
            return Long.valueOf(getId()).hashCode();
        }
    }

    /**
     * Show/dismiss dialog when sd card is ejected/remounted (collection is saved by SdCardReceiver)
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(SdCardReceiver.MEDIA_EJECT)) {
                        finishWithoutAnimation();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT);
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }

    /**
     * The views expand / contract when switching between multi-select mode so we manually
     * adjust so that the vertical position of the given view is maintained
     */
    private void recenterListView(@NonNull View view) {
        final int position = mCardsListView.getPositionForView(view);
        // Get the current vertical position of the top of the selected view
        final int top = view.getTop();
        final Handler handler = new Handler();
        // Post to event queue with some delay to give time for the UI to update the layout
        handler.postDelayed(() -> {
            // Scroll to the same vertical position before the layout was changed
            mCardsListView.setSelectionFromTop(position, top);
        }, 10);
    }

    /**
     * Turn on Multi-Select Mode so that the user can select multiple cards at once.
     */
    private void loadMultiSelectMode() {
        if (mInMultiSelectMode) {
            return;
        }
        Timber.d("loadMultiSelectMode()");
        // set in multi-select mode
        mInMultiSelectMode = true;
        // show title and hide spinner
        mActionBarTitle.setVisibility(View.VISIBLE);
        mActionBarTitle.setText(String.valueOf(checkedCardCount()));
        mActionBarSpinner.setVisibility(View.GONE);
        // reload the actionbar using the multi-select mode actionbar
        supportInvalidateOptionsMenu();
    }

    /**
     * Turn off Multi-Select Mode and return to normal state
     */
    private void endMultiSelectMode() {
        Timber.d("endMultiSelectMode()");
        mCheckedCards.clear();
        mInMultiSelectMode = false;
        // If view which was originally selected when entering multi-select is visible then maintain its position
        View view = mCardsListView.getChildAt(mLastSelectedPosition - mCardsListView.getFirstVisiblePosition());
        if (view != null) {
            recenterListView(view);
        }
        // update adapter to remove check boxes
        mCardsAdapter.notifyDataSetChanged();
        // update action bar
        supportInvalidateOptionsMenu();
        mActionBarSpinner.setVisibility(View.VISIBLE);
        mActionBarTitle.setVisibility(View.GONE);
    }

    @VisibleForTesting
    public int checkedCardCount() {
        return mCheckedCards.size();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    boolean isInMultiSelectMode() {
        return mInMultiSelectMode;
    }

    @VisibleForTesting()
    long cardCount() {
        return mCards.size();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
     boolean isShowingSelectAll() {
        return mActionBarMenu != null && mActionBarMenu.findItem(R.id.action_select_all).isVisible();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    boolean isShowingSelectNone() {
        return mActionBarMenu != null &&
                mActionBarMenu.findItem(R.id.action_select_none) != null && //
                mActionBarMenu.findItem(R.id.action_select_none).isVisible();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    void clearCardData(int position) {
        mCards.get(position).reload();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    void rerenderAllCards() {
        TaskManager.launchCollectionTask(renderBrowserQAParams(0, mCards.size()-1, getCards()), mRenderQAHandler);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    long[] getCardIds() {
        CardCache[] cardsCopy = mCards.unsafeGetWrapped().toArray(new CardCache[0]);
        long[] ret = new long[cardsCopy.length];
        for (int i = 0; i < cardsCopy.length; i++) {
            ret[i] = cardsCopy[i].getId();
        }
        return ret;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    void checkCardsAtPositions(int... positions) {
        for (int position : positions) {
            if (position >= mCards.size()) {
                throw new IllegalStateException(
                        String.format(Locale.US, "Attempted to check card at index %d. %d cards available",
                                position, mCards.size()));
            }
            mCheckedCards.add(getCards().get(position));
        }
        onSelectionChanged();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    boolean hasCheckedCardAtPosition(int i) {
        return mCheckedCards.contains(getCards().get(i));
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public int getChangeDeckPositionFromId(long deckId) {
        List<Deck> decks = getValidDecksForChangeDeck();
        for (int i = 0; i < decks.size(); i++) {
            Deck deck = decks.get(i);
            if (deck.getLong("id") == deckId) {
                return i;
            }
        }
        throw new IllegalStateException(String.format(Locale.US, "Deck %d not found", deckId));
    }


    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public List<Long> getCheckedCardIds() {
        List<Long> cardIds = new ArrayList<>(mCheckedCards.size());
        for (CardCache card : mCheckedCards) {
            long id = card.getId();
            cardIds.add(id);
        }
        return cardIds;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE) //should only be called from changeDeck()
    void executeChangeCollectionTask(List<Long> ids, long newDid) {
        mNewDid = newDid; //line required for unit tests, not necessary, but a noop in regular call.
        TaskManager.launchCollectionTask(
                new CollectionTask.ChangeDeckMulti(ids, newDid),
                new ChangeDeckHandler(this));
    }


    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public CardCache getPropertiesForCardId(long cardId) {
        for (CardCache props : mCards) {
            long id = props.getId();
            if (id == cardId) {
                return props;
            }
        }
        throw new IllegalStateException(String.format(Locale.US, "Card '%d' not found", cardId));
    }


    @VisibleForTesting
    void filterByTag(String... tags) {
        filterByTag(Arrays.asList(tags), 0);
    }

    @VisibleForTesting
    void filterByFlag(int flag) {
        mCurrentFlag = flag;
        filterByFlag();
    }

    @VisibleForTesting
    void replaceSelectionWith(int[] positions) {
        mCheckedCards.clear();
        checkCardsAtPositions(positions);
    }
}
