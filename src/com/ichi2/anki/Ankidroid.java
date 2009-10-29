package com.ichi2.anki;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.SQLException;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * Main activity for Ankidroid. Shows a card and controls to answer it.
 * 
 * @author Andrew Dubya, Nicolas Raoul, Edu Zamora
 * 
 */
public class Ankidroid extends Activity implements Runnable
{

	/**
	 * Default database
	 */
	public static final String OPT_DB = "com.ichi2.anki.deckFilename";

	/**
	 * Tag for logging messages
	 */
	private static String TAG = "Ankidroid";

	/**
	 * Menus
	 */
	public static final int MENU_OPEN = 0;

	public static final int MENU_PREFERENCES = 1;

	public static final int MENU_ABOUT = 2;

	/**
	 * Dialogs
	 */
	public static final int DIALOG_NO_SDCARD = 0;
	
	/**
	 * Available options returning from another activity
	 */
	public static final int PICK_DECK_REQUEST = 0;

	public static final int PREFERENCES_UPDATE = 1;

	/**
	 * Variables to hold the state
	 */
	private ProgressDialog dialog;
	
	private boolean layoutInitialized;

	private boolean deckSelected;

	private String deckFilename;

	private boolean corporalPunishments;

	private boolean timerAndWhiteboard;

	private boolean spacedRepetition;

	private String deckPath;

	public String cardTemplate;

	private AnkiDb.Card currentCard;

	/** 
	 * Variables to hold layout objects that we need to update or handle events for
	 */
	private WebView mCard;

	private ToggleButton mToggleWhiteboard, mFlipCard;

	private Button mSelectRemembered, mSelectNotRemembered;

	private Chronometer mTimer;

	private Whiteboard mWhiteboard;
	
	//private Hashtable<Integer,String> viewNames = new Hashtable<Integer,String>();
	
	//private LinearLayout mMainLayout, mRememberedLayout, mChronoButtonsLayout;
	
	//private FrameLayout mCardWhiteboardLayout;
	
	// Handler for the flip toogle button, between the question and the answer
	// of a card
	CompoundButton.OnCheckedChangeListener mFlipCardHandler = new CompoundButton.OnCheckedChangeListener()
	{
		//@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean showAnswer)
		{
			Log.i(TAG, "Flip card changed:");
			if (showAnswer)
				displayCardAnswer();
			else
				displayCardQuestion();
		}
	};

	// Handler for the Whiteboard toggle button.
	CompoundButton.OnCheckedChangeListener mToggleOverlayHandler = new CompoundButton.OnCheckedChangeListener()
	{
		public void onCheckedChanged(CompoundButton btn, boolean state)
		{
			setOverlayState(state);
		}
	};

	// Handlers for buttons that allow user to select how well they did.
	View.OnClickListener mSelectRememberedHandler = new View.OnClickListener()
	{
		public void onClick(View view)
		{
			// Space this card because it has been successfully remembered.
			if (spacedRepetition)
				currentCard.space();
			nextCard();
		}
	};

	View.OnClickListener mSelectNotRememberedHandler = new View.OnClickListener()
	{
		public void onClick(View view)
		{
			// Punish user.
			if (corporalPunishments)
			{
				Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
				v.vibrate(500);
			}
			// Reset this card because it has not been successfully remembered.
			if (spacedRepetition)
				currentCard.reset();
			nextCard();
		}
	};

	/*View.OnFocusChangeListener mOnFocusChangeHandler = new View.OnFocusChangeListener() {
		
		@Override
		public void onFocusChange(View v, boolean hasFocus) {
			String viewName = viewNames.get(new Integer(v.getId()));
			if(viewName == null) viewName = "Unknown View";
			
			String event = "";			
			if(hasFocus) event = " has gained focus.";
			else event = " has lost focus.";
			
			Log.i(TAG, "View " + viewName + event);
			
		}
	};*/
	
