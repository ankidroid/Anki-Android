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

import com.ichi2.libanki.Utils;

import java.util.HashMap;

public class Download extends HashMap<String, Object> implements Parcelable {

    // Status codes
    public static final int STATUS_STARTED = -1;
    public static final int STATUS_DOWNLOADING = 0;
    public static final int STATUS_PAUSED = 1;
    public static final int STATUS_COMPLETE = 2;
    public static final int STATUS_CANCELLED = 3;
    public static final int STATUS_ERROR = 4;

    private static final long serialVersionUID = 1L;

    // Download's title
    private String mTitle;
    // Download's filename
    private String mFilename;
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
        mStatus = STATUS_STARTED;
        // The deck file name should match the deck title, but some characters are invalid in it,
        // so they need to be replaced.
        mFilename = Utils.removeInvalidDeckNameCharacters(mTitle);
        if (mFilename.length() > 40) {
            mFilename = mFilename.substring(0, 40);
        }
    }


    public long getSize() {
        return mSize;
    }


    public void setSize(long size) {
        mSize = size;
    }


    public long getDownloaded() {
        return mDownloaded;
    }


    public void setDownloaded(long downloaded) {
        mDownloaded = downloaded;
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
        mFilename = title;
    }


    public String getFilename() {
        return mFilename;
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
