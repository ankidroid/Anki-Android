
package com.ichi2.anki;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.async.CollectionLoader;
import com.ichi2.libanki.Collection;
import com.ichi2.themes.StyledOpenCollectionDialog;

public class AnkiActivity extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Collection> {
    private Collection mCollection;
    private StyledOpenCollectionDialog mOpenCollectionDialog;


    // called when the CollectionLoader finishes... usually will be over-ridden
    protected void onCollectionLoaded(Collection col) {
    }


    public Collection getCol() {
        return mCollection;
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
        mCollection = col;
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


    // Dismiss whatever dialog is showing
    public void dismissAllDialogFragments() {
        getSupportFragmentManager().popBackStack("dialog", FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }


    // Restart the activity
    @SuppressLint("NewApi")
    protected void restartActivity() {
        // update language
        AnkiDroidApp.setLanguage(AnkiDroidApp.getSharedPrefs(getBaseContext()).getString(Preferences.LANGUAGE, ""));
        if (AnkiDroidApp.SDK_VERSION >= 11) {
            this.recreate();
        } else {
            Intent intent = new Intent();
            intent.setClass(this, this.getClass());
            this.startActivity(intent);
            this.finishWithoutAnimation();
        }
    }
}
