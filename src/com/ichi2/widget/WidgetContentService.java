package com.ichi2.widget;

import com.ichi2.anki.Card;
import com.ichi2.anki.Deck;
import com.ichi2.anki.DeckStatus;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

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
	}


	@Override
	public void onDestroy() {
		if (mLoadedDeck != null) {
			mLoadedDeck.closeDeck();
		}
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
