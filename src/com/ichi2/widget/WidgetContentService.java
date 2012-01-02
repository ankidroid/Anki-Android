//package com.ichi2.widget;
//
//import com.ichi2.anki.AnkiDroidApp;
//import com.ichi2.anki.Card;
//import com.ichi2.anki.Deck;
//import com.ichi2.anki.DeckStatus;
//import com.ichi2.anki.DeckManager;
//
//import com.tomgibara.android.veecheck.util.PrefSettings;
//
//import android.app.Service;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.os.Binder;
//import android.os.IBinder;
//import android.util.Log;
//
//public class WidgetContentService extends Service{
//	private final IBinder widgetContentBinder = new WidgetContentBinder();
//
//    public Decks mLoadedDeck;
//    public Card mCurrentCard;
//    public boolean mBigShowProgressDialog = false;
//    public int mBigCurrentView = AnkiDroidWidgetBig.UpdateService.VIEW_NOT_SPECIFIED;
//    public String mBigCurrentMessage;
//    public DeckStatus[] mTomorrowDues;
//    public boolean mWaitForAsyncTask = false;
//    public boolean mUpdateStarted = false;
//
//	@Override
//	public void onCreate() {
//		super.onCreate();
//		SharedPreferences prefs = PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
//		String path = prefs.getString("lastWidgetDeck", "");
//		if (path != null && path.length() > 0 && AnkiDroidApp.isSdCardMounted()) {
//			Log.i(AnkiDroidApp.TAG, "BigWidget: reloading deck " + path);
//			mLoadedDeck = DeckManager.getDeck(path, DeckManager.REQUESTING_ACTIVITY_BIGWIDGET, true);
//			if (mLoadedDeck != null) {
//				mCurrentCard = mLoadedDeck.getCard();				
//			}
//		}
//	}
//
//
//	@Override
//	public void onDestroy() {
//		// TODO: this does not seem to be reliably called
//		String path = "";
//		long cardId = 0l;
//		if (mLoadedDeck != null) {
//			path = mLoadedDeck.getDeckPath();
//			DeckManager.closeDeck(path, DeckManager.REQUESTING_ACTIVITY_BIGWIDGET);
//			if (mCurrentCard != null) {
//				cardId = mCurrentCard.getId();
//			}
//		}
//		PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit().putString("lastWidgetDeck", path).commit();
//		PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit().putLong("lastWidgetCard", cardId).commit();
//	}
//
//	public void setCard() {
//		if (mLoadedDeck != null) {
//			setCard(mLoadedDeck.getCard());
//		}
//	}
//	public void setCard(Card card) {
//		mCurrentCard = card;
//		Long cardId = 0l;
//		if (card != null) {
//			cardId = card.getId();
//		}
//		PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit().putLong("lastWidgetCard", cardId).commit();
//	}
//
//	@Override
//	public IBinder onBind(Intent arg0) {
//		return widgetContentBinder;
//	}
//
//	public class WidgetContentBinder extends Binder {
//		
//		WidgetContentService getService() {
//			return WidgetContentService.this;
//		}
//	}
//}
