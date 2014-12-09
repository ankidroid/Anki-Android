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
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
import android.util.Log;
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

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.TagsDialog;
import com.ichi2.anki.dialogs.TagsDialog.TagsDialogListener;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.upgrade.Upgrade;
import com.ichi2.widget.WidgetStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CardBrowser extends NavigationDrawerActivity implements ActionBar.OnNavigationListener {

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

    private StyledProgressDialog mProgressDialog;
    public static Card sCardBrowserCard;
    public static boolean sSearchCancelled = false;

    private int mPositionInCardsList;

    private int mOrder;
    private boolean mOrderAsc;
    private int mColumn2Index;

    private static final int CONTEXT_MENU_MARK = 0;
    private static final int CONTEXT_MENU_SUSPEND = 1;
    private static final int CONTEXT_MENU_DELETE = 2;
    private static final int CONTEXT_MENU_DETAILS = 3;

    private static final int DIALOG_ORDER = 0;
    private static final int DIALOG_CONTEXT_MENU = 1;
    private static final int DIALOG_TAGS = 3;

    private static final int BACKGROUND_NORMAL = 0;
    private static final int BACKGROUND_MARKED = 1;
    private static final int BACKGROUND_SUSPENDED = 2;
    private static final int BACKGROUND_MARKED_SUSPENDED = 3;

    private static final int EDIT_CARD = 0;
    private static final int ADD_NOTE = 1;
    private static final int DEFAULT_FONT_SIZE_RATIO = 100;

    // Should match order of R.array.card_browser_order_labels
    private static final int CARD_ORDER_NONE = 0;
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
    String[] mOrderByFields;
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

    private boolean mWholeCollection;

    private ActionBar mActionBar;
    private DeckDropDownAdapter mDropDownAdapter;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private DialogInterface.OnClickListener mContextMenuListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case CONTEXT_MENU_MARK:
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD,
                            mUpdateCardHandler,
                            new DeckTask.TaskData(getCol().getSched(), getCol().getCard(Long.parseLong(mCards.get(
                                    mPositionInCardsList).get("id"))), 0));
                    return;

                case CONTEXT_MENU_SUSPEND:
                    DeckTask.launchDeckTask(
                            DeckTask.TASK_TYPE_DISMISS_NOTE,
                            mSuspendCardHandler,
                            new DeckTask.TaskData(getCol().getSched(), getCol().getCard(Long.parseLong(mCards.get(
                                    mPositionInCardsList).get("id"))), 1));
                    return;

                case CONTEXT_MENU_DELETE:
                    Resources res = getResources();
                    StyledDialog.Builder builder = new StyledDialog.Builder(CardBrowser.this);
                    builder.setTitle(res.getString(R.string.delete_card_title));
                    builder.setIcon(R.drawable.ic_dialog_alert);
                    builder.setMessage(res.getString(R.string.delete_card_message, mCards.get(mPositionInCardsList)
                            .get("sfld")));
                    builder.setPositiveButton(res.getString(R.string.dialog_positive_delete),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Card card = getCol().getCard(Long.parseLong(mCards.get(mPositionInCardsList).get("id")));
                                    deleteNote(card);
                                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDeleteNoteHandler,
                                                            new DeckTask.TaskData(getCol().getSched(), card, 3));
                                }
                            });
                    builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                    builder.create().show();
                    return;

                case CONTEXT_MENU_DETAILS:
                    Long cardId = Long.parseLong(mCards.get(mPositionInCardsList).get("id"));
                    Intent previewer = new Intent(CardBrowser.this, Previewer.class);
                    previewer.putExtra("currentCardId", cardId);
                    startActivityWithoutAnimation(previewer);
                    return;
            }
        }

    };


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
        Log.v(AnkiDroidApp.TAG, "CardBrowser onCreate()");
        View mainView = getLayoutInflater().inflate(R.layout.card_browser, null);
        setContentView(mainView);
        Themes.setContentStyle(mainView, Themes.CALLER_CARDBROWSER);
        
        initNavigationDrawer(mainView);
        selectNavigationItem(DRAWER_BROWSER);
        
        startLoadingCollection();
    }


    // Finish initializing the activity after the collection has been correctly loaded
    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        Log.v(AnkiDroidApp.TAG, "Card Browser onCollectionLoaded()");
        mDeckNames = new HashMap<String, String>();
        for (long did : getCol().getDecks().allIds()) {
            mDeckNames.put(String.valueOf(did), getCol().getDecks().name(did));
        }
        registerExternalStorageListener();

        Intent i = getIntent();
        mWholeCollection = i.hasExtra("fromDeckpicker") && i.getBooleanExtra("fromDeckpicker", false);

        mBackground = Themes.getCardBrowserBackground();

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        Resources res = getResources();

        // Add drop-down menu to select deck to action bar.
        mDropDownDecks = getCol().getDecks().allSorted();
        mDropDownAdapter = new DeckDropDownAdapter(this, mDropDownDecks);
        mActionBar = getSupportActionBar();
        mActionBar.setDisplayShowTitleEnabled(false);
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        mActionBar.setListNavigationCallbacks(mDropDownAdapter, this);

        mOrderByFields = res.getStringArray(R.array.card_browser_order_labels);
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
                showDialog(DIALOG_CONTEXT_MENU);
                return true;
            }
        });

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // initialize mSearchTerms to a default value
        mSearchTerms = "";

        // onNavigationItemSelected will be called automatically, replacing onSearch in onCreate.
        if (!mWholeCollection) {
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
                    mActionBar.setSelectedNavigationItem(dropDownDeckIdx + 1);
                    break;
                }
            }
        }
        dismissOpeningCollectionDialog();
    }


    @Override
    protected void onStop() {
        Log.i(AnkiDroidApp.TAG, "CardBrowser - onStop()");
        // cancel rendering the question and answer, which has shared access to mCards
        DeckTask.cancelTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA);
        super.onStop();
        if (!isFinishing()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground();
        }
    }


    @Override
    protected void onDestroy() {
        Log.i(AnkiDroidApp.TAG, "CardBrowser - onDestroy()");
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "CardBrowser - onBackPressed()");
            closeCardBrowser(Activity.RESULT_OK);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    protected void onResume() {
        Log.v(AnkiDroidApp.TAG, "CardBrowser onResume()");
        super.onResume();
        selectNavigationItem(DRAWER_BROWSER);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.card_browser, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
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
        mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String newText) {
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

            case R.id.action_sort_by_size:
                showDialog(DIALOG_ORDER);
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
        Log.i(AnkiDroidApp.TAG, "CardBrowser - onActivityResult()");
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
        }

        if (requestCode == EDIT_CARD && resultCode != RESULT_CANCELED) {
            Log.i(AnkiDroidApp.TAG, "CardBrowser: Saving card...");
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler,
                    new DeckTask.TaskData(getCol().getSched(), sCardBrowserCard, false));
        } else if (requestCode == ADD_NOTE && resultCode == RESULT_OK) {
            mSearchTerms = mSearchView.getQuery().toString();
            // Both toLowerCase(Locale.US) and toLowerCase(Locale.getDefault()) would be wrong here. Keywords are pulled
            // toLowerCase(Locale.US) later.
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

    @Override
    protected Dialog onCreateDialog(int id) {
        StyledDialog dialog = null;
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);

        switch (id) {
            case DIALOG_ORDER:
                builder.setTitle(res.getString(R.string.card_browser_change_display_order_title));
                builder.setMessage(res.getString(R.string.card_browser_change_display_order_reverse));
                builder.setIcon(android.R.drawable.ic_menu_sort_by_size);
                builder.setSingleChoiceItems(res.getStringArray(R.array.card_browser_order_labels), mOrder,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface arg0, int which) {
                                if (which != mOrder) {
                                    mOrder = which;
                                    mOrderAsc = false;
                                    try {
                                        if (mOrder == 0) {
                                            getCol().getConf().put("sortType", fSortTypes[1]);
                                            AnkiDroidApp.getSharedPrefs(getBaseContext()).edit()
                                                    .putBoolean("cardBrowserNoSorting", true).commit();
                                        } else {
                                            getCol().getConf().put("sortType", fSortTypes[mOrder]);
                                            AnkiDroidApp.getSharedPrefs(getBaseContext()).edit()
                                                    .putBoolean("cardBrowserNoSorting", false).commit();
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
                            }
                        });
                dialog = builder.create();
                break;

            case DIALOG_CONTEXT_MENU:
                // FIXME:
                String[] entries = new String[4];
                @SuppressWarnings("unused")
                MenuItem item;
                entries[CONTEXT_MENU_MARK] = res.getString(R.string.card_browser_mark_card);
                entries[CONTEXT_MENU_SUSPEND] = res.getString(R.string.card_browser_suspend_card);
                entries[CONTEXT_MENU_DELETE] = res.getString(R.string.card_browser_delete_card);
                entries[CONTEXT_MENU_DETAILS] = res.getString(R.string.card_editor_preview_card);
                builder.setTitle("contextmenu");
                builder.setIcon(R.drawable.ic_menu_manage);
                builder.setItems(entries, mContextMenuListener);
                dialog = builder.create();
                break;
        }
        return dialog;
    }


    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        Resources res = getResources();
        StyledDialog ad = (StyledDialog) dialog;
        switch (id) {
            case DIALOG_ORDER:
                for (int i = 0; i < mOrderByFields.length; ++i) {
                    if (i != CARD_ORDER_NONE && i == mOrder) {
                        if (mOrderAsc) {
                            ad.changeListItem(i, mOrderByFields[i] + " (\u25b2)");
                        } else {
                            ad.changeListItem(i, mOrderByFields[i] + " (\u25bc)");
                        }
                    } else {
                        ad.changeListItem(i, mOrderByFields[i]);
                    }
                }
                break;
            case DIALOG_CONTEXT_MENU:
                HashMap<String, String> card = mCards.get(mPositionInCardsList);
                int flags = Integer.parseInt(card.get("flags"));
                if (flags == 2 || flags == 3) {
                    ad.changeListItem(CONTEXT_MENU_MARK, res.getString(R.string.card_browser_unmark_card));
                    Log.d(AnkiDroidApp.TAG, "Selected Card is currently marked");
                } else {
                    ad.changeListItem(CONTEXT_MENU_MARK, res.getString(R.string.card_browser_mark_card));
                }
                if (flags == 1 || flags == 3) {
                    ad.changeListItem(CONTEXT_MENU_SUSPEND, res.getString(R.string.card_browser_unsuspend_card));
                    Log.d(AnkiDroidApp.TAG, "Selected Card is currently suspended");
                } else {
                    ad.changeListItem(CONTEXT_MENU_SUSPEND, res.getString(R.string.card_browser_suspend_card));
                }
                ad.setTitle(card.get("sfld"));
                break;
        }
    }


    @Override
    public boolean onNavigationItemSelected(int position, long itemId) {
        // cancel rendering the question and answer, which has shared access to mCards
        DeckTask.cancelTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA);
        
        if (position == 0) {
            mRestrictOnDeck = "";
        } else {
            JSONObject deck = mDropDownDecks.get(position - 1);
            String deckName;
            try {
                deckName = deck.getString("name");
            } catch (JSONException e) {
                throw new RuntimeException();
            }
            mRestrictOnDeck = "deck:\"" + deckName + "\" ";
        }
        searchCards();
        return true;
    }


    private void searchCards() {
        String searchText = mRestrictOnDeck + mSearchTerms;
        if (colOpen()) {
            // clear the existing card list
            mCards.clear();
            // Perform database query to get all card ids / sfld. Shows "filtering cards..." progress message
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SEARCH_CARDS, mSearchCardsHandler, new DeckTask.TaskData(
                    new Object[] { getCol(), mDeckNames, searchText, ((mOrder == CARD_ORDER_NONE) ? false : true) }));
        }
    }


    private void updateList() {
        mCardsAdapter.notifyDataSetChanged();
        int count = mCards.size();
        mDropDownAdapter.setCardCount(count);
        mDropDownAdapter.notifyDataSetChanged();
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
            pos = getPosition(mCards, c.getId());
            if (pos < 0 || pos >= mCards.size()) {
                continue;
            }
            // update tags
            if (updatedCardTags != null) {
                mCards.get(pos).put("tags", updatedCardTags);
            }
            // update sfld
            String sfld = note.getSFld();
            mCards.get(pos).put("sfld", sfld);
            // update Q & A etc
            updateSearchItemQA(mCards.get(pos), c);
            // update deck
            String deckName;
            try {
                deckName = getCol().getDecks().get(card.getDid()).getString("name");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            mCards.get(pos).put("deck", deckName);
            // update flags (marked / suspended / etc) which determine color
            String flags = Integer.toString((c.getQueue() == -1 ? 1 : 0) + (note.hasTag("marked") ? 2 : 0));
            mCards.get(pos).put("flags", flags);
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
            pos = getPosition(mCards, c.getId());
            if (pos >= 0 && pos < mCards.size()) {
                mCards.remove(pos);
            }
        }
        // Delete itself if not deleted
        pos = getPosition(mCards, card.getId());
        if (pos >= 0 && pos < mCards.size()) {
            mCards.remove(pos);
        }
        updateList();
    }

    private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            if (mProgressDialog == null) {
                mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
                        res.getString(R.string.saving_changes), true);
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
            Log.i(AnkiDroidApp.TAG, "Card Browser - mUpdateCardHandler.onPostExecute()");
            if (!result.getBoolean()) {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
            mProgressDialog.dismiss();

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
                    true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (result.getBoolean()) {
                // // Update list if search on suspended
                // if (fSearchSuspendedPattern.matcher(mSearchTerms).find()) {
                // updateCardsList();
                // }
                updateCardInList(getCol().getCard(Long.parseLong(mCards.get(mPositionInCardsList).get("id"))), null);
            } else {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
            mProgressDialog.dismiss();

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
                    true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            mProgressDialog.dismiss();

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
            if (mProgressDialog == null) {
                mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
                        res.getString(R.string.card_browser_filtering_cards), true, true,
                        new DialogInterface.OnCancelListener(){
                            @Override
                            public void onCancel(DialogInterface dialog){
                                Log.i(AnkiDroidApp.TAG, "Search cards dialog dismissed");
                                DeckTask.cancelTask(DeckTask.TASK_TYPE_SEARCH_CARDS);
                                sSearchCancelled = true;
                            }
                });
            } else {
                mProgressDialog.setMessage(res.getString(R.string.card_browser_filtering_cards));
                mProgressDialog.show();
            }

        }


        @Override
        public void onPostExecute(TaskData result) {            
            if (result != null) {
                Log.i(AnkiDroidApp.TAG, "Completed doInBackgroundSearchCards Successfuly");
                updateList();
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                // After the initial searchCards query, start rendering the question and answer in the background
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA, mRenderQAHandler, new DeckTask.TaskData(
                        new Object[] { getCol(), mCards, 0, 100 }));
            } else {
                // this is a hack -- see DeckTask.launchDeckTask for more info
                Log.i(AnkiDroidApp.TAG, "doInBackgroundSearchCards onPostExecute() called but result was null");
                sSearchCancelled = false;
            }
        }
        
        @Override
        public void onCancelled(){
            // reset the hacky static variable which Finder is listening to check if main thread has requested cancellation
            Log.i(AnkiDroidApp.TAG, "doInBackgroundSearchCards onCancelled() called");
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
                Log.i(AnkiDroidApp.TAG, "Completed doInBackgroundRenderBrowserQA Successfuly");
            } else {
                // Might want to do something more proactive here like show a message box?
                Log.e(AnkiDroidApp.TAG, "doInBackgroundRenderBrowserQA was not successful... continuing anyway");
            }
        }


        @Override
        public void onCancelled() {
        }
    };


    private void closeCardBrowser(int result) {
        setResult(result);
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
            Log.v(AnkiDroidApp.TAG, "Scroll state changed to "+Integer.toString(scrollState));
            DeckTask.cancelTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA);
            // Resume rendering once the scrolling has finished
            if (scrollState == SCROLL_STATE_IDLE) {
                int startIdx = listView.getFirstVisiblePosition();
                int numVisible = listView.getLastVisiblePosition() - startIdx;
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_RENDER_BROWSER_QA, mRenderQAHandler, new DeckTask.TaskData(
                        new Object[] { getCol(), mCards, startIdx-5 , startIdx+2*numVisible}));
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


    private static class DeckDropDownViewHolder {
        public TextView deckNameView;
        public TextView deckCountsView;
    }


    private final class DeckDropDownAdapter extends BaseAdapter {

        private Context context;
        private ArrayList<JSONObject> decks;
        private int count;


        public DeckDropDownAdapter(Context context, ArrayList<JSONObject> decks) {
            this.context = context;
            this.decks = decks;
        }


        @Override
        public int getCount() {
            return decks.size() + 1;
        }


        @Override
        public Object getItem(int position) {
            if (position == 0) {
                return null;
            } else {
                return decks.get(position + 1);
            }
        }


        @Override
        public long getItemId(int position) {
            return position;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            DeckDropDownViewHolder viewHolder;
            TextView deckNameView;
            TextView deckCountsView;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.dropdown_deck_selected_item, parent, false);
                deckNameView = (TextView) convertView.findViewById(R.id.dropdown_deck_name);
                deckCountsView = (TextView) convertView.findViewById(R.id.dropdown_deck_counts);
                viewHolder = new DeckDropDownViewHolder();
                viewHolder.deckNameView = deckNameView;
                viewHolder.deckCountsView = deckCountsView;
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (DeckDropDownViewHolder) convertView.getTag();
                deckNameView = (TextView) viewHolder.deckNameView;
                deckCountsView = (TextView) viewHolder.deckCountsView;
            }
            if (position == 0) {
                deckNameView.setText(context.getResources().getString(R.string.deck_summary_all_decks));
            } else {
                JSONObject deck = decks.get(position - 1);
                try {
                    String deckName = deck.getString("name");
                    deckNameView.setText(deckName);
                } catch (JSONException ex) {
                    new RuntimeException();
                }
            }
            deckCountsView.setText(getResources().getQuantityString(R.plurals.card_browser_subtitle, count, count));
            return convertView;
        }


        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView deckNameView;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.dropdown_deck_item, parent, false);
                deckNameView = (TextView) convertView.findViewById(R.id.dropdown_deck_name);
                convertView.setTag(deckNameView);
            } else {
                deckNameView = (TextView) convertView.getTag();
            }
            if (position == 0) {
                deckNameView.setText(context.getResources().getString(R.string.deck_summary_all_decks));
            } else {
                JSONObject deck = decks.get(position - 1);
                try {
                    String deckName = deck.getString("name");
                    deckNameView.setText(deckName);
                } catch (JSONException ex) {
                    new RuntimeException();
                }
            }
            return convertView;
        }


        public void setCardCount(int count) {
            this.count = count;
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

}
