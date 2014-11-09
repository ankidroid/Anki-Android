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

import java.util.Locale;

public class SharedDeckDownload extends Download implements Parcelable {

    public static final int STATUS_UPDATING = 5;

    private static final long serialVersionUID = 1L;

    private int mId;
    private String mFilename;
    private int mNumUpdatedCards;
    private int mNumTotalCards;
    private double mEstTimeToCompletion;


    public SharedDeckDownload(String title) {
        super(title);
    }


    public SharedDeckDownload(int id, String title) {
        super(title);
        mId = id;
    }


    public int getId() {
        return mId;
    }


    public void setNumTotalCards(int numTotalCards) {
        mNumTotalCards = numTotalCards;
    }


    public int getNumUpdatedCards() {
        return mNumUpdatedCards;
    }


    public void setNumUpdatedCards(int numUpdatedCards) {
        mNumUpdatedCards = numUpdatedCards;
    }


    // ETA: estimated time for completion in seconds
    @Override
    public String getEstTimeToCompletion() {

        if (mEstTimeToCompletion < 0.1) {
            return "";
        }

        long estTime = (long) mEstTimeToCompletion;
        long hours = estTime / 3600;
        estTime %= 3600;
        long minutes = estTime / 60;
        long seconds = estTime % 60;

        if (hours > 0) {
            if (minutes > 0) {
                return String.format(Locale.getDefault(), "~ %dh %dm", hours, minutes);
            } else {
                return String.format(Locale.getDefault(), "~ %dh", hours);
            }
        } else if (minutes > 10) {
            return String.format(Locale.getDefault(), "~ %dm", minutes);
        } else if (minutes > 0) {
            if (seconds > 0) {
                return String.format(Locale.getDefault(), "~ %dm %ds", minutes, seconds);
            } else {
                return String.format(Locale.getDefault(), "~ %dm", minutes);
            }
        } else {
            return String.format(Locale.getDefault(), "~ %ds", seconds);
        }
    }


    @Override
    public void setEstTimeToCompletion(double estTime) {
        mEstTimeToCompletion = estTime;
    }


    @Override
    public int getProgress() {
        if (mStatus == STATUS_UPDATING || mStatus == STATUS_PAUSED) {
            if (mNumTotalCards > 0) {
                return (int) (((float) mNumUpdatedCards / mNumTotalCards) * 100);
            } else {
                return 0;
            }
        } else {
            return super.getProgress();
        }
    }


    /********************************************************************
     * Parcel methods *
     ********************************************************************/

    public SharedDeckDownload(Parcel in) {
        super(in);
        readFromParcel(in);
    }


    @Override
    public int describeContents() {
        return 0;
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mId);
        dest.writeString(mFilename);
        dest.writeInt(mNumUpdatedCards);
    }


    @Override
    protected void readFromParcel(Parcel in) {
        super.readFromParcel(in);
        mId = in.readInt();
        mFilename = in.readString();
        mNumUpdatedCards = in.readInt();
    }

    public static final Parcelable.Creator<SharedDeckDownload> CREATOR = new Parcelable.Creator<SharedDeckDownload>() {

        @Override
        public SharedDeckDownload createFromParcel(Parcel in) {
            return new SharedDeckDownload(in);
        }


        @Override
        public SharedDeckDownload[] newArray(int size) {
            return new SharedDeckDownload[size];
        }
    };
}
