/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.export

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import anki.generic.Empty
import anki.import_export.ExportLimit
import anki.import_export.exportLimit
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.dialogs.ExportCompleteDialog.ExportCompleteDialogListener
import com.ichi2.anki.dialogs.ExportDialog.ExportDialogListener
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.AnkiPackageExporter
import com.ichi2.libanki.Collection
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.libanki.utils.TimeUtils
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.function.Supplier

/**
 * A delegate class used in any [AnkiActivity] where the exporting feature is required.
 *
 * Must be constructed before calling {@link AnkiActivity#onCreate(Bundle, PersistableBundle)}, this is to fragment
 * factory {@link #mDialogsFactory} is set correctly.
 *
 * @param activity the calling activity (must implement {@link ExportCompleteDialogListener})
 * @param collectionSupplier a predicate that supplies a collection instance
*/
class ActivityExportingDelegate(private val activity: AnkiActivity, private val collectionSupplier: Supplier<Collection>) : ExportDialogListener, ExportCompleteDialogListener {
    private val mDialogsFactory: ExportDialogsFactory
    private val mSaveFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var mExportFileName: String

    fun showExportDialog(msg: String) {
        activity.showDialogFragment(mDialogsFactory.newExportDialog().withArguments(msg))
    }

    fun showExportDialog(msg: String, did: DeckId) {
        activity.showDialogFragment(mDialogsFactory.newExportDialog().withArguments(msg, did))
    }

    /**
     * Show the export dialog in the Browser to export selected cards or notes
     * @param msg the message to show in the dialog
     * @param ids the selected card/note ids
     * @param isCardList true if the ids are card ids, false if they are note ids
     */
    fun showExportDialog(msg: String, ids: List<Long>, isCardList: Boolean) {
        activity.showDialogFragment(mDialogsFactory.newExportDialog().withArguments(msg, ids, isCardList))
    }

    private fun getTimeStampSuffix() =
        "-" + run {
            collectionSupplier.get()
            TimeUtils.getTimestamp(TimeManager.time)
        }

    private fun getColpkgExportName(exportDir: File): File {
        // full collection export -- use "collection.colpkg"
        val colPath = File(collectionSupplier.get().path)
        val newFileName = colPath.name.replace(".anki2", "${getTimeStampSuffix()}.colpkg")
        return File(exportDir, newFileName)
    }

    private fun getExportFileName(path: String?, prefix: String, includeSched: Boolean): File {
        val exportDir = File(activity.externalCacheDir, "export")
        exportDir.mkdirs()

        return if (path != null) {
            File(exportDir, path)
        } else if (prefix == "All Decks" && includeSched) { // full collection export as .colpkg
            getColpkgExportName(exportDir)
        } else {
            File(exportDir, "$prefix${getTimeStampSuffix()}.apkg")
        }
    }

    override fun exportColAsApkgOrColpkg(path: String?, includeSched: Boolean, includeMedia: Boolean) {
        val exportPath = getExportFileName(path, "All Decks", includeSched)

        if (BackendFactory.defaultLegacySchema) {
            exportApkgOrColpkgLegacy(exportPath, null, includeSched, includeMedia)
        } else {
            if (includeSched) {
                activity.launchCatchingTask {
                    activity.exportColpkg(exportPath.path, includeMedia)
                    val dialog = mDialogsFactory.newExportCompleteDialog().withArguments(exportPath.path)
                    activity.showAsyncDialogFragment(dialog)
                }
            } else {
                val limit = exportLimit { this.wholeCollection = Empty.getDefaultInstance() }
                exportNewBackendApkg(exportPath, false, includeMedia, limit)
            }
        }
    }

    override fun exportDeckAsApkg(path: String?, did: DeckId, includeSched: Boolean, includeMedia: Boolean) {
        val deckName = collectionSupplier.get().decks.name(did)
        val exportPath = getExportFileName(path, deckName, includeSched)

        if (BackendFactory.defaultLegacySchema) {
            exportApkgOrColpkgLegacy(exportPath, did, includeSched, includeMedia)
        } else {
            val limit = exportLimit { this.deckId = did }
            exportNewBackendApkg(exportPath, includeSched, includeMedia, limit)
        }
    }

    /**
     * Export selected cards or notes using the new backend
     * TODO: Once new backend is default, exportColAsApkg and exportDeckAsApkg can be merged into this function
     */
    override fun exportSelectedAsApkg(path: String?, limit: ExportLimit, includeSched: Boolean, includeMedia: Boolean) {
        val prefix = if (limit.hasCardIds()) "Cards" else "Notes"
        val exportPath = getExportFileName(path, prefix, includeSched)
        exportNewBackendApkg(exportPath, includeSched, includeMedia, limit)
    }

    private fun exportApkgOrColpkgLegacy(exportPath: File, did: DeckId?, includeSched: Boolean, includeMedia: Boolean) {
        activity.launchCatchingTask {
            val exportPkgPath = exportPath.path
            activity.withProgress(activity.resources.getString(R.string.export_in_progress)) {
                withCol {
                    val exporter = if (did == null) {
                        AnkiPackageExporter(this, includeSched, includeMedia)
                    } else {
                        AnkiPackageExporter(this, did, includeSched, includeMedia)
                    }
                    exporter.exportInto(exportPkgPath, context)
                }
            }
            val dialog = mDialogsFactory.newExportCompleteDialog().withArguments(exportPkgPath)
            activity.showAsyncDialogFragment(dialog)
        }
    }

    // Only for new backend schema
    private fun exportNewBackendApkg(exportPath: File, includeSched: Boolean, includeMedia: Boolean, limit: ExportLimit) {
        activity.launchCatchingTask {
            activity.exportApkg(exportPath.path, includeSched, includeMedia, limit)
            val dialog = mDialogsFactory.newExportCompleteDialog().withArguments(exportPath.path)
            activity.showAsyncDialogFragment(dialog)
        }
    }

