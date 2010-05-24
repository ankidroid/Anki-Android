package com.ichi2.anki;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import com.tomgibara.android.veecheck.util.PrefSettings;

public class AnkiDroidWidget extends AppWidgetProvider {
	private static final String TAG = "AnkiDroidWidget";
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		Log.i(TAG, "onUpdate");
		
		context.startService(new Intent(context, UpdateService.class));
	}
	
	public static class UpdateService extends Service {
		private static final String TAG = "AnkiDroidWidgetUpdateService";

		//Simple class to hold the deck information for the widget
		public class DeckInformation {
			private String deckName;
			private int newCards;
			private int dueCards;
			
			public String getDeckName() { return deckName; }
			public int getNewCards() { return newCards; }
			public int getDueCards() { return dueCards; }
			
			public DeckInformation(String deckName, int newCards, int dueCards) {
				this.deckName = deckName;
				this.newCards = newCards;
				this.dueCards = dueCards;
			}
			
			public String toString() {
				if(getDeckName().length()>13)
					return String.format("%s %d %d", getDeckName().substring(0, 13), getNewCards(), getDueCards());
				else 
					return String.format("%s %d %d", getDeckName(), getNewCards(), getDueCards());
			}
		}
		
		@Override
		public void onStart(Intent intent, int startId) {
			Log.i(TAG, "OnStart");
			
			RemoteViews updateViews = buildUpdate(this);

			ComponentName thisWidget = new ComponentName(this, AnkiDroidWidget.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
		}

		private RemoteViews buildUpdate(Context context) {
			Log.i(TAG, "buildUpdate");

			//Resources res = context.getResources();
			RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.ankidroid_widget_view);
            Deck currentDeck = AnkiDroidApp.getDeck();
            
			//Fetch the deck information, sorted by due cards
			ArrayList<DeckInformation> decks = fetchDeckInformation();
			//ArrayList<DeckInformation> decks = mockFetchDeckInformation(); // TODO use real instead of mock
			StringBuilder sb = new StringBuilder();
			
			int totalDue = 0;
			
			//If there are less than 3 decks display all, otherwise only the first 3
			for(int i=0; i<decks.size() && i<3; i++) {
				DeckInformation deck = decks.get(i);
				sb.append(String.format("%s\n", deck.toString()));
				
				totalDue += deck.getDueCards();
			}
			
			if(sb.length()>1) { //Get rid of the trailing \n
				sb.substring(0, sb.length()-1);
			}
			
			updateViews.setTextViewText(R.id.anki_droid_text, sb);
						
			if(currentDeck!=null) {
				AnkiDroidApp.setDeck(currentDeck);
				Deck.openDeck(currentDeck.getDeckPath());
			}
			
			if(totalDue>30) { //Raise a notification
				String ns = Context.NOTIFICATION_SERVICE;
				NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
				
				int icon = R.drawable.anki;
				CharSequence tickerText = String.format("%d AnkiDroid cards due", totalDue);
				long when = System.currentTimeMillis();

				Notification notification = new Notification(icon, tickerText, when);
				notification.defaults |= Notification.DEFAULT_VIBRATE;
				notification.defaults |= Notification.DEFAULT_LIGHTS;
				
				Context appContext = getApplicationContext();
				CharSequence contentTitle = "Cards Due";
				String contentText = sb.toString();
				Intent notificationIntent = new Intent(this, AnkiDroid.class);
				PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

				notification.setLatestEventInfo(appContext, contentTitle, contentText, contentIntent);
				
				final int WIDGET_NOTIFY_ID = 1;
				mNotificationManager.notify(WIDGET_NOTIFY_ID, notification);
			}
			
			return updateViews;
		}

		@SuppressWarnings("unused")
		private ArrayList<DeckInformation> mockFetchDeckInformation() {
			final int maxDecks = 10;
			ArrayList<DeckInformation> information = new ArrayList<DeckInformation>(maxDecks);
			
			for(int i=0; i<maxDecks; i++) {
				String deckName = String.format("my anki deck number %d", i);
				information.add(new DeckInformation(deckName, i*20, i*25));
			}
			
			Collections.sort(information, new ByDueComparator());
			Collections.reverse(information);
			
			return information;
		}

		private ArrayList<DeckInformation> fetchDeckInformation() {
			Log.i(TAG, "fetchDeckInformation");
			
			SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
			String deckPath = preferences.getString("deckPath", "/sdcard");
			
			File dir = new File(deckPath);
			
			if(dir==null) //Directory doesn't exist
				return new ArrayList<DeckInformation>();
			
			File[] fileList = dir.listFiles(new AnkiFileFilter());
			
			if(fileList==null || fileList.length==0) //No files or some other error
				return new ArrayList<DeckInformation>();
			
			//For the deck information
			ArrayList<DeckInformation> information = new ArrayList<DeckInformation>(fileList.length);
			
			for(File file : fileList) {
				try { //Run through the decks and get the information
					String absPath = file.getAbsolutePath();
					String deckName = file.getName().replaceAll(".anki", "");
					
					Deck deck = Deck.openDeck(absPath);
					int dueCards = deck.failedSoonCount + deck.revCount;
					int newCards = deck.newCountToday;
					deck.closeDeck();
					
					//Add the information about the deck
					information.add(new DeckInformation(deckName, newCards, dueCards));
				}
				catch(Exception e) {
					Log.i(TAG, "Could not open deck");
					Log.e(TAG, e.toString());
				}
			}
			
			if(!information.isEmpty() && information.size()>1) { //Sort and reverse the list if there are decks
				Log.i(TAG, "Sorting deck");
				
				Collections.sort(information, new ByDueComparator());
				Collections.reverse(information);
			}
			
			return information;
		}
		
		//Sorter for the decks based on number due
		public class ByDueComparator implements java.util.Comparator<DeckInformation>
		{
			public int compare(DeckInformation deck1, DeckInformation deck2) {
				
				if(deck2.dueCards == deck2.dueCards)
					return 0;
				
				if(deck1.dueCards > deck2.dueCards)
					return 1;
				
				return -1;
			}
		}
		
		private static final class AnkiFileFilter implements FileFilter
		{
			public boolean accept(File pathname)
			{
				return pathname.isFile() && pathname.getName().endsWith(".anki");
			}
		}
			
		@Override
		public IBinder onBind(Intent arg0) {
			Log.i(TAG, "onBind");
			// TODO Auto-generated method stub
			return null;
		}
	}
}
