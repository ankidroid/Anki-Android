package com.ichi2.anki;import java.lang.reflect.Field;
import java.lang.Object;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.GregorianCalendar;

import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;
import com.ichi2.libanki.Collection;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class UIUtils {

	/** Returns the API level of this device. */
    public static int getApiLevel() {
        try {
            return Integer.parseInt(Build.VERSION.SDK);
        } catch (NumberFormatException e) {
            // If there is an error, return the minimum supported version.
            return 3;
        }
    }

    public static float getDensityAdjustedValue(Context context, float value) {
    	return context.getResources().getDisplayMetrics().density * value;
    }

    public static long getDayStart() {
		Calendar cal = GregorianCalendar.getInstance();
		if (cal.get(Calendar.HOUR_OF_DAY) < 4) {
			cal.roll(Calendar.DAY_OF_YEAR, -1);
		}
		cal.set(Calendar.HOUR_OF_DAY, 4);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTimeInMillis();
    }

    /**
     * Adds a menu item to the given menu.
     */
    public static MenuItem addMenuItem(Menu menu, int groupId, int itemId, int order, int titleRes,
            int iconRes) {
        MenuItem item = menu.add(groupId, itemId, order, titleRes);
        item.setIcon(iconRes);
        return item;
    }

    /**
     * Adds a menu item to the given menu and marks it as a candidate to be in the action bar.
     */
    public static MenuItem addMenuItemInActionBar(Menu menu, int groupId, int itemId, int order,
            int titleRes, int iconRes) {
        MenuItem item = addMenuItem(menu, groupId, itemId, order, titleRes, iconRes);
        setShowAsActionIfRoom(item);
        return item;
    }

    /**
     * Sets the menu item to appear in the action bar via reflection.
     * <p>
     * This method uses reflection so that it works on all platforms. It any error occurs, assume
     * the action bar is not available and just proceed.
     */
    private static void setShowAsActionIfRoom(MenuItem item) {
        try {
            Field showAsActionIfRoom = item.getClass().getField("SHOW_AS_ACTION_IF_ROOM");
            Method setShowAsAction = item.getClass().getMethod("setShowAsAction", int.class);
            setShowAsAction.invoke(item, showAsActionIfRoom.get(null));
        } catch (SecurityException e) {
        } catch (NoSuchMethodException e) {
        } catch (NoSuchFieldException e) {
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        } catch (NullPointerException e) {
        }
    }


    public static void setActionBarSubtitle(Context context, String text) {
        try {
        	Method getActionBar = context.getClass().getMethod("getActionBar");
        	if (getActionBar != null) {
        		Object o = getActionBar.invoke(context);
        		o.getClass().getMethod("setSubtitle", CharSequence.class).invoke(o, text);
        	}
        } catch (SecurityException e) {
        } catch (NoSuchMethodException e) {
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        } catch (NullPointerException e) {
        }
    }

    public static void saveCollectionInBackground(Collection col) {
		DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SAVE_COLLECTION, new DeckTask.TaskListener() {
			@Override
			public void onPreExecute() {
				Log.i(AnkiDroidApp.TAG, "saveCollectionInBackground: start");
			}
			@Override
			public void onPostExecute(TaskData result) {
				Log.i(AnkiDroidApp.TAG, "saveCollectionInBackground: finished");
			}
			@Override
			public void onProgressUpdate(TaskData... values) {
			}}, new DeckTask.TaskData(col));
    }
}
