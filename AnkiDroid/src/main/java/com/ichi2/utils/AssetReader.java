package com.ichi2.utils;

import android.content.Context;

import com.ichi2.compat.CompatV21;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AssetReader {
    private final Context mContext;

    public AssetReader(Context context) {
        this.mContext = context;
    }

    public String loadAsUtf8String(String assetName) throws IOException {
        try (InputStream is = mContext.getAssets().open(assetName);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            CompatV21.copyFile(is, bos);

            return new String(bos.toByteArray(), "UTF-8");
        }
    }
}
