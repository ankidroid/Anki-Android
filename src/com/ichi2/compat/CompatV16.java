
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.database.sqlite.SQLiteDatabase;
import android.text.Html;

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


}
