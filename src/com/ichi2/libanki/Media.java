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

import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;

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
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Class with static functions related with media handling (images and sounds).
 */
public class Media {
    public static final int MEDIA_ADD = 0;
    public static final int MEDIA_REM = 1;
    public static final long SYNC_ZIP_SIZE = 2560 * 1024;
    
    private static final Pattern sMediaRegexps[] = {
        Pattern.compile("(?i)(\\[sound:([^]]+)\\])"),
        Pattern.compile("(?i)(<img[^>]+src=[\"']?([^\"'>]+)[\"']?[^>]*>)")
    };
    private static final Pattern sRemoteFilePattern = Pattern.compile("(https?|ftp)://");
    
    private Collection mCol;
    private String mDir;
    private AnkiDb mMediaDb;
    
    public Media(Collection col) {
        mCol = col;
        mDir = col.getPath().replaceFirst("\\.anki2$", ".media");
        File fd = new File(mDir);
        if (!fd.exists()) {
            if (fd.mkdir()) {
                Log.e(AnkiDroidApp.TAG, "Cannot create media directory: " + mDir);
            }
        }
        
        connect();
    }

    private void connect() {
        String path = mDir + ".db";
        File mediaDbFile = new File(path);
        if (!mediaDbFile.exists()) {
            // Copy an empty collection file from the assets to the SD card.
            InputStream stream;
            try {
                stream = AnkiDroidApp.getAppResources().getAssets().open("collection.media.db");
                Utils.writeToFile(stream, path);
                stream.close();
            } catch (IOException e) {
                Log.e(AnkiDroidApp.TAG, "Error initialising " + path, e);
            }
        }
        mMediaDb = AnkiDatabaseManager.getDatabase(path);
    }
    private void close() {
    
        mMediaDb.closeDatabase();
        mMediaDb = null;
        mCol = null;
    }
    
    public String getDir() {
        return mDir;
    }

    // Adding media
    ///////////////
    
