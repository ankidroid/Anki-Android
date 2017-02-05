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

import android.content.Context;

import com.ichi2.compat.CompatHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import timber.log.Timber;

class Exporter {
    Collection mCol;
    Long mDid;


    public Exporter(Collection col) {
        mCol = col;
        mDid = null;
    }


    public Exporter(Collection col, Long did) {
        mCol = col;
        mDid = did;
    }
}



class AnkiExporter extends Exporter {
    boolean mIncludeSched;
    boolean mIncludeMedia;
    Collection mSrc;
    String mMediaDir;
    int mCount;
    ArrayList<String> mMediaFiles = new ArrayList<>();


    public AnkiExporter(Collection col) {
        super(col);
        mIncludeSched = false;
        mIncludeMedia = true;
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

    public void exportInto(String path, Context context) throws JSONException, IOException {
        // create a new collection at the target
        new File(path).delete();
        Collection dst = Storage.Collection(context, path);
        mSrc = mCol;
        // find cards
        Long[] cids;
        if (mDid == null) {
            cids = Utils.list2ObjectArray(mSrc.getDb().queryColumn(Long.class, "SELECT id FROM cards", 0));
        } else {
            cids = mSrc.getDecks().cids(mDid, true);
        }
        // attach dst to src so we can copy data between them. This isn't done in original libanki as Python more
        // flexible
        dst.close();
        Timber.d("Attach DB");
        mSrc.getDb().getDatabase().execSQL("ATTACH '" + path + "' AS DST_DB");
        // copy cards, noting used nids (as unique set)
        Timber.d("Copy cards");
        mSrc.getDb().getDatabase()
                .execSQL("INSERT INTO DST_DB.cards select * from cards where id in " + Utils.ids2str(cids));
        Set<Long> nids = new HashSet<>(mSrc.getDb().queryColumn(Long.class,
                "select nid from cards where id in " + Utils.ids2str(cids), 0));
        // notes
        Timber.d("Copy notes");
        ArrayList<Long> uniqueNids = new ArrayList<>(nids);
        String strnids = Utils.ids2str(uniqueNids);
        mSrc.getDb().getDatabase().execSQL("INSERT INTO DST_DB.notes select * from notes where id in " + strnids);
        // remove system tags if not exporting scheduling info
        if (!mIncludeSched) {
            Timber.d("Stripping system tags from list");
            ArrayList<String> srcTags = mSrc.getDb().queryColumn(String.class,
                    "select tags from notes where id in " + strnids, 0);
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
        ArrayList<Long> mids = mSrc.getDb().queryColumn(Long.class,
                "select distinct mid from DST_DB.notes where id in " + strnids, 0);
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
        for (JSONObject m : mSrc.getModels().all()) {
            if (mids.contains(m.getLong("id"))) {
                dst.getModels().update(m);
            }
        }
        // decks
        Timber.d("Copy decks");
        ArrayList<Long> dids = new ArrayList<>();
        if (mDid != null) {
            dids.add(mDid);
            for (Long x : mSrc.getDecks().children(mDid).values()) {
                dids.add(x);
            }
        }
        JSONObject dconfs = new JSONObject();
        for (JSONObject d : mSrc.getDecks().all()) {
            if (d.getString("id").equals("1")) {
                continue;
            }
            if (mDid != null && !dids.contains(d.getLong("id"))) {
                continue;
            }
            if (d.getInt("dyn") != 1 && d.getLong("conf") != 1L) {
                if (mIncludeSched) {
                    dconfs.put(Long.toString(d.getLong("conf")), true);
                }
            }
            if (!mIncludeSched) {
                // scheduling not included, so reset deck settings to default
                d.put("conf", 1);
            }
            dst.getDecks().update(d);
        }
        // copy used deck confs
        Timber.d("Copy deck options");
        for (JSONObject dc : mSrc.getDecks().allConf()) {
            if (dconfs.has(dc.getString("id"))) {
                dst.getDecks().updateConf(dc);
            }
        }
        // find used media
        Timber.d("Find used media");
        JSONObject media = new JSONObject();
        mMediaDir = mSrc.getMedia().dir();
        if (mIncludeMedia) {
            ArrayList<Long> mid = mSrc.getDb().queryColumn(Long.class, "select mid from notes where id in " + strnids,
                    0);
            ArrayList<String> flds = mSrc.getDb().queryColumn(String.class,
                    "select flds from notes where id in " + strnids, 0);
            for (int idx = 0; idx < mid.size(); idx++) {
                for (String file : mSrc.getMedia().filesInStr(mid.get(idx), flds.get(idx))) {
                    media.put(file, true);
                }
            }
            if (mMediaDir != null) {
                for (File f : new File(mMediaDir).listFiles()) {
                    String fname = f.getName();
                    if (fname.startsWith("_")) {
                        // Loop through every model that will be exported, and check if it contains a reference to f
                        for (int idx = 0; idx < mid.size(); idx++) {
                            if (_modelHasMedia(mSrc.getModels().get(idx), fname)) {
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
            for (int i = 0; i < keys.length(); i++) {
                mMediaFiles.add(keys.getString(i));
            }
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
        for (int idx = 0; idx < tmpls.length(); idx++) {
            JSONObject tmpl = tmpls.getJSONObject(idx);
            if (tmpl.getString("qfmt").contains(fname) || tmpl.getString("afmt").contains(fname)) {
                return true;
            }
        }
        return false;
    }


    /**
     * overwrite to apply customizations to the deck before it's closed, such as update the deck description
     */
    protected void postExport() {
    }


    private String removeSystemTags(String tags) {
        return mSrc.getTags().remFromStr("marked leech", tags);
    }


    public void setIncludeSched(boolean includeSched) {
        mIncludeSched = includeSched;
    }


    public void setIncludeMedia(boolean includeMedia) {
        mIncludeMedia = includeMedia;
    }


    public void setDid(Long did) {
        mDid = did;
    }
}



public final class AnkiPackageExporter extends AnkiExporter {

    public AnkiPackageExporter(Collection col) {
        super(col);
    }


    @Override
    public void exportInto(String path, Context context) throws IOException, JSONException {
        // open a zip file
        ZipFile z = new ZipFile(path);
        // if all decks and scheduling included, full export
        JSONObject media;
        if (mIncludeSched && mDid == null) {
            media = exportVerbatim(z);
        } else {
            // otherwise, filter
            media = exportFiltered(z, path, context);
        }
        // media map
        z.writeStr("media", Utils.jsonToString(media));
        z.close();
    }


    private JSONObject exportVerbatim(ZipFile z) throws IOException {
        // close our deck & write it into the zip file, and reopen
        mCount = mCol.cardCount();
        mCol.close();
        z.write(mCol.getPath(), "collection.anki2");
        mCol.reopen();
        // copy all media
        JSONObject media = new JSONObject();
        if (!mIncludeMedia) {
            return media;
        }
        File mdir = new File(mCol.getMedia().dir());
        if (mdir.exists() && mdir.isDirectory()) {
            File[] mediaFiles = mdir.listFiles();
            int c = 0;
            for (File f : mediaFiles) {
                z.write(f.getPath(), Integer.toString(c));
                try {
                    media.put(Integer.toString(c), f.getName());
                    c++;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return media;
    }


    private JSONObject exportFiltered(ZipFile z, String path, Context context) throws IOException, JSONException {
        // export into the anki2 file
        String colfile = path.replace(".apkg", ".anki2");
        super.exportInto(colfile, context);
        z.write(colfile, "collection.anki2");
        // and media
        prepareMedia();
        JSONObject media = new JSONObject();
        File mdir = new File(mCol.getMedia().dir());
        if (mdir.exists() && mdir.isDirectory()) {
            int c = 0;
            for (String file : mMediaFiles) {
                File mpath = new File(mdir,file);
                if (mpath.exists()) {
                    z.write(mpath.getPath(), Integer.toString(c));
                }
                try {
                    media.put(Integer.toString(c), file);
                    c++;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        // tidy up intermediate files
        CompatHelper.getCompat().deleteDatabase(new File(colfile));
        CompatHelper.getCompat().deleteDatabase(new File(path.replace(".apkg", ".media.ad.db2")));
        String tempPath = path.replace(".apkg", ".media");
        File file = new File(tempPath);
        if (file.exists()) {
            String deleteCmd = "rm -r " + tempPath;
            Runtime runtime = Runtime.getRuntime();
            try {
                runtime.exec(deleteCmd);
            } catch (IOException e) {
            }
        }
        return media;
    }


    protected void prepareMedia() {
        // chance to move each file in self.mediaFiles into place before media
        // is zipped up
    }
}



/**
 * Wrapper around standard Python zip class used in this module for exporting to APKG
 * 
 * @author Tim
 */
class ZipFile {
    final int BUFFER_SIZE = 1024;
    private ZipOutputStream mZos;


    public ZipFile(String path) throws FileNotFoundException {
        mZos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
    }


    public void write(String path, String entry) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(path), BUFFER_SIZE);
        ZipEntry ze = new ZipEntry(entry);
        writeEntry(bis, ze);
    }


    public void writeStr(String entry, String value) throws IOException {
        // TODO: Does this work with abnormal characters?
        InputStream is = new ByteArrayInputStream(value.getBytes());
        BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE);
        ZipEntry ze = new ZipEntry(entry);
        writeEntry(bis, ze);
    }


    private void writeEntry(BufferedInputStream bis, ZipEntry ze) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        mZos.putNextEntry(ze);
        int len;
        while ((len = bis.read(buf, 0, BUFFER_SIZE)) != -1) {
            mZos.write(buf, 0, len);
        }
        mZos.closeEntry();
        bis.close();
    }


    public void close() {
        try {
            mZos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
