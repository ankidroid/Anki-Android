
package com.ichi2.anki;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.IntentCompat;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.AsyncDialogFragment;
import com.ichi2.anki.dialogs.DialogHandler;
import com.ichi2.anki.dialogs.SimpleMessageDialog;
import com.ichi2.async.CollectionLoader;
import com.ichi2.libanki.Collection;
import com.ichi2.themes.StyledOpenCollectionDialog;

public class AnkiActivity extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Collection>,
        SimpleMessageDialog.SimpleMessageDialogListener {

    public final int SIMPLE_NOTIFICATION_ID = 0;

    private StyledOpenCollectionDialog mOpenCollectionDialog;
    private DialogHandler mHandler = new DialogHandler(this);

    @Override
    protected void onResume() {
        super.onResume();
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(SIMPLE_NOTIFICATION_ID);
        // Show any pending dialogs
        Message handlerMsg = AnkiDroidApp.getStoredDialogHandlerMessage();
        if (handlerMsg != null) {
            // TODO: Confirm whether or not it's ok to send the message without checking if the collection is open.
            mHandler.sendMessage(handlerMsg);
            AnkiDroidApp.setStoredDialogHandlerMessage(null);
        }
    }


    // called when the CollectionLoader finishes... usually will be over-ridden
    protected void onCollectionLoaded(Collection col) {
    }


    public Collection getCol() {
        return AnkiDroidApp.getCol();
    }

    public boolean colOpen() {
        return AnkiDroidApp.colIsOpen();
    }


    public boolean animationDisabled() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(this);
        return preferences.getBoolean("eInkDisplay", false);
    }


    public boolean animationEnabled() {
        return !animationDisabled();
    }


    @Override
    public void setContentView(View view) {
        if (animationDisabled()) {
            view.clearAnimation();
        }
        super.setContentView(view);
    }


    @Override
    public void setContentView(View view, LayoutParams params) {
        if (animationDisabled()) {
            view.clearAnimation();
        }
        super.setContentView(view, params);
    }


    @Override
    public void addContentView(View view, LayoutParams params) {
        if (animationDisabled()) {
            view.clearAnimation();
        }
        super.addContentView(view, params);
    }


    @Deprecated
    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
    }


    public void startActivityWithoutAnimation(Intent intent) {
        disableIntentAnimation(intent);
        super.startActivity(intent);
        disableActivityAnimation();
    }


    public void startActivityWithAnimation(Intent intent, int animation) {
        enableIntentAnimation(intent);
        super.startActivity(intent);
        enableActivityAnimation(animation);
    }


    @Deprecated
    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        super.startActivityForResult(intent, requestCode);
    }


    public void startActivityForResultWithoutAnimation(Intent intent, int requestCode) {
        disableIntentAnimation(intent);
        super.startActivityForResult(intent, requestCode);
        disableActivityAnimation();
    }


    public void startActivityForResultWithAnimation(Intent intent, int requestCode, int animation) {
        enableIntentAnimation(intent);
        super.startActivityForResult(intent, requestCode);
        enableActivityAnimation(animation);
    }


    @Deprecated
    @Override
    public void finish() {
        super.finish();
    }


    public void finishWithoutAnimation() {
        super.finish();
        disableActivityAnimation();
    }


    public void finishWithAnimation(int animation) {
        super.finish();
        enableActivityAnimation(animation);
    }


    protected void disableViewAnimation(View view) {
        view.clearAnimation();
    }


    protected void enableViewAnimation(View view, Animation animation) {
        if (animationDisabled()) {
            disableViewAnimation(view);
        } else {
            view.setAnimation(animation);
        }
    }


    private void disableIntentAnimation(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    }


    private void disableActivityAnimation() {
        ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.NONE);
    }


    private void enableIntentAnimation(Intent intent) {
        if (animationDisabled()) {
            disableIntentAnimation(intent);
        } else {
            // Nothing for now
        }
    }


    private void enableActivityAnimation(int animation) {
        if (animationDisabled()) {
            disableActivityAnimation();
        } else {
            ActivityTransitionAnimation.slide(this, animation);
        }
    }


    // Method for loading the collection which is inherited by all AnkiActivitys
    public void startLoadingCollection() {
        // Initialize the open collection loader
        Log.i(AnkiDroidApp.TAG, "AnkiActivity.startLoadingCollection()");
        if (!AnkiDroidApp.colIsOpen()) {
            showOpeningCollectionDialog();
        }
        getSupportLoaderManager().restartLoader(0, null, this);
    }


    // Kick user back to DeckPicker on collection load error unless this method is overridden
    protected void onCollectionLoadError() {
        Intent deckPicker = new Intent(this, DeckPicker.class);
        deckPicker.putExtra("collectionLoadError", true); // don't currently do anything with this
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityWithAnimation(deckPicker, ActivityTransitionAnimation.LEFT);
    }


    // CollectionLoader Listener callbacks
    @Override
    public Loader<Collection> onCreateLoader(int id, Bundle args) {
        // Currently only using one loader, so ignore id
        return new CollectionLoader(this);
    }


    @Override
    public void onLoadFinished(Loader<Collection> loader, Collection col) {
        if (col != null && AnkiDroidApp.colIsOpen()) {
            onCollectionLoaded(col);
        } else {
            onCollectionLoadError();
        }
    }


    @Override
    public void onLoaderReset(Loader<Collection> arg0) {
        // We don't currently retain any references, so no need to free any data here
    }


    public void showOpeningCollectionDialog() {
        if (mOpenCollectionDialog == null || !mOpenCollectionDialog.isShowing()) {
            mOpenCollectionDialog = StyledOpenCollectionDialog.show(AnkiActivity.this,
                    getResources().getString(R.string.open_collection), new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface arg0) {
                            finishWithoutAnimation();
                        }
                    });
        }
    }


    public void dismissOpeningCollectionDialog() {
        if (mOpenCollectionDialog != null && mOpenCollectionDialog.isShowing()) {
            mOpenCollectionDialog.dismiss();
        }
    }


    // Change string on collection loading progress dialog
    public void setOpeningCollectionDialogMessage(String message) {
        if (mOpenCollectionDialog != null && mOpenCollectionDialog.isShowing()) {
            mOpenCollectionDialog.setMessage(message);
        }
    }


    /**
     * Global method to show dialog fragment including adding it to back stack Note: DO NOT call this from an async
     * task! If you need to show a dialog from an async task, use showAsyncDialogFragment()
     * 
     * @param newFragment  the DialogFragment you want to show
     */
    public void showDialogFragment(DialogFragment newFragment) {
        // DialogFragment.show() will take care of adding the fragment
        // in a transaction. We also want to remove any currently showing
        // dialog, so make our own transaction and take care of that here.
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        // save transaction to the back stack
        ft.addToBackStack("dialog");
        newFragment.show(ft, "dialog");
    }


    /**
     * Global method to show a dialog fragment including adding it to back stack and handling the case where the dialog
     * is shown from an async task, by showing the message in the notification bar if the activity was stopped before the
     * AsyncTask completed
     * 
     * @param newFragment  the AsyncDialogFragment you want to show
     */
    public void showAsyncDialogFragment(AsyncDialogFragment newFragment) {
        try {
            showDialogFragment(newFragment);
        } catch (IllegalStateException e) {
            String title = newFragment.getNotificationTitle();
            String message = newFragment.getNotificationMessage();
            AnkiDroidApp.setStoredDialogHandlerMessage(newFragment.getDialogHandlerMessage());
            showSimpleNotification(title, message);
        }
    }


    /**
     * Show a simple message dialog, dismissing the message without taking any further action when OK button is pressed.
     * If a DialogFragment cannot be shown due to the Activity being stopped then the message is shown in the
     * notification bar instead.
     * 
     * @param String message
     */
    protected void showSimpleMessageDialog(String message) {
        showSimpleMessageDialog(message, false);
    }

    protected void showSimpleMessageDialog(String title, String message){
        showSimpleMessageDialog(title, message, false);
    }


    /**
     * Show a simple message dialog, dismissing the message without taking any further action when OK button is pressed.
     * If a DialogFragment cannot be shown due to the Activity being stopped then the message is shown in the
     * notification bar instead.
     * 
     * @param message
     * @param reload flag which forces app to be restarted when true
     */
    protected void showSimpleMessageDialog(String message, boolean reload) {
        AsyncDialogFragment newFragment = SimpleMessageDialog.newInstance(message, reload);
        showAsyncDialogFragment(newFragment);
    }

    protected void showSimpleMessageDialog(String title, String message, boolean reload) {
        AsyncDialogFragment newFragment = SimpleMessageDialog.newInstance(title, message, reload);
        showAsyncDialogFragment(newFragment);
    }


    protected void showSimpleNotification(String title, String message) {
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(this);
        // Don't show notification if disabled in preferences
        if (Integer.parseInt(prefs.getString("minimumCardsDueForNotification", "0")) <= 1000000) {
            // Use the title as the ticker unless the title is simply "AnkiDroid"
            String ticker = title;
            if (title.equals(getResources().getString(R.string.app_name))) {
                ticker = message;
            }
            // Build basic notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setTicker(ticker);
            // Enable vibrate and blink if set in preferences
            if (prefs.getBoolean("widgetVibrate", false)) {
                builder.setVibrate(new long[] { 1000, 1000, 1000});
            }
            if (prefs.getBoolean("widgetBlink", false)) {
                builder.setLights(Color.BLUE, 1000, 1000);
            }
            // Creates an explicit intent for an Activity in your app
            Intent resultIntent = new Intent(this, DeckPicker.class);
            resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | IntentCompat.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            if (resultPendingIntent == null) {
                // PendingIntent could not be created... probably something wrong with the extras
                // try again without the extras, though the original dialog will not be shown when app started
                Log.e(AnkiDroidApp.TAG, "AnkiActivity.showSimpleNotification() failed due to null PendingIntent");
                resultIntent = new Intent(this, DeckPicker.class);
                resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
            builder.setContentIntent(resultPendingIntent);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            // mId allows you to update the notification later on.
            notificationManager.notify(SIMPLE_NOTIFICATION_ID, builder.build());
        }

    }

    public DialogHandler getDialogHandler() {
        return mHandler;
    }

    // Handle closing simple message dialog
    @Override
    public void dismissSimpleMessageDialog(boolean reload) {
        dismissAllDialogFragments();
        if (reload) {
            Intent deckPicker = new Intent(this, DeckPicker.class);
            deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityWithAnimation(deckPicker, ActivityTransitionAnimation.LEFT);
        }
    }


    // Dismiss whatever dialog is showing
    public void dismissAllDialogFragments() {
        getSupportFragmentManager().popBackStack("dialog", FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }


    // Restart the activity
    @SuppressLint("NewApi")
    protected void restartActivity() {
        Log.i(AnkiDroidApp.TAG, "AnkiActivity -- restartActivity()");
        Intent intent = new Intent();
        intent.setClass(this, this.getClass());
        intent.putExtras(new Bundle());
        this.startActivityWithoutAnimation(intent);
        this.finishWithoutAnimation();
    }
}
