/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 *                                                                                      *
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
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.ichi2.async.Connection;
import com.ichi2.libanki.hooks.Hooks;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Application class. This file mainly contains Veecheck stuff.
 */
public class AnkiDroidApp extends Application {
	
	public static final String LIBANKI_VERSION = "1.2.5";
	public static final String DROPBOX_PUBLIC_DIR = "/dropbox/Public/Anki";

	public static final int RESULT_TO_HOME = 501;

    /**
     * Tag for logging messages.
     */
    public static final String TAG = "AnkiDroid";

	public static final String COLLECTION_PATH = "/collection.anki2";

    /**
     * Singleton instance of this class.
     */
    private static AnkiDroidApp sInstance;
    private static Typeface     mTibTypeface;
    private static boolean bTibetan;

    /**
     * Global hooks
     */
    private Hooks mHooks;

    /**
     * On application creation.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        sInstance.mHooks = new Hooks();

        Connection.setContext(getApplicationContext());

        // Error Reporter
        CustomExceptionHandler customExceptionHandler = CustomExceptionHandler.getInstance();
        customExceptionHandler.init(sInstance.getApplicationContext());
        Thread.setDefaultUncaughtExceptionHandler(customExceptionHandler);

        SharedPreferences preferences = PrefSettings.getSharedPrefs(this);
        // Assign some default settings if necessary
        if (preferences.getString(PrefSettings.KEY_CHECK_URI, null) == null) {
            Editor editor = preferences.edit();
            // Test Update Notifications
            // Some ridiculously fast polling, just to demonstrate it working...
            /*
             * editor.putBoolean(PrefSettings.KEY_ENABLED, true); editor.putLong(PrefSettings.KEY_PERIOD, 30 * 1000L);
             * editor.putLong(PrefSettings.KEY_CHECK_INTERVAL, 60 * 1000L); editor.putString(PrefSettings.KEY_CHECK_URI,
             * "http://ankidroid.googlecode.com/files/test_notifications.xml");
             */
            editor.putString(PrefSettings.KEY_CHECK_URI, "http://ankidroid.googlecode.com/files/last_release.xml");

            // Create the folder "AnkiDroid", if not exists, where the decks
            // will be stored by default
            String deckPath = getDefaultAnkiDroidDirectory();
            createDirectoryIfMissing(new File(deckPath));

            // Put the base path in preferences pointing to the default
            // "AnkiDroid" folder
            editor.putString("deckPath", deckPath);

