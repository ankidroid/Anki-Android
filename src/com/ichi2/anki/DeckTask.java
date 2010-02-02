/****************************************************************************************
* Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
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

import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Loading in the background, so that AnkiDroid does not look like frozen.
 */
public class DeckTask extends AsyncTask<DeckTask.TaskData, DeckTask.TaskData, DeckTask.TaskData>
{

	/**
	 * Tag for logging messages
	 */
	private static final String TAG = "AnkiDroid";

	public static final int TASK_TYPE_LOAD_DECK = 0;
	public static final int TASK_TYPE_ANSWER_CARD = 1;

	private static DeckTask instance;

	int type;
	TaskListener listener;

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
	}

	@Override
	protected void onProgressUpdate(TaskData... values)
	{
		listener.onProgressUpdate(values);
	}

	@Override
	protected void onPostExecute(TaskData result)
	{
		listener.onPostExecute(result);
	}

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
			deck.decreaseCounts(oldCard);
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

	private TaskData doInBackgroundLoadDeck(TaskData... params)
	{
		String deckFilename = params[0].getString();
		Log.i(TAG, "doInBackgroundLoadDeck - deckFilename = " + deckFilename);

		Log.i(TAG, "loadDeck - SD card mounted and existent file -> Loading deck...");
		try
		{
			// Open the right deck.
			Deck deck = Deck.openDeck(deckFilename);
			// Start by getting the first card and displaying it.
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
