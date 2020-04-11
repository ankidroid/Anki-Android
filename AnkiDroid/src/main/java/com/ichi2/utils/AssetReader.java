package com.ichi2.utils;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AssetReader {
    private final Context mContext;

    public AssetReader(Context context) {
        this.mContext = context;
    }

    public String loadAsUtf8String(String assetName) throws IOException {
        InputStream is = null;
        byte[] fs;
        try {
            is = mContext.getAssets().open(assetName);
            fs = readFile(is);
        } finally {
            if(is != null) {
                is.close();
            }
        }

        return new String(fs, "UTF-8");
    }


    private byte[] readFile(InputStream inputStream) throws IOException {

        ByteArrayOutputStream buffer = null;
        try {
            buffer = new ByteArrayOutputStream();

            int nRead;
            byte[] data = new byte[16384];

            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            return buffer.toByteArray();
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (buffer != null) {
                buffer.close();
            }
        }
    }
}
