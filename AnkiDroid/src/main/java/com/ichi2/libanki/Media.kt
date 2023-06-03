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

package com.ichi2.libanki

import android.database.Cursor
import android.database.SQLException
import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.CrashReportService
import com.ichi2.libanki.exception.EmptyMediaException
import com.ichi2.libanki.template.TemplateFilters
import com.ichi2.utils.*
import com.ichi2.utils.HashUtil.HashMapInit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import net.ankiweb.rsdroid.BackendFactory
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.*
import java.text.Normalizer
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.min

/**
 * Media manager - handles the addition and removal of media files from the media directory (collection.media) and
 * maintains the media database (collection.media.ad.db2) which is used to determine the state of files for syncing.
 * Note that the media database has an additional prefix for AnkiDroid (.ad) to avoid any potential issues caused by
 * users copying the file to the desktop client and vice versa.
 *
 *
 * Unlike the python version of this module, we do not (and cannot) modify the current working directory (CWD) before
 * performing operations on media files. In python, the CWD is changed to the media directory, allowing it to easily
 * refer to the files in the media directory by name only. In Java, we must be cautious about when to specify the full
 * path to the file and when we need to use the filename only. In general, when we refer to a file on disk (i.e.,
 * creating a new File() object), we must include the full path. Use the dir() method to make this step easier.
 *
 * E.g: new File(dir(), "filename.jpg")
 */
@KotlinCleanup("IDE Lint")
open class Media(private val col: Collection, server: Boolean) {
    private var mDir: String?

    /**
     * Used by unit tests only.
     */
    @KotlinCleanup("non-null + exception if used after .close()")
    var db: DB? = null
        private set

    open fun connect() {
        if (col.server) {
            return
        }
        // NOTE: We use a custom prefix for AnkiDroid to avoid issues caused by copying
        // the db to the desktop or vice versa.
        val path = dir() + ".ad.db2"
        val dbFile = File(path)
        val create = !dbFile.exists()
        db = DB.withAndroidFramework(col.context, path)
        if (create) {
            _initDB()
        }
        maybeUpgrade()
    }

    fun _initDB() {
        val sql = """create table media (
 fname text not null primary key,
 csum text,           -- null indicates deleted file
 mtime int not null,  -- zero if deleted
 dirty int not null
);
create index idx_media_dirty on media (dirty);
create table meta (dirMod int, lastUsn int); insert into meta values (0, 0);"""
        db!!.executeScript(sql)
    }

    private fun maybeUpgrade() {
        val oldPath = dir() + ".db"
        val oldDbFile = File(oldPath)
        if (oldDbFile.exists()) {
            db!!.execute(String.format(Locale.US, "attach \"%s\" as old", oldPath))
            try {
                val sql = """insert into media
 select m.fname, csum, mod, ifnull((select 1 from log l2 where l2.fname=m.fname), 0) as dirty
 from old.media m
 left outer join old.log l using (fname)
 union
 select fname, null, 0, 1 from old.log where type=${Consts.CARD_TYPE_LRN};"""
                db!!.apply {
                    execute(sql)
                    execute("delete from meta")
                    execute("insert into meta select dirMod, usn from old.meta")
                    commit()
                }
            } catch (e: Exception) {
                // if we couldn't import the old db for some reason, just start anew
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                col.log("failed to import old media db:$sw")
            }
            db!!.execute("detach old")
            val newDbFile = File("$oldPath.old")
            if (newDbFile.exists()) {
                newDbFile.delete()
            }
            oldDbFile.renameTo(newDbFile)
        }
    }

    open fun close() {
        if (col.server) {
            return
        }
        db!!.close()
        db = null
    }

    private fun _deleteDB() {
        val path = db!!.path
        close()
        File(path).delete()
        connect()
    }

    @KotlinCleanup("nullable if server == true, we don't do this in AnkiDroid so should be fine")
    fun dir(): String = mDir!!

    /*
      Adding media
      ***********************************************************
     */
    /**
     * In AnkiDroid, adding a media file will not only copy it to the media directory, but will also insert an entry
     * into the media database marking it as a new addition.
     */
    @Throws(IOException::class, EmptyMediaException::class)
    @KotlinCleanup("scope function: fname")
    fun addFile(oFile: File?): String {
        if (oFile == null || oFile.length() == 0L) {
            throw EmptyMediaException()
        }
        val fname = writeData(oFile)
        markFileAdd(fname)
        return fname
    }

