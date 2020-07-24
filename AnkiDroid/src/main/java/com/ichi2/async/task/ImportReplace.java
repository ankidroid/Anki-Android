package com.ichi2.async.task;

import android.content.res.Resources;
import android.util.JsonReader;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.Utils;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

public class ImportReplace extends Task {
    private final String mPath;

    public ImportReplace(String path) {
        mPath = path;
    }

    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundImportReplace");
        Collection col = task.getCol();
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();

        // extract the deck from the zip file
        String colPath = col.getPath();
        File dir = new File(new File(colPath).getParentFile(), "tmpzip");
        if (dir.exists()) {
            BackupManager.removeDir(dir);
        }

        // from anki2.py
        String colname = "collection.anki21";
        ZipFile zip;
        try {
            zip = new ZipFile(new File(mPath));
        } catch (IOException e) {
            Timber.e(e, "doInBackgroundImportReplace - Error while unzipping");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace0");
            return new TaskData(false);
        }
        try {
            // v2 scheduler?
            if (zip.getEntry(colname) == null) {
                colname = CollectionHelper.COLLECTION_FILENAME;
            }
            Utils.unzipFiles(zip, dir.getAbsolutePath(), new String[] { colname, "media" }, null);
        } catch (IOException e) {
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace - unzip");
            return new TaskData(-2, null, false);
        }
        String colFile = new File(dir, colname).getAbsolutePath();
        if (!(new File(colFile)).exists()) {
            return new TaskData(-2, null, false);
        }

        Collection tmpCol = null;
        try {
            tmpCol = Storage.Collection(task.getContext(), colFile);
            if (!tmpCol.validCollection()) {
                tmpCol.close();
                return new TaskData(-2, null, false);
            }
        } catch (Exception e) {
            Timber.e("Error opening new collection file... probably it's invalid");
            try {
                tmpCol.close();
            } catch (Exception e2) {
                // do nothing
            }
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace - open col");
            return new TaskData(-2, null, false);
        } finally {
            if (tmpCol != null) {
                tmpCol.close();
            }
        }

        task.doProgress(new TaskData(res.getString(R.string.importing_collection)));
        if (col != null) {
            // unload collection and trigger a backup
            CollectionHelper.getInstance().closeCollection(true, "Importing new collection");
            CollectionHelper.getInstance().lockCollection();
            BackupManager.performBackupInBackground(colPath, true);
        }
        // overwrite collection
        File f = new File(colFile);
        if (!f.renameTo(new File(colPath))) {
            // Exit early if this didn't work
            return new TaskData(-2, null, false);
        }
        int addedCount = -1;
        try {
            CollectionHelper.getInstance().unlockCollection();

            // because users don't have a backup of media, it's safer to import new
            // data and rely on them running a media db check to get rid of any
            // unwanted media. in the future we might also want to duplicate this step
            // import media
            HashMap<String, String> nameToNum = new HashMap<>();
            HashMap<String, String> numToName = new HashMap<>();
            File mediaMapFile = new File(dir.getAbsolutePath(), "media");
            if (mediaMapFile.exists()) {
                JsonReader jr = new JsonReader(new FileReader(mediaMapFile));
                jr.beginObject();
                String name;
                String num;
                while (jr.hasNext()) {
                    num = jr.nextName();
                    name = jr.nextString();
                    nameToNum.put(name, num);
                    numToName.put(num, name);
                }
                jr.endObject();
                jr.close();
            }
            String mediaDir = col.getMedia().dir();
            int total = nameToNum.size();
            int i = 0;
            for (Map.Entry<String, String> entry : nameToNum.entrySet()) {
                String file = entry.getKey();
                String c = entry.getValue();
                File of = new File(mediaDir, file);
                if (!of.exists()) {
                    Utils.unzipFiles(zip, mediaDir, new String[] { c }, numToName);
                }
                ++i;
                task.doProgress(new TaskData(res.getString(R.string.import_media_count, (i + 1) * 100 / total)));
            }
            zip.close();
            // delete tmp dir
            BackupManager.removeDir(dir);
            return new TaskData(true);
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundImportReplace - RuntimeException");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace1");
            return new TaskData(false);
        } catch (FileNotFoundException e) {
            Timber.e(e, "doInBackgroundImportReplace - FileNotFoundException");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace2");
            return new TaskData(false);
        } catch (IOException e) {
            Timber.e(e, "doInBackgroundImportReplace - IOException");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace3");
            return new TaskData(false);
        }
    }
}
