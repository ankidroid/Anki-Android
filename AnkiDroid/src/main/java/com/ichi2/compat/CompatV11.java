
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.TaskStackBuilder;

import com.ichi2.anki.AnkiActivity;

import timber.log.Timber;

/** Implementation of {@link Compat} for SDK level 11 (Honeycomb) */
@TargetApi(11)
public class CompatV11 extends CompatV10 implements Compat {

    /**
     * Restart the activity and discard old backstack, creating it new from the heirarchy in the manifest
     */
    public void restartActivityInvalidateBackstack(AnkiActivity activity) {
        Timber.i("AnkiActivity -- restartActivityInvalidateBackstack()");
        Intent intent = new Intent();
        intent.setClass(activity, activity.getClass());
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(activity);
        stackBuilder.addNextIntentWithParentStack(intent);
        stackBuilder.startActivities(new Bundle());
        activity.finishWithoutAnimation();
    }

    @Override
    public void disableDatabaseWriteAheadLogging(SQLiteDatabase db) {
        // disableWriteAheadLogging() method only available from API 16
        db.rawQuery("PRAGMA journal_mode = DELETE", null);
    }

    public boolean isWriteAheadLoggingEnabled(SQLiteDatabase db) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("pragma journal_mode", null);
            if (!cursor.moveToNext()) {
                throw new SQLException("No result for query: pragma journal_mode");
            }
            return cursor.getString(0).toLowerCase().equals("wal");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
