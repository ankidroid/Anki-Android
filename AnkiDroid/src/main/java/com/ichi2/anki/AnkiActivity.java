
package com.ichi2.anki;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.widget.ProgressBar;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.anki.dialogs.AsyncDialogFragment;
import com.ichi2.anki.dialogs.DialogHandler;
import com.ichi2.anki.dialogs.SimpleMessageDialog;
import com.ichi2.async.CollectionLoader;
import com.ichi2.compat.CompatHelper;
import com.ichi2.compat.customtabs.CustomTabActivityHelper;
import com.ichi2.compat.customtabs.CustomTabsFallback;
import com.ichi2.compat.customtabs.CustomTabsHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.themes.Themes;
import com.ichi2.utils.AdaptionUtil;

import timber.log.Timber;

public class AnkiActivity extends AppCompatActivity implements SimpleMessageDialog.SimpleMessageDialogListener {

    public final int SIMPLE_NOTIFICATION_ID = 0;
    public static final int REQUEST_REVIEW = 901;

    private DialogHandler mHandler = new DialogHandler(this);

    // custom tabs
    private CustomTabActivityHelper mCustomTabActivityHelper;

    private boolean mIsDestroyed = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.i("AnkiActivity::onCreate");
        // The hardware buttons should control the music volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Set the theme
        Themes.setTheme(this);
        super.onCreate(savedInstanceState);
        mCustomTabActivityHelper = new CustomTabActivityHelper();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AnkiDroidApp.updateContextWithLanguage(base));
    }

    @Override
    protected void onStart() {
        Timber.i("AnkiActivity::onStart");
        super.onStart();
        mCustomTabActivityHelper.bindCustomTabsService(this);
    }

    @Override
    protected void onStop() {
        Timber.i("AnkiActivity::onStop");
        super.onStop();
        mCustomTabActivityHelper.unbindCustomTabsService(this);
    }


    @Override
    protected void onPause() {
        Timber.i("AnkiActivity::onPause");
        super.onPause();
    }



    @Override
    protected void onResume() {
        Timber.i("AnkiActivity::onResume");
        super.onResume();
        UsageAnalytics.sendAnalyticsScreenView(this);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(SIMPLE_NOTIFICATION_ID);
        // Show any pending dialogs which were stored persistently
        mHandler.readMessage();
    }

    @Override
    protected void onDestroy() {
        this.mIsDestroyed = true;
        Timber.i("AnkiActivity::onDestroy");
        super.onDestroy();
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Timber.i("Home button pressed");
                finishWithoutAnimation();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    // called when the CollectionLoader finishes... usually will be over-ridden
    protected void onCollectionLoaded(Collection col) {
        hideProgressBar();
    }


    public Collection getCol() {
        return CollectionHelper.getInstance().getCol(this);
    }

    public boolean colIsOpen() {
        return CollectionHelper.getInstance().colIsOpen();
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
        try {
            super.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            UIUtils.showSimpleSnackbar(this, R.string.activity_start_failed,true);
        }
    }


    public void startActivityForResultWithoutAnimation(Intent intent, int requestCode) {
        disableIntentAnimation(intent);
        startActivityForResult(intent, requestCode);
        disableActivityAnimation();
    }


    public void startActivityForResultWithAnimation(Intent intent, int requestCode, int animation) {
        enableIntentAnimation(intent);
        startActivityForResult(intent, requestCode);
        enableActivityAnimation(animation);
    }


    @Deprecated
    @Override
    public void finish() {
        super.finish();
    }


    public void finishWithoutAnimation() {
        Timber.i("finishWithoutAnimation");
        super.finish();
        disableActivityAnimation();
    }


    public void finishWithAnimation(int animation) {
        Timber.i("finishWithAnimation %d", animation);
        super.finish();
        enableActivityAnimation(animation);
    }


    protected void disableViewAnimation(View view) {
        view.clearAnimation();
    }

    /** Compat shim for API 16 */
    public boolean wasDestroyed() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            return super.isDestroyed();
        }
        return mIsDestroyed;
    }


    protected void enableViewAnimation(View view, Animation animation) {
        if (animationDisabled()) {
            disableViewAnimation(view);
        } else {
            view.setAnimation(animation);
        }
    }

    /** Finish Activity using FADE animation **/
    public static void finishActivityWithFade(Activity activity) {
        activity.finish();
        ActivityTransitionAnimation.slide(activity, ActivityTransitionAnimation.UP);
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
        Timber.d("AnkiActivity.startLoadingCollection()");
        if (colIsOpen()) {
            Timber.d("Synchronously calling onCollectionLoaded");
            onCollectionLoaded(getCol());
            return;
        }
        // Open collection asynchronously if it hasn't already been opened
        showProgressBar();
        CollectionLoader.load(this, col -> {
            if (col != null) {
                Timber.d("Asynchronously calling onCollectionLoaded");
                onCollectionLoaded(col);
            } else {
                Intent deckPicker = new Intent(this, DeckPicker.class);
                deckPicker.putExtra("collectionLoadError", true); // don't currently do anything with this
                deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityWithAnimation(deckPicker, ActivityTransitionAnimation.LEFT);
            }
        });
    }

    public void showProgressBar() {
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
    }


    public void hideProgressBar() {
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }


    protected void mayOpenUrl(Uri url) {
        boolean success = mCustomTabActivityHelper.mayLaunchUrl(url, null, null);
        if (!success) {
            Timber.w("Couldn't preload url: %s", url.toString());
        }
    }

    protected void openUrl(Uri url) {
        //DEFECT: We might want a custom view for the toast, given i8n may make the text too long for some OSes to
        //display the toast
        if (!AdaptionUtil.hasWebBrowser(this)) {
            UIUtils.showThemedToast(this, getResources().getString(R.string.no_browser_notification) + url, false);
            return;
        }

        CustomTabActivityHelper helper = getCustomTabActivityHelper();
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(helper.getSession());
        builder.setToolbarColor(ContextCompat.getColor(this, R.color.material_light_blue_500)).setShowTitle(true);
        builder.setStartAnimations(this, R.anim.slide_right_in, R.anim.slide_left_out);
        builder.setExitAnimations(this, R.anim.slide_left_in, R.anim.slide_right_out);
        builder.setCloseButtonIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.ic_arrow_back_white_24dp));
        CustomTabsIntent customTabsIntent = builder.build();
        CustomTabsHelper.addKeepAliveExtra(this, customTabsIntent.intent);
        CustomTabActivityHelper.openCustomTab(this, customTabsIntent, url, new CustomTabsFallback());
    }

    public CustomTabActivityHelper getCustomTabActivityHelper() {
        return mCustomTabActivityHelper;
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
        getSupportFragmentManager().executePendingTransactions();
    }


    /**
     * Calls {@link #showAsyncDialogFragment(AsyncDialogFragment, NotificationChannels.Channel)} internally, using the channel
     * {@link NotificationChannels.Channel#GENERAL}
     *
     * @param newFragment  the AsyncDialogFragment you want to show
     */
    public void showAsyncDialogFragment(AsyncDialogFragment newFragment) {
        showAsyncDialogFragment(newFragment, NotificationChannels.Channel.GENERAL);
    }


    /**
     * Global method to show a dialog fragment including adding it to back stack and handling the case where the dialog
     * is shown from an async task, by showing the message in the notification bar if the activity was stopped before the
     * AsyncTask completed
     *
     * @param newFragment  the AsyncDialogFragment you want to show
     * @param channel the NotificationChannels.Channel to use for the notification
     */
    public void showAsyncDialogFragment(AsyncDialogFragment newFragment, NotificationChannels.Channel channel) {
        try {
            showDialogFragment(newFragment);
        } catch (IllegalStateException e) {
            // Store a persistent message to SharedPreferences instructing AnkiDroid to show dialog
            DialogHandler.storeMessage(newFragment.getDialogHandlerMessage());
            // Show a basic notification to the user in the notification bar in the meantime
            String title = newFragment.getNotificationTitle();
            String message = newFragment.getNotificationMessage();
            showSimpleNotification(title, message, channel);
        }
    }


    /**
     * Show a simple message dialog, dismissing the message without taking any further action when OK button is pressed.
     * If a DialogFragment cannot be shown due to the Activity being stopped then the message is shown in the
     * notification bar instead.
     *
     * @param message
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


    public void showSimpleNotification(String title, String message, NotificationChannels.Channel channel) {
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(this);
        // Show a notification unless all notifications have been totally disabled
        if (Integer.parseInt(prefs.getString("minimumCardsDueForNotification", "0")) <= Preferences.PENDING_NOTIFICATIONS_ONLY) {
            // Use the title as the ticker unless the title is simply "AnkiDroid"
            String ticker = title;
            if (title.equals(getResources().getString(R.string.app_name))) {
                ticker = message;
            }
            // Build basic notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
                    NotificationChannels.getId(channel))
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setColor(ContextCompat.getColor(this, R.color.material_light_blue_500))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
            resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
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
            startActivityWithoutAnimation(deckPicker);
        }
    }


    // Dismiss whatever dialog is showing
    public void dismissAllDialogFragments() {
        getSupportFragmentManager().popBackStack("dialog", FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }


    // Restart the activity
    public void restartActivity() {
        Timber.i("AnkiActivity -- restartActivity()");
        Intent intent = new Intent();
        intent.setClass(this, this.getClass());
        intent.putExtras(new Bundle());
        this.startActivityWithoutAnimation(intent);
        this.finishWithoutAnimation();
    }

    protected void enableToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
    }

    protected void enableToolbar(@Nullable View view) {
        if (view == null) {
            Timber.w("Unable to enable toolbar - invalid view supplied");
            return;
        }
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
    }
}

