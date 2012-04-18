/****************************************************************************************
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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
package com.ichi2.libanki.sync;

import android.util.Pair;

import com.ichi2.anki2.R;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Collection;

import org.json.JSONArray;

import java.io.File;
import java.util.List;

public class MediaSyncer {
    Collection mCol;
    RemoteMediaServer mServer;
    
    public MediaSyncer(Collection col, RemoteMediaServer server) {
        mCol = col;
        mServer = server;
    }
    
    public String sync(long mediaUsn, Connection con) {
        // step 1: check if there have been any changes
        con.publishProgress(R.string.sync_media_find);
        mCol.getMedia().findChanges();
        long lUsn = mCol.getMedia().usn();
        if (lUsn == mediaUsn && !mCol.getMedia().hasChanged()) {
            return "noChanges";
        }
        
        // step 2: send/recv deletions
        con.publishProgress(R.string.sync_media_remove);
        List<String> lRem = removed();
        JSONArray rRem = mServer.remove(lRem, lUsn);
        remove(rRem);
        
        // step 3: stream files from server
        con.publishProgress(R.string.sync_media_from_server);
        while (true) {
            long usn = mCol.getMedia().usn();
            File zip = mServer.files(usn, mCol.getPath().replaceFirst("collection\\.anki2$", "tmpSyncFromServer.zip"));
            if (zip == null) {
                break;
            }
            // The temporary file gets deleted in addFiles
            if (addFiles(zip)) {
                break;
            }
        }
        
        // step 4: stream files to the server
        con.publishProgress(R.string.sync_media_to_server);
        while (true) {
            Pair<File, List<String>> zipAdded = files();
            if (zipAdded.second == null || zipAdded.second.size() == 0) {
                zipAdded.first.delete();
                // finished
                break;
            }
            long usn = mServer.addFiles(zipAdded.first);
            // after server has replied, safe to remove from log
            zipAdded.first.delete();  // remove the temporary file created by Media.zipAdded
            mCol.getMedia().forgetAdded(zipAdded.second);
            mCol.getMedia().setUsn(usn);
        }
        
        // step 5: sanity check during beta testing
        long sMediaSanity = mServer.mediaSanity();
        long cMediaSanity = mediaSanity();
        if (sMediaSanity != cMediaSanity) {
            throw new RuntimeException("Media sanity check failed. Please copy and paste the text below:\n" +
                    cMediaSanity + "\n" + sMediaSanity);
        }
        return "success";
    }
    
    private List<String> removed() {
        return mCol.getMedia().removed();
    }
    
    private void remove(JSONArray fnames) {
        mCol.getMedia().syncRemove(fnames);
    }
    
    private Pair<File, List<String>> files() {
        return mCol.getMedia().zipAdded();
    }
    
    /**
     * Adds any media sent from the server.
     * @param zip A temporary zip file that contains the media files. 
     * @return True if zip is the last in set. Server returns new usn instead.
     */
    private boolean addFiles(File zip) {
        boolean result = mCol.getMedia().syncAdd(zip);
        zip.delete();
        return result;
    }
    
    private long mediaSanity() {
        return mCol.getMedia().sanityCheck();
    }
}
