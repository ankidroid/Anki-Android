package com.ichi2.async;

import android.content.Context;
import android.content.res.Resources;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;

import java.lang.ref.WeakReference;

public class CollectionLoader extends AsyncTaskLoader<Collection> {
    static WeakReference<AnkiActivity> sActivity;
    
    public CollectionLoader(Context context) {
        super(context);
        try {
            sActivity = new WeakReference<AnkiActivity>((AnkiActivity) context);    
        } catch (ClassCastException e) {
            sActivity = null;
        }
        
    }

    @Override
    public Collection loadInBackground() {
        // load collection
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        String colPath = AnkiDroidApp.getCollectionPath();
        try {
            return AnkiDroidApp.openCollection(colPath);
        } catch (RuntimeException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundOpenCollection - RuntimeException on opening collection: " + e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundOpenCollection");
            return null;
        }
    }
    
    @Override
    public void deliverResult(Collection col) {
        Log.v(AnkiDroidApp.TAG, "CollectionLoader.deliverResult()");
        // Loader has been reset so don't forward data to listener
        if (isReset()) {
            if (col != null) {
                return;
            }
        }
        // Loader is running so forward data to listener
        if (isStarted()) {
            super.deliverResult(col);
        }
    }
    
    @Override
    protected void onStartLoading() {
        // Don't touch collection if sync in progress
        if (AnkiDroidApp.getSyncInProgress()) {
            Log.v(AnkiDroidApp.TAG, "CollectionLoader.onStartLoading() -- sync in progress; don't load collection");
            return;
        }
        String colPath = AnkiDroidApp.getCollectionPath();
        if (AnkiDroidApp.colIsOpen() && AnkiDroidApp.getCol() != null && AnkiDroidApp.getCol().getPath().equals(colPath)) {
            // deliver current path if open and valid
            Log.v(AnkiDroidApp.TAG, "CollectionLoader.onStartLoading() -- deliverResult as col already open");
            deliverResult(AnkiDroidApp.getCol());
        } else {
            // otherwise reload the collection
            Log.v(AnkiDroidApp.TAG, "CollectionLoader.onStartLoading() -- force load collection");
            forceLoad();
        }        
    }
    
    @Override
    protected void onStopLoading() {
        // The Loader has been put in a stopped state, so we should attempt to cancel the current load (if there is one).
        Log.v(AnkiDroidApp.TAG, "CollectionLoader.onStopLoading()");
        cancelLoad();
    }
    
    @Override
    protected void onReset() {
        // Ensure the loader is stopped.
        Log.v(AnkiDroidApp.TAG, "CollectionLoader.onReset()");
        onStopLoading();
    }
    
    
    private void setProgressMessage(final String message) {
        // Update the text of the opening collection dialog
        if (sActivity != null && sActivity.get() != null) {
            sActivity.get().runOnUiThread(new Runnable() {
                public void run() {
                    sActivity.get().setOpeningCollectionDialogMessage(message);
                }
            });
        }
    }
}