    /**
     * Copy a file to the media directory and return the filename it was stored as.
     *
     *
     * Unlike the python version of this method, we don't read the file into memory as a string. All our operations are
     * done on streams opened on the file, so there is no second parameter for the string object here.
     */
    @Throws(IOException::class)
    private fun writeData(oFile: File): String {
        // get the file name
        var fname = oFile.name
        // make sure we write it in NFC form and return an NFC-encoded reference
        fname = Utils.nfcNormalized(fname)
        // ensure it's a valid filename
        val base = cleanFilename(fname)
        val split = Utils.splitFilename(base)
        var root = split[0]
        val ext = split[1]
        // find the first available name
        val csum = Utils.fileChecksum(oFile)
        while (true) {
            fname = root + ext
            val path = File(dir(), fname)
            // if it doesn't exist, copy it directly
            if (!path.exists()) {
                Utils.copyFile(oFile, path)
                return fname
            }
            // if it's identical, reuse
            if (Utils.fileChecksum(path) == csum) {
                return fname
            }
            // otherwise, increment the checksum in the filename
            root = "$root-$csum"
        }
    }
    /**
     * Extract media filenames from an HTML string.
     *
     * @param string The string to scan for media filenames ([sound:...] or <img...>).
     * @param includeRemote If true will also include external http/https/ftp urls.
     * @return A list containing all the sound and image filenames found in the input string.
     */
    /**
     * String manipulation
     * ***********************************************************
     */
    fun filesInStr(mid: Long?, string: String, includeRemote: Boolean = false): List<String> {
        val l: MutableList<String> = ArrayList()
        val model = col.models.get(col, mid!!)
        var strings: MutableList<String?> = ArrayList()
        if (model!!.isCloze && string.contains("{{c")) {
            // if the field has clozes in it, we'll need to expand the
            // possibilities so we can render latex
            strings = _expandClozes(string)
        } else {
            strings.add(string)
        }
        for (s in strings) {
            @Suppress("NAME_SHADOWING")
            var s = s

            // handle latex
            @KotlinCleanup("change to .map { }")
            val svg = model.optBoolean("latexsvg", false)
            s = LaTeX.mungeQA(col, s!!, svg)
            // extract filenames
            var m: Matcher
            for (p in REGEXPS) {
                // NOTE: python uses the named group 'fname'. Java doesn't have named groups, so we have to determine
                // the index based on which pattern we are using
                val fnameIdx = if (p == fSoundRegexps) 2 else if (p == fImgAudioRegExpU) 2 else 3
                m = p.matcher(s)
                while (m.find()) {
                    val fname = m.group(fnameIdx)!!
                    val isLocal =
                        !fRemotePattern.matcher(fname.lowercase(Locale.getDefault())).find()
                    if (isLocal || includeRemote) {
                        l.add(fname)
                    }
                }
            }
        }
        return l
    }

    private fun _expandClozes(string: String): MutableList<String?> {
        val ords: MutableSet<String> = TreeSet()
        var m = // In Android, } should be escaped
            Pattern.compile("\\{\\{c(\\d+)::.+?\\}\\}").matcher(string)
        while (m.find()) {
            ords.add(m.group(1)!!)
        }
        val strings = ArrayList<String?>(ords.size + 1)
        val clozeReg = TemplateFilters.CLOZE_REG
        for (ord in ords) {
            val buf = StringBuffer()
            m = Pattern.compile(String.format(Locale.US, clozeReg, ord)).matcher(string)
            while (m.find()) {
                if (!m.group(4).isNullOrEmpty()) {
                    m.appendReplacement(buf, "[$4]")
                } else {
                    m.appendReplacement(buf, TemplateFilters.CLOZE_DELETION_REPLACEMENT)
                }
            }
            m.appendTail(buf)
            val s =
                buf.toString().replace(String.format(Locale.US, clozeReg, ".+?").toRegex(), "$2")
            strings.add(s)
        }
        strings.add(string.replace(String.format(Locale.US, clozeReg, ".+?").toRegex(), "$2"))
        return strings
    }

