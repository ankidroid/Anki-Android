package com.ichi2.anki;


import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Pair;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.dialogs.ExportCompleteDialog;
import com.ichi2.anki.dialogs.ExportCompleteDialog.ExportCompleteDialogListener;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskListenerWithContext;
import com.ichi2.async.TaskManager;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.utils.TimeUtils;
import com.ichi2.themes.StyledProgressDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;
import timber.log.Timber;

/**
 * A delegate class used in any {@link AnkiActivity} where the exporting feature is required.
 * <p>
 * The calling activity must implement {@link ExportCompleteDialogListener} and can then forward any call the the exporting delegate
 */
public class ActivityExportingDelegate <A extends AnkiActivity & ExportCompleteDialogListener> {

    private final A mActivity;
    private final int mPickExportFileCallbackCode;

    private String mExportFileName;


    /**
     * @param activity the calling activity (must implement {@link ExportCompleteDialogListener})
     * @param mPickExportFileCallbackCode the code that will be used on onActivityResult
     */
    public ActivityExportingDelegate(A activity, int mPickExportFileCallbackCode) {
        mActivity = activity;
        this.mPickExportFileCallbackCode = mPickExportFileCallbackCode;
    }


    public void exportApkg(String filename, Long did, List<Long> cardIds, boolean includeSched, boolean includeMedia) {
        File exportDir = new File(mActivity.getExternalCacheDir(), "export");
        exportDir.mkdirs();
        File exportPath;
        String timeStampSuffix = "-" + TimeUtils.getTimestamp(getCol().getTime());
        if (filename != null) {
            // filename has been explicitly specified
            exportPath = new File(exportDir, filename);
        } else if (!includeSched) {
            // full export without scheduling is assumed to be shared with someone else -- use "All Decks.apkg"
            exportPath = new File(exportDir, "All Decks" + timeStampSuffix + ".apkg");
        } else if (did != null && cardIds == null) {
            // filename not explicitly specified, but a deck has been specified so use deck name
            exportPath = new File(exportDir, getCol().getDecks().get(did).getString("name").replaceAll("\\W+", "_") + timeStampSuffix + ".apkg");
        } else if (did == null && cardIds != null) {
            exportPath = new File(exportDir, "Selected Cards" + timeStampSuffix + ".apkg");
        } else {
            // full collection export -- use "collection.colpkg"
            File colPath = new File(getCol().getPath());
            String newFileName = colPath.getName().replace(".anki2", timeStampSuffix + ".colpkg");
            exportPath = new File(exportDir, newFileName);
        }
        final ExportListener exportListener = new ExportListener(mActivity);
        TaskManager.launchCollectionTask(new CollectionTask.ExportApkg(exportPath.getPath(), did, cardIds, includeSched, includeMedia), exportListener);
    }


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
                .setHtmlText(mActivity.getString(R.string.export_email_text))
                .getIntent();
        if (shareIntent.resolveActivity(mActivity.getPackageManager()) != null) {
            mActivity.startActivityWithoutAnimation(shareIntent);
        } else {
            // Try to save it?
            UIUtils.showSimpleSnackbar(mActivity, R.string.export_send_no_handlers, false);
            saveExportFile(path);
        }
    }


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
        mActivity.startActivityForResultWithoutAnimation(saveIntent, mPickExportFileCallbackCode);
    }


    public boolean exportToProvider(Intent intent, boolean deleteAfterExport) {
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


    private Collection getCol() {
        return CollectionHelper.getInstance().getCol(mActivity);
    }


    private static class ExportListener extends TaskListenerWithContext<AnkiActivity, Void, Pair<Boolean, String>> {
        private MaterialDialog mProgressDialog;


        public ExportListener(AnkiActivity activity) {
            super(activity);
        }


        @Override
        public void actualOnPreExecute(@NonNull AnkiActivity activity) {
            mProgressDialog = StyledProgressDialog.show(activity, "",
                    activity.getResources().getString(R.string.export_in_progress), false);
        }


        @Override
        public void actualOnPostExecute(@NonNull AnkiActivity activity, Pair<Boolean, String> result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }

            // If boolean and string are both set, we are signalling an error message
            // instead of a successful result.
            if (result.first && result.second != null) {
                Timber.w("Export Failed: %s", result.second);
                activity.showSimpleMessageDialog(result.second);
            } else {
                Timber.i("Export successful");
                String exportPath = result.second;
                if (exportPath != null) {
                    activity.showAsyncDialogFragment(ExportCompleteDialog.newInstance(exportPath));
                } else {
                    UIUtils.showThemedToast(activity, activity.getResources().getString(R.string.export_unsuccessful), true);
                }
            }
        }
    }
}
