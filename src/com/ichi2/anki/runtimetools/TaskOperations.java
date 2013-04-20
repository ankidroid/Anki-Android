package com.ichi2.anki.runtimetools;

import android.os.AsyncTask;
import android.os.AsyncTask.Status;

public class TaskOperations
{

    /**
     * @param t
     * 
     * Gently killing AsyncTask
     * 
     */
    public static void stopTaskGracefully(AsyncTask<?, ?, ?> t)
    {
        if (t != null)
        {
            if (t.getStatus() == Status.RUNNING)
            {
                t.cancel(true);
            }
        }
    }

}
