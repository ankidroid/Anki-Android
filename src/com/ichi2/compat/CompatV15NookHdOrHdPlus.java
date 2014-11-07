
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.database.sqlite.SQLiteDatabase;

/**
 * Implementation of {@link Compat} for SDK level 15 for Nook HD Plus.
 * <p>
 * This device actually supports disabling WAL via this hidden API and not doing so seems to create issues on this
 * device.
 */
@TargetApi(16)
public class CompatV15NookHdOrHdPlus extends CompatV15 implements Compat {

    @Override
    public void disableDatabaseWriteAheadLogging(SQLiteDatabase db) {
        db.disableWriteAheadLogging();
    }

}
