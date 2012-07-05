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

import com.ichi2.anki.R;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

public class CustomExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static CustomExceptionHandler sInstance;
    private Thread.UncaughtExceptionHandler mPreviousHandler;
    private Context mCurContext;
    // private Random randomGenerator = new Random();

    private HashMap<String, String> mInformation = new HashMap<String, String>(20);


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
        mCurContext = context;
    }


    private long getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }


    private long getTotalInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
        return totalBlocks * blockSize;
    }


    private void collectInformation() {
        Log.i(AnkiDroidApp.TAG, "collectInformation");

        if (mCurContext == null) {
            return;
        }

        try {
            Log.i(AnkiDroidApp.TAG, "collecting information");

            PackageManager pm = mCurContext.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(mCurContext.getPackageName(), 0);

            mInformation.put("VersionName", pi.versionName); // Version
            mInformation.put("PackageName", pi.packageName); // Package name
            mInformation.put("AndroidVersion", android.os.Build.VERSION.RELEASE); // Android version
            mInformation.put("Board", android.os.Build.BOARD);
            mInformation.put("Brand", android.os.Build.BRAND);
            mInformation.put("Device", android.os.Build.DEVICE);
            mInformation.put("Display", android.os.Build.DISPLAY);
            // mInformation.put("FingerPrint", android.os.Build.FINGERPRINT);
            mInformation.put("Host", android.os.Build.HOST);
            mInformation.put("ID", android.os.Build.ID);
            mInformation.put("Model", android.os.Build.MODEL);
            mInformation.put("Product", android.os.Build.PRODUCT);
            // mInformation.put("Tags", android.os.Build.TAGS);
            mInformation.put("Time", Long.toString(android.os.Build.TIME));
            // mInformation.put("Type", android.os.Build.TYPE);
            // mInformation.put("User", android.os.Build.USER);
            mInformation.put("TotalInternalMemory", Long.toString(getTotalInternalMemorySize()));
            mInformation.put("AvailableInternalMemory", Long.toString(getAvailableInternalMemorySize()));
            mInformation.put("Locale", AnkiDroidApp.getAppResources().getConfiguration().locale.toString());
            Log.i(AnkiDroidApp.TAG, "Information collected");
        } catch (Exception e) {
            Log.i(AnkiDroidApp.TAG, e.toString());
        }
    }


    @Override
    public void uncaughtException(Thread t, Throwable e) {
        uncaughtException(t, e, null);
    }


    public void uncaughtException(Thread t, Throwable e, String origin) {
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

        if (origin != null && origin.length() > 0) {
            reportInformation.append(String.format("origin=%s\n", origin));
        }

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
        saveReportToFile(reportInformation.toString());

        if (t != null) {
            mPreviousHandler.uncaughtException(t, e);
        }
    }


    private void saveReportToFile(String reportInformation) {
        try {
            Log.i(AnkiDroidApp.TAG, "saveReportFile");

            Date currentDate = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            String filename = String.format("ad-%s.stacktrace", formatter.format(currentDate));

            Log.i(AnkiDroidApp.TAG, "No external storage available");
            FileOutputStream trace = mCurContext.openFileOutput(filename, Context.MODE_PRIVATE);
            trace.write(reportInformation.getBytes());
            trace.close();

            Log.i(AnkiDroidApp.TAG, "report saved");
        } catch (Exception e) {
            Log.i(AnkiDroidApp.TAG, e.toString());
        }
    }
}
