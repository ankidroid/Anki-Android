package com.ichi2.anki;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.ichi2.anki.DeckPicker.AnkiFilter;
import com.ichi2.themes.StyledDialog;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

public class DeckManager {

	public static final int REQUESTING_ACTIVITY_STUDYOPTIONS = 0;
	public static final int REQUESTING_ACTIVITY_DECKPICKER = 1;
	public static final int REQUESTING_ACTIVITY_WIDGETSTATUS = 2;
	public static final int REQUESTING_ACTIVITY_BIGWIDGET = 3;
	public static final int REQUESTING_ACTIVITY_STATISTICS = 4;
	public static final int REQUESTING_ACTIVITY_SYNCCLIENT = 5;
	public static final int REQUESTING_ACTIVITY_CARDEDITOR = 6;
	public static final int REQUESTING_ACTIVITY_DOWNLOADMANAGER = 7;

	private static HashMap<String,DeckInformation> sLoadedDecks = new HashMap<String,DeckInformation>();

	private static HashMap<String, String> sDeckPaths;
	private static String[] sDeckNames;

	private static String sMainDeckPath;

	private static ReentrantLock mLock = new ReentrantLock(true);
	private static Condition mCondFinished = mLock.newCondition();
	private static String mOpeningDeck;
	private static String mClosingDeck;