    /**
     * Strips a string from media references.
     *
     * @param txt The string to be cleared of media references.
     * @return The media-free string.
     */
    @KotlinCleanup("return early and remove var")
    fun strip(txt: String): String {
        @Suppress("NAME_SHADOWING")
        var txt = txt
        for (p in REGEXPS) {
            txt = p.matcher(txt).replaceAll("")
        }
        return txt
    }
    /*
      Rebuilding DB
      ***********************************************************
     */

    @VisibleForTesting
    fun performFullCheck(): MediaCheckResult {
        if (BackendFactory.defaultLegacySchema) {
            // Ensure that the DB is valid - unknown why, but some users were missing the meta table.
            rebuildIfInvalid()
            // A media check on AnkiDroid will also update the media db
            findChanges(true)
        }
        return check()
    }

    /**
     * Finds missing, unused and invalid media files
     *
     * @return A list containing three lists of files (missingFiles, unusedFiles, invalidFiles)
     */
    open fun check(): MediaCheckResult = check(null)

    private fun check(local: Array<File>?): MediaCheckResult {
        val mdir = File(dir())
        // gather all media references in NFC form
        val allRefs: MutableSet<String> = HashSet()
        col.db.query("select id, mid, flds from notes").use { cur ->
            while (cur.moveToNext()) {
                val nid = cur.getLong(0)
                val mid = cur.getLong(1)
                val flds = cur.getString(2)
                var noteRefs = filesInStr(mid, flds)
                // check the refs are in NFC
                @KotlinCleanup("simplify with first {}")
                for (f in noteRefs) {
                    // if they're not, we'll need to fix them first
                    if (f != Utils.nfcNormalized(f)) { // TODO Call Normalizer.isNormalized instead
                        _normalizeNoteRefs(nid)
                        noteRefs = filesInStr(mid, flds) // TODO It seems that this does nothing; investigate
                        break
                    }
                }
                allRefs.addAll(noteRefs)
            }
        }
        // loop through media directory
        val unused: MutableList<String> = ArrayList()
        val invalid: List<String> = ArrayList()
        val files: Array<File>
        files = local ?: mdir.listFiles()!!
        var renamedFiles = false
        for (file in files) {
            @Suppress("NAME_SHADOWING")
            var file = file
            if (local == null) {
                if (file.isDirectory) {
                    // ignore directories
                    continue
                }
            }
            if (file.name.startsWith("_")) {
                // leading _ says to ignore file
                continue
            }
            val nfcFile = File(dir(), Utils.nfcNormalized(file.name))
            // we enforce NFC fs encoding
            if (local == null) {
                if (file.name != nfcFile.name) {
                    // delete if we already have the NFC form, otherwise rename
                    renamedFiles = if (nfcFile.exists()) {
                        file.delete()
                        true
                    } else {
                        file.renameTo(nfcFile)
                        true
                    }
                    file = nfcFile
                }
            }
            // compare
            if (!allRefs.contains(nfcFile.name)) {
                unused.add(file.name)
            } else {
                allRefs.remove(nfcFile.name)
            }
        }
        // if we renamed any files to nfc format, we must rerun the check
        // to make sure the renamed files are not marked as unused
        if (renamedFiles) {
            return check(local)
        }
        @KotlinCleanup(".filter { }")
        val noHave: MutableList<String> = ArrayList()
        for (x in allRefs) {
            if (!x.startsWith("_")) {
                noHave.add(x)
            }
        }
        // make sure the media DB is valid
        try {
            findChanges()
        } catch (ignored: SQLException) {
            Timber.w(ignored)
            _deleteDB()
        }
        return MediaCheckResult(noHave, unused, invalid)
    }

    private fun _normalizeNoteRefs(nid: Long) {
        val note = col.getNote(nid)
        val flds = note.fields
        @KotlinCleanup("improve")
        for (c in flds.indices) {
            val fld = flds[c]
            val nfc = Utils.nfcNormalized(fld)
            if (nfc != fld) {
                note.setField(c, nfc)
            }
        }
        note.flush(col)
    }

    class MediaCheckRequiredException : Exception("Media check required")

