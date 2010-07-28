package com.ichi2.anki;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;

public class SharedDownloadManager {

	private static final String TAG = "AnkiDroid";
	
	private static final String ANKI_URL = "http://anki.ichi2.net";
	private static final String SYNC_URL = ANKI_URL + "/sync/";
	// Max size of download buffer.
	private static final int MAX_BUFFER_SIZE = 1024;

	static SharedDownloadManager mSharedInstance;
	
	private Context mContext;
	private static String mUsername;
	private static String mPassword;
	private static String mDestination;
	
	private ArrayList<Download> mDownloads;
	
	private ArrayList<DownloadsListener> mListeners;
	
	private int mNotificationCounter = 0;
	
	public synchronized static SharedDownloadManager getSharedInstance(Context context, String username, String password, String destination)
	{
		if (mSharedInstance == null || !username.equalsIgnoreCase(mUsername) || !password.equalsIgnoreCase(mPassword) || !destination.equalsIgnoreCase(destination))
		{
			mSharedInstance = new SharedDownloadManager(context, username, password, destination);
		}
		return mSharedInstance;
	}

	public SharedDownloadManager(Context context, String username, String password, String destination)
	{
		mContext = context;
		mUsername = username;
		mPassword = password;
		mDestination = destination;
		mDownloads = new ArrayList<Download>();
		mListeners = new ArrayList<DownloadsListener>();
		
		addIncompleteDownloads();
		//resumeDownloads();
	}

	private void addIncompleteDownloads() 
	{
		Log.i(TAG, "Adding incomplete downloads:");
		File[] fileList;
		File dir = new File(mDestination);
		fileList = dir.listFiles(new IncompleteDownloadsFilter());
		
		for(int i = 0; i < fileList.length; i++)
		{
			File file = fileList[i];
			
			//Personal decks
			String filename = file.getName();
			if(filename.endsWith(".shared.anki.tmp"))
			{
				// FIXME: Maybe the identifier does not have only one character... fix that!
				String identifier = filename.substring(filename.length() - ".shared.anki.tmp".length() - 1, filename.length() - ".shared.anki.tmp".length());
				mDownloads.add(new Download(filename.substring(0, filename.length() - ".shared.anki.tmp".length()), file.length()));
				Log.i(TAG, "Incomplete download of deck = " + mDownloads.get(mDownloads.size() - 1).getTitle() + ", identifier = " + identifier + " downloaded = " +  mDownloads.get(mDownloads.size() - 1).getSize() + "B");
			}
		}
		
		notifyObservers();
	}
	
	public void resumeDownloads()
	{
		for(int i = 0; i < mDownloads.size(); i++)
		{
			resumeDownload(mDownloads.get(i));
		}
	}
	
	public List<Download> getDownloadsList()
	{
		return mDownloads;
	}
	
	
	public void downloadFile(Download download)
	{
		mDownloads.add(download);
		resumeDownload(download);
	}
	
	public void resumeDownload(Download download)
	{
		new DownloadFilesTask().execute(download);
	}
	
	private class DownloadFilesTask extends AsyncTask<Download, Object, Download> {
		
		protected Download doInBackground(Download... downloads) 
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
				connection.setRequestProperty("Range","bytes=" + download.getDownloaded() + "-");
				connection.setRequestProperty("Accept-Encoding", "identity");
				connection.setRequestProperty("Host", "anki.ichi2.net");
				connection.setRequestProperty("Connection", "close");

				connection.connect();

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
				
				// Open file and seek to the end of it.
				file = new RandomAccessFile(mDestination + "/" + download.getTitle() + "." + download.getId() +".shared.zip.tmp", "rw");
				file.seek(download.getDownloaded());
				
				is = connection.getInputStream();
				
				if(download.getFilename().endsWith(".zip"))
				{
					while(download.getStatus() == Download.DOWNLOADING)
					{
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
					}
				}
				
				// Change status to complete if this point was reached because downloading has finished
				
				if (download.getStatus() == Download.DOWNLOADING) 
				{
					download.setStatus(Download.COMPLETE);
					new File(mDestination + "/" + download.getTitle() + "." + download.getId() +".shared.zip.tmp").renameTo(new File(mDestination + "/" + download.getTitle() + ".zip"));
					//TODO: NOTIFY???
					publishProgress();
				}
				
				
				Log.i(TAG, "Finished!");
				connection.disconnect();
			} catch (Exception e) {
				e.printStackTrace();
				Log.i(TAG, "Exception Error = " + e.getMessage());
				download.setStatus(Download.ERROR);
				publishProgress();
			} finally {
				// Close file.
				if (file != null) {
					try {
						file.close();
					} catch (Exception e) {}
				}
				
				// Close connection to server.
				if (is != null) {
					try {
						is.close();
					} catch (Exception e) {}
				}
			}
			