    /**
     * Gets deck from its path. Opens it if needed.
     *
     * @param deckPath
     * @param requestingActivity Code of the requesting Activity to be saved with the deck
     * @return the loaded deck
     */
	public static Deck getDeck(String deckpath, int requestingActivity) {
		return getDeck(deckpath, false, requestingActivity);
	}
    /**
     * Gets deck from its path. Opens it if needed.
     *
     * @param deckPath
     * @param setAsMainDeck set deck as main deck to be used by other activities
     * @param requestingActivity Code of the requesting Activity to be saved with the deck
     * @return the loaded deck
     */
	public static Deck getDeck(String deckpath, boolean setAsMainDeck, int requestingActivity) {
		return getDeck(deckpath, setAsMainDeck, true, requestingActivity, true);
	}
    /**
     * Gets deck from its path. Opens it if needed.
     *
     * @param deckPath
     * @param requestingActivity Code of the requesting Activity to be saved with the deck
     * @param rebuild full deck load (rebuilds and resets a lot)
     * @return the loaded deck
     */
	public static Deck getDeck(String deckpath, int requestingActivity, boolean rebuild) {
		return getDeck(deckpath, false, requestingActivity);
	}
	public static Deck getDeck(String deckpath, boolean setAsMainDeck, boolean doSafetyBackupIfNeeded, int requestingActivity, boolean rebuild) {
		Deck deck = null;
		mLock.lock();
		mOpeningDeck = deckpath;
		try {
			while (mClosingDeck != null && mClosingDeck.equals(deckpath)) {
				Log.e(AnkiDroidApp.TAG, "DeckManager: waitForDeckClosing (" + deckpath + ")");
					mCondFinished.await();
			}
			if (sLoadedDecks.containsKey(deckpath)) {
		        DeckInformation deckInformation = sLoadedDecks.get(deckpath);
	        	try {
	                if ((deckInformation.mClosingAsyncTask != null) && (deckInformation.mClosingAsyncTask.getStatus() != AsyncTask.Status.FINISHED)) {
	                	// wait for closing deck async task before reopening it
	                	Log.e(AnkiDroidApp.TAG, "DeckManager: deck " + deckpath + " is closing now, waiting for this to finish and reopening it");
	                	deckInformation.mClosingAsyncTask.get();
	                	return getDeck(deckpath, requestingActivity);
	                }
	            } catch (Exception e) {
	            	Log.i(AnkiDroidApp.TAG, "DeckManager: An exception occurred while waiting for closing task of deck " + deckpath);
	            }

				// do not open deck if already loaded
		        ArrayList<Integer> openList = deckInformation.mOpenedBy;
		        if (!openList.contains(requestingActivity)) {
			        Log.i(AnkiDroidApp.TAG, "DeckManager: deck " + deckpath + " already loaded, adding requesting activity");
		        	openList.add(requestingActivity);
		        } else {
			        Log.e(AnkiDroidApp.TAG, "DeckManager: deck " + deckpath + " already loaded by this activity!");
		        }
		        Log.i(AnkiDroidApp.TAG, "DeckManager: deck " + deckpath + " is now opened by " + openList.toString());
	        	deck = deckInformation.mDeck;                	

        		// check for correct journal mode prior to syncing
                if (requestingActivity == REQUESTING_ACTIVITY_SYNCCLIENT) {
                	// close other learning activities
    				sendWidgetBigClosedNotification();
    				deckInformation.mOpenedBy.remove(REQUESTING_ACTIVITY_BIGWIDGET);
    				if (!deckInformation.mDeleteJournalModeForced) {
            			Cursor cur = null;
                        try {
                        	cur = deck.getDB().getDatabase().rawQuery("PRAGMA journal_mode", null);
                			if (cur.moveToFirst()) {
                				if (!cur.getString(0).equalsIgnoreCase("delete")) {
                					Log.i(AnkiDroidApp.TAG, "DeckManager: Journal mode not set to delete, reloading deck");
                					deck.closeDeck();
                					deck = Deck.openDeck(deckpath, rebuild, true);
                				}
            					deckInformation.mDeleteJournalModeForced = true;
                			}
            			} finally {
            				if (cur != null && !cur.isClosed()) {
            					cur.close();
                            }
                        }    					
    				} else if (deckInformation.mOpenedBy.contains(REQUESTING_ACTIVITY_SYNCCLIENT)) {
                    	// do not allow deck opening by other activities during syncing
                    	deck = null;
                    }
        		}
			} else {
		        try {
		            Log.i(AnkiDroidApp.TAG, "DeckManager: try to load deck " + deckpath + " (" + requestingActivity + ")");
		            if (doSafetyBackupIfNeeded) {
		            	BackupManager.safetyBackupNeeded(deckpath, BackupManager.SAFETY_BACKUP_THRESHOLD);
		            }
		            deck = Deck.openDeck(deckpath, rebuild, requestingActivity == REQUESTING_ACTIVITY_SYNCCLIENT);
		            Log.i(AnkiDroidApp.TAG, "DeckManager: Deck loaded!");
		            sLoadedDecks.put(deckpath, new DeckInformation(deckpath, deck, requestingActivity, rebuild));
				} catch (RuntimeException e) {
		            Log.e(AnkiDroidApp.TAG, "DeckManager: deck " + deckpath + " could not be opened = " + e.getMessage());
					BackupManager.restoreDeckIfMissing(deckpath);
					deck = null;
		        }
			}
			if (setAsMainDeck && deck != null) {
				sMainDeckPath = deckpath;
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			mOpeningDeck = null;
			mCondFinished.signal();
			mLock.unlock();
		}
		return deck;
	}


    /** get main deck path */
	public static String getMainDeckPath() {
		return sMainDeckPath;
	}


    /** get main deck, does not reloads the deck if it's not loaded anymore */
	public static Deck getMainDeck() {
		if (sMainDeckPath == null || !sLoadedDecks.containsKey(sMainDeckPath)) {
			return null;
		} else {
			return sLoadedDecks.get(sMainDeckPath).mDeck;
		}
	}


    /** get main deck, reloads the deck if it's not loaded anymore */
	public static Deck getMainDeck(int requestingActivity) {
		if (sMainDeckPath == null) {
			return null;
		} else {
			return getDeck(sMainDeckPath, requestingActivity);
		}
	}


	public static void waitForDeckClosingThread(String deckpath) {
		DeckInformation deckInformation = sLoadedDecks.get(deckpath);
    	try {
            if ((deckInformation.mClosingAsyncTask != null) && (deckInformation.mClosingAsyncTask.getStatus() != AsyncTask.Status.FINISHED)) {
            	// wait for closing deck async task before resuming
            	Log.e(AnkiDroidApp.TAG, "DeckManager: deck " + deckpath + " is closing now, waiting for this to finish and reopening it");
            	deckInformation.mClosingAsyncTask.get();
            }
        } catch (Exception e) {
        	Log.i(AnkiDroidApp.TAG, "DeckManager: An exception occurred while waiting for closing task of deck " + deckpath);
        }
	}


    /** closes main deck, regardless of openings by other activities */
	public static void closeMainDeck() {
		closeMainDeck(true);
	}


	/** closes main deck, regardless of openings by other activities */
	public static void closeMainDeck(boolean waitToFinish) {
		closeMainDeck(-1, waitToFinish);
	}


    /** closes main deck */
	public static void closeMainDeck(int requestingActivity) {
		closeMainDeck(requestingActivity, true);
	}
	public static void closeMainDeck(int requestingActivity, boolean waitToFinish) {
		if (sMainDeckPath != null && sLoadedDecks.containsKey(sMainDeckPath)) {
			closeDeck(sMainDeckPath, requestingActivity, waitToFinish);
		}
		sMainDeckPath = null;
	}


    /** set main deck*/
	public static void setMainDeck(String deckPath) {
		sMainDeckPath = deckPath;
	}


    /**
     * Closes all deck unconditionally regardless of a possible usage by other activities
     *
     * @param deckPath
     */
	public static void closeAllDecks() {
		for (String deckpath : sLoadedDecks.keySet()) {
			closeDeck(deckpath);
		}
	}


    /**
     * Closes the deck unconditionally regardless of a possible usage by other activities
     *
     * @param deckPath
     */
	public static void closeDeck(String deckpath) {
		closeDeck(deckpath, -1, true);
	}
	public static void closeDeck(String deckpath, boolean waitToFinish) {
		closeDeck(deckpath, -1, waitToFinish);
	}


    /**
     * Closes the deck if it is not used by any other activity
     *
     * @param deckPath
     * @param requestingActivity Code of the requesting Activity to be saved with the deck
     */
	public static void closeDeck(String deckpath, int requestingActivity) {
		closeDeck(deckpath, requestingActivity, true);
	}
	public static void closeDeck(String deckpath, int requestingActivity, boolean waitToFinish) {
		mLock.lock();
		mClosingDeck = deckpath;
		try {
			if (sLoadedDecks.containsKey(deckpath)) {
				// wait for potential deck openings to finish
				while (mOpeningDeck != null && mOpeningDeck.equals(deckpath)) {
					Log.e(AnkiDroidApp.TAG, "DeckManager: waitForDeckOpening (" + deckpath + ")");
					mCondFinished.await();
				}
				DeckInformation di = sLoadedDecks.get(deckpath);
				if ((di.mClosingAsyncTask != null) && (di.mClosingAsyncTask.getStatus() != AsyncTask.Status.FINISHED)) {
					Log.i(AnkiDroidApp.TAG, "DeckManager: closeDeck - deck " + deckpath + " is already closing");
	            	return;
	            }
				ArrayList<Integer> openList = sLoadedDecks.get(deckpath).mOpenedBy;
				if (requestingActivity != -1 && !openList.contains(requestingActivity)) {
					Log.e(AnkiDroidApp.TAG, "DeckManager: deck " + deckpath + " is not loaded by " + requestingActivity);
				} else if (requestingActivity != -1 && openList.size() > 1) {
					openList.remove(new Integer(requestingActivity));
					Log.i(AnkiDroidApp.TAG, "DeckManager: deck " + deckpath + " used still by more activities (" + openList.toString() + "), removing only " + requestingActivity);
					if (requestingActivity == REQUESTING_ACTIVITY_BIGWIDGET) {
						sendWidgetBigClosedNotification();
					}
				} else {
					Log.i(AnkiDroidApp.TAG, "DeckManager: closing deck " + deckpath + " (" + requestingActivity + ")");
					if (waitToFinish && (openList.contains(REQUESTING_ACTIVITY_STUDYOPTIONS))) {
						DeckTask.waitToFinish();
					}
					sLoadedDecks.get(deckpath).mClosingAsyncTask = new CloseDeckAsyncTask();
					sLoadedDecks.get(deckpath).mClosingAsyncTask.execute(sLoadedDecks.get(deckpath));

					// close widget learning screen when deck is closed by other activity
					if (openList.contains(REQUESTING_ACTIVITY_BIGWIDGET)) {
						sendWidgetBigClosedNotification();
					}
					if (sMainDeckPath != null && sMainDeckPath.equals(deckpath)) {
						sMainDeckPath = null;
					}
				}
			} else {
				Log.e(AnkiDroidApp.TAG, "DeckManager: deck " + deckpath + " is not a loaded deck");
			}			
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			mClosingDeck = null;
			mCondFinished.signal();
			mLock.unlock();
		}
	}


	private static void sendWidgetBigClosedNotification() {
		AnkiDroidWidgetBig.setDeckAndLoadCard(null);
    	AnkiDroidWidgetBig.updateWidget();
	}


	public static String getDeckPathAfterDeckSelectionDialog(int item) {
		return getDeckPathAfterDeckSelectionDialog(sDeckNames[item]);
	}
	public static String getDeckPathAfterDeckSelectionDialog(String deckName) {
		return sDeckPaths.get(deckName);
	}


	public static StyledDialog getSelectDeckDialog(Context context, OnClickListener itemClickListener, OnCancelListener cancelListener, OnDismissListener dismissListener) {
		int len = 0;
		File[] fileList;

		File dir = new File(PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext())
				.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
		fileList = dir.listFiles(new AnkiFilter());

		if (dir.exists() && dir.isDirectory() && fileList != null) {
			len = fileList.length;
		}

		TreeSet<String> tree = new TreeSet<String>();
		sDeckPaths = new HashMap<String, String>();

		if (len > 0 && fileList != null) {
			Log.i(AnkiDroidApp.TAG,
					"DeckManager - getSelectDeckDialog, number of anki files = " + len);
			for (File file : fileList) {
				String name = file.getName().replaceAll(".anki", "");
				tree.add(name);
				sDeckPaths.put(name, file.getAbsolutePath());
			}
		}

		StyledDialog.Builder builder = new StyledDialog.Builder(context);
		builder.setTitle(R.string.fact_adder_select_deck);
		// Convert to Array
		sDeckNames = new String[tree.size()];
		tree.toArray(sDeckNames);

		builder.setItems(sDeckNames, itemClickListener);
		builder.setOnCancelListener(cancelListener);
		builder.setOnDismissListener(dismissListener);
		return builder.create();
	}


    private static class CloseDeckAsyncTask extends AsyncTask<DeckInformation, Void, DeckInformation> {

    	@Override
        protected DeckInformation doInBackground(DeckInformation... params) {
            Log.d(AnkiDroidApp.TAG, "DeckManager.CloseDeckAsyncTask.doInBackground()");
            DeckInformation di = params[0];
            String deckpath = di.mDeck.getDeckPath();
            try {
                di.mDeck.closeDeck(false);
                Log.i(AnkiDroidApp.TAG, "DeckManager.CloseDeckAsyncTask: deck " + deckpath + " successfully closed");
            } catch (RuntimeException e) {
            	Log.e(AnkiDroidApp.TAG, "DeckManager.CloseDeckAsyncTask: could not close deck " + deckpath + ": " + e);
            }
            return di;
        }

        @Override
        protected void onPostExecute(DeckInformation deckInformation) {
            Log.d(AnkiDroidApp.TAG, "DeckManager.CloseDeckAsyncTask.onPostExecute()");
            sLoadedDecks.remove(deckInformation.mKey);
    		for (String dp : sLoadedDecks.keySet()) {
        		Log.i(AnkiDroidApp.TAG, "DeckManager: still loaded: " + dp + ": " + sLoadedDecks.get(dp).mOpenedBy.toString());
    		}
        }
    }


	public static class DeckInformation {
		public String mKey;
		public Deck mDeck;
		public boolean mRebuilt = true;
		public boolean mDeleteJournalModeForced = false;
		public ArrayList<Integer> mOpenedBy = new ArrayList<Integer>();
		public AsyncTask<DeckInformation, Void, DeckInformation> mClosingAsyncTask;

		DeckInformation(String key, Deck deck, int openedBy, boolean rebuilt) {
			this.mKey = key;
			this.mDeck = deck;
			this.mOpenedBy.add(openedBy);
			this.mRebuilt = rebuilt;
		}
	}
}
