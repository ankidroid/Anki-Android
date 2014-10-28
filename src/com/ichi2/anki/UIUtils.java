
package com.ichi2.anki;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.WindowManager;

import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;

import java.util.Calendar;

public class UIUtils {

    public static float getDensityAdjustedValue(Context context, float value) {
        return context.getResources().getDisplayMetrics().density * value;
    }


    public static long getDayStart() {
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.HOUR_OF_DAY) < 4) {
            cal.roll(Calendar.DAY_OF_YEAR, -1);
        }
        cal.set(Calendar.HOUR_OF_DAY, 4);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }


    public static void closeCollectionInBackground() {
        // note: this code used to be called in the onStop() method of DeckPicker
        // https://github.com/ankidroid/Anki-Android/blob/d7023159b3599d07e18c308fdaa4bb8f8935fd1d/src/com/ichi2/anki/DeckPicker.java#L1206
        // it's currently not being used anywhere, in favor of letting the Android kernel automatically close the
        // collection when it kills the process
        if (AnkiDroidApp.colIsOpen()) {
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CLOSE_DECK, new DeckTask.TaskListener() {
                @Override
                public void onPreExecute() {
                    // Log.i(AnkiDroidApp.TAG, "closeCollectionInBackground: start");
                }


                @Override
                public void onPostExecute(TaskData result) {
                    // Log.i(AnkiDroidApp.TAG, "closesCollectionInBackground: finished");
                }


                @Override
                public void onProgressUpdate(TaskData... values) {
                }


                @Override
                public void onCancelled() {
                }
            }, new DeckTask.TaskData(AnkiDroidApp.getCol()));
        }
    }


    public static void saveCollectionInBackground() {
        if (AnkiDroidApp.colIsOpen()) {
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SAVE_COLLECTION, new DeckTask.TaskListener() {
                @Override
                public void onPreExecute() {
                    // Log.i(AnkiDroidApp.TAG, "saveCollectionInBackground: start");
                }


                @Override
                public void onPostExecute(TaskData result) {
                    // Log.i(AnkiDroidApp.TAG, "saveCollectionInBackground: finished");
                }


                @Override
                public void onProgressUpdate(TaskData... values) {
                }


                @Override
                public void onCancelled() {
                }
            }, new DeckTask.TaskData(AnkiDroidApp.getCol()));
        }
    }


    public static void setFullScreen(Activity activity) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

}
