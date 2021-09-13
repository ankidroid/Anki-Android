/****************************************************************************************
 * Copyright (c) 2014 Timothy Rae   <perceptualchaos2@gmail.com>                        *
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.StringUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

import static com.ichi2.utils.CollectionUtils.addAll;

class Exporter {
    @NonNull
    protected final Collection mCol;
    /**
     * If set exporter will export only this deck, otherwise will export all cards
     */
    @Nullable
    protected final Long mDid;
    protected int mCount;
    protected boolean mIncludeHTML;

    /**
     * An exporter for the whole collection of decks
     *
     * @param col deck collection
     */
    public Exporter(@NonNull Collection col) {
        mCol = col;
        mDid = null;
    }


    /**
     * An exporter for the content of a deck
     *
     * @param col deck collection
     * @param did deck id
     */
    public Exporter(@NonNull Collection col, @NonNull Long did) {
        mCol = col;
        mDid = did;
    }


    /**
     * Fetches the ids of cards to be exported
     *
     * @return list of card ids
     */
    public Long[] cardIds() {
        Long[] cids;
        if (mDid == null) {
            cids = Utils.list2ObjectArray(mCol.getDb().queryLongList("select id from cards"));
        } else {
            cids = Utils.list2ObjectArray(mCol.getDecks().cids(mDid, true));
        }
        mCount = cids.length;
        return cids;
    }


    @NonNull
    protected String processText(@NonNull String text) {
        if (!mIncludeHTML) {
            text = stripHTML(text);
        }

        text = escapeText(text);

        return text;
    }


    /**
     * Escape newlines, tabs, CSS and quotechar.
     */
    @NonNull
    protected String escapeText(@NonNull String text) {
        //pylib:fixme: we should probably quote fields with newlines instead of converting them to spaces
        text = text.replace("\\n", " ");
        text = text.replace("\\r", "");

        //pylib: text = text.replace("\t", " " * 8)
        text = text.replace("\\t", "        "/*8 spaced*/);

        text = text.replaceAll("(?i)<style>.*?</style>", "");
        text = text.replaceAll("\\[\\[type:[^]]+\\]\\]", "");

        if (text.contains("\"")) {
            text = '"' + text.replace("\"", "\"\"") + "\"";
        }

        return text;
    }


    /**
     * very basic conversion to text
     */
    @NonNull
    protected String stripHTML(@NonNull String text) {
        String s = text;
        s = s.replaceAll("(?i)<(br ?/?|div|p)>", " ");
        s = s.replaceAll("\\[sound:[^]]+\\]", "");
        s = Utils.stripHTML(s);
        s = s.replaceAll("[ \\n\\t]+", " ");
        s = StringUtil.strip(s);
        return s;
    }
}


@SuppressWarnings({"PMD.AvoidReassigningParameters","PMD.DefaultPackage",
        "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.ExcessiveMethodLength",
        "PMD.EmptyIfStmt","PMD.CollapsibleIfStatements"})
class AnkiExporter extends Exporter {
    protected final boolean mIncludeSched;
    protected final boolean mIncludeMedia;
    private Collection mSrc;
    String mMediaDir;
    // Actual capacity will be set when known, if media are imported.
    final ArrayList<String> mMediaFiles = new ArrayList<>(0);
    @SuppressLint("NonPublicNonStaticFieldName")
    boolean _v2sched;


    /**
     * An exporter for the whole collection of decks
     *
     * @param col deck collection
     * @param includeSched should include scheduling
     * @param includeMedia should include media
     */
    public AnkiExporter(@NonNull Collection col, boolean includeSched, boolean includeMedia) {
        super(col);
        mIncludeSched = includeSched;
        mIncludeMedia = includeMedia;
    }


    /**
     * An exporter for the selected deck
     *
     * @param col deck collection
     * @param did selected deck id
     * @param includeSched should include scheduling
     * @param includeMedia should include media
     */
    public AnkiExporter(@NonNull Collection col, @NonNull Long did, boolean includeSched, boolean includeMedia) {
        super(col, did);
        mIncludeSched = includeSched;
        mIncludeMedia = includeMedia;
    }


    /**
     * Export source database into new destination database Note: The following python syntax isn't supported in
     * Android: for row in mSrc.db.execute("select * from cards where id in "+ids2str(cids)): therefore we use a
     * different method for copying tables
     *
     * @param path String path to destination database
     * @throws JSONException
     * @throws IOException
     */

