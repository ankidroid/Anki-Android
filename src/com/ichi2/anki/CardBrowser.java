/****************************************************************************************
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.amr.arabic.ArabicUtilities;

public class CardBrowser extends Activity {
    private ArrayList<HashMap<String, String>> mCards;
    private ArrayList<HashMap<String, String>> mAllCards;
    private ArrayList<HashMap<String, String>> mDeletedCards;
    private ListView mCardsListView;
    private SimpleAdapter mCardsAdapter;
    private EditText mSearchEditText;

    private AlertDialog mSelectOrderDialog;
    private ProgressDialog mProgressDialog;
    private boolean mUndoRedoDialogShowing = false;
    private Card mSelectedCard;
    private Card mUndoRedoCard;
    private long mUndoRedoCardId;
    private static Card sEditorCard;
    private boolean mIsSuspended;
    private boolean mIsMarked;
    private Deck mDeck;
    private int mPositionInCardsList;

    /** Modifier of percentage of the font size of the card browser */
    private int mrelativeBrowserFontSize = CardModel.DEFAULT_FONT_SIZE_RATIO;

    private static final int CONTEXT_MENU_MARK = 0;
    private static final int CONTEXT_MENU_SUSPEND = 1;
    private static final int CONTEXT_MENU_DELETE = 2;
    private static final int CONTEXT_MENU_DETAILS = 3;

    private static final int MENU_UNDO = 0;
    private static final int MENU_REDO = 1;
    private static final int MENU_ADD_FACT = 2;
    private static final int MENU_SHOW_MARKED = 3;
    private static final int MENU_SELECT = 4;
    private static final int MENU_SELECT_SUSPENDED = 41;
    private static final int MENU_SELECT_TAG = 42;
    private static final int MENU_CHANGE_ORDER = 5;

    private static final int EDIT_CARD = 0;
    private static final int ADD_FACT = 1;
    private static final int DEFAULT_FONT_SIZE_RATIO = 100;

    private int markedColor;
    private int suspendedColor;

    private boolean mShowOnlyMarSus = false;

    private int mSelectedOrder = 5;

    private String[] allTags;
    private HashSet<String> mSelectedTags;
    private AlertDialog mTagsDialog;
    
    private boolean mPrefFixArabic;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Themes.applyTheme(this);
        super.onCreate(savedInstanceState);

        View mainView = getLayoutInflater().inflate(R.layout.card_browser, null);
        setContentView(mainView);

        mDeck = AnkiDroidApp.deck();
        mDeck.resetUndo();

        markedColor = getResources().getColor(R.color.card_browser_marked);
        suspendedColor = getResources().getColor(R.color.card_browser_suspended);
        if (Themes.getTheme() == 2) {
            mainView.setBackgroundResource(Themes.getBackgroundColor());        	
        }

        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        mrelativeBrowserFontSize = preferences.getInt("relativeCardBrowserFontSize", DEFAULT_FONT_SIZE_RATIO);
        mPrefFixArabic = preferences.getBoolean("fixArabicText", false);

        mCards = new ArrayList<HashMap<String, String>>();
        mAllCards = new ArrayList<HashMap<String, String>>();
        mCardsListView = (ListView) findViewById(R.id.card_browser_list);

        mCardsAdapter = new SizeControlledListAdapter(this, mCards, R.layout.card_item, new String[] { "question",
                "answer", "marSus" }, new int[] { R.id.card_question, R.id.card_answer, R.id.card_item },
                mrelativeBrowserFontSize);
        mCardsAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object arg1, String text) {
                if (view.getId() == R.id.card_item) {
                    if (text.substring(1, 2).equals("1")) {
                        view.setBackgroundColor(suspendedColor);
                    } else if (text.substring(0, 1).equals("1")) {
                        view.setBackgroundColor(markedColor);
                    }
                    return true;
                }
                return false;
            }
        });

        mCardsListView.setAdapter(mCardsAdapter);
        mCardsListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent editCard = new Intent(CardBrowser.this, CardEditor.class);
                mPositionInCardsList = position;
                mSelectedCard = mDeck.cardFromId(Long.parseLong(mCards.get(mPositionInCardsList).get("id")));
                sEditorCard = mSelectedCard;
                editCard.putExtra("callfromcardbrowser", true);
                startActivityForResult(editCard, EDIT_CARD);
                if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                    ActivityTransitionAnimation.slide(CardBrowser.this, ActivityTransitionAnimation.LEFT);
                }
            }
        });
        registerForContextMenu(mCardsListView);

        mSearchEditText = (EditText) findViewById(R.id.card_browser_search);
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                updateCardsList();
            }


            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }


            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        setTitle(mDeck.getDeckName());

        initAllDialogs();
        allTags = null;
        mSelectedTags = new HashSet<String>();

        getCards();
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        int selectedPosition = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
        Resources res = getResources();
        @SuppressWarnings("unused")
        MenuItem item;
        mSelectedCard = mDeck.cardFromId(Long.parseLong(mCards.get(selectedPosition).get("id")));
        if (mSelectedCard.isMarked()) {
            item = menu.add(Menu.NONE, CONTEXT_MENU_MARK, Menu.NONE, res.getString(R.string.card_browser_unmark_card));
            mIsMarked = true;
            Log.i(AnkiDroidApp.TAG, "Selected Card is currently marked");
        } else {
            item = menu.add(Menu.NONE, CONTEXT_MENU_MARK, Menu.NONE, res.getString(R.string.card_browser_mark_card));
            mIsMarked = false;
        }
        if (mSelectedCard.getSuspendedState()) {
            item = menu.add(Menu.NONE, CONTEXT_MENU_SUSPEND, Menu.NONE,
                    res.getString(R.string.card_browser_unsuspend_card));
            mIsSuspended = true;
            Log.i(AnkiDroidApp.TAG, "Selected Card is currently suspended");
        } else {
            item = menu.add(Menu.NONE, CONTEXT_MENU_SUSPEND, Menu.NONE,
                    res.getString(R.string.card_browser_suspend_card));
            mIsSuspended = false;
        }
        item = menu.add(Menu.NONE, CONTEXT_MENU_DELETE, Menu.NONE, res.getString(R.string.card_browser_delete_card));
        item = menu.add(Menu.NONE, CONTEXT_MENU_DETAILS, Menu.NONE, res.getString(R.string.card_browser_card_details));
        menu.setHeaderTitle(mCards.get(selectedPosition).get("question"));
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
            case CONTEXT_MENU_MARK:
                mPositionInCardsList = info.position;
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(0, mDeck,
                        mSelectedCard));
                return true;
            case CONTEXT_MENU_SUSPEND:
                mPositionInCardsList = info.position;
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SUSPEND_CARD, mSuspendCardHandler, new DeckTask.TaskData(0,
                        mDeck, mSelectedCard));
                return true;
            case CONTEXT_MENU_DELETE:
                mPositionInCardsList = info.position;

                Dialog dialog;
                Resources res = getResources();
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(res.getString(R.string.delete_card_title));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(String.format(res.getString(R.string.delete_card_message),
                        mCards.get(mPositionInCardsList).get("question"), mCards.get(mPositionInCardsList)
                                .get("answer")));
                builder.setPositiveButton(res.getString(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_CARD, mDeleteCardHandler,
                                new DeckTask.TaskData(0, mDeck, mSelectedCard));
                    }
                });
                builder.setNegativeButton(res.getString(R.string.no), null);
                dialog = builder.create();
                dialog.show();
                return true;
            case CONTEXT_MENU_DETAILS:
                AlertDialog.Builder detailsbuilder = new AlertDialog.Builder(this);
                detailsbuilder.setPositiveButton(getResources().getString(R.string.ok), null);
                View contentView = getLayoutInflater().inflate(R.layout.dialog_webview, null);
                WebView detailsWebView = (WebView) contentView.findViewById(R.id.dialog_webview);
                detailsWebView.loadDataWithBaseURL("", mSelectedCard.getCardDetails(this), "text/html", "utf-8", null);
                detailsWebView.setBackgroundColor(getResources().getColor(R.color.card_browser_background));
                detailsbuilder.setView(contentView);
                detailsbuilder.create().show();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "CardBrowser - onBackPressed()");
            if (mSearchEditText.getText().length() == 0 && !mShowOnlyMarSus && mSelectedTags.size() == 0) {
                setResult(RESULT_OK);
                finish();
                if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                    ActivityTransitionAnimation.slide(CardBrowser.this, ActivityTransitionAnimation.RIGHT);
                }
            } else {
                mSearchEditText.setText("");
                mSearchEditText.setHint(R.string.downloaddeck_search);
                mSelectedTags.clear();
                mCards.clear();
                mCards.addAll(mAllCards);
                updateList();
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        item = menu.add(Menu.NONE, MENU_UNDO, Menu.NONE, R.string.undo);
        item.setIcon(R.drawable.ic_menu_revert);
        item = menu.add(Menu.NONE, MENU_REDO, Menu.NONE, R.string.redo);
        item.setIcon(R.drawable.ic_menu_redo);
        item = menu.add(Menu.NONE, MENU_ADD_FACT, Menu.NONE, R.string.add);
        item.setIcon(R.drawable.ic_menu_add);
        item = menu.add(Menu.NONE, MENU_CHANGE_ORDER, Menu.NONE, R.string.card_browser_change_display_order);
        item.setIcon(R.drawable.ic_menu_sort_by_size);
        item = menu.add(Menu.NONE, MENU_SHOW_MARKED, Menu.NONE, R.string.card_browser_show_marked);
        item.setIcon(R.drawable.ic_menu_star_on);
        SubMenu selectSubMenu = menu.addSubMenu(Menu.NONE, MENU_SELECT, Menu.NONE, R.string.card_browser_search);
        selectSubMenu.setIcon(R.drawable.ic_menu_search);
        selectSubMenu.add(Menu.NONE, MENU_SELECT_SUSPENDED, Menu.NONE, R.string.card_browser_search_suspended);
        selectSubMenu.add(Menu.NONE, MENU_SELECT_TAG, Menu.NONE, R.string.card_browser_search_by_tag);
        item.setIcon(R.drawable.ic_menu_close_clear_cancel);
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(MENU_UNDO).setEnabled(mDeck.undoAvailable());
        menu.findItem(MENU_REDO).setEnabled(mDeck.redoAvailable());
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_UNDO:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUndoRedoHandler, new DeckTask.TaskData(0, mDeck, 0,
                        true));
                return true;
            case MENU_REDO:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REDO, mUndoRedoHandler, new DeckTask.TaskData(0, mDeck, 0,
                        true));
                return true;
            case MENU_ADD_FACT:
                startActivityForResult(new Intent(CardBrowser.this, FactAdder.class), ADD_FACT);
                if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                    ActivityTransitionAnimation.slide(CardBrowser.this, ActivityTransitionAnimation.LEFT);
                }
                return true;
            case MENU_SHOW_MARKED:
                mShowOnlyMarSus = true;
                mSearchEditText.setHint(R.string.card_browser_show_marked);
                mCards.clear();
                for (int i = 0; i < mAllCards.size(); i++) {
                    if ((mAllCards.get(i).get("question").toLowerCase()
                            .indexOf(mSearchEditText.getText().toString().toLowerCase()) != -1 || mAllCards.get(i)
                            .get("answer").toLowerCase().indexOf(mSearchEditText.getText().toString().toLowerCase()) != -1)
                            && mAllCards.get(i).get("marSus").subSequence(0, 1).equals("1")) {
                        mCards.add(mAllCards.get(i));
                    }
                }
                updateList();
                return true;
            case MENU_SELECT_SUSPENDED:
                mShowOnlyMarSus = true;
                mSearchEditText.setHint(R.string.card_browser_show_suspended);
                mCards.clear();
                for (int i = 0; i < mAllCards.size(); i++) {
                    if ((mAllCards.get(i).get("question").toLowerCase()
                            .indexOf(mSearchEditText.getText().toString().toLowerCase()) != -1 || mAllCards.get(i)
                            .get("answer").toLowerCase().indexOf(mSearchEditText.getText().toString().toLowerCase()) != -1)
                            && mAllCards.get(i).get("marSus").subSequence(1, 2).equals("1")) {
                        mCards.add(mAllCards.get(i));
                    }
                }
                updateList();
                return true;
            case MENU_SELECT_TAG:
                recreateTagsDialog();
                mTagsDialog.show();
                return true;
            case MENU_CHANGE_ORDER:
                mSelectOrderDialog.show();
                return true;
        }
        return false;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == EDIT_CARD && resultCode == RESULT_OK) {
            Log.i(AnkiDroidApp.TAG, "Saving card...");
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler, new DeckTask.TaskData(0, mDeck,
                    mSelectedCard));
            // TODO: code to save the changes made to the current card.
        } else if (requestCode == ADD_FACT && resultCode == RESULT_OK) {
            getCards();
        }
    }


    private void initAllDialogs() {
        Resources res = getResources();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(res.getString(R.string.card_browser_change_display_order_title));
        builder.setIcon(android.R.drawable.ic_menu_sort_by_size);
        builder.setSingleChoiceItems(getResources().getStringArray(R.array.card_browser_order_labels), mSelectedOrder,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mSelectedOrder = whichButton;
                        mSelectOrderDialog.dismiss();
                        getCards();
                    }
                });
        mSelectOrderDialog = builder.create();
    }


    private void recreateTagsDialog() {
        Resources res = getResources();
        if (allTags == null) {
            String[] oldTags = AnkiDroidApp.deck().allTags_();
            Log.i(AnkiDroidApp.TAG, "all tags: " + Arrays.toString(oldTags));
            allTags = new String[oldTags.length];
            for (int i = 0; i < oldTags.length; i++) {
                allTags[i] = oldTags[i];
            }
        }
        mSelectedTags.clear();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.studyoptions_limit_select_tags);
        builder.setMultiChoiceItems(allTags, null, new DialogInterface.OnMultiChoiceClickListener() {
            public void onClick(DialogInterface dialog, int whichButton, boolean isChecked) {
                String tag = allTags[whichButton];
                if (!isChecked) {
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
                updateCardsList();
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
        mTagsDialog = builder.create();
    }


    private void updateCardsList() {
        mShowOnlyMarSus = false;
        if (mSelectedTags.size() == 0) {
            mSearchEditText.setHint(R.string.downloaddeck_search);
        } else {
            String tags = mSelectedTags.toString();
            mSearchEditText.setHint(getResources().getString(R.string.card_browser_tags_shown,
                    tags.substring(1, tags.length() - 1)));
        }
        mCards.clear();
        if (mSearchEditText.getText().length() == 0 && mSelectedTags.size() == 0 && mSelectedTags.size() == 0) {
            mCards.addAll(mAllCards);
        } else {
            for (int i = 0; i < mAllCards.size(); i++) {
                if ((mAllCards.get(i).get("question").toLowerCase()
                        .indexOf(mSearchEditText.getText().toString().toLowerCase()) != -1 || mAllCards.get(i)
                        .get("answer").toLowerCase().indexOf(mSearchEditText.getText().toString().toLowerCase()) != -1)
                        && Arrays.asList(Utils.parseTags(mAllCards.get(i).get("tags"))).containsAll(mSelectedTags)) {
                    mCards.add(mAllCards.get(i));
                }
            }
        }
        updateList();
    }


    private void getCards() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_CARDS, mLoadCardsHandler, new DeckTask.TaskData(mDeck,
                getResources().getStringArray(R.array.card_browser_order_values)[mSelectedOrder]));
    }


    public static Card getEditorCard() {
        return sEditorCard;
    }


    private void updateList() {
        mCardsAdapter.notifyDataSetChanged();
        int count = mCards.size();
        setTitle(getResources().getQuantityString(R.plurals.card_browser_title, count, mDeck.getDeckName(), count,
                mAllCards.size()));
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


    private void updateCard(Card card, ArrayList<HashMap<String, String>> list, int position) {
        list.get(position).put("question", Utils.stripHTML(card.getQuestion()));
        list.get(position).put("answer", Utils.stripHTML(card.getAnswer()));
        for (long cardId : mDeck.getCardsFromFactId(card.getFactId())) {
        	if (cardId != card.getId()) {
        		int positionC = getPosition(mCards, cardId);
                int positionA = getPosition(mAllCards, cardId);
                Card c = mDeck.cardFromId(cardId);
                String question = Utils.stripHTML(c.getQuestion());
                String answer = Utils.stripHTML(c.getAnswer());
                if (positionC != -1) {
                    mCards.get(positionC).put("question", question);
                    mCards.get(positionC).put("answer", answer);
                }
                mAllCards.get(positionA).put("question", question);
                mAllCards.get(positionA).put("answer", answer);
        	}
        }
    }


    private void markCards(long factId, boolean mark) {
        for (long cardId : mDeck.getCardsFromFactId(factId)) {
            int positionC = getPosition(mCards, cardId);
            int positionA = getPosition(mAllCards, cardId);
            String marSus = mAllCards.get(positionA).get("marSus");
            if (mark) {
                marSus = "1" + marSus.substring(1, 2);
                if (positionC != -1) {
                    mCards.get(positionC).put("marSus", marSus);
                }
                mAllCards.get(positionA).put("marSus", marSus);
            } else {
                marSus = "0" + marSus.substring(1, 2);
                if (positionC != -1) {
                    mCards.get(positionC).put("marSus", marSus);
                }
                mAllCards.get(positionA).put("marSus", marSus);
            }
        }
        updateList();
    }


    private void suspendCard(Card card, int position, boolean suspend) {
        int posA = getPosition(mAllCards, card.getId());
        String marSus = mAllCards.get(posA).remove("marSus");
        if (suspend) {
            marSus = marSus.substring(0, 1) + "1";
            if (position != -1) {
                mCards.get(position).put("marSus", marSus);
            }
            mAllCards.get(posA).put("marSus", marSus);
        } else {
            marSus = marSus.substring(0, 1) + "0";
            if (position != -1) {
                mCards.get(position).put("marSus", marSus);
            }
            mAllCards.get(posA).put("marSus", marSus);
        }
        updateList();
    }


    private void deleteCard(String cardId, int position) {
        if (mDeletedCards == null) {
            mDeletedCards = new ArrayList<HashMap<String, String>>();
        }
        HashMap<String, String> data = new HashMap<String, String>();
        for (int i = 0; i < mAllCards.size(); i++) {
            if (mAllCards.get(i).get("id").equals(cardId)) {
                data.put("id", mAllCards.get(i).get("id"));
                data.put("question", mAllCards.get(i).get("question"));
                data.put("answer", mAllCards.get(i).get("answer"));
                data.put("marSus", mAllCards.get(i).get("marSus"));
                data.put("allCardPos", Integer.toString(i));
                mDeletedCards.add(data);
                mAllCards.remove(i);
                Log.i(AnkiDroidApp.TAG, "Remove card from list");
                break;
            }
        }
        mCards.remove(position);
        updateList();
    }

    private DeckTask.TaskListener mLoadCardsHandler = new DeckTask.TaskListener() {

        @Override
        public void onPreExecute() {
            if (!mUndoRedoDialogShowing) {
                mProgressDialog = ProgressDialog.show(CardBrowser.this, "",
                        getResources().getString(R.string.card_browser_load), true);
            } else {
                mProgressDialog.setMessage(getResources().getString(R.string.card_browser_load));
                mUndoRedoDialogShowing = false;
            }
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mAllCards.clear();
            ArrayList<String[]> allCards = values[0].getAllCards();
            if (allCards == null) {
                Resources res = getResources();
                AlertDialog.Builder builder = new AlertDialog.Builder(CardBrowser.this);
                builder.setTitle(res.getString(R.string.error));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(res.getString(R.string.card_browser_cardloading_error));
                builder.setPositiveButton(res.getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CardBrowser.this.finish();
                    }
                });
                builder.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        CardBrowser.this.finish();
                    }
                });
                builder.create().show();
            } else {
                for (String[] item : allCards) {
                	// reshape Arabic words
                	if(mPrefFixArabic) {
                		item[1] = ArabicUtilities.reshapeSentence(item[1]);
                		item[2] = ArabicUtilities.reshapeSentence(item[2]);
                	}
                	
                    HashMap<String, String> data = new HashMap<String, String>();
                    data.put("id", item[0]);
                    data.put("question", item[1]);
                    data.put("answer", item[2]);
                    data.put("marSus", item[3]);
                    data.put("tags", item[4]);
                    mAllCards.add(data);
                }
                updateCardsList();
            }

            // This verification would not be necessary if
            // onConfigurationChanged it's executed correctly (which seems
            // that emulator does not do)
            if (mProgressDialog.isShowing()) {
                try {
                    mProgressDialog.dismiss();
                } catch (Exception e) {
                    Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                }
            }
        }
    };

    private DeckTask.TaskListener mMarkCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = ProgressDialog.show(CardBrowser.this, "", res.getString(R.string.saving_changes), true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mSelectedCard = values[0].getCard();
            markCards(mSelectedCard.getFactId(), !mIsMarked);
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            mProgressDialog.dismiss();

        }
    };

    private DeckTask.TaskListener mSuspendCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = ProgressDialog.show(CardBrowser.this, "", res.getString(R.string.saving_changes), true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            suspendCard(mSelectedCard, mPositionInCardsList, !mIsSuspended);
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            mProgressDialog.dismiss();
        }
    };

    private DeckTask.TaskListener mDeleteCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = ProgressDialog.show(CardBrowser.this, "", res.getString(R.string.saving_changes), true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            deleteCard(result.getString(), mPositionInCardsList);
            mProgressDialog.dismiss();

        }
    };

    private DeckTask.TaskListener mUndoRedoHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = ProgressDialog.show(CardBrowser.this, "", res.getString(R.string.saving_changes), true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            mUndoRedoCardId = result.getLong();
            String undoType = result.getString();
            if (undoType.equals(Deck.UNDO_TYPE_DELETE_CARD)) {
                int position = getPosition(mDeletedCards, mUndoRedoCardId);
                if (position != -1) {
                    HashMap<String, String> data = new HashMap<String, String>();
                    data.put("id", mDeletedCards.get(position).get("id"));
                    data.put("question", mDeletedCards.get(position).get("question"));
                    data.put("answer", mDeletedCards.get(position).get("answer"));
                    data.put("marSus", mDeletedCards.get(position).get("marSus"));
                    mAllCards.add(Integer.parseInt(mDeletedCards.get(position).get("allCardPos")), data);
                    mDeletedCards.remove(position);
                    updateCardsList();
                } else {
                    deleteCard(Long.toString(mUndoRedoCardId), getPosition(mCards, mUndoRedoCardId));
                }
                mProgressDialog.dismiss();
            } else {
                mUndoRedoCard = mDeck.cardFromId(mUndoRedoCardId);
                if (undoType.equals(Deck.UNDO_TYPE_EDIT_CARD)) {
                    mUndoRedoCard.fromDB(mUndoRedoCardId);
                    int pos = getPosition(mAllCards, mUndoRedoCardId);
                    updateCard(mUndoRedoCard, mAllCards, pos);
                    pos = getPosition(mCards, mUndoRedoCardId);
                    if (pos != -1) {
                        updateCard(mUndoRedoCard, mCards, pos);
                    }
                    updateList();
                    mProgressDialog.dismiss();
                } else if (undoType.equals(Deck.UNDO_TYPE_MARK_CARD)) {
                    markCards(mUndoRedoCard.getFactId(), mUndoRedoCard.isMarked());
                    mProgressDialog.dismiss();
                } else if (undoType.equals(Deck.UNDO_TYPE_SUSPEND_CARD)) {
                    suspendCard(mUndoRedoCard, getPosition(mCards, mUndoRedoCardId), mUndoRedoCard.getSuspendedState());
                    mProgressDialog.dismiss();
                } else {
                    mUndoRedoDialogShowing = true;
                    getCards();
                }
            }
        }
    };

    private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            mProgressDialog = ProgressDialog.show(CardBrowser.this, "",
                    getResources().getString(R.string.saving_changes), true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mSelectedCard.fromDB(mSelectedCard.getId());
            int pos = getPosition(mAllCards, mSelectedCard.getId());
            updateCard(mSelectedCard, mAllCards, pos);
            pos = getPosition(mCards, mSelectedCard.getId());
            if (pos != -1) {
                updateCard(mSelectedCard, mCards, pos);
            }
            updateList();
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            mProgressDialog.dismiss();
        }
    };

    public class SizeControlledListAdapter extends SimpleAdapter {

        private int fontSizeScalePcent;
        private float originalTextSize = -1.0f;


        public SizeControlledListAdapter(Context context, List<? extends Map<String, ?>> data, int resource,
                String[] from, int[] to, int fontSizeScalePcent) {
            super(context, data, resource, from, to);
            this.fontSizeScalePcent = fontSizeScalePcent;

        }


        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            // Iterate on all first level children
            if (view instanceof ViewGroup) {
                ViewGroup group = ((ViewGroup) view);
                View child;
                for (int i = 0; i < group.getChildCount(); i++) {
                    child = group.getChildAt(i);
                    // and set text size on all TextViews found
                    if (child instanceof TextView) {
                        // mBrowserFontSize
                        float currentSize = ((TextView) child).getTextSize();
                        if (originalTextSize < 0) {
                            originalTextSize = ((TextView) child).getTextSize();
                        }
                        // do nothing when pref is 100% and apply scaling only
                        // once
                        if ((fontSizeScalePcent < 99 || fontSizeScalePcent > 101)
                                && (Math.abs(originalTextSize - currentSize) < 0.1)) {
                            ((TextView) child).setTextSize(TypedValue.COMPLEX_UNIT_SP, originalTextSize
                                    * (fontSizeScalePcent / 100.0f));
                        }
                    }

                }

            }

            return view;
        }

    }

}
