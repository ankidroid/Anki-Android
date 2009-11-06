package com.ichi2.anki;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.CursorIndexOutOfBoundsException;
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
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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
	private static final String TAG = "Ankidroid";

	/**
	 * Menus
	 */
	public static final int MENU_OPEN = 0;

	public static final int MENU_PREFERENCES = 1;

	public static final int MENU_ABOUT = 2;

	
	/**
	 * Possible outputs trying to load a deck
	 */
	public static final int DECK_LOADED = 0;
	
	public static final int DECK_NOT_LOADED = 1;
	
	public static final int DECK_EMPTY = 2;
	
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
	
    private BroadcastReceiver mUnmountReceiver = null;

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

	
	@Override
	public void onCreate(Bundle savedInstanceState) throws SQLException
	{		
		super.onCreate(savedInstanceState);
		Log.i(TAG, "onCreate - savedInstanceState: " + savedInstanceState);
		
		registerExternalStorageListener();
		initResourceValues();

		Bundle extras = getIntent().getExtras();
		SharedPreferences preferences = restorePreferences();
		initLayout(R.layout.flashcard_portrait);

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
			if(isSdCardMounted())
			{
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
			}
		
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
		
		showControls(false);

		mSelectRemembered.setOnClickListener(mSelectRememberedHandler);
		mSelectNotRemembered.setOnClickListener(mSelectNotRememberedHandler);
		mFlipCard.setOnCheckedChangeListener(mFlipCardHandler);
		mToggleWhiteboard.setOnCheckedChangeListener(mToggleOverlayHandler);
		
		mCard.setFocusable(false);

		Log.i(TAG, "initLayout - Ending");

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

	
	public void openDeckPicker()
	{
		Log.i(TAG, "openDeckPicker");
		deckSelected = false; // Make sure we open the database again in
							  // onResume() if user pressed "back".
    	Log.i(TAG, "openDeckPicker - deckSelected = " + deckSelected);
		Intent decksPicker = new Intent(this, DeckPicker.class);
		decksPicker.putExtra("com.ichi2.anki.Ankidroid.DeckPath", deckPath);
		startActivityForResult(decksPicker, PICK_DECK_REQUEST);
		Log.i(TAG, "openDeckPicker - Ending");
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
		Log.i(TAG, "onSaveInstanceState - Ending");
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

		if (!deckSelected)
		{
			Log.i(TAG, "onResume() - No deck selected before");
			deckSelected = true;
        	Log.i(TAG, "onResume - deckSelected = " + deckSelected);
			displayProgressDialogAndLoadDeck();
		}

		Log.i(TAG, "onResume() - Ending");
	}

	private void displayProgressDialogAndLoadDeck()
	{
		Log.i(TAG, "displayProgressDialogAndLoadDeck - Loading deck " + deckFilename);

		if(isSdCardMounted())
		{
			if (deckFilename != null && new File(deckFilename).exists())
			{
				showControls(false);
				dialog = ProgressDialog.show(this, "", "Loading deck. Please wait...", true);
				Thread thread = new Thread(this);
				thread.start();
			}
			else
			{

				if(deckFilename == null) Log.i(TAG, "displayProgressDialogAndLoadDeck - SD card unmounted.");

				if(!new File(deckFilename).exists()) Log.i(TAG, "displayProgressDialogAndLoadDeck - The deck " + deckFilename + "does not exist.");
				
				//Show message informing that no deck has been loaded
				displayDeckNotLoaded();
			}
		} else
		{
			Log.i(TAG, "displayProgressDialogAndLoadDeck - SD card unmounted.");
			deckSelected = false;
        	Log.i(TAG, "displayProgressDialogAndLoadDeck - deckSelected = " + deckSelected);
			displaySdError();
		}

	}

	public void run()
	{
		Log.i(TAG, "Ankidroid loader thread - run");
		handler.sendEmptyMessage(loadDeck(deckFilename));
	}

	public int loadDeck(String deckFilename)
	{
		Log.i(TAG, "loadDeck - deckFilename = " + deckFilename);
		this.deckFilename = deckFilename;
		
		Log.i(TAG, "loadDeck - SD card mounted and existent file -> Loading deck...");
		try
		{
			// Open the right deck.
			AnkiDb.openDatabase(deckFilename);
			// Start by getting the first card and displaying it.
			nextCard();
			Log.i(TAG, "Deck loaded!");
			return DECK_LOADED;
		} catch (SQLException e)
		{
			Log.i(TAG, "The database " + deckFilename + " could not be opened = " + e.getMessage());
			//Dismiss dialog and show something to indicate to the user that a deck has not been loaded
			//dialog.dismiss();
			//displayDeckNotLoaded();
			return DECK_NOT_LOADED;
		} catch (CursorIndexOutOfBoundsException e)
		{
			Log.i(TAG, "The deck has no cards = " + e.getMessage());
			//dialog.dismiss();
			//displayNoCardsInDeck();
			return DECK_EMPTY;
		}
	}


	private Handler handler = new Handler()
	{
		public void handleMessage(Message msg)
		{
			switch(msg.what)
			{
				case DECK_LOADED:
					dialog.dismiss();
					showControls(true);
					displayCardQuestion();
					break;
					
				case DECK_NOT_LOADED:
					dialog.dismiss();
					displayDeckNotLoaded();
					break;
				
				case DECK_EMPTY:
					dialog.dismiss();
					displayNoCardsInDeck();
					break;
				
				default:
					dialog.dismiss();
			}
		}
	};
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
		super.onActivityResult(requestCode, resultCode, intent);
		if (requestCode == PICK_DECK_REQUEST)
		{
			if (resultCode != RESULT_OK)
			{
				Log.e(TAG, "onActivityResult - Deck browser returned with error");
				deckSelected = false;
				return;
			}
			if (intent == null)
			{
				deckSelected = false;
				Log.e(TAG, "onActivityResult - Deck browser returned null intent");
				return;
			}
			// A deck was picked. Save it in preferences and use it.
			Log.i(TAG, "onActivityResult = OK");
			deckFilename = intent.getExtras().getString(OPT_DB);
			savePreferences();

			// Don't open database again in onResume(). Load the new one in
			// another thread instead.
			deckSelected = true;
        	Log.i(TAG, "onActivityResult - deckSelected = " + deckSelected);
			displayProgressDialogAndLoadDeck();
		} else if (requestCode == PREFERENCES_UPDATE)
		{
			restorePreferences();
			//If any deck has been selected (usually because there was no sd card attached, and therefore was impossible to select one) 
			//the controls have not been initialized, so we don't have to try to show or hide them
			if(deckSelected && isSdCardMounted())
				showOrHideControls();
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	  super.onConfigurationChanged(newConfig);
	  
	  LinearLayout sdLayout = (LinearLayout) findViewById(R.id.sd_layout);
	  if(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) 
		  sdLayout.setPadding(0, 50, 0, 0);
	  else if(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
		  sdLayout.setPadding(0, 100, 0, 0);
		  
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
	
	
	private void showControls(boolean show)
	{

		if (show)
		{
			mCard.setVisibility(View.VISIBLE);
			mSelectRemembered.setVisibility(View.VISIBLE);
			mSelectNotRemembered.setVisibility(View.VISIBLE);
			mFlipCard.setVisibility(View.VISIBLE);
			showOrHideControls();
			hideDeckErrors();
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
		Log.i(TAG, "updateCard");
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


	
	private boolean isSdCardMounted() {
		return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
	}
	
    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     * The intent will call closeExternalStorageFiles() if the external media
     * is going to be ejected, so applications can clean up any files they have open.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                    	Log.i(TAG, "mUnmountReceiver - Action = Media Eject");
                    	closeExternalStorageFiles();
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    	Log.i(TAG, "mUnmountReceiver - Action = Media Mounted");
                    	hideSdError();
                    	deckSelected = false;
                    	Log.i(TAG, "mUnmountReceiver - deckSelected = " + deckSelected);
                    	onResume();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }
    
    private void closeExternalStorageFiles()
    {
    	AnkiDb.closeDatabase();
    	displaySdError();
    }
    
    private void displaySdError() 
    {
    	showControls(false);
    	hideDeckErrors();
    	showSdCardElements(true);
    }
    
    private void hideSdError()
    {
    	showSdCardElements(false);
    }
    
    private void showSdCardElements(boolean show)
    {
    	LinearLayout layout = (LinearLayout) findViewById(R.id.sd_layout);
    	TextView tv = (TextView) findViewById(R.id.sd_message);
    	ImageView image = (ImageView) findViewById(R.id.sd_icon);
    	if(show)
    	{        		
    		layout.setVisibility(View.VISIBLE);
    		tv.setVisibility(View.VISIBLE);
    		image.setVisibility(View.VISIBLE);
    	} else
    	{
    		layout.setVisibility(View.GONE);
    		tv.setVisibility(View.GONE);
    		image.setVisibility(View.GONE);
    	}
    }
    
    private void displayDeckNotLoaded()
    {
    	LinearLayout layout = (LinearLayout) findViewById(R.id.deck_error_layout);
    	TextView message = (TextView) findViewById(R.id.deck_message);
    	TextView detail = (TextView) findViewById(R.id.deck_message_detail);
     		
		message.setText(R.string.deck_not_loaded);
		detail.setText(R.string.deck_not_loaded_detail);
		layout.setVisibility(View.VISIBLE);
		message.setVisibility(View.VISIBLE);
		detail.setVisibility(View.VISIBLE);
    }
    
    private void hideDeckErrors()
    {
    	LinearLayout layout = (LinearLayout) findViewById(R.id.deck_error_layout);
    	TextView message = (TextView) findViewById(R.id.deck_message);
    	TextView detail = (TextView) findViewById(R.id.deck_message_detail);

		layout.setVisibility(View.GONE);
		message.setVisibility(View.GONE);
		detail.setVisibility(View.GONE);
    }
    
	private void displayNoCardsInDeck() 
	{
    	LinearLayout layout = (LinearLayout) findViewById(R.id.deck_error_layout);
    	TextView message = (TextView) findViewById(R.id.deck_message);
    	TextView detail = (TextView) findViewById(R.id.deck_message_detail);
     		
		message.setText(R.string.deck_empty);
		layout.setVisibility(View.VISIBLE);
		message.setVisibility(View.VISIBLE);
		detail.setVisibility(View.GONE);
	}
}