    /**
     * Find unused media files. Cancellable.
     * If any file names, or file references in notes, are not NFC-normalized,
     * throws [MediaCheckRequiredException].
     *
     * TODO Consolidate this method and related media checking functionality.
     *   This method does what the [check] method does, except this is cancellable,
     *   does not change files in case of any problems, and is less broken.
     *   The backend also provides a method for checking media, [BackendMedia.check];
     *   however it seems it performs normalization unconditionally.
     */
    @Throws(MediaCheckRequiredException::class)
    fun CoroutineScope.findUnusedMediaFiles(): List<File> {
        val namesOfFilesUsedInNotes = mutableSetOf<String>()

        col.db.query("select mid, flds from notes").use { cursor: Cursor ->
            while (cursor.moveToNext()) {
                ensureActive()
                val modelId = cursor.getLong(0)
                val fields = cursor.getString(1)
                namesOfFilesUsedInNotes += filesInStr(modelId, fields)
            }
        }

        val mediaDirectoryFiles = File(dir()).listFiles()?.filter { !it.isDirectory } ?: emptyList()

        fun String.isNormalized() = Normalizer.isNormalized(this, Normalizer.Form.NFC)

        val allNamesAreNormalized = namesOfFilesUsedInNotes.all { it.isNormalized() } &&
            mediaDirectoryFiles.all { it.name.isNormalized() }

        if (!allNamesAreNormalized) throw MediaCheckRequiredException()

        val nonStaticMediaDirectoryFiles = mediaDirectoryFiles.filter { !it.name.startsWith("_") }

        return nonStaticMediaDirectoryFiles.filter { it.name !in namesOfFilesUsedInNotes }
    }

    /**
     * Copying on import
     * ***********************************************************
     */
    open fun have(fname: String): Boolean = File(dir(), fname).exists()

    /**
     * Illegal characters and paths
     * ***********************************************************
     */
    fun stripIllegal(str: String): String = fIllegalCharReg.matcher(str).replaceAll("")

    fun hasIllegal(str: String): Boolean = fIllegalCharReg.matcher(str).find()

    @KotlinCleanup("fix reassignment")
    fun cleanFilename(fname: String): String {
        @Suppress("NAME_SHADOWING")
        var fname = fname
        fname = stripIllegal(fname)
        fname = _cleanWin32Filename(fname)
        fname = _cleanLongFilename(fname)
        if ("" == fname) {
            fname = "renamed"
        }
        return fname
    }

    /** This method only change things on windows. So it's the
     * identity here.  */
    private fun _cleanWin32Filename(fname: String): String = fname

    @KotlinCleanup("Fix reassignment")
    private fun _cleanLongFilename(fname: String): String {
        /* a fairly safe limit that should work on typical windows
         paths and on eCryptfs partitions, even with a duplicate
         suffix appended */
        @Suppress("NAME_SHADOWING")
        var fname = fname
        var nameMax = 136
        val pathMax = 1024 // 240 for windows

        // cap nameMax based on absolute path
        val dirLen =
            fname.length // ideally, name should be normalized. Without access to nio.Paths library, it's hard to do it really correctly. This is still a better approximation than nothing.
        val remaining = pathMax - dirLen
        nameMax = min(remaining, nameMax)
        Assert.that(
            nameMax > 0,
            "The media directory is maximally long. There is no more length available for file name."
        )
        if (fname.length > nameMax) {
            val lastSlash = fname.indexOf("/")
            val lastDot = fname.indexOf(".")
            if (lastDot == -1 || lastDot < lastSlash) {
                // no dot, or before last slash
                fname = fname.substring(0, nameMax)
            } else {
                val ext = fname.substring(lastDot + 1)
                var head = fname.substring(0, lastDot)
                val headMax = nameMax - ext.length
                head = head.substring(0, headMax)
                fname = head + ext
                Assert.that(
                    fname.length <= nameMax,
                    "The length of the file is greater than the maximal name value."
                )
            }
        }
        return fname
    }
    /*
      Tracking changes
      ***********************************************************
     */
    /**
     * Scan the media directory if it's changed, and note any changes.
     */
    fun findChanges() {
        findChanges(false)
    }