    public void exportInto(String path, Context context) throws JSONException, IOException, ImportExportException {
        // create a new collection at the target
        new File(path).delete();
        Collection dst = Storage.Collection(context, path);
        mSrc = mCol;
        // find cards
        Long[] cids = cardIds();
        // attach dst to src so we can copy data between them. This isn't done in original libanki as Python more
        // flexible
        dst.close();
        Timber.d("Attach DB");
        mSrc.getDb().getDatabase().execSQL("ATTACH '" + path + "' AS DST_DB");
        // copy cards, noting used nids (as unique set)
        Timber.d("Copy cards");
        mSrc.getDb().getDatabase()
                .execSQL("INSERT INTO DST_DB.cards select * from cards where id in " + Utils.ids2str(cids));
        List<Long> uniqueNids = mSrc.getDb().queryLongList(
                "select distinct nid from cards where id in " + Utils.ids2str(cids));
        // notes
        Timber.d("Copy notes");
        String strnids = Utils.ids2str(uniqueNids);
        mSrc.getDb().getDatabase().execSQL("INSERT INTO DST_DB.notes select * from notes where id in " + strnids);
        // remove system tags if not exporting scheduling info
        if (!mIncludeSched) {
            Timber.d("Stripping system tags from list");
            ArrayList<String> srcTags = mSrc.getDb().queryStringList(
                    "select tags from notes where id in " + strnids);
            ArrayList<Object[]> args = new ArrayList<>(srcTags.size());
            Object [] arg = new Object[2];
            for (int row = 0; row < srcTags.size(); row++) {
                arg[0]=removeSystemTags(srcTags.get(row));
                arg[1]=uniqueNids.get(row);
                args.add(row, arg);
            }
            mSrc.getDb().executeMany("UPDATE DST_DB.notes set tags=? where id=?", args);
        }
        // models used by the notes
        Timber.d("Finding models used by notes");
        ArrayList<Long> mids = mSrc.getDb().queryLongList(
                "select distinct mid from DST_DB.notes where id in " + strnids);
        // card history and revlog
        if (mIncludeSched) {
            Timber.d("Copy history and revlog");
            mSrc.getDb().getDatabase()
                    .execSQL("insert into DST_DB.revlog select * from revlog where cid in " + Utils.ids2str(cids));
            // reopen collection to destination database (different from original python code)
            mSrc.getDb().getDatabase().execSQL("DETACH DST_DB");
            dst.reopen();
        } else {
            Timber.d("Detaching destination db and reopening");
            // first reopen collection to destination database (different from original python code)
            mSrc.getDb().getDatabase().execSQL("DETACH DST_DB");
            dst.reopen();
            // then need to reset card state
            Timber.d("Resetting cards");
            dst.getSched().resetCards(cids);
        }
        // models - start with zero
        Timber.d("Copy models");
        for (Model m : mSrc.getModels().all()) {
            if (mids.contains(m.getLong("id"))) {
                dst.getModels().update(m);
            }
        }
        // decks
        Timber.d("Copy decks");
        java.util.Collection<Long> dids = null;
        if (mDid != null) {
            dids = new HashSet<>(mSrc.getDecks().children(mDid).values());
            dids.add(mDid);
        }
        JSONObject dconfs = new JSONObject();
        for (Deck d : mSrc.getDecks().all()) {
            if ("1".equals(d.getString("id"))) {
                continue;
            }
            if (dids != null && !dids.contains(d.getLong("id"))) {
                continue;
            }
            if (d.isStd() && d.getLong("conf") != 1L) {
                if (mIncludeSched) {
                    dconfs.put(Long.toString(d.getLong("conf")), true);
                }
            }

            Deck destinationDeck = d.deepClone();
            if (!mIncludeSched) {
                // scheduling not included, so reset deck settings to default
                destinationDeck.put("conf", 1);
            }
            dst.getDecks().update(destinationDeck);
        }
        // copy used deck confs
        Timber.d("Copy deck options");
        for (DeckConfig dc : mSrc.getDecks().allConf()) {
            if (dconfs.has(dc.getString("id"))) {
                dst.getDecks().updateConf(dc);
            }
        }
        // find used media
        Timber.d("Find used media");
        JSONObject media = new JSONObject();
        mMediaDir = mSrc.getMedia().dir();
        if (mIncludeMedia) {
            ArrayList<Long> mid = mSrc.getDb().queryLongList("select mid from notes where id in " + strnids);
            ArrayList<String> flds = mSrc.getDb().queryStringList(
                    "select flds from notes where id in " + strnids);
            for (int idx = 0; idx < mid.size(); idx++) {
                for (String file : mSrc.getMedia().filesInStr(mid.get(idx), flds.get(idx))) {
                    // skip files in subdirs
                    if (file.contains(File.separator)) {
                        continue;
                    }
                    media.put(file, true);
                }
            }
            if (mMediaDir != null) {
                for (File f : new File(mMediaDir).listFiles()) {
                    if (f.isDirectory()) {
                        continue;
                    }
                    String fname = f.getName();
                    if (fname.startsWith("_")) {
                        // Loop through every model that will be exported, and check if it contains a reference to f
                        for (JSONObject model : mSrc.getModels().all()) {
                            if (_modelHasMedia(model, fname)) {
                                media.put(fname, true);
                                break;
                            }
                        }
                    }
                }
            }
        }
        JSONArray keys = media.names();
        if (keys != null) {
            mMediaFiles.ensureCapacity(keys.length());
            addAll(mMediaFiles, keys.stringIterable());
        }
        Timber.d("Cleanup");
        dst.setCrt(mSrc.getCrt());
        // todo: tags?
        mCount = dst.cardCount();
        dst.setMod();
        postExport();
        dst.close();
    }

