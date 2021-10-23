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

import android.util.Pair;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.libanki.exception.EmptyMediaException;
import com.ichi2.libanki.template.TemplateFilters;
import com.ichi2.utils.Assert;

import com.ichi2.utils.ExceptionUtil;
import com.ichi2.utils.HashUtil;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
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

import androidx.annotation.NonNull;
import timber.log.Timber;

import static java.lang.Math.min;

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
@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
        "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.ExcessiveMethodLength","PMD.OneDeclarationPerLine",
        "PMD.SwitchStmtsShouldHaveDefault","PMD.EmptyIfStmt","PMD.SimplifyBooleanReturns","PMD.CollapsibleIfStatements"})
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
    private static final Pattern fSoundRegexps = Pattern.compile("(?i)(\\[sound:([^]]+)])");

    // src element quoted case
    /**
     * Group 1 = Contents of <img> tag <br>
     * Group 2 = "str" <br>
     * Group 3 = "fname" <br>
     * Group 4 = Backreference to "str" (i.e., same type of quote character)
     */
    private static final Pattern fImgRegExpQ = Pattern.compile("(?i)(<img[^>]* src=([\"'])([^>]+?)(\\2)[^>]*>)");

    // unquoted case
    /**
     * Group 1 = Contents of <img> tag <br>
     * Group 2 = "fname"
     */
    private static final Pattern fImgRegExpU = Pattern.compile("(?i)(<img[^>]* src=(?!['\"])([^ >]+)[^>]*?>)");

    public static final List<Pattern> REGEXPS =  Arrays.asList(fSoundRegexps, fImgRegExpQ, fImgRegExpU);

    private final Collection mCol;
    private final String mDir;
    private DB mDb;


    public Media(Collection col, boolean server) {
        mCol = col;
        if (server) {
            mDir = null;
            return;
        }
        // media directory
        mDir = getCollectionMediaPath(col.getPath());
        File fd = new File(mDir);
        if (!fd.exists()) {
            if (!fd.mkdir()) {
                Timber.e("Cannot create media directory: %s", mDir);
            }
        }
        // change database
        connect();
    }


    @NonNull
    public static String getCollectionMediaPath(String collectionPath) {
        return collectionPath.replaceFirst("\\.anki2$", ".media");
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
        mDb = new DB(path);
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
                             " select fname, null, 0, 1 from old.log where type=" + Consts.CARD_TYPE_LRN + ";";
                mDb.execute(sql);
                mDb.execute("delete from meta");
                mDb.execute("insert into meta select dirMod, usn from old.meta");
                mDb.commit();
            } catch (Exception e) {
                // if we couldn't import the old db for some reason, just start anew
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                mCol.log("failed to import old media db:" + sw.toString());
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
        mDb.close();
        mDb = null;
    }

    private void _deleteDB() {
        String path = mDb.getPath();
        close();
        (new File(path)).delete();
        connect();
    }

    public String dir() {
        return mDir;
    }


    /*
      Adding media
      ***********************************************************
     */

    /**
     * In AnkiDroid, adding a media file will not only copy it to the media directory, but will also insert an entry
     * into the media database marking it as a new addition.
     */
    public String addFile(File ofile) throws IOException, EmptyMediaException {
        if (ofile == null || ofile.length() == 0) {
            throw new EmptyMediaException();
        }
        String fname = writeData(ofile);
        markFileAdd(fname);
        return fname;
    }


    /**
     * Copy a file to the media directory and return the filename it was stored as.
     * <p>
     * Unlike the python version of this method, we don't read the file into memory as a string. All our operations are
     * done on streams opened on the file, so there is no second parameter for the string object here.
     */
    private String writeData(File ofile) throws IOException {
        // get the file name
        String fname = ofile.getName();
        // make sure we write it in NFC form and return an NFC-encoded reference
        fname = Utils.nfcNormalized(fname);
        // ensure it's a valid finename
        String base = cleanFilename(fname);
        String[] split = Utils.splitFilename(base);
        String root = split[0];
        String ext = split[1];
        // find the first available name
        String csum = Utils.fileChecksum(ofile);
        while (true) {
            fname = root + ext;
            File path = new File(dir(), fname);
            // if it doesn't exist, copy it directly
            if (!path.exists()) {
                Utils.copyFile(ofile, path);
                return fname;
            }
            // if it's identical, reuse
            if (Utils.fileChecksum(path).equals(csum)) {
                return fname;
            }
            // otherwise, increment the index in the filename
            Pattern reg = Pattern.compile(" \\((\\d+)\\)$");
            Matcher m = reg.matcher(root);
            if (!m.find()) {
                root = root + " (1)";
            } else {
                int n = Integer.parseInt(m.group(1));
                root = String.format(Locale.US, " (%d)", n + 1);
            }
        }
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
        List<String> l = new ArrayList<>();
        Model model = mCol.getModels().get(mid);
        List<String> strings = new ArrayList<>();
        if (model.isCloze() && string.contains("{{c")) {
            // if the field has clozes in it, we'll need to expand the
            // possibilities so we can render latex
            strings = _expandClozes(string);
        } else {
            strings.add(string);
        }

        for (String s : strings) {
            // handle latex
            s =  LaTeX.mungeQA(s, mCol, model);
            // extract filenames
            Matcher m;
            for (Pattern p : REGEXPS) {
                // NOTE: python uses the named group 'fname'. Java doesn't have named groups, so we have to determine
                // the index based on which pattern we are using
                int fnameIdx = p.equals(fSoundRegexps) ? 2 : p.equals(fImgRegExpU) ? 2 : 3;
                m = p.matcher(s);
                while (m.find()) {
                    String fname = m.group(fnameIdx);
                    boolean isLocal = !fRemotePattern.matcher(fname.toLowerCase(Locale.getDefault())).find();
                    if (isLocal || includeRemote) {
                        l.add(fname);
                    }
                }
            }
        }
        return l;
    }


    private List<String> _expandClozes(String string) {
        Set<String> ords = new TreeSet<>();
        @SuppressWarnings("RegExpRedundantEscape") // In Android, } should be escaped
        Matcher m = Pattern.compile("\\{\\{c(\\d+)::.+?\\}\\}").matcher(string);
        while (m.find()) {
            ords.add(m.group(1));
        }
        ArrayList<String> strings = new ArrayList<>(ords.size() + 1);
        String clozeReg = TemplateFilters.CLOZE_REG;
        
        for (String ord : ords) {
            StringBuffer buf = new StringBuffer();
            m = Pattern.compile(String.format(Locale.US, clozeReg, ord)).matcher(string);
            while (m.find()) {
                if (!TextUtils.isEmpty(m.group(4))) {
                    m.appendReplacement(buf, "[$4]");
                } else {
                    m.appendReplacement(buf, TemplateFilters.CLOZE_DELETION_REPLACEMENT);
                }
            }
            m.appendTail(buf);
            String s = buf.toString().replaceAll(String.format(Locale.US, clozeReg, ".+?"), "$2");
            strings.add(s);
        }
        strings.add(string.replaceAll(String.format(Locale.US, clozeReg, ".+?"), "$2"));
        return strings;
    }


    /**
     * Strips a string from media references.
     *
     * @param txt The string to be cleared of media references.
     * @return The media-free string.
     */
    public String strip(String txt) {
        for (Pattern p : REGEXPS) {
            txt = p.matcher(txt).replaceAll("");
        }
        return txt;
    }


    public static String escapeImages(String string) {
        return escapeImages(string, false);
    }


    /**
     * Percent-escape UTF-8 characters in local image filenames.
     * @param string The string to search for image references and escape the filenames.
     * @return The string with the filenames of any local images percent-escaped as UTF-8.
     */
    public static String escapeImages(String string, boolean unescape) {
        for (Pattern p : Arrays.asList(fImgRegExpQ, fImgRegExpU)) {
            Matcher m = p.matcher(string);
            // NOTE: python uses the named group 'fname'. Java doesn't have named groups, so we have to determine
            // the index based on which pattern we are using
            int fnameIdx = p.equals(fImgRegExpU) ? 2 : 3;
            while (m.find()) {
                String tag = m.group(0);
                String fname = m.group(fnameIdx);
                if (fRemotePattern.matcher(fname).find()) {
                    //dont't do any escaping if remote image
                } else {
                    if (unescape) {
                        string = string.replace(tag,tag.replace(fname, Uri.decode(fname)));
                    } else {
                        string = string.replace(tag,tag.replace(fname, Uri.encode(fname, "/")));
                    }
                }
            }
        }
        return string;
    }


    /*
      Rebuilding DB
      ***********************************************************
     */

    /**
     * Finds missing, unused and invalid media files
     *
     * @return A list containing three lists of files (missingFiles, unusedFiles, invalidFiles)
     */
    public @NonNull List<List<String>> check() {
        return check(null);
    }


    private @NonNull List<List<String>> check(File[] local) {
        File mdir = new File(dir());
        // gather all media references in NFC form
        Set<String> allRefs = new HashSet<>();
        try (Cursor cur = mCol.getDb().query("select id, mid, flds from notes")) {
            while (cur.moveToNext()) {
                long nid = cur.getLong(0);
                long mid = cur.getLong(1);
                String flds = cur.getString(2);
                List<String> noteRefs = filesInStr(mid, flds);
                // check the refs are in NFC
                for (String f : noteRefs) {
                    // if they're not, we'll need to fix them first
                    if (!f.equals(Utils.nfcNormalized(f))) {
                        _normalizeNoteRefs(nid);
                        noteRefs = filesInStr(mid, flds);
                        break;
                    }
                }
                allRefs.addAll(noteRefs);
            }
        }
        // loop through media folder
        List<String> unused = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
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
            File nfcFile = new File(dir(), Utils.nfcNormalized(file.getName()));
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
        List<String> nohave = new ArrayList<>();
        for (String x : allRefs) {
            if (!x.startsWith("_")) {
                nohave.add(x);
            }
        }
        // make sure the media DB is valid
        try {
            findChanges();
        } catch (SQLException ignored) {
            Timber.w(ignored);
            _deleteDB();
        }
        List<List<String>> result = new ArrayList<>(3);
        result.add(nohave);
        result.add(unused);
        result.add(invalid);
        return result;
    }


    private void _normalizeNoteRefs(long nid) {
        Note note = mCol.getNote(nid);
        String[] flds = note.getFields();
        for (int c = 0; c < flds.length; c++) {
            String fld = flds[c];
            String nfc = Utils.nfcNormalized(fld);
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
     * Illegal characters and paths
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

    public String cleanFilename(String fname) {
        fname = stripIllegal(fname);
        fname = _cleanWin32Filename(fname);
        fname = _cleanLongFilename(fname);
        if ("".equals(fname)) {
            fname = "renamed";
        }

        return fname;
    }

    /** This method only change things on windows. So it's the
     * identity here. */
    private String _cleanWin32Filename(String fname) {
        return fname;
    }

    private String _cleanLongFilename(String fname) {
        /* a fairly safe limit that should work on typical windows
         paths and on eCryptfs partitions, even with a duplicate
         suffix appended */
        int namemax = 136;
        int pathmax = 1024; // 240 for windows

        // cap namemax based on absolute path
        int dirlen = fname.length();// ideally, name should be normalized. Without access to nio.Paths library, it's hard to do it really correctly. This is still a better approximation than nothing.
        int remaining = pathmax - dirlen;
        namemax = min(remaining, namemax);
        Assert.that(namemax>0, "The media directory is maximally long. There is no more length available for file name.");

        if (fname.length() > namemax) {
            int lastSlash = fname.indexOf("/");
            int lastDot = fname.indexOf(".");
            if (lastDot == -1 || lastDot < lastSlash) {
                // no dot, or before last slash
                fname = fname.substring(0, namemax);
            } else {
                String ext = fname.substring(lastDot+1);
                String head = fname.substring(0, lastDot);
                int headmax = namemax - ext.length();
                head = head.substring(0, headmax);
                fname = head + ext;
                Assert.that (fname.length() <= namemax, "The length of the file is greater than the maximal name value.");
            }
        }

        return fname;
    }

    /*
      Tracking changes
      ***********************************************************
     */

    /**
     * Scan the media folder if it's changed, and note any changes.
     */
    public void findChanges() {
        findChanges(false);
    }


    /**
     * @param force Unconditionally scan the media folder for changes (i.e., ignore differences in recorded and current
     *            directory mod times). Use this when rebuilding the media database.
     */
    public void findChanges(boolean force) {
        if (force || _changed() != null) {
            _logChanges();
        }
    }


    public boolean haveDirty() {
        return mDb.queryScalar("select 1 from media where dirty=1 limit 1") > 0;
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
        if (mod != 0 && mod == mtime) {
            return null;
        }
        return mtime;
    }


    private void _logChanges()  {
        Pair<List<String>, List<String>> result = _changes();
        List<String> added = result.first;
        List<String> removed = result.second;
        ArrayList<Object[]> media = new ArrayList<>(added.size() + removed.size());
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
        mDb.execute("update meta set dirMod = ?", _mtime(dir()));
        mDb.commit();
    }


    private Pair<List<String>, List<String>> _changes() {
        Map<String, Object[]> cache = HashUtil.HashMapInit(mDb.queryScalar("SELECT count() FROM media WHERE csum IS NOT NULL"));
        try (Cursor cur = mDb.query("select fname, csum, mtime from media where csum is not null")) {
            while (cur.moveToNext()) {
                String name = cur.getString(0);
                String csum = cur.getString(1);
                long mod = cur.getLong(2);
                cache.put(name, new Object[] { csum, mod, false });
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        // loop through on-disk files
        for (File f : new File(dir()).listFiles()) {
            // ignore folders and thumbs.db
            if (f.isDirectory()) {
                continue;
            }
            String fname = f.getName();
            if ("thumbs.db".equalsIgnoreCase(fname)) {
                continue;
            }
            // and files with invalid chars
            if (hasIllegal(fname)) {
                continue;
            }
            // empty files are invalid; clean them up and continue
            long sz = f.length();
            if (sz == 0) {
                f.delete();
                continue;
            }
            if (sz > 100*1024*1024) {
                mCol.log("ignoring file over 100MB", f);
                continue;
            }
            // check encoding
            String normf = Utils.nfcNormalized(fname);
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
        for (Map.Entry<String, Object[]> entry : cache.entrySet()) {
            if (!((Boolean) entry.getValue()[2])) {
                removed.add(entry.getKey());
            }
        }
        return new Pair<>(added, removed);
    }


    /**
     * Syncing related
     * ***********************************************************
     */

    public int lastUsn() {
        return mDb.queryScalar("select lastUsn from meta");
    }


    public void setLastUsn(int usn) {
        mDb.execute("update meta set lastUsn = ?", usn);
        mDb.commit();
    }


    public Pair<String, Integer> syncInfo(String fname) {
        try (Cursor cur = mDb.query("select csum, dirty from media where fname=?", fname)) {
            if (cur.moveToNext()) {
                String csum = cur.getString(0);
                int dirty = cur.getInt(1);
                return new Pair<>(csum, dirty);
            } else {
                return new Pair<>(null, 0);
            }
        }
    }


    public void markClean(List<String> fnames) {
        for (String fname : fnames) {
            mDb.execute("update media set dirty=0 where fname=?", fname);
        }
    }


    public void syncDelete(String fname) {
        File f = new File(dir(), fname);
        if (f.exists()) {
            f.delete();
        }
        mDb.execute("delete from media where fname=?", fname);
    }


    public int mediacount() {
        return mDb.queryScalar("select count() from media where csum is not null");
    }


    public int dirtyCount() {
        return mDb.queryScalar("select count() from media where dirty=1");
    }


    public void forceResync() {
        mDb.execute("delete from media");
        mDb.execute("update meta set lastUsn=0,dirMod=0");
        mDb.execute("vacuum");
        mDb.execute("analyze");
        mDb.commit();
    }


    /*
     * Media syncing: zips
     * ***********************************************************
     */

    /**
     * Unlike python, our temp zip file will be on disk instead of in memory. This avoids storing
     * potentially large files in memory which is not feasible with Android's limited heap space.
     * <p>
     * Notes:
     * <p>
     * - The maximum size of the changes zip is decided by the constant SYNC_ZIP_SIZE. If a media file exceeds this
     * limit, only that file (in full) will be zipped to be sent to the server.
     * <p>
     * - This method will be repeatedly called from MediaSyncer until there are no more files (marked "dirty" in the DB)
     * to send.
     * <p>
     * - Since AnkiDroid avoids scanning the media folder on every sync, it is possible for a file to be marked as a
     * new addition but actually have been deleted (e.g., with a file manager). In this case we skip over the file
     * and mark it as removed in the database. (This behaviour differs from the desktop client).
     * <p>
     */
    public Pair<File, List<String>> mediaChangesZip() {
        File f = new File(mCol.getPath().replaceFirst("collection\\.anki2$", "tmpSyncToServer.zip"));
        List<String> fnames = new ArrayList<>();
        try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
             Cursor cur = mDb.query(
                "select fname, csum from media where dirty=1 limit " + Consts.SYNC_ZIP_COUNT)
        ) {
            z.setMethod(ZipOutputStream.DEFLATED);

            // meta is a list of (fname, zipname), where zipname of null is a deleted file
            // NOTE: In python, meta is a list of tuples that then gets serialized into json and added
            // to the zip as a string. In our version, we use JSON objects from the start to avoid the
            // serialization step. Instead of a list of tuples, we use JSONArrays of JSONArrays.
            JSONArray meta = new JSONArray();
            int sz = 0;
            byte[] buffer = new byte[2048];


            for (int c = 0; cur.moveToNext(); c++) {
                String fname = cur.getString(0);
                String csum = cur.getString(1);
                fnames.add(fname);
                String normname = Utils.nfcNormalized(fname);

                if (!TextUtils.isEmpty(csum)) {
                    try {
                        mCol.log("+media zip " + fname);
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
                    } catch (FileNotFoundException e) {
                        Timber.w(e);
                        // A file has been marked as added but no longer exists in the media directory.
                        // Skip over it and mark it as removed in the db.
                        removeFile(fname);
                    }
                } else {
                    mCol.log("-media zip " + fname);
                    meta.put(new JSONArray().put(normname).put(""));
                }
                if (sz >= Consts.SYNC_ZIP_SIZE) {
                    break;
                }
            }

            z.putNextEntry(new ZipEntry("_meta"));
            z.write(Utils.jsonToString(meta).getBytes());
            z.closeEntry();
            // Don't leave lingering temp files if the VM terminates.
            f.deleteOnExit();
            return new Pair<>(f, fnames);
        } catch (IOException e) {
            Timber.e(e, "Failed to create media changes zip: ");
            throw new RuntimeException(e);
        }
    }


    /**
     * Extract zip data; return the number of files extracted. Unlike the python version, this method consumes a
     * ZipFile stored on disk instead of a String buffer. Holding the entire downloaded data in memory is not feasible
     * since some devices can have very limited heap space.
     *
     * This method closes the file before it returns.
     */
    public int addFilesFromZip(ZipFile z) throws IOException {
        try {
            // get meta info first
            JSONObject meta = new JSONObject(Utils.convertStreamToString(z.getInputStream(z.getEntry("_meta"))));
            // then loop through all files
            int cnt = 0;
            ArrayList<? extends ZipEntry> zipEntries = Collections.list(z.entries());
            List<Object[]> media = new ArrayList<>(zipEntries.size());
            for (ZipEntry i : zipEntries) {
                String fileName = i.getName();
                if ("_meta".equals(fileName)) {
                     // ignore previously-retrieved meta
                    continue;
                }
                String name = meta.getString(fileName);
                // normalize name for platform
                name = Utils.nfcNormalized(name);
                // save file
                String destPath = (dir() + File.separator) + name;
                try (InputStream zipInputStream = z.getInputStream(i)) {
                    Utils.writeToFile(zipInputStream, destPath);
                }
                String csum = Utils.fileChecksum(destPath);
                // update db
                media.add(new Object[] {name, csum, _mtime(destPath), 0});
                cnt += 1;
            }
            if (!media.isEmpty()) {
                mDb.executeMany("insert or replace into media values (?,?,?,?)", media);
            }
            return cnt;
        } finally {
            z.close();
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
    public DB getDb() {
        return mDb;
    }


    /**
     * Used by other classes to determine the index of a regular expression group named "fname"
     * (Anki2Importer needs this). This is needed because we didn't implement the "transformNames"
     * function and have delegated its job to the caller of this class.
     */
    public static int indexOfFname(Pattern p) {
        return p.equals(fSoundRegexps) ? 2 : p.equals(fImgRegExpU) ? 2 : 3;
    }


    /**
     * Add an entry into the media database for file named fname, or update it
     * if it already exists.
     */
    public void markFileAdd(String fname) {
        Timber.d("Marking media file addition in media db: %s", fname);
        String path = new File(dir(), fname).getAbsolutePath();
        mDb.execute("insert or replace into media values (?,?,?,?)",
                fname, _checksum(path), _mtime(path), 1);
    }


    /**
     * Remove a file from the media directory if it exists and mark it as removed in the media database.
     */
    public void removeFile(String fname) {
        File f = new File(dir(), fname);
        if (f.exists()) {
            f.delete();
        }
        Timber.d("Marking media file removal in media db: %s", fname);
        mDb.execute("insert or replace into media values (?,?,?,?)",
               fname, null, 0, 1);
    }


    /**
     * @return True if the media db has not been populated yet.
     */
    public boolean needScan() {
        long mod = mDb.queryLongScalar("select dirMod from meta");
        return mod == 0;
    }


    public void rebuildIfInvalid() throws IOException {
        try {
            _changed();
            return;
        } catch (Exception e) {
            if (!ExceptionUtil.containsMessage(e, "no such table: meta")) {
                throw e;
            }
            AnkiDroidApp.sendExceptionReport(e, "media::rebuildIfInvalid");

            // TODO: We don't know the root cause of the missing meta table
            Timber.w(e, "Error accessing media database. Rebuilding");
            // continue below
        }


        // Delete and recreate the file
        mDb.getDatabase().close();

        String path = mDb.getPath();
        Timber.i("Deleted %s", path);

        new File(path).delete();

        mDb = new DB(path);
        _initDB();
    }


}
