package com.ichi2.async;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.libanki.Collection;

import timber.log.Timber;

public class CollectionLoader extends AsyncTaskLoader<Collection> {

    public CollectionLoader(Context context) {
        super(context);
    }

    @Override
    public Collection loadInBackground() {
        // load collection
        try {
            Timber.d("CollectionLoader accessing collection");
            return CollectionHelper.getInstance().getCol(getContext());
        } catch (RuntimeException e) {
            Timber.e(e, "loadInBackground - RuntimeException on opening collection");
            AnkiDroidApp.sendExceptionReport(e, "CollectionLoader.loadInBackground");
            return null;
        }
    }
    
    @Override
    public void deliverResult(Collection col) {
        Timber.d("CollectionLoader.deliverResult()");
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
        // Don't touch collection if lockCollection flag is set
        if (CollectionHelper.getInstance().isCollectionLocked()) {
            Timber.w("onStartLoading() :: Another thread has requested to keep the collection closed.");
            return;
        }
        // Since the CollectionHelper only opens if necessary, we can just force every time
        forceLoad();
    }
    
    @Override
    protected void onStopLoading() {
        // The Loader has been put in a stopped state, so we should attempt to cancel the current load (if there is one).
        Timber.d("CollectionLoader.onStopLoading()");
        cancelLoad();
    }
    
    @Override
    protected void onReset() {
        // Ensure the loader is stopped.
        Timber.d("CollectionLoader.onReset()");
        onStopLoading();
    }
}