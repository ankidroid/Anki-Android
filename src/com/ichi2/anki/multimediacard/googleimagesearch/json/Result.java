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

/**
 * One of the JSON classes. generated, corrected by Zaur.
 * <p>
 * Google Image search response.
 */
public class Result {
    private String gsearchResultClass;
    private String content;
    private String contentNoFormatting;
    private String height;
    private String imageId;
    private String originalContextUrl;
    private String tbHeight;
    private String tbUrl;
    private String tbWidth;
    private String title;
    private String titleNoFormatting;
    private String unescapedUrl;
    private String url;
    private String visibleUrl;
    private String width;


    public String getGsearchResultClass() {
        return this.gsearchResultClass;
    }


    public void setGsearchResultClass(String gsearchResultClass) {
        this.gsearchResultClass = gsearchResultClass;
    }


    public String getContent() {
        return this.content;
    }


    public void setContent(String content) {
        this.content = content;
    }


    public String getContentNoFormatting() {
        return this.contentNoFormatting;
    }


    public void setContentNoFormatting(String contentNoFormatting) {
        this.contentNoFormatting = contentNoFormatting;
    }


    public String getHeight() {
        return this.height;
    }


    public void setHeight(String height) {
        this.height = height;
    }


    public String getImageId() {
        return this.imageId;
    }


    public void setImageId(String imageId) {
        this.imageId = imageId;
    }


    public String getOriginalContextUrl() {
        return this.originalContextUrl;
    }


    public void setOriginalContextUrl(String originalContextUrl) {
        this.originalContextUrl = originalContextUrl;
    }


    public String getTbHeight() {
        return this.tbHeight;
    }


    public void setTbHeight(String tbHeight) {
        this.tbHeight = tbHeight;
    }


    public String getTbUrl() {
        return this.tbUrl;
    }


    public void setTbUrl(String tbUrl) {
        this.tbUrl = tbUrl;
    }


    public String getTbWidth() {
        return this.tbWidth;
    }


    public void setTbWidth(String tbWidth) {
        this.tbWidth = tbWidth;
    }


    public String getTitle() {
        return this.title;
    }


    public void setTitle(String title) {
        this.title = title;
    }


    public String getTitleNoFormatting() {
        return this.titleNoFormatting;
    }


    public void setTitleNoFormatting(String titleNoFormatting) {
        this.titleNoFormatting = titleNoFormatting;
    }


    public String getUnescapedUrl() {
        return this.unescapedUrl;
    }


    public void setUnescapedUrl(String unescapedUrl) {
        this.unescapedUrl = unescapedUrl;
    }


    public String getUrl() {
        return this.url;
    }


    public void setUrl(String url) {
        this.url = url;
    }


    public String getVisibleUrl() {
        return this.visibleUrl;
    }


    public void setVisibleUrl(String visibleUrl) {
        this.visibleUrl = visibleUrl;
    }


    public String getWidth() {
        return this.width;
    }


    public void setWidth(String width) {
        this.width = width;
    }
}
