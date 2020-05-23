package com.ichi2.libanki.hooks;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import dalvik.system.DexClassLoader;

public class ExternalHookLoader {   
    // Buffer size for file copying.  While 8kb is used in this sample, you
    // may want to tweak it based on actual size of the secondary dex file involved.
    private static final int BUF_SIZE = 8 * 1024;
    private static final String HOOK_SUB_PATH = "plugins/hooks";


    // File I/O code to copy the secondary dex file from asset resource to internal storage.
    private boolean prepareDex(File dexExternalStoragePath, File dexInternalStoragePath) {
        BufferedInputStream bis = null;
        OutputStream dexWriter = null;

        try {
            bis = new BufferedInputStream(new FileInputStream(dexExternalStoragePath));
            dexWriter = new BufferedOutputStream(new FileOutputStream(dexInternalStoragePath));
            byte[] buf = new byte[BUF_SIZE];
            int len;
            while((len = bis.read(buf, 0, BUF_SIZE)) > 0) {
                dexWriter.write(buf, 0, len);
            }
            dexWriter.close();
            bis.close();
            return true;
        } catch (IOException e) {
            if (dexWriter != null) {
                try {
                    dexWriter.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            return false;
        }
    }
    
    private class PrepareDexTask extends AsyncTask<File, Void, Boolean> {

        @Override
        protected void onCancelled() {
            super.onCancelled();
            //if (mProgressDialog != null) mProgressDialog.cancel();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            //if (mProgressDialog != null) mProgressDialog.cancel();
        }

        @Override
        protected Boolean doInBackground(File... dexStoragePaths) {
            prepareDex(dexStoragePaths[0],dexStoragePaths[1]);
            return null;
        }
    }
}
