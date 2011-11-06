/***************************************************************************************
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

import com.ichi2.anki.DeckTask.TaskData;
import com.ichi2.anki.services.NotificationService;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;

/**
 * The status of the widget.
 * <p>
 * It contains the status of each of the decks.
 */
public final class WidgetStatus {

	private static boolean mediumWidget = false;
	private static boolean smallWidget = false;
	private static boolean notification = false;
	private static boolean onlyCurrentDeck = false;
	private static AsyncTask<Context,Void,Context> sUpdateDeckStatusAsyncTask;
	private static AsyncTask<WidgetDeckTaskData,Void,WidgetDeckTaskData> sDeckOperationTask;
	private static int sDeckOperationType;
	public static final int TASK_OPEN_DECK = 0;
	public static final int TASK_ANSWER_CARD = 1;
	public static final int TASK_CLOSE_DECK = 2;
	public static final int TASK_UNDO = 3;
	public static final int TASK_BURY_CARD = 4;

    /** This class should not be instantiated. */
    private WidgetStatus() {}

    /** Request the widget to update its status. */
    public static void update(Context context) {
    	update(context, false);
    }
    /** Request the widget to update its status. */
    public static void update(Context context, boolean onlyCurrent) {
    	onlyCurrentDeck = onlyCurrent;
        SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
        if (preferences.getBoolean("widgetMediumEnabled", false)) {
            mediumWidget = true;
        } else {
            mediumWidget = false;
        }
        if (preferences.getBoolean("widgetSmallEnabled", false)) {
            smallWidget = true;
        } else {
            smallWidget = false;
        }
        if (Integer.parseInt(preferences.getString("minimumCardsDueForNotification", "25")) < 1000000 && !onlyCurrentDeck) {
        	notification = true;
        } else {
        	notification = false;
        }
        if (mediumWidget || smallWidget || notification) {
            Log.d(AnkiDroidApp.TAG, "WidgetStatus.update(): updating");
            sUpdateDeckStatusAsyncTask = new UpdateDeckStatusAsyncTask();
            sUpdateDeckStatusAsyncTask.execute(context);
        } else {
            Log.d(AnkiDroidApp.TAG, "WidgetStatus.update(): not enabled");
        }
    }


    /**
     * Block the current thread until the currently running UpdateDeckStatusAsyncTask instance (if any) has finished.
     */
    public static void waitToFinish() {
        try {
            if ((sUpdateDeckStatusAsyncTask != null) && (sUpdateDeckStatusAsyncTask.getStatus() != AsyncTask.Status.FINISHED)) {
            	sUpdateDeckStatusAsyncTask.get();
            }
        } catch (Exception e) {
            return;
        }
    }


    /** Returns the status of each of the decks. */
    public static DeckStatus[] fetch(Context context) {
        return MetaDB.getWidgetStatus(context);
    }

    /** Returns the status of each of the decks. */
    public static int[] fetchSmall(Context context) {
        return MetaDB.getWidgetSmallStatus(context);
    }

    public static int fetchDue(Context context) {
        return MetaDB.getNotificationStatus(context);
    }


    public static void deckOperation(int type, WidgetDeckTaskData params) {
        try {
            if ((sDeckOperationTask != null) && (sDeckOperationTask.getStatus() != AsyncTask.Status.FINISHED)) {
            	sDeckOperationTask.get();
            }
        } catch (Exception e) {
            Log.e(AnkiDroidApp.TAG,
                    "deckOperation - Got exception while waiting for thread to finish: " + e.getMessage());
        }

    	sDeckOperationType = type;
    	sDeckOperationTask = new DeckOperationAsyncTask();
    	sDeckOperationTask.execute(params);
    }


    private static class UpdateDeckStatusAsyncTask extends AsyncTask<Context, Void, Context> {
        private static final DeckStatus[] EMPTY_DECK_STATUS = new DeckStatus[0];

        private static DeckStatus[] mDecks = EMPTY_DECK_STATUS;

