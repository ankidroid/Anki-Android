/****************************************************************************************
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

package com.ichi2.libanki;

import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.util.Log;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Class with static functions related with media handling (images and sounds).
 */
public class Media {
    public static final int MEDIA_ADD = 0;
    public static final int MEDIA_REM = 1;
    public static final long SYNC_ZIP_SIZE = 2560 * 1024;

    public static final Pattern fMediaRegexps[] = { Pattern.compile("(?i)(\\[sound:([^]]+)\\])"),
            Pattern.compile("(?i)(<img[^>]+src=[\"']?([^\"'>]+)[\"']?[^>]*>)") };
    private static final Pattern fSoundRegexps = Pattern.compile("\\[sound:(.*?)\\]");
    private static final Pattern fRemoteFilePattern = Pattern.compile("(https?|ftp)://");
    private static final Pattern fDangerousCharacters = Pattern.compile("[]\\[<>:/\\\\&?\\\"\\|]");
    private static final Pattern fFileOrdinal = Pattern.compile(" \\((\\d+)\\)$");

    private Collection mCol;
    private String mDir;
    private String mMediaDbFilename;
    private AnkiDb mMediaDb;


    public Media(Collection col, boolean server) {
        mCol = col;
        if (server) {
            mDir = null;
            return;
        }
        mDir = col.getPath().replaceFirst("\\.anki2$", ".media");
        mMediaDbFilename = mDir + ".db";
        File fd = new File(mDir);
        if (!fd.exists()) {
            if (!fd.mkdir()) {
                Log.e(AnkiDroidApp.TAG, "Cannot create media directory: " + mDir);
            }
        }

        connect();
    }


