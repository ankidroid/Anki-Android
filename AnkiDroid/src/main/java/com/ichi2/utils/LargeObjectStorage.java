/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

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

package com.ichi2.utils;

import android.content.Context;
import android.os.Bundle;

import com.ichi2.compat.CompatHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import androidx.annotation.Nullable;
import timber.log.Timber;

/**
 * Class to allow the transfer of large data between activities (currently) using a static cache file.
 *
 * This is due to the data limit on a Bundle (TransactionTooLargeException) after ~500KB+ of data.
 *
 * Using a single file pre data item means we will not excessively use cached data.
 */
public class LargeObjectStorage {
    private final Context mContext;

    public LargeObjectStorage(Context context) {
        mContext = context;
    }

    public <T extends Serializable> void storeSingleInstance(StorageData<T> data, Bundle bundle) throws IOException {
        Timber.i("Setting '%s'", data.getBundleKey());
        bundle.putString(data.getBundleKey(), "1");
        writeFile(data);
    }

    private <T extends Serializable> void writeFile(StorageData<T> data) throws IOException {
        ByteArrayInputStream inputStream = null;
        try (ByteArrayOutputStream source = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(source)) {

            objectOutputStream.writeObject(data.getData());
            objectOutputStream.flush();
            objectOutputStream.close();

            inputStream = new ByteArrayInputStream(source.toByteArray());
            File outputFile = new File(mContext.getCacheDir(), data.getFileName() + "." + data.getExtension());
            CompatHelper.getCompat().copyFile(inputStream, outputFile.getAbsolutePath());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    //ignore
                }
            }
        }
    }


    @Nullable
    public <T extends Serializable> T getSingleInstance(StorageKey<T> key, Bundle bundle) {
        if (bundle.getString(key.getBundleKey(), null) == null) {
            Timber.i("No data in bundle for key %s", key.getBundleKey());
            return null;
        }

        File outputFile = new File(mContext.getCacheDir(), key.getFileName() + "." + key.getExtension());

        try (
            FileInputStream fis = new FileInputStream(outputFile.getAbsolutePath());
            ObjectInputStream ois = new ObjectInputStream(fis)) {
            //noinspection unchecked
            return (T) ois.readObject();
        } catch (Exception e) {
            Timber.e(e, "Error deserializing field");
            return null;
        }
    }


    public <T extends Serializable> boolean hasKey(StorageKey<T> storageCurrentField, Bundle bundle) {
        return bundle != null && bundle.containsKey(storageCurrentField.getBundleKey());
    }


    public static class StorageData<T extends Serializable> {
        private final StorageKey<T> mKey;
        private final T mData;

        public StorageData(StorageKey<T> storageKey, T data) {
            this.mKey = storageKey;
            this.mData = data;
        }

        public String getBundleKey() {
            return mKey.getBundleKey();
        }

        public T getData() {
            return mData;
        }

        public String getFileName() {
            return mKey.mFileName;
        }

        public String getExtension() {
            return mKey.mExtension;
        }
    }

    public static class StorageKey<T extends Serializable> {
        private final String mBundleKey;
        private final String mFileName;
        private final String mExtension;


        public StorageKey(String mBundleKey, String mFileName, String mExtension) {
            this.mBundleKey = mBundleKey;
            this.mFileName = mFileName;
            this.mExtension = mExtension;
        }


        public StorageData<T> asData(T data) {
            return new StorageData<>(this, data);
        }


        public String getFileName() {
            return mFileName;
        }

        public String getExtension() {
            return mExtension;
        }


        public String getBundleKey() {
            return mBundleKey;
        }
    }
}
