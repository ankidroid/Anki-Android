package com.ichi2.anki;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
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
import android.content.Context;
import android.content.Intent;
import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;

public class DownloadManager {

	private static final String TAG = "AnkiDroid";

	public static final int ALL_DOWNLOADS = 0;
	public static final int PERSONAL_DECK_DOWNLOAD = 1;
	public static final int SHARED_DECK_DOWNLOAD = 2;
	
	private static final String ANKI_URL = "http://anki.ichi2.net";
	private static final String SYNC_URL = ANKI_URL + "/sync/";
	// Max size of download buffer.
	private static final int MAX_BUFFER_SIZE = 1024;

	static DownloadManager mSharedInstance;
	
	private Context mContext;
	private static String mUsername;
	private static String mPassword;
	private static String mDestination;
	
	private ArrayList<Download> mPersonalDeckDownloads;
	private ArrayList<DownloadsListener> mPersonalDeckListeners;
	
	private ArrayList<SharedDeckDownload> mSharedDeckDownloads;
	private ArrayList<DownloadsListener> mSharedDeckListeners;
	
	private int mNotificationCounter = 0;
	
	public synchronized static DownloadManager getSharedInstance(Context context, String username, String password, String destination)
	{
		if (mSharedInstance == null || !username.equalsIgnoreCase(mUsername) || !password.equalsIgnoreCase(mPassword) || !destination.equalsIgnoreCase(destination))
		{
			mSharedInstance = new DownloadManager(context, username, password, destination);
		}
		return mSharedInstance;
	}

	public DownloadManager(Context context, String username, String password, String destination)
	{
		mContext = context;
		mUsername = username;
		mPassword = password;
		mDestination = destination;
		mPersonalDeckDownloads = new ArrayList<Download>();
		mPersonalDeckListeners = new ArrayList<DownloadsListener>();
		mSharedDeckDownloads = new ArrayList<SharedDeckDownload>();
		mSharedDeckListeners = new ArrayList<DownloadsListener>();
		
		//addIncompleteDownloads();
		//resumeDownloads();
	}

	private void addIncompleteDownloads() 
	{
		Log.i(TAG, "DownloadManager - Adding incomplete downloads:");
		File dir = new File(mDestination);
		File[] fileList = dir.listFiles(new IncompleteDownloadsFilter());
		
		if(fileList != null)
		{
			for(int i = 0; i < fileList.length; i++)
			{
				File file = fileList[i];
				
				
				String filename = file.getName();
				// Personal decks
				if(filename.endsWith(".anki.tmp"))
				{
					mPersonalDeckDownloads.add(new Download(filename.substring(0, filename.length() - ".anki.tmp".length()), file.length()));
					Log.i(TAG, "Incomplete download of deck = " + mPersonalDeckDownloads.get(mPersonalDeckDownloads.size() - 1).getTitle() + ", downloaded = " +  mPersonalDeckDownloads.get(mPersonalDeckDownloads.size() - 1).getDownloaded() + "B");
				}
				// Shared decks
				else if(filename.endsWith(".shared.zip.tmp"))
				{
					filename = filename.substring(0, filename.length() - ".shared.zip.tmp".length());
					Log.i(TAG, "filename = " + filename);
					int lastDotPosition = filename.lastIndexOf(".");
					Log.i(TAG, "lastDotPosition = " + lastDotPosition);
					String identifier = filename.substring(lastDotPosition + 1, filename.length());
					Log.i(TAG, "identifier = " + identifier);
					String title = filename.substring(0, lastDotPosition);
					Log.i(TAG, "Title = " + title);
					mSharedDeckDownloads.add(new SharedDeckDownload(Integer.parseInt(identifier), title, file.length()));
					Log.i(TAG, "Incomplete download of deck = " + mSharedDeckDownloads.get(mSharedDeckDownloads.size() - 1).getTitle() + ", identifier = " + identifier + " downloaded = " +  mSharedDeckDownloads.get(mSharedDeckDownloads.size() - 1).getDownloaded() + "B");
				}
			}
			notifyObservers();
		}
	}
	
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
		