        @Override
        protected Context doInBackground(Context... params) {
            Log.d(AnkiDroidApp.TAG, "WidgetStatus.UpdateDeckStatusAsyncTask.doInBackground()");
            Context context = params[0];

            if (!AnkiDroidApp.isSdCardMounted()) {
            	return context;
            }

            // For the deck information
            ArrayList<DeckStatus> decks;

            if (onlyCurrentDeck) {
                decks = new ArrayList<DeckStatus>(mDecks.length);

                Deck currentDeck = AnkiDroidApp.deck();
                if (currentDeck != null) {
                	String currentDeckPath = currentDeck.getDeckPath();
                	try {
                		for (DeckStatus m : mDecks) {
                			if (m.mDeckPath.equals(currentDeckPath)) {
	                    			Log.i(AnkiDroidApp.TAG, "UpdateWidget - update information for deck " + currentDeckPath);
						if (!currentDeck.hasFinishScheduler()) {
		                			decks.add(new DeckStatus(currentDeckPath, currentDeck.getDeckName(), currentDeck.getNewCountToday(), currentDeck.getRevCount(), currentDeck.getFailedSoonCount(), currentDeck.getETA(), currentDeck.getSessionFinishedCards()));
						} else {
		                			decks.add(new DeckStatus(currentDeckPath, currentDeck.getDeckName(), 0, 0, currentDeck.getFailedSoonCount(), 0, currentDeck.getSessionFinishedCards()));
						}
                			} else {
                    			Log.i(AnkiDroidApp.TAG, "UpdateWidget - copy information for deck " + m.mDeckPath);
                				decks.add(m);
                			}
                		}
                    } catch (SQLException e) {
                        Log.i(AnkiDroidApp.TAG, "Widget: Could not retrieve deck information");
                        Log.e(AnkiDroidApp.TAG, e.toString());
                        if (currentDeckPath != null) {
                            BackupManager.restoreDeckIfMissing(currentDeckPath);                    	
                        }
                    }
                }
            } else {
                SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
                String deckPath = preferences.getString("deckPath",
                        AnkiDroidApp.getStorageDirectory() + "/AnkiDroid");

                File dir = new File(deckPath);

                File[] fileList = dir.listFiles(new AnkiFileFilter());

                if (fileList == null || fileList.length == 0) {
                    mDecks = EMPTY_DECK_STATUS;
                    return context;
                }

                decks = new ArrayList<DeckStatus>(fileList.length);

                for (File file : fileList) {
                	String absPath = null;
                    try {
                        // Run through the decks and get the information
                        absPath = file.getAbsolutePath();
                        String deckName = file.getName().replaceAll(".anki", "");

                        Log.i(AnkiDroidApp.TAG, "Found deck: " + absPath);

                        Deck deck;
                        Deck currentDeck = AnkiDroidApp.deck();
                        if (currentDeck != null && currentDeck.getDeckPath().equals(absPath)) {
                        	deck = currentDeck;
                        } else {
                        	try {
                            	deck = Deck.openDeck(absPath, false);                    		
                			} catch (RuntimeException e) {
                				Log.w(AnkiDroidApp.TAG, "Widget: Could not open database " + absPath + ": " + e);
                				BackupManager.restoreDeckIfMissing(absPath);
                				deck = null;
                			}
                        }
                        if (deck == null) {
                            Log.e(AnkiDroidApp.TAG, "Widget: Skipping null deck: " + absPath);
                            // Use the data from the last time we updated the deck, if available.
//                            for (DeckStatus deckStatus : mDecks) {
//                                if (absPath.equals(deckStatus.mDeckPath)) {
//                                    Log.d(AnkiDroidApp.TAG, "Using previous value");
//                                    decks.add(deckStatus);
//                                    break;
//                                }
//                            }
                            continue;
                        }
                        int dueCards = 0;
                        int newCards = 0;
                        int failedCards = deck.getFailedSoonCount();
                        int eta = 0;
                        int reps = deck.getSessionFinishedCards();


			if(!deck.hasFinishScheduler()) {
        	                dueCards = deck.getRevCount();
        	                newCards = deck.getNewCountToday();
        	                eta = deck.getETA();
			}
                        // Close the database connection, but only if this is not the current database.
                        // Probably we need to make this atomic to be sure it will not cause a failure.
                        if (currentDeck != null && currentDeck.getDB() != deck.getDB()) {
                            deck.closeDeck();
                        }

                        // Add the information about the deck
                        decks.add(new DeckStatus(absPath, deckName, newCards, dueCards, failedCards, eta, reps));
                    } catch (SQLException e) {
                        Log.i(AnkiDroidApp.TAG, "Widget: Could not open deck");
                        Log.e(AnkiDroidApp.TAG, e.toString());
                        if (absPath != null) {
                            BackupManager.restoreDeckIfMissing(absPath);                    	
                        }
                    }
                }
            }

            if (!decks.isEmpty() && decks.size() > 1) {
                // Sort and reverse the list if there are decks
                Log.i(AnkiDroidApp.TAG, "Sorting deck");

                // Ordered by reverse due cards number
                Collections.sort(decks, new ByDueComparator());
            }

            mDecks = decks.toArray(EMPTY_DECK_STATUS);
            return context;
        }

