
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Html;
import android.util.TypedValue;
import android.widget.RemoteViews;

import com.ichi2.anki.R;

/** Implementation of {@link Compat} for SDK level 16 */
@TargetApi(16)
public class CompatV16 extends CompatV15 implements Compat {

    @Override
    public void disableDatabaseWriteAheadLogging(SQLiteDatabase db) {
        db.disableWriteAheadLogging();
    }

    /*
     *  Return the input string in a form suitable for display on a HTML page.
     *
     * @param txt Text to be cleaned.
     * @return The input text, HTML-escpaped
    */
    @Override
    public String detagged(String txt) {
        return Html.escapeHtml(txt);
    }

    @Override
    public void adjustSmallWidgetDimensions(RemoteViews views, int id, int left, int top, int right, int bottom, float ts) {
        views.setTextViewTextSize(R.id.widget_due, TypedValue.COMPLEX_UNIT_SP, ts);
        views.setTextViewTextSize(R.id.widget_eta, TypedValue.COMPLEX_UNIT_SP, ts);
        views.setViewPadding(R.id.ankidroid_widget_text_layout, left, top, right, bottom);
    }

    @Override
    public int[] getWidgetDimensions(AppWidgetManager manager, int id) {
        Bundle options = manager.getAppWidgetOptions(id);
        int[] dim = new int[4];
        dim[0] = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        dim[1] = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
        dim[2] = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        dim[3] = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        return dim;
    }

}
