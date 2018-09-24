package com.ichi2.async;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import android.os.AsyncTask;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.libanki.Collection;

import timber.log.Timber;

public final class CollectionLoader extends AsyncTask<Void, Void, Collection> {
    private LifecycleOwner mLifecycleOwner;
    private Callback mCallback;

    public interface Callback {
        void execute(Collection col);
    }

    public static void load(LifecycleOwner lifecycleOwner, Callback callback) {
        CollectionLoader loader = new CollectionLoader(lifecycleOwner, callback);
        loader.execute();
    }

    private CollectionLoader(LifecycleOwner lifecycleOwner, Callback callback) {
        mLifecycleOwner = lifecycleOwner;
        mCallback = callback;
    }

    @Override
    protected Collection doInBackground(Void... params) {
        // Don't touch collection if lockCollection flag is set
        if (CollectionHelper.getInstance().isCollectionLocked()) {
            Timber.w("onStartLoading() :: Another thread has requested to keep the collection closed.");
            return null;
        }
        // load collection
        try {
            Timber.d("CollectionLoader accessing collection");
            return CollectionHelper.getInstance().getCol(AnkiDroidApp.getInstance().getApplicationContext());
        } catch (RuntimeException e) {
            Timber.e(e, "loadInBackground - RuntimeException on opening collection");
            AnkiDroidApp.sendExceptionReport(e, "CollectionLoader.loadInBackground");
            return null;
        }
    }

    @Override
    protected void onPostExecute(Collection col) {
        super.onPostExecute(col);
        if (mLifecycleOwner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.CREATED)) {
            mCallback.execute(col);
        }
    }

}