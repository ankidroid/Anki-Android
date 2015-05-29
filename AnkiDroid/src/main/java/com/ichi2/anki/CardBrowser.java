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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
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
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.CardBrowserContextMenu;
import com.ichi2.anki.dialogs.CardBrowserMySearchesDialog;
import com.ichi2.anki.dialogs.CardBrowserOrderDialog;
import com.ichi2.anki.dialogs.TagsDialog;
import com.ichi2.anki.dialogs.TagsDialog.TagsDialogListener;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.widgets.DeckDropDownAdapter;
import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.upgrade.Upgrade;
import com.ichi2.widget.WidgetStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import timber.log.Timber;

public class CardBrowser extends NavigationDrawerActivity implements
        DeckDropDownAdapter.SubtitleListener {

    // private List<Long> mCardIds = new ArrayList<Long>();
    private ArrayList<HashMap<String, String>> mCards;
    // private ArrayList<HashMap<String, String>> mAllCards;
    private HashMap<String, String> mDeckNames;
    private ArrayList<JSONObject> mDropDownDecks;
    private SearchView mSearchView;
    private ListView mCardsListView;
    private Spinner mCardsColumn1Spinner;
    private Spinner mCardsColumn2Spinner;
    private MultiColumnListAdapter mCardsAdapter;
    private String mSearchTerms;
    private String mRestrictOnDeck;

    private MenuItem mSearchItem;
    private MenuItem mSaveSearchItem;
    private MenuItem mMySearchesItem;

    private MaterialDialog mProgressDialog;
    public static Card sCardBrowserCard;
    public static boolean sSearchCancelled = false;

    private int mPositionInCardsList;

    private int mOrder;
    private boolean mOrderAsc;
    private int mColumn2Index;

    private static final int DIALOG_TAGS = 3;

    private static final int BACKGROUND_NORMAL = 0;
    private static final int BACKGROUND_MARKED = 1;
    private static final int BACKGROUND_SUSPENDED = 2;
    private static final int BACKGROUND_MARKED_SUSPENDED = 3;

    private static final int EDIT_CARD = 0;
    private static final int ADD_NOTE = 1;
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
    // list of available keys in mCards corresponding to the column names in R.array.browser_column2_headings.
    // Note: the last 6 are currently hidden
    private static final String[] COLUMN_KEYS = {"answer",
        "card",
        "deck",
        "note",
        "question",
        "tags",
        "lapses",
        "reviews",
        "changed",
        "created",
        "due",
        "ease",
        "edited",
        "interval"};

    private int[] mBackground;

    private ActionBar mActionBar;
    private DeckDropDownAdapter mDropDownAdapter;
    private Spinner mActionBarSpinner;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private MaterialDialog.ListCallback mContextMenuListener = new MaterialDialog.ListCallback() {
        @Override
        public void onSelection(MaterialDialog materialDialog, View view, int which,
        CharSequence charSequence) {
            if (getCards().size() == 0) {
                // Don't do anything if mCards empty
                searchCards();
                return;
            }
            switch (which) {
                case CardBrowserContextMenu.CONTEXT_MENU_MARK:
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD,
                            mUpdateCardHandler,
                            new DeckTask.TaskData(getCol(), getCol().getSched(), getCol().getCard(Long.parseLong(getCards().get(
                                    mPositionInCardsList).get("id"))), 0));
                    return;

                case CardBrowserContextMenu.CONTEXT_MENU_SUSPEND:
                    DeckTask.launchDeckTask(
                            DeckTask.TASK_TYPE_DISMISS_NOTE,
                            mSuspendCardHandler,
                            new DeckTask.TaskData(getCol(), getCol().getSched(), getCol().getCard(Long.parseLong(getCards().get(
                                    mPositionInCardsList).get("id"))), 1));
                    return;

                case CardBrowserContextMenu.CONTEXT_MENU_DELETE:
                    Resources res = getResources();
                    Drawable icon = res.getDrawable(R.drawable.ic_warning_black_36dp);
                    icon.setAlpha(Themes.ALPHA_ICON_ENABLED_DARK);
                    new MaterialDialog.Builder(CardBrowser.this)
                            .title(res.getString(R.string.delete_card_title))
                            .icon(icon)
                            .content(res.getString(R.string.delete_card_message, getCards().get(mPositionInCardsList)
                                    .get("sfld")))
                            .positiveText(res.getString(R.string.dialog_positive_delete))
                            .negativeText(res.getString(R.string.dialog_cancel))
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onPositive(MaterialDialog dialog) {
                                    Card card = getCol().getCard(Long.parseLong(getCards().get(mPositionInCardsList).get("id")));
                                    deleteNote(card);
                                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDeleteNoteHandler,
                                                            new DeckTask.TaskData(getCol(), getCol().getSched(), card, 3));
                                }
                            })
                            .build().show();
                    return;

                case CardBrowserContextMenu.CONTEXT_MENU_DETAILS:
                    Long cardId = Long.parseLong(getCards().get(mPositionInCardsList).get("id"));
                    Intent previewer = new Intent(CardBrowser.this, Previewer.class);
                    previewer.putExtra("currentCardId", cardId);
                    startActivityWithoutAnimation(previewer);
            }
        }
    };


    private MaterialDialog.ListCallbackSingleChoice mOrderDialogListener =
            new MaterialDialog.ListCallbackSingleChoice() {
        @Override
        public boolean onSelection(MaterialDialog materialDialog, View view, int which,
                CharSequence charSequence) {
            if (which != mOrder) {
                mOrder = which;
                mOrderAsc = false;
                try {
                    if (mOrder == 0) {
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
                    // default to descending for non-text fields
                    if (fSortTypes[mOrder].equals("noteFld")) {
                        mOrderAsc = true;
                    }
                    getCol().getConf().put("sortBackwards", mOrderAsc);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                searchCards();
            } else if (which != CARD_ORDER_NONE) {
                mOrderAsc = !mOrderAsc;
                try {
                    getCol().getConf().put("sortBackwards", mOrderAsc);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                Collections.reverse(mCards);
                updateList();
            }
            return true;
        }
    };

    private CardBrowserMySearchesDialog.MySearchesDialogListener mMySearchesDialogListener =
            new CardBrowserMySearchesDialog.MySearchesDialogListener() {
        @Override
        public void OnSelection(String searchName) {
            JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
            if (savedFiltersObj != null) {
                mSearchTerms = savedFiltersObj.optString(searchName);
                mSearchView.setQuery(mSearchTerms, false);
                MenuItemCompat.expandActionView(mSearchItem);
                searchCards();
            }
        }

        @Override
        public void OnRemoveSearch(String searchName) {
            try {
                JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
                if (savedFiltersObj != null && savedFiltersObj.has(searchName)) {
                    savedFiltersObj.remove(searchName);
                    getCol().getConf().put("savedFilters", savedFiltersObj);
                    getCol().flush();
                    if (savedFiltersObj.length() == 0) {
                        mMySearchesItem.setVisible(false);
                    }
                }

            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void OnSaveSearch(String searchName, String searchTerms) {
            if (TextUtils.isEmpty(searchName)) {
                Themes.showThemedToast(CardBrowser.this,
                        getString(R.string.card_browser_list_my_searches_new_search_error_empty_name), true);
                return;
            }
            try {
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
                    Themes.showThemedToast(CardBrowser.this,
                            getString(R.string.card_browser_list_my_searches_new_search_error_dup), true);
                }
                if (should_save) {
                    getCol().getConf().put("savedFilters", savedFiltersObj);
                    getCol().flush();
                    mSearchView.setQuery("", false);
                    mMySearchesItem.setVisible(true);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    };

    /** Returns the navdrawer item that corresponds to this Activity. */
    @Override
    protected int getSelfNavDrawerItem() {
        return DRAWER_BROWSER;
    }


    private void onSearch() {
        mSearchTerms = mSearchView.getQuery().toString();
        if (mSearchTerms.length() == 0) {
            mSearchView.setQueryHint(getResources().getString(R.string.downloaddeck_search));
        }
        searchCards();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        Timber.d("onCreate()");
        View mainView = getLayoutInflater().inflate(R.layout.card_browser, null);
        setContentView(mainView);
        Themes.setContentStyle(mainView, Themes.CALLER_CARDBROWSER);
        
        initNavigationDrawer(mainView);
        
        startLoadingCollection();
    }


    // Finish initializing the activity after the collection has been correctly loaded
    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        Timber.d("onCollectionLoaded()");
        mDeckNames = new HashMap<String, String>();
        for (long did : getCol().getDecks().allIds()) {
            mDeckNames.put(String.valueOf(did), getCol().getDecks().name(did));
        }
        registerExternalStorageListener();

        mBackground = Themes.getCardBrowserBackground();

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());

        // Add drop-down menu to select deck to action bar.
        mDropDownDecks = getCol().getDecks().allSorted();
        mDropDownAdapter = new DeckDropDownAdapter(this, mDropDownDecks);
        mActionBar = getSupportActionBar();
        mActionBar.setDisplayShowTitleEnabled(false);
        mActionBarSpinner = (Spinner) findViewById(R.id.toolbar_spinner);
        mActionBarSpinner.setAdapter(mDropDownAdapter);
        mActionBarSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectDropDownItem(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });
        mActionBarSpinner.setVisibility(View.VISIBLE);

        try {
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
            mOrderAsc = Upgrade.upgradeJSONIfNecessary(getCol(), getCol().getConf(), "sortBackwards", false);
            // default to descending for non-text fields
            if (fSortTypes[mOrder].equals("noteFld")) {
                mOrderAsc = !mOrderAsc;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        
        mCards = new ArrayList<HashMap<String, String>>();
        mCardsListView = (ListView) findViewById(R.id.card_browser_list);
        // Create a spinner for column1, but without letting the user change column
        // TODO: Maybe allow column1 to be changed as well, but always make default sfld
        mCardsColumn1Spinner = (Spinner) findViewById(R.id.browser_column1_spinner);
        ArrayAdapter<CharSequence> column1Adapter = ArrayAdapter.createFromResource(this,
                R.array.browser_column1_headings, android.R.layout.simple_spinner_item);
        column1Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCardsColumn1Spinner.setAdapter(column1Adapter);
        mCardsColumn1Spinner.setClickable(false); // We disable and set plain background since it only has 1 item
        // Load default value for column2 selection
        mColumn2Index = AnkiDroidApp.getSharedPrefs(getBaseContext()).getInt("cardBrowserColumn2", 0);
        // Setup the column 2 heading as a spinner so that users can easily change the column type
        mCardsColumn2Spinner = (Spinner) findViewById(R.id.browser_column2_spinner);
        ArrayAdapter<CharSequence> column2Adapter = ArrayAdapter.createFromResource(this,
                R.array.browser_column2_headings, android.R.layout.simple_spinner_item);
        column2Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCardsColumn2Spinner.setAdapter(column2Adapter);
        // Create a new list adapter with updated column map any time the user changes the column
        mCardsColumn2Spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                // If a new column was selected then change the key used to map from mCards to the column TextView
                if (pos != mColumn2Index) {
                    mColumn2Index = pos;
                    AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit()
                            .putInt("cardBrowserColumn2", mColumn2Index).commit();
                    String[] fromMap = mCardsAdapter.getFromMapping();
                    fromMap[1] = COLUMN_KEYS[mColumn2Index];
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
        // make a new list adapter mapping the data in mCards to column1 and column2 of R.layout.card_item_browser
        mCardsAdapter = new MultiColumnListAdapter(
                this,
                mCards,
                R.layout.card_item_browser,
                new String[] {"sfld", COLUMN_KEYS[mColumn2Index]},
                new int[] {R.id.card_sfld, R.id.card_column2},
                "flags",
                sflRelativeFontSize,
                sflCustomFont);
        // link the adapter to the main mCardsListView
        mCardsListView.setAdapter(mCardsAdapter);
        // make the second column load dynamically when scrolling
        mCardsListView.setOnScrollListener(new RenderOnScroll());
        // set the spinner index
        mCardsColumn2Spinner.setSelection(mColumn2Index);


        mCardsListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // load up the card selected on the list
                mPositionInCardsList = position;
                long cardId = Long.parseLong(mCards.get(mPositionInCardsList).get("id"));
                sCardBrowserCard = getCol().getCard(cardId);
                // start note editor using the card we just loaded
                Intent editCard = new Intent(CardBrowser.this, NoteEditor.class);
                editCard.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_CARDBROWSER_EDIT);
                editCard.putExtra(NoteEditor.EXTRA_CARD_ID, sCardBrowserCard.getId());
                startActivityForResultWithAnimation(editCard, EDIT_CARD, ActivityTransitionAnimation.LEFT);
            }
        });
        mCardsListView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
                mPositionInCardsList = position;
                HashMap<String, String> card = mCards.get(mPositionInCardsList);
                int flags = Integer.parseInt(card.get("flags"));
                String cardName = card.get("sfld");
                boolean isMarked = (flags == 2 || flags == 3);
                boolean isSuspended = (flags == 1 || flags == 3);
                showDialogFragment(CardBrowserContextMenu
                        .newInstance(cardName, isMarked, isSuspended, mContextMenuListener));
                return true;
            }
        });

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // initialize mSearchTerms to a default value
        mSearchTerms = "";

        // set the currently selected deck
        if (!sIsWholeCollection) {
            String currentDeckName;
            try {
                currentDeckName = getCol().getDecks().current().getString("name");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            for (int dropDownDeckIdx = 0; dropDownDeckIdx < mDropDownDecks.size(); dropDownDeckIdx++) {
                JSONObject deck = mDropDownDecks.get(dropDownDeckIdx);
                String deckName;
                try {
                    deckName = deck.getString("name");
                } catch (JSONException e) {
                    throw new RuntimeException();
                }
                if (deckName.equals(currentDeckName)) {
                    selectDropDownItem(dropDownDeckIdx + 1);
                    break;
                }
            }
        }
        hideProgressBar();
    }


    @Override
    protected void onStop() {
        Timber.d("onStop()");
        // cancel rendering the question and answer, which has shared access to mCards
        DeckTask.cancelTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA);
        super.onStop();
        if (!isFinishing()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground(this);
        }
    }


    @Override
    protected void onDestroy() {
        Timber.d("onDestroy()");
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Timber.i("CardBrowser:: CardBrowser - onBackPressed()");
            Intent data = new Intent();
            if (getIntent().hasExtra("selectedDeck")) {
                data.putExtra("originalDeck", getIntent().getLongExtra("selectedDeck", 0L));
            }
            closeCardBrowser(Activity.RESULT_OK, data);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    protected void onResume() {
        Timber.d("onResume()");
        super.onResume();
        selectNavigationItem(DRAWER_BROWSER);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.card_browser, menu);
        mSaveSearchItem = menu.findItem(R.id.action_save_search);
        mSaveSearchItem.setVisible(false); //the searchview's query always starts empty.
        mMySearchesItem = menu.findItem(R.id.action_list_my_searches);
        JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
        mMySearchesItem.setVisible(savedFiltersObj != null && savedFiltersObj.length() > 0);
        mSearchItem = menu.findItem(R.id.action_search);
        MenuItemCompat.setOnActionExpandListener(mSearchItem, new MenuItemCompat.OnActionExpandListener() {
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
                return true;
            }
        });
        mSearchView = (SearchView) MenuItemCompat.getActionView(mSearchItem);
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
        mSearchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Provide SearchView with the previous search terms
                mSearchView.setQuery(mSearchTerms, false);
            }
        });
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
        if (getDrawerToggle().onOptionsItemSelected(item)) {
            return true;
        }       
        
        switch (item.getItemId()) {

            case R.id.action_add_card_from_card_browser:
                Intent intent = new Intent(CardBrowser.this, NoteEditor.class);
                intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_CARDBROWSER_ADD);
                startActivityForResultWithAnimation(intent, ADD_NOTE, ActivityTransitionAnimation.LEFT);
                return true;

            case R.id.action_save_search:
                String searchTerms = mSearchView.getQuery().toString();
                showDialogFragment(CardBrowserMySearchesDialog.newInstance(null, mMySearchesDialogListener,
                        searchTerms, CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_SAVE));
                return true;

            case R.id.action_list_my_searches:
                JSONObject savedFiltersObj = getCol().getConf().optJSONObject("savedFilters");
                HashMap<String, String> savedFilters = new HashMap<String, String>();
                if (savedFiltersObj != null) {
                    Iterator<String> it = savedFiltersObj.keys();
                    while (it.hasNext()) {
                        String searchName = it.next();
                        savedFilters.put(searchName, savedFiltersObj.optString(searchName));
                    }
                }
                showDialogFragment(CardBrowserMySearchesDialog.newInstance(savedFilters, mMySearchesDialogListener,
                        "", CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_LIST));
                return true;
            case R.id.action_sort_by_size:
                showDialogFragment(CardBrowserOrderDialog
                        .newInstance(mOrder, mOrderAsc, mOrderDialogListener));
                return true;

            case R.id.action_show_marked:
                mSearchTerms = "tag:marked";
                mSearchView.setQuery("", false);
                mSearchView.setQueryHint(getResources().getString(R.string.card_browser_show_marked));
                searchCards();
                return true;

            case R.id.action_show_suspended:
                mSearchTerms = "is:suspended";
                mSearchView.setQuery("", false);
                mSearchView.setQueryHint(getResources().getString(R.string.card_browser_show_suspended));
                searchCards();
                return true;

            case R.id.action_search_by_tag:
                showDialogFragment(DIALOG_TAGS);
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
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
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler,
                    new DeckTask.TaskData(getCol(), getCol().getSched(), sCardBrowserCard, false));
        } else if (requestCode == ADD_NOTE && resultCode == RESULT_OK) {
            if (mSearchView != null) {
                mSearchTerms = mSearchView.getQuery().toString();
                searchCards();
            } else {
                Timber.w("Note was added from browser and on return mSearchView == null");
            }

        }

        if (requestCode == EDIT_CARD &&  data!=null && data.hasExtra("reloadRequired")) {
            // if reloadRequired flag was sent from note editor then reload card list
            searchCards();
        }
    }

    private DialogFragment showDialogFragment(int id) {
        DialogFragment dialogFragment = null;
        String tag = null;
        switch(id) {
            case DIALOG_TAGS:
                TagsDialog dialog = com.ichi2.anki.dialogs.TagsDialog.newInstance(
                    TagsDialog.TYPE_FILTER_BY_TAG, new ArrayList<String>(), new ArrayList<String>(getCol().getTags().all()));
                dialog.setTagsDialogListener(new TagsDialogListener() {                    
                    @Override
                    public void onPositive(List<String> selectedTags, int option) {
                        mSearchView.setQuery("", false);
                        String tags = selectedTags.toString();
                        mSearchView.setQueryHint(getResources().getString(R.string.card_browser_tags_shown,
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
                            sb.append("tag:").append(tag).append(" ");
                            i++;
                        }
                        if (i > 0) {
                            sb.append(")"); // Only if we added anything to the tag list
                        }
                        mSearchTerms = sb.toString();
                        searchCards();
                    }
                });
                dialogFragment = dialog;
                break;
            default:
                break;
        }
        

        dialogFragment.show(getSupportFragmentManager(), tag);
        return dialogFragment;
    }


    public void selectDropDownItem(int position) {
        // cancel rendering the question and answer, which has shared access to mCards
        DeckTask.cancelTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA);

        mActionBarSpinner.setSelection(position);
        if (position == 0) {
            sIsWholeCollection = true;
            mRestrictOnDeck = "";
        } else {
            sIsWholeCollection = false;
            JSONObject deck = mDropDownDecks.get(position - 1);
            String deckName;
            try {
                deckName = deck.getString("name");
            } catch (JSONException e) {
                throw new RuntimeException();
            }
            try {
                getCol().getDecks().select(deck.getLong("id"));
            } catch (JSONException e) {
                Timber.e(e, "Could not get ID from deck");
            }
            mRestrictOnDeck = "deck:\"" + deckName + "\" ";
        }
        searchCards();
    }

    private void searchCards() {
        String searchText;
        if (mSearchTerms.contains("deck:")) {
            searchText = mSearchTerms;
        } else {
            searchText = mRestrictOnDeck + mSearchTerms;
        }
        if (colIsOpen()) {
            // clear the existing card list
            getCards().clear();
            mCardsAdapter.notifyDataSetChanged();
            // Perform database query to get all card ids / sfld. Shows "filtering cards..." progress message
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SEARCH_CARDS, mSearchCardsHandler, new DeckTask.TaskData(
                    new Object[] { getCol(), mDeckNames, searchText, ((mOrder != CARD_ORDER_NONE)) }));
        }
    }


    private void updateList() {
        mCardsAdapter.notifyDataSetChanged();
        mDropDownAdapter.notifyDataSetChanged();
    }

    /**
     * @return text to be used in the subtitle of the drop-down deck selector
     */
    public String getSubtitleText() {
        int count = getCards().size();
        return getResources().getQuantityString(R.plurals.card_browser_subtitle, count, count);
    }


    private int getPosition(ArrayList<HashMap<String, String>> list, long cardId) {
        String cardid = Long.toString(cardId);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).get("id").equals(cardid)) {
                return i;
            }
        }
        return -1;
    }


    private void updateCardInList(Card card, String updatedCardTags) {
        Note note = card.note();
        int pos;
        for (Card c : note.cards()) {
            // get position in the mCards search results HashMap
            pos = getPosition(getCards(), c.getId());
            if (pos < 0 || pos >= getCards().size()) {
                continue;
            }
            // update tags
            if (updatedCardTags != null) {
                getCards().get(pos).put("tags", updatedCardTags);
            }
            // update sfld
            String sfld = note.getSFld();
            getCards().get(pos).put("sfld", sfld);
            // update Q & A etc
            updateSearchItemQA(getCards().get(pos), c);
            // update deck
            String deckName;
            try {
                deckName = getCol().getDecks().get(card.getDid()).getString("name");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            getCards().get(pos).put("deck", deckName);
            // update flags (marked / suspended / etc) which determine color
            String flags = Integer.toString((c.getQueue() == -1 ? 1 : 0) + (note.hasTag("marked") ? 2 : 0));
            getCards().get(pos).put("flags", flags);
        }
        updateList();
    }


    public static void updateSearchItemQA(HashMap<String, String> item, Card c) {
        // render question and answer
        HashMap<String, String> qa = c._getQA(true, true);
        // Render full answer if the bafmt (i.e. "browser appearance") setting forced blank result
        if (qa.get("a").equals("")) {
            qa = c._getQA(true, false);
        }
        // update the original hash map to include rendered question & answer
        String q = qa.get("q");
        String a = qa.get("a");
        // remove the question from the start of the answer if it exists
        if (a.startsWith(q)) {
            a = a.replaceFirst(Pattern.quote(q), "");
        }
        // put all of the fields in except for those that have already been pulled out straight from the
        // database
        item.put("answer", formatQA(a));
        item.put("card", c.template().optString("name"));
        // item.put("changed",strftime("%Y-%m-%d", localtime(c.getMod())));
        // item.put("created",strftime("%Y-%m-%d", localtime(c.note().getId()/1000)));
        // item.put("due",getDueString(c));
        // item.put("ease","");
        // item.put("edited",strftime("%Y-%m-%d", localtime(c.note().getMod())));
        // item.put("interval","");
        item.put("lapses", Integer.toString(c.getLapses()));
        item.put("note", c.model().optString("name"));
        item.put("question", formatQA(q));
        item.put("reviews", Integer.toString(c.getReps()));
    }


    private static String formatQA(String txt) {
        /* Strips all formatting from the string txt for use in displaying question/answer in browser */
        String s = txt.replace("<br>", " ");
        s = s.replace("<br />", " ");
        s = s.replace("<div>", " ");
        s = s.replace("\n", " ");
        s = s.replaceAll("\\[sound:[^]]+\\]", "");
        s = s.replaceAll("\\[\\[type:[^]]+\\]\\]", "");
        s = Utils.stripHTMLMedia(s);
        s = s.trim();
        return s;
    }


    private void deleteNote(Card card) {
        ArrayList<Card> cards = card.note().cards();
        int pos;
        for (Card c : cards) {
            pos = getPosition(getCards(), c.getId());
            if (pos >= 0 && pos < getCards().size()) {
                getCards().remove(pos);
            }
        }
        // Delete itself if not deleted
        pos = getPosition(getCards(), card.getId());
        if (pos >= 0 && pos < getCards().size()) {
            getCards().remove(pos);
        }
        updateList();
    }

    private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            if (mProgressDialog == null) {
                mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
                        res.getString(R.string.saving_changes), false);
            }
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            // // Update list if search involved marked
            // if (fSearchMarkedPattern.matcher(mSearchTerms).find()) {
            // updateCardsList();
            // }
            updateCardInList(values[0].getCard(), values[0].getString());
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            Timber.d("Card Browser - mUpdateCardHandler.onPostExecute()");
            if (!result.getBoolean()) {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
            dismissProgressDialog();
        }


        @Override
        public void onCancelled() {
        }
    };

    private DeckTask.TaskListener mSuspendCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res.getString(R.string.saving_changes),
                    false);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (result.getBoolean() && mCards != null) {
                // // Update list if search on suspended
                // if (fSearchSuspendedPattern.matcher(mSearchTerms).find()) {
                // updateCardsList();
                // }
                updateCardInList(getCol().getCard(Long.parseLong(mCards.get(mPositionInCardsList).get("id"))), null);
            } else {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
            dismissProgressDialog();
        }


        @Override
        public void onCancelled() {
        }
    };

    private DeckTask.TaskListener mDeleteNoteHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res.getString(R.string.saving_changes),
                    false);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            dismissProgressDialog();
        }


        @Override
        public void onCancelled() {
        }
    };

    private DeckTask.TaskListener mSearchCardsHandler = new DeckTask.TaskListener() {
        @Override
        public void onProgressUpdate(TaskData... values) {
            if (mCards != null && values[0]!= null) {
                mCards.clear();
                mCards.addAll(values[0].getCards());
                updateList();
            }
        }


        @Override
        public void onPreExecute() {
            Resources res = getResources();
            sSearchCancelled = false;
            if (mProgressDialog == null) {
                mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
                        res.getString(R.string.card_browser_filtering_cards), true,
                        new DialogInterface.OnCancelListener(){
                            @Override
                            public void onCancel(DialogInterface dialog){
                                Timber.i("CardBrowser:: Search cards dialog dismissed");
                                DeckTask.cancelTask(DeckTask.TASK_TYPE_SEARCH_CARDS);
                                sSearchCancelled = true;
                            }
                });
            } else {
                mProgressDialog.setContent(res.getString(R.string.card_browser_filtering_cards));
                mProgressDialog.show();
            }

        }


        @Override
        public void onPostExecute(TaskData result) {            
            if (result != null && mCards != null) {
                Timber.i("CardBrowser:: Completed doInBackgroundSearchCards Successfuly");
                updateList();
                dismissProgressDialog();
                // After the initial searchCards query, start rendering the question and answer in the background
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA, mRenderQAHandler, new DeckTask.TaskData(
                        new Object[] { getCol(), mCards, 0, 100 }));
            } else {
                // this is a hack -- see DeckTask.launchDeckTask for more info
                Timber.w("doInBackgroundSearchCards onPostExecute() called but result was null");
                sSearchCancelled = false;
            }
        }
        
        @Override
        public void onCancelled(){
            // reset the hacky static variable which Finder is listening to check if main thread has requested cancellation
            Timber.d("doInBackgroundSearchCards onCancelled() called");
            sSearchCancelled = false;
        }
    };

    private DeckTask.TaskListener mRenderQAHandler = new DeckTask.TaskListener() {
        @Override
        public void onProgressUpdate(TaskData... values) {
            mCardsAdapter.notifyDataSetChanged();
        }


        @Override
        public void onPreExecute() {
        }


        @Override
        public void onPostExecute(TaskData result) {
            if (result != null) {
                mCardsAdapter.notifyDataSetChanged();
                Timber.d("Completed doInBackgroundRenderBrowserQA Successfuly");
            } else {
                // Might want to do something more proactive here like show a message box?
                Timber.e("doInBackgroundRenderBrowserQA was not successful... continuing anyway");
            }
        }


        @Override
        public void onCancelled() {
        }
    };


    private void closeCardBrowser(int result) {
        closeCardBrowser(result, null);
    }

    private void closeCardBrowser(int result, Intent data) {
        setResult(result, data);
        finishWithAnimation(ActivityTransitionAnimation.RIGHT);
    }

    /**
     * Render the second column whenever the user stops scrolling
     */
    private final class RenderOnScroll implements AbsListView.OnScrollListener {
        @Override
        public void onScroll(AbsListView view, int firstVisibleItem,
                             int visibleItemCount, int totalItemCount) {
        }

        @Override
        public void onScrollStateChanged(AbsListView listView, int scrollState) {
            // Cancel any rendering so that scrolling occurs as fluidly as possible
            Timber.v("Scroll state changed to "+Integer.toString(scrollState));
            DeckTask.cancelTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA);
            // Resume rendering once the scrolling has finished
            if (scrollState == SCROLL_STATE_IDLE) {
                int startIdx = listView.getFirstVisiblePosition();
                int numVisible = listView.getLastVisiblePosition() - startIdx;
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA, mRenderQAHandler, new DeckTask.TaskData(
                        new Object[] { getCol(), getCards(), startIdx - 5 , 2*numVisible + 5}));
            }
        }
    }

    private final class MultiColumnListAdapter extends BaseAdapter {
        private ArrayList<HashMap<String, String>> mData;
        private final int mResource;
        private String[] mFromKeys;
        private final int[] mToIds;
        private final String mColorFlagKey;
        private float mOriginalTextSize = -1.0f;
        private final int mFontSizeScalePcent;
        private Typeface mCustomTypeface = null;
        private LayoutInflater mInflater;


        public MultiColumnListAdapter(Context context, ArrayList<HashMap<String, String>> data, int resource,
                String[] from, int[] to, String colorFlagKey, int fontSizeScalePcent, String customFont) {
            mData = data;
            mResource = resource;
            mFromKeys = from;
            mToIds = to;
            mColorFlagKey = colorFlagKey;
            mFontSizeScalePcent = fontSizeScalePcent;
            if (!customFont.equals("")) {
                mCustomTypeface = AnkiFont.getTypeface(context, customFont);
            }
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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


        private void bindView(int position, View v) {
            // Draw the content in the columns
            View[] columns = (View[]) v.getTag();
            final Map<String, String> dataSet = mData.get(position);
            final int color = getColor(dataSet.get(mColorFlagKey));
            for (int i = 0; i < mToIds.length; i++) {
                TextView col = (TextView) columns[i];
                // set font for column
                setFont(col);
                // set background color for column
                col.setBackgroundResource(mBackground[color]);
                // set text for column
                col.setText(dataSet.get(mFromKeys[i]));
            }
        }


        private void setFont(TextView v) {
            // Set the font and font size for a TextView v
            float currentSize = v.getTextSize();
            if (mOriginalTextSize < 0) {
                mOriginalTextSize = v.getTextSize();
            }
            // do nothing when pref is 100% and apply scaling only once
            if (mFontSizeScalePcent != 100 && Math.abs(mOriginalTextSize - currentSize) < 0.1) {
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, mOriginalTextSize * (mFontSizeScalePcent / 100.0f));
            }

            if (mCustomTypeface != null) {
                v.setTypeface(mCustomTypeface);
            }
        }


        private int getColor(String flag) {
            int which = BACKGROUND_NORMAL;
            if (flag == null) {
                // use BACKGROUND_NORMAL
            } else if (flag.equals("1")) {
                which = BACKGROUND_SUSPENDED;
            } else if (flag.equals("2")) {
                which = BACKGROUND_MARKED;
            } else if (flag.equals("3")) {
                which = BACKGROUND_MARKED_SUSPENDED;
            }
            return which;
        }


        public void setFromMapping(String[] from) {
            mFromKeys = from;
            notifyDataSetChanged();
        }


        public String[] getFromMapping() {
            return mFromKeys;
        }


        @Override
        public int getCount() {
            return mData.size();
        }


        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }


        @Override
        public long getItemId(int position) {
            return position;
        }
    }


    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            try {
                mProgressDialog.dismiss();
            } catch (IllegalArgumentException e) {
                // This shouldn't be neccessary, but crashes still occurring (see 4f949b11-9cdc-41fd-80b8-7c4d02b25151)
                // TODO: Check if multithreading issue
                Timber.w(e, "Could not dismiss mProgressDialog");
            }
        }
    }


    private ArrayList<HashMap<String, String>> getCards() {
        if (mCards == null) {
            mCards = new ArrayList<HashMap<String, String>>();
        }
        return mCards;
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

}