    /**
     * @param force Unconditionally scan the media directory for changes (i.e., ignore differences in recorded and current
     * directory mod times). Use this when rebuilding the media database.
     */
    open fun findChanges(force: Boolean) {
        if (force || _changed() != null) {
            _logChanges()
        }
    }

    fun haveDirty(): Boolean = db!!.queryScalar("select 1 from media where dirty=1 limit 1") > 0

    /**
     * Returns the number of seconds from epoch since the last modification to the file in path. Important: this method
     * does not automatically append the root media directory to the path; the FULL path of the file must be specified.
     *
     * @param path The path to the file we are checking. path can be a file or a directory.
     * @return The number of seconds (rounded down).
     */
    private fun _mtime(path: String): Long = File(path).lastModified() / 1000

    private fun _checksum(path: String): String = Utils.fileChecksum(path)

    /**
     * Return dir mtime if it has changed since the last findChanges()
     * Doesn't track edits, but user can add or remove a file to update
     *
     * @return The modification time of the media directory if it has changed since the last call of findChanges(). If
     * it hasn't, it returns null.
     */
    fun _changed(): Long? {
        val mod = db!!.queryLongScalar("select dirMod from meta")
        val mtime = _mtime(dir())
        return if (mod != 0L && mod == mtime) {
            null
        } else {
            mtime
        }
    }

    @KotlinCleanup("destructure directly val (added, removed) = _changes()")
    private fun _logChanges() {
        val result = _changes()
        val added = result.first
        val removed = result.second
        val media = ArrayList<Array<Any?>>(added.size + removed.size)
        for (f in added) {
            val path = File(dir(), f).absolutePath
            val mt = _mtime(path)
            media.add(arrayOf(f, _checksum(path), mt, 1))
        }
        for (f in removed) {
            media.add(arrayOf(f, null, 0, 1))
        }
        // update media db
        db!!.apply {
            executeMany("insert or replace into media values (?,?,?,?)", media)
            execute("update meta set dirMod = ?", _mtime(dir()))
            commit()
        }
    }

