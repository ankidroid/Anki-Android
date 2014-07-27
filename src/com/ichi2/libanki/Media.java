/****************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
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
import android.text.TextUtils;
import android.util.Log;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Pair;
import com.ichi2.anki.exception.APIVersionException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Media manager - handles the addition and removal of media files from the media directory (collection.media) and
 * maintains the media database (collection.media.ad.db2) which is used to determine the state of files for syncing.
 * Note that the media database has an additional prefix for AnkiDroid (.ad) to avoid any potential issues caused by
 * users copying the file to the desktop client and vice versa.
 * <p>
 * Unlike the python version of this module, we do not (and cannot) modify the current working directory (CWD) before
 * performing operations on media files. In python, the CWD is changed to the media directory, allowing it to easily
 * refer to the files in the media directory by name only. In Java, we must be cautious about when to specify the full
 * path to the file and when we need to use the filename only. In general, when we refer to a file on disk (i.e.,
 * creating a new File() object), we must include the full path. Use the dir() method to make this step easier.<br>
 * E.g: new File(dir(), "filename.jpg")
 */
public class Media {

    private static final Pattern fIllegalCharReg = Pattern.compile("[><:\"/?*^\\\\|\\x00\\r\\n]");
    private static final Pattern fRemotePattern  = Pattern.compile("(https?|ftp)://");

    /*
     * A note about the regular expressions below: the python code uses named groups for the image and sound patterns.
     * Our version of Java doesn't support named groups, so we must use indexes instead. In the expressions below, the
     * group names (e.g., ?P<fname>) have been stripped and a comment placed above indicating the index of the group
     * name in the original. Refer to these indexes whenever the python code makes use of a named group.
     */

    /**
     * Group 1 = Contents of [sound:] tag <br>
     * Group 2 = "fname"
     */
    private static final Pattern fSoundRegexps = Pattern.compile("(?i)(\\[sound:([^]]+)\\])");

    // src element quoted case
    /**
     * Group 1 = Contents of <img> tag <br>
     * Group 2 = "str" <br>
     * Group 3 = "fname" <br>
     * Group 4 = Backreference to "str" (i.e., same type of quote character)
     */
    private static final Pattern fImgRegExpQ = Pattern.compile("(?i)(<img[^>]* src=([\\\"'])([^>]+?)(\\2)[^>]*>)");

    // unquoted case
    /**
     * Group 1 = Contents of <img> tag <br>
     * Group 2 = "fname"
     */
    private static final Pattern fImgRegExpU = Pattern.compile("(?i)(<img[^>]* src=(?!['\\\"])([^ >]+)[^>]*?>)");

    public static List<Pattern> mRegexps =  Arrays.asList(fSoundRegexps, fImgRegExpQ, fImgRegExpU);

    private Collection mCol;
    private String mDir;
    private AnkiDb mDb;


    public Media(Collection col, boolean server) {
        mCol = col;
        if (server) {
            mDir = null;
            return;
        }
        // media directory
        mDir = col.getPath().replaceFirst("\\.anki2$", ".media");
        File fd = new File(mDir);
        if (!fd.exists()) {
            if (!fd.mkdir()) {
                Log.e(AnkiDroidApp.TAG, "Cannot create media directory: " + mDir);
            }
        }
        // change database
        connect();
    }


    public void connect() {
        if (mCol.getServer()) {
            return;
        }
        // NOTE: We use a custom prefix for AnkiDroid to avoid issues caused by copying
        // the db to the desktop or vice versa.
        String path = dir() + ".ad.db2";
        File dbFile = new File(path);
        boolean create = !(dbFile.exists());
        mDb = AnkiDatabaseManager.getDatabase(path);
        if (create) {
            _initDB();
        }
        maybeUpgrade();
    }


    public void _initDB() {
        String sql = "create table media (\n" +
                     " fname text not null primary key,\n" +
                     " csum text,           -- null indicates deleted file\n" +
                     " mtime int not null,  -- zero if deleted\n" +
                     " dirty int not null\n" +
                     ");\n" +
                     "create index idx_media_dirty on media (dirty);\n" +
                     "create table meta (dirMod int, lastUsn int); insert into meta values (0, 0);";
        mDb.executeScript(sql);
    }


