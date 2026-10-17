// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.libanki.CardTemplate
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.utils.ext.readJson
import com.ichi2.anki.utils.ext.writeJson
import timber.log.Timber
import java.io.File
import java.io.IOException

/** A wrapper for a notetype in JSON format with helpers for editing the notetype. */
class CardTemplateNotetype(
    val notetype: NotetypeJson,
) {
    enum class ChangeType {
        ADD,
        DELETE,
    }

    data class TemplateChange(
        var ordinal: Int,
        val type: ChangeType,
    ) : java.io.Serializable

    private val changes = CardTemplateChanges()

    var templateChanges: ArrayList<TemplateChange>
        get() = changes.templateChanges
        private set(value) {
            changes.templateChanges = value
        }

    fun toBundle(): Bundle =
        Bundle().apply {
            putString(INTENT_MODEL_FILENAME, saveTempNoteType(appContext, notetype))
            putSerializable("mTemplateChanges", templateChanges)
        }

    private fun loadTemplateChanges(bundle: Bundle) {
        try {
            templateChanges = bundle.getSerializableCompat("mTemplateChanges")!!
        } catch (e: ClassCastException) {
            Timber.e(e, "Unexpected cast failure")
        }
    }

    fun getTemplate(ord: Int): CardTemplate {
        Timber.d("getTemplate() on ordinal %s", ord)
        return notetype.templates[ord]
    }

    val templateCount: Int
        get() = notetype.templates.length()

    val noteTypeId: NoteTypeId
        get() = notetype.id

    var css: String
        get() = notetype.css
        set(value) {
            notetype.css = value
        }

    fun updateTemplate(
        ordinal: Int,
        template: CardTemplate,
    ) {
        notetype.templates[ordinal] = template
    }

    fun addNewTemplate(newTemplate: CardTemplate) {
        Timber.d("addNewTemplate()")
        addTemplateChange(ChangeType.ADD, newTemplate.ord)
    }

    fun removeTemplate(ord: Int) {
        Timber.d("removeTemplate() on ordinal %s", ord)
        addTemplateChange(ChangeType.DELETE, ord)
    }

    suspend fun saveToDatabase() {
        Timber.d("saveToDatabase() called")
        changes.dumpChanges()
        clearTempNoteTypeFiles()
        saveNoteType(notetype, adjustedTemplateChanges)
    }

    /**
     * Handles everything for a note type change at once - template add / deletes as well as content updates
     */
    suspend fun saveNoteType(
        notetype: NotetypeJson,
        templateChanges: ArrayList<TemplateChange>,
    ) {
        Timber.d("saveNoteType")
        val oldNoteType = withCol { notetypes.get(notetype.id) }

        val newTemplates = notetype.templates
        for (change in templateChanges) {
            val oldTemplates = oldNoteType!!.templates
            when (change.type) {
                ChangeType.ADD -> {
                    Timber.d("saveNoteType() adding template %s", change.ordinal)
                    withCol { notetypes.addTemplate(oldNoteType, newTemplates[change.ordinal]) }
                }
                ChangeType.DELETE -> {
                    Timber.d("saveNoteType() deleting template currently at ordinal %s", change.ordinal)
                    withCol { notetypes.removeTemplate(oldNoteType, oldTemplates[change.ordinal]) }
                }
            }
        }

        // required for Rust: the modified time can't go backwards, and we updated the note type by adding fields
        // This could be done better
        notetype.mod = oldNoteType!!.mod
        undoableOp {
            notetypes.updateDict(notetype)
        }
    }

    fun addTemplateChange(
        type: ChangeType,
        ordinal: Int,
    ) = changes.addTemplateChange(type, ordinal)

    fun getDeleteDbOrds(ord: Int): IntArray = changes.getDeleteDbOrds(ord)

    val adjustedTemplateChanges: ArrayList<TemplateChange>
        get() = changes.adjustedTemplateChanges

    companion object {
        const val INTENT_MODEL_FILENAME = "editedNoteTypeFilename"

        /**
         * Load the TemporaryNoteType from the filename included in a Bundle
         *
         * @param bundle a Bundle that should contain persisted JSON under INTENT_MODEL_FILENAME key
         * @return re-hydrated TemporaryNoteType or null if there was a problem, null means should reload from database
         */
        fun fromBundle(bundle: Bundle): CardTemplateNotetype? {
            val editedNoteTypeFileName = bundle.getString(INTENT_MODEL_FILENAME)
            // Bundle.getString is @Nullable, so we have to check.
            if (editedNoteTypeFileName == null) {
                Timber.d("fromBundle() - note type file name under key %s", INTENT_MODEL_FILENAME)
                return null
            }
            Timber.d("onCreate() loading saved note type file %s", editedNoteTypeFileName)
            val tempNotetypeJSON: NotetypeJson =
                try {
                    getTempNoteType(editedNoteTypeFileName)
                } catch (e: IOException) {
                    Timber.w(e, "Unable to load saved note type file")
                    return null
                }
            return CardTemplateNotetype(tempNotetypeJSON).apply {
                loadTemplateChanges(bundle)
            }
        }

        /**
         * Save the current note type to a temp file in the application internal cache directory
         * @return String representing the absolute path of the saved file, or null if there was a problem
         */
        fun saveTempNoteType(
            context: Context,
            tempNoteType: NotetypeJson,
        ): String? {
            Timber.d("saveTempNoteType() saving tempNoteType")
            var tempNoteTypeFile: File
            try {
                tempNoteTypeFile = File.createTempFile("editedTemplate", ".json", context.cacheDir)
                tempNoteTypeFile.writeJson(tempNoteType.jsonObject)
            } catch (ioe: IOException) {
                Timber.e(ioe, "Unable to create+write temp file for note type")
                return null
            }
            return tempNoteTypeFile.absolutePath
        }

        /**
         * Get the note type temporarily saved into the file represented by the given path
         * @return JSONObject holding the note type, or null if there was a problem
         */
        @Throws(IOException::class)
        fun getTempNoteType(tempNoteTypeFileName: String): NotetypeJson {
            Timber.d("getTempNoteType() fetching tempNoteType %s", tempNoteTypeFileName)
            try {
                return NotetypeJson(File(tempNoteTypeFileName).readJson())
            } catch (e: IOException) {
                Timber.e(e, "Unable to read+parse tempNoteType from file %s", tempNoteTypeFileName)
                throw e
            }
        }

        /** Clear any temp note type files saved into internal cache directory  */
        fun clearTempNoteTypeFiles(): Int {
            var deleteCount = 0
            for (c in appContext.cacheDir.listFiles() ?: arrayOf()) {
                val absolutePath = c.absolutePath
                if (absolutePath.contains("editedTemplate") && absolutePath.endsWith("json")) {
                    if (!c.delete()) {
                        Timber.w("Unable to delete temp file %s", c.absolutePath)
                    } else {
                        deleteCount++
                        Timber.d("Deleted temp note type file %s", c.absolutePath)
                    }
                }
            }
            return deleteCount
        }

        fun isOrdinalPendingAdd(
            noteType: CardTemplateNotetype,
            ord: Int,
        ): Boolean = CardTemplateChanges.isOrdinalPendingAdd(noteType.changes, ord)

        fun getAdjustedAddOrdinalAtChangeIndex(
            noteType: CardTemplateNotetype,
            changesIndex: Int,
        ): Int = CardTemplateChanges.getAdjustedAddOrdinalAtChangeIndex(noteType.changes, changesIndex)
    }
}

