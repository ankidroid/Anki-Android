package com.ichi2.anki;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;
import java.util.HashMap;

public class CardBrowser extends Activity {
    private ArrayList<HashMap<String, String>> mCards;
    private ArrayList<HashMap<String, String>> mAllCards;
    private ListView mCardsListView;
    private SimpleAdapter mCardsAdapter;
    private EditText mSearchEditText;
    
    private ProgressDialog mProgressDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.card_browser);

        mCards = new ArrayList<HashMap<String, String>>();
        mAllCards = new ArrayList<HashMap<String, String>>();
        mCardsListView = (ListView) findViewById(R.id.card_browser_list);  
        
        mCardsAdapter = new SimpleAdapter(this, mCards,
                R.layout.card_item, new String[] { "question", "answer" }, new int[] {
                        R.id.card_question, R.id.card_answer });
        mCardsAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View arg0, Object arg1, String arg2) {
                return false;
            }
        });

        mCardsListView.setAdapter(mCardsAdapter);
        mCardsListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent editCard = new Intent(CardBrowser.this, CardEditor.class);
                editCard.putExtra("card", Long.parseLong(mCards.get(position).get("id")));
                startActivityForResult(editCard, 0);
            }
        });

        mSearchEditText = (EditText) findViewById(R.id.card_browser_search);
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                mCards.clear();
            	for (int i = 0; i < mAllCards.size(); i++) {
                    if (mAllCards.get(i).get("question").toLowerCase().indexOf(mSearchEditText.getText().toString().toLowerCase()) != -1 ||
                    		mAllCards.get(i).get("answer").toLowerCase().indexOf(mSearchEditText.getText().toString().toLowerCase()) != -1) { 
                    	mCards.add(mAllCards.get(i));
                    }
                }
            	mCardsAdapter.notifyDataSetChanged();
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            public void onTextChanged(CharSequence s, int start, int before, int count){}
        });
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        
        getCards();
    }


    private void getCards() {
        DeckTask.launchDeckTask(DeckTask.TASK_LOAD_CARDS, mLoadCardsHandler, new DeckTask.TaskData(AnkiDroidApp.deck(), "answer"));
    }


    DeckTask.TaskListener mLoadCardsHandler = new DeckTask.TaskListener() {

        @Override
        public void onPreExecute() {
            mProgressDialog = ProgressDialog.show(CardBrowser.this, "", getResources().getString(R.string.card_browser_load), true);
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
        	mAllCards.clear();
        	ArrayList<String[]> allCards = new ArrayList<String[]>();
        	allCards.addAll(result.getAllCards());

        	for (String[] item : allCards) {
        		HashMap<String, String> data = new HashMap<String, String>();
                data.put("id", item[0]);
        		data.put("question",  item[1]);
                data.put("answer",  item[2]);
            	mAllCards.add(data);
        	}
        	mCardsAdapter.notifyDataSetChanged();
        	mCards.clear();
        	mCards.addAll(mAllCards);
        	
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


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }
    };
}