    private fun _changes(): Pair<List<String>, List<String>> {
        val cache: MutableMap<String, Array<Any>> = HashMapInit(
            db!!.queryScalar("SELECT count() FROM media WHERE csum IS NOT NULL")
        )
        try {
            db!!.query("select fname, csum, mtime from media where csum is not null").use { cur ->
                while (cur.moveToNext()) {
                    val name = cur.getString(0)
                    val csum = cur.getString(1)
                    val mod = cur.getLong(2)
                    cache[name] = arrayOf(csum, mod, false)
                }
            }
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
        val added: MutableList<String> = ArrayList()
        val removed: MutableList<String> = ArrayList()
        // loop through on-disk files
        for (f in File(dir()).listFiles()!!) {
            // ignore directories and thumbs.db
            if (f.isDirectory) {
                continue
            }
            val fname = f.name
            if ("thumbs.db".equals(fname, ignoreCase = true)) {
                continue
            }
            // and files with invalid chars
            if (hasIllegal(fname)) {
                continue
            }
            // empty files are invalid; clean them up and continue
            val sz = f.length()
            if (sz == 0L) {
                f.delete()
                continue
            }
            if (sz > 100 * 1024 * 1024) {
                col.log("ignoring file over 100MB", f)
                continue
            }
            // check encoding
            val normf = Utils.nfcNormalized(fname)
            if (fname != normf) {
                // wrong filename encoding which will cause sync errors
                val nf = File(dir(), normf)
                if (nf.exists()) {
                    f.delete()
                } else {
                    f.renameTo(nf)
                }
            }
            // newly added?
            if (!cache.containsKey(fname)) {
                added.add(fname)
            } else {
                // modified since last time?
                if (_mtime(f.absolutePath) != cache[fname]!![1] as Long) {
                    // and has different checksum?
                    if (_checksum(f.absolutePath) != cache[fname]!![0]) {
                        added.add(fname)
                    }
                }
                // mark as used
                cache[fname]!![2] = true
            }
        }
        // look for any entries in the cache that no longer exist on disk
        for ((key, value) in cache) {
            if (!(value[2] as Boolean)) {
                removed.add(key)
            }
        }
        return Pair(added, removed)
    }

    /**
     * Syncing related
     * ***********************************************************
     */
    fun lastUsn(): Int = db!!.queryScalar("select lastUsn from meta")

    fun setLastUsn(usn: Int) {
        db!!.execute("update meta set lastUsn = ?", usn)
        db!!.commit()
    }

    fun syncInfo(fname: String?): Pair<String?, Int> {
        db!!.query("select csum, dirty from media where fname=?", fname!!).use { cur ->
            return if (cur.moveToNext()) {
                val csum = cur.getString(0)
                val dirty = cur.getInt(1)
                Pair(csum, dirty)
            } else {
                Pair(null, 0)
            }
        }
    }

    fun markClean(fnames: List<String?>) {
        for (fname in fnames) {
            db!!.execute("update media set dirty=0 where fname=?", fname!!)
        }
    }

    fun syncDelete(fname: String) {
        val f = File(dir(), fname)
        if (f.exists()) {
            f.delete()
        }
        db!!.execute("delete from media where fname=?", fname)
    }

    fun mediacount(): Int = db!!.queryScalar("select count() from media where csum is not null")

    fun dirtyCount(): Int = db!!.queryScalar("select count() from media where dirty=1")

    open fun forceResync() {
        db!!.apply {
            execute("delete from media")
            execute("update meta set lastUsn=0,dirMod=0")
            execute("vacuum")
            execute("analyze")
            commit()
        }
    }
    /*
     * Media syncing: zips
     * ***********************************************************
     */
    /**
     * Unlike python, our temp zip file will be on disk instead of in memory. This avoids storing
     * potentially large files in memory which is not feasible with Android's limited heap space.
     *
     *
     * Notes:
     *
     *
     * - The maximum size of the changes zip is decided by the constant SYNC_ZIP_SIZE. If a media file exceeds this
     * limit, only that file (in full) will be zipped to be sent to the server.
     *
     *
     * - This method will be repeatedly called from MediaSyncer until there are no more files (marked "dirty" in the DB)
     * to send.
     *
     *
     * - Since AnkiDroid avoids scanning the media directory on every sync, it is possible for a file to be marked as a
     * new addition but actually have been deleted (e.g., with a file manager). In this case we skip over the file
     * and mark it as removed in the database. (This behaviour differs from the desktop client).
     *
     *
     */
    fun mediaChangesZip(): Pair<File, List<String>> {
        val f = File(col.path.replaceFirst("collection\\.anki2$".toRegex(), "tmpSyncToServer.zip"))
        val fnames: MutableList<String> = ArrayList()
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(f))).use { z ->
                db!!.query(
                    "select fname, csum from media where dirty=1 limit " + Consts.SYNC_MAX_FILES
                ).use { cur ->
                    z.setMethod(ZipOutputStream.DEFLATED)

                    // meta is a list of (fname, zipName), where zipName of null is a deleted file
                    // NOTE: In python, meta is a list of tuples that then gets serialized into json and added
                    // to the zip as a string. In our version, we use JSON objects from the start to avoid the
                    // serialization step. Instead of a list of tuples, we use JSONArrays of JSONArrays.
                    val meta = JSONArray()
                    var sz = 0
                    val buffer = ByteArray(2048)
                    var c = 0
                    while (cur.moveToNext()) {
                        val fname = cur.getString(0)
                        val csum = cur.getString(1)
                        fnames.add(fname)
                        val normName = Utils.nfcNormalized(fname)
                        if (!csum.isNullOrEmpty()) {
                            try {
                                col.log("+media zip $fname")
                                val file = File(dir(), fname)
                                val bis = BufferedInputStream(FileInputStream(file), 2048)
                                z.putNextEntry(ZipEntry(Integer.toString(c)))
                                @KotlinCleanup("improve")
                                var count: Int
                                while (bis.read(buffer, 0, 2048).also { count = it } != -1) {
                                    z.write(buffer, 0, count)
                                }
                                z.closeEntry()
                                bis.close()
                                meta.put(JSONArray().put(normName).put(Integer.toString(c)))
                                sz += file.length().toInt()
                            } catch (e: FileNotFoundException) {
                                Timber.w(e)
                                // A file has been marked as added but no longer exists in the media directory.
                                // Skip over it and mark it as removed in the db.
                                removeFile(fname)
                            }
                        } else {
                            col.log("-media zip $fname")
                            meta.put(JSONArray().put(normName).put(""))
                        }
                        if (sz >= Consts.SYNC_MAX_BYTES) {
                            break
                        }
                        c++
                    }
                    z.putNextEntry(ZipEntry("_meta"))
                    z.write(Utils.jsonToString(meta).toByteArray())
                    z.closeEntry()
                    // Don't leave lingering temp files if the VM terminates.
                    f.deleteOnExit()
                    return Pair(f, fnames)
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to create media changes zip: ")
            throw RuntimeException(e)
        }
    }

