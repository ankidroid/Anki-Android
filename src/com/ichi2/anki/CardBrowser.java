/****************************************************************************************
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.multimediacard.activity.MultimediaCardEditorActivity;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.CardStats;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledOpenCollectionDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.upgrade.Upgrade;
import com.ichi2.widget.WidgetStatus;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class CardBrowser extends Activity {
    // private List<Long> mCardIds = new ArrayList<Long>();
    private ArrayList<HashMap<String, String>> mCards;
    // private ArrayList<HashMap<String, String>> mAllCards;
    private HashMap<String, String> mDeckNames;
    private ListView mCardsListView;
    private SimpleAdapter mCardsAdapter;
    private EditText mSearchEditText;
    private String mSearchTerms;
    private String mRestrictOnDeck;
    private ImageButton mSearchButton;

    private StyledProgressDialog mProgressDialog;
    private StyledOpenCollectionDialog mOpenCollectionDialog;
    private boolean mUndoRedoDialogShowing = false;

    public static Card sCardBrowserCard;

    private int mPositionInCardsList;

    private int mOrder;
    private boolean mOrderAsc;
    private int mField;
    private int mTotalCount;

    private static final int CONTEXT_MENU_MARK = 0;
    private static final int CONTEXT_MENU_SUSPEND = 1;
    private static final int CONTEXT_MENU_DELETE = 2;
    private static final int CONTEXT_MENU_DETAILS = 3;

    private static final int DIALOG_ORDER = 0;
    private static final int DIALOG_CONTEXT_MENU = 1;
    private static final int DIALOG_RELOAD_CARDS = 2;
    private static final int DIALOG_TAGS = 3;
    // TODO(flerda@gmail.com): Fix card browser fields. See below.
    // https://code.google.com/p/ankidroid/issues/detail?id=1310
    /*
    private static final int DIALOG_FIELD = 4;
    */

    private static final int BACKGROUND_NORMAL = 0;
    private static final int BACKGROUND_MARKED = 1;
    private static final int BACKGROUND_SUSPENDED = 2;
    private static final int BACKGROUND_MARKED_SUSPENDED = 3;

    // TODO(flerda@gmail.com): Fix card browser's undo.
    // https://code.google.com/p/ankidroid/issues/detail?id=1561
    /*
    private static final int MENU_UNDO = 0;
    */
    private static final int MENU_ADD_NOTE = 1;
    private static final int MENU_SHOW_MARKED = 2;
    private static final int MENU_SELECT = 3;
    private static final int MENU_SELECT_SUSPENDED = 31;
    private static final int MENU_SELECT_TAG = 32;
    private static final int MENU_CHANGE_ORDER = 5;
    // TODO(flerda@gmail.com): Fix card browser fields. See below.
    // https://code.google.com/p/ankidroid/issues/detail?id=1310
    /*
    private static final int MENU_FIELD = 6;
    */

    private static final int EDIT_CARD = 0;
    private static final int ADD_NOTE = 1;
    private static final int DEFAULT_FONT_SIZE_RATIO = 100;

    // Should match order of R.array.card_browser_order_labels
    private static final int CARD_ORDER_NONE = 0;
    private static final String[] fSortTypes = new String[]{
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

    private int[] mBackground;

    private boolean mWholeCollection;

    private String[] allTags;
    private String[] mFields;
    private HashSet<String> mSelectedTags;

    private boolean mPrefFixArabic;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private Collection mCol;

    private DialogInterface.OnClickListener mContextMenuListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case CONTEXT_MENU_MARK:
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mUpdateCardHandler, new DeckTask.TaskData(
                            mCol.getSched(), mCol.getCard(Long.parseLong(mCards.get(mPositionInCardsList).get("id"))),
                            0));
                    return;

                case CONTEXT_MENU_SUSPEND:
                    DeckTask.launchDeckTask(
                            DeckTask.TASK_TYPE_DISMISS_NOTE,
                            mSuspendCardHandler,
                            new DeckTask.TaskData(mCol.getSched(), mCol.getCard(Long.parseLong(mCards.get(
                                    mPositionInCardsList).get("id"))), 1));
                    return;

                case CONTEXT_MENU_DELETE:
                    Resources res = getResources();
                    StyledDialog.Builder builder = new StyledDialog.Builder(CardBrowser.this);
                    builder.setTitle(res.getString(R.string.delete_card_title));
                    builder.setIcon(R.drawable.ic_dialog_alert);
                    builder.setMessage(res.getString(R.string.delete_card_message, mCards.get(mPositionInCardsList)
                            .get("sfld")));
                    builder.setPositiveButton(res.getString(R.string.yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Card card = mCol.getCard(Long.parseLong(mCards.get(mPositionInCardsList).get("id")));
                            deleteNote(card);
                            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDeleteNoteHandler,
                                    new DeckTask.TaskData(mCol.getSched(), card, 3));
                        }
                    });
                    builder.setNegativeButton(res.getString(R.string.no), null);
                    builder.create().show();
                    return;

                case CONTEXT_MENU_DETAILS:
                    Card tempCard = mCol.getCard(Long.parseLong(mCards.get(mPositionInCardsList).get("id")));
                    Themes.htmlOkDialog(
                            CardBrowser.this,
                            getResources().getString(R.string.card_browser_card_details),
                            CardStats.report(CardBrowser.this, tempCard, mCol))
                        .show();
                    return;
            }
        }

    };


    private void onSearch() {
        mSearchTerms = mSearchEditText.getText().toString().toLowerCase();
        if (mSearchTerms.length() == 0) {
            mSearchEditText.setHint(R.string.downloaddeck_search);
        }
        searchCards();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);

        View mainView = getLayoutInflater().inflate(R.layout.card_browser, null);
        setContentView(mainView);
        Themes.setContentStyle(mainView, Themes.CALLER_CARDBROWSER);

        mCol = AnkiDroidApp.getCol();
        if (mCol == null) {
            reloadCollection(savedInstanceState);
            return;
        }
        mDeckNames = new HashMap<String, String>();
        for (long did : mCol.getDecks().allIds()) {
            mDeckNames.put(String.valueOf(did), mCol.getDecks().name(did));
        }
        registerExternalStorageListener();

        Intent i = getIntent();
        mWholeCollection = i.hasExtra("fromDeckpicker") && i.getBooleanExtra("fromDeckpicker", false);

        mBackground = Themes.getCardBrowserBackground();

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        int sflRelativeFontSize = preferences.getInt("relativeCardBrowserFontSize", DEFAULT_FONT_SIZE_RATIO);
        String sflCustomFont = preferences.getString("browserEditorFont", "");
        mPrefFixArabic = preferences.getBoolean("fixArabicText", false);

        Resources res = getResources();
        mOrderByFields = res.getStringArray(R.array.card_browser_order_labels);
        try {
            mOrder = CARD_ORDER_NONE;
            String colOrder = mCol.getConf().getString("sortType");
            for (int c = 0; c < fSortTypes.length; ++c) {
                if (fSortTypes[c].equals(colOrder)) {
                    mOrder = c;
                    break;
                }
            }
            if (mOrder == 1 && preferences.getBoolean("cardBrowserNoSorting", false)) {
                mOrder = 0;
            }
            mOrderAsc = Upgrade.upgradeJSONIfNecessary(mCol, mCol.getConf(), "sortBackwards", false);
            // default to descending for non-text fields
            if (fSortTypes[mOrder].equals("noteFld")) {
                mOrderAsc = !mOrderAsc;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        mCards = new ArrayList<HashMap<String, String>>();
        mCardsListView = (ListView) findViewById(R.id.card_browser_list);

        mCardsAdapter = new SizeControlledListAdapter(
                this,
                mCards,
                R.layout.card_item,
                new String[] { "sfld", "deck", "flags" },
                new int[] { R.id.card_sfld, R.id.card_deck, R.id.card_item },
                sflRelativeFontSize,
                sflCustomFont);
        mCardsAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object arg1, String text) {
                if (view.getId() == R.id.card_item) {
                    int which = BACKGROUND_NORMAL;
                    if (text.equals("1")) {
                        which = BACKGROUND_SUSPENDED;
                    } else if (text.equals("2")) {
                        which = BACKGROUND_MARKED;
                    } else if (text.equals("3")) {
                        which = BACKGROUND_MARKED_SUSPENDED;
                    }
                    view.setBackgroundResource(mBackground[which]);
                    return true;
                } else if (view.getId() == R.id.card_deck && text.length() > 0) {
                    view.setVisibility(View.VISIBLE);
                }
                return false;
            }
        });

        mCardsListView.setAdapter(mCardsAdapter);
        mCardsListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mPositionInCardsList = position;
                long cardId = Long.parseLong(mCards.get(mPositionInCardsList).get("id"));
                sCardBrowserCard = mCol.getCard(cardId);
                Intent editCard = new Intent(CardBrowser.this, CardEditor.class);
                editCard.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_CARDBROWSER_EDIT);
                editCard.putExtra(CardEditor.EXTRA_CARD_ID, sCardBrowserCard.getId());
                startActivityForResult(editCard, EDIT_CARD);
                if (AnkiDroidApp.SDK_VERSION > 4) {
                    ActivityTransitionAnimation.slide(CardBrowser.this, ActivityTransitionAnimation.LEFT);
                }
            }
        });
        registerForContextMenu(mCardsListView);

        mSearchEditText = (EditText) findViewById(R.id.card_browser_search);
        mSearchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    onSearch();
                    return true;
                }
                return false;
            }
        });
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        mSearchButton = (ImageButton) findViewById(R.id.card_browser_search_button);
        mSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSearch();
            }
        });

        mSearchTerms = "";
        if (mWholeCollection) {
            mRestrictOnDeck = "";
            setTitle(res.getString(R.string.card_browser_all_decks));
        } else {
            try {
                String deckName = mCol.getDecks().current().getString("name");
                mRestrictOnDeck = "deck:'" + deckName + "' ";
                setTitle(deckName);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        mSelectedTags = new HashSet<String>();

        if (!preferences.getBoolean("cardBrowserNoSearchOnOpen", false)) {
            searchCards();
        }
    }


    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
        Log.i(AnkiDroidApp.TAG, "CardBrowser - onDestroy()");
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        mPositionInCardsList = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
        showDialog(DIALOG_CONTEXT_MENU);
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "CardBrowser - onBackPressed()");
            if (mSearchEditText.getText().length() == 0) {
                closeCardBrowser(Activity.RESULT_OK);
            } else {
                mSearchEditText.setText("");
                mSearchEditText.setHint(R.string.downloaddeck_search);
                mSelectedTags.clear();
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        // TODO(flerda@gmail.com): Fix card browser's undo.
        // https://code.google.com/p/ankidroid/issues/detail?id=1561
        /*
        item = menu.add(Menu.NONE, MENU_UNDO, Menu.NONE, R.string.undo);
        item.setIcon(R.drawable.ic_menu_revert);
        */
        item = menu.add(Menu.NONE, MENU_ADD_NOTE, Menu.NONE, R.string.card_editor_add_card);
        item.setIcon(R.drawable.ic_menu_add);
        // TODO(flerda@gmail.com): Fix card browser fields.
        // https://code.google.com/p/ankidroid/issues/detail?id=1310
        // Currently this is disabled because it is obvious what to do when cards with different models are present in
        // the deck.
        /*
        if (mWholeCollection == false) {
            item = menu.add(Menu.NONE, MENU_FIELD, Menu.NONE, R.string.card_browser_field);
            item.setIcon(R.drawable.ic_menu_add);
        }
        */
        item = menu.add(Menu.NONE, MENU_CHANGE_ORDER, Menu.NONE, R.string.card_browser_change_display_order);
        item.setIcon(R.drawable.ic_menu_sort_by_size);
        item = menu.add(Menu.NONE, MENU_SHOW_MARKED, Menu.NONE, R.string.card_browser_show_marked);
        item.setIcon(R.drawable.ic_menu_star_on);
        item = menu.add(Menu.NONE, MENU_SELECT_SUSPENDED, Menu.NONE, R.string.card_browser_show_suspended);
        item.setIcon(R.drawable.ic_menu_close_clear_cancel);
        item = menu.add(Menu.NONE, MENU_SELECT_TAG, Menu.NONE, R.string.card_browser_search_by_tag);
        item.setIcon(R.drawable.ic_menu_search);
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mCol == null) {
            return false;
        }
        // TODO(flerda@gmail.com): Fix card browser's undo.
        // https://code.google.com/p/ankidroid/issues/detail?id=1561
        /*
        menu.findItem(MENU_UNDO).setEnabled(mCol.undoAvailable());
        */
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            // TODO(flerda@gmail.com): Fix card browser's undo.
            // https://code.google.com/p/ankidroid/issues/detail?id=1561
            /*
            case MENU_UNDO:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUndoRedoHandler,
                        new DeckTask.TaskData(0, mDeck, 0, true));
                return true;
            */

            case MENU_ADD_NOTE:
                Intent intent = new Intent(CardBrowser.this, CardEditor.class);
                intent.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_CARDBROWSER_ADD);
                startActivityForResult(intent, ADD_NOTE);
                if (AnkiDroidApp.SDK_VERSION > 4) {
                    ActivityTransitionAnimation.slide(CardBrowser.this, ActivityTransitionAnimation.LEFT);
                }
                return true;

            case MENU_SHOW_MARKED:
                mSearchTerms = "tag:marked";
                mSearchEditText.setText("");
                mSearchEditText.setHint(R.string.card_browser_show_marked);
                searchCards();
                return true;

            case MENU_SELECT_SUSPENDED:
                mSearchTerms = "is:suspended";
                mSearchEditText.setText("");
                mSearchEditText.setHint(R.string.card_browser_show_suspended);
                searchCards();
                return true;

            case MENU_SELECT_TAG:
                showDialog(DIALOG_TAGS);
                return true;

            case MENU_CHANGE_ORDER:
                showDialog(DIALOG_ORDER);
                return true;
            // TODO(flerda@gmail.com): Fix card browser fields. See above.
            // https://code.google.com/p/ankidroid/issues/detail?id=1310
            /*
            case MENU_FIELD:
                showDialog(DIALOG_FIELD);
                return true;
            */
        }

        return false;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // FIXME:
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
        }

        // TODO(flerda): Currently we are using the regular card editor and
        // delete is not possible. We should probably update this went
        // switching back to the multimedia card editor.
        if (requestCode == EDIT_CARD && resultCode == MultimediaCardEditorActivity.RESULT_DELETED) {
            deleteNote(sCardBrowserCard);
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDeleteNoteHandler,
                    new DeckTask.TaskData(mCol.getSched(), sCardBrowserCard, 3));
        } else if (requestCode == EDIT_CARD && resultCode != RESULT_CANCELED) {
            Log.i(AnkiDroidApp.TAG, "CardBrowser: Saving card...");
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler,
                    new DeckTask.TaskData(mCol.getSched(), sCardBrowserCard, false));
        } else if (requestCode == ADD_NOTE && resultCode == RESULT_OK) {
            mSearchTerms = mSearchEditText.getText().toString().toLowerCase();
            searchCards();
        }
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
                                            mCol.getConf().put("sortType", fSortTypes[1]);
                                            AnkiDroidApp.getSharedPrefs(getBaseContext()).edit()
                                                    .putBoolean("cardBrowserNoSorting", true).commit();
                                        } else {
                                            mCol.getConf().put("sortType", fSortTypes[mOrder]);
                                            AnkiDroidApp.getSharedPrefs(getBaseContext()).edit()
                                                    .putBoolean("cardBrowserNoSorting", false).commit();
                                        }
                                        // default to descending for non-text fields
                                        if (fSortTypes[mOrder].equals("noteFld")) {
                                            mOrderAsc = true;
                                        }
                                        mCol.getConf().put("sortBackwards", mOrderAsc);
                                    } catch (JSONException e) {
                                        throw new RuntimeException(e);
                                    }
                                    searchCards();
                                } else if (which != CARD_ORDER_NONE) {
                                    mOrderAsc = !mOrderAsc;
                                    try {
                                        mCol.getConf().put("sortBackwards", mOrderAsc);
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
                entries[CONTEXT_MENU_DETAILS] = res.getString(R.string.card_browser_card_details);
                builder.setTitle("contextmenu");
                builder.setIcon(R.drawable.ic_menu_manage);
                builder.setItems(entries, mContextMenuListener);
                dialog = builder.create();
                break;

            case DIALOG_TAGS:
                allTags = mCol.getTags().all();
                builder.setTitle(R.string.studyoptions_limit_select_tags);
                builder.setMultiChoiceItems(allTags, new boolean[allTags.length],
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String tag = allTags[which];
                                if (mSelectedTags.contains(tag)) {
                                    Log.i(AnkiDroidApp.TAG, "unchecked tag: " + tag);
                                    mSelectedTags.remove(tag);
                                } else {
                                    Log.i(AnkiDroidApp.TAG, "checked tag: " + tag);
                                    mSelectedTags.add(tag);
                                }
                            }
                        });
                builder.setPositiveButton(res.getString(R.string.select), new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSearchEditText.setText("");
                        String tags = mSelectedTags.toString();
                        mSearchEditText.setHint(getResources().getString(R.string.card_browser_tags_shown,
                                tags.substring(1, tags.length() - 1)));
                        StringBuilder sb = new StringBuilder();
                        for (String tag : mSelectedTags) {
                            sb.append("tag:").append(tag).append(" ");
                        }
                        mSearchTerms = sb.toString();
                        searchCards();
                    }
                });
                builder.setNegativeButton(res.getString(R.string.cancel), new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSelectedTags.clear();
                    }
                });
                builder.setOnCancelListener(new OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mSelectedTags.clear();
                    }
                });
                dialog = builder.create();
                break;
            // TODO(flerda@gmail.com): Fix card browser fields. See above.
            // https://code.google.com/p/ankidroid/issues/detail?id=1310
            /*
            case DIALOG_FIELD:
                builder.setTitle(res
                        .getString(R.string.card_browser_field_title));
                builder.setIcon(android.R.drawable.ic_menu_sort_by_size);

                HashMap<String, String> card = mAllCards.get(0);

                String[][] items = mCol.getCard(Long.parseLong( card.get("id") )).note().items();

                mFields = new String[items.length+1];
                mFields[0]="SFLD";

                for (int i = 0; i < items.length; i++) {
                    mFields[i+1] = items[i][0];
                }

                builder.setSingleChoiceItems(mFields, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int which) {
                        if (which != mField) {
                            mField = which;
                            AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit()
                                .putInt("cardBrowserField", mField).commit();
                            getCards();
                        }
                    }
                });
                dialog = builder.create();
                break;
            */
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
            case DIALOG_TAGS:
                mSelectedTags.clear();
                ad.setMultiChoiceItems(allTags, new boolean[allTags.length], new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String tag = allTags[which];
                        if (mSelectedTags.contains(tag)) {
                            Log.d(AnkiDroidApp.TAG, "unchecked tag: " + tag);
                            mSelectedTags.remove(tag);
                        } else {
                            Log.d(AnkiDroidApp.TAG, "checked tag: " + tag);
                            mSelectedTags.add(tag);
                        }
                    }
                });
                break;
        }
    }


    private void searchCards() {
        String searchText = mRestrictOnDeck + mSearchTerms;
        if (mCol != null) {
            mCards.clear();
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SEARCH_CARDS, mSearchCardsHandler, new DeckTask.TaskData(
                    new Object[] { mCol, mDeckNames, searchText, ((mOrder == CARD_ORDER_NONE) ? "" : "true") }));
        }
    }


    private void reloadCollection(final Bundle savedInstanceState) {
        DeckTask.launchDeckTask(
                DeckTask.TASK_TYPE_OPEN_COLLECTION,
                new DeckTask.TaskListener() {

                    @Override
                    public void onPostExecute(DeckTask.TaskData result) {
                        if (mOpenCollectionDialog.isShowing()) {
                            try {
                                mOpenCollectionDialog.dismiss();
                            } catch (Exception e) {
                                Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                            }
                        }
                        mCol = result.getCollection();
                        if (mCol == null) {
                            finish();
                        } else {
                            onCreate(savedInstanceState);
                        }
                    }
        
        
                    @Override
                    public void onPreExecute() {
                        mOpenCollectionDialog = StyledOpenCollectionDialog.show(
                                CardBrowser.this,
                                getResources().getString(R.string.open_collection),
                                new OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface arg0) {
                                        finish();
                                    }
                                });
                    }
        
        
                    @Override
                    public void onProgressUpdate(DeckTask.TaskData... values) {
                    }
                },
                new DeckTask.TaskData(AnkiDroidApp.getCurrentAnkiDroidDirectory() + AnkiDroidApp.COLLECTION_PATH));
    }


    private void updateList() {
        mCardsAdapter.notifyDataSetChanged();
        int count = mCards.size();
        AnkiDroidApp.getCompat().setSubtitle(
                this,
                getResources().getQuantityString(R.plurals.card_browser_subtitle, count, count, mTotalCount));
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
            pos = getPosition(mCards, c.getId());
            if (pos < 0 || pos >= mCards.size()) {
                continue;
            }

            if (updatedCardTags != null) {
                mCards.get(pos).put("tags", updatedCardTags);
            }

            String sfld = note.getSFld();
            mCards.get(pos).put("sfld", sfld);

            if (mWholeCollection) {
                String deckName;
                try {
                    deckName = mCol.getDecks().get(card.getDid()).getString("name");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                mCards.get(pos).put("deck", deckName);
            }

            String flags = Integer.toString((c.getQueue() == -1 ? 1 : 0) + (note.hasTag("marked") ? 2 : 0));
            mCards.get(pos).put("flags", flags);
        }
        updateList();
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

//    private DeckTask.TaskListener mLoadCardsHandler = new DeckTask.TaskListener() {
//        boolean canceled = false;
//
//
//        @Override
//        public void onPreExecute() {
//            if (!mUndoRedoDialogShowing) {
//                if (mProgressDialog != null && mProgressDialog.isShowing()) {
//                    mProgressDialog.setMessage(getResources().getString(R.string.card_browser_load));
//                    mProgressDialog.setOnCancelListener(new OnCancelListener() {
//
//                        @Override
//                        public void onCancel(DialogInterface arg0) {
//                            canceled = true;
//                            DeckTask.cancelTask();
//                            closeCardBrowser();
//                        }
//                    });
//                } else {
//                    mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
//                            getResources().getString(R.string.card_browser_load), true, true, new OnCancelListener() {
//
//                                @Override
//                                public void onCancel(DialogInterface arg0) {
//                                    canceled = true;
//                                    DeckTask.cancelTask();
//                                    closeCardBrowser();
//                                }
//                            });
//                }
//            } else {
//                mProgressDialog.setMessage(getResources().getString(R.string.card_browser_load));
//                mUndoRedoDialogShowing = false;
//            }
//        }
//
//
//        @Override
//        public void onPostExecute(DeckTask.TaskData result) {
//            // This verification would not be necessary if
//            // onConfigurationChanged it's executed correctly (which seems
//            // that emulator does not do)
//        }
//
//
//        @Override
//        public void onProgressUpdate(DeckTask.TaskData... values) {
//            if (canceled) {
//                return;
//            }
//            ArrayList<HashMap<String, String>> cards = values[0].getCards();
//            if (cards == null) {
//                Resources res = getResources();
//                StyledDialog.Builder builder = new StyledDialog.Builder(CardBrowser.this);
//                builder.setTitle(res.getString(R.string.error));
//                builder.setIcon(R.drawable.ic_dialog_alert);
//                builder.setMessage(res.getString(R.string.card_browser_cardloading_error));
//                builder.setPositiveButton(res.getString(R.string.ok), new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        closeCardBrowser();
//                    }
//                });
//                builder.setOnCancelListener(new OnCancelListener() {
//                    @Override
//                    public void onCancel(DialogInterface dialog) {
//                        closeCardBrowser();
//                    }
//                });
//                builder.create().show();
//            } else {
//                if (mPrefFixArabic) {
//                    for (HashMap<String, String> entry : cards) {
//                        entry.put("sfld", ArabicUtilities.reshapeSentence(entry.get("sfld")));
//                    }
//                }
//                try {
//
//                    int field = AnkiDroidApp.getSharedPrefs(getBaseContext()).getInt("cardBrowserField", 0);
//
//					if (cards.size() > 0 && field > 0 && (mFields != null)) {
//						Card tempCard = mCol.getCard(Long.parseLong(cards.get(0).get("id")));
//						ArrayList<String> uniqueFields = new ArrayList<String>();
//						for (HashMap<String, String> entry : cards) {
//							tempCard = mCol.getCard(Long.parseLong(entry.get("id")));
//							String item = tempCard.note().getitem(mFields[field]);
//							entry.put("sfld", item);
//
//							if (!uniqueFields.contains(item)) {
//								uniqueFields.add(item);
//								mAllCards.add(entry);
//								mCards.add(entry);
//							}
//						}
//					} else {
//						mAllCards.addAll(cards);
//						mCards.addAll(cards);
//					}
//
//                    if (mOrder == CARD_ORDER_NONE) {
//                        updateCardsList();
//                        mProgressDialog.dismiss();
//                    } else {
//                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_CARD_BROWSER_LIST, mSortCardsHandler,
//                                new DeckTask.TaskData(mAllCards, new HashMapCompare()));
//                    }
//                } catch (OutOfMemoryError e) {
//                    Log.e(AnkiDroidApp.TAG, "CardBrowser: mLoadCardsHandler: OutOfMemoryError: " + e);
//                    Themes.showThemedToast(CardBrowser.this,
//                            getResources().getString(R.string.error_insufficient_memory), false);
//                    closeCardBrowser();
//                }
//            }
//        }
//    };

    private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res.getString(R.string.saving_changes),
                    true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
//            // Update list if search involved marked
//            if (fSearchMarkedPattern.matcher(mSearchTerms).find()) {
//                updateCardsList();
//            }
            updateCardInList(values[0].getCard(), values[0].getString());
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (!result.getBoolean()) {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
            mProgressDialog.dismiss();

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
//                // Update list if search on suspended
//                if (fSearchSuspendedPattern.matcher(mSearchTerms).find()) {
//                    updateCardsList();
//                }
                updateCardInList(mCol.getCard(Long.parseLong(mCards.get(mPositionInCardsList).get("id"))), null);
            } else {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
            mProgressDialog.dismiss();

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
    };

    private DeckTask.TaskListener mSearchCardsHandler = new DeckTask.TaskListener() {
        @Override
        public void onProgressUpdate(TaskData... values) {
            mCards.clear();
            mCards.addAll(values[0].getCards());
            updateList();
        }


        @Override
        public void onPreExecute() {
            Resources res = getResources();
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setMessage(res.getString(R.string.card_browser_filtering_cards));
            } else {
                mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
                        res.getString(R.string.card_browser_filtering_cards), true);
            }
        }


        @Override
        public void onPostExecute(TaskData result) {
            mTotalCount = result.getInt();
            updateList();
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }
    };

    private DeckTask.TaskListener mReloadCardsHandler = new DeckTask.TaskListener() {
        @Override
        public void onProgressUpdate(TaskData... values) {
        }


        @Override
        public void onPreExecute() {
            Resources res = getResources();
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setMessage(res.getString(R.string.card_browser_load));
            } else {
                mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
                        res.getString(R.string.card_browser_load), true);
            }
        }


        @Override
        public void onPostExecute(TaskData result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            mCards.clear();
            mCards.addAll(result.getCards());
            updateList();
        }
    };

    private DeckTask.TaskListener mSortCardsHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setMessage(res.getString(R.string.card_browser_sorting_cards));
            } else {
                mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
                        res.getString(R.string.card_browser_sorting_cards), true);
            }
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            // FIXME:
            searchCards();
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }
    };


    // private DeckTask.TaskListener mUndoRedoHandler = new DeckTask.TaskListener() {
    // @Override
    // public void onPreExecute() {
    // Resources res = getResources();
    // mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res
    // .getString(R.string.saving_changes), true);
    // }
    //
    // @Override
    // public void onProgressUpdate(DeckTask.TaskData... values) {
    // }
    //
    // @Override
    // public void onPostExecute(DeckTask.TaskData result) {
    // mUndoRedoCardId = result.getLong();
    // String undoType = result.getString();
    // if (undoType.equals(Deck.UNDO_TYPE_DELETE_CARD)) {
    // int position = getPosition(mDeletedCards, mUndoRedoCardId);
    // if (position != -1) {
    // HashMap<String, String> data = new HashMap<String, String>();
    // data.put("id", mDeletedCards.get(position).get("id"));
    // data.put("question", mDeletedCards.get(position).get(
    // "question"));
    // data.put("answer", mDeletedCards.get(position)
    // .get("answer"));
    // data.put("flags", mDeletedCards.get(position)
    // .get("flags"));
    // mAllCards.add(Integer.parseInt(mDeletedCards.get(position)
    // .get("allCardPos")), data);
    // mDeletedCards.remove(position);
    // updateCardsList();
    // } else {
    // deleteCard(Long.toString(mUndoRedoCardId), getPosition(
    // mCards, mUndoRedoCardId));
    // }
    // mProgressDialog.dismiss();
    // } else {
    // mUndoRedoCard = mDeck.cardFromId(mUndoRedoCardId);
    // if (undoType.equals(Deck.UNDO_TYPE_EDIT_CARD)) {
    // mUndoRedoCard.fromDB(mUndoRedoCardId);
    // int pos = getPosition(mAllCards, mUndoRedoCardId);
    // updateCard(mUndoRedoCard, mAllCards, pos);
    // pos = getPosition(mCards, mUndoRedoCardId);
    // if (pos != -1) {
    // updateCard(mUndoRedoCard, mCards, pos);
    // }
    // updateList();
    // mProgressDialog.dismiss();
    // } else if (undoType.equals(Deck.UNDO_TYPE_MARK_CARD)) {
    // markCards(mUndoRedoCard.getFactId(), mUndoRedoCard
    // .isMarked());
    // mProgressDialog.dismiss();
    // } else if (undoType.equals(Deck.UNDO_TYPE_SUSPEND_CARD)) {
    // suspendCard(mUndoRedoCard, getPosition(mCards,
    // mUndoRedoCardId), mUndoRedoCard.getSuspendedState());
    // mProgressDialog.dismiss();
    // } else {
    // mUndoRedoDialogShowing = true;
    // getCards();
    // }
    // }
    // }
    // };

    private void closeCardBrowser() {
        closeCardBrowser(RESULT_CANCELED);
    }


    private void closeCardBrowser(int result) {
        setResult(result);
        finish();
        if (AnkiDroidApp.SDK_VERSION > 4) {
            ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.RIGHT);
        }
    }

    public class SizeControlledListAdapter extends SimpleAdapter {

        private int fontSizeScalePcent;
        private float originalTextSize = -1.0f;
        private Typeface mCustomTypeface = null;


        public SizeControlledListAdapter(Context context, List<? extends Map<String, ?>> data, int resource,
                String[] from, int[] to, int fontSizeScalePcent, String customFont) {
            super(context, data, resource, from, to);
            this.fontSizeScalePcent = fontSizeScalePcent;
            if (!customFont.equals("")) {
                mCustomTypeface = AnkiFont.getTypeface(context, customFont);
            }
        }


        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            // Iterate on all first level children
            if (view instanceof ViewGroup) {
                ViewGroup group = ((ViewGroup) view);
                View child;
                for (int i = 0; i < group.getChildCount(); i++) {
                    child = group.getChildAt(i);
                    // and set text size and custom font on the sfld view only
                    if (child instanceof TextView && child.getId() == R.id.card_sfld) {
                        float currentSize = ((TextView) child).getTextSize();
                        if (originalTextSize < 0) {
                            originalTextSize = ((TextView) child).getTextSize();
                        }
                        // do nothing when pref is 100% and apply scaling only once
                        if (fontSizeScalePcent != 100 && Math.abs(originalTextSize - currentSize) < 0.1) {
                            ((TextView) child).setTextSize(TypedValue.COMPLEX_UNIT_SP, originalTextSize
                                    * (fontSizeScalePcent / 100.0f));
                        }

                        if (mCustomTypeface != null) {
                            ((TextView) child).setTypeface(mCustomTypeface);
                        }
                    }
                }
            }
            return view;
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
                        finish();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT);
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }

}
