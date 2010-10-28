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

public class SharedDeckDownload extends Download implements Parcelable {

    private static final long serialVersionUID = 1L;

    public static final int UPDATING = 5;

    private int id;
    private String filename;
    private int numUpdatedCards;
    private int numTotalCards;
    private double estTimeToCompletion;


    public SharedDeckDownload(String title) {
        super(title);
    }


    public SharedDeckDownload(int id, String title) {
        super(title);
        this.id = id;
    }


    public int getId() {
        return id;
    }


    public void setId(int id) {
        this.id = id;
    }


    public String getFilename() {
        return filename;
    }


    public void setFilename(String filename) {
        this.filename = filename;
    }


    public int getNumTotalCards() {
        return numTotalCards;
    }


    public void setNumTotalCards(int numTotalCards) {
        this.numTotalCards = numTotalCards;
    }


    public int getNumUpdatedCards() {
        return numUpdatedCards;
    }


    public void setNumUpdatedCards(int numUpdatedCards) {
        this.numUpdatedCards = numUpdatedCards;
    }


    // ETA: estimated time for completion in seconds
    @Override
    public String getEstTimeToCompletion() {

        if (estTimeToCompletion < 0.1) {
            return "";
        }

        String estTimeStr;
        long estTime = (long) estTimeToCompletion;
        long hours = estTime / 3600;
        estTime %= 3600;
        long minutes = estTime / 60;
        long seconds = estTime % 60;

        if (hours > 0) {
            if (minutes > 0) {
                return String.format("~ %dh %dm", hours, minutes);
            } else {
                return String.format("~ %dh", hours);
            }
        } else if (minutes > 10) {
            return String.format("~ %dm", minutes);
        } else if (minutes > 0) {
            if (seconds > 0) {
                return String.format("~ %dm %ds", minutes, seconds);
            } else {
                return String.format("~ %dm", minutes);
            }
        } else {
            return String.format("~ %ds", seconds);
        }
    }


    @Override
    public void setEstTimeToCompletion(double estTime) {
        estTimeToCompletion = estTime;
    }


    @Override
    public int getProgress() {
        if (status == UPDATING || status == PAUSED) {
            if (numTotalCards > 0) {
                return (int) (((float) numUpdatedCards / numTotalCards) * 100);
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
        dest.writeInt(id);
        dest.writeString(filename);
        dest.writeInt(numUpdatedCards);
    }


    @Override
    protected void readFromParcel(Parcel in) {
        super.readFromParcel(in);
        id = in.readInt();
        filename = in.readString();
        numUpdatedCards = in.readInt();
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
