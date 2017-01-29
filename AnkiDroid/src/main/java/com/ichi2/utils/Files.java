package com.ichi2.utils;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import timber.log.Timber;

public class Files {
    public static boolean copy(File file, File outfile) {
        Timber.d("Copying file %s to %s", file.getAbsolutePath(), outfile.getAbsolutePath());
        if (!file.isFile()) {
            Timber.e("%s is not a file", file.getAbsolutePath());
            return false;
        }
        if (!outfile.isFile()) {
            Timber.e("%s is not a file", outfile.getAbsolutePath());
            return false;
        }

        FileInputStream inStream = null;
        FileOutputStream outStream = null;
        try {
            inStream = new FileInputStream(file);
            outStream = new FileOutputStream(outfile);
            FileChannel inChannel = inStream.getChannel();
            FileChannel outChannel = outStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (Exception e) {
            Timber.e("Exception copying file %s to %s\n %s", file.getAbsolutePath(), outfile.getAbsolutePath(), e.getMessage());
            return false;
        } finally {
            closeQuietly(inStream);
            closeQuietly(outStream);
        }
        return true;
    }

    public static boolean move(File file, File outfile) {
        boolean result = copy(file, outfile);
        file.delete();
        return result;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
            }
        }
    }
}
