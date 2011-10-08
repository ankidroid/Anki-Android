package com.ichi2.anki;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.Themes;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.WindowManager.BadTokenException;
import android.webkit.WebView;

public class BroadcastMessages {

	public static final String FILE_URL = "https://ankidroid.googlecode.com/files/broadcastMessages.xml";

	public static final String NUM = "Num";
	public static final String MIN_VERSION = "MinVer";
	public static final String MAX_VERSION = "MaxVer";
	public static final String TITLE = "Title";
	public static final String TEXT = "Text";
	public static final String URL = "Url";

	private static StyledDialog mDialog;

	private static final int TIMEOUT = 30000;


	public static void init(Context context, long lastTimeOpened) {
		Log.d(AnkiDroidApp.TAG, "BroadcastMessages: init");
		// retrieve messages on first start of the day
		if (Utils.isNewDay(lastTimeOpened)) {
			PrefSettings.getSharedPrefs(context).edit().putBoolean("showBroadcastMessageToday", true).commit();
		}
	}


	public static void checkForNewMessages(Context context) {
		Log.d(AnkiDroidApp.TAG, "BroadcastMessages: checkForNewMessages");
		SharedPreferences prefs = PrefSettings.getSharedPrefs(context);
		// don't retrieve messages, if option in preferences is not set
		if (!prefs.getBoolean("showBroadcastMessages", true)) {
			return;
		}
		// don't proceed if messages were already shown today
		if (!prefs.getBoolean("showBroadcastMessageToday", true)) {
			Log.d(AnkiDroidApp.TAG, "BroadcastMessages: already shown today");
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


	public static void showDialog() {
		if (mDialog != null && mDialog.isShowing()) {
			// workaround for the dialog content not showing when starting AnkiDroid with Deckpicker and open then Studyoptions
			try {
				mDialog.dismiss();
				mDialog.show();			
			} catch (IllegalArgumentException e) {
				Log.e(AnkiDroidApp.TAG, "Error on dismissing and showing new messages dialog: " + e);
			}
		}
	}


    private static class DownloadBroadcastMessage extends AsyncTask<Context, Void, Context> {

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
    		int lastNum = prefs.getInt("lastMessageNum", -1);
		if (lastNum == -1) {
			// first start of AnkiDroid ever (or at least of a version which supports broadcast messages).
			// do nothing yet but retrieve message the next time, AD is started
			prefs.edit().putInt("lastMessageNum", 0).commit();
			return context;
		}
    		try {
        		Log.i(AnkiDroidApp.TAG, "BroadcastMessage: download file " + FILE_URL);
    			URL fileUrl;
    			fileUrl = new URL(FILE_URL);
    			URLConnection conn = fileUrl.openConnection();
    			conn.setConnectTimeout(TIMEOUT);
    			conn.setReadTimeout(TIMEOUT);
    			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    			DocumentBuilder db = dbf.newDocumentBuilder();
    			Document dom = db.parse(conn.getInputStream());
    			Element docEle = dom.getDocumentElement();
    			NodeList nl = docEle.getElementsByTagName("Message");
    			String currentVersion = AnkiDroidApp.getPkgVersion();
    			if(nl != null && nl.getLength() > 0) {
    				for(int i = 0 ; i < nl.getLength();i++) {
    					Element el = (Element)nl.item(i);

    					// get message number
    					mNum = Integer.parseInt(getXmlValue(el, NUM));
    					if (mNum <= lastNum) {
    			            		Log.d(AnkiDroidApp.TAG, "BroadcastMessage - message " + mNum + " already shown");
    						continue;
    					}

    					// get message version info
    					mMinVersion = getXmlValue(el, MIN_VERSION);
    					if (mMinVersion != null && mMinVersion.length() > 0 && compareVersions(mMinVersion, currentVersion) > 0) {
        			            Log.d(AnkiDroidApp.TAG, "BroadcastMessage - too low AnkiDroid version (" + currentVersion + "), message " + mNum + " only for > " + mMinVersion);
        			            continue;
    					}
    					mMaxVersion = getXmlValue(el, MAX_VERSION);
    					if (mMaxVersion != null && mMaxVersion.length() > 0 && compareVersions(mMaxVersion, currentVersion) < 0) {
        			            Log.d(AnkiDroidApp.TAG, "BroadcastMessage - too high AnkiDroid version (" + currentVersion + "), message " + mNum + " only for > " + mMaxVersion);
        			            continue;
    					}

    					// get Title, Text, Url
    					mTitle = getXmlValue(el, TITLE);
    					mText = getXmlValue(el, TEXT);
    					mUrl = getXmlValue(el, URL);
    					if (mText != null && mText.length() > 0) {
        	    				mShowDialog = true;
        	    				return context;
    					}
    				}
    				// no valid message left
    				Log.d(AnkiDroidApp.TAG, "BroadcastMessages: disable messaging system for today");
    				prefs.edit().putBoolean("showBroadcastMessageToday", false).commit();
    				mShowDialog = false;
    			}
    		} catch (IOException e) {
            	Log.e(AnkiDroidApp.TAG, "DownloadBroadcastMessage: IOException on reading file " + FILE_URL + ": " + e);
				return context;
        	} catch (NumberFormatException e) {
            	Log.e(AnkiDroidApp.TAG, "DownloadBroadcastMessage: Number of file " + FILE_URL + " could not be read: " + e);
				return context;
        	}catch(ParserConfigurationException e) {
            	Log.e(AnkiDroidApp.TAG, "DownloadBroadcastMessage: ParserConfigurationException: " + e);
				return context;
    		}catch(SAXException e) {
            	Log.e(AnkiDroidApp.TAG, "DownloadBroadcastMessage: SAXException: " + e);
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
	    	Resources res = context.getResources();
	    	if (mText != null && mText.length() > 0) {
		        WebView view = new WebView(context);
		        view.setBackgroundColor(res.getColor(Themes.getDialogBackgroundColor()));
		        view.loadDataWithBaseURL("", "<html><body text=\"#FFFFFF\" link=\"#E37068\" alink=\"#E37068\" vlink=\"#E37068\">" + mText + "<br></body></html>", "text/html", "UTF-8", "");
		        builder.setView(view, true);
	    		builder.setCancelable(true);
	    		builder.setNegativeButton(res.getString(R.string.close),  new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
	    					setMessageRead(mContext, mNum);
	    			        BroadcastMessages.checkForNewMessages(mContext);
						}
					});
	    	} else {
				return;
	    	}
    		if (mTitle != null && mTitle.length() > 0) {
    			builder.setTitle(mTitle);
    		}
    		if (mUrl != null && mUrl.length() > 0) {
    			builder.setPositiveButton(mUrl.substring(mUrl.length() - 4).equals(".apk") ? res.getString(R.string.download) : res.getString(R.string.visit), new DialogInterface.OnClickListener() {
    				@Override
    				public void onClick(DialogInterface dialog, int which) {
    					setMessageRead(mContext, mNum);
    					String action = "android.intent.action.VIEW";
    					if (Utils.isIntentAvailable(mContext, action)) {
    						Intent i = new Intent(action, Uri.parse(mUrl));
    						mContext.startActivity(i);
    					}
    				}
    			});
    			builder.setNeutralButton(res.getString(R.string.later), null);
    		}
    		try {
    			mDialog = builder.create();
			Log.d(AnkiDroidApp.TAG, "BroadcastMessages: show dialog");
    			mDialog.show();
    		} catch (BadTokenException e) {
                Log.e(AnkiDroidApp.TAG, "BroadcastMessage - BadTokenException on showing dialog: " + e);
    		}
        }
    }


    private static void setMessageRead(Context context, int num) {
		Editor editor = PrefSettings.getSharedPrefs(context).edit();
		Log.d(AnkiDroidApp.TAG, "BroadcastMessages: set message " + num + " as read");
		editor.putInt("lastMessageNum", num);
		editor.commit();
    }


    private static String getXmlValue(Element e, String tag) {
    	String text = null;
    	NodeList list = e.getElementsByTagName(tag);
    	if (list != null && list.getLength() > 0) {
    		Element element = (Element) list.item(0);
    		text = element.getFirstChild().getNodeValue();
    	}
    	return text;
    }
}