            // Using commit instead of apply even though we don't need a return value.
            // Reason: apply() not available on Android 1.5
            editor.commit();
        }
        
        
        

        // Reschedule the checks - we need to do this if the settings have
        // changed (as above)
        // It may also necessary in the case where an application has been
        // updated
        // Here for simplicity, we do it every time the application is launched
        // Intent intent = new Intent(Veecheck.getRescheduleAction(this));
        // sendBroadcast(intent);
    }
    
    public static boolean isTibetan() {
    	
    	SharedPreferences preferences = PrefSettings.getSharedPrefs(getInstance());
    	
    	//check for Tibetan support & Typeface initialisation
        if (preferences.getBoolean("enableTibetan", false)) {
			bTibetan = true;
		} else {
			bTibetan = false;
		}
        
    	
    	if (bTibetan && mTibTypeface == null) {
        	String fileName = "/mnt/sdcard/fonts/DDC_Uchen.ttf";
//        	mTibTypeface = Typeface.createFromAsset(getInstance().getAssets(), fileName);
        	File mTibFontFile = new File(fileName);
        	if ( mTibFontFile.exists()) {
        		mTibTypeface = Typeface.createFromFile(fileName);
        	} else {
        		return false;
        	}
        	
        	
        }
    	
    	return bTibetan;
    }
    
    public static Typeface getTibetanTypeface() {
    	return mTibTypeface;
    }


    public static AnkiDroidApp getInstance() {
        return sInstance;
    }


    public static String getStorageDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }


    public static String getCollectionPath() {
		String deckPath = PrefSettings.getSharedPrefs(sInstance.getApplicationContext()).getString("deckPath", AnkiDroidApp.getDefaultAnkiDroidDirectory());
		return deckPath + AnkiDroidApp.COLLECTION_PATH;
	}

    public static String getDefaultAnkiDroidDirectory() {
        return getStorageDirectory() + "/AnkiDroid";
    }

    public static void createDirectoryIfMissing(File decksDirectory) {
    	if (!decksDirectory.isDirectory()) {
    		decksDirectory.mkdirs();
        }
    	try {
			new File(decksDirectory.getAbsolutePath() + "/.nomedia").createNewFile();
		} catch (IOException e) {
			Log.e(AnkiDroidApp.TAG, "Nomedia file could not be created");
		}
		createNoMediaFileIfMissing(decksDirectory);
    }


    public static void createNoMediaFileIfMissing(File decksDirectory) {
    	File mediaFile = new File(decksDirectory.getAbsolutePath() + "/.nomedia");
    	if (!mediaFile.exists()) {
        	try {
        		mediaFile.createNewFile();
    		} catch (IOException e) {
    			Log.e(AnkiDroidApp.TAG, "Nomedia file could not be created in path " + decksDirectory.getAbsolutePath());
    		}    		
    	}
    }


    public static Resources getAppResources() {
        return sInstance.getResources();
    }


    public static boolean isSdCardMounted() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }


    public static int getDisplayHeight() {
        Display display = ((WindowManager) sInstance.getApplicationContext().getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        return display.getHeight();
    }


    public static int getDisplayWidth() {
        Display display = ((WindowManager) sInstance.getApplicationContext().getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        return display.getWidth();
    }


    /**
     * Get package name as defined in the manifest.
     * @return the package name.
     */
    public static String getPkgName() {
        String pkgName = TAG;
        Context context = sInstance.getApplicationContext();

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            pkgName = context.getString(pInfo.applicationInfo.labelRes);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Couldn't find package named " + context.getPackageName(), e);
        }

        return pkgName;
    }


    /**
     * Get the package version as defined in the manifest.
     * @return the package version.
     */
    public static String getPkgVersion() {
        String pkgVersion = "?";
        Context context = sInstance.getApplicationContext();

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            pkgVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Couldn't find package named " + context.getPackageName(), e);
        }

        return pkgVersion;
    }
    
    /**
     * Get the DropBox folder
     * @return the absolute path to the DropBox public folder, or null if it is not found
     */
    public static String getDropboxDir() {
        File f = new File(AnkiDroidApp.getStorageDirectory() + DROPBOX_PUBLIC_DIR);
        if (f.exists() && f.isDirectory()) {
            return f.getAbsolutePath();
        }
        return null;
    }

    public static void saveExceptionReportFile(Throwable e, String origin) {
    	CustomExceptionHandler.getInstance().uncaughtException(null, e, origin);
    }

    public static boolean isDonutOrLater() {
        return getSdkVersion() > 3;
    }

    public static boolean isEclairOrLater() {
        return getSdkVersion() > 4;
    }

    public static boolean isFroyoOrLater() {
        return getSdkVersion() > 7;
    }

    public static boolean isHoneycombOrLater() {
        return getSdkVersion() > 11;
    }

    public static int getSdkVersion() {
        return Integer.valueOf(android.os.Build.VERSION.SDK);
    }


	public void setLanguage(String language) {
		Locale locale;
		if (language.equals("")) {
			locale = Locale.getDefault();
		} else {
			locale = new Locale(language);
		}
		Configuration config = new Configuration();
		config.locale = locale;
		this.getResources().updateConfiguration(config,
				this.getResources().getDisplayMetrics());
	}

    public static Hooks getHooks() {
        return sInstance.mHooks;
    }
}
