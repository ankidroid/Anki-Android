package com.ichi2.anki;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.SQLException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * Main activity for Ankidroid.
 * Shows a card and controls to answer it.
 *  
 * @author Andrew Dubya, Nicolas Raoul
 *
 */
public class Ankidroid extends Activity implements Runnable {
	
	public static final String OPT_DB = "com.ichi2.anki.deckFilename";
	
	public static final int MENU_OPEN = 0;
	public static final int MENU_PREFERENCES = 1;
	public static final int MENU_ABOUT = 2;
	
	public static final int PICK_DECK_REQUEST = 0;
	public static final int PREFERENCES_UPDATE = 1;

	private ProgressDialog dialog;
	private boolean deckSelected;
	
	private String deckFilename;
	
	private boolean corporalPunishments;
	private boolean timerAndWhiteboard;
	private boolean spacedRepetition;
	private String deckPath;

	public String cardTemplate;

	private AnkiDb.Card currentCard;
	
	// Variables to hold layout objects that we need to update or handle events for.
	private WebView mCard;
	private ToggleButton mToggleWhiteboard, mFlipCard;
	private Button mSelectRemembered, mSelectNotRemembered;
	private Chronometer mTimer;
	private Whiteboard mWhiteboard;
	
	// Handler for the 'Show Answer' button.
	/*View.OnClickListener mShowAnswerHandler = new View.OnClickListener() {
		public void onClick(View view) {
			displayCardAnswer();
		}
	};*/