    /**
     * Returns whether or not the specified model contains a reference to the given media file.
     * In order to ensure relatively fast operation we only check if the styling, front, back templates *contain* fname,
     * and thus must allow for occasional false positives.
     * @param model the model to scan
     * @param fname the name of the media file to check for
     * @return
     * @throws JSONException
     */
    private boolean _modelHasMedia(JSONObject model, String fname) throws JSONException {
        // Don't crash if the model is null
        if (model == null) {
            Timber.w("_modelHasMedia given null model");
            return true;
        }
        // First check the styling
        if (model.getString("css").contains(fname)) {
            return true;
        }
        // If not there then check the templates
        JSONArray tmpls = model.getJSONArray("tmpls");
        for (JSONObject tmpl: tmpls.jsonObjectIterable()) {
            if (tmpl.getString("qfmt").contains(fname) || tmpl.getString("afmt").contains(fname)) {
                return true;
            }
        }
        return false;
    }


    /**
     * override to apply customizations to the deck before it's closed, such as update the deck description
     */
    protected void postExport() {
        // do nothing
    }


    private String removeSystemTags(String tags) {
        return mSrc.getTags().remFromStr("marked leech", tags);
    }
}



public final class AnkiPackageExporter extends AnkiExporter {

    /**
     * An exporter for the whole collection of decks
     *
     * @param col deck collection
     * @param includeSched should include scheduling
     * @param includeMedia should include media
     */
    public AnkiPackageExporter(@NonNull Collection col, boolean includeSched, boolean includeMedia) {
        super(col, includeSched, includeMedia);
    }


    /**
     * An exporter for a selected deck
     *
     * @param col deck collection
     * @param did selected deck id
     * @param includeSched should include scheduling
     * @param includeMedia should include media
     */
    public AnkiPackageExporter(@NonNull Collection col, @NonNull Long did, boolean includeSched, boolean includeMedia) {
        super(col, did, includeSched, includeMedia);
    }


    @Override
    public void exportInto(String path, Context context) throws IOException, JSONException, ImportExportException {
        // sched info+v2 scheduler not compatible w/ older clients
        Timber.i("Starting export into %s", path);
        _v2sched = mCol.schedVer() != 1 && mIncludeSched;

        // open a zip file
        ZipFile z = new ZipFile(path);
        // if all decks and scheduling included, full export
        JSONObject media;
        if (mIncludeSched && mDid == null) {
            media = exportVerbatim(z, context);
        } else {
            // otherwise, filter
            media = exportFiltered(z, path, context);
        }
        // media map
        z.writeStr("media", Utils.jsonToString(media));
        z.close();
    }


    private JSONObject exportVerbatim(ZipFile z, Context context) throws IOException {
        // close our deck & write it into the zip file, and reopen
        mCount = mCol.cardCount();
        mCol.close();
        if (!_v2sched) {
            z.write(mCol.getPath(), CollectionHelper.COLLECTION_FILENAME);
        } else {
            _addDummyCollection(z, context);
            z.write(mCol.getPath(), "collection.anki21");
        }

        mCol.reopen();
        // copy all media
        if (!mIncludeMedia) {
            return new JSONObject();
        }
        File mdir = new File(mCol.getMedia().dir());
        if (mdir.exists() && mdir.isDirectory()) {
            File[] mediaFiles = mdir.listFiles();
            return _exportMedia(z, mediaFiles, ValidateFiles.SKIP_VALIDATION);
        } else {
            return new JSONObject();
        }
    }

