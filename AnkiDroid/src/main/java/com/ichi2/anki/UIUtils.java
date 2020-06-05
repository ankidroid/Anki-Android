
package com.ichi2.anki;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.CollectionTask.TaskData;

import java.util.Calendar;

import timber.log.Timber;

public class UIUtils {

    public static void showThemedToast(Context context, String text, boolean shortLength) {
        Toast.makeText(context, text, shortLength ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
    }


    /**
     * Show a simple Toast-like Snackbar with no actions.
     * To enable swipe-to-dismiss, the Activity layout should include a CoordinatorLayout with id "root_layout"
     */
    public static Snackbar showSimpleSnackbar(Activity activity, int mainTextResource, boolean shortLength) {
        View root = activity.findViewById(R.id.root_layout);
        return showSnackbar(activity, mainTextResource, shortLength, -1, null, root);
    }
    public static Snackbar showSimpleSnackbar(Activity activity, String mainText, boolean shortLength) {
        View root = activity.findViewById(R.id.root_layout);
        return showSnackbar(activity, mainText, shortLength, -1, null, root, null);
    }

    /**
     * Show a snackbar with an action
     * @param mainTextResource resource for the main text string
     * @param shortLength whether or not to use long length
     * @param actionTextResource resource for the text string shown as the action
     * @param listener listener for the action (if null no action shown)
     * @param root View Snackbar will attach to. Should be CoordinatorLayout for swipe-to-dismiss to work.
     * @return Snackbar object
     */
    public static Snackbar showSnackbar(Activity activity, int mainTextResource, boolean shortLength,
                                int actionTextResource, View.OnClickListener listener, View root) {
        return showSnackbar(activity, mainTextResource,shortLength,actionTextResource,listener,root, null);
    }


    public static Snackbar showSnackbar(Activity activity, int mainTextResource, boolean shortLength,
                                int actionTextResource, View.OnClickListener listener, View root,
                                Snackbar.Callback callback) {
        String mainText = activity.getResources().getString(mainTextResource);
        return showSnackbar(activity, mainText, shortLength, actionTextResource, listener, root, callback);
    }

    public static Snackbar showSnackbar(Activity activity, String mainText, boolean shortLength,
                                        int actionTextResource, View.OnClickListener listener, View root,
                                        Snackbar.Callback callback) {
        return showSnackbar(activity, mainText, shortLength ? Snackbar.LENGTH_SHORT : Snackbar.LENGTH_LONG, actionTextResource, listener, root, callback);
    }

    public static Snackbar showSnackbar(Activity activity, String mainText, int length,
                                int actionTextResource, View.OnClickListener listener, View root,
                                Snackbar.Callback callback) {
        if (root == null) {
            root = activity.findViewById(android.R.id.content);
            if (root == null) {
                Timber.e("Could not show Snackbar due to null View");
                return null;
            }
        }
        Snackbar sb = Snackbar.make(root, mainText, length);
        if (listener != null) {
            sb.setAction(actionTextResource, listener);
        }
        if (callback != null) {
            sb.addCallback(callback);
        }
        // Make the text white to avoid interference from our theme colors.
        View view = sb.getView();
        TextView tv = view.findViewById(com.google.android.material.R.id.snackbar_text);
        TextView action = view.findViewById(com.google.android.material.R.id.snackbar_action);
        if (tv != null && action != null) {
            tv.setTextColor(Color.WHITE);
            action.setTextColor(ContextCompat.getColor(activity, R.color.material_light_blue_500));
            tv.setMaxLines(2);  // prevent tablets from truncating to 1 line
        }
        sb.show();

        return sb;
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
            CollectionTask.launchCollectionTask(CollectionTask.TASK_TYPE_SAVE_COLLECTION, new CollectionTask.TaskListener() {
                @Override
                public void onPreExecute() {
                    Timber.d("saveCollectionInBackground: start");
                }


                @Override
                public void onPostExecute(TaskData result) {
                    Timber.d("saveCollectionInBackground: finished");
                }
            });
        }
    }
}
