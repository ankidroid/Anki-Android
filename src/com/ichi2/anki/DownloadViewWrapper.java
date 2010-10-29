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

import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

public class DownloadViewWrapper {

    private View mBase;
    private TextView mHeaderTitle = null;
    private TextView mDownloadTitle = null;
    private ProgressBar mProgressBar = null;
    private TextView mProgressBarText = null;
    private TextView mEstimatedTimeText = null;
    private TextView mDeckTitle = null;
    private TextView mDeckFacts = null;


    DownloadViewWrapper(View base) {
        this.mBase = base;
    }


    TextView getHeaderTitle() {
        if (mHeaderTitle == null) {
            mHeaderTitle = (TextView) mBase.findViewById(R.id.header_title);
        }
        return mHeaderTitle;
    }


    TextView getDownloadTitle() {
        if (mDownloadTitle == null) {
            mDownloadTitle = (TextView) mBase.findViewById(R.id.download_title);
        }
        return mDownloadTitle;
    }


    ProgressBar getProgressBar() {
        if (mProgressBar == null) {
            mProgressBar = (ProgressBar) mBase.findViewById(R.id.progress_bar);
        }
        return mProgressBar;
    }


    TextView getProgressBarText() {
        if (mProgressBarText == null) {
            mProgressBarText = (TextView) mBase.findViewById(R.id.progress_text);
        }
        return mProgressBarText;
    }


    TextView getEstimatedTimeText() {
        if (mEstimatedTimeText == null) {
            mEstimatedTimeText = (TextView) mBase.findViewById(R.id.estimated_text);
        }
        return mEstimatedTimeText;
    }


    TextView getDeckTitle() {
        if (mDeckTitle == null) {
            mDeckTitle = (TextView) mBase.findViewById(R.id.deck_title);
        }
        return mDeckTitle;
    }


    TextView getDeckFacts() {
        if (mDeckFacts == null) {
            mDeckFacts = (TextView) mBase.findViewById(R.id.deck_facts);
        }
        return mDeckFacts;
    }
}