	@Override
	public void onCreate(Bundle savedInstanceState) throws SQLException
	{
		super.onCreate(savedInstanceState);
		Log.i(TAG, "onCreate - savedInstanceState: " + savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		Bundle extras = getIntent().getExtras();
		
		initResourceValues();

		SharedPreferences preferences = restorePreferences();

		if (extras != null && extras.getString(OPT_DB) != null)
		{
			// A deck has just been selected in the decks browser.
			deckFilename = extras.getString(OPT_DB);
			Log.i(TAG, "onCreate - deckFilename from extras: " + deckFilename);
		} else if (savedInstanceState != null)
		{
			// Use the same deck as last time Ankidroid was used.
			deckFilename = savedInstanceState.getString("deckFilename");
			Log.i(TAG, "onCreate - deckFilename from savedInstanceState: " + deckFilename);
		} else
		{
			Log.i(TAG, "onCreate - " + preferences.getAll().toString());
			deckFilename = preferences.getString("deckFilename", null);
			Log.i(TAG, "onCreate - deckFilename from preferences: " + deckFilename);
		}

		if (deckFilename == null || !new File(deckFilename).exists())
		{
			// No previously selected deck.
			Log.i(TAG, "onCreate - No previously selected deck or the previously selected deck is not available at the moment");
			boolean generateSampleDeck = preferences.getBoolean("generateSampleDeck", true);
			if (generateSampleDeck)
			{
				Log.i(TAG, "onCreate - Generating sample deck...");
				// Load sample deck.
				// This sample deck is for people who downloaded the app but
				// don't know Anki.
				// These people will understand how it works and will get to
				// love it!
				// TODO Where should we put this sample deck?
				String SAMPLE_DECK_FILENAME = "/sdcard/country-capitals.anki";
				if (!new File(/*
							 * deckFilename triggers NPE bug in
							 * java.io.File.java
							 */"/sdcard", "country-capitals.anki").exists())
				{
					try
					{
						// Copy the sample deck from the assets to the SD card.
						InputStream stream = getResources().getAssets().open("country-capitals.anki");
						boolean written = writeToFile(stream, SAMPLE_DECK_FILENAME);
						if (!written)
						{
							openDeckPicker();
							Log.i(TAG, "onCreate - The copy of country-capitals.anki to the sd card failed.");
							return;
						}
						Log.i(TAG, "onCreate - The copy of country-capitals.anki to the sd card was sucessful.");
					} catch (IOException e)
					{
						e.printStackTrace();
					}
				}
				// Load sample deck.
				deckFilename = SAMPLE_DECK_FILENAME;
				//displayProgressDialogAndLoadDeck();
			} else
			{
				// Show the deck picker.
				openDeckPicker();
			}
		} else
		{
			// Load deck.
			//displayProgressDialogAndLoadDeck();
		}
	}

	public void loadDeck(String deckFilename)
	{
		this.deckFilename = deckFilename;
		
		//If I'm not mistaken, the only possibility for deckFilename to be null here is if never existed a deck before 
		//(so, the sd card was not attached the first time we opened the app and therefore country-capitals.anki wasn't created either)
		if(isSdCardMounted() && deckFilename != null && new File(deckFilename).exists())
		{
			if(!layoutInitialized) 
			{
				// Initialize the current view to the portrait layout.
				initLayout(R.layout.flashcard_portrait);
				layoutInitialized = true;
			}
			Log.i(TAG, "loadDeck - SD card mounted and existent file -> Loading deck...");
			// Open the right deck.
			AnkiDb.openDatabase(deckFilename);
			Log.i(TAG, "Deck loaded!");

			// Don't open database in onResume(). Is already opening elsewhere.
			deckSelected = true;
			// Start by getting the first card and displaying it.
			nextCard();
		}
	}

	// Retrieve resource values.
	public void initResourceValues()
	{
		Resources r = getResources();
		cardTemplate = r.getString(R.string.card_template);
	}

	// Set the content view to the one provided and initialize accessors.
	public void initLayout(Integer layout)
	{
		Log.i(TAG, "initLayout - Beginning");
		setContentView(layout);

		mCard = (WebView) findViewById(R.id.flashcard);
		mSelectRemembered = (Button) findViewById(R.id.select_remembered);
		mSelectNotRemembered = (Button) findViewById(R.id.select_notremembered);
		mTimer = (Chronometer) findViewById(R.id.card_time);
		mFlipCard = (ToggleButton) findViewById(R.id.flip_card);
		mToggleWhiteboard = (ToggleButton) findViewById(R.id.toggle_overlay);
		mWhiteboard = (Whiteboard) findViewById(R.id.whiteboard);
		//mMainLayout = (LinearLayout) findViewById(R.id.main_layout);
		//mRememberedLayout = (LinearLayout) findViewById(R.id.remembered_layout);
		//mChronoButtonsLayout = (LinearLayout) findViewById(R.id.chrono_buttons_layout);
		//mCardWhiteboardLayout = (FrameLayout) findViewById(R.id.card_whiteboard_layout);
		
		showOrHideControls();

		mSelectRemembered.setOnClickListener(mSelectRememberedHandler);
		mSelectNotRemembered.setOnClickListener(mSelectNotRememberedHandler);
		mFlipCard.setOnCheckedChangeListener(mFlipCardHandler);
		mToggleWhiteboard.setOnCheckedChangeListener(mToggleOverlayHandler);
		
		mCard.setFocusable(false);

		Log.i(TAG, "initLayout - Ending");

		//For focus testing purposes
		/*viewNames.put(new Integer(mCard.getId()), "mCard");
		viewNames.put(new Integer(mSelectRemembered.getId()), "mSelectRemembered");
		viewNames.put(new Integer(mSelectNotRemembered.getId()), "mSelectNotRemembered");
		viewNames.put(new Integer(mTimer.getId()), "mTimer");
		viewNames.put(new Integer(mFlipCard.getId()), "mFlipCard");
		viewNames.put(new Integer(mToggleWhiteboard.getId()), "mToggleWhiteboard");
		viewNames.put(new Integer(mWhiteboard.getId()), "mWhiteboard");
		viewNames.put(new Integer(mMainLayout.getId()), "mMainLayout");
		viewNames.put(new Integer(mRememberedLayout.getId()), "mRememberedLayout");
		viewNames.put(new Integer(mChronoButtonsLayout.getId()), "mChronoButtonsLayout");
		viewNames.put(new Integer(mCardWhiteboardLayout.getId()), "mCardWhiteboardLayout");
		
		mCard.setOnFocusChangeListener(mOnFocusChangeHandler);
		mSelectRemembered.setOnFocusChangeListener(mOnFocusChangeHandler);
		mSelectNotRemembered.setOnFocusChangeListener(mOnFocusChangeHandler);
		mTimer.setOnFocusChangeListener(mOnFocusChangeHandler);
		mFlipCard.setOnFocusChangeListener(mOnFocusChangeHandler);
		mToggleWhiteboard.setOnFocusChangeListener(mOnFocusChangeHandler);
		mWhiteboard.setOnFocusChangeListener(mOnFocusChangeHandler);
		mMainLayout.setOnFocusChangeListener(mOnFocusChangeHandler);
		mRememberedLayout.setOnFocusChangeListener(mOnFocusChangeHandler);
		mChronoButtonsLayout.setOnFocusChangeListener(mOnFocusChangeHandler);
		mCardWhiteboardLayout.setOnFocusChangeListener(mOnFocusChangeHandler);*/
	}

	/** Creates the menu items */
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_OPEN, 0, "Switch to another deck");
		menu.add(1, MENU_PREFERENCES, 0, "Preferences");
		menu.add(1, MENU_ABOUT, 0, "About");
		return true;
	}

	/** Handles item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case MENU_OPEN:
			openDeckPicker();
			return true;
		case MENU_PREFERENCES:
			Intent preferences = new Intent(this, Preferences.class);
			startActivityForResult(preferences, PREFERENCES_UPDATE);
			return true;
		case MENU_ABOUT:
			Intent about = new Intent(this, About.class);
			startActivity(about);
			return true;
		}
		return false;
	}

	protected Dialog onCreateDialog(int id)
	{
		Dialog dialog;
		switch(id)
		{
		case DIALOG_NO_SDCARD:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("The SD card could not be read. Please, turn off USB storage.");
			builder.setPositiveButton("OK", null);
			dialog = builder.create();
			break;
		
		default:
			dialog = null;
		}
		
		return dialog;
	}
	
	public void openDeckPicker()
	{
		deckSelected = false; // Make sure we open the database again in
							  // onResume() if user pressed "back".
		Intent decksPicker = new Intent(this, DeckPicker.class);
		decksPicker.putExtra("com.ichi2.anki.Ankidroid.DeckPath", deckPath);
		startActivityForResult(decksPicker, PICK_DECK_REQUEST);
	}

	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		Log.i(TAG, "onSaveInstanceState: " + deckFilename);
		// Remember current deck's filename.
		if (deckFilename != null)
		{
			outState.putString("deckFilename", deckFilename);
		}
	}

	@Override
	public void onStop()
	{
		Log.i(TAG, "onStop() - " + System.currentTimeMillis());
		super.onStop();
		if (deckFilename != null)
		{
			savePreferences();
		}
	}

	@Override
	public void onResume()
	{
		Log.i(TAG, "onResume() - deckFilename = " + deckFilename + ", deckSelected = " + deckSelected);
		super.onResume();
		if (!deckSelected && isSdCardMounted())
		{
			Log.i(TAG, "onResume() - No deck selected before");
			//AnkiDb.openDatabase(deckFilename);
			//loadDeck(deckFilename);
			//deckSelected = true;
			displayProgressDialogAndLoadDeck();
		}
		Log.i(TAG, "onResume() - Ending");
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
		super.onActivityResult(requestCode, resultCode, intent);
		if (requestCode == PICK_DECK_REQUEST)
		{
			if (resultCode != RESULT_OK)
			{
				Log.e(TAG, "onActivityResult - Deck browser returned with error");
				//if deckFilename
				return;
			}
			if (intent == null)
			{
				Log.e(TAG, "onActivityResult - Deck browser returned null intent");
				return;
			}
			// A deck was picked. Save it in preferences and use it.

			deckFilename = intent.getExtras().getString(OPT_DB);
			savePreferences();

			// Don't open database again in onResume(). Load the new one in
			// another thread instead.
			deckSelected = true;
			displayProgressDialogAndLoadDeck();
		} else if (requestCode == PREFERENCES_UPDATE)
		{
			restorePreferences();
			//If any deck has been selected (usually because there was no sd card attached, and therefore was impossible to select one) 
			//the controls have not been initialized, so we don't have to try to show or hide them
			if(deckSelected)
				showOrHideControls();
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	  super.onConfigurationChanged(newConfig);
	  //extra height that the Whiteboard should have to be able to write in all its surface either on the question or on the answer
	  int extraHeight = 0;
	  if(mSelectRemembered.isShown() && mSelectNotRemembered.isShown())
	  {
		  //if the "Remembered" and "Not remembered" buttons are visible, their height has to be counted in the creation of the new Whiteboard
		  //because we should be able to write in their space when it is the front part of the card
		  extraHeight = java.lang.Math.max(mSelectRemembered.getHeight(), mSelectNotRemembered.getHeight());
	  } 
	  mWhiteboard.rotate(extraHeight);
	}

	
	private void displayProgressDialogAndLoadDeck()
	{
		Log.i(TAG, "displayProgressDialogAndLoadDeck - Loading deck " + deckFilename);
		if(!layoutInitialized) 
		{
			// Initialize the current view to the portrait layout.
			initLayout(R.layout.flashcard_portrait);
			layoutInitialized = true;
		}
		showControls(false);
		dialog = ProgressDialog.show(this, "", "Loading deck. Please wait...", true);
		Thread thread = new Thread(this);
		thread.start();
	}

	private void showControls(boolean show)
	{
		if (show)
		{
			mCard.setVisibility(View.VISIBLE);
			mSelectRemembered.setVisibility(View.VISIBLE);
			mSelectNotRemembered.setVisibility(View.VISIBLE);
			mFlipCard.setVisibility(View.VISIBLE);
			showOrHideControls();
		} else
		{
			mCard.setVisibility(View.GONE);
			mSelectRemembered.setVisibility(View.GONE);
			mSelectNotRemembered.setVisibility(View.GONE);
			mFlipCard.setVisibility(View.GONE);
			mTimer.setVisibility(View.GONE);
			mToggleWhiteboard.setVisibility(View.GONE);
			mWhiteboard.setVisibility(View.GONE);
		}
	}

	/**
	 * Depending on preferences, show or hide the timer and whiteboard.
	 */
	private void showOrHideControls()
	{
		Log.i(TAG, "showOrHideControls - timerAndWhiteboard: " + timerAndWhiteboard);
		if (!timerAndWhiteboard)
		{
			mTimer.setVisibility(View.GONE);
			mToggleWhiteboard.setVisibility(View.GONE);
			mWhiteboard.setVisibility(View.GONE);
		} else
		{
			mTimer.setVisibility(View.VISIBLE);
			mToggleWhiteboard.setVisibility(View.VISIBLE);
			if (mToggleWhiteboard.isChecked())
			{
				mWhiteboard.setVisibility(View.VISIBLE);
			}
		}
	}

	public void setOverlayState(boolean enabled)
	{
		mWhiteboard.setVisibility((enabled) ? View.VISIBLE : View.GONE);
	}

	// Get the next card.
	public void nextCard()
	{
		Log.i(TAG, "nextCard");
		if (spacedRepetition)
			currentCard = AnkiDb.Card.smallestIntervalCard();
		else
			currentCard = AnkiDb.Card.randomCard();

		// Set the correct value for the flip card button - That triggers the
		// listener which displays the question of the card
		mFlipCard.setChecked(false);
		mWhiteboard.clear();
		mTimer.setBase(SystemClock.elapsedRealtime());
		mTimer.start();
	}

	// Set up the display for the current card.
	public void displayCardQuestion()
	{
		Log.i(TAG, "displayCardQuestion");

		if (currentCard == null)
		{
			nextCard();
		}

		if (currentCard == null)
		{
			// error :(
			updateCard("Unable to find a card!");
		} else
		{
			Log.i(TAG, "displayCardQuestion - Hiding 'Remembered' and 'Not Remembered' buttons...");
			mSelectRemembered.setVisibility(View.GONE);
			mSelectNotRemembered.setVisibility(View.GONE);
									
			mFlipCard.requestFocus();

			updateCard(currentCard.question);
		}
	}

	public void updateCard(String content)
	{
		String card = cardTemplate.replace("::content::", content);
		mCard.loadDataWithBaseURL("", card, "text/html", "utf-8", null);
	}

	// Display the card answer.
	public void displayCardAnswer()
	{
		Log.i(TAG, "displayCardAnswer");

		mTimer.stop();
		mWhiteboard.lock();

		mSelectRemembered.setVisibility(View.VISIBLE);
		mSelectNotRemembered.setVisibility(View.VISIBLE);
		
		mSelectRemembered.requestFocus();
		
		updateCard(currentCard.answer);
	}

	private boolean writeToFile(InputStream source, String destination) throws IOException
	{
		try
		{
			new File(destination).createNewFile();
		} catch (IOException e)
		{
			// Most probably the SD card is not mounted on the Android.
			// Tell the user to turn off USB storage, which will automatically
			// mount it on Android.
			//Toast.makeText(this, "Please turn off USB storage", Toast.LENGTH_LONG).show();
			return false;
		}
		OutputStream output = new FileOutputStream(destination);

		// Transfer bytes, from source to destination.
		byte[] buf = new byte[1024];
		int len;
		while ((len = source.read(buf)) > 0)
		{
			output.write(buf, 0, len);
		}
		source.close();
		output.close();
		return true;
	}

	private SharedPreferences restorePreferences()
	{
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		corporalPunishments = preferences.getBoolean("corporalPunishments", false);
		timerAndWhiteboard = preferences.getBoolean("timerAndWhiteboard", true);
		Log.i(TAG, "restorePreferences - timerAndWhiteboard: " + timerAndWhiteboard);
		spacedRepetition = preferences.getBoolean("spacedRepetition", true);
		deckPath = preferences.getString("deckPath", "/sdcard");

		return preferences;
	}

	private void savePreferences()
	{
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		Editor editor = preferences.edit();
		editor.putString("deckFilename", deckFilename);
		editor.commit();
	}

	public void run()
	{
		loadDeck(deckFilename);
		handler.sendEmptyMessage(0);
	}

	private Handler handler = new Handler()
	{
		public void handleMessage(Message msg)
		{
			dialog.dismiss();
			showControls(true);
			displayCardQuestion();

		}
	};
	
	private boolean isSdCardMounted() {
		return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
	}
}