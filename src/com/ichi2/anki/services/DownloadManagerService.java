package com.ichi2.anki.services;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Card;
import com.ichi2.anki.Deck;
import com.ichi2.anki.DeckTask;
import com.ichi2.anki.Download;
import com.ichi2.anki.R;
import com.ichi2.anki.SharedDeckDownload;
import com.ichi2.anki.StudyOptions;
import com.ichi2.anki.Utils;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;


public class DownloadManagerService extends Service {

	private static final String TAG = "AnkiDroid";
	
	//private int counter = 0;
	//private Handler serviceHandler = null;
	
	// Max size of download buffer.
	private static final int MAX_BUFFER_SIZE = 1024;
	
	private static final String ANKI_URL = "http://anki.ichi2.net";
	private static final String SYNC_URL = ANKI_URL + "/sync/";

	private String mUsername;
	private String mPassword;
	private String mDestination;
	
	private ArrayList<Download> mPersonalDeckDownloads;
	private final RemoteCallbackList<IPersonalDeckServiceCallback> mPersonalDeckCallbacks = new RemoteCallbackList<IPersonalDeckServiceCallback>();
	
	private ArrayList<SharedDeckDownload> mSharedDeckDownloads;
	private final RemoteCallbackList<ISharedDeckServiceCallback> mSharedDeckCallbacks = new RemoteCallbackList<ISharedDeckServiceCallback>();
	
	
	/********************************************************************
	 * Lifecycle methods												*
	 ********************************************************************/
	
	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "Service - onCreate");
		mPersonalDeckDownloads = new ArrayList<Download>();
		mSharedDeckDownloads = new ArrayList<SharedDeckDownload>();
		
		restorePreferences();
		
		// If there is incomplete work, finish it
		addIncompleteDownloads();
		resumeDownloads();
		
		//serviceHandler = new Handler();
		//serviceHandler.postDelayed( new RunTask(),1000L );
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		Log.i(TAG, "Service - onStart");
		super.onStart(intent, startId);
		restorePreferences();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "Service - onBind");
		restorePreferences();
		return mBinder;
	}
	

	@Override
	public boolean onUnbind(Intent intent) {
		Log.i(TAG, "onUnbind");
		return super.onUnbind(intent);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "Service - onDestroy");
		//serviceHandler = null;
	}
	
	public void stopIfFinished() {
		if(!hasMoreWork())
		{
			// Delete tmp folder
			boolean deleted = new File(mDestination + "/tmp").delete();
			Log.i(TAG, mDestination + "/tmp folder was deleted = " + deleted);
			Log.i(TAG, "Service stopping itself...");
			stopSelf();
		}
	}
	
	/********************************************************************
	 * Custom methods													*
	 ********************************************************************/
	
	private boolean hasMoreWork() {
		return (mPersonalDeckDownloads.size() != 0 || mSharedDeckDownloads.size() != 0);
	}
	
	private void restorePreferences()
	{
		SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
		mUsername = pref.getString("username", "");
		mPassword = pref.getString("password", "");
		mDestination = pref.getString("deckPath", AnkiDroidApp.getStorageDirectory());
	}
	
	// It could be part of the AIDL Interface but at the moment no Activity uses it directly
	public void addIncompleteDownloads() 
	{
		Log.i(TAG, "DownloadManagerService - Adding incomplete downloads:");

		File dir = new File(mDestination + "/tmp/");
		File[] fileList = dir.listFiles(new IncompleteDownloadsFilter());
		
		if(fileList != null)
		{
			for(int i = 0; i < fileList.length; i++)
			{
				File file = fileList[i];
				
				String filename = file.getName();
				Log.i(TAG, "Filename = " + filename);
				
				// Personal decks
				if(filename.endsWith(".anki.tmp"))
				{
					Download download = new Download(filename.substring(0, filename.length() - ".anki.tmp".length()));
					download.setDownloaded(file.length());
					mPersonalDeckDownloads.add(download);
				}
				// Shared decks
				else if(filename.endsWith(".shared.zip.tmp"))
				{
					filename = filename.substring(0, filename.length() - ".shared.zip.tmp".length());
					int lastDotPosition = filename.lastIndexOf(".");
					String identifier = filename.substring(lastDotPosition + 1, filename.length());
					String title = filename.substring(0, lastDotPosition);
					
					SharedDeckDownload download = new SharedDeckDownload(Integer.parseInt(identifier), title);
					download.setDownloaded(file.length());
					mSharedDeckDownloads.add(download);
				}
				//Shared but not totally updated decks
				else if(filename.endsWith(".anki.updating"))
				{
					String title = filename.substring(0, filename.length() - ".anki.updating".length());
					
					SharedDeckDownload download = new SharedDeckDownload(title);
					download.setStatus(SharedDeckDownload.UPDATE);
					mSharedDeckDownloads.add(download);
				}
			}
			notifyObservers();
		}
		// If no decks were added, stop the service
		stopIfFinished();
	}
	
	// It could be part of the AIDL Interface but at the moment no Activity uses it directly
	public void resumeDownloads()
	{
		int i = 0;
		int j = 0;
		
		int personalDeckDownloadsSize = mPersonalDeckDownloads.size();
		int sharedDeckDownloadsSize = mSharedDeckDownloads.size();
		
		// Resume both personal deck downloads and shared deck downloads
		for(i = 0; i < personalDeckDownloadsSize &&  i < sharedDeckDownloadsSize; i++)
		{
			resumeDownload(mPersonalDeckDownloads.get(i));
			resumeDownload(mSharedDeckDownloads.get(i));
		}
		
		// Resume remaining personal deck downloads
		for(j = i; j < personalDeckDownloadsSize; j++)
		{
			resumeDownload(mPersonalDeckDownloads.get(j));
		}
		
		// Resume remaining shared deck downloads
		for(j = i; j < sharedDeckDownloadsSize; j++)
		{
			resumeDownload(mSharedDeckDownloads.get(j));
		}
	}
	
	// It could be part of the AIDL Interface but at the moment no Activity uses it directly
	public void resumeDownload(Download download)
	{
		// Create tmp folder where the temporal decks are going to be stored
		new File(mDestination + "/tmp/").mkdirs();
		
		if(download instanceof SharedDeckDownload)
		{
			SharedDeckDownload sharedDeckDownload = (SharedDeckDownload) download;
			if(sharedDeckDownload.getStatus() == SharedDeckDownload.UPDATE)
			{
				new UpdateDeckTask().execute(new Payload(new Object[] {sharedDeckDownload}));
			}
			else
			{
				new DownloadSharedDeckTask().execute(sharedDeckDownload);
			}
		}
		else
		{
			// TODO: Check if there is already a deck with the same name, and if that's so 
			// add the current milliseconds to the end of the name or notify the user
			new DownloadPersonalDeckTask().execute(download);
		}
	}
	
	private String unzipSharedDeckFile(String zipFilename, String title)
	{
		ZipInputStream zipInputStream = null;
		Log.i(TAG, "unzipSharedDeckFile");
		if(zipFilename.endsWith(".zip"))
		{
			Log.i(TAG, "zipFilename ends with .zip");
			try {
				zipInputStream = new ZipInputStream(new FileInputStream(new File(zipFilename)));
			
				title = title.replace("^", "");
				title = title.substring(0, java.lang.Math.min(title.length(), 40));
	
				if(new File(mDestination + "/" + title + ".anki").exists())
					title += System.currentTimeMillis();
	
				String partialDeckPath = mDestination + "/tmp/" + title;
				String deckFilename = partialDeckPath + ".anki.updating";
	
				ZipEntry zipEntry = null;
				while((zipEntry = zipInputStream.getNextEntry()) != null)
				{
					Log.i(TAG, "zipEntry = " + zipEntry.getName());
	
					if("shared.anki".equalsIgnoreCase(zipEntry.getName()))
					{
						Utils.writeToFile(zipInputStream, deckFilename);
					}
					else if(zipEntry.getName().startsWith("shared.media/", 0))
					{
						Log.i(TAG, "Folder created = " + new File(partialDeckPath + ".media/").mkdir());
						Log.i(TAG, "Destination = " + AnkiDroidApp.getStorageDirectory() + "/" + title + ".media/" + zipEntry.getName().replace("shared.media/", ""));
						Utils.writeToFile(zipInputStream, partialDeckPath + ".media/" + zipEntry.getName().replace("shared.media/", ""));
					}
				}
				zipInputStream.close();
				
				// Delete zip file
				new File(zipFilename).delete();
			} catch (FileNotFoundException e) {
				Log.e(TAG, "FileNotFoundException = " + e.getMessage());
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(TAG, "IOException = " + e.getMessage());
				e.printStackTrace();
			}
		}

		return title;
	}
	
	private int getNextNotificationId()
	{
		// Retrieve previously saved value
		SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
		int notificationCounter = pref.getInt("notificationCounter", 0);
		
		// Increment it
		notificationCounter++;
		
		// Save new value
		Editor editor = pref.edit();
		editor.putInt("notificationCounter", notificationCounter);
		editor.commit();
		
		return notificationCounter;
	}
	
	/********************************************************************
	 * Notification methods												*
	 ********************************************************************/
	
	/**
	 * Show a notification informing the user when a deck is ready to be used
	 */
	private void showNotification(String deckTitle) 
	{
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Resources res = getResources();
		
		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(R.drawable.anki, res.getString(R.string.download_finished), System.currentTimeMillis());

		Intent loadDeckIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mDestination + "/" + deckTitle + ".anki"), DownloadManagerService.this, StudyOptions.class);
		// The PendingIntent to launch our activity if the user selects this notification
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, loadDeckIntent, 0);

		// Set the info for the views that show in the notification panel
		notification.setLatestEventInfo(this, deckTitle, res.getString(R.string.deck_downloaded), contentIntent);

		// Clear the notification when the user selects it
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		
		// Vibrate
		notification.defaults |= Notification.DEFAULT_VIBRATE;
		
		// Show a blue light
		notification.ledARGB = 0xff0000ff;
		notification.ledOnMS = 500;
		notification.ledOffMS = 1000;
		notification.flags |= Notification.FLAG_SHOW_LIGHTS;
		
		// Send the notification
		Log.i(TAG, "Sending notification...");
		mNotificationManager.notify(getNextNotificationId(), notification);
	}
	
	private synchronized void notifyObservers()
	{
		notifyPersonalDeckObservers();
		notifySharedDeckObservers();
	}
	
	private synchronized void notifyPersonalDeckObservers()
	{
		final int numPersonalDeckCallbacks = mPersonalDeckCallbacks.beginBroadcast();
		for(int i = 0; i < numPersonalDeckCallbacks; i++)
		{
			try {
				mPersonalDeckCallbacks.getBroadcastItem(i).publishProgress(mPersonalDeckDownloads);
			} catch (RemoteException e) {
				// There is nothing special we need to do if the service has crashed
				Log.e(TAG, "RemoteException = " + e.getMessage());
				e.printStackTrace();
			}
		}
		mPersonalDeckCallbacks.finishBroadcast();
	}
	
	private synchronized void notifySharedDeckObservers()
	{
		Log.i(TAG, "notifySharedDeckObservers");
		final int numSharedDeckCallbacks = mSharedDeckCallbacks.beginBroadcast();
		for(int i = 0; i < numSharedDeckCallbacks; i++)
		{
			try {
				mSharedDeckCallbacks.getBroadcastItem(i).publishProgress(mSharedDeckDownloads);
			} catch (RemoteException e) {
				// There is nothing special we need to do if the service has crashed
				Log.e(TAG, "RemoteException = " + e.getMessage());
				e.printStackTrace();
			}
		}
		mSharedDeckCallbacks.finishBroadcast();
	}
	
	/********************************************************************
	 * Filters															*
	 ********************************************************************/
	
	private static final class IncompleteDownloadsFilter implements FileFilter
	{
		public boolean accept(File pathname)
		{
			String filename = pathname.getName();
			// The filter searches for unfinished tasks:
			// * not completed personal deck downloads
			// * not completed shared deck downloads
			// * downloaded but not totally updated shared decks
			if(pathname.isFile() && (filename.endsWith(".anki.tmp") || filename.endsWith(".shared.zip.tmp") || filename.endsWith(".anki.updating")))
			{
				return true;
			}
			return false;
		}
	}
	
	/********************************************************************
	 * Interfaces	 													*
	 ********************************************************************/
	
	/**
	 * IDownloadManagerService is defined through IDL
	 */
	private final IDownloadManagerService.Stub mBinder = new IDownloadManagerService.Stub() {

		public void registerPersonalDeckCallback(IPersonalDeckServiceCallback cb) {
			Log.i(TAG, "registerPersonalDeckCallback");
			if(cb != null) 
			{
				mPersonalDeckCallbacks.register(cb);
				notifyPersonalDeckObservers();
			}
		}
		
		public void unregisterPersonalDeckCallback(IPersonalDeckServiceCallback cb) {
			Log.i(TAG, "unregisterPersonalDeckCallback");
			if(cb != null) 
			{
				mPersonalDeckCallbacks.unregister(cb);
			}
		}

		@Override
		public void registerSharedDeckCallback(ISharedDeckServiceCallback cb) throws RemoteException {
			Log.i(TAG, "registerSharedDeckCallback");
			if(cb != null) 
			{
				mSharedDeckCallbacks.register(cb);
				notifySharedDeckObservers();
			}
		}

		@Override
		public void unregisterSharedDeckCallback(ISharedDeckServiceCallback cb) throws RemoteException {
			Log.i(TAG, "unregisterSharedDeckCallback");
			if(cb != null)
			{
				mSharedDeckCallbacks.unregister(cb);
			}
		}
		
		@Override
		public void downloadFile(Download download) throws RemoteException
		{
			if(download instanceof SharedDeckDownload)
			{
				mSharedDeckDownloads.add((SharedDeckDownload)download);
			}
			else
			{
				mPersonalDeckDownloads.add(download);
			}
			resumeDownload(download);
		}
		
		@Override
		public List<Download> getPersonalDeckDownloads() throws RemoteException {
			return mPersonalDeckDownloads;
		}

		@Override
		public List<SharedDeckDownload> getSharedDeckDownloads() throws RemoteException {
			return mSharedDeckDownloads;
		}
	};
	
	/********************************************************************
	 * Listeners	 													*
	 ********************************************************************/
	/*	
	public interface ProgressListener 
	{
		
		public void onProgressUpdate(Object... values);
	}
	
	private ProgressListener mUpdateListener = new ProgressListener() {

		@Override
		public void onProgressUpdate(Object... values) {
			String deckPath = (String) values[0];
			Long numUpdatedCards = (Long) values[1];
			
			//Save on preferences
			SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
			Editor editor = pref.edit();
			Log.w(TAG, "ProgressListener, deckPath = " + deckPath + " NumCards: " + numUpdatedCards);
			editor.putLong("numUpdatedCards:" + deckPath, numUpdatedCards);
			editor.commit();
		}
		
	};
	*/
	
	/********************************************************************
	 * Async Tasks	 													*
	 ********************************************************************/
	
	private class DownloadPersonalDeckTask extends AsyncTask<Download, Object, Download> {
		
		@Override
		protected Download doInBackground(Download... downloads) 
		{
			Download download = downloads[0];
			
			URL url;
			RandomAccessFile file = null;
			InflaterInputStream iis = null;

			try {
				url = new URL(SYNC_URL + "fulldown");
				HttpURLConnection connection = (HttpURLConnection)url.openConnection();

				connection.setDoInput(true);
				connection.setDoOutput(true);
				connection.setUseCaches(false);
				connection.setRequestMethod("POST");
				// FIXME: The connection always returns all bytes, regardless of what is indicated in range property, so resuming downloads of personal decks is not possible at the moment
				//		  Fix this when the connection is fixed on AnkiOnline
				//Log.i(TAG, "Range = " + download.getDownloaded());
				//connection.setRequestProperty("Range","bytes=" + download.getDownloaded() + "-");
				connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");

				connection.connect();

				long startTime = System.currentTimeMillis();
				
				DataOutputStream ds = new DataOutputStream(connection.getOutputStream());
				String data = "p=" + URLEncoder.encode(mPassword,"UTF-8") + "&u=" + URLEncoder.encode(mUsername,"UTF-8") + "&d=" + URLEncoder.encode(download.getTitle(), "UTF-8");
				ds.writeBytes(data);
				Log.i(TAG, "Closing streams...");
				ds.flush();
				ds.close();

				// Make sure response code is in the 200 range.
				if (connection.getResponseCode() / 100 != 2) 
				{
					download.setStatus(Download.ERROR);
					publishProgress();
				}
				else
				{
					download.setStatus(Download.DOWNLOADING);
					publishProgress();
				}

				Log.i(TAG, "Response code = " + connection.getResponseCode());

				// Check for valid content length.
				Log.i(TAG, "Connection length = " + connection.getContentLength());
				int contentLength = connection.getContentLength();
				if (contentLength < 1) 
				{
					Log.i(TAG, "Content Length = -1");
					//download.setStatus(Download.ERROR);
				}

				// Set the size for this download if it hasn't been already set
				if (download.getSize() == -1 && contentLength != -1) 
				{
					download.setSize(contentLength);
					Log.i(TAG, "File size = " + contentLength);
				}
				
				// Open file
				file = new RandomAccessFile(mDestination + "/tmp/" + download.getTitle() + ".anki.tmp", "rw");
				// FIXME:  Uncomment next line when the connection is fixed on AnkiOnline (= when the connection only returns the bytes specified on the range property)
				//file.seek(download.getDownloaded());
				
				iis = new InflaterInputStream(connection.getInputStream());
				
				while (download.getStatus() == Download.DOWNLOADING) 
				{
					// Size buffer according to how much of the file is left to download
					Log.i(TAG, "Downloading... " + download.getDownloaded());
					byte buffer[];
					//if (size - downloaded > MAX_BUFFER_SIZE) {
					buffer = new byte[MAX_BUFFER_SIZE];
					//} else {
					//    buffer = new byte[size - downloaded];
					//}
					
					// Read from server into buffer.
					int read = iis.read(buffer);
					if (read == -1)
					{
						break;
					}
					
					// Write buffer to file.
					file.write(buffer, 0, read);
					download.setDownloaded(download.getDownloaded() + read);
				}
				
				// Change status to complete if this point was reached because downloading has finished
				if (download.getStatus() == Download.DOWNLOADING) 
				{
					download.setStatus(Download.COMPLETE);
					new File(mDestination + "/tmp/" + download.getTitle() + ".anki.tmp").renameTo(new File(mDestination + "/" + download.getTitle() + ".anki"));
					publishProgress();
				}
				long finishTime = System.currentTimeMillis();
				Log.i(TAG, "Finished in " + ((finishTime - startTime)/1000) + " seconds!");
				Log.i(TAG, "Downloaded = " + download.getDownloaded());
				connection.disconnect();
			} catch (Exception e) {
				e.printStackTrace();
				Log.i(TAG, "Exception Error = " + e.getMessage());
				download.setStatus(Download.ERROR);
				publishProgress();
			} finally {
				// Close file
				if (file != null) {
					try {
						file.close();
					} catch (Exception e) {}
				}
				
				// Close connection to server
				if (iis != null) {
					try {
						iis.close();
					} catch (Exception e) {}
				}
			}
			
			return download;
		}

		@Override
		protected void onProgressUpdate(Object... values) 
		{
			notifyPersonalDeckObservers();
		}

		@Override
		protected void onPostExecute(Download download) 
		{
			// TODO: Error cases
			if(download.getStatus() == Download.COMPLETE)
			{
				showNotification(download.getTitle());
			}
			mPersonalDeckDownloads.remove(download);
			notifyPersonalDeckObservers();
			stopIfFinished();
		}
	}
	
	private class DownloadSharedDeckTask extends AsyncTask<Download, Object, SharedDeckDownload> {
		
		@Override
		protected SharedDeckDownload doInBackground(Download... downloads) 
		{
			SharedDeckDownload download = (SharedDeckDownload) downloads[0];
			
			URL url;
			RandomAccessFile file = null;
			InputStream is = null;

			try {
				url = new URL(ANKI_URL + "/file/get?id=" + download.getId());
				HttpURLConnection connection = (HttpURLConnection)url.openConnection();

				connection.setDoInput(true);
				connection.setDoOutput(true);
				connection.setUseCaches(false);
				connection.setRequestMethod("GET");
				Log.i(TAG, "Range = " + download.getDownloaded());
				// FIXME: Seems that Range property is also not working well here -> TEST IT!
				//connection.setRequestProperty("Range","bytes=" + download.getDownloaded() + "-");
				connection.setRequestProperty("Accept-Encoding", "identity");
				connection.setRequestProperty("Host", "anki.ichi2.net");
				connection.setRequestProperty("Connection", "close");

				connection.connect();

				long startTime = System.currentTimeMillis();
				
				// Make sure response code is in the 200 range.
				if (connection.getResponseCode() / 100 != 2) 
				{
					download.setStatus(Download.ERROR);
					publishProgress();
				}
				else
				{
					download.setStatus(Download.DOWNLOADING);
					publishProgress();
				}

				Log.i(TAG, "Response code = " + connection.getResponseCode());

				// Check for valid content length.
				Log.i(TAG, "Connection length = " + connection.getContentLength());
				int contentLength = connection.getContentLength();
				if (contentLength < 1) 
				{
					Log.i(TAG, "Content Length = -1");
					//download.setStatus(Download.ERROR);
				}

				// Set the size for this download if it hasn't been already set
				if (download.getSize() == -1 && contentLength != -1) 
				{
					download.setSize(contentLength);
					Log.i(TAG, "File size = " + contentLength);
					// TODO: NOTIFY???
				}
				
				// Open file
				file = new RandomAccessFile(mDestination + "/tmp/" + download.getTitle() + "." + download.getId() +".shared.zip.tmp", "rw");
				// FIXME:  Uncomment next line when the connection is fixed on AnkiOnline (= when the connection only returns the bytes specified on the range property)
				//file.seek(download.getDownloaded());
				
				is = connection.getInputStream();
				
				while(download.getStatus() == Download.DOWNLOADING)
				{
					Log.i(TAG, "Downloading... " + download.getDownloaded());
					byte buffer[];
					//if (size - downloaded > MAX_BUFFER_SIZE) {
					buffer = new byte[MAX_BUFFER_SIZE];
					//} else {
					//    buffer = new byte[size - downloaded];
					//}
					
					// Read from server into buffer.
					int read = is.read(buffer);
					if (read == -1)
					{
						break;
					}
					
					// Write buffer to file.
					file.write(buffer, 0, read);
					download.setDownloaded(download.getDownloaded() + read);
					publishProgress();
				}
				
				// Change status to complete if this point was reached because downloading has finished
				if (download.getStatus() == Download.DOWNLOADING) 
				{
					download.setStatus(Download.COMPLETE);
					new File(mDestination + "/tmp/" + download.getTitle() + "." + download.getId() +".shared.zip.tmp").renameTo(new File(mDestination + "/tmp/" + download.getTitle() + ".zip"));
					publishProgress();
				}
				
				long finishTime = System.currentTimeMillis();
				Log.i(TAG, "Finished in " + ((finishTime - startTime)/1000) + " seconds!");
				Log.i(TAG, "Downloaded = " + download.getDownloaded());
				connection.disconnect();
			} catch (Exception e) {
				e.printStackTrace();
				Log.i(TAG, "Exception Error = " + e.getMessage());
				download.setStatus(Download.ERROR);
				publishProgress();
			} finally {
				// Close file
				if (file != null) {
					try {
						file.close();
					} catch (Exception e) {}
				}
				// Close connection to server
				if (is != null) {
					try {
						is.close();
					} catch (Exception e) {}
				}
			}
			
			return download;
		}
		
		@Override
		protected void onProgressUpdate(Object... values) 
		{
			notifySharedDeckObservers();
		}

		@Override
		protected void onPostExecute(SharedDeckDownload download) 
		{
			Log.i(TAG, "onPostExecute");
			SharedDeckDownload sharedDownload = (SharedDeckDownload) download;
			sharedDownload.setStatus(SharedDeckDownload.UPDATE);
			notifySharedDeckObservers();
			
			// Unzip deck and media
			String unzippedDeckName = unzipSharedDeckFile(mDestination + "/tmp/" + download.getTitle() + ".zip", sharedDownload.getTitle());
			download.setTitle(unzippedDeckName);
			
			// Update all cards in deck
			SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
			Editor editor = pref.edit();
			editor.putLong("numUpdatedCards:" + mDestination + "/tmp/" + download.getTitle() + ".anki.updating", 0);
			editor.commit();
			new UpdateDeckTask().execute(new Payload(new Object[] {download}));
			
		}
	}
	
	private class UpdateDeckTask extends AsyncTask<Connection.Payload, Connection.Payload, Connection.Payload> {

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			// Pass
		}

		@Override
		protected void onProgressUpdate(Payload... values) 
		{
			notifySharedDeckObservers();
		}

		@Override
		protected Payload doInBackground(Payload... args) {
			Payload data = doInBackgroundLoadDeck(args);
			if(data.returnType == DeckTask.DECK_LOADED)
			{
				double now = System.currentTimeMillis();
				HashMap<String,Object> results = (HashMap<String, Object>) data.result;
				Deck deck = (Deck) results.get("deck");
				//deck.updateAllCards();
				SharedDeckDownload download = (SharedDeckDownload) args[0].data[0];
				SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
				String updatedCardsPref = "numUpdatedCards:" + mDestination + "/tmp/" + download.getTitle() + ".anki.updating";
				long totalCards = deck.getCardCount();
				long updatedCards = pref.getLong(updatedCardsPref, 0);
				long batchSize = Math.max(100, totalCards/100);
				download.setNumTotalCards((int)totalCards);
				while (updatedCards < totalCards) {
					updatedCards = deck.updateAllCardsFromPosition(updatedCards, batchSize);
					Editor editor = pref.edit();
					editor.putLong(updatedCardsPref, updatedCards);
					editor.commit();
					download.setNumUpdatedCards((int)updatedCards);
					publishProgress();
				}
				Log.i(TAG, "Time to update deck = " + (System.currentTimeMillis() - now)/1000.0 + " sec.");
			}
			else
			{
				data.success = false;
			}
			return data;
		}
		
		private Payload doInBackgroundLoadDeck(Payload... params)
		{
			Payload data = params[0];
			SharedDeckDownload download = (SharedDeckDownload) data.data[0];
			String deckFilename = mDestination + "/tmp/" + download.getTitle() + ".anki.updating";
			Log.i(TAG, "doInBackgroundLoadDeck - deckFilename = " + deckFilename);

			Log.i(TAG, "loadDeck - SD card mounted and existent file -> Loading deck...");
			try
			{
				// Open the right deck.
				Deck deck = Deck.openDeck(deckFilename);
				// Start by getting the first card and displaying it.
				Card card = deck.getCard();
				Log.i(TAG, "Deck loaded!");

				// Set the result
				data.returnType = DeckTask.DECK_LOADED;
				HashMap<String,Object> results = new HashMap<String,Object>();
				results.put("deck", deck);
				results.put("card", card);
				results.put("position", download.getNumUpdatedCards());
				data.result = results;
				return data;
			} catch (SQLException e)
			{
				Log.i(TAG, "The database " + deckFilename + " could not be opened = " + e.getMessage());
				data.success = false;
				data.returnType = DeckTask.DECK_NOT_LOADED;
				data.exception = e;
				return data;
			} catch (CursorIndexOutOfBoundsException e)
			{
				Log.i(TAG, "The deck has no cards = " + e.getMessage());
				data.success = false;
				data.returnType = DeckTask.DECK_EMPTY;
				data.exception = e;
				return data;
			}
		}
		
		@Override
		protected void onPostExecute(Payload result)
	 	{
			super.onPostExecute(result);
			if(result.success)
			{
				HashMap<String,Object> results = (HashMap<String, Object>) result.result;
				Deck deck = (Deck) results.get("deck");
				// Close the previously opened deck.
				if (deck != null)
				{
					deck.closeDeck();
				}
				// TODO: Remove download and notify only on success?
				SharedDeckDownload download = (SharedDeckDownload)result.data[0];
				
				// Put updated cards to 0
				SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
				Editor editor = pref.edit();
				editor.putLong("numUpdatedCards:" + mDestination + "/tmp/" + download.getTitle() + ".anki.updating", 0);
				editor.commit();
				// Move deck and media to the default deck path
				new File(mDestination + "/tmp/" + download.getTitle() + ".anki.updating").renameTo(new File(mDestination + "/" + download.getTitle() + ".anki"));
				new File(mDestination + "/tmp/" + download.getTitle() + ".media/").renameTo(new File(mDestination + "/" + download.getTitle() + ".media/"));
				mSharedDeckDownloads.remove(download);
				notifySharedDeckObservers();
				showNotification(download.getTitle());
				stopIfFinished();
			}
		}
	}
	
	// To test when the service is alive
	/*
	class RunTask implements Runnable {
		public void run() {
			Log.i(TAG, "Service running...");
			++counter;
			if(serviceHandler != null)
			{
				serviceHandler.postDelayed( this, 1000L );
			}
		}
	}
	*/
}
