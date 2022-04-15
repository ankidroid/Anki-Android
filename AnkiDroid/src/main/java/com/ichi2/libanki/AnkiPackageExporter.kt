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

package com.ichi2.libanki

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.exception.ImportExportException
import com.ichi2.utils.CollectionUtils.addAll
import com.ichi2.utils.JSONException
import com.ichi2.utils.JSONObject
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.StringUtil.strip
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import timber.log.Timber
import java.io.*

@KotlinCleanup("lots in this file")
open class Exporter {
    @JvmField
    protected val mCol: Collection

    /**
     * If set exporter will export only this deck, otherwise will export all cards
     */
    protected val mDid: Long?
    @JvmField
    protected var mCount = 0
    @JvmField
    protected var mIncludeHTML = false

    /**
     * An exporter for the whole collection of decks
     *
     * @param col deck collection
     */
    constructor(col: Collection) {
        mCol = col
        mDid = null
    }

    /**
     * An exporter for the content of a deck
     *
     * @param col deck collection
     * @param did deck id
     */
    constructor(col: Collection, did: Long) {
        mCol = col
        mDid = did
    }

    /**
     * Fetches the ids of cards to be exported
     *
     * @return list of card ids
     */
    fun cardIds(): Array<Long> {
        val cids: Array<Long>
        cids = if (mDid == null) {
            Utils.list2ObjectArray(mCol.db.queryLongList("select id from cards"))
        } else {
            Utils.list2ObjectArray(mCol.decks.cids(mDid, true))
        }
        mCount = cids.size
        return cids
    }

    fun processText(input: String): String {
        var text = input
        if (!mIncludeHTML) {
            text = stripHTML(text)
        }
        text = escapeText(text)
        return text
    }

    /**
     * Escape newlines, tabs, CSS and quotechar.
     */
    protected fun escapeText(input: String): String {
        // pylib:fixme: we should probably quote fields with newlines instead of converting them to spaces
        var text = input
        text = text.replace("\\n", " ")
        text = text.replace("\\r", "")

        // pylib: text = text.replace("\t", " " * 8)
        text = text.replace("\\t", "        " /*8 spaced*/)
        text = text.replace("(?i)<style>.*?</style>".toRegex(), "")
        text = text.replace("\\[\\[type:[^]]+\\]\\]".toRegex(), "")
        if (text.contains("\"")) {
            text = '"'.toString() + text.replace("\"", "\"\"") + "\""
        }
        return text
    }

    /**
     * very basic conversion to text
     */
    fun stripHTML(text: String): String {
        var s = text
        s = s.replace("(?i)<(br ?/?|div|p)>".toRegex(), " ")
        s = s.replace("\\[sound:[^]]+\\]".toRegex(), "")
        s = Utils.stripHTML(s)
        s = s.replace("[ \\n\\t]+".toRegex(), " ")
        s = strip(s)!!
        return s
    }
}

open class AnkiExporter : Exporter {
    protected val mIncludeSched: Boolean
    protected val mIncludeMedia: Boolean
    private var mSrc: Collection? = null
    var mediaDir: String? = null

    // Actual capacity will be set when known, if media are imported.
    val mMediaFiles = ArrayList<String>(0)

    @SuppressLint("NonPublicNonStaticFieldName")
    var _v2sched = false

    /**
     * An exporter for the whole collection of decks
     *
     * @param col deck collection
     * @param includeSched should include scheduling
     * @param includeMedia should include media
     */
    constructor(col: Collection, includeSched: Boolean, includeMedia: Boolean) : super(col) {
        mIncludeSched = includeSched
        mIncludeMedia = includeMedia
    }

