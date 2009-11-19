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
import android.database.SQLException;
import android.os.Bundle;
import android.os.Environment;
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
	 * Menus
	 */
	public static final int MENU_OPEN = 0;

	public static final int MENU_PREFERENCES = 1;

	public static final int MENU_ABOUT = 2;

	/**
	 * Dialogs
	 */
	public static final int DIALOG_UPDATE = 0;
	
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
		
    private BroadcastReceiver mUnmountReceiver = null;

    //Indicates if a deck is trying to be load. onResume() won't try to load a deck if deckSelected is true
    //We don't have to worry to set deckSelected to true, it's done automatically in displayProgressDialogAndLoadDeck()
    //We have to set deckSelected to false only on these situations a deck has to be reload and when we know for sure no other thread is trying to load a deck (for example, when sd card is mounted again) 
	private boolean deckSelected;

	private boolean deckLoaded;
	
	//Name of the last deck loaded
	private String deckFilename;

	private boolean corporalPunishments;

	private boolean timerAndWhiteboard;

	private boolean spacedRepetition;

	public String cardTemplate;

	//private AnkiDb.Card currentCard;
	private Card currentCard;
	
	private Deck deck;

	/** 
	 * Variables to hold layout objects that we need to update or handle events for
	 */
	private WebView mCard;

	private ToggleButton mToggleWhiteboard, mFlipCard;

	//private Button mSelectRemembered, mSelectNotRemembered;
	private Button mEase0, mEase1, mEase2, mEase3;

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

