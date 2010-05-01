/****************************************************************************************
* Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
* Copyright (c) 2009 Andrew <andrewdubya@gmail.                                        *
* Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
* Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
* Copyright (c) 2009 Jordi Chacon <jordi.chacon@gmail.com>                             *
* Copyright (c) 2010 Rick Gruber-Riemer <rick@vanosten.net>                            *
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.SQLException;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.ichi2.utils.DiffEngine;
import com.ichi2.utils.RubyParser;
import com.tomgibara.android.veecheck.util.PrefSettings;

/**
 * Main activity for AnkiDroid. Shows a card and controls to answer it.
 */
public class AnkiDroid extends Activity
{

	/**
	 * Default database
	 */
	public static final String OPT_DB = "com.ichi2.anki.deckFilename";

	/**
	 * Tag for logging messages
	 */
	private static final String TAG = "AnkiDroid";
	
	/** Max size of the font for dynamic calculation of font size */
	protected static final int MAX_DYNAMIC_FONT_SIZE = 14;

	/** Min size of the font for dynamic calculation of font size */
	protected static final int MIN_DYNAMIC_FONT_SIZE = 3;
	
	/** The percentage of the absolute font size specified in the deck. */
	private int displayFontSize = 100;

	/**
	 * Menus
	 */
	public static final int MENU_OPEN = 0;

	public static final int MENU_PREFERENCES = 1;

	public static final int MENU_ABOUT = 2;

	public static final int MENU_DECKOPTS = 3;

	public static final int MENU_SUSPEND = 4;

    private static final int MENU_EDIT = 5; 
    
    private static final int MENU_GET_SHARED_DECKS = 6;

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

    public static final int EDIT_CURRENT_CARD = 2;
    
    public static final int GET_SHARED_DECK = 3;

	/**
	 * Variables to hold the state
	 */
	private ProgressDialog progressDialog;

	private AlertDialog updateAlert;
	
	private AlertDialog noConnectionAlert;
	
	private AlertDialog connectionFailedAlert;

	private BroadcastReceiver mUnmountReceiver = null;

	/**
	 * Name of the last deck loaded
	 */
	private String deckFilename;
	
    /**
     * Indicates if a deck is trying to be load. onResume() won't try to load a deck if deckSelected is true.
     * We don't have to worry to set deckSelected to true, it's done automatically in displayProgressDialogAndLoadDeck().
     * We have to set deckSelected to false only on these situations a deck has to be reload and when we know for sure no other thread is trying to load a deck (for example, when sd card is mounted again)
     */
	private boolean deckSelected;

	private boolean deckLoaded;
	
	private boolean cardsToReview;

	private boolean sdCardAvailable = isSdCardMounted();
	
	private boolean inDeckPicker;

	private boolean corporalPunishments;

	private boolean timerAndWhiteboard;

	private boolean writeAnswers;
	
	/** Preference: parse for ruby annotations */
	private boolean useRubySupport;
	
	/** Preference: hide the question when showing the answer */
	private int hideQuestionInAnswer;
	
	private static final int HQIA_DO_HIDE = 0;
	
	private static final int HQIA_DO_SHOW = 1;
	
	private static final int HQIA_CARD_MODEL = 2;

	private boolean updateNotifications; // TODO use Veecheck only if this is true

	public String cardTemplate;

	private Card currentCard;
	
	/**
	 * To be assigned as the currentCard or a new card to be sent to and from the editor
	 */
    private static Card editorCard;

	/**
	 * Variables to hold layout objects that we need to update or handle events for
	 */
	private WebView mCard;

	private ToggleButton mToggleWhiteboard, mFlipCard;

	private EditText mAnswerField;

	private Button mButtonReviewEarly, mEase0, mEase1, mEase2, mEase3;

	private Chronometer mCardTimer;
	
	/**
	 * Time (in ms) at which the session will be over.
	 */
	private long mSessionTimeLimit;

	private int mSessionCurrReps = 0;

	private Whiteboard mWhiteboard;

	/**
	 * Handler for the flip toogle button, between the question and the answer of a card.
	 */
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

	/**
	 * Handler for the Whiteboard toggle button.
	 */
	CompoundButton.OnCheckedChangeListener mToggleOverlayHandler = new CompoundButton.OnCheckedChangeListener()
	{
		public void onCheckedChanged(CompoundButton btn, boolean state)
		{
			setOverlayState(state);
		}
	};

