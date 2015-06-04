
package com.ichi2.anki;

import android.app.Activity;
import android.content.Context;

import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.view.WindowManager;

import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;
import com.ichi2.themes.Themes;

import java.util.Calendar;

import timber.log.Timber;

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



    public static void saveCollectionInBackground(Context context) {
        if (CollectionHelper.getInstance().colIsOpen()) {
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SAVE_COLLECTION, new DeckTask.TaskListener() {
                @Override
                public void onPreExecute() {
                    Timber.d("saveCollectionInBackground: start");
                }


                @Override
                public void onPostExecute(TaskData result) {
                    Timber.d("saveCollectionInBackground: finished");
                }


                @Override
                public void onProgressUpdate(TaskData... values) {
                }


                @Override
                public void onCancelled() {
                }
            }, new DeckTask.TaskData(CollectionHelper.getInstance().getCol(context)));
        }
    }


    public static void setFullScreen(Activity activity) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }


    public static void setTitle(Activity activity, String title) {
        AppCompatActivity actionBarActivity = (AppCompatActivity) activity;
        ActionBar actionBar = actionBarActivity.getSupportActionBar();
        if (actionBar != null) {
            CharacterStyle span = new ForegroundColorSpan(activity.getResources().getColor(
                    Themes.getResourceIdFromAttributeId(R.attr.actionBarTextColor)));
            SpannableStringBuilder ssb = new SpannableStringBuilder(title);// Is it even necessary to use spannables anymore?
            ssb.setSpan(span, 0, ssb.length(), 0);
            actionBar.setTitle(ssb);
        }
    }


    public static void setSubtitle(Activity activity, String title) {
        setSubtitle(activity, title, false);
    }


    public static void setSubtitle(Activity activity, String title, boolean inverted) {
        AppCompatActivity actionBarActivity = (AppCompatActivity) activity;
        ActionBar actionBar = actionBarActivity.getSupportActionBar();
        if (actionBar != null) {
            if (inverted) {
                CharacterStyle span = new ForegroundColorSpan(activity.getResources().getColor(
                        Themes.getResourceIdFromAttributeId(R.attr.actionBarTextColor)));
                SpannableStringBuilder ssb = new SpannableStringBuilder(title);
                ssb.setSpan(span, 0, ssb.length(), 0);
                actionBar.setSubtitle(ssb);
            } else {
                actionBar.setSubtitle(title);
            }
        }
    }
}