    public void maybeUpgrade() {
        String oldpath = dir() + ".db";
        File oldDbFile = new File(oldpath);
        if (oldDbFile.exists()) {
            mDb.execute(String.format(Locale.US, "attach \"%s\" as old", oldpath));
            try {
                String sql = "insert into media\n" +
                             " select m.fname, csum, mod, ifnull((select 1 from log l2 where l2.fname=m.fname), 0) as dirty\n" +
                             " from old.media m\n" +
                             " left outer join old.log l using (fname)\n" +
                             " union\n" +
                             " select fname, null, 0, 1 from old.log where type=1;";
                mDb.execute(sql);
                mDb.execute("delete from meta");
                mDb.execute("insert into meta select dirMod, usn from old.meta");
                mDb.commit();
            } catch (Exception e) {
                // if we couldn't import the old db for some reason, just start anew
                Log.e(AnkiDroidApp.TAG, "Failed to import old media db", e);
            }
            mDb.execute("detach old");
            File newDbFile = new File(oldpath + ".old");
            if (newDbFile.exists()) {
                newDbFile.delete();
            }
            oldDbFile.renameTo(newDbFile);
        }
    }


    public void close() {
        if (mCol.getServer()) {
            return;
        }
        AnkiDatabaseManager.closeDatabase(mDb.getPath());
        mDb = null;
    }


    public String dir() {
        return mDir;
    }


    // TODO: Assume we are on FAT32 for every sync until we can find a reliable way to detect
    // the file system type. This will trigger a media scan on every sync attempt even on devices
    // that didn't need it, so it makes syncing a little slower.
    public boolean _isFAT32() {
        return true;
    }


    /**
     * Adding media
     * ***********************************************************
     */