    /**
     * An exporter for the selected deck
     *
     * @param col deck collection
     * @param did selected deck id
     * @param includeSched should include scheduling
     * @param includeMedia should include media
     */
    constructor(col: Collection, did: Long, includeSched: Boolean, includeMedia: Boolean) : super(col, did) {
        mIncludeSched = includeSched
        mIncludeMedia = includeMedia
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
    @Throws(JSONException::class, IOException::class, ImportExportException::class)
    open fun exportInto(path: String, context: Context) {
        // create a new collection at the target
        File(path).delete()
        val dst = Storage.Collection(context, path)
        mSrc = mCol
        // find cards
        val cids: Array<Long> = cardIds()
        // attach dst to src so we can copy data between them. This isn't done in original libanki as Python more
        // flexible
        dst.close()
        Timber.d("Attach DB")
        mSrc!!.db.database.execSQL("ATTACH '$path' AS DST_DB")
        // copy cards, noting used nids (as unique set)
        Timber.d("Copy cards")
        mSrc!!.db.database
            .execSQL("INSERT INTO DST_DB.cards select * from cards where id in " + Utils.ids2str(cids))
        val uniqueNids: List<Long> = mSrc!!.db.queryLongList(
            "select distinct nid from cards where id in " + Utils.ids2str(cids)
        )
        // notes
        Timber.d("Copy notes")
        val strnids = Utils.ids2str(uniqueNids)
        mSrc!!.db.database.execSQL("INSERT INTO DST_DB.notes select * from notes where id in $strnids")
        // remove system tags if not exporting scheduling info
        if (!mIncludeSched) {
            Timber.d("Stripping system tags from list")
            val srcTags = mSrc!!.db.queryStringList(
                "select tags from notes where id in $strnids"
            )
            val args = ArrayList<Array<Any?>>(srcTags.size)
            val arg = arrayOfNulls<Any>(2)
            for (row in srcTags.indices) {
                arg[0] = removeSystemTags(srcTags[row])
                arg[1] = uniqueNids[row]
                args.add(row, arg)
            }
            mSrc!!.db.executeMany("UPDATE DST_DB.notes set tags=? where id=?", args)
        }
        // models used by the notes
        Timber.d("Finding models used by notes")
        val mids = mSrc!!.db.queryLongList(
            "select distinct mid from DST_DB.notes where id in $strnids"
        )
        // card history and revlog
        if (mIncludeSched) {
            Timber.d("Copy history and revlog")
            mSrc!!.db.database
                .execSQL("insert into DST_DB.revlog select * from revlog where cid in " + Utils.ids2str(cids))
            // reopen collection to destination database (different from original python code)
            mSrc!!.db.database.execSQL("DETACH DST_DB")
            dst.reopen()
        } else {
            Timber.d("Detaching destination db and reopening")
            // first reopen collection to destination database (different from original python code)
            mSrc!!.db.database.execSQL("DETACH DST_DB")
            dst.reopen()
            // then need to reset card state
            Timber.d("Resetting cards")
            dst.sched.resetCards(cids)
        }
        // models - start with zero
        Timber.d("Copy models")
        for (m in mSrc!!.models.all()) {
            if (mids.contains(m.getLong("id"))) {
                dst.models.update(m)
            }
        }
        // decks
        Timber.d("Copy decks")
        var dids: MutableCollection<Long?>? = null
        if (mDid != null) {
            dids = HashSet(mSrc!!.decks.children(mDid).values)
            dids.add(mDid)
        }
        val dconfs = JSONObject()
        for (d in mSrc!!.decks.all()) {
            if ("1" == d.getString("id")) {
                continue
            }
            if (dids != null && !dids.contains(d.getLong("id"))) {
                continue
            }
            if (d.isStd && d.getLong("conf") != 1L) {
                if (mIncludeSched) {
                    dconfs.put(java.lang.Long.toString(d.getLong("conf")), true)
                }
            }
            val destinationDeck = d.deepClone()
            if (!mIncludeSched) {
                // scheduling not included, so reset deck settings to default
                destinationDeck.put("conf", 1)
            }
            dst.decks.update(destinationDeck)
        }
        // copy used deck confs
        Timber.d("Copy deck options")
        for (dc in mSrc!!.decks.allConf()) {
            if (dconfs.has(dc.getString("id"))) {
                dst.decks.updateConf(dc)
            }
        }
        // find used media
        Timber.d("Find used media")
        val media = JSONObject()
        mediaDir = mSrc!!.media.dir()
        if (mIncludeMedia) {
            val mid = mSrc!!.db.queryLongList("select mid from notes where id in $strnids")
            val flds = mSrc!!.db.queryStringList(
                "select flds from notes where id in $strnids"
            )
            for (idx in mid.indices) {
                for (file in mSrc!!.media.filesInStr(mid[idx], flds[idx])) {
                    // skip files in subdirs
                    if (file.contains(File.separator)) {
                        continue
                    }
                    media.put(file, true)
                }
            }
            if (mediaDir != null) {
                @KotlinCleanup("!! usage")
                for (f in File(mediaDir!!).listFiles()!!) {
                    if (f.isDirectory) {
                        continue
                    }
                    val fname = f.name
                    if (fname.startsWith("_")) {
                        // Loop through every model that will be exported, and check if it contains a reference to f
                        for (model in mSrc!!.models.all()) {
                            if (_modelHasMedia(model, fname)) {
                                media.put(fname, true)
                                break
                            }
                        }
                    }
                }
            }
        }
        val keys = media.names()
        if (keys != null) {
            mMediaFiles.ensureCapacity(keys.length())
            addAll(mMediaFiles, keys.stringIterable())
        }
        Timber.d("Cleanup")
        dst.crt = mSrc!!.crt
        // todo: tags?
        mCount = dst.cardCount()
        dst.setMod()
        postExport()
        dst.close()
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
    @Throws(JSONException::class)
    private fun _modelHasMedia(model: JSONObject?, fname: String): Boolean {
        // Don't crash if the model is null
        if (model == null) {
            Timber.w("_modelHasMedia given null model")
            return true
        }
        // First check the styling
        if (model.getString("css").contains(fname)) {
            return true
        }
        // If not there then check the templates
        val tmpls = model.getJSONArray("tmpls")
        for (tmpl in tmpls.jsonObjectIterable()) {
            if (tmpl.getString("qfmt").contains(fname) || tmpl.getString("afmt").contains(fname)) {
                return true
            }
        }
        return false
    }

    /**
     * override to apply customizations to the deck before it's closed, such as update the deck description
     */
    protected fun postExport() {
        // do nothing
    }

    private fun removeSystemTags(tags: String): String {
        return mSrc!!.tags.remFromStr("marked leech", tags)
    }
}

class AnkiPackageExporter : AnkiExporter {
    /**
     * An exporter for the whole collection of decks
     *
     * @param col deck collection
     * @param includeSched should include scheduling
     * @param includeMedia should include media
     */
    constructor(col: Collection, includeSched: Boolean, includeMedia: Boolean) : super(col, includeSched, includeMedia) {}

    /**
     * An exporter for a selected deck
     *
     * @param col deck collection
     * @param did selected deck id
     * @param includeSched should include scheduling
     * @param includeMedia should include media
     */
    constructor(col: Collection, did: Long, includeSched: Boolean, includeMedia: Boolean) : super(col, did, includeSched, includeMedia) {}

    @Throws(IOException::class, JSONException::class, ImportExportException::class)
    override fun exportInto(path: String, context: Context) {
        // sched info+v2 scheduler not compatible w/ older clients
        Timber.i("Starting export into %s", path)
        _v2sched = mCol.schedVer() != 1 && mIncludeSched

        // open a zip file
        val z = ZipFile(path)
        // if all decks and scheduling included, full export
        val media: JSONObject
        media = if (mIncludeSched && mDid == null) {
            exportVerbatim(z, context)
        } else {
            // otherwise, filter
            exportFiltered(z, path, context)
        }
        // media map
        z.writeStr("media", Utils.jsonToString(media))
        z.close()
    }

    @KotlinCleanup("!! usage in listFiles")
    @Throws(IOException::class)
    private fun exportVerbatim(z: ZipFile, context: Context): JSONObject {
        // close our deck & write it into the zip file, and reopen
        mCount = mCol.cardCount()
        mCol.close()
        if (!_v2sched) {
            z.write(mCol.path, CollectionHelper.COLLECTION_FILENAME)
        } else {
            _addDummyCollection(z, context)
            z.write(mCol.path, "collection.anki21")
        }
        mCol.reopen()
        // copy all media
        if (!mIncludeMedia) {
            return JSONObject()
        }
        val mdir = File(mCol.media.dir())
        return if (mdir.exists() && mdir.isDirectory) {
            val mediaFiles = mdir.listFiles()!!
            _exportMedia(z, mediaFiles, ValidateFiles.SKIP_VALIDATION)
        } else {
            JSONObject()
        }
    }

    @Throws(IOException::class)
    private fun _exportMedia(z: ZipFile, fileNames: ArrayList<String>, mdir: String): JSONObject {
        val size = fileNames.size
        var i = 0
        val files = arrayOfNulls<File>(size)
        for (fileName in fileNames) {
            files[i++] = File(mdir, fileName)
        }
        return _exportMedia(z, files, ValidateFiles.VALIDATE)
    }

    @Throws(IOException::class)
    private fun _exportMedia(z: ZipFile, files: Array<File?>, validateFiles: ValidateFiles): JSONObject {
        var c = 0
        val media = JSONObject()
        for (file in files) {
            // todo: deflate SVG files, as in dae/anki@a5b0852360b132c0d04094f5ca8f1933f64d7c7e
            if (validateFiles == ValidateFiles.VALIDATE && !file!!.exists()) {
                // Anki 2.1.30 does the same
                Timber.d("Skipping missing file %s", file)
                continue
            }
            z.write(file!!.path, Integer.toString(c))
            try {
                media.put(Integer.toString(c), file.name)
                c++
            } catch (e: JSONException) {
                Timber.w(e)
            }
        }
        return media
    }

    @Throws(IOException::class, JSONException::class, ImportExportException::class)
    private fun exportFiltered(z: ZipFile, path: String, context: Context): JSONObject {
        // export into the anki2 file
        val colfile = path.replace(".apkg", ".anki2")
        super.exportInto(colfile, context)
        z.write(colfile, CollectionHelper.COLLECTION_FILENAME)
        // and media
        prepareMedia()
        val media = _exportMedia(z, mMediaFiles, mCol.media.dir())
        // tidy up intermediate files
        SQLiteDatabase.deleteDatabase(File(colfile))
        SQLiteDatabase.deleteDatabase(File(path.replace(".apkg", ".media.ad.db2")))
        val tempPath = path.replace(".apkg", ".media")
        val file = File(tempPath)
        if (file.exists()) {
            val deleteCmd = "rm -r $tempPath"
            val runtime = Runtime.getRuntime()
            try {
                runtime.exec(deleteCmd)
            } catch (ignored: IOException) {
                Timber.w(ignored)
            }
        }
        return media
    }

    protected fun prepareMedia() {
        // chance to move each file in self.mediaFiles into place before media
        // is zipped up
    }

    // create a dummy collection to ensure older clients don't try to read
    // data they don't understand
    @Throws(IOException::class)
    private fun _addDummyCollection(zip: ZipFile, context: Context) {
        val f = File.createTempFile("dummy", ".anki2")
        val path = f.absolutePath
        f.delete()
        val c = Storage.Collection(context, path)
        val n = c.newNote()
        // The created dummy collection only contains the StdModels.
        // The field names for those are localised during creation, so we need to consider that when creating dummy note
        n.setItem(context.getString(R.string.front_field_name), context.getString(R.string.export_v2_dummy_note))
        c.addNote(n)
        c.save()
        c.close()
        zip.write(f.absolutePath, CollectionHelper.COLLECTION_FILENAME)
    }

    /** Whether media files should be validated before being added to the zip  */
    private enum class ValidateFiles {
        VALIDATE, SKIP_VALIDATION
    }
}

/**
 * Wrapper around standard Python zip class used in this module for exporting to APKG
 *
 * @author Tim
 */
internal class ZipFile(path: String?) {
    private val mZos: ZipArchiveOutputStream
    @Throws(IOException::class)
    fun write(path: String?, entry: String?) {
        val bis = BufferedInputStream(FileInputStream(path), BUFFER_SIZE)
        val ze = ZipArchiveEntry(entry)
        writeEntry(bis, ze)
    }

    @Throws(IOException::class)
    fun writeStr(entry: String?, value: String) {
        // TODO: Does this work with abnormal characters?
        val `is`: InputStream = ByteArrayInputStream(value.toByteArray())
        val bis = BufferedInputStream(`is`, BUFFER_SIZE)
        val ze = ZipArchiveEntry(entry)
        writeEntry(bis, ze)
    }

    @Throws(IOException::class)
    private fun writeEntry(bis: BufferedInputStream, ze: ZipArchiveEntry) {
        val buf = ByteArray(BUFFER_SIZE)
        mZos.putArchiveEntry(ze)
        var len: Int
        while (bis.read(buf, 0, BUFFER_SIZE).also { len = it } != -1) {
            mZos.write(buf, 0, len)
        }
        mZos.closeArchiveEntry()
        bis.close()
    }

    fun close() {
        try {
            mZos.close()
        } catch (e: IOException) {
            Timber.w(e)
        }
    }

    companion object {
        private const val BUFFER_SIZE = 1024
    }

    init {
        mZos = ZipArchiveOutputStream(BufferedOutputStream(FileOutputStream(path)))
    }
}
