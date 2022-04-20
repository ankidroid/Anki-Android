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

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showSimpleSnackbar
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.dialogs.ExportCompleteDialog.ExportCompleteDialogListener
import com.ichi2.anki.dialogs.ExportDialog.ExportDialogListener
import com.ichi2.async.CollectionTask.ExportApkg
import com.ichi2.async.TaskManager
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Collection
import com.ichi2.libanki.utils.TimeUtils
import com.ichi2.utils.KotlinCleanup
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

    @KotlinCleanup("make msg non-null")
    fun showExportDialog(msg: String?) {
        activity.showDialogFragment(mDialogsFactory.newExportDialog().withArguments(msg!!))
    }

    @KotlinCleanup("make msg non-null")
    fun showExportDialog(msg: String?, did: Long) {
        activity.showDialogFragment(mDialogsFactory.newExportDialog().withArguments(msg!!, did))
    }

    override fun exportApkg(path: String?, did: Long?, includeSched: Boolean, includeMedia: Boolean) {
        val exportDir = File(activity.externalCacheDir, "export")
        exportDir.mkdirs()
        val exportPath: File
        val timeStampSuffix = "-" + TimeUtils.getTimestamp(collectionSupplier.get().time)
        exportPath = if (path != null) {
            // filename has been explicitly specified
            File(exportDir, path)
        } else if (did != null) {
            // filename not explicitly specified, but a deck has been specified so use deck name
            File(exportDir, collectionSupplier.get().decks.get(did).getString("name").replace("\\W+".toRegex(), "_") + timeStampSuffix + ".apkg")
        } else if (!includeSched) {
            // full export without scheduling is assumed to be shared with someone else -- use "All Decks.apkg"
            File(exportDir, "All Decks$timeStampSuffix.apkg")
        } else {
            // full collection export -- use "collection.colpkg"
            val colPath = File(collectionSupplier.get().path)
            val newFileName = colPath.name.replace(".anki2", "$timeStampSuffix.colpkg")
            File(exportDir, newFileName)
        }
        val exportListener = ExportListener(activity, mDialogsFactory)
        TaskManager.launchCollectionTask(ExportApkg(exportPath.path, did, includeSched, includeMedia), exportListener)
    }

    override fun dismissAllDialogFragments() {
        activity.dismissAllDialogFragments()
    }

    @KotlinCleanup("make path non-null")
    @SuppressLint("StringFormatInvalid")
    override fun emailFile(path: String?) {
        // Make sure the file actually exists
        val attachment = File(path!!)
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
        val shareIntent = IntentBuilder(activity)
            .setType("application/apkg")
            .setStream(uri)
            .setSubject(activity.getString(R.string.export_email_subject, attachment.name))
            .setHtmlText(activity.getString(R.string.export_email_text, activity.getString(R.string.link_manual), activity.getString(R.string.link_distributions)))
            .intent
        if (shareIntent.resolveActivity(activity.packageManager) != null) {
            activity.startActivityWithoutAnimation(shareIntent)
        } else {
            // Try to save it?
            showSimpleSnackbar(activity, R.string.export_send_no_handlers, false)
            saveExportFile(path)
        }
    }

    @KotlinCleanup("make exportPath non-null")
    override fun saveExportFile(exportPath: String?) {
        // Make sure the file actually exists
        val attachment = File(exportPath!!)
        if (!attachment.exists()) {
            Timber.e("saveExportFile() Specified apkg file %s does not exist", exportPath)
            showSimpleSnackbar(activity, R.string.export_save_apkg_unsuccessful, false)
            return
        }

        // Send the user to the standard Android file picker via Intent
        mExportFileName = exportPath
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = "application/apkg"
        saveIntent.putExtra(Intent.EXTRA_TITLE, attachment.name)
        saveIntent.putExtra("android.content.extra.SHOW_ADVANCED", true)
        saveIntent.putExtra("android.content.extra.FANCY", true)
        saveIntent.putExtra("android.content.extra.SHOW_FILESIZE", true)
        mSaveFileLauncher.launch(saveIntent)
    }

    private fun saveFileCallback(result: ActivityResult) {
        val isSuccessful = exportToProvider(result.data, true)
        @StringRes val message = if (isSuccessful) R.string.export_save_apkg_successful else R.string.export_save_apkg_unsuccessful
        showSimpleSnackbar(activity, activity.getString(message), isSuccessful)
    }

    @KotlinCleanup("make intent non-null")
    private fun exportToProvider(intent: Intent?, deleteAfterExport: Boolean): Boolean {
        if (intent == null || intent.data == null) {
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
        ) { result: ActivityResult -> saveFileCallback(result) }
    }
}
