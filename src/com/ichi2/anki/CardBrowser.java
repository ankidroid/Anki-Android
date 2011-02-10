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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;
import java.util.HashMap;

public class CardBrowser extends Activity {
    private ArrayList<HashMap<String, String>> mCards;
    private ArrayList<HashMap<String, String>> mAllCards;
    private ArrayList<HashMap<String, String>> mDeletedCards;
    private ListView mCardsListView;
    private SimpleAdapter mCardsAdapter;
    private EditText mSearchEditText;
    
    private ProgressDialog mProgressDialog;
    private boolean mUndoRedoDialogShowing = false;
    private Card mSelectedCard;
    private static Card sEditorCard;
    private boolean mIsSuspended;
    private boolean mIsMarked;
    private Deck mDeck;
    private int mPositionInCardsList;
    
    private static final int CONTEXT_MENU_MARK = 0;
    private static final int CONTEXT_MENU_SUSPEND = 1;
    private static final int CONTEXT_MENU_DELETE = 2;

    private static final int MENU_UNDO = 0;
    private static final int MENU_REDO = 1;

    private static final int EDIT_CARD = 0;
    
    private int markedColor;
    private int suspendedColor;
    private int backgroundColor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.card_browser);
        mDeck = AnkiDroidApp.deck();

        markedColor = getResources().getColor(R.color.card_browser_marked);
        suspendedColor = getResources().getColor(R.color.card_browser_suspended);
        backgroundColor = getResources().getColor(R.color.card_browser_background);
        
        mCards = new ArrayList<HashMap<String, String>>();
        mAllCards = new ArrayList<HashMap<String, String>>();
        mCardsListView = (ListView) findViewById(R.id.card_browser_list);  
        
        mCardsAdapter = new SimpleAdapter(this, mCards,
                R.layout.card_item, new String[] { "question", "answer", "marSus" }, new int[] {
                        R.id.card_question, R.id.card_answer, R.id.card_item });
        mCardsAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object arg1, String text) {
                if (view.getId() == R.id.card_item) {
                    if (text.substring(1, 2).equals("1")) {
                        view.setBackgroundColor(suspendedColor);
                    } else if (text.substring(0, 1).equals("1")) {
                        view.setBackgroundColor(markedColor);
                    } else {
                        view.setBackgroundColor(backgroundColor);
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
                    MyAnimation.slide(CardBrowser.this, MyAnimation.LEFT);
                }
            }
        });
        registerForContextMenu(mCardsListView);

        mSearchEditText = (EditText) findViewById(R.id.card_browser_search);
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                updateCardsList();
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            public void onTextChanged(CharSequence s, int start, int before, int count){}
        });
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        
        setTitle(mDeck.getDeckName());

        getCards(Deck.ORDER_BY_ANSWER);
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        int selectedPosition = ((AdapterView.AdapterContextMenuInfo)menuInfo).position;
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
            item = menu.add(Menu.NONE, CONTEXT_MENU_SUSPEND, Menu.NONE, res.getString(R.string.card_browser_unsuspend_card));
            mIsSuspended = true;
            Log.i(AnkiDroidApp.TAG, "Selected Card is currently suspended");
        } else {
            item = menu.add(Menu.NONE, CONTEXT_MENU_SUSPEND, Menu.NONE, res.getString(R.string.card_browser_suspend_card));
            mIsSuspended = false;
        }
        item = menu.add(Menu.NONE, CONTEXT_MENU_DELETE, Menu.NONE, res.getString(R.string.card_browser_delete_card));
        menu.setHeaderTitle(mCards.get(selectedPosition).get("question"));
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        
        switch (item.getItemId()) {
        case CONTEXT_MENU_MARK:
            mPositionInCardsList = info.position;
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(0, mDeck, mSelectedCard));
            return true;
        case CONTEXT_MENU_SUSPEND:
            mPositionInCardsList = info.position;
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SUSPEND_CARD, mSuspendCardHandler, new DeckTask.TaskData(0, mDeck, mSelectedCard));
            return true;
        case CONTEXT_MENU_DELETE:
            mPositionInCardsList = info.position;

            Dialog dialog;
            Resources res = getResources();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(res.getString(R.string.delete_card_title));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(String.format(res.getString(R.string.delete_card_message), mCards.get(mPositionInCardsList).get("question"), mCards.get(mPositionInCardsList).get("answer")));
            builder.setPositiveButton(res.getString(R.string.yes),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_CARD, mDeleteCardHandler, new DeckTask.TaskData(mSelectedCard));
                        }
                    });
            builder.setNegativeButton(res.getString(R.string.no), null);
            dialog = builder.create();
            dialog.show();
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "CardBrowser - onBackPressed()");
            if (mSearchEditText.getText().length() == 0) {
                setResult(RESULT_OK);
                finish();
                if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                    MyAnimation.slide(CardBrowser.this, MyAnimation.RIGHT);
                }                
            } else {
                mSearchEditText.setText("");
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
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUndoRedoHandler, new DeckTask.TaskData(0,
                        mDeck, mSelectedCard));
                return true;
            case MENU_REDO:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REDO, mUndoRedoHandler, new DeckTask.TaskData(0,
                        mDeck, mSelectedCard));
                return true;
        }
        return false;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == EDIT_CARD && resultCode == RESULT_OK) {
            Log.i(AnkiDroidApp.TAG, "Saving card...");
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler, new DeckTask.TaskData(0,
                    mDeck, mSelectedCard));
            // TODO: code to save the changes made to the current card.
        }
    }


    private void updateCardsList() {
        mCards.clear();
        if (mSearchEditText.getText().length() == 0) {
            mCards.addAll(mAllCards);
        } else {
            for (int i = 0; i < mAllCards.size(); i++) {
                if (mAllCards.get(i).get("question").toLowerCase().indexOf(mSearchEditText.getText().toString().toLowerCase()) != -1 ||
                    mAllCards.get(i).get("answer").toLowerCase().indexOf(mSearchEditText.getText().toString().toLowerCase()) != -1) { 
                    mCards.add(mAllCards.get(i));
                }
            }                    
        }
        updateList();
    }


    private void getCards(String order) {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_CARDS, mLoadCardsHandler, new DeckTask.TaskData(mDeck, order));
    }
    
    
    public static Card getEditorCard() {
        return sEditorCard;
    }


    private void updateList() {
        mCardsAdapter.notifyDataSetChanged();
        setTitle(String.format(getResources().getString(R.string.card_browser_title), mDeck.getDeckName(), mCards.size(), mAllCards.size()));
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
    }


    private void markCards(long factId, boolean mark) {
        for (long cardId : mDeck.getCardsFromFactId(factId)) {
            int positionC = getPosition(mCards, cardId);
            int positionA = getPosition(mAllCards, cardId);
            String marSus = mAllCards.get(positionA).get("marSus");
            if (mark) {
                marSus = "1" + marSus.substring(1,2);
                if (positionC != -1) {
                    mCards.get(positionC).put("marSus", marSus);                    
                }
                mAllCards.get(positionA).put("marSus", marSus);
            } else {
                marSus = "0" + marSus.substring(1,2);
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
            marSus = marSus.substring(0,1) + "1";
            if (position != -1) {
                mCards.get(position).put("marSus", marSus);                
            }
            mAllCards.get(posA).put("marSus", marSus);
        } else {
            marSus = marSus.substring(0,1) + "0";
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
                data.put("marSus", mAllCards.get(i).get("id"));
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
                mProgressDialog = ProgressDialog.show(CardBrowser.this, "", getResources().getString(R.string.card_browser_load), true);                
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

            for (String[] item : allCards) {
                HashMap<String, String> data = new HashMap<String, String>();
                data.put("id", item[0]);
                data.put("question",  item[1]);
                data.put("answer",  item[2]);
                data.put("marSus", item[3]);
                mAllCards.add(data);
            }
            mCards.clear();
            mCards.addAll(mAllCards);
            updateList();
            
            // This verification would not be necessary if onConfigurationChanged it's executed correctly (which seems
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
            deleteCard(values[0].getString(), mPositionInCardsList);
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
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
            mSelectedCard = values[0].getCard();
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
        	String undoType = result.getString();
            if (undoType.equals(Deck.UNDO_TYPE_EDIT_CARD)) {
                mSelectedCard.fromDB(mSelectedCard.getId());
                int pos = getPosition(mAllCards, mSelectedCard.getId());
                updateCard(mSelectedCard, mAllCards, pos);                    
                pos = getPosition(mCards, mSelectedCard.getId());
                if (pos != -1) {
                    updateCard(mSelectedCard, mCards, pos);                    
                }
                updateList();
            	mProgressDialog.dismiss();
            } else if (undoType.equals(Deck.UNDO_TYPE_MARK_CARD)) {
            	markCards(mSelectedCard.getFactId(), mSelectedCard.isMarked());
            	mProgressDialog.dismiss();
            } else if (undoType.equals(Deck.UNDO_TYPE_SUSPEND_CARD)) {
            	suspendCard(mSelectedCard, getPosition(mCards, mSelectedCard.getId()), mSelectedCard.getSuspendedState());
            	mProgressDialog.dismiss();
            } else if (undoType.equals(Deck.UNDO_TYPE_DELETE_CARD)) {
                if (mSelectedCard != null) {
                    int position = getPosition(mDeletedCards, mSelectedCard.getId());
                    if (position != -1) {
                        HashMap<String, String> data = new HashMap<String, String>();
                        data.put("id", mDeletedCards.get(position).get("id"));
                        data.put("question", mDeletedCards.get(position).get("question"));
                        data.put("answer", mDeletedCards.get(position).get("answer"));
                        data.put("marSus", mDeletedCards.get(position).get("id"));
                        mAllCards.add(Integer.parseInt(mDeletedCards.get(position).get("allCardPos")), data);
                        mDeletedCards.remove(position);
                        updateCardsList();
                    } else {
                        deleteCard(Long.toString(mSelectedCard.getId()), getPosition(mCards, mSelectedCard.getId()));
                    }                    
                    mProgressDialog.dismiss();
                } else {
                    mUndoRedoDialogShowing = true;
                    getCards(Deck.ORDER_BY_ANSWER);                    
                }
            } else {
            	mUndoRedoDialogShowing = true;
            	getCards(Deck.ORDER_BY_ANSWER);
            }
        }
    };


    private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            mProgressDialog = ProgressDialog.show(CardBrowser.this, "", getResources().getString(R.string.saving_changes), true);
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
}