    /**
     * Extract zip data; return the number of files extracted. Unlike the python version, this method consumes a
     * ZipFile stored on disk instead of a String buffer. Holding the entire downloaded data in memory is not feasible
     * since some devices can have very limited heap space.
     *
     * This method closes the file before it returns.
     */
    @Throws(IOException::class)
    fun addFilesFromZip(z: ZipFile): Int {
        return try {
            // get meta info first
            val meta =
                JSONObject(Utils.convertStreamToString(z.getInputStream(z.getEntry("_meta"))))
            // then loop through all files
            var cnt = 0
            val zipEntries = Collections.list(z.entries())
            val media: MutableList<Array<Any>> = ArrayList(zipEntries.size)
            for (i in zipEntries) {
                val fileName = i.name
                if ("_meta" == fileName) {
                    // ignore previously-retrieved meta
                    continue
                }
                var name = meta.getString(fileName)
                // normalize name for platform
                name = Utils.nfcNormalized(name)
                // save file
                val destPath = dir() + File.separator + name
                z.getInputStream(i)
                    .use { zipInputStream -> Utils.writeToFile(zipInputStream, destPath) }
                val csum = Utils.fileChecksum(destPath)
                // update db
                media.add(arrayOf(name, csum, _mtime(destPath), 0))
                cnt += 1
            }
            if (!media.isEmpty()) {
                db!!.executeMany("insert or replace into media values (?,?,?,?)", media)
            }
            cnt
        } finally {
            z.close()
        }
    }
    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */
    /**
     * Add an entry into the media database for file named fname, or update it
     * if it already exists.
     */
    open fun markFileAdd(fname: String) {
        Timber.d("Marking media file addition in media db: %s", fname)
        val path = File(dir(), fname).absolutePath
        db!!.execute(
            "insert or replace into media values (?,?,?,?)",
            fname,
            _checksum(path),
            _mtime(path),
            1
        )
    }

    /**
     * Remove a file from the media directory if it exists and mark it as removed in the media database.
     */
    open fun removeFile(fname: String) {
        val f = File(dir(), fname)
        if (f.exists()) {
            f.delete()
        }
        Timber.d("Marking media file removal in media db: %s", fname)
        db!!.execute(
            "insert or replace into media values (?,?,?,?)",
            fname,
            null,
            0,
            1
        )
    }

    /**
     * @return True if the media db has not been populated yet.
     */
    fun needScan(): Boolean {
        val mod = db!!.queryLongScalar("select dirMod from meta")
        return mod == 0L
    }

    @Throws(IOException::class)
    open fun rebuildIfInvalid() {
        try {
            _changed()
            return
        } catch (e: Exception) {
            if (!ExceptionUtil.containsMessage(e, "no such table: meta")) {
                throw e
            }
            CrashReportService.sendExceptionReport(e, "media::rebuildIfInvalid")

            // TODO: We don't know the root cause of the missing meta table
            Timber.w(e, "Error accessing media database. Rebuilding")
            // continue below
        }

        // Delete and recreate the file
        db!!.database.close()
        val path = db!!.path
        Timber.i("Deleted %s", path)
        File(path).delete()
        db = DB.withAndroidFramework(col.context, path)
        _initDB()
    }

