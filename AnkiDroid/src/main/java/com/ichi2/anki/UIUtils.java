
package com.ichi2.anki;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.ichi2.async.CollectionTask;

import java.util.Calendar;

import timber.log.Timber;
import com.ichi2.async.TaskListener;
import com.ichi2.async.TaskManager;
import com.ichi2.libanki.utils.Time;

public class UIUtils {

    public static void showThemedToast(Context context, String text, boolean shortLength) {
        Toast.makeText(context, text, shortLength ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
    }
    public static void showThemedToast(Context context, CharSequence text, boolean shortLength) {
        UIUtils.showThemedToast(context, text.toString(), shortLength);
    }
    public static void showThemedToast(Context context, @StringRes int textResource, boolean shortLength) {
        Toast.makeText(context, textResource, shortLength ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
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
        Snackbar sb = getSnackbar(activity, mainText, length, actionTextResource, listener, root, callback);
        sb.show();

        return sb;
    }


    @NonNull
    public static Snackbar getSnackbar(Activity activity, String mainText, int length, int actionTextResource, View.OnClickListener listener, @NonNull View root, Snackbar.Callback callback) {
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
        return sb;
    }


    public static float getDensityAdjustedValue(Context context, float value) {
        return context.getResources().getDisplayMetrics().density * value;
    }


    public static long getDayStart(Time time) {
        Calendar cal = time.calendar();
        if (cal.get(Calendar.HOUR_OF_DAY) < 4) {
            cal.roll(Calendar.DAY_OF_YEAR, -1);
        }
        cal.set(Calendar.HOUR_OF_DAY, 4);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }


    public static void saveCollectionInBackground() {
        saveCollectionInBackground(false);
    }

    public static void saveCollectionInBackground(boolean syncIgnoresDatabaseModification) {
        if (CollectionHelper.getInstance().colIsOpen()) {
            TaskListener<Void, Void> listener = new TaskListener<Void, Void>() {
                @Override
                public void onPreExecute() {
                    Timber.d("saveCollectionInBackground: start");
                }


                @Override
                public void onPostExecute(Void v) {
                    Timber.d("saveCollectionInBackground: finished");
                }
            };
            TaskManager.launchCollectionTask(new CollectionTask.SaveCollection(syncIgnoresDatabaseModification), listener);
        }
    }
}
