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

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;

public class Download extends HashMap<String, Object> implements Parcelable {

    private static final long serialVersionUID = 1L;

    // Status codes
    public static final int START = -1;
    public static final int DOWNLOADING = 0;
    public static final int PAUSED = 1;
    public static final int COMPLETE = 2;
    public static final int CANCELLED = 3;
    public static final int ERROR = 4;

    // Download's title
    private String mTitle;
    // Download URL
    private String mUrl;
    // Size of download in bytes
    private long mSize;
    // Number of bytes downloaded
    private long mDownloaded;
    // Current status of download
    protected int mStatus;


    public Download(String title) {
        mTitle = title;
        this.put(title, true);
        mSize = -1;
        mDownloaded = 0;
        mStatus = START;
    }


    public String getUrl() {
        return mUrl;
    }


    public void setUrl(String url) {
        mUrl = url;
        put("filename", url.toString());
    }


    public long getSize() {
        return mSize;
    }


    public void setSize(long size) {
        mSize = size;
        /*
         * float sizeToShow = size; int divs = 0; while(sizeToShow > 1000) { sizeToShow = sizeToShow / 1000; divs++; }
         * DecimalFormat dec = new DecimalFormat("#.##"); switch(divs) { case 0: put("size", dec.format(sizeToShow) +
         * "B"); break; case 1: put("size", dec.format(sizeToShow) + "KB"); break; case 2: put("size",
         * dec.format(sizeToShow) + "MB"); break; case 3: put("size", dec.format(sizeToShow) + "GB"); break; }
         */
    }


    public long getDownloaded() {
        return mDownloaded;
    }


    public void setDownloaded(long downloaded) {
        mDownloaded = downloaded;
        /*
         * float downloadedToShow = downloaded; int divs = 0; while(downloadedToShow > 1000) { downloadedToShow =
         * downloadedToShow / 1000; divs++; } DecimalFormat dec = new DecimalFormat("#.##"); switch(divs) { case 0:
         * put("downloaded", dec.format(downloadedToShow) + "B"); break; case 1: put("downloaded",
         * dec.format(downloadedToShow) + "KB"); break; case 2: put("downloaded", dec.format(downloadedToShow) + "MB");
         * break; case 3: put("downloaded", dec.format(downloadedToShow) + "GB"); break; }
         */
    }


    public String getEstTimeToCompletion() {
        return "";
    }


    public void setEstTimeToCompletion(double estTime) {
        // pass
    }


    public int getProgress() {
        return (int) (((float) mDownloaded / mSize) * 100);
    }


    public int getStatus() {
        return mStatus;
    }


    public void setStatus(int status) {
        mStatus = status;
    }


    public String getTitle() {
        return mTitle;
    }


    public void setTitle(String title) {
        this.remove(mTitle);
        this.put(title, true);
        mTitle = title;
    }


    /********************************************************************
     * Parcel methods *
     ********************************************************************/

    public Download(Parcel in) {
        readFromParcel(in);
    }


    @Override
    public int describeContents() {
        return 0;
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mUrl);
        dest.writeLong(mSize);
        dest.writeLong(mDownloaded);
        dest.writeInt(mStatus);
        dest.writeString(mTitle);
    }


    protected void readFromParcel(Parcel in) {
        mUrl = in.readString();
        mSize = in.readLong();
        mDownloaded = in.readLong();
        mStatus = in.readInt();
        mTitle = in.readString();
    }

    public static final Parcelable.Creator<Download> CREATOR = new Parcelable.Creator<Download>() {

        @Override
        public Download createFromParcel(Parcel in) {
            return new Download(in);
        }


        @Override
        public Download[] newArray(int size) {
            return new Download[size];
        }
    };
}