//	// Handlers for buttons that allow user to select how well they did.
//	View.OnClickListener mSelectRememberedHandler = new View.OnClickListener()
//	{
//		public void onClick(View view)
//		{
//			// Space this card because it has been successfully remembered.
//			if (spacedRepetition)
//				currentCard.space();
//			nextCard();
//		}
//	};
//
//	View.OnClickListener mSelectNotRememberedHandler = new View.OnClickListener()
//	{
//		public void onClick(View view)
//		{
//			// Punish user.
//			if (corporalPunishments)
//			{
//				Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
//				v.vibrate(500);
//			}
//			// Reset this card because it has not been successfully remembered.
//			if (spacedRepetition)
//				currentCard.reset();
//			nextCard();
//		}
//	};

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
//			long start = System.currentTimeMillis();
//			deck.answerCard(currentCard, ease);
//			long stop = System.currentTimeMillis();
//			Log.v(TAG, "mSelectEaseHandler.onClick - Answered card in " + (stop - start) + " ms.");
			
			//nextCard(ease);
			
			DeckTask.launchDeckTask(
					DeckTask.TASK_TYPE_ANSWER_CARD,
					mAnswerCardHandler,
					new DeckTask.TaskData(ease, deck, currentCard));
		}
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) throws SQLException
	{		
		super.onCreate(savedInstanceState);
		Log.i(TAG, "onCreate - savedInstanceState: " + savedInstanceState);
		
		//checkUpdates();
		
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
//		mSelectRemembered = (Button) findViewById(R.id.select_remembered);
//		mSelectNotRemembered = (Button) findViewById(R.id.select_notremembered);
		mEase0 = (Button) findViewById(R.id.ease1);
		mEase1 = (Button) findViewById(R.id.ease2);
		mEase2 = (Button) findViewById(R.id.ease3);
		mEase3 = (Button) findViewById(R.id.ease4);
		mTimer = (Chronometer) findViewById(R.id.card_time);
		mFlipCard = (ToggleButton) findViewById(R.id.flip_card);
		mToggleWhiteboard = (ToggleButton) findViewById(R.id.toggle_overlay);
		mWhiteboard = (Whiteboard) findViewById(R.id.whiteboard);
		
		showControls(false);

//		mSelectRemembered.setOnClickListener(mSelectRememberedHandler);
//		mSelectNotRemembered.setOnClickListener(mSelectNotRememberedHandler);
		mEase0.setOnClickListener(mSelectEaseHandler);
		mEase1.setOnClickListener(mSelectEaseHandler);
		mEase2.setOnClickListener(mSelectEaseHandler);
		mEase3.setOnClickListener(mSelectEaseHandler);
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

	/*protected Dialog onCreateDialog(int id)
	{
		Dialog dialog;
		switch(id)
		{
		case DIALOG_UPDATE:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Update available");
			builder.setMessage("A new version of Ankidroid is available in Android Market. Would you like to install it?");
			builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				
				public void onClick(DialogInterface dialog, int which) {
					Uri ankidroidMarketURI = Uri.parse("http://market.android.com/search?q=pname:com.ichi2.anki");
					Intent searchUpdateIntent = new Intent(Intent.ACTION_VIEW, ankidroidMarketURI);
					startActivity(searchUpdateIntent);
				}
			});
			builder.setNegativeButton("No", null);
			dialog = builder.create();
			break;
		
		default:
			dialog = null;
		}
		
		return dialog;
	}*/
	
	public void openDeckPicker()
	{
    	Log.i(TAG, "openDeckPicker - deckSelected = " + deckSelected);
    	deck.closeDeck();
    	deckLoaded = false;
		Intent decksPicker = new Intent(this, DeckPicker.class);
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
			//deck.closeDeck();
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
//				dialog = ProgressDialog.show(this, "", "Loading deck. Please wait...", true);
//				Thread thread = new Thread(this);
//				thread.start();
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

//	public void run()
//	{
//		Log.i(TAG, "Ankidroid loader thread - run");
//		handler.sendEmptyMessage(loadDeck(deckFilename));
//	}

//	public int loadDeck(String deckFilename)
//	{
//		Log.i(TAG, "loadDeck - deckFilename = " + deckFilename);
//		this.deckFilename = deckFilename;
//		
//		Log.i(TAG, "loadDeck - SD card mounted and existent file -> Loading deck...");
//		try
//		{
//			// Open the right deck.
//			//AnkiDb.openDatabase(deckFilename);
//			deck = Deck.openDeck(deckFilename);
//			// Start by getting the first card and displaying it.
//			//nextCard(0);
//			Log.i(TAG, "Deck loaded!");
//			return DECK_LOADED;
//		} catch (SQLException e)
//		{
//			Log.i(TAG, "The database " + deckFilename + " could not be opened = " + e.getMessage());
//			return DECK_NOT_LOADED;
//		} catch (CursorIndexOutOfBoundsException e)
//		{
//			Log.i(TAG, "The deck has no cards = " + e.getMessage());;
//			return DECK_EMPTY;
//		}
//	}


//	private Handler handler = new Handler()
//	{
//		public void handleMessage(Message msg)
//		{	
//			//This verification would not be necessary if onConfigurationChanged it's executed correctly (which seems that emulator does not do)
//			if(dialog.isShowing()) 
//			{
//				try
//				{
//					dialog.dismiss();
//				} catch(Exception e)
//				{
//					Log.e(TAG, "handleMessage - Dialog dismiss Exception = " + e.getMessage());
//				}
//				
//			}
//			nextCard(0);
//			
//			switch(msg.what)
//			{
//
//				case DECK_LOADED:
//					showControls(true);
//					deckLoaded = true;
//					displayCardQuestion();
//					break;
//					
//				case DECK_NOT_LOADED:
//					displayDeckNotLoaded();
//					break;
//				
//				case DECK_EMPTY:
//					displayNoCardsInDeck();
//					break;
//			}
//		}
//	};
	
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
			if(deckLoaded)
				showOrHideControls();
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
			//mSelectRemembered.setVisibility(View.VISIBLE);
			//mSelectNotRemembered.setVisibility(View.VISIBLE);
			mEase0.setVisibility(View.VISIBLE);
			mEase1.setVisibility(View.VISIBLE);
			mEase2.setVisibility(View.VISIBLE);
			mEase3.setVisibility(View.VISIBLE);
			mFlipCard.setVisibility(View.VISIBLE);
			showOrHideControls();
			hideDeckErrors();
		} else
		{
			mCard.setVisibility(View.GONE);
			//mSelectRemembered.setVisibility(View.GONE);
			//mSelectNotRemembered.setVisibility(View.GONE);
			mEase0.setVisibility(View.GONE);
			mEase1.setVisibility(View.GONE);
			mEase2.setVisibility(View.GONE);
			mEase3.setVisibility(View.GONE);
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
//	public void nextCard(int ease)
//	{
//		Log.i(TAG, "nextCard");
//		long start, stop;
//		if (spacedRepetition)
//			currentCard = AnkiDb.Card.smallestIntervalCard();
//		else
//			currentCard = AnkiDb.Card.randomCard();

		// Store away the current card to answer after the new card has been loaded.
		// In order for it to not appear again as the new card, we temporarily set its
		// priority to 0.
//		Card oldCard = currentCard;
//		if (oldCard != null)
//		{
//			start = System.currentTimeMillis();
//			oldCard.temporarilySetLowestPriority();
//			stop = System.currentTimeMillis();
//			Log.v(TAG, "nextCard - Set old card 0 priority in " + (stop - start) + " ms.");
//		}
//		
//		start = System.currentTimeMillis();
//		currentCard = deck.getCard();
//		stop = System.currentTimeMillis();
//		
//		Log.v(TAG, "nextCard - Loaded new card in " + (stop - start) + " ms.");
//		
//		// Set the correct value for the flip card button - That triggers the
//		// listener which displays the question of the card
//		mFlipCard.setChecked(false);
//		mWhiteboard.clear();
//		mTimer.setBase(SystemClock.elapsedRealtime());
//		mTimer.start();
//		
//		if (ease != 0 && oldCard != null)
//		{
//			start = System.currentTimeMillis();
//			deck.answerCard(oldCard, ease);
//			stop = System.currentTimeMillis();
//			Log.v(TAG, "nextCard - Answered old card in " + (stop - start) + " ms.");
//		}
//		task = launchDeckTask(
//				deck,
//				DeckTask.TASK_TYPE_ANSWER_CARD,
//				new TaskData(ease));
//		
//	}

	// Set up the display for the current card.
	public void displayCardQuestion()
	{
		Log.i(TAG, "displayCardQuestion");

//		if (currentCard == null)
//		{
//			nextCard();
//		}

		if (currentCard == null)
		{
			// error :(
			updateCard("Unable to find a card!");
			mEase0.setVisibility(View.GONE);
			mEase1.setVisibility(View.GONE);
			mEase2.setVisibility(View.GONE);
			mEase3.setVisibility(View.GONE);
			mFlipCard.setVisibility(View.GONE);
			mTimer.setVisibility(View.GONE);
			mToggleWhiteboard.setVisibility(View.GONE);
			mWhiteboard.setVisibility(View.GONE);
		} else
		{
			Log.i(TAG, "displayCardQuestion - Hiding 'Remembered' and 'Not Remembered' buttons...");
			//mSelectRemembered.setVisibility(View.GONE);
			//mSelectNotRemembered.setVisibility(View.GONE);
			mEase0.setVisibility(View.GONE);
			mEase1.setVisibility(View.GONE);
			mEase2.setVisibility(View.GONE);
			mEase3.setVisibility(View.GONE);
									
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

		//mSelectRemembered.setVisibility(View.VISIBLE);
		//mSelectNotRemembered.setVisibility(View.VISIBLE);
		mEase0.setVisibility(View.VISIBLE);
		mEase1.setVisibility(View.VISIBLE);
		mEase2.setVisibility(View.VISIBLE);
		mEase3.setVisibility(View.VISIBLE);
		
		//mSelectRemembered.requestFocus();
		mEase2.requestFocus();
		
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
    	//AnkiDb.closeDatabase();
    	deck.closeDeck();
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

	/*private void checkUpdates() 
	{
		showDialog(DIALOG_UPDATE);
	}*/
	
//	private DeckTask launchDeckTask(Deck deck, Card cardToAnswer, int type, DeckTask.TaskData... params)
//	{
//		try
//		{
//		if ((task != null) && (task.getStatus() != AsyncTask.Status.FINISHED))
//			task.get();
//		} catch (Exception e)
//		{
//			e.printStackTrace();
//		}
//		
//		switch (type)
//		{
//		case DeckTask.TASK_TYPE_LOAD_DECK:
//			return (DeckTask) new DeckTask(null, null, type, mLoadDeckHandler).execute(params);
//		case DeckTask.TASK_TYPE_ANSWER_CARD:
//			return (DeckTask) new DeckTask(deck, cardToAnswer, type, mAnswerCardHandler).execute(params);
//		default:
//			return null;
//		}
//	}
	
	DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener()
	{
		
		@Override
		public void onPreExecute() {
			dialog = ProgressDialog.show(Ankidroid.this, "", "Loading new card...", true);
		}
		
		@Override
		public void onPostExecute(DeckTask.TaskData result) {
			// Pass
		}
		
		@Override
		public void onProgressUpdate(DeckTask.TaskData... values) {
			Card newCard = values[0].getCard();
			
			currentCard = newCard;
			
			// Set the correct value for the flip card button - That triggers the
			// listener which displays the question of the card
			mFlipCard.setChecked(false);
			mWhiteboard.clear();
			mTimer.setBase(SystemClock.elapsedRealtime());
			mTimer.start();
			
			dialog.dismiss();
		}
		
	};
	
	DeckTask.TaskListener mLoadDeckHandler = new DeckTask.TaskListener() 
	{
		
		@Override
		public void onPreExecute() {
			dialog = ProgressDialog.show(Ankidroid.this, "", "Loading deck. Please wait...", true);
		}
		
		@Override
		public void onPostExecute(DeckTask.TaskData result) {
			// This verification would not be necessary if onConfigurationChanged it's executed correctly (which seems that emulator does not do)
			if(dialog.isShowing()) 
			{
				try
				{
					dialog.dismiss();
				} catch(Exception e)
				{
					Log.e(TAG, "handleMessage - Dialog dismiss Exception = " + e.getMessage());
				}
			}
			
			switch(result.getInt())
			{
				case DECK_LOADED:
					deck = result.getDeck();
					currentCard = result.getCard();
					showControls(true);
					deckLoaded = true;
					displayCardQuestion();
					break;
					
				case DECK_NOT_LOADED:
					displayDeckNotLoaded();
					break;
				
				case DECK_EMPTY:
					displayNoCardsInDeck();
					break;
			}
		}
		
		@Override
		public void onProgressUpdate(DeckTask.TaskData... values) {
			// Pass
		}
		
	};
	
//	public static class DeckTask extends AsyncTask<TaskData, TaskData, TaskData>
//	{
//		public static final int TASK_TYPE_LOAD_DECK = 0;
//		public static final int TASK_TYPE_ANSWER_CARD = 1;
//		int type;
//		Deck deck;
//		Card oldCard;
//		TaskListener listener;
//		
//		public DeckTask(Deck deck, int type, TaskListener listener)
//		{
//			this.deck = deck;
//			this.type = type;
//			this.listener = listener;
//		}
//		
//		public DeckTask(Deck deck, Card cardToAnswer, int type, TaskListener listener)
//		{
//			this(deck, type, listener);
//			this.oldCard = cardToAnswer;
//		}
//
//		@Override
//		protected TaskData doInBackground(TaskData... params)
//		{
//			switch (type)
//			{
//			case TASK_TYPE_LOAD_DECK:
//				return doInBackgroundLoadDeck(params);
//			case TASK_TYPE_ANSWER_CARD:
//				return doInBackgroundAnswerCard(params);
//			default:
//				return null;
//			}
//		}
//		
//		@Override
//		protected void onPreExecute()
//		{
//			listener.onPreExecute();
////			switch (type)
////			{
////			case TASK_TYPE_LOAD_DECK:
////				onPreExecuteLoadDeck();
////			case TASK_TYPE_ANSWER_CARD:
////				onPreExecuteAnswerCard();
////				break;
////			default:
////				break;
////			}
//		}
//		
//		@Override
//		protected void onProgressUpdate(TaskData... values)
//		{
//			listener.onProgressUpdate(values);
////			switch (type)
////			{
////			case TASK_TYPE_ANSWER_CARD:
////				onProgressUpdateAnswerCard(values);
////				break;
////			default:
////				break;
////			}
//		}
//		
//		@Override
//		protected void onPostExecute(TaskData result)
//		{
//			listener.onPostExecute(result);
////			switch (type)
////			{
////			case TASK_TYPE_LOAD_DECK:
////				onPostExecuteLoadDeck(result);
////				break;
////			default:
////				break;
////			}
//		}
//		
////		private void onPreExecuteAnswerCard()
////		{
////			oldCard = Ankidroid.this.currentCard;
////			dialog = ProgressDialog.show(Ankidroid.this, "", "Loading new card...", true);
////		}
//		
//		private TaskData doInBackgroundAnswerCard(TaskData... params)
//		{
//			long start, stop;
//			Card newCard;
//			int ease = params[0].getInt();
//			
//			if (oldCard != null)
//			{
//				start = System.currentTimeMillis();
//				oldCard.temporarilySetLowestPriority();
//				stop = System.currentTimeMillis();
//				Log.v(TAG, "doInBackground - Set old card 0 priority in " + (stop - start) + " ms.");
//			}
//			
//			start = System.currentTimeMillis();
//			newCard = this.deck.getCard();
//			stop = System.currentTimeMillis();
//			Log.v(TAG, "doInBackground - Loaded new card in " + (stop - start) + " ms.");
//			publishProgress(new TaskData(newCard));
//			
//			if (ease != 0 && oldCard != null)
//			{
//				start = System.currentTimeMillis();
//				this.deck.answerCard(oldCard, ease);
//				stop = System.currentTimeMillis();
//				Log.v(TAG, "doInBackground - Answered old card in " + (stop - start) + " ms.");
//			}
//			
//			return null;
//		}
//		
////		private void onProgressUpdateAnswerCard(TaskData... values)
////		{
////			Card newCard = values[0].getCard();
////			
////			Ankidroid.this.currentCard = newCard;
////			
////			// Set the correct value for the flip card button - That triggers the
////			// listener which displays the question of the card
////			Ankidroid.this.mFlipCard.setChecked(false);
////			Ankidroid.this.mWhiteboard.clear();
////			Ankidroid.this.mTimer.setBase(SystemClock.elapsedRealtime());
////			Ankidroid.this.mTimer.start();
////			
////			dialog.dismiss();
////		}
//		
////		private void onPreExecuteLoadDeck()
////		{
////			dialog = ProgressDialog.show(Ankidroid.this, "", "Loading deck. Please wait...", true);
////		}
//		
//		private TaskData doInBackgroundLoadDeck(TaskData... params)
//		{
//			String deckFilename = params[0].getString();
//			Log.i(TAG, "doInBackgroundLoadDeck - deckFilename = " + deckFilename);
//			
//			Log.i(TAG, "loadDeck - SD card mounted and existent file -> Loading deck...");
//			try
//			{
//				// Open the right deck.
//				//AnkiDb.openDatabase(deckFilename);
//				Deck deck = Deck.openDeck(deckFilename);
//				// Start by getting the first card and displaying it.
//				//nextCard(0);
//				Card card = deck.getCard();
//				Log.i(TAG, "Deck loaded!");
//				
//				return new TaskData(Ankidroid.DECK_LOADED, deck, card);
//			} catch (SQLException e)
//			{
//				Log.i(TAG, "The database " + deckFilename + " could not be opened = " + e.getMessage());
//				return new TaskData(Ankidroid.DECK_NOT_LOADED);
//			} catch (CursorIndexOutOfBoundsException e)
//			{
//				Log.i(TAG, "The deck has no cards = " + e.getMessage());;
//				return new TaskData(Ankidroid.DECK_EMPTY);
//			}
//		}
//		
////		private void onPostExecuteLoadDeck(TaskData result)
////		{
////			// This verification would not be necessary if onConfigurationChanged it's executed correctly (which seems that emulator does not do)
////			if(dialog.isShowing()) 
////			{
////				try
////				{
////					dialog.dismiss();
////				} catch(Exception e)
////				{
////					Log.e(TAG, "handleMessage - Dialog dismiss Exception = " + e.getMessage());
////				}
////			}
////			
////			switch(result.getInt())
////			{
////				case DECK_LOADED:
////					showControls(true);
////					deckLoaded = true;
////					displayCardQuestion();
////					break;
////					
////				case DECK_NOT_LOADED:
////					displayDeckNotLoaded();
////					break;
////				
////				case DECK_EMPTY:
////					displayNoCardsInDeck();
////					break;
////			}
////		}
//		
//		public static interface TaskListener
//		{
//			public void onPreExecute();
//			
//			public void onPostExecute(TaskData result);
//			
//			public void onProgressUpdate(TaskData... values);
//		}
//		
//		public static class TaskData
//		{
//			private Deck deck;
//			private Card card;
//			private int integer;
//			private String msg;
//			
//			public TaskData(int value, Deck deck, Card card)
//			{
//				this(value);
//				this.deck = deck;
//				this.card = card;
//			}
//			
//			public TaskData(Card card)
//			{
//				this.card = card;
//			}
//			
//			public TaskData(int value)
//			{
//				this.integer = value;
//			}
//			
//			public TaskData(String msg)
//			{
//				this.msg = msg;
//			}
//			
////			public void putCard(Card card)
////			{
////				this.card = card;
////			}
////			
////			public void putInt(int value)
////			{
////				this.integer = value;
////			}
//			
//			public Deck getDeck()
//			{
//				return deck;
//			}
//			
//			public Card getCard()
//			{
//				return card;
//			}
//			
//			public int getInt()
//			{
//				return integer;
//			}
//			
//			public String getString()
//			{
//				return msg;
//			}
//			
//		}
//		
//	}
	
}