    public void connect() {
        File mediaDbFile = new File(mMediaDbFilename);
        if (!mediaDbFile.exists()) {
            // Copy an empty collection file from the assets to the SD card.
            InputStream stream;
            try {
                stream = AnkiDroidApp.getAppResources().getAssets().open("collection.media.db");
                Utils.writeToFile(stream, mMediaDbFilename);
                stream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        mMediaDb = AnkiDatabaseManager.getDatabase(mMediaDbFilename);
    }


    public void close() {
        AnkiDatabaseManager.closeDatabase(mMediaDbFilename);
        mMediaDb = null;
        mCol = null;
    }


    public String getDir() {
        return mDir;
    }


    // Adding media
    // /////////////

    /**
     * Copy PATH to MEDIADIR and return new filename. If the same name exists, compare checksums.
     * 
     * @param opath The path where the media file exists before adding it.
     * @return The filename of the resulting file.
     */
    public String addFile(String opath) {
        String mdir = getDir();
        // remove any dangerous characters
        String base = fDangerousCharacters.matcher(new File(opath).getName()).replaceAll("");
        // if it doesn't exist, copy it directly
        File newMediaFile = new File(mdir, base);
        String dst = newMediaFile.getAbsolutePath();
        if (!newMediaFile.exists()) {
            try {
                Utils.copyFile(new File(opath), newMediaFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return base;
        }
        if (filesIdentical(opath, dst)) {
            return base;
        }
        // otherwise, find a unique name
        String root, ext;
        int extIndex = base.lastIndexOf('.');
        if (extIndex == 0 || extIndex == -1) {
            root = base;
            ext = "";
        } else {
            root = base.substring(0, extIndex);
            ext = base.substring(extIndex);
        }
        StringBuilder sb = null;
        String path = null;
        Matcher m = null;
        int n = 0;
        while (true) {
            sb = new StringBuilder(mdir);
            path = sb.append(File.separatorChar).append(root).append(ext).toString();
            newMediaFile = new File(path);
            if (!newMediaFile.exists()) {
                break;
            }
            m = fFileOrdinal.matcher(root);
            if (!m.find()) {
                root = root.concat(" (1)");
            } else {
                n = Integer.parseInt(m.group(1));
                root = m.replaceFirst(" (" + String.valueOf(n + 1) + ")");
            }
        }
        // copy and return
        try {
            Utils.copyFile(new File(opath), newMediaFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return newMediaFile.getName();
    }


    /**
     * Checks if two files are identical
     * 
     * @param filepath1 The path of the first file to be checked
     * @param filepath2 The path of the second file to be checked
     * @return True if both files have the same contents
     */
    private boolean filesIdentical(String filepath1, String filepath2) {
        return Utils.fileChecksum(filepath1).equals(Utils.fileChecksum(filepath2));
    }


    // String manipulation
    // ////////////////////

    public List<String> filesInStr(String string) {
        return filesInStr(string, false);
    }


    /**
     * Extract media filenames from an HTML string.
     * 
     * @param string The string to scan for media filenames ([sound:...] or <img...>).
     * @param includeRemote If true will also include external http/https/ftp urls.
     * @return A list containing all the sound and image filenames found in the input string.
     */
    public List<String> filesInStr(String string, boolean includeRemote) {
        List<String> l = new ArrayList<String>();
        // Convert latex first
        string = LaTeX.mungeQA(string, mCol);
        // Extract filenames
        Matcher m = null;
        for (Pattern p : fMediaRegexps) {
            m = p.matcher(string);
            while (m.find()) {
                String fname = m.group(2);
                if (includeRemote || (!fRemoteFilePattern.matcher(fname.toLowerCase()).find())) {
                    l.add(fname);
                }
            }
        }
        return l;
    }

    /**
     * Strips a string from media references.
     * 
     * @param txt The string to be cleared of media references.
     * @return The media-free string.
     */
    public String strip(String txt) {
        Matcher m = null;
        for (Pattern p : fMediaRegexps) {
            m = p.matcher(txt);
            txt = m.replaceAll("");
        }
        return txt;
    }


    public String stripAudio(String txt) {
    	Matcher m = fSoundRegexps.matcher(txt);
    	return m.replaceAll("");
    }


    /**
     * Percent-escape UTF-8 characters in local image filenames.
     * 
     * @param string The string to search for image references and escape the filenames.
     * @return The string with the filenames of any local images percent-escaped as UTF-8.
     */
    public String escapeImages(String string) {
        Matcher m = fMediaRegexps[1].matcher(string);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            if (fRemoteFilePattern.matcher(m.group(2)).find()) {
                m.appendReplacement(sb, m.group());
            } else {
                String tagBegin = m.group(1).substring(0, m.start(2));
                String fname = m.group(2);
                String tagEnd = m.group(1).substring(m.end(2));
                String tag = tagBegin + Uri.encode(fname) + tagEnd;
                m.appendReplacement(sb, tag);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }


    // Rebuilding DB
    // //////////////


    /**
     * Finds missing and unused media files
     * 
     * @return A list containing two lists of filenames (missingList, unusedList)
     */
    public List<List<String>> check() {
        File mdir = new File(getDir());
        List<List<String>> result = new ArrayList<List<String>>();
        List<String> unused = new ArrayList<String>();

        Set<String> normrefs = new HashSet<String>();
        for (String f : allMedia()) {
            if (AnkiDroidApp.SDK_VERSION > 9) {
                f = AnkiDroidApp.getCompat().normalizeUnicode(f);
            }
            normrefs.add(f);
        }
        for (File file : mdir.listFiles()) {
            if (file.isDirectory()) {
                continue;
            }
            if (file.getName().startsWith("_")) {
                // leading _ says to ignore file
                continue;
            }
            String nfile = file.getName();
            if (AnkiDroidApp.SDK_VERSION > 9) {
                nfile = AnkiDroidApp.getCompat().normalizeUnicode(nfile);
            }
            if (!normrefs.contains(nfile)) {
                unused.add(file.getName());
            } else {
                normrefs.remove(nfile);
            }
        }
        List<String> nohave = new ArrayList<String>(normrefs);
        result.add(nohave);
        result.add(unused);

        return result;
    }


    /**
     * Return a list of all referenced filenames.
     * 
     * @return The list of all media references found in the media database.
     */
    private List<String> allMedia() {
        Set<String> files = new HashSet<String>();
        List<String> fldsList = mCol.getDb().queryColumn(String.class, "select flds from notes", 0);
        for (String flds : fldsList) {
            List<String> fList = filesInStr(flds);
            for (String f : fList) {
                files.add(f);
            }
        }
        return new ArrayList<String>(files);
    }

    // Copying on import
    ////////////////////

    public boolean have(String fname) {
        return new File(fname, getDir()).exists();
    }

    // Media syncing - changes and removal
    // ////////////////////////////////////

    public boolean hasChanged() {
        return (mMediaDb != null && mMediaDb.queryLongScalar("select 1 from log limit 1", false) == 1);
    }


    public List<String> removed() {
        String sql = "select fname from log where type = " + Integer.toString(MEDIA_REM);
        return mMediaDb.queryColumn(String.class, sql, 0);
    }


    /**
     * Remove provided deletions and all locally-logged deletions, as server has acked them
     * 
     * @param fnames The list of filenames to be deleted.
     */
    public void syncRemove(JSONArray fnames) {
        mMediaDb.getDatabase().beginTransaction();
        try {
            for (int i = 0; i < fnames.length(); ++i) {
                String f = fnames.optString(i);
                if (f == "") {
                    continue;
                }
                File file = new File(getDir(), f);
                if (file.exists()) {
                    file.delete();
                }

                mMediaDb.execute("delete from log where fname = ?", new String[]{f});
                mMediaDb.execute("delete from media where fname = ?", new String[]{f});
            }
            mMediaDb.execute("delete from log where type = ?", new String[]{Integer.toString(MEDIA_REM)});
            mMediaDb.getDatabase().setTransactionSuccessful();
        } finally {

            mMediaDb.getDatabase().endTransaction();
        }
    }


    // Media syncing - unbundling zip files from server
    // /////////////////////////////////////////////////

    /**
     * Extract zip data.
     * 
     * @param zipData An input stream that represents a zipped file.
     * @return True if finished.
     */
    public boolean syncAdd(File zipData) {
        boolean finished = false;
        ZipFile z = null;
        ArrayList<Object[]> media = new ArrayList<Object[]>();
        long sizecnt = 0;
        JSONObject meta = null;
        int nextUsn = 0;
        try {
            z = new ZipFile(zipData, ZipFile.OPEN_READ);
            // get meta info first
            ZipEntry metaEntry = z.getEntry("_meta");
            // if (metaEntry.getSize() >= 100000) {
            // Log.e(AnkiDroidApp.TAG, "Size for _meta entry found too big (" + z.getEntry("_meta").getSize() + ")");
            // return false;
            // }
            meta = new JSONObject(Utils.convertStreamToString(z.getInputStream(metaEntry)));
            ZipEntry usnEntry = z.getEntry("_usn");
            String usnstr = Utils.convertStreamToString(z.getInputStream(usnEntry));
            nextUsn = Integer.parseInt(usnstr);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (ZipException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Then loop through all files
        for (ZipEntry zentry : Collections.list(z.entries())) {
            // Check for zip bombs
            sizecnt += zentry.getSize();
            if (sizecnt > 100 * 1024 * 1024) {
                Log.e(AnkiDroidApp.TAG, "Media zip file exceeds 100MB uncompressed, aborting unzipping");
                return false;
            }
            if (zentry.getName().compareTo("_meta") == 0 || zentry.getName().compareTo("_usn") == 0) {
                // Ignore previously retrieved meta
                continue;
            } else if (zentry.getName().compareTo("_finished") == 0) {
                finished = true;
            } else {
                String name = meta.optString(zentry.getName());
                if (illegal(name)) {
                    continue;
                }
                String path = getDir().concat(File.separator).concat(name);
                try {
                    Utils.writeToFile(z.getInputStream(zentry), path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                String csum = Utils.fileChecksum(path);
                // append db
                media.add(new Object[] { name, csum, _mtime(name) });
                mMediaDb.execute("delete from log where fname = ?", new String[]{name});
            }
        }

        // update media db and note new starting usn
        if (!media.isEmpty()) {
            mMediaDb.executeMany("insert or replace into media values (?,?,?)", media);
        }
        setUsn(nextUsn); // commits
        // if we have finished adding, we need to record the new folder mtime
        // so that we don't trigger a needless scan
        if (finished) {
            syncMod();
        }
        return finished;
    }


    /**
     * Check if the file name has illegal for the OS characters.
     * 
     * @param f The filename to be checked.
     * @return Returns true if at least an illegal character is found.
     */
    private boolean illegal(String f) {
        if (f.contains("/")) {
            return true;
        }
        return false;
    }


    // Media syncing - bundling zip files to send to server
    // Because there's no standard filename encoding for zips, and because not
    // all zip clients support retrieving mtime, we store the files as ascii
    // and place a json file in the zip with the necessary information.
    // /////////////////////////////////////////////////////

    /**
     * Add files to a zip until over SYNC_ZIP_SIZE. Return zip data.
     * 
     * @return Returns a tuple with two objects. The first one is the zip file contents, the second a list with the
     *         filenames of the files inside the zip.
     */
    public Pair<File, List<String>> zipAdded() {
        File f = new File(mCol.getPath().replaceFirst("collection\\.anki2$", "tmpSyncToServer.zip"));

        String sql = "select fname from log where type = " + Integer.toString(MEDIA_ADD);
        List<String> filenames = mMediaDb.queryColumn(String.class, sql, 0);
        List<String> fnames = new ArrayList<String>();

        try {
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
            zos.setLevel(8);

            JSONObject files = new JSONObject();
            int cnt = 0;
            long sz = 0;
            byte buffer[] = new byte[2048];
            boolean finished = true;
            for (String fname : filenames) {
                fnames.add(fname);
                File file = new File(getDir(), fname);
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file), 2048);
                ZipEntry entry = new ZipEntry(Integer.toString(cnt));
                zos.putNextEntry(entry);
                int count = 0;
                while ((count = bis.read(buffer, 0, 2048)) != -1) {
                    zos.write(buffer, 0, count);
                }
                zos.closeEntry();
                bis.close();
                files.put(Integer.toString(cnt), fname);
                sz += file.length();
                if (sz > SYNC_ZIP_SIZE) {
                    finished = false;
                    break;
                }
                cnt += 1;
            }
            if (finished) {
                zos.putNextEntry(new ZipEntry("_finished"));
                zos.closeEntry();
            }
            zos.putNextEntry(new ZipEntry("_meta"));
            zos.write(Utils.jsonToString(files).getBytes());
            zos.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return new Pair<File, List<String>>(f, fnames);
    }


    /**
     * Remove records from log table in media DB for a list or files.
     * 
     * @param fnames A list containing the list of filenames to be removed from log table.
     */
    public void forgetAdded(List<String> fnames) {
        if (!fnames.isEmpty()) {
            ArrayList<Object[]> args = new ArrayList<Object[]>();
            for (String fname : fnames) {
                args.add(new Object[] { fname });
            }
            mMediaDb.executeMany("delete from log where fname = ?", args);
        }
    }


    // Tracking changes (private)
    // ///////////////////////////

    /**
     * Returns the number of seconds from epoch, since the last modification to the file in path.
     * 
     * @param path The path to the file we are checking.
     * @return The number of seconds (rounded down).
     */
    private long _mtime(String path) {
        File f = new File(path);
        return f.lastModified() / 1000;
    }


    private String _checksum(String path) {
        return Utils.fileChecksum(path);
    }


    public long usn() {
        return mMediaDb.queryLongScalar("select usn from meta");
    }


    public void setUsn(long usn) {
        mMediaDb.execute("update meta set usn = ?", new Object[] { usn });
    }


    private void syncMod() {
        mMediaDb.execute("update meta set dirMod = ?", new Object[] { _mtime(mDir) });
    }


    /**
     * Return dir mtime if it has changed since the last findChanges() Doesn't track edits, but user can add or remove a
     * file to update
     * 
     * @return The modification time of the media directory, if it has changed since the last call of findChanges(). If
     *         it hasn't, it returns 0.
     */
    public long _changed() {
        long mod = mMediaDb.queryLongScalar("select dirMod from meta");
        long mtime = _mtime(getDir());
        if (mod != 0 && mod == mtime) {
            return 0;
        }
        return mtime;
    }


    /**
     * Scan the media folder if it's changed, and note any changes.
     */
    public void findChanges() {
        if (_changed() != 0) {
            _logChanges();
        }
    }


    private void _logChanges() {
        Pair<List<String>, List<String>> result = _changes();
        ArrayList<Object[]> log = new ArrayList<Object[]>();
        ArrayList<Object[]> media = new ArrayList<Object[]>();
        ArrayList<Object[]> mediaRem = new ArrayList<Object[]>();

        for (String f : result.first) {
            long mt = _mtime(f);
            String csum = _checksum(getDir().concat(File.separator).concat(f));
            media.add(new Object[] { f, csum, mt });
            log.add(new Object[] { f, MEDIA_ADD });
        }
        for (String f : result.second) {
            mediaRem.add(new Object[] { f });
            log.add(new Object[] { f, MEDIA_REM });
        }

        // update media db
        mMediaDb.executeMany("insert or replace into media values (?, ?, ?)", media);
        if (mediaRem.size() > 0) {
            mMediaDb.executeMany("delete from media where fname = ?", mediaRem);
        }
        mMediaDb.execute("update meta set dirMod = ?", new Object[] { _mtime(getDir()) });
        // and logs
        mMediaDb.executeMany("insert or replace into log values (?, ?)", log);
    }


    private Pair<List<String>, List<String>> _changes() {
        Map<String, Pair<String, Long>> cache = new HashMap<String, Pair<String, Long>>();
        Map<String, Boolean> used = new HashMap<String, Boolean>();
        Cursor cur = null;
        try {
            cur = mMediaDb.getDatabase().query("media", new String[] { "fname", "csum", "mod" }, null, null, null,
                    null, null);
            while (cur.moveToNext()) {
                cache.put(cur.getString(0), new Pair<String, Long>(cur.getString(1), cur.getLong(1)));
                used.put(cur.getString(0), false);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (cur != null) {
                cur.close();
            }
        }

        List<String> added = new ArrayList<String>();
        List<String> removed = new ArrayList<String>();

        File mediaDir = new File(getDir());
        for (File f : mediaDir.listFiles()) {
            // ignore folders and thumbs.db
            if (f.isDirectory()) {
                continue;
            }
            String fname = f.getName();
            if (fname.compareTo("thumbs.db") == 0) {
                continue;
            }
            // and files with invalid chars
            boolean bad = false;
            for (String c : new String[] { "\0", "/", "\\", ":" }) {
                if (fname.contains(c)) {
                    bad = true;
                    break;
                }
            }
            if (bad) {
                continue;
            }
            // empty files are invalid; clean them up and continue
            if (f.length() == 0) {
                f.delete();
                continue;
            }
            // newly added?
            if (!cache.containsKey(fname)) {
                added.add(fname);
            } else {
                // modified since last time?
                if ((f.lastModified() / 1000) != cache.get(fname).second) {
                    // and has different checksum?
                    if (_checksum(f.getAbsolutePath()).compareTo(cache.get(fname).first) != 0) {
                        added.add(fname);
                    }
                }
                // mark as used
                used.put(fname, true);
            }
        }

        // look for any entries in the cache that no longer exist on disk
        for (String fname : used.keySet()) {
            if (!used.get(fname)) {
                removed.add(fname);
            }
        }
        return new Pair<List<String>, List<String>>(added, removed);
    }


    public Pair<Long, Long> sanityCheck() {
        return new Pair<Long, Long> (
                mMediaDb.queryLongScalar("select count() from log"),
                mMediaDb.queryLongScalar("select count() from media"));
    }

    public void resetMediaDb() {
        mMediaDb.execute("delete from log");
        mMediaDb.execute("delete from media");
        mMediaDb.execute("delete from meta");
        mMediaDb.execute("insert into meta values(0,0)");
    }


    public AnkiDb getMediaDb() {
        return mMediaDb;
    }

    /**
     * Remove media that is no longer being used from the SD-card.
     */
	public void removeUnusedImages() {
		List<String> listOfUnusedMedia = check().get(1); // Returns two lists, 2nd is unused media.
		for (String mediaName : listOfUnusedMedia) {
			File mediaFile = new File(mDir + "/" + mediaName);
			if (mediaFile.exists()) {
				mediaFile.delete();
			}
		}
	}

}
