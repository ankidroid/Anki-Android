package com.ichi2.async;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;

/** Similar to task listener, but if the context disappear, no action are executed.
 * We ensure that the context can't disappear during the execution of the methods. */
public abstract class TaskListenerWithContext<CTX> extends TaskListener {
    private WeakReference<CTX> mContext;
    protected TaskListenerWithContext(CTX context) {
        mContext = new WeakReference<>(context);
    }


    final public void onPreExecute() {
        CTX context = mContext.get();
        if (context != null) {
            actualOnPreExecute(context);
        }
    };


    final public void onProgressUpdate(TaskData value) {
        CTX context = mContext.get();
        if (context != null) {
            actualOnProgressUpdate(context, value);
        }
    }


    /**
     * Invoked when the background task publishes an update.
     * <p>
     * The semantics of the update data depends on the task itself.
     * Assumes context exists.
     */
    public void actualOnProgressUpdate(@NonNull CTX context, TaskData value) {
        // most implementations do nothing with this, provide them a default implementation
    }


    /** Invoked before the task is started. Assumes context exists. */
    public abstract void actualOnPreExecute(@NonNull CTX context);


    final public void onPostExecute(TaskData result) {
        CTX context = mContext.get();
        if (context != null) {
            actualOnPostExecute(context, result);
        }
    }


    /**
     * Invoked after the task has completed.
     * <p>
     * The semantics of the result depends on the task itself.
     */
    public abstract void actualOnPostExecute(@NonNull CTX context, TaskData result);


    public void onCancelled() {
        CTX context = mContext.get();
        if (context != null) {
            actualOnCancelled(context);
        }
    }


    /** Assumes context exists. */
    public void actualOnCancelled(@NonNull CTX context) {
        // most implementations do nothing with this, provide them a default implementation
    }
}