		// Download remaining personal deck downloads
		for(j = i; j < personalDeckDownloadsSize; j++)
		{
			resumeDownload(mPersonalDeckDownloads.get(j));
		}
		
		// Download remaining shared deck downloads
		for(j = i; j < sharedDeckDownloadsSize; j++)
		{
			resumeDownload(mSharedDeckDownloads.get(j));
		}
	}
	
	public List<Download> getDownloadsList(int downloadType)
	{
		ArrayList<Download> downloadsList = new ArrayList<Download>();
		
		switch(downloadType)
		{
			case PERSONAL_DECK_DOWNLOAD:
				downloadsList.addAll(mPersonalDeckDownloads);
				break;
				
			case SHARED_DECK_DOWNLOAD:
				downloadsList.addAll(mSharedDeckDownloads);
				break;
				
			case ALL_DOWNLOADS:
				downloadsList.addAll(mPersonalDeckDownloads);
				downloadsList.addAll(mSharedDeckDownloads);
				break;
		}
		return downloadsList;
	}
	
	public void downloadFile(Download download)
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
	
	public void resumeDownload(Download download)
	{
		if(download instanceof SharedDeckDownload)
		{
			new DownloadSharedDeckTask().execute(download);
		}
		else
		{
			new DownloadPersonalDeckTask().execute(download);
		}
	}
	
	private class DownloadPersonalDeckTask extends AsyncTask<Download, Object, Download> {
		
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
					// TODO: NOTIFY???
				}
				
				// Open file
				file = new RandomAccessFile(mDestination + "/" + download.getTitle() + ".anki.tmp", "rw");
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
					//TODO: NOTIFY???
				}
				
				// Change status to complete if this point was reached because downloading has finished
				if (download.getStatus() == Download.DOWNLOADING) 
				{
					download.setStatus(Download.COMPLETE);
					new File(mDestination + "/" + download.getTitle() + ".anki.tmp").renameTo(new File(mDestination + "/" + download.getTitle() + ".anki"));
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
				// Close file.
				if (file != null) {
					try {
						file.close();
					} catch (Exception e) {}
				}
				
				// Close connection to server.
				if (iis != null) {
					try {
						iis.close();
					} catch (Exception e) {}
				}
			}
			
			return download;
		}

		protected void onProgressUpdate(Object... values) 
		{
			notifyPersonalDeckObservers();
		}

		protected void onPostExecute(Download download) 
		{
			mPersonalDeckDownloads.remove(download);
			notifyPersonalDeckObservers();
			showNotification(download.getTitle());
		}
	}

	private class DownloadSharedDeckTask extends AsyncTask<Download, Object, Download> {
		
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
				file = new RandomAccessFile(mDestination + "/" + download.getTitle() + "." + download.getId() +".shared.zip.tmp", "rw");
				// FIXME:  Uncomment next line when the connection is fixed on AnkiOnline (= when the connection only returns the bytes specified on the range property)
				//file.seek(download.getDownloaded());
				
				is = connection.getInputStream();
				
				//if(download.getFilename().endsWith(".zip"))
				//{
					
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
					}
					
				//Utils.writeToFile(is, mDestination + "/" + download.getTitle() + ".zip");
				//}
				
				// Change status to complete if this point was reached because downloading has finished
				if (download.getStatus() == Download.DOWNLOADING) 
				{
					download.setStatus(Download.COMPLETE);
					new File(mDestination + "/" + download.getTitle() + "." + download.getId() +".shared.zip.tmp").renameTo(new File(mDestination + "/" + download.getTitle() + ".zip"));
					//TODO: NOTIFY???
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
			notifySharedDeckObservers();
		}

		protected void onPostExecute(Download download) 
		{
			Log.i(TAG, "onPostExecute");
			SharedDeckDownload sharedDownload = (SharedDeckDownload) download;
			sharedDownload.setStatus(SharedDeckDownload.UPDATE);
			notifySharedDeckObservers();
			
			
			try {
				// Unzip deck and media
				String unzippedDeckName = unzipSharedDeckFile(mDestination + "/" + download.getTitle() + ".zip", sharedDownload.getTitle());
				// Update all the cards in the deck
				new UpdateDeckTask().execute(new Payload(new Object[] {unzippedDeckName, download}));
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
			String deckName = (String) data.data[0];
			String deckFilename = mDestination + "/" + deckName + ".anki";
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
				data.success = false;
				data.returnType = AnkiDroid.DECK_NOT_LOADED;
				data.exception = e;
				return data;
			} catch (CursorIndexOutOfBoundsException e)
			{
				Log.i(TAG, "The deck has no cards = " + e.getMessage());
				data.success = false;
				data.returnType = AnkiDroid.DECK_EMPTY;
				data.exception = e;
				return data;
			}
		}
		
		@Override
		protected void onPostExecute(Payload result) {
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
				mSharedDeckDownloads.remove((Download)result.data[1]);
				notifySharedDeckObservers();
				showNotification((String)result.data[0]);
			}
		}
	}
	
	private static final class IncompleteDownloadsFilter implements FileFilter
	{
		public boolean accept(File pathname)
		{
			if(pathname.isFile() && (pathname.getName().endsWith(".anki.tmp") || pathname.getName().endsWith(".shared.zip.tmp")))
			{
				return true;
			}
			return false;
		}
	}
	
	public static interface DownloadsListener {
		
		public void onStateChanged(List downloads);
		
	}
	
	private synchronized void notifyObservers()
	{
		notifyPersonalDeckObservers();
		notifySharedDeckObservers();
	}
	
	private synchronized void notifyPersonalDeckObservers()
	{
		for(int i = 0; i < mPersonalDeckListeners.size(); i++)
		{
			mPersonalDeckListeners.get(i).onStateChanged(mPersonalDeckDownloads);
		}
	}
	
	private synchronized void notifySharedDeckObservers()
	{
		for(int i = 0; i < mSharedDeckListeners.size(); i++)
		{
			mSharedDeckListeners.get(i).onStateChanged(mSharedDeckDownloads);
		}
	}
	
	public void registerListener(int downloadType, DownloadsListener listener)
	{
		switch(downloadType)
		{
			case PERSONAL_DECK_DOWNLOAD:
				mPersonalDeckListeners.add(listener);
				break;
				
			case SHARED_DECK_DOWNLOAD:
				mSharedDeckListeners.add(listener);
				break;
				
			case ALL_DOWNLOADS:
				mPersonalDeckListeners.add(listener);
				mSharedDeckListeners.add(listener);
				break;
		}
	}
	
	public void unregisterListener(DownloadsListener listener)
	{
		mPersonalDeckListeners.remove(listener);
		mSharedDeckListeners.remove(listener);
	}
	
	private static String unzipSharedDeckFile(String zipFilename, String title) throws IOException
	{
		ZipInputStream zipInputStream = null;
		Log.i(TAG, "unzipSharedDeckFile");
		if(zipFilename.endsWith(".zip"))
		{
			Log.i(TAG, "zipFilename ends with .zip");
			zipInputStream = new ZipInputStream(new FileInputStream(new File(zipFilename)));

			title = title.replace("^", "");
			title = title.substring(0, java.lang.Math.min(title.length(), 40));

			if(new File(mDestination + "/" + title + ".anki").exists())
				title += System.currentTimeMillis();

			String partialDeckPath = mDestination + "/" + title;
			String deckFilename = partialDeckPath + ".anki";

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
					Log.i(TAG, "Folder created = " + new File(AnkiDroidApp.getStorageDirectory() + title + ".media/").mkdir());
					Log.i(TAG, "Destination = " + AnkiDroidApp.getStorageDirectory() + "/" + title + ".media/" + zipEntry.getName().replace("shared.media/", ""));
					Utils.writeToFile(zipInputStream, partialDeckPath + ".media/" + zipEntry.getName().replace("shared.media/", ""));
				}
			}
			zipInputStream.close();
			
			new File(zipFilename).delete();
		}

		return title;
	}
	
	/**
	 * Show a notification informing the user when a deck is ready to be used
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
