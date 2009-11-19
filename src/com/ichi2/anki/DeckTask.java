package com.ichi2.anki;

import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.os.AsyncTask;
import android.util.Log;

public class DeckTask extends AsyncTask<DeckTask.TaskData, DeckTask.TaskData, DeckTask.TaskData>
{
	
	/**
	 * Tag for logging messages
	 */
	private static final String TAG = "Ankidroid";
	
	public static final int TASK_TYPE_LOAD_DECK = 0;
	public static final int TASK_TYPE_ANSWER_CARD = 1;
	
	private static DeckTask instance;
	
	int type;
//	Deck deck;
//	Card oldCard;
	TaskListener listener;
	
//	public DeckTask(Deck deck, int type, TaskListener listener)
//	{
//		this.deck = deck;
//		this.type = type;
//		this.listener = listener;
//	}
//	
//	public DeckTask(Deck deck, Card cardToAnswer, int type, TaskListener listener)
//	{
//		this(deck, type, listener);
//		this.oldCard = cardToAnswer;
//	}
	
	public static DeckTask launchDeckTask(int type, TaskListener listener, TaskData... params)
	{
		try
		{
			if ((instance != null) && (instance.getStatus() != AsyncTask.Status.FINISHED))
				instance.get();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		
		instance = new DeckTask();
		instance.listener = listener;
		instance.type = type;
		
		return (DeckTask) instance.execute(params);
	}

	@Override
	protected TaskData doInBackground(TaskData... params)
	{
		switch (type)
		{
		case TASK_TYPE_LOAD_DECK:
			return doInBackgroundLoadDeck(params);
		case TASK_TYPE_ANSWER_CARD:
			return doInBackgroundAnswerCard(params);
		default:
			return null;
		}
	}
	
	@Override
	protected void onPreExecute()
	{
		listener.onPreExecute();
//		switch (type)
//		{
//		case TASK_TYPE_LOAD_DECK:
//			onPreExecuteLoadDeck();
//		case TASK_TYPE_ANSWER_CARD:
//			onPreExecuteAnswerCard();
//			break;
//		default:
//			break;
//		}
	}
	
	@Override
	protected void onProgressUpdate(TaskData... values)
	{
		listener.onProgressUpdate(values);
//		switch (type)
//		{
//		case TASK_TYPE_ANSWER_CARD:
//			onProgressUpdateAnswerCard(values);
//			break;
//		default:
//			break;
//		}
	}
	
	@Override
	protected void onPostExecute(TaskData result)
	{
		listener.onPostExecute(result);
//		switch (type)
//		{
//		case TASK_TYPE_LOAD_DECK:
//			onPostExecuteLoadDeck(result);
//			break;
//		default:
//			break;
//		}
	}
	
//	private void onPreExecuteAnswerCard()
//	{
//		oldCard = Ankidroid.this.currentCard;
//		dialog = ProgressDialog.show(Ankidroid.this, "", "Loading new card...", true);
//	}
	
	private TaskData doInBackgroundAnswerCard(TaskData... params)
	{
		long start, stop;
		Deck deck = params[0].getDeck();
		Card oldCard = params[0].getCard();
		int ease = params[0].getInt();
		Card newCard;
		
		if (oldCard != null)
		{
			start = System.currentTimeMillis();
			oldCard.temporarilySetLowestPriority();
			stop = System.currentTimeMillis();
			Log.v(TAG, "doInBackground - Set old card 0 priority in " + (stop - start) + " ms.");
		}
		
		start = System.currentTimeMillis();
		newCard = deck.getCard();
		stop = System.currentTimeMillis();
		Log.v(TAG, "doInBackground - Loaded new card in " + (stop - start) + " ms.");
		publishProgress(new TaskData(newCard));
		
		if (ease != 0 && oldCard != null)
		{
			start = System.currentTimeMillis();
			deck.answerCard(oldCard, ease);
			stop = System.currentTimeMillis();
			Log.v(TAG, "doInBackground - Answered old card in " + (stop - start) + " ms.");
		}
		
		return null;
	}
	
//	private void onProgressUpdateAnswerCard(TaskData... values)
//	{
//		Card newCard = values[0].getCard();
//		
//		Ankidroid.this.currentCard = newCard;
//		
//		// Set the correct value for the flip card button - That triggers the
//		// listener which displays the question of the card
//		Ankidroid.this.mFlipCard.setChecked(false);
//		Ankidroid.this.mWhiteboard.clear();
//		Ankidroid.this.mTimer.setBase(SystemClock.elapsedRealtime());
//		Ankidroid.this.mTimer.start();
//		
//		dialog.dismiss();
//	}
	
//	private void onPreExecuteLoadDeck()
//	{
//		dialog = ProgressDialog.show(Ankidroid.this, "", "Loading deck. Please wait...", true);
//	}
	
	private TaskData doInBackgroundLoadDeck(TaskData... params)
	{
		String deckFilename = params[0].getString();
		Log.i(TAG, "doInBackgroundLoadDeck - deckFilename = " + deckFilename);
		
		Log.i(TAG, "loadDeck - SD card mounted and existent file -> Loading deck...");
		try
		{
			// Open the right deck.
			//AnkiDb.openDatabase(deckFilename);
			Deck deck = Deck.openDeck(deckFilename);
			// Start by getting the first card and displaying it.
			//nextCard(0);
			Card card = deck.getCard();
			Log.i(TAG, "Deck loaded!");
			
			return new TaskData(Ankidroid.DECK_LOADED, deck, card);
		} catch (SQLException e)
		{
			Log.i(TAG, "The database " + deckFilename + " could not be opened = " + e.getMessage());
			return new TaskData(Ankidroid.DECK_NOT_LOADED);
		} catch (CursorIndexOutOfBoundsException e)
		{
			Log.i(TAG, "The deck has no cards = " + e.getMessage());;
			return new TaskData(Ankidroid.DECK_EMPTY);
		}
	}
	
//	private void onPostExecuteLoadDeck(TaskData result)
//	{
//		// This verification would not be necessary if onConfigurationChanged it's executed correctly (which seems that emulator does not do)
//		if(dialog.isShowing()) 
//		{
//			try
//			{
//				dialog.dismiss();
//			} catch(Exception e)
//			{
//				Log.e(TAG, "handleMessage - Dialog dismiss Exception = " + e.getMessage());
//			}
//		}
//		
//		switch(result.getInt())
//		{
//			case DECK_LOADED:
//				showControls(true);
//				deckLoaded = true;
//				displayCardQuestion();
//				break;
//				
//			case DECK_NOT_LOADED:
//				displayDeckNotLoaded();
//				break;
//			
//			case DECK_EMPTY:
//				displayNoCardsInDeck();
//				break;
//		}
//	}
	
	public static interface TaskListener
	{
		public void onPreExecute();
		
		public void onPostExecute(TaskData result);
		
		public void onProgressUpdate(TaskData... values);
	}
	
	public static class TaskData
	{
		private Deck deck;
		private Card card;
		private int integer;
		private String msg;
		
		public TaskData(int value, Deck deck, Card card)
		{
			this(value);
			this.deck = deck;
			this.card = card;
		}
		
		public TaskData(Card card)
		{
			this.card = card;
		}
		
		public TaskData(int value)
		{
			this.integer = value;
		}
		
		public TaskData(String msg)
		{
			this.msg = msg;
		}
		
//		public void putCard(Card card)
//		{
//			this.card = card;
//		}
//		
//		public void putInt(int value)
//		{
//			this.integer = value;
//		}
		
		public Deck getDeck()
		{
			return deck;
		}
		
		public Card getCard()
		{
			return card;
		}
		
		public int getInt()
		{
			return integer;
		}
		
		public String getString()
		{
			return msg;
		}
		
	}
	
}