    /**
     * Copy opath to the media directory and return new filename. If the same name exists, compare checksums.
     * <p>
     * TODO: This method is unreviewed and could contain errors. It is currently not used anywhere. The desktop client
     * makes use of this method to insert media into the collection through the note editor, which is currently not done
     * in AnkiDroid.
     * 
     * @param opath The path where the media file exists before adding it.
     * @return The filename of the resulting file.
     */
    public String addFile(String opath) {
        String mdir = dir();
        // remove any dangerous characters
        String base = fIllegalCharReg.matcher(new File(opath).getName()).replaceAll("");
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

        if (Utils.fileChecksum(opath).equals(Utils.fileChecksum(dst))) {
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
        Pattern fileOrdinal = Pattern.compile(" \\((\\d+)\\)$");
        while (true) {
            sb = new StringBuilder(mdir);
            path = sb.append(File.separatorChar).append(root).append(ext).toString();
            newMediaFile = new File(path);
            if (!newMediaFile.exists()) {
                break;
            }
            m = fileOrdinal.matcher(root);
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
     * String manipulation
     * ***********************************************************
     */

    public List<String> filesInStr(Long mid, String string) {
        return filesInStr(mid, string, false);
    }


    /**
     * Extract media filenames from an HTML string.
     *
     * @param string The string to scan for media filenames ([sound:...] or <img...>).
     * @param includeRemote If true will also include external http/https/ftp urls.
     * @return A list containing all the sound and image filenames found in the input string.
     */
    public List<String> filesInStr(Long mid, String string, boolean includeRemote) {
        List<String> l = new ArrayList<String>();
        JSONObject model = mCol.getModels().get(mid);
        List<String> strings = new ArrayList<String>();
        try {
            if (model.getInt("type") == Consts.MODEL_CLOZE && string.contains("{{c")) {
                // if the field has clozes in it, we'll need to expand the
                // possibilities so we can render latex
                strings = _expandClozes(string);
            } else {
                strings.add(string);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        
        for (String s : strings) {
            // handle latex
            s =  LaTeX.mungeQA(s, mCol); // TODO: why only two parameters? what about model?
            // extract filenames
            Matcher m;
            for (Pattern p : mRegexps) {
                // NOTE: python uses the named group 'fname'. Java doesn't have named groups, so we have to determine
                // the index based on which pattern we are using
                int fnameIdx = p == fSoundRegexps ? 2 : p == fImgRegExpU ? 2 : 3;
                m = p.matcher(string);
                while (m.find()) {
                    String fname = m.group(fnameIdx);
                    boolean isLocal = !fRemotePattern.matcher(fname.toLowerCase(Locale.US)).find();
                    if (isLocal || includeRemote) {
                        l.add(fname);
                    }
                }
            }
        }
        return l;
    }

    
    // TODO: Not implemented yet. Currently not triggered by anything available in the UI, but must be completed
    // before we expose the "Media check" option in the future.
    private List<String> _expandClozes(String string) {
        Set<String> ords = new TreeSet<String>();
        Pattern cloze = Pattern.compile("{{c(\\\\d+)::.+?}}");
        Matcher m = cloze.matcher(string);
        while (m.find()) {
            ords.add(m.group(1));
        }
        return new ArrayList<String>();
    }


    /**
     * Strips a string from media references.
     *
     * @param txt The string to be cleared of media references.
     * @return The media-free string.
     */
    public String strip(String txt) {
        for (Pattern p : mRegexps) {
            txt = p.matcher(txt).replaceAll("");
        }
        return txt;
    }


    /**
     * Percent-escape UTF-8 characters in local image filenames.
     * @param string The string to search for image references and escape the filenames.
     * @return The string with the filenames of any local images percent-escaped as UTF-8.
     */
    public String escapeImages(String string) {
        for (Pattern p : Arrays.asList(fImgRegExpQ, fImgRegExpU)) {
            Matcher m = p.matcher(string);
            // NOTE: python uses the named group 'fname'. Java doesn't have named groups, so we have to determine
            // the index based on which pattern we are using
            int fnameIdx = p == fImgRegExpU ? 2 : 3;
            while (m.find()) {
                String tag = m.group(0);
                String fname = m.group(fnameIdx);
                if (fRemotePattern.matcher(fname).find()) {
                    string = tag;
                } else {
                    string = tag.replace(fname, Uri.encode(fname));
                }
            }
        }
        return string;
    }


    /**
     * Rebuilding DB
     * ***********************************************************
     */

    /**
     * Finds missing, unused and invalid media files
     *
     * @return A list containing three lists of files (missingFiles, unusedFiles, invalidFiles)
     */
    public List<List<String>> check() throws APIVersionException {
        return check(null);
    }


    private List<List<String>> check(File[] local) throws APIVersionException {
        File mdir = new File(dir());
        // gather all media references in NFC form
        Set<String> allRefs = new HashSet<String>();
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().rawQuery("select id, mid, flds from notes", null);
            while (cur.moveToNext()) {
                long nid = cur.getLong(0);
                long mid = cur.getLong(1);
                String flds = cur.getString(2);
                List<String> noteRefs = filesInStr(mid, flds);
                // check the refs are in NFC
                for (String f : noteRefs) {
                    // if they're not, we'll need to fix them first
                    if (!f.equals(AnkiDroidApp.getCompat().nfcNormalized(f))) {
                        _normalizeNoteRefs(nid);
                        noteRefs = filesInStr(mid, flds);
                        break;
                    }
                }
                allRefs.addAll(noteRefs);
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        // loop through media folder
        List<String> unused = new ArrayList<String>();
        List<String> invalid = new ArrayList<String>();
        File[] files;
        if (local == null) {
            files = mdir.listFiles();
        } else {
            files = local;
        }
        boolean renamedFiles = false;
        for (File file : files) {
            if (local == null) {
                if (file.isDirectory()) {
                    // ignore directories
                    continue;
                }
            }
            if (file.getName().startsWith("_")) {
                // leading _ says to ignore file
                continue;
            }
            File nfcFile = new File(dir(), AnkiDroidApp.getCompat().nfcNormalized(file.getName()));
            // we enforce NFC fs encoding
            if (local == null) {
                if (!file.getName().equals(nfcFile.getName())) {
                    // delete if we already have the NFC form, otherwise rename
                    if (nfcFile.exists()) {
                        file.delete();
                        renamedFiles = true;
                    } else {
                        file.renameTo(nfcFile);
                        renamedFiles = true;
                    }
                    file = nfcFile;
                }
            }
            // compare
            if (!allRefs.contains(nfcFile.getName())) {
                unused.add(file.getName());
            } else {
                allRefs.remove(nfcFile.getName());
            }
        }
        // if we renamed any files to nfc format, we must rerun the check
        // to make sure the renamed files are not marked as unused
        if (renamedFiles) {
            return check(local);
        }
        List<String> nohave = new ArrayList<String>();
        for (String x : allRefs) {
            if (!x.startsWith("_")) {
                nohave.add(x);
            }
        }
        List<List<String>> result = new ArrayList<List<String>>();
        result.add(nohave);
        result.add(unused);
        result.add(invalid);
        return result;
    }


    private void _normalizeNoteRefs(long nid) throws APIVersionException {
        Note note = mCol.getNote(nid);
        String[] flds = note.getFields();
        for (int c = 0; c < flds.length; c++) {
            String fld = flds[c];
            String nfc = AnkiDroidApp.getCompat().nfcNormalized(fld);
            if (!nfc.equals(fld)) {
                note.setField(c, nfc);
            }
        }
        note.flush();
    }


    /**
     * Copying on import
     * ***********************************************************
     */

    public boolean have(String fname) {
        return new File(dir(), fname).exists();
    }

    /**
     * Illegal characters
     * ***********************************************************
     */

    public String stripIllegal(String str) {
        Matcher m = fIllegalCharReg.matcher(str);
        return m.replaceAll("");
    }


    public boolean hasIllegal(String str) {
        Matcher m = fIllegalCharReg.matcher(str);
        return m.find();
    }


    /**
     * Tracking changes
     * ***********************************************************
     */

    /**
     * Scan the media folder if it's changed, and note any changes.
     */
    public void findChanges() throws APIVersionException {
        if (_changed() != null) {
            _logChanges();
        }
    }


    public boolean haveDirty() {
        return mDb.queryScalar("select 1 from media where dirty=1 limit 1", false) > 0;
    }


    /**
     * Returns the number of seconds from epoch since the last modification to the file in path. Important: this method
     * does not automatically append the root media directory to the path; the FULL path of the file must be specified.
     * 
     * @param path The path to the file we are checking. path can be a file or a directory.
     * @return The number of seconds (rounded down).
     */
    private long _mtime(String path) {
        File f = new File(path);
        return f.lastModified() / 1000;
    }


    private String _checksum(String path) {
        return Utils.fileChecksum(path);
    }


    /**
     * Return dir mtime if it has changed since the last findChanges()
     * Doesn't track edits, but user can add or remove a file to update
     *
     * @return The modification time of the media directory if it has changed since the last call of findChanges(). If
     *         it hasn't, it returns null.
     */
    public Long _changed() {
        long mod = mDb.queryLongScalar("select dirMod from meta");
        long mtime = _mtime(dir());
        if (!_isFAT32() && mod != 0 && mod == mtime) {
            return null;
        }
        return mtime;
    }


    private void _logChanges() throws APIVersionException  {
        Pair<List<String>, List<String>> result = _changes();
        List<String> added = result.first;
        List<String> removed = result.second;
        ArrayList<Object[]> media = new ArrayList<Object[]>();
        for (String f : added) {
            String path = new File(dir(), f).getAbsolutePath();
            long mt = _mtime(path);
            media.add(new Object[] { f, _checksum(path), mt, 1 });
        }
        for (String f : removed) {
            media.add(new Object[] { f, null, 0, 1});
        }
        // update media db
        mDb.executeMany("insert or replace into media values (?,?,?,?)", media);
        mDb.execute("update meta set dirMod = ?", new Object[] { _mtime(dir()) });
        mDb.commit();
    }


    private Pair<List<String>, List<String>> _changes() throws APIVersionException  {
        Map<String, Object[]> cache = new HashMap<String, Object[]>();
        Cursor cur = null;
        try {
            cur = mDb.getDatabase().rawQuery("select fname, csum, mtime from media where csum is not null", null);
            while (cur.moveToNext()) {
                String name = cur.getString(0);
                String csum = cur.getString(1);
                Long mod = cur.getLong(2);
                cache.put(name, new Object[] { csum, mod, false });
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
        // loop through on-disk files
        for (File f : new File(dir()).listFiles()) {
            // ignore folders and thumbs.db
            if (f.isDirectory()) {
                continue;
            }
            String fname = f.getName();
            if (fname.equalsIgnoreCase("thumbs.db")) {
                continue;
            }
            // and files with invalid chars
            if (hasIllegal(fname)) {
                continue;
            }
            // empty files are invalid; clean them up and continue
            if (f.length() == 0) {
                f.delete();
                continue;
            }
            // check encoding
            String normf = AnkiDroidApp.getCompat().nfcNormalized(fname);
            if (!fname.equals(normf)) {
                // wrong filename encoding which will cause sync errors
                File nf = new File(dir(), normf);
                if (nf.exists()) {
                    f.delete();
                } else {
                    f.renameTo(nf);
                }
            }
            // newly added?
            if (!cache.containsKey(fname)) {
                added.add(fname);
            } else {
                // modified since last time?
                if (_mtime(f.getAbsolutePath()) != (Long) cache.get(fname)[1]) {
                    // and has different checksum?
                    if (!_checksum(f.getAbsolutePath()).equals(cache.get(fname)[0])) {
                        added.add(fname);
                    }
                }
                // mark as used
                cache.get(fname)[2] = true;
            }
        }
        // look for any entries in the cache that no longer exist on disk
        for (String fname : cache.keySet()) {
            if (!((Boolean)cache.get(fname)[2])) {
                removed.add(fname);
            }
        }
        return new Pair<List<String>, List<String>>(added, removed);
    }


    /**
     * Syncing related
     * ***********************************************************
     */

    public int lastUsn() {
        return mDb.queryScalar("select lastUsn from meta", false);
    }


    public void setLastUsn(int usn) {
        mDb.execute("update meta set lastUsn = ?", new Object[] { usn });
        mDb.commit();
    }


    public Pair<String, Integer> syncInfo(String fname) {
        Cursor cur = null;
        try {
            cur = mDb.getDatabase().rawQuery("select csum, dirty from media where fname=?", new String[] { fname });
            if (cur.moveToNext()) {
                String csum = cur.getString(0);
                int dirty = cur.getInt(1);
                return new Pair<String, Integer>(csum, dirty);
            } else {
                return new Pair<String, Integer>(null, 0);
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
    }


    public void markClean(List<String> fnames) {
        for (String fname : fnames) {
            mDb.execute("update media set dirty=0 where fname=?", new Object[] { fname });
        }
    }


    public void syncDelete(String fname) {
        File f = new File(dir(), fname);
        if (f.exists()) {
            f.delete();
        }
        mDb.execute("delete from media where fname=?", new Object[] { fname });
    }


    public int mediacount() {
        return mDb.queryScalar("select count() from media where csum is not null", false);
    }


    public void forceResync() {
        mDb.execute("delete from media");
        mDb.execute("update meta set lastUsn=0,dirMod=0");
        mDb.execute("vacuum analyze");
        mDb.commit();
    }


    /**
     * Media syncing: zips
     * ***********************************************************
     */

    /**
     * Unlike python, our temp zip file will be on disk instead of in memory. This might be slower (not tested) but
     * avoids storing potentially large files in memory, which may not be desirable on a mobile device. <br>
     * Note: the maximum size of the file is decided by the constant SYNC_ZIP_SIZE. If a file exceeds this limit, only
     * that file (in full) will be sent to the server. This method will be repeatedly called until there are no more
     * files (marked "dirty" in the DB) to send. <br>
     * TODO: Investigate performance impact of in-memory zip file.
     */
    public Pair<File, List<String>> mediaChangesZip() throws APIVersionException {
        File f = new File(mCol.getPath().replaceFirst("collection\\.anki2$", "tmpSyncToServer.zip"));
        Cursor cur = null;
        try {
            ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
            z.setMethod(ZipOutputStream.DEFLATED);

            List<String> fnames = new ArrayList<String>();
            // meta is a list of (fname, zipname), where zipname of null is a deleted file
            // NOTE: In python, meta is a list of tuples that then gets serialized into json and added
            // to the zip as a string. In our version, we use JSON objects from the start to avoid the
            // serialization step. Instead of a list of tuples, we use JSONArrays of JSONArrays.
            JSONArray meta = new JSONArray();
            int sz = 0;
            byte buffer[] = new byte[2048];
            cur = mDb.getDatabase().rawQuery(
                    "select fname, csum from media where dirty=1 limit " + Consts.SYNC_ZIP_COUNT, null);

            for (int c = 0; cur.moveToNext(); c++) {
                String fname = cur.getString(0);
                String csum = cur.getString(1);
                fnames.add(fname);
                String normname = AnkiDroidApp.getCompat().nfcNormalized(fname);

                if (!TextUtils.isEmpty(csum)) {
                    Log.v(AnkiDroidApp.TAG, "+media zip " + fname);
                    File file = new File(dir(), fname);
                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file), 2048);
                    z.putNextEntry(new ZipEntry(Integer.toString(c)));
                    int count = 0;
                    while ((count = bis.read(buffer, 0, 2048)) != -1) {
                        z.write(buffer, 0, count);
                    }
                    z.closeEntry();
                    bis.close();
                    meta.put(new JSONArray().put(normname).put(Integer.toString(c)));
                    sz += file.length();
                } else {
                    Log.v(AnkiDroidApp.TAG, "-media zip " + fname);
                    meta.put(new JSONArray().put(normname).put(""));
                }
                if (sz >= Consts.SYNC_ZIP_SIZE) {
                    break;
                }
            }

            z.putNextEntry(new ZipEntry("_meta"));
            z.write(Utils.jsonToString(meta).getBytes());
            z.closeEntry();
            z.close();
            return new Pair<File, List<String>>(f, fnames);
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "Failed to create media changes zip", e);
            throw new RuntimeException(e);
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
    }

    
    /**
     * Extract zip data; return the number of files extracted. Consume a file stored on disk instead of a String buffer
     * like in python. This allows us to use ZipFile utilities to interact with the file efficiently but requires the
     * caller to save the zip to disk first. This also spares us from holding the entire downloaded data in memory,
     * which is not practical on a mobile device.
     */
    public int addFilesFromZip(ZipFile z) throws APIVersionException {
        try {
            List<Object[]> media = new ArrayList<Object[]>();
            // get meta info first
            JSONObject meta = new JSONObject(Utils.convertStreamToString(z.getInputStream(z.getEntry("_meta"))));
            // then loop through all files
            int cnt = 0;
            
            for (ZipEntry i : Collections.list(z.entries())) {
                if (i.getName().equals("_meta")) {
                    // ignore previously-retrieved meta
                    continue;
                } else {
                    String name = meta.getString(i.getName());
                    // normalize name for platform
                    name = AnkiDroidApp.getCompat().nfcNormalized(name);
                    // save file
                    String destPath = dir().concat(File.separator).concat(name);
                    Utils.writeToFile(z.getInputStream(i), destPath);
                    String csum = Utils.fileChecksum(destPath);
                    // update db
                    media.add(new Object[] {name, csum, _mtime(destPath), 0});
                    cnt += 1;
                }
            }
            z.close();
            if (media.size() > 0) {
                mDb.executeMany("insert or replace into media values (?,?,?,?)", media);
            }
            return cnt;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */

    /**
     * Used by unit tests only.
     */
    public AnkiDb getDb() {
        return mDb;
    }


    /**
     * Used by other classes to determine the index of a regular expression group named "fname"
     * (Anki2Importer needs this). This is needed because we didn't implement the "transformNames"
     * function and have delegated its job to the caller of this class.
     */
    public static int indexOfFname(Pattern p) {
        int fnameIdx = p == fSoundRegexps ? 2 : p == fImgRegExpU ? 2 : 3;
        return fnameIdx;
    }
}
