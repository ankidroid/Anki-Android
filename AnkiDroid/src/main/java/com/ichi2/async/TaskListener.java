package com.ichi2.async;

import android.os.AsyncTask;

/**
 * Listener for the status and result of a {@link CollectionTask}.
 * <p>
 * Its methods are guaranteed to be invoked on the main thread.
 * <p>
 * Their semantics is equivalent to the methods of {@link AsyncTask}.
 */
public abstract class TaskListener {

    /** Invoked before the task is started. */
    public abstract void onPreExecute();


    /**
     * Invoked after the task has completed.
     * <p>
     * The semantics of the result depends on the task itself.
     */
    public abstract void onPostExecute(TaskData result);


    /**
     * Invoked when the background task publishes an update.
     * <p>
     * The semantics of the update data depends on the task itself.
     */
    public void onProgressUpdate(TaskData value) {
        // most implementations do nothing with this, provide them a default implementation
    }

    public void onCancelled() {
        // most implementations do nothing with this, provide them a default implementation
    }
}
