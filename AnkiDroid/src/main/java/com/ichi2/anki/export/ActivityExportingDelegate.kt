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
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.dialogs.ExportReadyDialog.ExportReadyDialogListener
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Collection
import com.ichi2.libanki.utils.TimeManager
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.function.Supplier

/**
 * A delegate class used in any [AnkiActivity] where the exporting feature is required.
 *
 * Must be constructed before calling [AnkiActivity.onCreate(Bundle, PersistableBundle)][AnkiActivity.onCreate],
 * to ensure the fragment factory ([mDialogsFactory]) is set correctly.
 *
 * @param activity the calling activity (must implement [ExportReadyDialogListener])
 * @param collectionSupplier a predicate that supplies a collection instance
*/
class ActivityExportingDelegate(private val activity: AnkiActivity, private val collectionSupplier: Supplier<Collection>) : ExportReadyDialogListener {
    val mDialogsFactory: ExportDialogsFactory
    private val saveFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var fileExportPath: String

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
        val authority = "${activity.packageName}.apkgfileprovider"

        // Get a URI for the file to be shared via the FileProvider API
        val uri: Uri = try {
            FileProvider.getUriForFile(activity, authority, attachment)
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
                    activity.getString(R.string.link_distributions)
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
            activity.startActivity(shareFileIntent)
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

        fileExportPath = exportPath

        // Send the user to the standard Android file picker via Intent
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/apkg"
            putExtra(Intent.EXTRA_TITLE, attachment.name)
            putExtra("android.content.extra.SHOW_ADVANCED", true)
            putExtra("android.content.extra.FANCY", true)
            putExtra("android.content.extra.SHOW_FILESIZE", true)
        }
        saveFileLauncher.launch(saveIntent)
    }

    fun onSaveInstanceState(outState: Bundle) {
        if (::fileExportPath.isInitialized) {
            outState.putString(EXPORT_FILE_NAME_KEY, fileExportPath)
        }
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        val restoredValue = savedInstanceState?.getString(EXPORT_FILE_NAME_KEY) ?: return
        fileExportPath = restoredValue
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
        Timber.d("Exporting from file to ContentProvider URI: %s/%s", fileExportPath, uri.toString())
        try {
            activity.contentResolver.openFileDescriptor(uri!!, "w").use { pfd ->
                if (pfd != null) {
                    FileOutputStream(pfd.fileDescriptor).use { fileOutputStream ->
                        CompatHelper.compat.copyFile(fileExportPath, fileOutputStream)
                    }
                } else {
                    Timber.w(
                        "exportToProvider() failed - ContentProvider returned null file descriptor for %s",
                        uri
                    )
                    return false
                }
            }
            if (deleteAfterExport && !File(fileExportPath).delete()) {
                Timber.w("Failed to delete temporary export file %s", fileExportPath)
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to export file to Uri: %s/%s", fileExportPath, uri.toString())
            return false
        }
        return true
    }

    init {
        val fragmentManager = activity.supportFragmentManager
        mDialogsFactory = ExportDialogsFactory(this).attachToActivity(activity)
        fragmentManager.fragmentFactory = mDialogsFactory
        saveFileLauncher = activity.registerForActivityResult(
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
     * If we exported a collection (hence [fileExportPath] ends with ".colpkg"), save in the preferences
     * the mod of the collection and the time at which it occurred.
     * This will allow to check whether a recent export was made, hence scoped storage migration is safe.
     */
    @NeedsTest("fix crash when sharing")
    private fun saveSuccessfulCollectionExportIfRelevant() {
        if (::fileExportPath.isInitialized && !fileExportPath.endsWith(".colpkg")) return
        activity.sharedPrefs().edit {
            putLong(
                LAST_SUCCESSFUL_EXPORT_AT_SECOND_KEY,
                TimeManager.time.intTime()
            )
        }
        val col = collectionSupplier.get()
        activity.sharedPrefs().edit {
            putLong(
                LAST_SUCCESSFUL_EXPORT_AT_MOD_KEY,
                col.mod
            )
        }
    }
}

private const val EXPORT_FILE_NAME_KEY = "export_file_name_key"
const val LAST_SUCCESSFUL_EXPORT_AT_MOD_KEY = "last_successful_export_mod"
const val LAST_SUCCESSFUL_EXPORT_AT_SECOND_KEY = "last_successful_export_second"