        @Override
        protected void onPostExecute(Context context) {
            Log.d(AnkiDroidApp.TAG, "WidgetStatus.UpdateDeckStatusAsyncTask.onPostExecute()");
            MetaDB.storeWidgetStatus(context, mDecks);
            if (mediumWidget) {
            	Intent intent;
                intent = new Intent(context, AnkiDroidWidgetMedium.UpdateService.class);
                intent.setAction(AnkiDroidWidgetMedium.UpdateService.ACTION_UPDATE);
                context.startService(intent);
            }
            if (smallWidget) {
            	Intent intent;
                intent = new Intent(context, AnkiDroidWidgetSmall.UpdateService.class);            	
                context.startService(intent);
            }
            if (notification) {
            	Intent intent;
                intent = new Intent(context, NotificationService.class);
                context.startService(intent);
            }
        	Intent intent;
            intent = new Intent(context, AnkiDroidWidgetBig.UpdateService.class);
            intent.setAction(AnkiDroidWidgetBig.UpdateService.ACTION_UPDATE);
            context.startService(intent);
        }

        /** Comparator that sorts instances of {@link DeckStatus} based on number of due cards. */
        private static class ByDueComparator implements java.util.Comparator<DeckStatus> {
            @Override
            public int compare(DeckStatus deck1, DeckStatus deck2) {
                // Reverse due cards number order
                return deck2.mDueCards - deck1.mDueCards;
            }
        }

