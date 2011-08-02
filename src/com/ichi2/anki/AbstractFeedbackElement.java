/***************************************************************************************
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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

abstract class AbstractFeedbackElement {

    protected Context mCurContext;

    protected HashMap<String, String> mInformation = new HashMap<String, String>(20);

    private long getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }


    private long getTotalInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
        return totalBlocks * blockSize;
    }


    protected void collectInformation() {
        Log.i(AnkiDroidApp.TAG, "collectInformation");

        if (mCurContext == null) {
            return;
        }

        try {
            Log.i(AnkiDroidApp.TAG, "collecting information");

            PackageManager pm = mCurContext.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(mCurContext.getPackageName(), 0);

            mInformation.put("VersionName", pi.versionName); // Version
            mInformation.put("PackageName", pi.packageName); // Package name
            mInformation.put("AndroidVersion", android.os.Build.VERSION.RELEASE); // Android version
            mInformation.put("Board", android.os.Build.BOARD);
            mInformation.put("Brand", android.os.Build.BRAND);
            mInformation.put("Device", android.os.Build.DEVICE);
            mInformation.put("Display", android.os.Build.DISPLAY);
            //mInformation.put("FingerPrint", android.os.Build.FINGERPRINT);
            mInformation.put("Host", android.os.Build.HOST);
            mInformation.put("ID", android.os.Build.ID);
            mInformation.put("Model", android.os.Build.MODEL);
            mInformation.put("Product", android.os.Build.PRODUCT);
            //mInformation.put("Tags", android.os.Build.TAGS);
            mInformation.put("Time", Long.toString(android.os.Build.TIME));
            //mInformation.put("Type", android.os.Build.TYPE);
            //mInformation.put("User", android.os.Build.USER);
            mInformation.put("TotalInternalMemory", Long.toString(getTotalInternalMemorySize()));
            mInformation.put("AvailableInternalMemory", Long.toString(getAvailableInternalMemorySize()));

            Log.i(AnkiDroidApp.TAG, "Information collected");
        } catch (Exception e) {
            Log.i(AnkiDroidApp.TAG, e.toString());
        }
    }


    /**
     * Create a feedback report, to be sent by the Feedback activity.
     * @param reportInformation the report to send, can be quite long.
     */
    public void createReport(String reportInformation) {
        try {
            Log.i(AnkiDroidApp.TAG, "saveReportFile");

            Date currentDate = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            String filename = String.format("ad-%s.stacktrace", formatter.format(currentDate));

            FileOutputStream trace = mCurContext.openFileOutput(filename, Context.MODE_PRIVATE);
            trace.write(reportInformation.getBytes());
            trace.close();

            Log.i(AnkiDroidApp.TAG, "report saved");
        } catch (Exception e) {
            Log.i(AnkiDroidApp.TAG, e.toString());
        }
    }
    
    
    public void setContext(Context context) {
        this.mCurContext = context;
    }
}