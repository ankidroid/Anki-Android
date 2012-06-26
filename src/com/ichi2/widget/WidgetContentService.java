//
//package com.ichi2.widget;
//
//import com.ichi2.anki.AnkiDroidApp;
//import com.ichi2.libanki.Card;
//import com.ichi2.libanki.Collection;
//
//import android.app.Service;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.os.Binder;
//import android.os.IBinder;
//import android.util.Log;
//
//public class WidgetContentService extends Service {
//    private final IBinder widgetContentBinder = new WidgetContentBinder();
//
//    public Collection mCol;
//    public Card mCurrentCard;
//    public boolean mBigShowProgressDialog = false;
//    public int mBigCurrentView = AnkiDroidWidgetBig.UpdateService.VIEW_NOT_SPECIFIED;
//    public String mBigCurrentMessage;
//    public DeckStatus[] mTomorrowDues;
//    public boolean mWaitForAsyncTask = false;
//    public boolean mUpdateStarted = false;
//
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
//        String path = prefs.getString("lastWidgetDeck", "");
//        if (path != null && path.length() > 0 && AnkiDroidApp.isSdCardMounted()) {
//            Log.i(AnkiDroidApp.TAG, "BigWidget: reloading deck " + path);
//            mCol = Collection.currentCollection();
//            if (mCol != null) {
//                mCurrentCard = mCol.getSched().getCard();
//            }
//        }
//    }
//
//
//    @Override
//    public void onDestroy() {
//        // // TODO: this does not seem to be reliably called
//        // String path = "";
//        long cardId = 0l;
//        if (mCol != null) {
//            // path = mLoadedDeck.getDeckPath();
//            // DeckManager.closeDeck(path, DeckManager.REQUESTING_ACTIVITY_BIGWIDGET);
//            if (mCurrentCard != null) {
//                cardId = mCurrentCard.getId();
//            }
//        }
//        // PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit().putString("lastWidgetDeck",
//        // path).commit();
//        AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit()
//                .putLong("lastWidgetCard", cardId).commit();
//    }
//
//
//    public void setCard() {
//        if (mCol != null) {
//            setCard(mCol.getSched().getCard());
//        }
//    }
//
//
//    public void setCard(Card card) {
//        mCurrentCard = card;
//        Long cardId = 0l;
//        if (card != null) {
//            cardId = card.getId();
//        }
//        AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit()
//                .putLong("lastWidgetCard", cardId).commit();
//    }
//
//
//    @Override
//    public IBinder onBind(Intent arg0) {
//        return widgetContentBinder;
//    }
//
//    public class WidgetContentBinder extends Binder {
//
//        WidgetContentService getService() {
//            return WidgetContentService.this;
//        }
//    }
//}
