/***************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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

package com.ichi2.anki.services;

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
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Download;
import com.ichi2.anki.R;
import com.ichi2.anki.SharedDeckDownload;
import com.ichi2.anki.StudyOptionsFragment;
import com.ichi2.async.Connection;
import com.ichi2.async.DeckTask;
import com.ichi2.async.Connection.Payload;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.sync.BasicHttpSyncer;

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
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DownloadManagerService extends Service {

    // private int counter = 0;
    // private Handler serviceHandler = null;

    // Max size of download buffer.
    private static final int MAX_BUFFER_SIZE = 1024;

    // Regex for finding incomplete downloads shared preferences
    private static final Pattern sNumUpdatedCardsPattern = Pattern
            .compile("^numUpdatedCards:.*/([^/]+\\.anki\\.updating)$");
    private static final Pattern sPausedPattern = Pattern.compile("^paused:.*/([^/]+\\.anki\\.updating)$");

    private String mUsername;
    private String mPassword;
    private String mDestination;

    private ArrayList<Download> mPersonalDeckDownloads;
    private final RemoteCallbackList<IPersonalDeckServiceCallback> mPersonalDeckCallbacks = new RemoteCallbackList<IPersonalDeckServiceCallback>();

    private ArrayList<SharedDeckDownload> mSharedDeckDownloads;
    private final RemoteCallbackList<ISharedDeckServiceCallback> mSharedDeckCallbacks = new RemoteCallbackList<ISharedDeckServiceCallback>();


    /********************************************************************
     * Lifecycle methods *
     ********************************************************************/

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(AnkiDroidApp.TAG, "Service - onCreate");
        mPersonalDeckDownloads = new ArrayList<Download>();
        mSharedDeckDownloads = new ArrayList<SharedDeckDownload>();

        restorePreferences();

        // If there is incomplete work, finish it
        addIncompleteDownloads();
        // Clean up shared preferences of completed downloads
        removeCompletedDownloadsPrefs();
        resumeDownloads();

        // serviceHandler = new Handler();
        // serviceHandler.postDelayed( new RunTask(),1000L );
    }


    @Override
    public void onStart(Intent intent, int startId) {
        Log.i(AnkiDroidApp.TAG, "Service - onStart");
        super.onStart(intent, startId);
        restorePreferences();
    }


    @Override
    public IBinder onBind(Intent intent) {
        Log.i(AnkiDroidApp.TAG, "Service - onBind");
        restorePreferences();
        return mBinder;
    }


    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(AnkiDroidApp.TAG, "onUnbind");
        return super.onUnbind(intent);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(AnkiDroidApp.TAG, "Service - onDestroy");
        // serviceHandler = null;
    }


    public void stopIfFinished() {
        if (!hasMoreWork()) {
            // Delete tmp folder
            boolean deleted = new File(mDestination + "/tmp").delete();
            Log.i(AnkiDroidApp.TAG, mDestination + "/tmp folder was deleted = " + deleted);
            Log.i(AnkiDroidApp.TAG, "Service stopping itself...");
            stopSelf();
        }
    }


    /********************************************************************
     * Custom methods *
     ********************************************************************/

    private boolean hasMoreWork() {
        return (mPersonalDeckDownloads.size() != 0 || mSharedDeckDownloads.size() != 0);
    }


    private void restorePreferences() {
        SharedPreferences pref = AnkiDroidApp.getSharedPrefs(getBaseContext());
        mUsername = pref.getString("username", "");
        mPassword = pref.getString("password", "");
        mDestination = AnkiDroidApp.getCurrentAnkiDroidDirectory();
    }


    // It could be part of the AIDL Interface but at the moment no Activity uses it directly
    public void addIncompleteDownloads() {
        Log.i(AnkiDroidApp.TAG, "DownloadManagerService - Adding incomplete downloads:");

        File dir = new File(mDestination + "/tmp/");
        File[] fileList = dir.listFiles(new IncompleteDownloadsFilter());

        if (fileList != null) {
            for (File file : fileList) {
                String filename = file.getName();
                Log.i(AnkiDroidApp.TAG, "Filename = " + filename);

                // Personal decks
                if (filename.endsWith(".anki.tmp")) {
                    Download download = new Download(filename.substring(0, filename.length() - ".anki.tmp".length()));
                    download.setDownloaded(file.length());
                    mPersonalDeckDownloads.add(download);
                }
                // Shared decks
                else if (filename.endsWith(".shared.zip.tmp")) {
                    filename = filename.substring(0, filename.length() - ".shared.zip.tmp".length());
                    int lastDotPosition = filename.lastIndexOf(".");
                    String identifier = filename.substring(lastDotPosition + 1, filename.length());
                    String title = filename.substring(0, lastDotPosition);

                    SharedDeckDownload download = new SharedDeckDownload(Integer.parseInt(identifier), title);
                    download.setDownloaded(file.length());
                    mSharedDeckDownloads.add(download);
                }
                // Shared but not totally updated decks
                else if (filename.endsWith(".anki.updating")) {
                    String title = filename.substring(0, filename.length() - ".anki.updating".length());
                    SharedDeckDownload download = new SharedDeckDownload(title);

                    SharedPreferences pref = AnkiDroidApp.getSharedPrefs(getBaseContext());
                    String pausedPref = "paused:" + mDestination + "/tmp/" + download.getFilename() + ".anki.updating";
                    if (pref.getBoolean(pausedPref, false)) {
                        download.setStatus(SharedDeckDownload.STATUS_PAUSED);
                    } else {
                        download.setStatus(SharedDeckDownload.STATUS_UPDATING);
                    }
                    mSharedDeckDownloads.add(download);
                }
            }
            notifyObservers();
        }
        // If no decks were added, stop the service
        stopIfFinished();
    }


    /**
     * Cleans up the SharedPreferences space from numUpdatedCards records of downloads that have been completed or
     * cancelled.
     */
    public void removeCompletedDownloadsPrefs() {
        Log.i(AnkiDroidApp.TAG,  "DownloadManagerService - Removing shared preferences of completed or cancelled downloads");

        File dir = new File(mDestination + "/tmp/");
        File[] fileList = dir.listFiles(new IncompleteDownloadsFilter());
        HashSet<String> filenames = new HashSet<String>();

        // Get all incomplete downloads filenames
        if (fileList != null) {
            for (File file : fileList) {
                filenames.add(file.getName());
            }
        }

        // Remove any download related shared preference that doesn't have a corresponding incomplete file
        SharedPreferences pref = AnkiDroidApp.getSharedPrefs(getBaseContext());
        Matcher sharedPrefMatcher;
        Editor editor = pref.edit();
        boolean sharedPreferencesChanged = false;
        for (String key : pref.getAll().keySet()) {
            sharedPrefMatcher = sNumUpdatedCardsPattern.matcher(key);
            if (sharedPrefMatcher.matches() && sharedPrefMatcher.groupCount() > 0) {
                if (!filenames.contains(sharedPrefMatcher.group(1))) {
                    editor.remove(key);
                    sharedPreferencesChanged = true;
                }
            }
            sharedPrefMatcher = sPausedPattern.matcher(key);
            if (sharedPrefMatcher.matches() && sharedPrefMatcher.groupCount() > 0) {
                if (!filenames.contains(sharedPrefMatcher.group(1))) {
                    editor.remove(key);
                    sharedPreferencesChanged = true;
                }
            }
        }
        if (sharedPreferencesChanged) {
            editor.commit();
        }
    }


    // It could be part of the AIDL Interface but at the moment no Activity uses it directly
    public void resumeDownloads() {
        int i = 0;
        int j = 0;

        int personalDeckDownloadsSize = mPersonalDeckDownloads.size();
        int sharedDeckDownloadsSize = mSharedDeckDownloads.size();

        // Resume both personal deck downloads and shared deck downloads
        for (i = 0; i < personalDeckDownloadsSize && i < sharedDeckDownloadsSize; i++) {
            resumeDownload(mPersonalDeckDownloads.get(i));
            resumeDownload(mSharedDeckDownloads.get(i));
        }

        // Resume remaining personal deck downloads
        for (j = i; j < personalDeckDownloadsSize; j++) {
            resumeDownload(mPersonalDeckDownloads.get(j));
        }

        // Resume remaining shared deck downloads
        for (j = i; j < sharedDeckDownloadsSize; j++) {
            resumeDownload(mSharedDeckDownloads.get(j));
        }
    }


    // It could be part of the AIDL Interface but at the moment no Activity uses it directly
    public void resumeDownload(Download download) {
        // Create tmp folder where the temporal decks are going to be stored
        new File(mDestination + "/tmp/").mkdirs();
        AnkiDroidApp.createNoMediaFileIfMissing(new File(mDestination));

        if (download instanceof SharedDeckDownload) {
            SharedDeckDownload sharedDeckDownload = (SharedDeckDownload) download;
            // We need to go through UpdateDeckTask even when the download is paused, in order for
            // numUpdatedCards and numTotalCards to get updated, so that progress is displayed correctly
            if (sharedDeckDownload.getStatus() == SharedDeckDownload.STATUS_PAUSED
                    || sharedDeckDownload.getStatus() == SharedDeckDownload.STATUS_UPDATING) {
                new UpdateDeckTask().execute(new Payload(new Object[] { sharedDeckDownload }));
            } else {
                new DownloadSharedDeckTask().execute(sharedDeckDownload);
            }
        } else {
            // TODO: Check if there is already a deck with the same name, and if that's so
            // add the current milliseconds to the end of the name or notify the user
            new DownloadPersonalDeckTask().execute(download);
        }
    }


    private String unzipSharedDeckFile(String zipFilename, String title) {
        ZipInputStream zipInputStream = null;
        Log.i(AnkiDroidApp.TAG, "unzipSharedDeckFile");
        if (zipFilename.endsWith(".zip")) {
            Log.i(AnkiDroidApp.TAG, "zipFilename ends with .zip");
            try {
                zipInputStream = new ZipInputStream(new FileInputStream(new File(zipFilename)));

                if (new File(mDestination + "/" + title + ".anki").exists()) {
                    title += System.currentTimeMillis();
                }

                String partialDeckPath = mDestination + "/tmp/" + title;
                String deckFilename = partialDeckPath + ".anki.updating";

                ZipEntry zipEntry = null;
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    Log.i(AnkiDroidApp.TAG, "zipEntry = " + zipEntry.getName());

                    if ("shared.anki".equalsIgnoreCase(zipEntry.getName())) {
                        Utils.writeToFile(zipInputStream, deckFilename);
                    } else if (zipEntry.getName().startsWith("shared.media/", 0)) {
                        Log.i(AnkiDroidApp.TAG, "Folder created = " + new File(partialDeckPath + ".media/").mkdir());
                        Log.i(AnkiDroidApp.TAG, "Destination = " + AnkiDroidApp.getCurrentAnkiDroidDirectory() + "/" + title  + ".media/" + zipEntry.getName().replace("shared.media/", ""));
                        Utils.writeToFile(zipInputStream,
                                partialDeckPath + ".media/" + zipEntry.getName().replace("shared.media/", ""));
                    }
                }
                zipInputStream.close();

                // Delete zip file
                new File(zipFilename).delete();
            } catch (FileNotFoundException e) {
                Log.e(AnkiDroidApp.TAG, "FileNotFoundException = " + e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(AnkiDroidApp.TAG, "IOException = " + e.getMessage());
                e.printStackTrace();
            }
        }

        return title;
    }


    private int getNextNotificationId() {
        // Retrieve previously saved value
        SharedPreferences pref = AnkiDroidApp.getSharedPrefs(getBaseContext());
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
     * Notification methods *
     ********************************************************************/

    /**
     * Show a notification informing the user when a deck is ready to be used
     */
    private void showNotification(String deckTitle, String deckFilename) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Resources res = getResources();

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.anki, res.getString(R.string.download_finished),
                System.currentTimeMillis());

        String deckPath = mDestination + "/" + deckFilename + ".anki";
        // Intent loadDeckIntent = StudyOptions.getLoadDeckIntent(this, deckPath);
        // The PendingIntent to launch our activity if the user selects this notification
        // PendingIntent contentIntent = PendingIntent.getActivity(this, 0, loadDeckIntent, 0);

        // Set the info for the views that show in the notification panel
        // notification.setLatestEventInfo(this, deckTitle, res.getString(R.string.deck_downloaded), contentIntent);

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
        Log.i(AnkiDroidApp.TAG, "Sending notification...");
        mNotificationManager.notify(getNextNotificationId(), notification);
    }


    private synchronized void notifyObservers() {
        notifyPersonalDeckObservers();
        notifySharedDeckObservers();
    }


    private synchronized void notifyPersonalDeckObservers() {
        final int numPersonalDeckCallbacks = mPersonalDeckCallbacks.beginBroadcast();
        for (int i = 0; i < numPersonalDeckCallbacks; i++) {
            try {
                mPersonalDeckCallbacks.getBroadcastItem(i).publishProgress(mPersonalDeckDownloads);
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service has crashed
                Log.e(AnkiDroidApp.TAG, "RemoteException = " + e.getMessage());
                e.printStackTrace();
            }
        }
        mPersonalDeckCallbacks.finishBroadcast();
    }


    private synchronized void notifySharedDeckObservers() {
        Log.i(AnkiDroidApp.TAG, "notifySharedDeckObservers");
        final int numSharedDeckCallbacks = mSharedDeckCallbacks.beginBroadcast();
        for (int i = 0; i < numSharedDeckCallbacks; i++) {
            try {
                mSharedDeckCallbacks.getBroadcastItem(i).publishProgress(mSharedDeckDownloads);
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service has crashed
                Log.e(AnkiDroidApp.TAG, "RemoteException = " + e.getMessage());
                e.printStackTrace();
            }
        }
        mSharedDeckCallbacks.finishBroadcast();
    }

    /********************************************************************
     * Filters *
     ********************************************************************/

    private static final class IncompleteDownloadsFilter implements FileFilter {
        public boolean accept(File pathname) {
            String filename = pathname.getName();
            // The filter searches for unfinished tasks:
            // * not completed personal deck downloads
            // * not completed shared deck downloads
            // * downloaded but not totally updated shared decks
            if (pathname.isFile()
                    && (filename.endsWith(".anki.tmp") || filename.endsWith(".shared.zip.tmp") || filename
                            .endsWith(".anki.updating"))) {
                return true;
            }
            return false;
        }
    }

    /********************************************************************
     * Interfaces *
     ********************************************************************/

    /**
     * IDownloadManagerService is defined through IDL
     */
    private final IDownloadManagerService.Stub mBinder = new IDownloadManagerService.Stub() {

        @Override
        public void registerPersonalDeckCallback(IPersonalDeckServiceCallback cb) {
            Log.i(AnkiDroidApp.TAG, "registerPersonalDeckCallback");
            if (cb != null) {
                mPersonalDeckCallbacks.register(cb);
                notifyPersonalDeckObservers();
            }
        }


        @Override
        public void unregisterPersonalDeckCallback(IPersonalDeckServiceCallback cb) {
            Log.i(AnkiDroidApp.TAG, "unregisterPersonalDeckCallback");
            if (cb != null) {
                mPersonalDeckCallbacks.unregister(cb);
            }
        }


        @Override
        public void registerSharedDeckCallback(ISharedDeckServiceCallback cb) throws RemoteException {
            Log.i(AnkiDroidApp.TAG, "registerSharedDeckCallback");
            if (cb != null) {
                mSharedDeckCallbacks.register(cb);
                notifySharedDeckObservers();
            }
        }


        @Override
        public void unregisterSharedDeckCallback(ISharedDeckServiceCallback cb) throws RemoteException {
            Log.i(AnkiDroidApp.TAG, "unregisterSharedDeckCallback");
            if (cb != null) {
                mSharedDeckCallbacks.unregister(cb);
            }
        }


        @Override
        public void downloadFile(Download download) throws RemoteException {
            if (download instanceof SharedDeckDownload) {
                mSharedDeckDownloads.add((SharedDeckDownload) download);
            } else {
                mPersonalDeckDownloads.add(download);
            }
            resumeDownload(download);
        }


        @Override
        public void resumeDownloadUpdating(Download download) throws RemoteException {
            if (download instanceof SharedDeckDownload) {
                resumeDownload(download);
            }
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
     * Listeners *
     ********************************************************************/

    // public interface ProgressListener {
    // public void onProgressUpdate(Object... values);
    // }
    // private ProgressListener mUpdateListener = new ProgressListener() {
    // @Override
    // public void onProgressUpdate(Object... values) {
    // String deckPath = (String) values[0];
    // Long numUpdatedCards = (Long) values[1];
    // //Save on preferences
    // SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
    // Editor editor = pref.edit();
    // editor.putLong("numUpdatedCards:" + deckPath, numUpdatedCards); editor.commit();
    // }
    // };

    /********************************************************************
     * Async Tasks *
     ********************************************************************/

    private class DownloadPersonalDeckTask extends AsyncTask<Download, Object, Download> {

        @Override
        protected Download doInBackground(Download... downloads) {
            Download download = downloads[0];

            URL url;
            RandomAccessFile file = null;
            InflaterInputStream iis = null;

            try {
                url = new URL(Collection.SYNC_URL + "fulldown");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setRequestMethod("POST");
                // FIXME: The connection always returns all bytes, regardless of what is indicated in range property, so
                // resuming downloads of personal decks is not possible at the moment
                // Fix this when the connection is fixed on AnkiOnline
                Log.i(AnkiDroidApp.TAG, "Range = " + download.getDownloaded());
                // connection.setRequestProperty("Range","bytes=" + download.getDownloaded() + "-");
                connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");

                connection.connect();

                long startTime = System.currentTimeMillis();

                DataOutputStream ds = new DataOutputStream(connection.getOutputStream());
                String data = "p=" + URLEncoder.encode(mPassword, "UTF-8") + "&u="
                        + URLEncoder.encode(mUsername, "UTF-8") + "&d="
                        + URLEncoder.encode(download.getTitle(), "UTF-8");
                ds.writeBytes(data);
                Log.i(AnkiDroidApp.TAG, "Closing streams...");
                ds.flush();
                ds.close();

                // Make sure response code is in the 200 range.
                if (connection.getResponseCode() / 100 != 2) {
                    download.setStatus(Download.STATUS_ERROR);
                    publishProgress();
                } else {
                    download.setStatus(Download.STATUS_DOWNLOADING);
                    publishProgress();
                }

                Log.i(AnkiDroidApp.TAG, "Response code = " + connection.getResponseCode());

                // Check for valid content length.
                Log.i(AnkiDroidApp.TAG, "Connection length = " + connection.getContentLength());
                int contentLength = connection.getContentLength();
                if (contentLength < 1) {
                    Log.i(AnkiDroidApp.TAG, "Content Length = -1");
                    // download.setStatus(Download.ERROR);
                }

                // Set the size for this download if it hasn't been already set
                if (download.getSize() == -1 && contentLength != -1) {
                    download.setSize(contentLength);
                    Log.i(AnkiDroidApp.TAG, "File size = " + contentLength);
                }

                // Open file
                file = new RandomAccessFile(mDestination + "/tmp/" + download.getFilename() + ".anki.tmp", "rw");
                // FIXME: Uncomment next line when the connection is fixed on AnkiOnline (= when the connection only
                // returns the bytes specified on the range property)
                // file.seek(download.getDownloaded());

                iis = new InflaterInputStream(connection.getInputStream());

                int phase = 0;
                while (download.getStatus() == Download.STATUS_DOWNLOADING) {
                    // Size buffer according to how much of the file is left to download
                    Log.v(AnkiDroidApp.TAG, "Downloading... " + download.getDownloaded());
                    byte[] buffer;
                    // if (size - downloaded > MAX_BUFFER_SIZE) {
                    buffer = new byte[MAX_BUFFER_SIZE];
                    // } else {
                    // buffer = new byte[size - downloaded];
                    // }

                    // Read from server into buffer.
                    int read = iis.read(buffer);
                    if (read == -1) {
                        break;
                    }

                    // Write buffer to file.
                    file.write(buffer, 0, read);
                    download.setDownloaded(download.getDownloaded() + read);
                    // Less frequent updates
                    phase++;
                    if (phase == 249) {
                        phase = 0;
                        publishProgress();
                    }
                }

                if (download.getStatus() == Download.STATUS_DOWNLOADING) {
                    // Change status to complete if this point was reached because downloading has finished
                    download.setStatus(Download.STATUS_COMPLETE);
                    new File(mDestination + "/tmp/" + download.getFilename() + ".anki.tmp").renameTo(new File(
                            mDestination + "/" + download.getFilename() + ".anki"));
                    long finishTime = System.currentTimeMillis();
                    Log.i(AnkiDroidApp.TAG, "Finished in " + ((finishTime - startTime) / 1000) + " seconds!");
                    Log.i(AnkiDroidApp.TAG, "Downloaded = " + download.getDownloaded());
                } else if (download.getStatus() == Download.STATUS_CANCELLED) {
                    // Cancelled download, clean up
                    new File(mDestination + "/tmp/" + download.getFilename() + ".anki.tmp").delete();
                    Log.i(AnkiDroidApp.TAG, "Download cancelled.");
                }
                publishProgress();
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                Log.i(AnkiDroidApp.TAG, "Exception Error = " + e.getMessage());
                download.setStatus(Download.STATUS_ERROR);
                publishProgress();
            } finally {
                Log.i(AnkiDroidApp.TAG, "finally");
                // Close file
                if (file != null) {
                    try {
                        Log.i(AnkiDroidApp.TAG, "closing file");
                        file.close();
                    } catch (Exception e) {
                        Log.i(AnkiDroidApp.TAG, "exception closing file");
                    }
                }

                // Close connection to server
                if (iis != null) {
                    try {
                        Log.i(AnkiDroidApp.TAG, "closing iis");
                        iis.close();
                        Log.i(AnkiDroidApp.TAG, "closed iis");
                    } catch (Exception e) {
                        Log.i(AnkiDroidApp.TAG, "exception closing iis: " + e.getMessage());
                    }
                }
            }

            return download;
        }


        @Override
        protected void onProgressUpdate(Object... values) {
            notifyPersonalDeckObservers();
        }


        @Override
        protected void onPostExecute(Download download) {
            Log.i(AnkiDroidApp.TAG, "on post execute");
            if (download.getStatus() == Download.STATUS_COMPLETE) {
                showNotification(download.getTitle(), download.getFilename());
            } else if (download.getStatus() == Download.STATUS_ERROR) {
                // Error - Clean up
                Log.i(AnkiDroidApp.TAG, "deleting file");
                new File(mDestination + "/tmp/" + download.getFilename() + ".anki.tmp").delete();
                Log.e(AnkiDroidApp.TAG, "Error while downloading personal deck.");
            }
            mPersonalDeckDownloads.remove(download);
            notifyPersonalDeckObservers();
            stopIfFinished();
        }
    }

    private class DownloadSharedDeckTask extends AsyncTask<Download, Object, SharedDeckDownload> {

        @Override
        protected SharedDeckDownload doInBackground(Download... downloads) {
            // SharedDeckDownload download = (SharedDeckDownload) downloads[0];
            //
            // URL url;
            // RandomAccessFile file = null;
            // InputStream is = null;
            //
            // try {
            // url = new URL("http://" + Collection.SYNC_HOST + "/file/get?id=" + download.getId());
            // HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            //
            // connection.setDoInput(true);
            // connection.setDoOutput(true);
            // connection.setUseCaches(false);
            // connection.setRequestMethod("GET");
            // Log.i(AnkiDroidApp.TAG, "Range = " + download.getDownloaded());
            // // FIXME: Seems that Range property is also not working well here -> TEST IT!
            // // connection.setRequestProperty("Range","bytes=" + download.getDownloaded() + "-");
            // connection.setRequestProperty("Accept-Encoding", "identity");
            // // connection.setRequestProperty("Host", Collection.SYNC_HOST);
            // connection.setRequestProperty("Connection", "close");
            //
            // connection.connect();
            //
            // long startTime = System.currentTimeMillis();
            //
            // // Make sure response code is in the 200 range.
            // if (connection.getResponseCode() / 100 != 2) {
            // download.setStatus(Download.STATUS_ERROR);
            // publishProgress();
            // } else {
            // download.setStatus(Download.STATUS_DOWNLOADING);
            // publishProgress();
            // }
            //
            // Log.i(AnkiDroidApp.TAG, "Response code = " + connection.getResponseCode());
            //
            // // Check for valid content length.
            // Log.i(AnkiDroidApp.TAG, "Connection length = " + connection.getContentLength());
            // int contentLength = connection.getContentLength();
            // if (contentLength < 1) {
            // Log.i(AnkiDroidApp.TAG, "Content Length = -1");
            // // download.setStatus(Download.ERROR);
            // }
            //
            // // Set the size for this download if it hasn't been already set
            // if (download.getSize() == -1 && contentLength != -1) {
            // download.setSize(contentLength);
            // Log.i(AnkiDroidApp.TAG, "File size = " + contentLength);
            // // TODO: NOTIFY???
            // }
            //
            // // Open file
            // file = new RandomAccessFile(mDestination + "/tmp/" + download.getFilename() + "." + download.getId()
            // + ".shared.zip.tmp", "rw");
            // // FIXME: Uncomment next line when the connection is fixed on AnkiOnline (= when the connection only
            // // returns the bytes specified on the range property)
            // // file.seek(download.getDownloaded());
            //
            // is = connection.getInputStream();
            //
            // while (download.getStatus() == Download.STATUS_DOWNLOADING) {
            // Log.i(AnkiDroidApp.TAG, "Downloading... " + download.getDownloaded());
            // byte[] buffer;
            // // if (size - downloaded > MAX_BUFFER_SIZE) {
            // buffer = new byte[MAX_BUFFER_SIZE];
            // // } else {
            // // buffer = new byte[size - downloaded];
            // // }
            //
            // // Read from server into buffer.
            // int read = is.read(buffer);
            // if (read == -1) {
            // break;
            // }
            //
            // // Write buffer to file.
            // file.write(buffer, 0, read);
            // download.setDownloaded(download.getDownloaded() + read);
            // publishProgress();
            // }
            //
            // if (download.getStatus() == Download.STATUS_DOWNLOADING) {
            // // Change status to complete if this point was reached because downloading has finished
            // download.setStatus(Download.STATUS_COMPLETE);
            // new File(mDestination + "/tmp/" + download.getFilename() + "." + download.getId() + ".shared.zip.tmp")
            // .renameTo(new File(mDestination + "/tmp/" + download.getFilename() + ".zip"));
            // long finishTime = System.currentTimeMillis();
            // Log.i(AnkiDroidApp.TAG, "Finished in " + ((finishTime - startTime) / 1000) + " seconds!");
            // Log.i(AnkiDroidApp.TAG, "Downloaded = " + download.getDownloaded());
            // } else if (download.getStatus() == Download.STATUS_CANCELLED) {
            // // Cancelled download, clean up
            // new File(mDestination + "/tmp/" + download.getFilename() + "." + download.getId()
            // + ".shared.zip.tmp").delete();
            // Log.i(AnkiDroidApp.TAG, "Download cancelled.");
            // }
            // publishProgress();
            // connection.disconnect();
            // } catch (Exception e) {
            // e.printStackTrace();
            // Log.i(AnkiDroidApp.TAG, "Exception Error = " + e.getMessage());
            // download.setStatus(Download.STATUS_ERROR);
            // publishProgress();
            // } finally {
            // // Close file
            // if (file != null) {
            // try {
            // file.close();
            // } catch (Exception e) {
            // }
            // }
            // // Close connection to server
            // if (is != null) {
            // try {
            // is.close();
            // } catch (Exception e) {
            // }
            // }
            // }

            // return download;
            return null;
        }


        @Override
        protected void onProgressUpdate(Object... values) {
            notifySharedDeckObservers();
        }


        @Override
        protected void onPostExecute(SharedDeckDownload download) {
            Log.i(AnkiDroidApp.TAG, "onPostExecute");
            if (download.getStatus() == Download.STATUS_COMPLETE) {
                download.setStatus(SharedDeckDownload.STATUS_UPDATING);
                notifySharedDeckObservers();

                // Unzip deck and media
                String unzippedDeckName = unzipSharedDeckFile(mDestination + "/tmp/" + download.getFilename() + ".zip",
                        download.getFilename());
                download.setTitle(unzippedDeckName);

                // Update all cards in deck
                SharedPreferences pref = AnkiDroidApp.getSharedPrefs(getBaseContext());
                Editor editor = pref.edit();
                editor.putLong("numUpdatedCards:" + mDestination + "/tmp/" + download.getFilename() + ".anki.updating",
                        0);
                editor.commit();

                new UpdateDeckTask().execute(new Payload(new Object[] { download }));

            } else if (download.getStatus() == Download.STATUS_CANCELLED) {
                mSharedDeckDownloads.remove(download);
                notifySharedDeckObservers();
                stopIfFinished();
            }
        }
    }

    private class UpdateDeckTask extends AsyncTask<Connection.Payload, Connection.Payload, Connection.Payload> {

        private static final int sRunningAvgLength = 5;
        private long[] mRecentBatchTimings;
        private double mTotalBatches;


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Pass
        }


        @Override
        protected void onProgressUpdate(Payload... values) {
            notifySharedDeckObservers();
        }


        @Override
        protected Payload doInBackground(Payload... args) {

            Payload data = doInBackgroundLoadDeck(args);
            if (data.returnType == 0) {// DeckTask.DECK_LOADED) {
                HashMap<String, Object> results = (HashMap<String, Object>) data.result;
                Decks deck = (Decks) results.get("deck");
                // if (!deck.isUnpackNeeded()) {
                // data.success = true;
                // return data;
                // }
                // deck.beforeUpdateCards();
                // deck.updateAllCards();
                SharedDeckDownload download = (SharedDeckDownload) args[0].data[0];
                SharedPreferences pref = AnkiDroidApp.getSharedPrefs(getBaseContext());
                String updatedCardsPref = "numUpdatedCards:" + mDestination + "/tmp/" + download.getFilename()
                        + ".anki.updating";
                long totalCards = 0;// deck.cardCount();
                download.setNumTotalCards((int) totalCards);
                long updatedCards = pref.getLong(updatedCardsPref, 0);
                download.setNumUpdatedCards((int) updatedCards);
                long batchSize = Math.max(100, totalCards / 200);
                mRecentBatchTimings = new long[sRunningAvgLength];
                mTotalBatches = ((double) totalCards) / batchSize;
                int currentBatch = (int) (updatedCards / batchSize);
                long runningAvgCount = 0;
                long batchStart;
                while (updatedCards < totalCards && download.getStatus() == SharedDeckDownload.STATUS_UPDATING) {
                    batchStart = System.currentTimeMillis();
                    // updatedCards = deck.updateAllCardsFromPosition(updatedCards, batchSize);
                    Editor editor = pref.edit();
                    editor.putLong(updatedCardsPref, updatedCards);
                    editor.commit();
                    download.setNumUpdatedCards((int) updatedCards);
                    publishProgress();
                    estimateTimeToCompletion(download, currentBatch, runningAvgCount, System.currentTimeMillis()
                            - batchStart);
                    currentBatch++;
                    runningAvgCount++;
                }
                if (download.getStatus() == SharedDeckDownload.STATUS_UPDATING) {
                    data.success = true;
                } else if (download.getStatus() == SharedDeckDownload.STATUS_PAUSED) {
                    Editor editor = pref.edit();
                    String pausedPref = "paused:" + mDestination + "/tmp/" + download.getFilename() + ".anki.updating";
                    editor.putBoolean(pausedPref, true);
                    editor.commit();
                    data.success = false;
                    Log.i(AnkiDroidApp.TAG, "pausing deck " + download.getFilename());
                } else if (download.getStatus() == SharedDeckDownload.STATUS_CANCELLED) {
                    data.success = false;
                }
                Log.i(AnkiDroidApp.TAG, "Time to update deck = " + download.getEstTimeToCompletion() + " sec.");
                // deck.afterUpdateCards();
            } else {
                data.success = false;
            }
            return data;
        }


        private void estimateTimeToCompletion(SharedDeckDownload download, long currentBatch, long runningAvgCount,
                long lastBatchTime) {
            double avgBatchTime = 0.0;
            avgBatchTime = 0;
            mRecentBatchTimings[((int) runningAvgCount) % sRunningAvgLength] = lastBatchTime;
            int usedForAvg = Math.min(((int) runningAvgCount) + 1, sRunningAvgLength);
            for (int i = 0; i < usedForAvg; i++) {
                avgBatchTime += mRecentBatchTimings[i];
            }
            avgBatchTime /= usedForAvg;
            download.setEstTimeToCompletion(Math.max(0, mTotalBatches - currentBatch - 1) * avgBatchTime / 1000.0);
            // Log.i(AnkiDroidApp.TAG, "TotalBatches: " + totalBatches + " Current: " + currentBatch + " LastBatch: " +
            // lastBatchTime/1000.0 + " RunningAvg: " + avgBatchTime/1000.0 + " Elapsed: " + elapsedTime/1000.0 +
            // " TotalEstimated: " + (elapsedTime + Math.max(0, totalBatches - currentBatch - 1) * avgBatchTime) /
            // 1000.0 + " sec");
        }


        private Payload doInBackgroundLoadDeck(Payload... params) {
            Payload data = params[0];
            SharedDeckDownload download = (SharedDeckDownload) data.data[0];
            String deckFilename = mDestination + "/tmp/" + download.getFilename() + ".anki.updating";
            Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadDeck - deckFilename = " + deckFilename);

            Log.i(AnkiDroidApp.TAG, "loadDeck - SD card mounted and existent file -> Loading deck...");
            try {
                // Open the right deck.
                Decks deck = null;// DeckManager.getDeck(deckFilename, DeckManager.REQUESTING_ACTIVITY_DOWNLOADMANAGER);
                // Start by getting the first card and displaying it.
                // Card card = deck.getCard();
                Log.i(AnkiDroidApp.TAG, "Deck loaded!");

                // Set the result
                // data.returnType = DeckTask.DECK_LOADED;
                HashMap<String, Object> results = new HashMap<String, Object>();
                results.put("deck", deck);
                // results.put("card", card);
                results.put("position", download.getNumUpdatedCards());
                data.result = results;
                return data;
            } catch (SQLException e) {
                Log.i(AnkiDroidApp.TAG, "The database " + deckFilename + " could not be opened = " + e.getMessage());
                data.success = false;
                // data.returnType = DeckTask.DECK_NOT_LOADED;
                data.exception = e;
                return data;
            } catch (CursorIndexOutOfBoundsException e) {
                // XXX: Where is this exception thrown?
                Log.i(AnkiDroidApp.TAG, "The deck has no cards = " + e.getMessage());
                data.success = false;
                // data.returnType = DeckTask.DECK_EMPTY;
                data.exception = e;
                return data;
            }
        }


        @Override
        protected void onPostExecute(Payload result) {
            super.onPostExecute(result);
            HashMap<String, Object> results = (HashMap<String, Object>) result.result;
            Decks deck = (Decks) results.get("deck");
            // Close the previously opened deck.
            // DeckManager.closeDeck(deck.getDeckPath());

            SharedDeckDownload download = (SharedDeckDownload) result.data[0];
            SharedPreferences pref = AnkiDroidApp.getSharedPrefs(getBaseContext());
            Editor editor = pref.edit();

            Log.i(AnkiDroidApp.TAG, "Finished deck " + download.getFilename() + " " + result.success);
            if (result.success) {
                // Put updated cards to 0
                // TODO: Why do we need to zero the updated cards?
                editor.putLong("numUpdatedCards:" + mDestination + "/tmp/" + download.getFilename() + ".anki.updating",
                        0);
                editor.commit();
                // Move deck and media to the default deck path
                new File(mDestination + "/tmp/" + download.getFilename() + ".anki.updating").renameTo(new File(
                        mDestination + "/" + download.getFilename() + ".anki"));
                new File(mDestination + "/tmp/" + download.getFilename() + ".media/").renameTo(new File(mDestination
                        + "/" + download.getFilename() + ".media/"));
                mSharedDeckDownloads.remove(download);
                showNotification(download.getTitle(), download.getFilename());
            } else {
                // If paused do nothing, if cancelled clean up
                if (download.getStatus() == Download.STATUS_CANCELLED) {
                    try {
                        new File(mDestination + "/tmp/" + download.getFilename() + ".anki.updating").delete();
                        File mediaFolder = new File(mDestination + "/tmp/" + download.getFilename() + ".media/");
                        if (mediaFolder != null && mediaFolder.listFiles() != null) {
                            for (File f : mediaFolder.listFiles()) {
                                f.delete();
                            }
                            mediaFolder.delete();
                        }
                    } catch (SecurityException e) {
                        Log.e(AnkiDroidApp.TAG, "SecurityException = " + e.getMessage());
                        e.printStackTrace();
                    }
                    editor.remove("numUpdatedCards:" + mDestination + "/tmp/" + download.getFilename()
                            + ".anki.updating");
                    editor.remove("paused:" + mDestination + "/tmp/" + download.getFilename() + ".anki.updating");
                    editor.commit();
                    mSharedDeckDownloads.remove(download);
                }
            }
            notifySharedDeckObservers();
            stopIfFinished();
        }
    }

    // To test when the service is alive
    /*
     * class RunTask implements Runnable { public void run() { Log.i(AnkiDroidApp.TAG, "Service running..."); ++counter;
     * if(serviceHandler != null) { serviceHandler.postDelayed( this, 1000L ); } } }
     */
}
