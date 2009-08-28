package com.ichi2.anki;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.SQLException;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

public class Ankidroid extends Activity {
	
	public static final String OPT_DB = "com.ichi2.anki.deckFilename";
	
	public static final int MENU_OPEN = 0;
	public static final int MENU_PREFERENCES = 1;
	public static final int MENU_ABOUT = 2;
	
	public static final int PICK_DECK_REQUEST = 0;
	public static final int PREFERENCES_UPDATE = 1;
	
	private String deckFilename;
	
	private boolean corporalPunishments;

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
			// Punish user.
			if(corporalPunishments) {
				Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);  
				v.vibrate(500);
			}
			// Reset this card because it has not been successfully remembered.
			currentCard.reset();
			nextCard();
			displayCardQuestion();
		}
	};

    @Override
	public void onCreate(Bundle savedInstanceState) throws SQLException {
		super.onCreate(savedInstanceState);
		Bundle extras = getIntent().getExtras();

		initResourceValues();
		
		Log.i("ankidroidstart", "savedInstanceState: " + savedInstanceState);
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		corporalPunishments = preferences.getBoolean("corporalPunishments", false);
		
		if (extras != null && extras.getString(OPT_DB) != null) {
			// A deck has just been selected in the decks browser.
			deckFilename = extras.getString(OPT_DB);
			Log.i("ankidroidstart", "deckFilename from extras: " + deckFilename);
		}
		else if (savedInstanceState != null) {
			// Use the same deck as last time Ankidroid was used.
        	deckFilename = savedInstanceState.getString("deckFilename");
	    	Log.i("ankidroidstart", "deckFilename from savedInstanceState: " + deckFilename);
        }
		else {
			Log.i("ankidroidstart", preferences.getAll().toString());
			deckFilename = preferences.getString("deckFilename", null);
			Log.i("ankidroidstart", "deckFilename from preferences: " + deckFilename);
		}
		
		if (deckFilename == null || !new File(deckFilename).exists()) {
			// No previously selected deck.
			
			boolean generateSampleDeck = preferences.getBoolean("generateSampleDeck", true);
			if (generateSampleDeck) {
				// Load sample deck.
				// This sample deck is for people who downloaded the app but don't know Anki.
				// These people will understand how it works and will get to love it!
				// TODO Where should we put this sample deck?
				String SAMPLE_DECK_FILENAME = "/sdcard/sample-deck.anki";
				if ( ! new File(/*deckFilename triggers NPE bug in java.io.File.java */"/sdcard", "japanese.anki").exists()) {
					try {
						// Copy the sample deck from the assets to the SD card.
						InputStream stream = getResources().getAssets().open("sample-deck.anki");
						writeToFile(stream, SAMPLE_DECK_FILENAME);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				loadDeck(SAMPLE_DECK_FILENAME);
			}
			else {
				// Show the deck picker.
				openDeckPicker();
			}
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
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
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
	    menu.add(1, MENU_PREFERENCES, 0, "Preferences");
	    menu.add(1, MENU_ABOUT, 0, "About");
	    return true;
	}

	/** Handles item selections */
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case MENU_OPEN:
	    	openDeckPicker();
			return true;
	    case MENU_PREFERENCES:
			Intent preferences = new Intent(this, Preferences.class) ;
	    	startActivityForResult(preferences, PREFERENCES_UPDATE);
	        return true;
	    case MENU_ABOUT:
	    	Toast.makeText(this, deckFilename, Toast.LENGTH_LONG).show();
	    	Intent about = new Intent(this, About.class) ;
	    	startActivity(about);
	        return true;
	    }
	    return false;
	}
    
    public void openDeckPicker() {
    	Intent decksPicker = new Intent(this, DeckPicker.class) ;
    	startActivityForResult(decksPicker, PICK_DECK_REQUEST);
    }
	
    @Override
    public void onSaveInstanceState(Bundle outState) {
    	Log.i("ankidroidstart", "onSaveInstanceState: " + deckFilename);
    	// Remember current deck's filename.
    	if(deckFilename != null) {
    		outState.putString("deckFilename", deckFilename);
    	}
    }

    @Override
    public void onStop() {
    	super.onStop();
    	if(deckFilename != null) {
    		SharedPreferences preferences = getSharedPreferences("ankidroid", MODE_PRIVATE);
    		preferences.edit().putString("deckFilename", deckFilename);
    		preferences.edit().commit();
    	}
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
            // A deck was picked. Save it in preferences and use it.
        	deckFilename = intent.getExtras().getString(OPT_DB);
        	SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    		Editor editor = preferences.edit();
    		editor.putString("deckFilename", deckFilename);
    		editor.commit();
    		
        	loadDeck(deckFilename);
        }
        else if(requestCode == PREFERENCES_UPDATE) {
        	SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    		corporalPunishments = preferences.getBoolean("corporalPunishments", false);
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
	
	void writeToFile(InputStream source, String destination) throws IOException {
		new File(destination).createNewFile();
        OutputStream output = new FileOutputStream(destination);
    
        // Transfer bytes, from source to destination.
        byte[] buf = new byte[1024];
        int len;
        while ((len = source.read(buf)) > 0) {
            output.write(buf, 0, len);
        }
        source.close();
        output.close();
    }
}