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

    private View base;
    private TextView headerTitle = null;
    private TextView downloadTitle = null;
    private ProgressBar progressBar = null;
    private TextView progressBarText = null;
    private TextView estimatedTimeText = null;
    private TextView deckTitle = null;
    private TextView deckFacts = null;


    DownloadViewWrapper(View base) {
        this.base = base;
    }


    TextView getHeaderTitle() {
        if (headerTitle == null) {
            headerTitle = (TextView) base.findViewById(R.id.header_title);
        }
        return headerTitle;
    }


    TextView getDownloadTitle() {
        if (downloadTitle == null) {
            downloadTitle = (TextView) base.findViewById(R.id.download_title);
        }
        return downloadTitle;
    }


    ProgressBar getProgressBar() {
        if (progressBar == null) {
            progressBar = (ProgressBar) base.findViewById(R.id.progress_bar);
        }
        return progressBar;
    }


    TextView getProgressBarText() {
        if (progressBarText == null) {
            progressBarText = (TextView) base.findViewById(R.id.progress_text);
        }
        return progressBarText;
    }


    TextView getEstimatedTimeText() {
        if (estimatedTimeText == null) {
            estimatedTimeText = (TextView) base.findViewById(R.id.estimated_text);
        }
        return estimatedTimeText;
    }


    TextView getDeckTitle() {
        if (deckTitle == null) {
            deckTitle = (TextView) base.findViewById(R.id.deck_title);
        }
        return deckTitle;
    }


    TextView getDeckFacts() {
        if (deckFacts == null) {
            deckFacts = (TextView) base.findViewById(R.id.deck_facts);
        }
        return deckFacts;
    }
}
