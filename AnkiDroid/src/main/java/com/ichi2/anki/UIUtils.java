
package com.ichi2.anki;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;

import java.util.Calendar;

import timber.log.Timber;

public class UIUtils {

    public static void showThemedToast(Context context, String text, boolean shortLength) {
        Toast.makeText(context, text, shortLength ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
    }


    /**
     * Show a simple Toast-like Snackbar with no actions.
     * To enable swipe-to-dismiss, the Activity layout should include a CoordinatorLayout with id "root_layout"
     * @param mainTextResource
     * @param shortLength
     */
    public static void showSimpleSnackbar(Activity activity, int mainTextResource, boolean shortLength) {
        View root = activity.findViewById(R.id.root_layout);
        showSnackbar(activity, mainTextResource, shortLength, -1, null, root);
    }
    public static void showSimpleSnackbar(Activity activity, String mainText, boolean shortLength) {
        View root = activity.findViewById(R.id.root_layout);
        showSnackbar(activity, mainText, shortLength, -1, null, root, null);
    }

    /**
     * Show a snackbar with an action
     * @param mainTextResource resource for the main text string
     * @param shortLength whether or not to use long length
     * @param actionTextResource resource for the text string shown as the action
     * @param listener listener for the action (if null no action shown)
     * @oaram root View Snackbar will attach to. Should be CoordinatorLayout for swipe-to-dismiss to work.
     */
    public static void showSnackbar(Activity activity, int mainTextResource, boolean shortLength,
                                int actionTextResource, View.OnClickListener listener, View root) {
        showSnackbar(activity, mainTextResource,shortLength,actionTextResource,listener,root, null);
    }


    public static void showSnackbar(Activity activity, int mainTextResource, boolean shortLength,
                                int actionTextResource, View.OnClickListener listener, View root,
                                Snackbar.Callback callback) {
        String mainText = activity.getResources().getString(mainTextResource);
        showSnackbar(activity, mainText, shortLength, actionTextResource, listener, root, callback);
    }


    public static void showSnackbar(Activity activity, String mainText, boolean shortLength,
                                int actionTextResource, View.OnClickListener listener, View root,
                                Snackbar.Callback callback) {
        if (root == null) {
            root = activity.findViewById(android.R.id.content);
            if (root == null) {
                Timber.e("Could not show Snackbar due to null View");
                return;
            }
        }
        int length = shortLength ? Snackbar.LENGTH_SHORT : Snackbar.LENGTH_LONG;
        Snackbar sb = Snackbar.make(root, mainText, length);
        if (listener != null) {
            sb.setAction(actionTextResource, listener);
        }
        if (callback != null) {
            sb.setCallback(callback);
        }
        // Make the text white to avoid interference from our theme colors.
        View view = sb.getView();
        TextView tv = (TextView) view.findViewById(android.support.design.R.id.snackbar_text);
        TextView action = (TextView) view.findViewById(android.support.design.R.id.snackbar_action);
        if (tv != null && action != null) {
            tv.setTextColor(Color.WHITE);
            action.setTextColor(ContextCompat.getColor(activity, R.color.material_light_blue_500));
            tv.setMaxLines(2);  // prevent tablets from truncating to 1 line
        }
        sb.show();
    }


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
            });
        }
    }
}
