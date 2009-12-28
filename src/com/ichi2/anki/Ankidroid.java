/****************************************************************************************
* Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
* Copyright (c) 2009 Andrew <andrewdubya@gmail.                                        *
* Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
* Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
* Copyright (c) 2009 Jordi Chacon <jordi.chacon@gmail.com>                             *
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
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
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

import com.ichi2.utils.DiffEngine;
import com.tomgibara.android.veecheck.util.PrefSettings;

/**
 * Main activity for Ankidroid. Shows a card and controls to answer it.
 *
 */
public class Ankidroid extends Activity// implements Runnable
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
	 * Max and min size of the font of the questions and answers
	 */
	private static final int MAX_FONT_SIZE = 14;
	private static final int MIN_FONT_SIZE = 3;

	/**
	 * Menus
	 */
	public static final int MENU_OPEN = 0;

	public static final int MENU_PREFERENCES = 1;

	public static final int MENU_ABOUT = 2;

	public static final int MENU_DECKOPTS = 3;


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
	private ProgressDialog progressDialog;

	private AlertDialog updateDialog;

    private BroadcastReceiver mUnmountReceiver = null;

	//Name of the last deck loaded
	private String deckFilename;
	
	private String deckPath;
	
    //Indicates if a deck is trying to be load. onResume() won't try to load a deck if deckSelected is true
    //We don't have to worry to set deckSelected to true, it's done automatically in displayProgressDialogAndLoadDeck()
    //We have to set deckSelected to false only on these situations a deck has to be reload and when we know for sure no other thread is trying to load a deck (for example, when sd card is mounted again)
	private boolean deckSelected;

	private boolean deckLoaded;
	
	private boolean cardsToReview;

	private boolean sdCardAvailable = isSdCardMounted();
	
	private boolean inDeckPicker;

	private boolean corporalPunishments;

	private boolean timerAndWhiteboard;

	private boolean spacedRepetition;

	private boolean writeAnswers;

	private boolean updateNotifications;

	public String cardTemplate;

	private Card currentCard;

	/**
	 * Variables to hold layout objects that we need to update or handle events for
	 */
	private WebView mCard;

	private ToggleButton mToggleWhiteboard, mFlipCard;

	private EditText mAnswerField;

	private Button mEase0, mEase1, mEase2, mEase3;

	private Chronometer mCardTimer;

	private ArrayList<MediaPlayer> sounds;
	
	//the time (in ms) at which the session will be over
	private long mSessionTimeLimit;

	private int mSessionCurrReps = 0;

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
					new DeckTask.TaskData(ease, AnkidroidApp.deck(), currentCard));
		}
	};


	@Override
	public void onCreate(Bundle savedInstanceState) throws SQLException
	{
		super.onCreate(savedInstanceState);
		Log.i(TAG, "onCreate - savedInstanceState: " + savedInstanceState);

		Bundle extras = getIntent().getExtras();
		SharedPreferences preferences = restorePreferences();
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

	/** Creates the menu items */
	@Override
    public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_OPEN, 0, "Switch to another deck");
		menu.add(1, MENU_PREFERENCES, 0, "Preferences");
		menu.add(1, MENU_ABOUT, 0, "About");
		menu.add(1, MENU_DECKOPTS, 0, "Study Options");
		return true;
	}

	public boolean onPrepareOptionsMenu(Menu menu)
	{
		Log.i(TAG, "sdCardAvailable = " + sdCardAvailable + ", deckLoaded = " + deckLoaded);
		menu.findItem(MENU_DECKOPTS).setEnabled(sdCardAvailable && deckLoaded);
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
		case MENU_DECKOPTS:
//		    Intent opts = new Intent(this, DeckPreferences.class);
			Intent opts = new Intent(this, StudyOptions.class);
		    startActivity( opts );
		    return true;
		}
		return false;
	}

	public void openDeckPicker()
	{
    	Log.i(TAG, "openDeckPicker - deckSelected = " + deckSelected);
    	if(AnkidroidApp.deck() != null && sdCardAvailable)
    		AnkidroidApp.deck().closeDeck();
    	deckLoaded = false;
		Intent decksPicker = new Intent(this, DeckPicker.class);
		inDeckPicker = true;
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
		Log.i(TAG, "displayProgressDialogAndLoadDeck - Loading deck " + deckFilename);

		// Don't open database again in onResume() until we know for sure this attempt to load the deck is finished
		deckSelected = true;

		if(isSdCardMounted())
		{
			if (deckFilename != null && new File(deckFilename).exists())
			{
				showControls(false);
				DeckTask.launchDeckTask(
						DeckTask.TASK_TYPE_LOAD_DECK,
						mLoadDeckHandler,
						new DeckTask.TaskData(deckFilename));
			}
			else
			{
				if(deckFilename == null) Log.i(TAG, "displayProgressDialogAndLoadDeck - SD card unmounted.");
				else if(!new File(deckFilename).exists()) Log.i(TAG, "displayProgressDialogAndLoadDeck - The deck " + deckFilename + "does not exist.");

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

	  //extra height that the Whiteboard should have to be able to write in all its surface either on the question or on the answer
	  int extraHeight = 0;
	  //if(mSelectRemembered.isShown() && mSelectNotRemembered.isShown())
	  // TODO: test for all buttons here, not just two.
	  if(mEase0.isShown() && mEase1.isShown())
	  {
		  //if the "Remembered" and "Not remembered" buttons are visible, their height has to be counted in the creation of the new Whiteboard
		  //because we should be able to write in their space when it is the front part of the card
		  extraHeight = java.lang.Math.max(mEase0.getHeight(), mEase1.getHeight());
	  }
	  mWhiteboard.rotate(extraHeight);
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

	// Set up the display for the current card.
	public void displayCardQuestion()
	{
		Log.i(TAG, "displayCardQuestion");

		if (currentCard == null)
		{
			// error :(
			updateCard("Unable to find a card!");
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

			mFlipCard.requestFocus();

			updateCard(currentCard.question);
		}
	}

	public void updateCard(String content)
	{
		Log.i(TAG, "updateCard");

		content = Sound.extractSounds(deckFilename, content);
		content = Image.loadImages(deckFilename, content);
		
		// We want to modify the font size depending on how long is the content
		// Replace each <br> with 15 spaces, then remove all html tags and spaces
		String realContent = content.replaceAll("\\<br.*?\\>", "               ");
		realContent = realContent.replaceAll("\\<.*?\\>", "");
		realContent = realContent.replaceAll("&nbsp;", " ");

		// Calculate the size of the font depending on the length of the content
		int size = Math.max(MIN_FONT_SIZE, MAX_FONT_SIZE - (int)(realContent.length()/5));
		mCard.getSettings().setDefaultFontSize(size);

		//In order to display the bold style correctly, we have to change font-weight to 700
		content = content.replaceAll("font-weight:600;", "font-weight:700;");

		Log.i(TAG, "content card = \n" + content);
		String card = cardTemplate.replace("::content::", content);
		mCard.loadDataWithBaseURL("", card, "text/html", "utf-8", null);
		Sound.playSounds();
	}

	// Display the card answer.
	public void displayCardAnswer()
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
				updateCard(diff.diff_prettyHtml(
						diff.diff_main(userAnswer, correctAnswer)) +
						"<br/>" + currentCard.answer);
			}
			else
			{
				updateCard("");
			}
		}
		else
		{
			updateCard(currentCard.answer);
		}
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
		SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
		corporalPunishments = preferences.getBoolean("corporalPunishments", false);
		timerAndWhiteboard = preferences.getBoolean("timerAndWhiteboard", true);
		Log.i(TAG, "restorePreferences - timerAndWhiteboard: " + timerAndWhiteboard);
		spacedRepetition = preferences.getBoolean("spacedRepetition", true);
		writeAnswers = preferences.getBoolean("writeAnswers", false);
		updateNotifications = preferences.getBoolean("enabled", true);
		deckPath = preferences.getString("deckPath", "/sdcard");

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
    	if(AnkidroidApp.deck() != null)
    		AnkidroidApp.deck().closeDeck();
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
	  
	DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener()
	{
	    boolean sessioncomplete = false;

		public void onPreExecute() {
			progressDialog = ProgressDialog.show(Ankidroid.this, "", "Loading new card...", true);
		}

		public void onPostExecute(DeckTask.TaskData result) {
		    // TODO show summary screen?
			if( sessioncomplete )
			    openDeckPicker();
		}

		public void onProgressUpdate(DeckTask.TaskData... values) {
		    mSessionCurrReps++; // increment number reps counter

		    // Check to see if session rep limit has been reached
		    long sessionRepLimit = AnkidroidApp.deck().getSessionRepLimit();
		    Toast sessionMessage = null;

		    if( (sessionRepLimit > 0) && (mSessionCurrReps >= sessionRepLimit) )
		    {
		    	sessioncomplete = true;
		    	sessionMessage = Toast.makeText(Ankidroid.this, "Session question limit reached", Toast.LENGTH_SHORT);
		    } else if( System.currentTimeMillis() >= mSessionTimeLimit ) //Check to see if the session time limit has been reached
		    {
		        // session time limit reached, flag for halt once async task has completed.
		        sessioncomplete = true;
		        sessionMessage = Toast.makeText(Ankidroid.this, "Session time limit reached", Toast.LENGTH_SHORT);

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
		}

	};

	DeckTask.TaskListener mLoadDeckHandler = new DeckTask.TaskListener()
	{

		public void onPreExecute() {
			if(updateDialog == null || !updateDialog.isShowing())
			{
				progressDialog = ProgressDialog.show(Ankidroid.this, "", "Loading deck. Please wait...", true);
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
				    AnkidroidApp.setDeck( result.getDeck() );
					currentCard = result.getCard();
					showControls(true);
					deckLoaded = true;
					cardsToReview = true;
					mFlipCard.setChecked(false);
					displayCardQuestion();

					mWhiteboard.clear();
					mCardTimer.setBase(SystemClock.elapsedRealtime());
					mCardTimer.start();
					long timelimit = AnkidroidApp.deck().getSessionTimeLimit() * 1000;
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

}