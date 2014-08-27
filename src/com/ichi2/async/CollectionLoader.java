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
        // do a safety backup if last backup is too old --> addresses Android's delete db bug
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        String colPath = AnkiDroidApp.getCollectionPath();
        if (BackupManager.safetyBackupNeeded(colPath)) {
            setProgressMessage(res.getString(R.string.backup_collection));
            BackupManager.performBackup(colPath);
        }
        setProgressMessage(res.getString(R.string.open_collection));

        // load collection
        try {
            return AnkiDroidApp.openCollection(colPath);
        } catch (RuntimeException e) {
            BackupManager.restoreCollectionIfMissing(colPath);
            Log.e(AnkiDroidApp.TAG, "doInBackgroundOpenCollection - RuntimeException on opening collection: " + e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundOpenCollection");
            return null;
        }
    }
    
    @Override
    public void deliverResult(Collection col) {
        // Loader has been reset so don't forward data to listener
        if (isReset()) {
            if (col != null) {
                return;
            }
        }
        // Loader is running so dismiss progress dialog and forward data to listener
        if (isStarted()) {
            super.deliverResult(col);
        }
    }
    
    @Override
    protected void onStartLoading() {
        String colPath = AnkiDroidApp.getCollectionPath();
        
        if (AnkiDroidApp.colIsOpen() && AnkiDroidApp.getCol() != null && AnkiDroidApp.getCol().getPath().equals(colPath)) {
            // deliver current path if open and valid
            deliverResult(AnkiDroidApp.getCol());
        } else {
            // otherwise reload the collection
            forceLoad();
        }        
    }
    
    @Override
    protected void onStopLoading() {
        // The Loader has been put in a stopped state, so we should attempt to cancel the current load (if there is one).
        cancelLoad();
    }
    
    @Override
    protected void onReset() {
        // Ensure the loader is stopped.
        onStopLoading();
    }
    
    
    private void setProgressMessage(final String message) {
        // Update the text of the opening collection dialog
        if (sActivity != null && sActivity.get() != null) {
            sActivity.get().runOnUiThread(new Runnable() {
                public void run() {
                    sActivity.get().setProgressMessage(message);
                }
            });
        }
    }
}