	View.OnClickListener mSelectEaseHandler = new View.OnClickListener()
	{
		public void onClick(View view)
		{
			int ease;
			switch (view.getId())
			{
			case R.id.ease1:
				ease = 1;
				if (corporalPunishments)
				{
					Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
					v.vibrate(500);
				}
				break;
			case R.id.ease2:
				ease = 2;
				break;
			case R.id.ease3:
				ease = 3;
				break;
			case R.id.ease4:
				ease = 4;
				break;
			default:
				ease = 0;
				return;
			}

			DeckTask.launchDeckTask(
					DeckTask.TASK_TYPE_ANSWER_CARD,
					mAnswerCardHandler,
					new DeckTask.TaskData(ease, AnkiDroidApp.getDeck(), currentCard));
		}
	};

	View.OnClickListener mButtonReviewEarlyHandler = new View.OnClickListener()
	{
		public void onClick(View view)
		{
			Log.i(TAG, "mButtonReviewEarlyHandler");
			mButtonReviewEarly.setVisibility(View.GONE);
			Deck d = AnkiDroidApp.getDeck();
			d.setReviewEarly(true);
			currentCard = d.getCard();
			if (currentCard != null){
				showControls(true);
				deckLoaded = true;
				cardsToReview = true;
				mFlipCard.setChecked(false);
				displayCardQuestion();

				mWhiteboard.clear();
				mCardTimer.setBase(SystemClock.elapsedRealtime());
				mCardTimer.start();
				long timelimit = AnkiDroidApp.getDeck().getSessionTimeLimit() * 1000;
				Log.i(TAG, "SessionTimeLimit: " + timelimit + " ms.");
				mSessionTimeLimit = System.currentTimeMillis() + timelimit;
				mSessionCurrReps = 0;
	
			}
				
			
		}
	};

    public static Card getEditorCard () {
        return editorCard;
    }

