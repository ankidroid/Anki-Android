/****************************************************************************************
* Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
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

package com.ichi2.anki;

import java.io.File;
import java.io.FileNotFoundException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

/**
 * Content Provider for AnkiDroid
 */
public class AnkiDroidContentProvider extends ContentProvider {
	
	/**
	 * Tag for logging messages
	 */
	private static final String TAG = "AnkiDroid";


	/**
	 * Opens the file located on uri, with permissions mode and retrieves a ParcelFileDescriptor representing it
	 * @param uri URI where the file is located
	 * @param mode Permissions which the file will be opened with
	 * @return ParcelFileDescriptor of the file located on uri
	 */
	@Override
	public ParcelFileDescriptor openFile( Uri uri, String mode ){
		Log.i(TAG, "AnkiDroidContentProvider - openFile = " + uri.getEncodedPath() + ", " + uri.getPath());
		Log.i(TAG, "Open mode = " + mode);
		
		File file = null;
		ParcelFileDescriptor parcel = null;

		try {
			file = new File(uri.getPath());
			parcel = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
			Log.i(TAG, "AnkiDroidContentProvider - File opened succesfully");
		} catch (FileNotFoundException e)
		{
			Log.e( TAG, "Error finding: " + uri.getPath() + "\n" + e.toString() );
		} catch (Exception e)
		{
			Log.e(TAG, "Could not open the file = " + uri.getPath() + ", " + e.getMessage());
		}
	
		return parcel;
	}
   

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public int delete(Uri uri, String s, String[] as) {
		throw new UnsupportedOperationException("Not supported by this provider");
	}

	@Override
	public String getType(Uri uri) {
		throw new UnsupportedOperationException("Not supported by this provider");
	}

	@Override
	public Uri insert(Uri uri, ContentValues contentvalues) {
		throw new UnsupportedOperationException("Not supported by this provider");
	}

	@Override
	public Cursor query(Uri uri, String[] as, String s, String[] as1, String s1) {
		throw new UnsupportedOperationException("Not supported by this provider");
	}

	@Override
	public int update(Uri uri, ContentValues contentvalues, String s, String[] as) {
		throw new UnsupportedOperationException("Not supported by this provider");
	}

}
