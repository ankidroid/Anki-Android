
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
    private Thread.UncaughtExceptionHandler PreviousHandler;
    private static CustomExceptionHandler instance;
    private Context curContext;
    private final static String TAG = "CustomExceptionHandler";
    // private Random randomGenerator = new Random();

    private HashMap<String, String> information = new HashMap<String, String>(20);


    static CustomExceptionHandler getInstance() {
        if (instance == null) {
            instance = new CustomExceptionHandler();
            Log.i(TAG, "New instance of custom exception handler");
        }

        return instance;
    }


    public void Init(Context context) {
        PreviousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        curContext = context;
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
        Log.i(TAG, "collectInformation");

        if (curContext == null) {
            return;
        }

        try {
            Log.i(TAG, "collecting information");

            PackageManager pm = curContext.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(curContext.getPackageName(), 0);

            information.put("VersionName", pi.versionName); // Version
            information.put("PackageName", pi.packageName);// Package name
            information.put("PhoneModel", android.os.Build.MODEL); // Device
                                                                   // model
            information.put("AndroidVersion", android.os.Build.VERSION.RELEASE);// Android
                                                                                // version
            information.put("Board", android.os.Build.BOARD);
            information.put("Brand", android.os.Build.BRAND);
            information.put("Device", android.os.Build.DEVICE);
            information.put("Display", android.os.Build.DISPLAY);
            information.put("FingerPrint", android.os.Build.FINGERPRINT);
            information.put("Host", android.os.Build.HOST);
            information.put("ID", android.os.Build.ID);
            information.put("Model", android.os.Build.MODEL);
            information.put("Product", android.os.Build.PRODUCT);
            information.put("Tags", android.os.Build.TAGS);
            information.put("Time", Long.toString(android.os.Build.TIME));
            information.put("Type", android.os.Build.TYPE);
            information.put("User", android.os.Build.USER);
            information.put("TotalInternalMemory", Long.toString(getTotalInternalMemorySize()));
            information.put("AvailableInternalMemory", Long.toString(getAvailableInternalMemorySize()));

            Log.i(TAG, "Information collected");
        } catch (Exception e) {
            Log.i(TAG, e.toString());
        }
    }


    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Log.i(TAG, "uncaughtException");

        collectInformation();
        Date currentDate = new Date();
        SimpleDateFormat df1 = new SimpleDateFormat("EEE MMM dd HH:mm:ss ", Locale.US);
        SimpleDateFormat df2 = new SimpleDateFormat(" yyyy", Locale.US);
        TimeZone tz = TimeZone.getDefault();
        StringBuilder reportInformation = new StringBuilder(10000);
        reportInformation.append(String.format("Report Generated: %s%s%s\nBegin Collected Information\n\n",
                df1.format(currentDate), tz.getID(), df2.format(currentDate)));

        for (String key : information.keySet()) {
            String value = information.get(key);

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

        Log.i(TAG, "report infomation string created");
        saveReportToFile(reportInformation.toString());

        PreviousHandler.uncaughtException(t, e);
    }


    private void saveReportToFile(String reportInformation) {
        try {
            Log.i(TAG, "saveReportFile");

            Date currentDate = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            String filename = String.format("ad-%s.stacktrace", formatter.format(currentDate));

            Log.i(TAG, "No external storage available");
            FileOutputStream trace = curContext.openFileOutput(filename, Context.MODE_PRIVATE);
            trace.write(reportInformation.getBytes());
            trace.close();

            Log.i(TAG, "report saved");
        } catch (Exception e) {
            Log.i(TAG, e.toString());
        }
    }
}