        /** Filter for Anki files. */
        private static final class AnkiFileFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile() && pathname.getName().endsWith(".anki");
            }
        }
    }

    private static class DeckOperationAsyncTask extends AsyncTask<WidgetDeckTaskData, Void, WidgetDeckTaskData> {

        @Override
        protected WidgetDeckTaskData doInBackground(WidgetDeckTaskData... params) {
            Log.d(AnkiDroidApp.TAG, "WidgetStatus.DeckOperationAsyncTask.doInBackground()");

            switch (sDeckOperationType) {
            case TASK_OPEN_DECK:
            	return doInBackgroundOpenDeck(params);
            case TASK_ANSWER_CARD:
            	return doInBackgroundAnswerCard(params);
            case TASK_CLOSE_DECK:
            	return doInBackgroundCloseDeck(params);
            case TASK_UNDO:
            	return doInBackgroundUndo(params);
            case TASK_BURY_CARD:
            	return doInBackgroundBury(params);
            }
            return params[0];
        }

        protected WidgetDeckTaskData doInBackgroundOpenDeck(WidgetDeckTaskData... params) {
        	Log.e(AnkiDroidApp.TAG, "doInBackgroundOpenDeck");
        	Deck deck = Deck.openDeck(params[0].getString(), true);
        	Card card = deck.getCard();
        	return new WidgetDeckTaskData(params[0].getContext(), deck, card);
        }

        protected WidgetDeckTaskData doInBackgroundAnswerCard(WidgetDeckTaskData... params) {
        	Log.e(AnkiDroidApp.TAG, "doInBackgroundAnswerCard");
        	Context context = params[0].getContext();
        	Card card = params[0].getCard();
        	Deck deck = params[0].getDeck();
        	Card newCard;
        	int ease = params[0].getInt();
        	if (ease == 0) {
        		return new WidgetDeckTaskData(context, deck, card);
        	}
//            try {
    	        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
    	        ankiDB.getDatabase().beginTransaction();
    	        try {
    	            if (card != null) {
    	            	if (card.thinkingTime() >= 20) {
    	            		// user restarted learning
    	            		card.startTimer();
    	            	}
    	                deck.answerCard(card, ease);
    	                Log.e(AnkiDroidApp.TAG, "WidgetBig: answering card with ease " + ease);
    	            }
    	            newCard = deck.getCard();

    	            AnkiDroidWidgetBig.setCard(newCard);
    	            sendUpdateIntent(context);

    	            ankiDB.getDatabase().setTransactionSuccessful();
    	        } finally {
    	            ankiDB.getDatabase().endTransaction();
    	        }
    	    
        	return new WidgetDeckTaskData(params[0].getContext(), deck, newCard);
        }

        protected WidgetDeckTaskData doInBackgroundCloseDeck(WidgetDeckTaskData... params) {
        	Log.e(AnkiDroidApp.TAG, "doInBackgroundCloseDeck");
        	params[0].getDeck().closeDeck(false);
        	return new WidgetDeckTaskData(params[0].getContext());
        }
        

        protected WidgetDeckTaskData doInBackgroundUndo(WidgetDeckTaskData... params) {
        	Log.e(AnkiDroidApp.TAG, "doInBackgroundUndo");
        	Deck deck = params[0].getDeck();
        	deck.undo(0, true);
        	AnkiDroidWidgetBig.setCard(deck.getCard());//deck.cardFromId(deck.undo(0, true)));
        	sendUpdateIntent(params[0].getContext());

        	return new WidgetDeckTaskData(params[0].getContext());
        }


        protected WidgetDeckTaskData doInBackgroundBury(WidgetDeckTaskData... params) {
        	Log.e(AnkiDroidApp.TAG, "doInBackgroundBury");
        	Card card = params[0].getCard();
        	long id = card.getId();
        	Deck deck = params[0].getDeck();
        	deck.buryFact(card.getFactId(), id);
        	deck.reset();

        	AnkiDroidWidgetBig.setCard(deck.getCard());
        	sendUpdateIntent(params[0].getContext());

        	return new WidgetDeckTaskData(params[0].getContext());
        }
        

        @Override
        protected void onPostExecute(WidgetDeckTaskData params) {
            Log.d(AnkiDroidApp.TAG, "WidgetStatus.DeckOperationAsyncTask.onPostExecute()");
            Context context = params.getContext();
            boolean update = false;
            switch(sDeckOperationType) {
            case TASK_OPEN_DECK:
                AnkiDroidWidgetBig.setDeckAndLoadCard(params.getDeck());            	
            	update = true;
            	break;
            case TASK_CLOSE_DECK:
                AnkiDroidWidgetBig.setDeckAndLoadCard(params.getDeck());            	
            	update = true;
            	break;
            case TASK_ANSWER_CARD:
            	break;
            case TASK_UNDO:
            	break;
            case TASK_BURY_CARD:
            	break;            	
            }
            if (update) {
            	sendUpdateIntent(context);
            }
        }
    }

    public static void sendUpdateIntent(Context context) {
        Intent intent;
        intent = new Intent(context, AnkiDroidWidgetBig.UpdateService.class);
        intent.setAction(AnkiDroidWidgetBig.UpdateService.ACTION_UPDATE);
        context.startService(intent);
    }


    public static class WidgetDeckTaskData {
    	private Context context;
    	private String string;
    	private Deck deck;
    	private Card card;
    	private int integer;

    	public WidgetDeckTaskData(Context context) {
    		this.context = context;
        }

    	public WidgetDeckTaskData(Context context, String string) {
    		this.context = context;
    		this.string = string;
        }

    	public WidgetDeckTaskData(Context context, Deck deck, String string) {
    		this.deck = deck;
    		this.context = context;
    		this.string = string;
        }

    	public WidgetDeckTaskData(Context context, Card card) {
    		this.context = context;
    		this.card = card;
        }

    	public WidgetDeckTaskData(Context context, Deck deck) {
    		this.context = context;
    		this.deck = deck;
        }

    	public WidgetDeckTaskData(Context context, Deck deck, Card card) {
    		this.context = context;
    		this.deck = deck;
    		this.card = card;
        }

    	public WidgetDeckTaskData(Context context, Deck deck, Card card, int integer) {
    		this.context = context;
    		this.deck = deck;
    		this.card = card;
    		this.integer = integer;
        }

    	public String getString() {
    		this.context = context;
    		return string;
    	}

    	public Card getCard() {
    		return card;
    	}

    	public Deck getDeck() {
    		return deck;
    	}

    	public Context getContext() {
    		return context;
    	}

    	public int getInt() {
    		return integer;
    	}
    }
}
