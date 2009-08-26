package com.ichi2.anki;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.database.SQLException;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

public class Ankidroid extends Activity {
	
	public static final String OPT_DB = "com.ichi2.anki.deckFilename";
	
	public static final int MENU_OPEN = 0;
	
	public static final int PICK_DECK_REQUEST = 0;
	
	private String deckFilename;

	public String card_template;

	private AnkiDb.Card currentCard;
	
	// Variables to hold layout objects that we need to update or handle events for.
	private WebView mCard;
	private ToggleButton mToggleWhiteboard;
	private Button mShowAnswer, mSelectRemembered, mSelectNotRemembered;
	private Chronometer mTimer;
	private Whiteboard mWhiteboard;
	
	// Handler for the 'Show Answer' button.
	View.OnClickListener mShowAnswerHandler = new View.OnClickListener() {
		public void onClick(View view) {
			displayCardAnswer();
		}
	};
	
	// Handler for the Whiteboard toggle button.
	CompoundButton.OnCheckedChangeListener mToggleOverlayHandler = new CompoundButton.OnCheckedChangeListener() {
		public void onCheckedChanged(CompoundButton btn, boolean state) {
			setOverlayState(state);
		}
	};
	
	// Handlers for buttons that allow user to select how well they did.
	View.OnClickListener mSelectRememberedHandler = new View.OnClickListener() {
		public void onClick(View view) {
			// Space this card because it has been successfully remembered.
			currentCard.space();
			nextCard();
			displayCardQuestion();
		}
	};
	View.OnClickListener mSelectNotRememberedHandler = new View.OnClickListener() {
		public void onClick(View view) {
			// Reset this card because it has not been successfully remembered.
			currentCard.reset();
			nextCard();
			displayCardQuestion();
		}
	};

	public void onCreate(Bundle savedInstanceState) throws SQLException {
		super.onCreate(savedInstanceState);
		Bundle extras = getIntent().getExtras();

		initResourceValues();
		
		Log.i("ankidroidstart", "savedInstanceState: " + savedInstanceState);
		
		if (extras != null && extras.getString(OPT_DB) != null) {
			// A deck has just been selected in the decks browser.
			deckFilename = extras.getString(OPT_DB);
		}
		else if (savedInstanceState != null) {
			// Use the same deck as last time Ankidroid was used.
        	deckFilename = savedInstanceState.getString("deckFilename");
	    	Log.i("ankidroidstart", "savedInstanceState deckFilename: " + deckFilename);
        }
		
		if (deckFilename == null || !new File(deckFilename).exists()) {
			// No previously selected deck. Open decks browser.
			openDeckPicker();
		}
		else {
			loadDeck(deckFilename);
		}
	}
	
	public void loadDeck(String deckFilename) {
		this.deckFilename = deckFilename;
		
		// Open the right deck.
		AnkiDb.openDatabase(deckFilename);

		// Initialize the current view to the portrait layout.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
		initLayout(R.layout.flashcard_portrait);

		// Start by getting the first card and displaying it.
		nextCard();
		displayCardQuestion();
	}
	
	// Retrieve resource values.
	public void initResourceValues() {
		Resources r = getResources();
		card_template = r.getString(R.string.card_template);
	}
	
	// Set the content view to the one provided and initialize accessors.
	public void initLayout(Integer layout) {
		setContentView(layout);

		mCard = (WebView)findViewById(R.id.flashcard);
		mShowAnswer = (Button)findViewById(R.id.show_answer);
		mSelectRemembered = (Button)findViewById(R.id.select_remembered);
		mSelectNotRemembered = (Button)findViewById(R.id.select_notremembered);
		mTimer = (Chronometer)findViewById(R.id.card_time);
		mToggleWhiteboard = (ToggleButton)findViewById(R.id.toggle_overlay);
		mWhiteboard = (Whiteboard)findViewById(R.id.whiteboard);
		
		mShowAnswer.setOnClickListener(mShowAnswerHandler);
		mSelectRemembered.setOnClickListener(mSelectRememberedHandler);
		mSelectNotRemembered.setOnClickListener(mSelectNotRememberedHandler);
		mToggleWhiteboard.setOnCheckedChangeListener(mToggleOverlayHandler);
	}
	
	/** Creates the menu items */
	public boolean onCreateOptionsMenu(Menu menu) {
	    menu.add(0, MENU_OPEN, 0, "Switch to another deck");
	    return true;
	}

	/** Handles item selections */
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case MENU_OPEN:
	    	openDeckPicker();
	        return true;
	    }
	    return false;
	}
	
    public void onSaveInstanceState(Bundle outState) {
    	Log.i("ankidroidstart", "onSaveInstanceState: " + deckFilename);
    	if(deckFilename != null)
    		outState.putString("deckFilename", deckFilename);
    }
    
	public void openDeckPicker() {
		Intent decksPicker = new Intent(this, DeckPicker.class) ;
    	startActivityForResult(decksPicker, PICK_DECK_REQUEST);
	}

	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == PICK_DECK_REQUEST) {
            if (resultCode != RESULT_OK) {
            	Log.e("ankidroid", "Deck browser returned with error");
            	return;
            }
            if (intent == null) {
            	Log.e("ankidroid", "Deck browser returned null intent");
            	return;
            }
            // A deck was picked. Use it.
        	//deckFilename = intent.getStringExtra(Intent.EXTRA_TEXT);
        	deckFilename = intent.getExtras().getString(OPT_DB);
            loadDeck(deckFilename);
        }
    }

	public void setOverlayState(boolean enabled) {
		mWhiteboard.setVisibility((enabled) ? View.VISIBLE : View.GONE);
	}
	
	// Get the next card.
	public void nextCard() {
		currentCard = AnkiDb.Card.smallestIntervalCard();
	}
	
	// Set up the display for the current card.
	public void displayCardQuestion() {
		if (currentCard == null) {
			nextCard();
		}
		
		if (currentCard == null) {
			// error :(
			updateCard("Unable to find a card!");
		} else {
			mWhiteboard.clear();
			mSelectRemembered.setVisibility(View.GONE);
			mSelectNotRemembered.setVisibility(View.GONE);
			mShowAnswer.setVisibility(View.VISIBLE);
			mShowAnswer.requestFocus();
			mTimer.setBase(SystemClock.elapsedRealtime());
			mTimer.start();
			updateCard(currentCard.question);
		}
	}
	
	public void updateCard(String content) {
		String card = card_template.replace("::content::", content);
		mCard.loadDataWithBaseURL("", card, "text/html", "utf-8", null);
	}
	
	// Display the card answer.
	public void displayCardAnswer() {
		mTimer.stop();
		mSelectRemembered.setVisibility(View.VISIBLE);
		mSelectNotRemembered.setVisibility(View.VISIBLE);
		mSelectRemembered.requestFocus();
		mShowAnswer.setVisibility(View.GONE);
		updateCard(currentCard.answer);
	}
}