			return download;
		}
		
		protected void onProgressUpdate(Object... values) 
		{
			notifyObservers();
		}

		protected void onPostExecute(Download download) 
		{
			Log.i(TAG, "onPostExecute");
			SharedDeckDownload sharedDownload = (SharedDeckDownload) download;
			sharedDownload.setStatus(SharedDeckDownload.UPDATE);
			notifyObservers();
			
			try {
				// Unzip deck and media
				unzipSharedDeckFile(mDestination + "/" + download.getTitle() + ".zip", sharedDownload.getTitle());
				// Update all the cards in the deck
				new UpdateDeckTask().execute(new Payload(new Object[] {mDestination + "/" + download.getTitle() + ".anki", download}));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	// It seemed to work better with DeckTask: TRY AGAIN and compare them!

	private class UpdateDeckTask extends AsyncTask<Connection.Payload, Connection.Payload, Connection.Payload> {

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			// Pass
		}

		@Override
		protected Payload doInBackground(Payload... args) {
			Payload data = doInBackgroundLoadDeck(args);
			if(data.returnType == AnkiDroid.DECK_LOADED)
			{
				HashMap<String,Object> results = (HashMap<String, Object>) data.result;
				Deck deck = (Deck) results.get("deck");
				deck.updateAllCards();
				//results.put("card", deck.getCurrentCard());
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
			String deckFilename = (String) data.data[0];
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
				data.returnType = AnkiDroid.DECK_LOADED;
				HashMap<String,Object> results = new HashMap<String,Object>();
				results.put("deck", deck);
				results.put("card", card);
				data.result = results;
				return data;
			} catch (SQLException e)
			{
				Log.i(TAG, "The database " + deckFilename + " could not be opened = " + e.getMessage());
				data.returnType = AnkiDroid.DECK_NOT_LOADED;
				return data;
			} catch (CursorIndexOutOfBoundsException e)
			{
				Log.i(TAG, "The deck has no cards = " + e.getMessage());
				data.returnType = AnkiDroid.DECK_EMPTY;
				return data;
			}
		}
		
		@Override
		protected void onPostExecute(Payload result) {
			super.onPostExecute(result);
			if(result.returnType == AnkiDroid.DECK_LOADED)
			{
				HashMap<String,Object> results = (HashMap<String, Object>) result.result;
				Deck deck = (Deck) results.get("deck");
				// Close the previously opened deck.
				if (deck != null)
				{
					deck.closeDeck();
				}
				mDownloads.remove((Download)result.data[1]);
				notifyObservers();
				showNotification(deck.deckName);
			}
		}
	}
	
	private static final class IncompleteDownloadsFilter implements FileFilter
	{
		public boolean accept(File pathname)
		{
			if(pathname.isFile() && pathname.getName().endsWith(".shared.anki.tmp"))
			{
				return true;
			}
			return false;
		}
	}
	
	
	
	private static String unzipSharedDeckFile(String zipFilename, String title) throws IOException
	{
		String deckFilename = "";
		
		Log.i(TAG, "Unzipping " + zipFilename + "...");
		InputStream is = null;
		if(zipFilename.endsWith(".zip"))
		{
			ZipFile zipFile = new ZipFile(zipFilename);

			title = title.replace("^", "");
			title = title.substring(0, java.lang.Math.min(title.length(), 40));
			
			if(new File(mDestination + "/" + title + ".anki").exists())
				title += System.currentTimeMillis();
			
			String partialDeckPath = mDestination + "/" + title;
			deckFilename = partialDeckPath + ".anki";
			
			ZipEntry zipEntry = null;
			Enumeration zipEntries = zipFile.entries();
			while(zipEntries.hasMoreElements())
			{
				zipEntry = (ZipEntry) zipEntries.nextElement();
				is = zipFile.getInputStream(zipEntry);
				//Log.i(TAG, "zipEntry = " + zipEntry.getName());
				
				if("shared.anki".equalsIgnoreCase(zipEntry.getName()))
				{
					Utils.writeToFile(is, deckFilename);
				}
				else if(zipEntry.getName().startsWith("shared.media/", 0))
				{
					//Log.i(TAG, "Folder created = " + new File(AnkiDroidApp.getStorageDirectory() + title + ".media/").mkdir());
					//Log.i(TAG, "Destination = " + AnkiDroidApp.getStorageDirectory() + "/" + title + ".media/" + zipEntry.getName().replace("shared.media/", ""));
					Utils.writeToFile(is, partialDeckPath + ".media/" + zipEntry.getName().replace("shared.media/", ""));
				}
			}
			is.close();
			
			// TODO: Finish he workflow of temporal files (when a temporal file is renamed, how...) and see that is consistent in any scenario
			new File(zipFilename).delete();
		}
		
		Log.i(TAG, "Deck unzipped!");
		return deckFilename;
	}
	
	public static interface DownloadsListener {
		
		public void onStateChanged(List<Download> downloads);
		
	}
	
	private synchronized void notifyObservers()
	{
		Log.i(TAG, "Notifying observers...");
		for(int i = 0; i < mListeners.size(); i++)
		{
			mListeners.get(i).onStateChanged(mDownloads);
		}
	}
	
	public void registerListener(DownloadsListener listener)
	{
		mListeners.add(listener);
	}
	
	public void unregisterListener(DownloadsListener listener)
	{
		mListeners.remove(listener);
	}
	
	
	/**
	 * Show a notification informing the user when a deck is downloaded
	 */
	private void showNotification(String deckTitle) 
	{
		NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

		// In this sample, we'll use the same text for the ticker and the expanded notification
		//CharSequence text = getText(R.string.remote_service_started);

		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(R.drawable.anki, "Download finished", System.currentTimeMillis());

		Intent loadDeckIntent = new Intent(mContext, StudyOptions.class);
		loadDeckIntent.putExtra(StudyOptions.OPT_DB, mDestination + "/" + deckTitle + ".anki");
		loadDeckIntent.setData(Uri.parse(deckTitle));
		
		// The PendingIntent to launch our activity if the user selects this notification
		PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, loadDeckIntent, 0);

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(mContext, deckTitle, "Deck downloaded", contentIntent);

		// Clear the notification when the user selects it
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		
		// Vibrate
		notification.defaults |= Notification.DEFAULT_VIBRATE;
		
		// Show a blue light
		notification.ledARGB = 0xff0000ff;
		notification.ledOnMS = 500;
		notification.ledOffMS = 1000;
		notification.flags |= Notification.FLAG_SHOW_LIGHTS;
		
		//notification.number = 1;
		
		// Send the notification.
		mNotificationManager.notify(mNotificationCounter++, notification);
	}

}