	@Override
	public void onCreate(Bundle savedInstanceState) throws SQLException
	{
		super.onCreate(savedInstanceState);
		Log.i(TAG, "onCreate - savedInstanceState: " + savedInstanceState);

		Bundle extras = getIntent().getExtras();
		SharedPreferences preferences = restorePreferences();
		initAlertDialogs();
		initLayout(R.layout.flashcard_portrait);

		registerExternalStorageListener();
		initResourceValues();

		if (extras != null && extras.getString(OPT_DB) != null)
		{
			// A deck has just been selected in the decks browser.
			deckFilename = extras.getString(OPT_DB);
			Log.i(TAG, "onCreate - deckFilename from extras: " + deckFilename);
		} else if (savedInstanceState != null)
		{
			// Use the same deck as last time AnkiDroid was used.
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
					String SAMPLE_DECK_FILENAME = AnkiDroidApp.getStorageDirectory() + "/country-capitals.anki";
					if (!new File(/*
								 * deckFilename triggers NPE bug in
								 * java.io.File.java
								 */AnkiDroidApp.getStorageDirectory(), "country-capitals.anki").exists())
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
				} else
				{
					// Show the deck picker.
					openDeckPicker();
				}
			}

		}
	}


	/**
	 * Retrieve resource values.
	 */
	public void initResourceValues()
	{
		Resources r = getResources();
		cardTemplate = r.getString(R.string.card_template);
	}

	/**
	 * Create AlertDialogs used on all the activity
	 */
	private void initAlertDialogs()
	{
		Resources res = getResources();
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setMessage(res.getString(R.string.connection_needed));
		builder.setPositiveButton(res.getString(R.string.ok), null);
		noConnectionAlert = builder.create();
		
	    builder.setMessage(res.getString(R.string.connection_unsuccessful));
	    connectionFailedAlert = builder.create();
	}
	
	/**
	 * Set the content view to the one provided and initialize accessors.
	 */
	public void initLayout(Integer layout)
	{
		Log.i(TAG, "initLayout - Beginning");
		setContentView(layout);

		mCard = (WebView) findViewById(R.id.flashcard);
		mButtonReviewEarly = (Button) findViewById(R.id.review_early);
		mEase0 = (Button) findViewById(R.id.ease1);
		mEase1 = (Button) findViewById(R.id.ease2);
		mEase2 = (Button) findViewById(R.id.ease3);
		mEase3 = (Button) findViewById(R.id.ease4);
		mCardTimer = (Chronometer) findViewById(R.id.card_time);
		mFlipCard = (ToggleButton) findViewById(R.id.flip_card);
		mToggleWhiteboard = (ToggleButton) findViewById(R.id.toggle_overlay);
		mWhiteboard = (Whiteboard) findViewById(R.id.whiteboard);
		mAnswerField = (EditText) findViewById(R.id.answer_field);

		showControls(false);

		mButtonReviewEarly.setOnClickListener(mButtonReviewEarlyHandler);
		mEase0.setOnClickListener(mSelectEaseHandler);
		mEase1.setOnClickListener(mSelectEaseHandler);
		mEase2.setOnClickListener(mSelectEaseHandler);
		mEase3.setOnClickListener(mSelectEaseHandler);
		mFlipCard.setChecked(true); // Fix for mFlipCardHandler not being called on first deck load.
		mFlipCard.setOnCheckedChangeListener(mFlipCardHandler);
		mToggleWhiteboard.setOnCheckedChangeListener(mToggleOverlayHandler);

		mCard.setFocusable(false);

		Log.i(TAG, "initLayout - Ending");

	}

	/** 
	 * Creates the menu items
	 */
	@Override
    public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_OPEN, 0, getString(R.string.switch_another_deck));
		menu.add(1, MENU_PREFERENCES, 0, getString(R.string.preferences));
		menu.add(1, MENU_ABOUT, 0, getString(R.string.about));
		menu.add(1, MENU_DECKOPTS, 0, getString(R.string.study_options));
		menu.add(1, MENU_SUSPEND, 0, getString(R.string.suspend));
        menu.add(1, MENU_EDIT, 0, getString(R.string.edit_card)); //Edit the current card.
        menu.add(1, MENU_GET_SHARED_DECKS, 0, getString(R.string.get_shared_deck));
		return true;
	}

	public boolean onPrepareOptionsMenu(Menu menu)
	{
		Log.i(TAG, "sdCardAvailable = " + sdCardAvailable + ", deckLoaded = " + deckLoaded);
		menu.findItem(MENU_DECKOPTS).setEnabled(sdCardAvailable && deckLoaded);
		menu.findItem(MENU_SUSPEND).setEnabled(currentCard != null);
		menu.findItem(MENU_SUSPEND).setVisible(currentCard != null);
		menu.findItem(MENU_EDIT).setEnabled(currentCard != null);
		menu.findItem(MENU_EDIT).setVisible(currentCard != null);
		return true;
	}
	
	/**
	 * Handles item selections
	 */
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
		case MENU_DECKOPTS:
		    Intent opts = new Intent(this, DeckPreferences.class);
		    startActivity( opts );
		    return true;
		case MENU_SUSPEND:
			mFlipCard.setChecked(true);
			DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SUSPEND_CARD, 
					mAnswerCardHandler,
					new DeckTask.TaskData(0, AnkiDroidApp.getDeck(), currentCard));
		    return true;
        case MENU_EDIT:
            editorCard = currentCard;
            Intent editCard = new Intent(this, CardEditor.class);
            startActivityForResult(editCard, EDIT_CURRENT_CARD);
            return true;
        case MENU_GET_SHARED_DECKS:
        	Connection.getSharedDecks(getSharedDecksListener, new Connection.Payload(new Object[] {}));
        	return true;
		}
		return false;
	}

	public void openDeckPicker()
	{
    	Log.i(TAG, "openDeckPicker - deckSelected = " + deckSelected);
    	
    	if(AnkiDroidApp.getDeck() != null && sdCardAvailable)
    		AnkiDroidApp.getDeck().closeDeck();
    	deckLoaded = false;
		Intent decksPicker = new Intent(this, DeckPicker.class);
		inDeckPicker = true;
		startActivityForResult(decksPicker, PICK_DECK_REQUEST);
		Log.i(TAG, "openDeckPicker - Ending");
	}

	public void openSharedDeckPicker()
	{
    	if(AnkiDroidApp.getDeck() != null && sdCardAvailable)
    		AnkiDroidApp.getDeck().closeDeck();
    	deckLoaded = false;
		Intent intent = new Intent(AnkiDroid.this, SharedDeckPicker.class);
		startActivityForResult(intent, GET_SHARED_DECK);
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

		//registerExternalStorageListener();
		
		if (!deckSelected)
		{
			Log.i(TAG, "onResume() - No deck selected before");
			displayProgressDialogAndLoadDeck();
		}

		Log.i(TAG, "onResume() - Ending");
	}
	
	
	@Override
	protected void onDestroy() {
		Log.i(TAG, "onDestroy()");
		super.onDestroy();
    	unregisterReceiver(mUnmountReceiver);
	}

	private void displayProgressDialogAndLoadDeck()
	{
		displayProgressDialogAndLoadDeck(false);
	}

	private void displayProgressDialogAndLoadDeck(boolean updateAllCards)
	{
		Log.i(TAG, "displayProgressDialogAndLoadDeck - Loading deck " + deckFilename + ", update all cards = " + updateAllCards);

		// Don't open database again in onResume() until we know for sure this attempt to load the deck is finished
		deckSelected = true;

		if(isSdCardMounted())
		{
			if (deckFilename != null && new File(deckFilename).exists())
			{
				showControls(false);
				if(updateAllCards)
				{
					DeckTask.launchDeckTask(
							DeckTask.TASK_TYPE_LOAD_DECK_AND_UPDATE_CARDS,
							mLoadDeckHandler,
							new DeckTask.TaskData(deckFilename));
				}
				else
				{
					DeckTask.launchDeckTask(
							DeckTask.TASK_TYPE_LOAD_DECK,
							mLoadDeckHandler,
							new DeckTask.TaskData(deckFilename));
				}
			}
			else
			{
				if(deckFilename == null){
					Log.i(TAG, "displayProgressDialogAndLoadDeck - SD card unmounted.");
				}
				else if(!new File(deckFilename).exists()){
					Log.i(TAG, "displayProgressDialogAndLoadDeck - The deck " + deckFilename + "does not exist.");
				}

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
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
		super.onActivityResult(requestCode, resultCode, intent);
		if (requestCode == PICK_DECK_REQUEST)
		{
			//Clean the previous card before showing the first of the new loaded deck (so the transition is not so abrupt)
			updateCard("");
			hideSdError();
			hideDeckErrors();
			inDeckPicker = false;
			
			if (resultCode != RESULT_OK)
			{
				Log.e(TAG, "onActivityResult - Deck browser returned with error");
				//Make sure we open the database again in onResume() if user pressed "back"
				deckSelected = false;
				return;
			}
			if (intent == null)
			{
				Log.e(TAG, "onActivityResult - Deck browser returned null intent");
				//Make sure we open the database again in onResume()
				deckSelected = false;
				return;
			}
			// A deck was picked. Save it in preferences and use it.
			Log.i(TAG, "onActivityResult = OK");
			deckFilename = intent.getExtras().getString(OPT_DB);
			savePreferences();

			Log.i(TAG, "onActivityResult - deckSelected = " + deckSelected);
			displayProgressDialogAndLoadDeck();
		} else if (requestCode == PREFERENCES_UPDATE)
		{
			restorePreferences();
			//If there is no deck loaded the controls have not to be shown
			if(deckLoaded && cardsToReview)
			{
				showOrHideControls();
				showOrHideAnswerField();
			}
		} else if (requestCode == EDIT_CURRENT_CARD) {
			if (CardEditor.SAVE_CARD == resultCode) {
				DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT,
						mUpdateCardHandler, new DeckTask.TaskData(0, AnkiDroidApp
								.getDeck(), currentCard));
				mFlipCard.setChecked(true);
				displayCardQuestion();
			}
		} else if(requestCode == GET_SHARED_DECK)
		{
			//Clean the previous card before showing the first of the new loaded deck (so the transition is not so abrupt)
			updateCard("");
			hideSdError();
			hideDeckErrors();
			
			if (resultCode != RESULT_OK)
			{
				Log.e(TAG, "onActivityResult - Deck browser returned with error");
				//Make sure we open the database again in onResume() if user pressed "back"
				deckSelected = false;
				return;
			}
			if (intent == null)
			{
				Log.e(TAG, "onActivityResult - Deck browser returned null intent");
				//Make sure we open the database again in onResume()
				deckSelected = false;
				return;
			}
			// A deck was picked. Save it in preferences and use it.
			Log.i(TAG, "onActivityResult = OK");
			deckFilename = intent.getExtras().getString(OPT_DB);
			savePreferences();

        	Log.i(TAG, "onActivityResult - deckSelected = " + deckSelected);
        	// Load deck and update all cards, because if that is not done both the answer and question will be empty
			displayProgressDialogAndLoadDeck(true);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	  super.onConfigurationChanged(newConfig);

	  Log.i(TAG, "onConfigurationChanged");

	  LinearLayout sdLayout = (LinearLayout) findViewById(R.id.sd_layout);
	  if(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
		  sdLayout.setPadding(0, 50, 0, 0);
	  else if(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
		  sdLayout.setPadding(0, 100, 0, 0);

	  mWhiteboard.rotate();
	}


	private void showControls(boolean show)
	{
		if (show)
		{
			mCard.setVisibility(View.VISIBLE);
			mEase0.setVisibility(View.VISIBLE);
			mEase1.setVisibility(View.VISIBLE);
			mEase2.setVisibility(View.VISIBLE);
			mEase3.setVisibility(View.VISIBLE);
			mFlipCard.setVisibility(View.VISIBLE);
			showOrHideControls();
			showOrHideAnswerField();
			hideDeckErrors();
		} else
		{
			mCard.setVisibility(View.GONE);
			mButtonReviewEarly.setVisibility(View.GONE);
			mEase0.setVisibility(View.GONE);
			mEase1.setVisibility(View.GONE);
			mEase2.setVisibility(View.GONE);
			mEase3.setVisibility(View.GONE);
			mFlipCard.setVisibility(View.GONE);
			mCardTimer.setVisibility(View.GONE);
			mToggleWhiteboard.setVisibility(View.GONE);
			mWhiteboard.setVisibility(View.GONE);
			mAnswerField.setVisibility(View.GONE);
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
			mCardTimer.setVisibility(View.GONE);
			mToggleWhiteboard.setVisibility(View.GONE);
			mWhiteboard.setVisibility(View.GONE);
		} else
		{
			mCardTimer.setVisibility(View.VISIBLE);
			mToggleWhiteboard.setVisibility(View.VISIBLE);
			if (mToggleWhiteboard.isChecked())
			{
				mWhiteboard.setVisibility(View.VISIBLE);
			}
		}
	}

	/**
	 * Depending on preferences, show or hide the answer field.
	 */
	private void showOrHideAnswerField()
	{
		Log.i(TAG, "showOrHideAnswerField - writeAnswers: " + writeAnswers);
		if (!writeAnswers)
		{
			mAnswerField.setVisibility(View.GONE);
		} else
		{
			mAnswerField.setVisibility(View.VISIBLE);
		}
	}

	public void setOverlayState(boolean enabled)
	{
		mWhiteboard.setVisibility((enabled) ? View.VISIBLE : View.GONE);
	}

	/**
	 * Set up the display for the current card.
	 */
	private void displayCardQuestion()
	{
		Log.i(TAG, "displayCardQuestion");

		if (currentCard == null)
		{
			// Either the deck does not contain any card, or all reviews have been done for the time being
			// TODO a button leading to the deck picker would be nice.
			updateCard(getString(R.string.congratulations_finished_for_now));
			mButtonReviewEarly.setVisibility(View.VISIBLE);
			mEase0.setVisibility(View.GONE);
			mEase1.setVisibility(View.GONE);
			mEase2.setVisibility(View.GONE);
			mEase3.setVisibility(View.GONE);
			mFlipCard.setVisibility(View.GONE);
			mCardTimer.setVisibility(View.GONE);
			mToggleWhiteboard.setVisibility(View.GONE);
			mWhiteboard.setVisibility(View.GONE);
			mAnswerField.setVisibility(View.GONE);
			
			cardsToReview = false;
		} else
		{
			Log.i(TAG, "displayCardQuestion - Hiding Ease buttons...");

			mEase0.setVisibility(View.GONE);
			mEase1.setVisibility(View.GONE);
			mEase2.setVisibility(View.GONE);
			mEase3.setVisibility(View.GONE);

			// If the user wants to write the answer
			if(writeAnswers)
			{
				mAnswerField.setVisibility(View.VISIBLE);
			}

			mFlipCard.setVisibility(View.VISIBLE);
			mFlipCard.requestFocus();

			String displayString = enrichWithQASpan(currentCard.question, false);
			//Depending on preferences do or do not show the question
			if (calculateShowQuestion()) {
				displayString = displayString + "<hr/>";
			}
			updateCard(displayString);
		}
	}
	
	/**
	 * Display the card answer.
	 */
	private void displayCardAnswer()
	{
		Log.i(TAG, "displayCardAnswer");

		mCardTimer.stop();
		mWhiteboard.lock();

		mEase0.setVisibility(View.VISIBLE);
		mEase1.setVisibility(View.VISIBLE);
		mEase2.setVisibility(View.VISIBLE);
		mEase3.setVisibility(View.VISIBLE);

		mAnswerField.setVisibility(View.GONE);

		mEase2.requestFocus();
		
		String displayString = "";

		// If the user wrote an answer
		if(writeAnswers)
		{
			if(currentCard != null)
			{
				// Obtain the user answer and the correct answer
				String userAnswer = mAnswerField.getText().toString();
				String correctAnswer = (String) currentCard.answer.subSequence(
						currentCard.answer.indexOf(">")+1,
						currentCard.answer.lastIndexOf("<"));

				// Obtain the diff and send it to updateCard
				DiffEngine diff = new DiffEngine();
				
				displayString = enrichWithQASpan(diff.diff_prettyHtml(
						diff.diff_main(userAnswer, correctAnswer)) +
						"<br/>" + currentCard.answer, true);
			}
			else
			{
				displayString = "";
			}
		}
		else
		{
			displayString = enrichWithQASpan(currentCard.answer, true);
		}
		//Depending on preferences do or do not show the question
		if (calculateShowQuestion()) {
			StringBuffer sb = new StringBuffer();
			sb.append(enrichWithQASpan(currentCard.question, false));
			sb.append("<hr/>");
			sb.append(displayString);
			displayString = sb.toString();
			mFlipCard.setVisibility(View.GONE);
		}
		updateCard(displayString);
	}
	
	private final boolean calculateShowQuestion() {
		if (HQIA_DO_SHOW == hideQuestionInAnswer) {
			return true;
		}
		if (HQIA_CARD_MODEL == hideQuestionInAnswer && 0 == Model.getModel(currentCard.cardModelId, false).getCardModel(currentCard.cardModelId).questionInAnswer) {
			return true;
		}
		return false;
	}
	
	/**
	 * Updates the main screen for learning with the actual contents (question/answer or something else).
	 * the contents is enriched based on preferences with Ruby and font size.
	 * @param content
	 */
	private void updateCard(String content)
	{
		Log.i(TAG, "updateCard");

		content = Sound.extractSounds(deckFilename, content);
		content = Image.loadImages(deckFilename, content);
		
		// In order to display the bold style correctly, we have to change font-weight to 700
		content = content.replaceAll("font-weight:600;", "font-weight:700;");

		// If ruby annotation support is activated, then parse and add markup
		if (useRubySupport) {
			content = RubyParser.ankiRubyToMarkup(content);
		}
		
		// Add CSS for font colour and font size
		if (null != currentCard) {
			Model myModel = Model.getModel(currentCard.cardModelId, false);
			content = myModel.getCSSForFontColorSize(currentCard.cardModelId, displayFontSize) + content;
		} else {
			mCard.getSettings().setDefaultFontSize(calculateDynamicFontSize(content));
		}

		Log.i(TAG, "content card = \n" + content);
		String card = cardTemplate.replace("::content::", content);
		mCard.loadDataWithBaseURL("", card, "text/html", "utf-8", null);
		Sound.playSounds();
	}
	
	/** Constant for class attribute signalling answer */
	protected final static String ANSWER_CLASS = "answer";
	
	/** Constant for class attribute signalling question */
	protected final static String QUESTION_CLASS = "question";
	
	/**
	 * Adds a span html tag around the contents to have an indication, where answer/question is displayed
	 * @param content
	 * @param isAnswer if true then the class attribute is set to "answer", "question" otherwise.
	 * @return
	 */
	private final static String enrichWithQASpan(String content, boolean isAnswer) {
		StringBuffer sb = new StringBuffer();
		sb.append("<span class=\"");
		if (isAnswer) {
			sb.append(ANSWER_CLASS);
		} else {
			sb.append(QUESTION_CLASS);
		}
		sb.append("\">");
		sb.append(content);
		sb.append("</span>");
		return sb.toString();
	}

	/**
	* Calculates a dynamic font size depending on the length of the contents
	* taking into account that the input string contains html-tags, which will not
	* be displayed and therefore should not be taken into account.
	* @param htmlContents
	* @return font size respecting MIN_DYNAMIC_FONT_SIZE and MAX_DYNAMIC_FONT_SIZE
	*/
	protected final static int calculateDynamicFontSize(String htmlContent) {
		// Replace each <br> with 15 spaces, each <hr> with 30 spaces, then remove all html tags and spaces
		String realContent = htmlContent.replaceAll("\\<br.*?\\>", " ");
		realContent = realContent.replaceAll("\\<hr.*?\\>", " ");
		realContent = realContent.replaceAll("\\<.*?\\>", "");
		realContent = realContent.replaceAll("&nbsp;", " ");
		return Math.max(MIN_DYNAMIC_FONT_SIZE, MAX_DYNAMIC_FONT_SIZE - (int)(realContent.length()/5));
	}


	/**
	 * Utility method to write to a file.
	 */
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

	private SharedPreferences restorePreferences() {
		SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
		corporalPunishments = preferences.getBoolean("corporalPunishments", false);
		timerAndWhiteboard = preferences.getBoolean("timerAndWhiteboard", true);
		Log.i(TAG, "restorePreferences - timerAndWhiteboard: " + timerAndWhiteboard);
		writeAnswers = preferences.getBoolean("writeAnswers", false);
		useRubySupport = preferences.getBoolean("useRubySupport", false);
		displayFontSize = Integer.parseInt(preferences.getString("displayFontSize", "100"));
		hideQuestionInAnswer = Integer.parseInt(preferences.getString("hideQuestionInAnswer", Integer.toString(HQIA_DO_SHOW)));
		updateNotifications = preferences.getBoolean("enabled", true);
		
		//redraw screen with new preferences
		if (null != mFlipCard) {
			if (mFlipCard.isChecked()) {
				displayCardAnswer();
			} else {
				displayCardQuestion();
			}
		}

		return preferences;
	}

	private void savePreferences()
	{
		SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
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
                    	sdCardAvailable = false;
                    	closeExternalStorageFiles();
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    	Log.i(TAG, "mUnmountReceiver - Action = Media Mounted");
                    	hideSdError();
                    	deckSelected = false;
                    	sdCardAvailable = true;
                    	Log.i(TAG, "mUnmountReceiver - deckSelected = " + deckSelected);
                    	if(!inDeckPicker)
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
    	if(AnkiDroidApp.getDeck() != null)
    		AnkiDroidApp.getDeck().closeDeck();
    	deckLoaded = false;
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
    	Log.i(TAG, "showSdCardElements");

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
    	Log.i(TAG, "displayDeckNotLoaded");

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
    	Log.i(TAG, "hideDeckErrors");

    	LinearLayout layout = (LinearLayout) findViewById(R.id.deck_error_layout);
    	TextView message = (TextView) findViewById(R.id.deck_message);
    	TextView detail = (TextView) findViewById(R.id.deck_message_detail);

		layout.setVisibility(View.GONE);
		message.setVisibility(View.GONE);
		detail.setVisibility(View.GONE);
    }

	private void displayNoCardsInDeck()
	{
    	Log.i(TAG, "displayNoCardsInDeck");

    	LinearLayout layout = (LinearLayout) findViewById(R.id.deck_error_layout);
    	TextView message = (TextView) findViewById(R.id.deck_message);
    	TextView detail = (TextView) findViewById(R.id.deck_message_detail);

		message.setText(R.string.deck_empty);
		layout.setVisibility(View.VISIBLE);
		message.setVisibility(View.VISIBLE);
		detail.setVisibility(View.GONE);
	}
	  
	/**
	 * Listeners
	 */
    DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener()
    {
        public void onPreExecute() {
            progressDialog = ProgressDialog.show(AnkiDroid.this, "", getString(R.string.saving_changes), true);
        }

        public void onPostExecute(DeckTask.TaskData result) {

            // Set the correct value for the flip card button - That triggers the
            // listener which displays the question of the card
            mFlipCard.setChecked(false);
            mWhiteboard.clear();
            mCardTimer.setBase(SystemClock.elapsedRealtime());
            mCardTimer.start();

            progressDialog.dismiss();
        }

        public void onProgressUpdate(DeckTask.TaskData... values) 
        {
            currentCard = values[0].getCard();
        }
    };


	DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener()
	{
	    boolean sessioncomplete = false;
	    long start;

		public void onPreExecute() {
			start = System.currentTimeMillis();
			progressDialog = ProgressDialog.show(AnkiDroid.this, "", getString(R.string.loading_new_card), true);
		}

		public void onPostExecute(DeckTask.TaskData result) {
		    // TODO show summary screen?
			if( sessioncomplete )
			    openDeckPicker();
		}

		public void onProgressUpdate(DeckTask.TaskData... values) {
		    mSessionCurrReps++; // increment number reps counter

		    // Check to see if session rep or time limit has been reached
		    Deck deck = AnkiDroidApp.getDeck();
		    long sessionRepLimit = deck.getSessionRepLimit();
		    long sessionTime = deck.getSessionTimeLimit();
		    Toast sessionMessage = null;

		    if( (sessionRepLimit > 0) && (mSessionCurrReps >= sessionRepLimit) )
		    {
		    	sessioncomplete = true;
		    	sessionMessage = Toast.makeText(AnkiDroid.this, getString(R.string.session_question_limit_reached), Toast.LENGTH_SHORT);
		    } else if( (sessionTime > 0) && (System.currentTimeMillis() >= mSessionTimeLimit) ) //Check to see if the session time limit has been reached
		    {
		        // session time limit reached, flag for halt once async task has completed.
		        sessioncomplete = true;
		        sessionMessage = Toast.makeText(AnkiDroid.this, getString(R.string.session_time_limit_reached), Toast.LENGTH_SHORT);

		    } else {
		        // session limits not reached, show next card
		    	sessioncomplete = false;
		        Card newCard = values[0].getCard();

		        currentCard = newCard;

		        // Set the correct value for the flip card button - That triggers the
		        // listener which displays the question of the card
		        mFlipCard.setChecked(false);
		        mWhiteboard.clear();
		        mCardTimer.setBase(SystemClock.elapsedRealtime());
		        mCardTimer.start();
		    }

		    progressDialog.dismiss();

			// Show a message to user if a session limit has been reached.
			if (sessionMessage != null)
				sessionMessage.show();
			
			Log.w(TAG, "onProgressUpdate - New card received in " + (System.currentTimeMillis() - start) + " ms.");
		}

	};

	DeckTask.TaskListener mLoadDeckHandler = new DeckTask.TaskListener()
	{

		public void onPreExecute() {
			if(updateAlert == null || !updateAlert.isShowing())
			{
				progressDialog = ProgressDialog.show(AnkiDroid.this, "", getString(R.string.loading_deck), true);
			}
		}

		public void onPostExecute(DeckTask.TaskData result) {
			// This verification would not be necessary if onConfigurationChanged it's executed correctly (which seems that emulator does not do)
			if(progressDialog.isShowing())
			{
				try
				{
					progressDialog.dismiss();
				} catch(Exception e)
				{
					Log.e(TAG, "handleMessage - Dialog dismiss Exception = " + e.getMessage());
				}
			}

			switch(result.getInt())
			{
				case DECK_LOADED:
					// Set the deck in the application instance, so other activities
					// can access the loaded deck.
					AnkiDroidApp.setDeck( result.getDeck() );
					currentCard = result.getCard();
					showControls(true);
					deckLoaded = true;
					cardsToReview = true;
					mFlipCard.setChecked(false);
					displayCardQuestion();

					mWhiteboard.clear();
					mCardTimer.setBase(SystemClock.elapsedRealtime());
					mCardTimer.start();
					long timelimit = AnkiDroidApp.getDeck().getSessionTimeLimit() * 1000;
					Log.i(TAG, "SessionTimeLimit: " + timelimit + " ms.");
					mSessionTimeLimit = System.currentTimeMillis() + timelimit;
					mSessionCurrReps = 0;
					break;

				case DECK_NOT_LOADED:
					displayDeckNotLoaded();
					break;

				case DECK_EMPTY:
					displayNoCardsInDeck();
					break;
			}
		}

		public void onProgressUpdate(DeckTask.TaskData... values) {
			// Pass
		}

	};

	Connection.TaskListener getSharedDecksListener = new Connection.TaskListener() {

		@Override
		public void onDisconnected() {
			noConnectionAlert.show();
		}

		@SuppressWarnings("unchecked")
		@Override
		public void onPostExecute(Payload data) {
			progressDialog.dismiss();
			if(data.success)
			{
				openSharedDeckPicker();
			}
			else
			{
				connectionFailedAlert.show();
			}
		}

		@Override
		public void onPreExecute() {
			progressDialog = ProgressDialog.show(AnkiDroid.this, "", getResources().getString(R.string.loading_shared_decks));
		}

		@Override
		public void onProgressUpdate(Object... values) {
			//Pass
		}
		
	};

}
