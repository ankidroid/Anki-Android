/***************************************************************************************
  * Copyright (c) 2016 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

package com.ichi2.libanki.importer;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.AnkiSerialization;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.Utils;
import com.ichi2.utils.HashUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.zip.ZipFile;

import timber.log.Timber;

@SuppressWarnings({"PMD.NPathComplexity"})
public class AnkiPackageImporter extends Anki2Importer {

    private ZipFile mZip;
    private Map<String, String> mNameToNum;

    public AnkiPackageImporter(Collection col, String file) {
        super(col, file);
    }

    @Override
    public void run() throws ImportExportException {
        publishProgress(0, 0, 0);
        File tempDir = new File(new File(mCol.getPath()).getParent(), "tmpzip");
        Collection tmpCol; //self.col into Anki.
        Timber.d("Attempting to import package %s", mFile);
        try {
            // We extract the zip contents into a temporary directory and do a little more
            // validation than the desktop client to ensure the extracted collection is an apkg.
            String colname = "collection.anki21";
            try {
                // extract the deck from the zip file
                try {
                    mZip = new ZipFile(new File(mFile));
                } catch (FileNotFoundException fileNotFound) {
                    Timber.w(fileNotFound);
                    // The cache can be cleared between copying the file in and importing. This is temporary
                    if (fileNotFound.getMessage().contains("ENOENT")) {
                        mLog.add(getRes().getString(R.string.import_log_file_cache_cleared));
                        return;
                    }
                    throw fileNotFound; //displays: failed to unzip
                }
                // v2 scheduler?
                if (mZip.getEntry(colname) == null) {
                    colname = CollectionHelper.COLLECTION_FILENAME;
                }

                // Make sure we have sufficient free space
                long uncompressedSize = Utils.calculateUncompressedSize(mZip);
                long availableSpace = Utils.determineBytesAvailable(mCol.getPath());
                Timber.d("Total uncompressed size will be: %d", uncompressedSize);
                Timber.d("Total available size is:         %d", availableSpace);
                if (uncompressedSize > availableSpace) {
                    Timber.e("Not enough space to unzip, need %d, available %d", uncompressedSize, availableSpace);
                    mLog.add(getRes().getString(R.string.import_log_insufficient_space, uncompressedSize, availableSpace));
                    return;
                }
                // The filename that we extract should be collection.anki2
                // Importing collection.anki21 fails due to some media regexes expecting collection.anki2.
                // We follow how Anki does it and fix the problem here.
                HashMap<String, String> mediaToFileNameMap = HashUtil.HashMapInit(1);
                mediaToFileNameMap.put(colname, CollectionHelper.COLLECTION_FILENAME);
                Utils.unzipFiles(mZip, tempDir.getAbsolutePath(), new String[]{colname, "media"}, mediaToFileNameMap);
                colname = CollectionHelper.COLLECTION_FILENAME;
            } catch (IOException e) {
                Timber.e(e, "Failed to unzip apkg.");
                AnkiDroidApp.sendExceptionReport(e, "AnkiPackageImporter::run() - unzip");
                mLog.add(getRes().getString(R.string.import_log_failed_unzip, e.getLocalizedMessage()));
                return;
            }
            String colpath = new File(tempDir, colname).getAbsolutePath();
            if (!(new File(colpath)).exists()) {
                mLog.add(getRes().getString(R.string.import_log_failed_copy_to, colpath));
                return;
            }
            tmpCol = Storage.Collection(mContext, colpath);
            try {
                if (!tmpCol.validCollection()) {
                    mLog.add(getRes().getString(R.string.import_log_failed_validate));
                    return;
                }
            } finally {
                if (tmpCol != null) {
                    tmpCol.close();
                }
            }
            mFile = colpath;
            // we need the media dict in advance, and we'll need a map of fname ->
            // number to use during the import
            File mediaMapFile = new File(tempDir, "media");
            mNameToNum = new HashMap<>(); // Number of file in mediamMMapFile as json. Not knowable
            String dirPath = tmpCol.getMedia().dir();
            File dir = new File(dirPath);
            // We need the opposite mapping in AnkiDroid since our extraction method requires it.
            Map<String, String> numToName = new HashMap<>(); // Number of file in mediamMMapFile as json. Not knowable
            try (JsonParser jp = AnkiSerialization.getFactory().createParser(mediaMapFile)) {
                String name; // v in anki
                String num; // k in anki
                if (jp.nextToken() != JsonToken.START_OBJECT) {
                    throw new IllegalStateException("Expected content to be an object");
                }
                while (jp.nextToken() != JsonToken.END_OBJECT) {
                    num = jp.currentName();
                    name = jp.nextTextValue();
                    File file= new File(dir, name);
                    if (!Utils.isInside(file, dir)) {
                        throw (new RuntimeException("Invalid file"));
                    }
                    Utils.nfcNormalized(num);
                    mNameToNum.put(name, num);
                    numToName.put(num, name);
                }
            } catch (FileNotFoundException e) {
                Timber.e("Apkg did not contain a media dict. No media will be imported.");
            } catch (IOException e) {
                Timber.e("Malformed media dict. Media import will be incomplete.");
            }
            // run anki2 importer
            super.run();
            // import static media
            for (Map.Entry<String, String> entry : mNameToNum.entrySet()) {
                String file = entry.getKey();
                String c = entry.getValue();
                if (!file.startsWith("_") && !file.startsWith("latex-")) {
                    continue;
                }
                File path = new File(mCol.getMedia().dir(), Utils.nfcNormalized(file));
                if (!path.exists()) {
                    try {
                        Utils.unzipFiles(mZip, mCol.getMedia().dir(), new String[]{c}, numToName);
                    } catch (IOException e) {
                        Timber.e("Failed to extract static media file. Ignoring.");
                    }
                }
            }
        } finally {
            long availableSpace = Utils.determineBytesAvailable(mCol.getPath());
            Timber.d("Total available size is: %d", availableSpace);

            // Clean up our temporary files
            if (tempDir.exists()) {
                BackupManager.removeDir(tempDir);
            }
        }
        publishProgress(100, 100, 100);
    }

    @Override
    protected BufferedInputStream _srcMediaData(String fname) {
        if (mNameToNum.containsKey(fname)) {
            try {
                return new BufferedInputStream(mZip.getInputStream(mZip.getEntry(mNameToNum.get(fname))));
            } catch (IOException | NullPointerException e) {
                Timber.e("Could not extract media file %s from zip file.", fname);
            }
        }
        return null;
    }
}
