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


import com.google.gson.stream.JsonReader;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.Utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

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
        try {
            // We extract the zip contents into a temporary directory and do a little more
            // validation than the desktop client to ensure the extracted collection is an apkg.
            String colname = "collection.anki21";
            try {
                // extract the deck from the zip file
                mZip = new ZipFile(new File(mFile), ZipFile.OPEN_READ);
                // v2 scheduler?
                if (mZip.getEntry(colname) == null) {
                    colname = "collection.anki2";
                }
                Utils.unzipFiles(mZip, tempDir.getAbsolutePath(), new String[]{colname, "media"}, null);
            } catch (IOException e) {
                Timber.e(e, "Failed to unzip apkg.");
                mLog.add(getRes().getString(R.string.import_log_no_apkg));
                return;
            }
            String colpath = new File(tempDir, colname).getAbsolutePath();
            if (!(new File(colpath)).exists()) {
                mLog.add(getRes().getString(R.string.import_log_no_apkg));
                return;
            }
            tmpCol = Storage.Collection(mContext, colpath);
            try {
                if (!tmpCol.validCollection()) {
                    mLog.add(getRes().getString(R.string.import_log_no_apkg));
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
            mNameToNum = new HashMap<>();
            String dirPath = tmpCol.getMedia().dir();
            File dir = new File(dirPath);
            // We need the opposite mapping in AnkiDroid since our extraction method requires it.
            Map<String, String> numToName = new HashMap<>();
            try {
                JsonReader jr = new JsonReader(new FileReader(mediaMapFile));
                jr.beginObject();
                String name; // v in anki
                String num; // k in anki
                while (jr.hasNext()) {
                    num = jr.nextName();
                    name = jr.nextString();
                    File file= new File(dir, name);
                    if (!Utils.isInside(file, dir)) {
                        throw (new RuntimeException("Invalid file"));
                    }
                    Utils.nfcNormalized(num);
                    mNameToNum.put(name, num);
                    numToName.put(num, name);
                }
                jr.endObject();
                jr.close();
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
                Timber.e("Could not extract media file " + fname + "from zip file.");
            }
        }
        return null;
    }
}