    private JSONObject _exportMedia(ZipFile z, ArrayList<String> fileNames, String mdir) throws IOException {
        int size = fileNames.size();
        int i = 0;
        File[] files = new File[size];
        for (String fileName: fileNames){
            files[i++] = new File(mdir, fileName);
        }
        return _exportMedia(z, files, ValidateFiles.VALIDATE);
    }

    private JSONObject _exportMedia(ZipFile z, File[] files, ValidateFiles validateFiles) throws IOException {
        int c = 0;
        JSONObject media = new JSONObject();
        for (File file : files) {
            // todo: deflate SVG files, as in dae/anki@a5b0852360b132c0d04094f5ca8f1933f64d7c7e
            if (validateFiles == ValidateFiles.VALIDATE && !file.exists()) {
                // Anki 2.1.30 does the same
                Timber.d("Skipping missing file %s", file);
                continue;
            }
            z.write(file.getPath(), Integer.toString(c));
            try {
                media.put(Integer.toString(c), file.getName());
                c++;
            } catch (JSONException e) {
                Timber.w(e);
            }
        }
        return media;
    }


    private JSONObject exportFiltered(ZipFile z, String path, Context context) throws IOException, JSONException, ImportExportException {
        // export into the anki2 file
        String colfile = path.replace(".apkg", ".anki2");

        super.exportInto(colfile, context);
        z.write(colfile, CollectionHelper.COLLECTION_FILENAME);
        // and media
        prepareMedia();
    	JSONObject media = _exportMedia(z, mMediaFiles, mCol.getMedia().dir());
        // tidy up intermediate files
        SQLiteDatabase.deleteDatabase(new File(colfile));
        SQLiteDatabase.deleteDatabase(new File(path.replace(".apkg", ".media.ad.db2")));
        String tempPath = path.replace(".apkg", ".media");
        File file = new File(tempPath);
        if (file.exists()) {
            String deleteCmd = "rm -r " + tempPath;
            Runtime runtime = Runtime.getRuntime();
            try {
                runtime.exec(deleteCmd);
            } catch (IOException ignored) {
                Timber.w(ignored);
            }
        }
        return media;
    }


    protected void prepareMedia() {
        // chance to move each file in self.mediaFiles into place before media
        // is zipped up
    }

    // create a dummy collection to ensure older clients don't try to read
    // data they don't understand
    private void _addDummyCollection(ZipFile zip, Context context) throws IOException {
        File f = File.createTempFile("dummy", ".anki2");
        String path = f.getAbsolutePath();
        f.delete();
        Collection c = Storage.Collection(context, path);
        @NonNull Note n = c.newNote();
        //The created dummy collection only contains the StdModels.
        //The field names for those are localised during creation, so we need to consider that when creating dummy note
        n.setItem(context.getString(R.string.front_field_name), context.getString(R.string.export_v2_dummy_note));
        c.addNote(n);
        c.save();
        c.close();
        zip.write(f.getAbsolutePath(), CollectionHelper.COLLECTION_FILENAME);
    }


    /** Whether media files should be validated before being added to the zip */
    private enum ValidateFiles {
        VALIDATE,
        SKIP_VALIDATION
    }
}



/**
 * Wrapper around standard Python zip class used in this module for exporting to APKG
 * 
 * @author Tim
 */
class ZipFile {
    private static final int BUFFER_SIZE = 1024;
    private ZipArchiveOutputStream mZos;


    public ZipFile(String path) throws FileNotFoundException {
        mZos = new ZipArchiveOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
    }


    public void write(String path, String entry) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(path), BUFFER_SIZE);
        ZipArchiveEntry ze = new ZipArchiveEntry(entry);
        writeEntry(bis, ze);
    }


    public void writeStr(String entry, String value) throws IOException {
        // TODO: Does this work with abnormal characters?
        InputStream is = new ByteArrayInputStream(value.getBytes());
        BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE);
        ZipArchiveEntry ze = new ZipArchiveEntry(entry);
        writeEntry(bis, ze);
    }


    private void writeEntry(BufferedInputStream bis, ZipArchiveEntry ze) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        mZos.putArchiveEntry(ze);
        int len;
        while ((len = bis.read(buf, 0, BUFFER_SIZE)) != -1) {
            mZos.write(buf, 0, len);
        }
        mZos.closeArchiveEntry();
        bis.close();
    }


    public void close() {
        try {
            mZos.close();
        } catch (IOException e) {
            Timber.w(e);
        }
    }
}