    companion object {
        // Upstream illegal chars defined on disallowed_char()
        // in https://github.com/ankitects/anki/blob/main/rslib/src/media/files.rs
        private val fIllegalCharReg = Pattern.compile("[\\[\\]><:\"/?*^\\\\|\\x00\\r\\n]")
        private val fRemotePattern = Pattern.compile("(https?|ftp)://")
        /*
     * A note about the regular expressions below: the python code uses named groups for the image and sound patterns.
     * Our version of Java doesn't support named groups, so we must use indexes instead. In the expressions below, the
     * group names (e.g., ?P<fname>) have been stripped and a comment placed above indicating the index of the group
     * name in the original. Refer to these indexes whenever the python code makes use of a named group.
     */
        /**
         * Group 1 = Contents of [sound:] tag
         * Group 2 = "fname"
         */
        // Regexes defined on https://github.com/ankitects/anki/blob/b403f20cae8fcdd7c3ff4c8d21766998e8efaba0/pylib/anki/media.py#L34-L45
        private val fSoundRegexps = Pattern.compile("(?i)(\\[sound:([^]]+)])")
        // src element quoted case
        /**
         * Group 1 = Contents of `<img>|<audio>` tag
         * Group 2 = "str"
         * Group 3 = "fname"
         * Group 4 = Backreference to "str" (i.e., same type of quote character)  */
        private val fImgAudioRegExpQ =
            Pattern.compile("(?i)(<(?:img|audio)\\b[^>]* src=([\"'])([^>]+?)(\\2)[^>]*>)")
        private val fObjectRegExpQ =
            Pattern.compile("(?i)(<object\\b[^>]* data=([\"'])([^>]+?)(\\2)[^>]*>)")
        // unquoted case
        /**
         * Group 1 = Contents of `<img>|<audio>` tag
         * Group 2 = "fname"
         */
        private val fImgAudioRegExpU =
            Pattern.compile("(?i)(<(?:img|audio)\\b[^>]* src=(?!['\"])([^ >]+)[^>]*?>)")
        private val fObjectRegExpU =
            Pattern.compile("(?i)(<object\\b[^>]* data=(?!['\"])([^ >]+)[^>]*?>)")
        val REGEXPS = listOf(
            fSoundRegexps,
            fImgAudioRegExpQ,
            fImgAudioRegExpU,
            fObjectRegExpQ,
            fObjectRegExpU
        )

        fun getCollectionMediaPath(collectionPath: String): String {
            return collectionPath.replaceFirst("\\.anki2$".toRegex(), ".media")
        }

        /**
         * Percent-escape UTF-8 characters in local image filenames.
         * @param string The string to search for image references and escape the filenames.
         * @return The string with the filenames of any local images percent-escaped as UTF-8.
         */
        @KotlinCleanup("fix 'string' as var")
        fun escapeImages(string: String, unescape: Boolean = false): String {
            @Suppress("NAME_SHADOWING")
            var string = string
            for (p in listOf(fImgAudioRegExpQ, fImgAudioRegExpU)) {
                val m = p.matcher(string)
                // NOTE: python uses the named group 'fname'. Java doesn't have named groups, so we have to determine
                // the index based on which pattern we are using
                val fnameIdx = if (p == fImgAudioRegExpU) 2 else 3
                while (m.find()) {
                    val tag = m.group(0)!!
                    val fname = m.group(fnameIdx)!!
                    if (fRemotePattern.matcher(fname).find()) {
                        // don't do any escaping if remote image
                    } else {
                        string = if (unescape) {
                            string.replace(tag, tag.replace(fname, Uri.decode(fname)))
                        } else {
                            string.replace(tag, tag.replace(fname, Uri.encode(fname, "/")))
                        }
                    }
                }
            }
            return string
        }

        /**
         * Used by other classes to determine the index of a regular expression group named "fname"
         * (Anki2Importer needs this). This is needed because we didn't implement the "transformNames"
         * function and have delegated its job to the caller of this class.
         */
        fun indexOfFname(p: Pattern): Int {
            return if (p == fSoundRegexps) 2 else if (p == fImgAudioRegExpU) 2 else 3
        }
    }

    init {
        if (server) {
            mDir = null
        } else {
            // media directory
            mDir = getCollectionMediaPath(col.path)
            val fd = File(mDir!!)
            if (!fd.exists()) {
                if (!fd.mkdir()) {
                    Timber.e("Cannot create media directory: %s", mDir)
                }
            }
        }
    }
}

data class MediaCheckResult(
    val missingFileNames: List<String>,
    val unusedFileNames: List<String>,
    val invalidFileNames: List<String>
)