    /**
     * Copy PATH to MEDIADIR and return new filename.
     * If the same name exists, compare checksums.
     * 
     * @param opath The path where the media file exists before adding it.
     * @return The filename of the resulting file.
     */
    private String addFile(String opath) {
        String mdir = getDir();
        // remove any dangerous characters
        String base = new File(opath).getName().replaceAll("[][<>:/\\&]", "");
        String dst = mdir + base;
        // if it doesn't exist, copy it directly
        File newMediaFile = new File(dst);
        if (!newMediaFile.exists()) {
            try {
                Utils.copyFile(new File(opath), newMediaFile);
            } catch (IOException e) {
                Log.e(AnkiDroidApp.TAG, "Could not copy file " + opath + " to location " + dst, e);
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
        int num = 1;
        StringBuilder sb = new StringBuilder(mdir);
        sb.append("/").append(root).append(" (");
        do {
            StringBuilder sb2 = new StringBuilder(sb);
            sb2.append(num).append(ext);
            newMediaFile = new File(sb2.toString());
            num += 1;
        } while (newMediaFile.exists());
        try {
            Utils.copyFile(new File(opath), newMediaFile);
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "Could not copy file " + opath + " to location " + newMediaFile.getAbsolutePath(), e);
        }
        return newMediaFile.getName();
    }
    
    /**
     * Checks if two files are identical
     * @param filepath1 The path of the first file to be checked
     * @param filepath2 The path of the second file to be checked
     * @return True if both files have the same contents
     */
    private boolean filesIdentical(String filepath1, String filepath2) {
        return (Utils.fileChecksum(filepath1) == Utils.fileChecksum(filepath2));
    }
    
    // String manipulation
    //////////////////////
    
    /**
     * Extract media filenames from an HTML string.
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
        for (Pattern p : sMediaRegexps) {
            m = p.matcher(string);
            while (m.find()) {
                String fname = m.group(2);
                if (includeRemote || (!sRemoteFilePattern.matcher(fname.toLowerCase()).find())) {
                    l.add(fname);
                }
            }
        }
        return l;
    }
    
    /**
     * Strips a string from media references.
     * @param txt The string to be cleared of media references.
     * @return The media-free string.
     */
    public String strip(String txt) {
        Matcher m = null;
        for (Pattern p : sMediaRegexps) {
            m = p.matcher(txt);
            txt = m.replaceAll("");
        }
        return txt;
    }
    
    /**
     * Percent-escape UTF-8 characters in local image filenames.
     * @param string The string to search for image references and escape the filenames.
     * @return The string with the filenames of any local images percent-escaped as UTF-8.
     */
    public String escapeImages(String string) {
        Matcher m = sMediaRegexps[1].matcher(string);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            if (sRemoteFilePattern.matcher(m.group(2)).find()) {
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
    ////////////////
    
    /**
     * Finds missing and unused media files 
     * @return A list containing two lists of filenames (missingList, unusedList)
     */
    public List<List<String>> check() {
        File mdir = new File(getDir());
        List<List<String>> result = new ArrayList<List<String>>();
        List<String> unused = new ArrayList<String>();
        Normalizer.Form form = Normalizer.Form.NFD;
        
        Set<String> normrefs = new HashSet<String>();
        for (String f : allMedia()) {
            if (Normalizer.isNormalized(f, form)) {
                f = Normalizer.normalize(f, form);
            }
            normrefs.add(f);
        }
        for (File file : mdir.listFiles()) {
            if (file.isDirectory()) {
                continue;
            }
            String nfile = file.getName();
            if (!Normalizer.isNormalized(nfile, form)) {
                nfile = Normalizer.normalize(nfile, form);
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
     * @return The list of all media references found in the media database.
     */
    private List<String> allMedia() {
        Set<String> files = new HashSet<String>();
        List<String> fldsList = mMediaDb.queryColumn(String.class, "select flds from notes", 0);
        for (String flds : fldsList) {
            List<String> fList = filesInStr(flds, false);
            for (String f : fList) {
                files.add(f);
            }
        }
        return new ArrayList<String>(files);
    }
    
    // Media syncing - changes and removal
    //////////////////////////////////////
    
    public boolean hasChanged() {
        return (mMediaDb != null && mMediaDb.queryLongScalar("select 1 from log limit 1") == 1);
    }
    
    public List<String> removed() {
        String sql = "select fname from log where type = " + Integer.toString(MEDIA_REM);
        return mMediaDb.queryColumn(String.class, sql, 0);
    }
    
    /**
     * Remove provided deletions and all locally-logged deletions, as server has acked them
     * @param fnames The list of filenames to be deleted.
     */
    public void syncRemove(List<String> fnames) {
        for (String f : fnames) {
            File file = new File(f);
            if (file.exists()) {
                file.delete();
            }
            mMediaDb.execute("delete from log where fname = ?", new String[]{f});
            mMediaDb.execute("delete from media where fname = ?", new String[]{f});
        }
        mMediaDb.execute("delete from log where type = ?", new String[]{Integer.toString(MEDIA_REM)});
    }
    
    // Media syncing - unbundling zip files from server
    ///////////////////////////////////////////////////
    
    /**
     * Extract zip data.
     * @param zipData An input stream that represents a zipped file.
     * @return True if finished.
     */
    public boolean syncAdd(File zipData) {
        boolean finished = false;
        ZipFile z = null;
        try {
            z = new ZipFile(zipData, ZipFile.OPEN_READ);
        } catch (ZipException e) {
            Log.e(AnkiDroidApp.TAG, "Error opening " + zipData.getAbsolutePath() + " as a zip file.", e);
            return false;
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "Error accessing " + zipData.getAbsolutePath(), e);
        }
        ArrayList<Object[]> media = new ArrayList<Object[]>();
        long sizecnt = 0;
        
        // get meta info first
        ZipEntry metaEntry = z.getEntry("_meta");
        if (metaEntry.getSize() >= 100000) {
            Log.e(AnkiDroidApp.TAG, "Size for _meta entry found too big (" + z.getEntry("_meta").getSize() + ")");
            return false;
        }
        byte buffer[] = new byte[100000];
        try {
            z.getInputStream(metaEntry).read(buffer);
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "Error accessing _meta file in zip " + zipData.getAbsolutePath(), e);
        }
        JSONObject meta = null;
        try {
            meta = new JSONObject(buffer.toString());
        } catch (JSONException e) {
            Log.e(AnkiDroidApp.TAG, "Error constructing JSONObject for meta entry", e);
            return false;
        }
        ZipEntry usnEntry = z.getEntry("_usn");
        try {
            z.getInputStream(usnEntry).read(buffer);
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "Error accessing _usn file in zip " + zipData.getAbsolutePath(), e);
        }
        int nextUsn = Integer.parseInt(buffer.toString());

        // Then loop through all files
        for (ZipEntry zentry : Collections.list(z.entries())) {
            // Check for zip bombs
            sizecnt += zentry.getSize();
            if (sizecnt > 100 * 1024 * 1024) {
                Log.e(AnkiDroidApp.TAG, "Media zip file exceeds 100MB uncompressed, aborting unzipping");
                return false;
            }
            if (zentry.getName() == "_meta" || zentry.getName() == "_usn") {
                // Ignore previously retrieved meta
                continue;
            } else if (zentry.getName() == "_finished") {
                finished = true;
            } else {
                String name = meta.optString(zentry.getName());
                if (illegal(name)) {
                    continue;
                }
                try {
                    Utils.writeToFile(z.getInputStream(zentry), name);
                } catch (IOException e1) {
                    Log.e(AnkiDroidApp.TAG, "Error writing synced media file " + name, e1);
                }
                String csum = Utils.fileChecksum(name);
                // append db
                media.add(new Object[]{name, csum, _mtime(name)});
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
    ///////////////////////////////////////////////////////
    
    /**
     * Add files to a zip until over SYNC_ZIP_SIZE. Return zip data.
     * @return Returns a tuple with two objects. The first one is the zip file contents, the second a list
     * with the filenames of the files inside the zip.
     */
    public Pair<File, List<String>> zipAdded() {
        File f = new File(mCol.getPath().replaceFirst("collection\\.anki2$", "tmpsync.zip"));
        
        String sql = "select fname from log where type = " + Integer.toString(MEDIA_ADD);
        List<String> filenames = mMediaDb.queryColumn(String.class, sql, 0);
        List<String> fnames = new ArrayList<String>();
        
        try {
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
            
            JSONObject files = new JSONObject();
            int cnt = 0;
            long sz = 0;
            byte buffer[] = new byte[2048];
            boolean finished = true;
            for (String fname : filenames) {
                fnames.add(fname);
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fname), 2048);
                ZipEntry entry = new ZipEntry(Integer.toString(cnt));
                zos.putNextEntry(entry);
                int count = 0;
                while((count = bis.read(buffer, 0, 2048)) != -1) {
                    zos.write(buffer, 0, count);
                }
                bis.close();
                files.put(Integer.toString(cnt), fname);
                File file = new File(fname);
                sz += file.length();
                if (sz > SYNC_ZIP_SIZE) {
                    finished = false;
                    break;
                }
                cnt += 1;
            }
            if (finished) {
                zos.putNextEntry(new ZipEntry("_finished"));
            }
            zos.putNextEntry(new ZipEntry("_meta"));
            zos.write(files.toString().getBytes());
            zos.close();
        } catch (FileNotFoundException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            return null;
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            return null;
        } catch (JSONException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            return null;
        }
        
        return new Pair<File, List<String>>(f, fnames);
    }
    
    /**
     * Remove records from log table in media DB for a list or files.
     * @param fnames A list containing the list of filenames to be removed from log table.
     */
    public void forgetAdded(List<String> fnames) {
        if (!fnames.isEmpty()) {
            ArrayList<Object[]> args = new ArrayList<Object[]>();
            for (String fname : fnames) {
                args.add(new Object[]{fname});
            }
            mMediaDb.executeMany("delete from log where fname = ?", args);
        }
    }
    
    
    // Tracking changes (private)
    /////////////////////////////
    
    /**
     * Returns the number of seconds from epoch, since the last modification to the file in path.
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
    
    private long usn() {
        return mMediaDb.queryScalar("select usn from meta");
    }
    private void setUsn(int usn) {
        mMediaDb.execute("update meta set usn = ?", new Object[]{usn});
    }
    private void syncMod() {
        mMediaDb.execute("update meta set dirMod = ?", new Object[]{_mtime(mDir)});
    }
    
//    private static final Pattern regPattern = Pattern.compile("\\((\\d+)\\)$");
//
//    // File Handling
//    // *************
//
//    /**
//     * Copy PATH to MEDIADIR, and return new filename.
//     * If a file with the same md5sum exists in the DB, return that.
//     * If a file with the same name exists, return a unique name.
//     * This does not modify the media table.
//     *
//     * @param deck The deck whose media we are dealing with
//     * @param path The path and filename of the media file we are adding
//     * @return The new filename.
//     */
//    public static String copyToMedia(Decks deck, String path) {
//        // See if have duplicate contents
//        String newpath = null;
//        Cursor cursor = null;
//        try {
//            cursor = deck.getDB().getDatabase().rawQuery("SELECT filename FROM media WHERE originalPath = '" +
//                    Utils.fileChecksum(path) + "'", null);
//            if (cursor.moveToNext()) {
//                newpath = cursor.getString(0);
//            }
//        } finally {
//            if (cursor != null) {
//                cursor.close();
//            }
//        }
//        if (newpath == null) {
//            File file = new File(path);
//            String base = file.getName();
//            String mdir = deck.mediaDir(true);
//            newpath = uniquePath(mdir, base);
//            if (!file.renameTo(new File(newpath))) {
//                Log.e(AnkiDroidApp.TAG, "Couldn't move media file " + path + " to " + newpath);
//            }
//        }
//        return newpath.substring(newpath.lastIndexOf("/") + 1);
//    }
//
//
//    /**
//     * Makes sure the filename of the media is unique.
//     * If the filename matches an existing file, then a counter of the form " (x)" is appended before the media file
//     * extension, where x = 1, 2, 3... as needed so that the filename is unique.
//     *
//     * @param dir The path to the media file, excluding the filename
//     * @param base The filename of the file without the path
//     */
//    private static String uniquePath(String dir, String base) {
//        // Remove any dangerous characters
//        base = base.replaceAll("[][<>:/\\&", "");
//        // Find a unique name
//        int extensionOffset = base.lastIndexOf(".");
//        String root = base.substring(0, extensionOffset);
//        String ext = base.substring(extensionOffset);
//        File file = null;
//        while (true) {
//            file = new File(dir, root + ext);
//            if (!file.exists()) {
//                break;
//            }
//            Matcher regMatcher = regPattern.matcher(root);
//            if (!regMatcher.find()) {
//                root = root + " (1)";
//            } else {
//                int num = Integer.parseInt(regMatcher.group(1));
//                root = root.substring(regMatcher.start()) + " (" + num + ")";
//            }
//        }
//        return dir + "/" + root + ext;
//    }
//
//
//    // DB Routines
//    // ***********
//
//    /**
//     * Updates the field size of a media record.
//     * The field size is used to store the count of how many times is this media referenced in question and answer
//     * fields of the cards in the deck.
//     *
//     * @param deck The deck that contains the media we are dealing with
//     * @param file The full path of the media in question
//     */
//    public static void updateMediaCount(Decks deck, String file) {
//        updateMediaCount(deck, file, 1);
//    }
//    public static void updateMediaCount(Decks deck, String file, int count) {
//        if (deck.getDB().queryScalar("SELECT 1 FROM media WHERE filename = '" + file + "'") == 1l) {
//            deck.getDB().getDatabase().execSQL(String.format(Utils.ENGLISH_LOCALE,
//                        "UPDATE media SET size = size + %d, created = %f WHERE filename = '%s'",
//                        count, Utils.now(), file));
//        } else if (count > 0) {
//            String sum = Utils.fileChecksum(file);
////            deck.getDB().getDatabase().execSQL(String.format(Utils.ENGLISH_LOCALE, "INSERT INTO media " +
////                    "(id, filename, size, created, originalPath, description) " +
////                    "VALUES (%d, '%s', %d, %f, '%s', '')", Utils.genID(), file, count, Utils.now(), sum));
//        }
//    }
//
//
//    /**
//     * Deletes from media table any entries that are not referenced in question or answer of any card.
//     *
//     * @param deck The deck that this operation will be performed on
//     */
//    public static void removeUnusedMedia(Decks deck) {
//        ArrayList<Long> ids = deck.getDB().queryColumn(Long.class, "SELECT id FROM media WHERE size = 0", 0);
//        for (Long id : ids) {
//            deck.getDB().getDatabase().execSQL(String.format(Utils.ENGLISH_LOCALE, "INSERT INTO mediaDeleted " +
//                        "VALUES (%d, %f)", id.longValue(), Utils.now()));
//        }
//        deck.getDB().getDatabase().execSQL("DELETE FROM media WHERE size = 0");
//    }
//
//    // Rebuilding DB
//    // *************
//
//    /**
//     * Rebuilds the reference counts, potentially deletes unused media files,
//     *
//     * @param deck The deck to perform the operation on
//     * @param delete If true, then unused (unreferenced in question/answer fields) media files will be deleted
//     * @param dirty If true, then the modified field of deck will be updated
//     * @return Nothing, but the original python code returns a list of unreferenced media files and a list
//     * of missing media files (referenced in question/answer fields, but with the actual files missing)
//     */
//    public static void rebuildMediaDir(Decks deck) {
//        rebuildMediaDir(deck, false, true);
//    }
//    public static void rebuildMediaDir(Decks deck, boolean delete) {
//        rebuildMediaDir(deck, delete, true);
//    }
//    public static void rebuildMediaDir(Decks deck, boolean delete, boolean dirty) {
//        String mdir = deck.mediaDir();
//        if (mdir == null) {
//            return;
//        }
//        //Set all ref counts to 0
//        deck.getDB().getDatabase().execSQL("UPDATE media SET size = 0");
//
//        // Look through the cards for media references
//        Cursor cursor = null;
//        String txt = null;
//        Map<String, Integer> refs = new HashMap<String, Integer>();
//        Set<String> normrefs = new HashSet<String>();
//        try {
//            cursor = deck.getDB().getDatabase().rawQuery("SELECT question, answer FROM cards", null);
//            while (cursor.moveToNext()) {
//                for (int i = 0; i < 2; i++) {
//                    txt = cursor.getString(i);
//                    for (String f : mediaFiles(txt)) {
//                        if (refs.containsKey(f)) {
//                            refs.put(f, refs.get(f) + 1);
//                        } else {
//                            refs.put(f, 1);
//                            // normrefs.add(Normalizer.normalize(f, Normalizer.Form.NFC));
//                            normrefs.add(f);
//                        }
//                    }
//                }
//            }
//        } finally {
//            if (cursor != null) {
//                cursor.close();
//            }
//        }
//
//        // Update ref counts
//        for (Entry<String, Integer> entry : refs.entrySet()) {
//            updateMediaCount(deck, entry.getKey(), entry.getValue());
//        }
//        String fname = null;
//
//        //If there is no media dir, then there is nothing to find.
//        if(mdir != null) {
//            // Find unused media
//            Set<String> unused = new HashSet<String>();
//            File mdirfile = new File(mdir);
//            if (mdirfile.exists()) {
//                fname = null;
//                for (File f : mdirfile.listFiles()) {
//                    if (!f.isFile()) {
//                        // Ignore directories
//                        continue;
//                    }
//                    // fname = Normalizer.normalize(f.getName(), Normalizer.Form.NFC);
//                    fname = f.getName();
//                    if (!normrefs.contains(fname)) {
//                        unused.add(fname);
//                    }
//                }
//            }
//            // Optionally delete
//            if (delete) {
//                for (String fn : unused) {
//                    File file = new File(mdir + "/" + fn);
//                    try {
//                        if (!file.delete()) {
//                            Log.e(AnkiDroidApp.TAG, "Couldn't delete unused media file " + mdir + "/" + fn);
//                        }
//                    } catch (SecurityException e) {
//                        Log.e(AnkiDroidApp.TAG, "Security exception while deleting unused media file " + mdir + "/" + fn);
//                    }
//                }
//            }
//        }
//        // Remove entries in db for unused media
//        removeUnusedMedia(deck);
//
//        // Check md5s are up to date
//        cursor = null;
//        String path = null;
//        fname = null;
//        String md5 = null;
//        SQLiteDatabase db = deck.getDB().getDatabase();
//        db.beginTransaction();
//        try {
//            cursor = db.query("media", new String[] {"filename", "created", "originalPath"}, null, null, null, null, null);
//            while (cursor.moveToNext()) {
//                fname = cursor.getString(0);
//                md5 = cursor.getString(2);
//                path = mdir + "/" + fname;
//                File file = new File(path);
//                if (!file.exists()) {
//                   if (!md5.equals("")) {
//                       db.execSQL(String.format(Utils.ENGLISH_LOCALE,
//                               "UPDATE media SET originalPath = '', created = %f where filename = '%s'",
//                               Utils.now(), fname));
//                   }
//                } else {
//                    String sum = Utils.fileChecksum(path);
//                    if (!md5.equals(sum)) {
//                       db.execSQL(String.format(Utils.ENGLISH_LOCALE,
//                               "UPDATE media SET originalPath = '%s', created = %f where filename = '%s'",
//                               sum, Utils.now(), fname));
//                    }
//                }
//            }
//        } finally {
//            if (cursor != null) {
//                cursor.close();
//            }
//        }
//        db.setTransactionSuccessful();
//        db.endTransaction();
//
//        // Update deck and get return info
//        if (dirty) {
//            deck.flushMod();
//        }
//        // In contrast to the python code we don't return anything. In the original python code, the return
//        // values are used in a function (media.onCheckMediaDB()) that we don't have in AnkiDroid.
//    }
}
