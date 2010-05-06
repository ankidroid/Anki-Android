package com.ichi2.anki;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public class CustomExceptionHandler implements Thread.UncaughtExceptionHandler {
	private Thread.UncaughtExceptionHandler PreviousHandler;
	private static CustomExceptionHandler instance;
	private Context curContext;
	private final static String TAG = "CustomExceptionHandler";
	private Random randomGenerator = new Random();
	
	private HashMap<String, String> information = new HashMap<String, String>(
			20);

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

		if (curContext == null)
			return;

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
			information.put("TotalInternalMemory", Long
					.toString(getTotalInternalMemorySize()));
			information.put("AvailableInternalMemory", Long
					.toString(getAvailableInternalMemorySize()));

			Log.i(TAG, "Information collected");
		} catch (Exception e) {
			Log.i(TAG, e.toString());
		}
	}

	public void uncaughtException(Thread t, Throwable e) {
		Log.i(TAG, "uncaughtException");

		collectInformation();
		Date currentDate = new Date();

		StringBuilder reportInformation = new StringBuilder(10000);
		reportInformation.append(String.format(
				"Report Generated: %s\nBegin Collected Information\n\n",
				currentDate.toString()));

		for (String key : information.keySet()) {
			String value = information.get(key);

			reportInformation.append(String.format("%s = %s\n", key, value));
		}

		reportInformation.append(String
				.format("End Collected Information\n\nBegin Stacktrace\n\n"));

		// Stack trace
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		e.printStackTrace(printWriter);
		reportInformation.append(String.format("%s\n", result.toString()));

		reportInformation.append(String
				.format("End Stacktrace\n\nBegin Inner exceptions\n\n"));

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

			int random = randomGenerator.nextInt(99999);
			String filename = String.format("ad-%d.stacktrace", random);
			
			if(hasStorage(true)) {
				Log.i(TAG, "External storage available");
				File f = new File(Environment.getExternalStorageDirectory().toString(), filename);
				Log.i(TAG, String.format("Writing to: %s", f.getAbsoluteFile()));
				
				f.createNewFile();
				FileOutputStream fw = new FileOutputStream(f);
				fw.write(reportInformation.getBytes());
				fw.close();
			}
			else {
				Log.i(TAG, "No external storage available");
				FileOutputStream trace = curContext.openFileOutput(filename, Context.MODE_PRIVATE);
				trace.write(reportInformation.getBytes());
				trace.close();
			}

			Log.i(TAG, "report saved");
		} catch (Exception e) {
			Log.i(TAG, e.toString());
		}
	}

	static private boolean checkFsWritable() {
		String directoryName = Environment.getExternalStorageDirectory().toString();

		File directory = new File(directoryName);

		if (!directory.isDirectory()) {
			if (!directory.mkdirs()) {
				return false;
			}
		}

		File f = new File(directoryName, ".probe");

		try {
			// Remove stale file if any
			if (f.exists()) {
				f.delete();
			}

			if (!f.createNewFile())
				return false;

			f.delete();
			return true;

		} catch (IOException ex) {
			return false;
		}

	}

	static public boolean hasStorage(boolean requireWriteAccess) {
		String state = Environment.getExternalStorageState();
		Log.i(TAG, "storage state is " + state);

		if (Environment.MEDIA_MOUNTED.equals(state)) {
			if (requireWriteAccess) {
				boolean writable = checkFsWritable();
				Log.i(TAG, "storage writable is " + writable);
				return writable;
			} else {
				return true;
			}
		} else if (!requireWriteAccess && Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			return true;
		}
		return false;
	}
}
