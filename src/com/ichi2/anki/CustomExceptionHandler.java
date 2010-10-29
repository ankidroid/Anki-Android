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


    public void Init(Context context) {
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
            mInformation.put("PackageName", pi.packageName);// Package name
            mInformation.put("PhoneModel", android.os.Build.MODEL); // Device
                                                                   // model
            mInformation.put("AndroidVersion", android.os.Build.VERSION.RELEASE);// Android
                                                                                // version
            mInformation.put("Board", android.os.Build.BOARD);
            mInformation.put("Brand", android.os.Build.BRAND);
            mInformation.put("Device", android.os.Build.DEVICE);
            mInformation.put("Display", android.os.Build.DISPLAY);
            mInformation.put("FingerPrint", android.os.Build.FINGERPRINT);
            mInformation.put("Host", android.os.Build.HOST);
            mInformation.put("ID", android.os.Build.ID);
            mInformation.put("Model", android.os.Build.MODEL);
            mInformation.put("Product", android.os.Build.PRODUCT);
            mInformation.put("Tags", android.os.Build.TAGS);
            mInformation.put("Time", Long.toString(android.os.Build.TIME));
            mInformation.put("Type", android.os.Build.TYPE);
            mInformation.put("User", android.os.Build.USER);
            mInformation.put("TotalInternalMemory", Long.toString(getTotalInternalMemorySize()));
            mInformation.put("AvailableInternalMemory", Long.toString(getAvailableInternalMemorySize()));

            Log.i(AnkiDroidApp.TAG, "Information collected");
        } catch (Exception e) {
            Log.i(AnkiDroidApp.TAG, e.toString());
        }
    }


    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Log.i(AnkiDroidApp.TAG, "uncaughtException");

        collectInformation();
        Date currentDate = new Date();
        SimpleDateFormat df1 = new SimpleDateFormat("EEE MMM dd HH:mm:ss ", Locale.US);
        SimpleDateFormat df2 = new SimpleDateFormat(" yyyy", Locale.US);
        TimeZone tz = TimeZone.getDefault();
        StringBuilder reportInformation = new StringBuilder(10000);
        reportInformation.append(String.format("Report Generated: %s%s%s\nBegin Collected Information\n\n",
                df1.format(currentDate), tz.getID(), df2.format(currentDate)));

        for (String key : mInformation.keySet()) {
            String value = mInformation.get(key);

            reportInformation.append(String.format("%s = %s\n", key, value));
        }

        reportInformation.append(String.format("End Collected Information\n\nBegin Stacktrace\n\n"));

        // Stack trace
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        e.printStackTrace(printWriter);
        reportInformation.append(String.format("%s\n", result.toString()));

        reportInformation.append(String.format("End Stacktrace\n\nBegin Inner exceptions\n\n"));

        // Cause, inner exceptions
        Throwable cause = e.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            reportInformation.append(String.format("%s\n", result.toString()));
            cause = cause.getCause();
        }

        reportInformation.append(String.format("End Inner exceptions"));

        printWriter.close();

        Log.i(AnkiDroidApp.TAG, "report infomation string created");
        saveReportToFile(reportInformation.toString());

        mPreviousHandler.uncaughtException(t, e);
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
