/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
 *                                                                                      *
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

package com.ichi2.anki.multimediacard.googleimagesearch.json;

import java.util.List;

/**
 * One of the JSON classes. generated, corrected by Zaur.
 * <p>
 * Google Image search response.
 */
public class Cursor {
    private Number currentPageIndex;
    private String estimatedResultCount;
    private String moreResultsUrl;
    private List<Page> pages;
    private String resultCount;
    private String searchResultTime;


    public Number getCurrentPageIndex() {
        return this.currentPageIndex;
    }


    public void setCurrentPageIndex(Number currentPageIndex) {
        this.currentPageIndex = currentPageIndex;
    }


    public String getEstimatedResultCount() {
        return this.estimatedResultCount;
    }


    public void setEstimatedResultCount(String estimatedResultCount) {
        this.estimatedResultCount = estimatedResultCount;
    }


    public String getMoreResultsUrl() {
        return this.moreResultsUrl;
    }


    public void setMoreResultsUrl(String moreResultsUrl) {
        this.moreResultsUrl = moreResultsUrl;
    }


    public List<Page> getPages() {
        return this.pages;
    }


    public void setPages(List<Page> pages) {
        this.pages = pages;
    }


    public String getResultCount() {
        return this.resultCount;
    }


    public void setResultCount(String resultCount) {
        this.resultCount = resultCount;
    }


    public String getSearchResultTime() {
        return this.searchResultTime;
    }


    public void setSearchResultTime(String searchResultTime) {
        this.searchResultTime = searchResultTime;
    }
}
