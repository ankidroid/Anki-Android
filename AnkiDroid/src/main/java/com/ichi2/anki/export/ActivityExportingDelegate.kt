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
package com.ichi2.anki.export;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.dialogs.ExportCompleteDialog.ExportCompleteDialogListener;
import com.ichi2.anki.dialogs.ExportDialog.ExportDialogListener;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskManager;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.utils.TimeUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.function.Supplier;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentManager;
import timber.log.Timber;

/**
 * A delegate class used in any {@link AnkiActivity} where the exporting feature is required.
 */
public class ActivityExportingDelegate implements ExportDialogListener, ExportCompleteDialogListener {


    private final AnkiActivity mActivity;
    private final Supplier<Collection> mCollectionSupplier;
    private final ExportDialogsFactory mDialogsFactory;
    private final ActivityResultLauncher<Intent> mSaveFileLauncher;

    private String mExportFileName;

    /**
     * Must be constructed before calling {@link AnkiActivity#onCreate(Bundle, PersistableBundle)}, this is to fragment
     * factory {@link #mDialogsFactory} is set correctly.
     *
     * @param activity the calling activity (must implement {@link ExportCompleteDialogListener})
     * @param collectionSupplier a predicate that supplies a collection instance
     */
    public ActivityExportingDelegate(AnkiActivity activity, Supplier<Collection> collectionSupplier) {
        mActivity = activity;
        this.mCollectionSupplier = collectionSupplier;
        final FragmentManager fragmentManager = activity.getSupportFragmentManager();
        mDialogsFactory = new ExportDialogsFactory(this, this).attachToActivity(activity);
        fragmentManager.setFragmentFactory(mDialogsFactory);
        mSaveFileLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::saveFileCallback
        );
    }


    public void showExportDialog(String msg) {
        mActivity.showDialogFragment(mDialogsFactory.newExportDialog().withArguments(msg));
    }


    public void showExportDialog(String msg, long did) {
        mActivity.showDialogFragment(mDialogsFactory.newExportDialog().withArguments(msg, did));
    }

    @Override
    public void exportApkg(String filename, Long did, boolean includeSched, boolean includeMedia) {
        File exportDir = new File(mActivity.getExternalCacheDir(), "export");
        exportDir.mkdirs();
        File exportPath;
        String timeStampSuffix = "-" + TimeUtils.getTimestamp(mCollectionSupplier.get().getTime());
        if (filename != null) {
            // filename has been explicitly specified
            exportPath = new File(exportDir, filename);
        } else if (did != null) {
            // filename not explicitly specified, but a deck has been specified so use deck name
            exportPath = new File(exportDir, mCollectionSupplier.get().getDecks().get(did).getString("name").replaceAll("\\W+", "_") + timeStampSuffix + ".apkg");
        } else if (!includeSched) {
            // full export without scheduling is assumed to be shared with someone else -- use "All Decks.apkg"
            exportPath = new File(exportDir, "All Decks" + timeStampSuffix + ".apkg");
        } else {
            // full collection export -- use "collection.colpkg"
            File colPath = new File(mCollectionSupplier.get().getPath());
            String newFileName = colPath.getName().replace(".anki2", timeStampSuffix + ".colpkg");
            exportPath = new File(exportDir, newFileName);
        }
        final ExportListener exportListener = new ExportListener(mActivity, mDialogsFactory);
        TaskManager.launchCollectionTask(new CollectionTask.ExportApkg(exportPath.getPath(), did, includeSched, includeMedia), exportListener);
    }


    @Override
    public void dismissAllDialogFragments() {
        mActivity.dismissAllDialogFragments();
    }


    @Override
    public void emailFile(String path) {
        // Make sure the file actually exists
        File attachment = new File(path);
        if (!attachment.exists()) {
            Timber.e("Specified apkg file %s does not exist", path);
            UIUtils.showThemedToast(mActivity, mActivity.getResources().getString(R.string.apk_share_error), false);
            return;
        }
        // Get a URI for the file to be shared via the FileProvider API
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(mActivity, "com.ichi2.anki.apkgfileprovider", attachment);
        } catch (IllegalArgumentException e) {
            Timber.e("Could not generate a valid URI for the apkg file");
            UIUtils.showThemedToast(mActivity, mActivity.getResources().getString(R.string.apk_share_error), false);
            return;
        }
        Intent shareIntent = new ShareCompat.IntentBuilder(mActivity)
                .setType("application/apkg")
                .setStream(uri)
                .setSubject(mActivity.getString(R.string.export_email_subject, attachment.getName()))
                .setHtmlText(mActivity.getString(R.string.export_email_text_new, mActivity.getString(R.string.link_manual), mActivity.getString(R.string.link_distributions)))
                .getIntent();
        if (shareIntent.resolveActivity(mActivity.getPackageManager()) != null) {
            mActivity.startActivityWithoutAnimation(shareIntent);
        } else {
            // Try to save it?
            UIUtils.showSimpleSnackbar(mActivity, R.string.export_send_no_handlers, false);
            saveExportFile(path);
        }
    }


    @Override
    public void saveExportFile(String path) {
        // Make sure the file actually exists
        File attachment = new File(path);
        if (!attachment.exists()) {
            Timber.e("saveExportFile() Specified apkg file %s does not exist", path);
            UIUtils.showSimpleSnackbar(mActivity, R.string.export_save_apkg_unsuccessful, false);
            return;
        }

        // Send the user to the standard Android file picker via Intent
        mExportFileName = path;
        Intent saveIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        saveIntent.addCategory(Intent.CATEGORY_OPENABLE);
        saveIntent.setType("application/apkg");
        saveIntent.putExtra(Intent.EXTRA_TITLE, attachment.getName());
        saveIntent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        saveIntent.putExtra("android.content.extra.FANCY", true);
        saveIntent.putExtra("android.content.extra.SHOW_FILESIZE", true);
        mSaveFileLauncher.launch(saveIntent);
    }


    private void saveFileCallback(ActivityResult result) {
        boolean isSuccessful = exportToProvider(result.getData(), true);
        @StringRes int message = (isSuccessful) ?
                R.string.export_save_apkg_successful :
                R.string.export_save_apkg_unsuccessful;
        UIUtils.showSimpleSnackbar(mActivity, mActivity.getString(message), isSuccessful);
    }

    private boolean exportToProvider(Intent intent, boolean deleteAfterExport) {
        if ((intent == null) || (intent.getData() == null)) {
            Timber.e("exportToProvider() provided with insufficient intent data %s", intent);
            return false;
        }
        Uri uri = intent.getData();
        Timber.d("Exporting from file to ContentProvider URI: %s/%s", mExportFileName, uri.toString());
        FileOutputStream fileOutputStream;
        ParcelFileDescriptor pfd;
        try {
            pfd = mActivity.getContentResolver().openFileDescriptor(uri, "w");

            if (pfd != null) {
                fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
                CompatHelper.getCompat().copyFile(mExportFileName, fileOutputStream);
                fileOutputStream.close();
                pfd.close();
            } else {
                Timber.w("exportToProvider() failed - ContentProvider returned null file descriptor for %s", uri);
                return false;
            }
            if (deleteAfterExport && !new File(mExportFileName).delete()) {
                Timber.w("Failed to delete temporary export file %s", mExportFileName);
            }
        } catch (Exception e) {
            Timber.e(e, "Unable to export file to Uri: %s/%s", mExportFileName, uri.toString());
            return false;
        }
        return true;
    }
}
