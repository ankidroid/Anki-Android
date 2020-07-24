package com.ichi2.async.task;

import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.AnkiPackageExporter;
import com.ichi2.libanki.Collection;
import com.ichi2.utils.JSONException;

import java.io.FileNotFoundException;
import java.io.IOException;

import timber.log.Timber;

public class ExportApkg extends Task {
    private final String mApkgPath;
    private final long mDid;
    private final boolean mIncludeSched;
    private final boolean mIncludeMedia;

    public ExportApkg(String apkgPath, long did,
                      boolean includeSChed, boolean includeMedia) {
        mApkgPath = apkgPath;
        mDid = did;
        mIncludeSched = includeSChed;
        mIncludeMedia = includeMedia;
    }

    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        Timber.d("doInBackgroundExportApkg");
        try {
            AnkiPackageExporter exporter = new AnkiPackageExporter(col);
            exporter.setIncludeSched(mIncludeSched);
            exporter.setIncludeMedia(mIncludeMedia);
            exporter.setDid(mDid);
            exporter.exportInto(mApkgPath, task.getContext());
        } catch (FileNotFoundException e) {
            Timber.e(e, "FileNotFoundException in doInBackgroundExportApkg");
            return new TaskData(false);
        } catch (IOException e) {
            Timber.e(e, "IOException in doInBackgroundExportApkg");
            return new TaskData(false);
        } catch (JSONException e) {
            Timber.e(e, "JSOnException in doInBackgroundExportApkg");
            return new TaskData(false);
        } catch (ImportExportException e) {
            Timber.e(e, "ImportExportException in doInBackgroundExportApkg");
            return new TaskData(e.getMessage(), true);
        }
        return new TaskData(mApkgPath);
    }
}
