/***************************************************************************************
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.content.Context;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class CustomExceptionHandler extends AbstractFeedbackElement implements Thread.UncaughtExceptionHandler {

    private static CustomExceptionHandler sInstance;
    private Thread.UncaughtExceptionHandler mPreviousHandler;


    static CustomExceptionHandler getInstance() {
        if (sInstance == null) {
            sInstance = new CustomExceptionHandler();
            Log.i(AnkiDroidApp.TAG, "New instance of custom exception handler");
        }

        return sInstance;
    }


    public void init(Context context) {
        mPreviousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }


    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Log.i(AnkiDroidApp.TAG, "uncaughtException");

        collectInformation();

        Date ts = new Date();
    	TimeZone tz = TimeZone.getDefault();
    	
        SimpleDateFormat df1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
        SimpleDateFormat df2 = new SimpleDateFormat("Z", Locale.US);
        
        df1.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        String reportgeneratedutc = String.format("%s", df1.format(ts));
        String reportgeneratedtzoffset = String.format("%s", df2.format(ts));
        String reportgeneratedtz = String.format("%s", tz.getID());
        
        StringBuilder reportInformation = new StringBuilder(10000);
        
        reportInformation.append(String.format("reportgeneratedutc=%s\n", reportgeneratedutc));
        reportInformation.append(String.format("reportgeneratedtzoffset=%s\n", reportgeneratedtzoffset));
        reportInformation.append(String.format("reportgeneratedtz=%s\n", reportgeneratedtz));
        
        for (String key : mInformation.keySet()) {
            String value = mInformation.get(key);

            reportInformation.append(String.format("%s=%s\n", key.toLowerCase(), value));
        }

        reportInformation.append("stacktrace=\nBegin Stacktrace\n");

        // Stack trace
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        e.printStackTrace(printWriter);
        reportInformation.append(String.format("%s\n", result.toString()));

        reportInformation.append("End Stacktrace\n\nBegin Inner exceptions\n");

        // Cause, inner exceptions
        Throwable cause = e.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            reportInformation.append(String.format("%s\n", result.toString()));
            cause = cause.getCause();
        }
        reportInformation.append("End Inner exceptions");

        printWriter.close();

        Log.i(AnkiDroidApp.TAG, "report infomation string created");
        createReport(reportInformation.toString());

        mPreviousHandler.uncaughtException(t, e);
    }
}