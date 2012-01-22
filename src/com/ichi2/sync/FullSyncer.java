/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.sync;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpResponse;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.util.Log;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki2.R;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;


public class FullSyncer extends HttpSyncer {

	Collection mCol;

	public FullSyncer(Collection col, String hkey) {
		super(hkey);
		mCol = col;
	} 

	@Override
	public HttpResponse download(Connection connection) {
		HttpResponse ret = super.req("download", new ByteArrayInputStream("{}".toString()
				.getBytes()));
		InputStream cont;
		try {
			cont = ret.getEntity().getContent();
		} catch (IllegalStateException e1) {
			throw new RuntimeException(e1);
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
		String path = mCol.getPath();
		mCol.close();
		mCol = null;
		String tpath = path + ".tmp";
		try {
			Utils.writeToFile(cont, tpath);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		// check the received file is ok
		try {
			AnkiDb d = AnkiDatabaseManager.getDatabase(tpath);
			Cursor cur = null;
			try {
				cur = d.getDatabase().rawQuery("PRAGMA integrity_check", null);
				if (!cur.moveToFirst() || !cur.getString(0).equals("ok")) {
					Log.e(AnkiDroidApp.TAG, "Full sync - downloaded file corrupt");
					return null;
				}
			} finally {
				if (cur != null && !cur.isClosed()) {
					cur.close();
				}
			}
			AnkiDatabaseManager.closeDatabase(tpath);			
		} catch (SQLiteDatabaseCorruptException e) {
			return null;
		}
		connection.publishProgress(R.string.sync_complete_message);
		// overwrite existing collection
		File newFile = new File(tpath);
		if (newFile.renameTo(new File(path))) {
			return ret;
		} else {
			return null;
		}
	}

	@Override
	public HttpResponse upload(Connection connection) {
		// make sure it's ok before we try to upload
		Cursor cur = null;
		try {
			cur = mCol.getDb().getDatabase().rawQuery("PRAGMA integrity_check", null);
			if (!cur.moveToFirst() || !cur.getString(0).equals("ok")) {
				return null;
			}
		} finally {
			if (cur != null && !cur.isClosed()) {
				cur.close();
			}
		}
		// apply some adjustments, then upload
		mCol.beforeUpload();
		String filePath = mCol.getPath();
		mCol.close();
		HttpResponse ret;
		connection.publishProgress(R.string.sync_uploading_message);
		try {
			ret = super.req("upload", new FileInputStream(filePath));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		return ret;
	}
}
