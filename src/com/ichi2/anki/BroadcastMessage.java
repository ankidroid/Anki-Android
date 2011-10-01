package com.ichi2.anki;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import com.ichi2.themes.StyledDialog;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.WindowManager.BadTokenException;

public class BroadcastMessage {

	public static final String FILE_URL = "https://ankidroid.googlecode.com/files/broadcastMessage.txt";

	public static final String NUM = "num: ";
	public static final String NEW = "new: ";
	public static final String MIN_VERSION = "minVer: ";
	public static final String MAX_VERSION = "maxVer: ";
	public static final String TITLE = "title: ";
	public static final String TEXT = "text: ";
	public static final String URL = "url: ";

	private static final int TIMEOUT = 30000;

	public static void checkForNewMessage(Context context, long lastTimeOpened) {
		SharedPreferences prefs = PrefSettings.getSharedPrefs(context);
		if (!prefs.getBoolean("showBroadcastMessages", true) || !Utils.isNewDay(lastTimeOpened)) {
			return;
		}

        AsyncTask<Context,Void,Context> checkForNewMessage = new DownloadBroadcastMessage();
        checkForNewMessage.execute(context);
	}


	private static int compareVersions(String ver1, String ver2) {
		String[] version1 = ver1.replaceAll("([:alpha:])([:digit:])", "$1.$2").replaceAll("([:digit:])([:alpha:])", "$1.$2").split("\\.");
		String[] version2 = ver2.replaceAll("([:alpha:])([:digit:])", "$1.$2").replaceAll("([:digit:])([:alpha:])", "$1.$2").split("\\.");
		for (int i = 0; i < Math.min(version1.length, version2.length); i++) {
			int com = 0;
			try {
				com = Integer.valueOf(version1[i]).compareTo(Integer.valueOf(version2[i]));
			} catch (NumberFormatException e) {
				com = version1[i].compareToIgnoreCase(version2[i]);
			}
			if (com != 0) {
				return com;
			}
		}
		return 0;
	}


    private static class DownloadBroadcastMessage extends AsyncTask<Context, Void, Context> {

    	private static int mNew;
    	private static int mNum;
    	private static String mMinVersion;
    	private static String mMaxVersion;
    	private static String mTitle;
    	private static String mText;
    	private static String mUrl;

    	private static Context mContext;

    	private static boolean mShowDialog = false;

        @Override
        protected Context doInBackground(Context... params) {
            Log.d(AnkiDroidApp.TAG, "BroadcastMessage.DownloadBroadcastMessage.doInBackground()");

            Context context = params[0];
            mContext = context;

    		SharedPreferences prefs = PrefSettings.getSharedPrefs(context);
    		try {
        		Log.i(AnkiDroidApp.TAG, "BroadcastMessage: download file " + FILE_URL);
    			URL fileUrl;
    			fileUrl = new URL(FILE_URL);
    			URLConnection conn = fileUrl.openConnection();
    			conn.setConnectTimeout(TIMEOUT);
    			conn.setReadTimeout(TIMEOUT);
    			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    			String inputLine;
    			while ((inputLine = in.readLine()) != null) {
    				if (inputLine.length() == 0 || inputLine.startsWith("#")) {
    					continue;
    				} else if (inputLine.startsWith(NEW)) {
    					mNew = Integer.parseInt(inputLine.substring(NEW.length()));
    				} else if (inputLine.startsWith(NUM)) {
    					mNum = Integer.parseInt(inputLine.substring(NUM.length()));
    					int lastNum = prefs.getInt("lastMessageNum", -1);
    					if (mNew == 0 && lastNum == -1) {
    						prefs.edit().putInt("lastMessageNum", mNum).commit();
    						return context;
    					} else if (mNum <= lastNum) {
    			            Log.d(AnkiDroidApp.TAG, "BroadcastMessage - message " + mNum + " already shown");
    						return context;
    					}
    			    } else if (inputLine.startsWith(MIN_VERSION)) {
    					mMinVersion = inputLine.substring(MIN_VERSION.length());
    					if (compareVersions(mMinVersion, AnkiDroidApp.getPkgVersion()) > 0) {
    			            Log.d(AnkiDroidApp.TAG, "BroadcastMessage - too low AnkiDroid version, message only for > " + mMinVersion);
    						return context;
    					}
    			    } else if (inputLine.startsWith(MAX_VERSION)) {
    					mMaxVersion = inputLine.substring(MAX_VERSION.length());
    					if (compareVersions(mMaxVersion, AnkiDroidApp.getPkgVersion()) < 0) {
    			            Log.d(AnkiDroidApp.TAG, "BroadcastMessage - too high AnkiDroid version, message only for < " + mMaxVersion);
    						return context;
    					}
    			    } else if (inputLine.startsWith(TITLE)) {
    					mTitle = inputLine.substring(TITLE.length());
    			    } else if (inputLine.startsWith(TEXT)) {
    					mText = inputLine.substring(TEXT.length());
    			    } else if (inputLine.startsWith(URL)) {
    					mUrl = inputLine.substring(URL.length());
    			    }
    			}
    			mShowDialog = true;
    			in.close();
    		} catch (IOException e) {
            	Log.e(AnkiDroidApp.TAG, "IOException on reading file " + FILE_URL + ": " + e);
				return context;
        	} catch (NumberFormatException e) {
            	Log.e(AnkiDroidApp.TAG, "Number of file " + FILE_URL + " could not be read: " + e);
				return context;
        	}
            return context;
        }


        @Override
        protected void onPostExecute(Context context) {
            Log.d(AnkiDroidApp.TAG, "BroadcastMessage.DownloadBroadcastMessage.onPostExecute()");
            if (!mShowDialog) {
            	return;
            }
	    	StyledDialog.Builder builder = new StyledDialog.Builder(context);
	    	if (mText != null && mText.length() > 0) {
	    		builder.setMessage("<html><body text=\"#FFFFFF\">" + mText + "</body></html>");
	    		builder.setCancelable(true);
	    		builder.setNegativeButton(context.getResources().getString(R.string.close),  new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							PrefSettings.getSharedPrefs(mContext).edit().putInt("lastMessageNum", mNum).commit();
						}
					});
	    		builder.setOnCancelListener(new OnCancelListener() {
	    				@Override
	    				public void onCancel(DialogInterface arg0) {
	    					PrefSettings.getSharedPrefs(mContext).edit().putInt("lastMessageNum", mNum).commit();
	    				}
	    			});
	    	} else {
				return;
	    	}
    		if (mTitle != null && mTitle.length() > 0) {
    			builder.setTitle(mTitle);
    		}
    		if (mUrl != null && mUrl.length() > 0) {
    			builder.setMessage(mText);
    			builder.setPositiveButton(context.getResources().getString(R.string.visit), new DialogInterface.OnClickListener() {
    				@Override
    				public void onClick(DialogInterface dialog, int which) {
    					PrefSettings.getSharedPrefs(mContext).edit().putInt("lastMessageNum", mNum).commit();
    					String action = "android.intent.action.VIEW";
    					if (Utils.isIntentAvailable(mContext, action)) {
    						Intent i = new Intent(action, Uri.parse(mUrl));
    						mContext.startActivity(i);
    					}
    				}
    			});
    		}
    		try {
        		builder.create().show();    			
    		} catch (BadTokenException e) {
                Log.e(AnkiDroidApp.TAG, "BroadcastMessage - BadTokenException on showing dialog: " + e);
    		}
        }
    }
}