    override fun dismissAllDialogFragments() {
        activity.dismissAllDialogFragments()
    }

    override fun shareFile(path: String) {
        // Make sure the file actually exists
        val attachment = File(path)
        if (!attachment.exists()) {
            Timber.e("Specified apkg file %s does not exist", path)
            showThemedToast(activity, activity.resources.getString(R.string.apk_share_error), false)
            return
        }
        // Get a URI for the file to be shared via the FileProvider API
        val uri: Uri = try {
            FileProvider.getUriForFile(activity, "com.ichi2.anki.apkgfileprovider", attachment)
        } catch (e: IllegalArgumentException) {
            Timber.e("Could not generate a valid URI for the apkg file")
            showThemedToast(activity, activity.resources.getString(R.string.apk_share_error), false)
            return
        }
        val sendIntent = ShareCompat.IntentBuilder(activity)
            .setType("application/apkg")
            .setStream(uri)
            .setSubject(activity.getString(R.string.export_email_subject, attachment.name))
            .setHtmlText(
                activity.getString(
                    R.string.export_email_text,
                    activity.getString(R.string.link_manual),
                    activity.getString(R.string.link_distributions),
                )
            )
            .intent.apply {
                clipData = ClipData.newUri(activity.contentResolver, attachment.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        val shareFileIntent = Intent.createChooser(
            sendIntent,
            activity.getString(R.string.export_share_title)
        )
        if (shareFileIntent.resolveActivity(activity.packageManager) != null) {
            activity.startActivityWithoutAnimation(shareFileIntent)
            // TODO: find if there is a way to check whether the activity successfully shared the collection.
            saveSuccessfulCollectionExportIfRelevant()
        } else {
            // Try to save it?
            activity.showSnackbar(R.string.export_send_no_handlers)
            saveExportFile(path)
        }
    }

    override fun saveExportFile(exportPath: String) {
        // Make sure the file actually exists
        val attachment = File(exportPath)
        if (!attachment.exists()) {
            Timber.e("saveExportFile() Specified apkg file %s does not exist", exportPath)
            activity.showSnackbar(R.string.export_save_apkg_unsuccessful)
            return
        }

        // Send the user to the standard Android file picker via Intent
        mExportFileName = exportPath
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/apkg"
            putExtra(Intent.EXTRA_TITLE, attachment.name)
            putExtra("android.content.extra.SHOW_ADVANCED", true)
            putExtra("android.content.extra.FANCY", true)
            putExtra("android.content.extra.SHOW_FILESIZE", true)
        }
        mSaveFileLauncher.launch(saveIntent)
    }

    private fun saveFileCallback(result: ActivityResult) {
        val isSuccessful = exportToProvider(result.data!!)

        if (isSuccessful) {
            activity.showSnackbar(R.string.export_save_apkg_successful, Snackbar.LENGTH_SHORT)
            saveSuccessfulCollectionExportIfRelevant()
        } else {
            activity.showSnackbar(R.string.export_save_apkg_unsuccessful)
        }
    }

    private fun exportToProvider(intent: Intent, deleteAfterExport: Boolean = true): Boolean {
        if (intent.data == null) {
            Timber.e("exportToProvider() provided with insufficient intent data %s", intent)
            return false
        }
        val uri = intent.data
        Timber.d("Exporting from file to ContentProvider URI: %s/%s", mExportFileName, uri.toString())
        val fileOutputStream: FileOutputStream
        val pfd: ParcelFileDescriptor?
        try {
            pfd = activity.contentResolver.openFileDescriptor(uri!!, "w")
            if (pfd != null) {
                fileOutputStream = FileOutputStream(pfd.fileDescriptor)
                CompatHelper.compat.copyFile(mExportFileName, fileOutputStream)
                fileOutputStream.close()
                pfd.close()
            } else {
                Timber.w("exportToProvider() failed - ContentProvider returned null file descriptor for %s", uri)
                return false
            }
            if (deleteAfterExport && !File(mExportFileName).delete()) {
                Timber.w("Failed to delete temporary export file %s", mExportFileName)
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to export file to Uri: %s/%s", mExportFileName, uri.toString())
            return false
        }
        return true
    }

    init {
        val fragmentManager = activity.supportFragmentManager
        mDialogsFactory = ExportDialogsFactory(this, this).attachToActivity(activity)
        fragmentManager.fragmentFactory = mDialogsFactory
        mSaveFileLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                saveFileCallback(result)
            } else {
                Timber.i("The file selection for the exported collection was cancelled")
            }
        }
    }

    /**
     * If we exported a collection (hence [mExportFileName] ends with ".colpkg"), save in the preferences
     * the mod of the collection and the time at which it occurred.
     * This will allow to check whether a recent export was made, hence scoped storage migration is safe.
     */
    private fun saveSuccessfulCollectionExportIfRelevant() {
        if (::mExportFileName.isInitialized && !mExportFileName.endsWith(".colpkg")) return
        AnkiDroidApp.getSharedPrefs(activity).edit {
            putLong(
                LAST_SUCCESSFUL_EXPORT_AT_SECOND_KEY, TimeManager.time.intTime()
            )
        }
        val col = collectionSupplier.get()
        AnkiDroidApp.getSharedPrefs(activity).edit {
            putLong(
                LAST_SUCCESSFUL_EXPORT_AT_MOD_KEY, col.mod
            )
        }
    }
}

const val LAST_SUCCESSFUL_EXPORT_AT_MOD_KEY = "last_successful_export_mod"
const val LAST_SUCCESSFUL_EXPORT_AT_SECOND_KEY = "last_successful_export_second"