	// Handler for the flip toogle button, between the question and the answer of a card
	CompoundButton.OnCheckedChangeListener mFlipCardHandler = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean showAnswer) {			
			if(showAnswer) displayCardAnswer();
			else displayCardQuestion();
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
			if(spacedRepetition)
				currentCard.space();
			nextCard();
			//displayCardQuestion();
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
			if (spacedRepetition)
				currentCard.reset();
			nextCard();
			//displayCardQuestion();
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
		timerAndWhiteboard = preferences.getBoolean("timerAndWhiteboard", true);
		spacedRepetition = preferences.getBoolean("spacedRepetition", true);
		deckPath = preferences.getString("deckPath", "/sdcard");
		
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
				String SAMPLE_DECK_FILENAME = "/sdcard/country-capitals.anki";
				if ( ! new File(/*deckFilename triggers NPE bug in java.io.File.java */"/sdcard", "country-capitals.anki").exists()) {
					try {
						// Copy the sample deck from the assets to the SD card.
						InputStream stream = getResources().getAssets().open("country-capitals.anki");
						boolean written = writeToFile(stream, SAMPLE_DECK_FILENAME);
						if ( ! written) {
							openDeckPicker();
							return;
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				// Initialize the current view to the portrait layout.
				initLayout(R.layout.flashcard_portrait);
				// Load sample deck.
				deckFilename = SAMPLE_DECK_FILENAME;
				displayProgressDialogAndLoadDeck();
			}
			else {
				// Show the deck picker.
				openDeckPicker();
			}
		}
		else {
			// Initialize the current view to the portrait layout.
			initLayout(R.layout.flashcard_portrait);
			// Load deck.
			displayProgressDialogAndLoadDeck();
		}
		// Don't open database in onResume(). Is already opening elsewhere.
		deckSelected = true;
	}

	public void loadDeck(String deckFilename) {
		this.deckFilename = deckFilename;
		
		// Open the right deck.
		AnkiDb.openDatabase(deckFilename);

		// Start by getting the first card and displaying it.
		nextCard();
	}
	
	// Retrieve resource values.
	public void initResourceValues() {
		Resources r = getResources();
		cardTemplate = r.getString(R.string.card_template);
	}
	
	// Set the content view to the one provided and initialize accessors.
	public void initLayout(Integer layout) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(layout);

		mCard = (WebView)findViewById(R.id.flashcard);
		mSelectRemembered = (Button)findViewById(R.id.select_remembered);
		mSelectNotRemembered = (Button)findViewById(R.id.select_notremembered);
		mTimer = (Chronometer)findViewById(R.id.card_time);
		mFlipCard = (ToggleButton)findViewById(R.id.flip_card);
		mToggleWhiteboard = (ToggleButton)findViewById(R.id.toggle_overlay);
		mWhiteboard = (Whiteboard)findViewById(R.id.whiteboard);

		showOrHideControls();
		
		//mShowAnswer.setOnClickListener(mShowAnswerHandler);
		mSelectRemembered.setOnClickListener(mSelectRememberedHandler);
		mSelectNotRemembered.setOnClickListener(mSelectNotRememberedHandler);
		mFlipCard.setOnCheckedChangeListener(mFlipCardHandler);
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
    	deckSelected = false; // Make sure we open the database again in onResume() if user pressed "back".
    	Intent decksPicker = new Intent(this, DeckPicker.class);
    	decksPicker.putExtra("com.ichi2.anki.Ankidroid.DeckPath", deckPath);
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
    public void onResume() {
    	super.onResume();
    	if (!deckSelected) {
    		AnkiDb.openDatabase(deckFilename);
    		deckSelected = true;
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
    		
    		// Don't open database again in onResume(). Load the new one in another thread instead.
    		deckSelected = true;
    		displayProgressDialogAndLoadDeck();
        }
        else if(requestCode == PREFERENCES_UPDATE) {
        	SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    		corporalPunishments = preferences.getBoolean("corporalPunishments", false);
    		timerAndWhiteboard = preferences.getBoolean("timerAndWhiteboard", true);
    		spacedRepetition = preferences.getBoolean("spacedRepetition", true);
    		deckPath = preferences.getString("deckPath", "/sdcard");
    		showOrHideControls();
        }
    }

	private void displayProgressDialogAndLoadDeck() {
		Log.i("anki", "Loading deck " + deckFilename);
		showControls(false);
		dialog = ProgressDialog.show(this, "", "Loading deck. Please wait...", true);
		Thread thread = new Thread(this);
		thread.start();
	}

	private void showControls(boolean show) {
		if (show) {
			mCard.setVisibility(View.VISIBLE);
			//mShowAnswer.setVisibility(View.VISIBLE);
			mSelectRemembered.setVisibility(View.VISIBLE);
			mSelectNotRemembered.setVisibility(View.VISIBLE);
			mTimer.setVisibility(View.VISIBLE);
			mFlipCard.setVisibility(View.VISIBLE);
			mToggleWhiteboard.setVisibility(View.VISIBLE);
			if (mToggleWhiteboard.isChecked()) {
				mWhiteboard.setVisibility(View.VISIBLE);
			}
		} else {
			mCard.setVisibility(View.GONE);
			mToggleWhiteboard.setVisibility(View.GONE);
			//mShowAnswer.setVisibility(View.GONE);
			mSelectRemembered.setVisibility(View.GONE);
			mSelectNotRemembered.setVisibility(View.GONE);
			mTimer.setVisibility(View.GONE);
			mFlipCard.setVisibility(View.GONE);
			mWhiteboard.setVisibility(View.GONE);
		}
	}
	
	/**
	 * Depending on preferences, show or hide the timer and whiteboard. 
	 */
	private void showOrHideControls() {
		if( ! timerAndWhiteboard) {
			mTimer.setVisibility(View.GONE);
			mToggleWhiteboard.setVisibility(View.GONE);
			mWhiteboard.setVisibility(View.GONE);
		}
		else {
			mTimer.setVisibility(View.VISIBLE);
			mToggleWhiteboard.setVisibility(View.VISIBLE);
			if (mToggleWhiteboard.isChecked()) {
				mWhiteboard.setVisibility(View.VISIBLE);
			}
		}
	}

	public void setOverlayState(boolean enabled) {
		mWhiteboard.setVisibility((enabled) ? View.VISIBLE : View.GONE);
	}
	
	// Get the next card.
	public void nextCard() {
		if (spacedRepetition)
			currentCard = AnkiDb.Card.smallestIntervalCard();
		else
			currentCard = AnkiDb.Card.randomCard();
		
		// Set the correct value for the flip card button - That triggers the listener which displays the question of the card
		mFlipCard.setChecked(false);
		mWhiteboard.clear();
		mTimer.setBase(SystemClock.elapsedRealtime());
		mTimer.start();
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
			mSelectRemembered.setVisibility(View.GONE);
			mSelectNotRemembered.setVisibility(View.GONE);
			updateCard(currentCard.question);
			//mWhiteboard.bringToFront();
		}
	}
	
	public void updateCard(String content) {
		String card = cardTemplate.replace("::content::", content);
		mCard.loadDataWithBaseURL("", card, "text/html", "utf-8", null);
	}
	
	// Display the card answer.
	public void displayCardAnswer() {
		mTimer.stop();
		mWhiteboard.lock();
		mSelectRemembered.setVisibility(View.VISIBLE);
		mSelectNotRemembered.setVisibility(View.VISIBLE);
		mSelectRemembered.requestFocus();
		//mShowAnswer.setVisibility(View.GONE);
		updateCard(currentCard.answer);
		//mWhiteboard.bringToFront();
	}
	
	private boolean writeToFile(InputStream source, String destination) throws IOException {
		try {
			new File(destination).createNewFile();
		}
		catch(IOException e) {
			// Most probably the SD card is not mounted on the Android.
			// Tell the user to turn off USB storage, which will automatically mount it on Android.
			Toast.makeText(this, "Please turn off USB storage", Toast.LENGTH_LONG).show();
			return false;
		}
        OutputStream output = new FileOutputStream(destination);
    
        // Transfer bytes, from source to destination.
        byte[] buf = new byte[1024];
        int len;
        while ((len = source.read(buf)) > 0) {
            output.write(buf, 0, len);
        }
        source.close();
        output.close();
        return true;
    }
	
	public void run() {
		loadDeck(deckFilename);
    	handler.sendEmptyMessage(0);
    }
	
	private Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			dialog.dismiss();
			showControls(true);
			displayCardQuestion();
		}
	};
}