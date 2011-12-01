package com.ichi2.widget;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Card;
import com.ichi2.anki.Deck;
import com.ichi2.anki.DeckStatus;
import com.ichi2.anki.DeckManager;

import com.tomgibara.android.veecheck.util.PrefSettings;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class WidgetContentService extends Service{
	private final IBinder widgetContentBinder = new WidgetContentBinder();

    public Deck mLoadedDeck;
    public Card mCurrentCard;
    public boolean mBigShowProgressDialog = false;
    public int mBigCurrentView;
    public String mBigCurrentMessage;
    public DeckStatus[] mTomorrowDues;
    public boolean mWaitForAsyncTask = false;
    public boolean mUpdateStarted = false;

	@Override
	public void onCreate() {
		super.onCreate();
		String path = PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getString("lastWidgetDeck", "");
		if (path != null && path.length() > 0) {
			Log.i(AnkiDroidApp.TAG, "BigWidget: reloading deck " + path);
			mLoadedDeck = DeckManager.getDeck(path, DeckManager.REQUESTING_ACTIVITY_BIGWIDGET, true);
			mCurrentCard = mLoadedDeck.getCard();
		}
	}


	@Override
	public void onDestroy() {
		String path = "";
		long cardId = 0;
		if (mLoadedDeck != null) {
			path = mLoadedDeck.getDeckPath();
			DeckManager.closeDeck(path, DeckManager.REQUESTING_ACTIVITY_BIGWIDGET);
			if (mCurrentCard != null) {
				cardId = mCurrentCard.getId();
			}
		}
		PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit().putString("lastWidgetDeck", path).commit();
		PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit().putLong("lastWidgetCard", cardId).commit();
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return widgetContentBinder;
	}

	public class WidgetContentBinder extends Binder {
		
		WidgetContentService getService() {
			return WidgetContentService.this;
		}
	}
}