/**
 * Represents a note type.
 */
sealed class NoteTypeProvider : Parcelable {
    abstract fun getNoteType(col: Collection): NotetypeJson

    /**
     * Returns whether we can get a note type from this provider.
     */
    abstract fun isUsable(): Boolean

    companion object CREATOR : Parcelable.Creator<NoteTypeProvider> {
        override fun createFromParcel(source: Parcel): NoteTypeProvider {
            val providerType = source.readInt()
            return when (providerType) {
                Id.PROVIDER_TYPE -> Id(source.readLong())
                File.PROVIDER_TYPE -> File(NotetypeFile.CREATOR.createFromParcel(source))
                else -> throw IllegalArgumentException("Unknown NoteTypeProvider type: $providerType")
            }
        }

        override fun newArray(size: Int): Array<NoteTypeProvider?> = arrayOfNulls(size)
    }

    /**
     * A note type from the collection. We assume it's actually currently in the collection.
     */
    data class Id(
        val id: NoteTypeId,
    ) : NoteTypeProvider() {
        companion object {
            const val PROVIDER_TYPE = 0
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(
            dest: Parcel,
            flags: Int,
        ) {
            dest.writeInt(PROVIDER_TYPE)
            dest.writeLong(id)
        }

        override fun getNoteType(col: Collection): NotetypeJson = col.notetypes.get(id)!!

        override fun isUsable(): Boolean = true
    }

    data class File(
        val file: NotetypeFile,
    ) : NoteTypeProvider() {
        companion object {
            const val PROVIDER_TYPE = 1
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(
            dest: Parcel,
            flags: Int,
        ) {
            dest.writeInt(PROVIDER_TYPE)
            file.writeToParcel(dest, flags)
        }

        override fun getNoteType(col: Collection): NotetypeJson = file.getNotetype()

        override fun isUsable(): Boolean = file.isUsable()
    }
}

/**
 * Temporary file containing a [NotetypeJson]
 *
 * Useful for adding a [NotetypeJson] into a [Bundle], like when using [Intent.putExtra]
 * for sending an object to another activity.
 *
 * The notetype is written into a file because there is a
 * [limit of 1MB](https://developer.android.com/reference/android/os/TransactionTooLargeException.html)
 * for [Bundle] transactions, and notetypes can be bigger than that (#5600).
 */
class NotetypeFile(
    path: String,
) : File(path),
    Parcelable {
    /**
     * A cache containing the note type if it can be known othewise the reason it can't.
     */
    sealed class Cache {
        class Exc(
            val e: Exception,
        ) : Cache()

        class NT(
            val nt: NotetypeJson,
        ) : Cache()
    }

    private var cache: Cache? = null

    /**
     * @param directory where the file will be saved
     * @param notetype to be stored
     */
    constructor(directory: File, notetype: NotetypeJson) : this(createTempFile("notetype", ".tmp", directory).absolutePath) {
        cache = Cache.NT(notetype)
        try {
            writeJson(notetype.jsonObject)
        } catch (ioe: IOException) {
            Timber.w(ioe, "Unable to create+write temp file for note type")
        }
    }

    /**
     * @param context for getting the cache directory
     * @param notetype to be stored
     */
    constructor(context: Context, notetype: NotetypeJson) : this(context.cacheDir, notetype)

    /**
     * Gets the note type. This potentially read from file so should be used on IO thread ideally.
     */
    fun getNotetype(): NotetypeJson =
        when (val cache = fillCacheIfNeeded()) {
            is Cache.Exc -> throw cache.e
            is Cache.NT -> cache.nt
        }

    /**
     * Load the note type from file.
     */
    private fun loadNotetype(): NotetypeJson =
        try {
            NotetypeJson(readJson())
        } catch (e: IOException) {
            Timber.w(e, "Unable to read+parse tempNoteType from file %s", absolutePath)
            throw e
        }

    /**
     * Refresh the cache from file. Returns its new value.
     */
    private fun fillCache(): Cache {
        val cache =
            try {
                val nt = loadNotetype()
                Cache.NT(nt)
            } catch (e: Exception) {
                Cache.Exc(e)
            }
        this.cache = cache
        return cache
    }

    /**
     * Fill the cache from file if it's not yet filled.
     */
    private fun fillCacheIfNeeded(): Cache = cache ?: fillCache()

    /**
     * Whether we can get a note type from this object. Returns false if the file was deleted.
     * Loads the note type in cache to avoid rereading it.
     */
    fun isUsable(): Boolean = fillCacheIfNeeded() is Cache.NT

    override fun describeContents(): Int = 0

    override fun writeToParcel(
        dest: Parcel,
        flags: Int,
    ) {
        dest.writeString(path)
    }

    fun toProvider(): NoteTypeProvider = NoteTypeProvider.File(this)

    companion object {
        @JvmField
        @Suppress("unused")
        val CREATOR =
            object : Parcelable.Creator<NotetypeFile> {
                override fun createFromParcel(source: Parcel): NotetypeFile = NotetypeFile(source.readString()!!)

                override fun newArray(size: Int): Array<NotetypeFile> = arrayOf()
            }
    }
}
