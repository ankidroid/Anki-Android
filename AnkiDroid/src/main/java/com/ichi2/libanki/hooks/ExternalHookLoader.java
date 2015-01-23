package com.ichi2.libanki.hooks;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import dalvik.system.DexClassLoader;

public class ExternalHookLoader {   
    // Buffer size for file copying.  While 8kb is used in this sample, you
    // may want to tweak it based on actual size of the secondary dex file involved.
    private static final int BUF_SIZE = 8 * 1024;
    private static final String HOOK_SUB_PATH = "plugins/hooks";
    private Activity mMainActivity;
    private final File mHookFolderPath;
    
    /**
     * Adaption of example from Google on loading custom classes. See original example for more help
     * https://code.google.com/p/android-custom-class-loading-sample/source/browse/#svn%2Ftrunk%2Fandroid-custom-class-loading-sample
     * 
     * @param Activity mainActivity : the activity which is going to load the hook
     * @param String colPath : path to AnkiDroid collection. Hook plugins should go in ~/plugins/hooks as compiled apk or jar packages
     */
    public ExternalHookLoader(Activity mainActivity, String colPath) {
        mMainActivity = mainActivity;
        mHookFolderPath = new File(colPath, HOOK_SUB_PATH);
    }


    /**
     * import a hook class from an external apk or jar package stored in COLLECTION_PATH/plugins/hooks
     * 
     * @param String dexFilename : name of the package file in hooks folder -- e.g. "ChessFilter.jar"
     * @param String className: full name of class to load from package -- e.g. "com.testplugin.ChessFilter"
     */
    @SuppressLint("NewApi")
	public HookPlugin importExternalHook(String dexFilename, String className) {
        // filename of the hook which is currently being loaded
        final File dexExternalStoragePath = new File(mHookFolderPath, dexFilename);
        
        // Before the secondary dex file can be processed by the DexClassLoader,
        // it has to be first copied from asset resource to a storage location.
        final File dexInternalStoragePath = new File(mMainActivity.getDir("dex", Context.MODE_PRIVATE), 
                dexFilename);        
        (new PrepareDexTask()).execute(dexExternalStoragePath,dexInternalStoragePath);
        
        // Internal storage where the DexClassLoader writes the optimized dex file to.
        final File optimizedDexOutputPath = mMainActivity.getDir("outdex", Context.MODE_PRIVATE);
        
        // Initialize the class loader with the secondary dex file.
        DexClassLoader cl = new DexClassLoader(dexInternalStoragePath.getAbsolutePath(),
                optimizedDexOutputPath.getAbsolutePath(),
                null,
                mMainActivity.getClassLoader());
        Class hookClass = null;
        
        try {
            hookClass = cl.loadClass(className);
            // Cast the return object to the library interface so that the
            // caller can directly invoke methods in the interface.
            // Alternatively, the caller can invoke methods through reflection,
            // which is more verbose and slow.
            HookPlugin importedHook = (HookPlugin) hookClass.newInstance();
            return importedHook;
        } catch (Exception exception) {
            // Handle exception gracefully here.
            exception.printStackTrace();
            return null;
        }
    }
    
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