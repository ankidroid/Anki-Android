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

package com.ichi2.anki.multimediacard.fields;

import com.ichi2.libanki.Collection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

/**
 * Field with an image.
 */
public class ImageField extends FieldBase implements IField {
    private static final long serialVersionUID = 4431611060655809687L;
    String mImagePath;
    private boolean mHasTemporaryMedia = false;
    private String mName;


    @Override
    public EFieldType getType() {
        return EFieldType.IMAGE;
    }


    @Override
    public boolean setType(EFieldType type) {
        return false;
    }


    @Override
    public boolean isModified() {
        return getThisModified();
    }


    @Override
    public String getHtml() {
        return null;
    }


    @Override
    public boolean setHtml(String html) {
        return false;
    }


    @Override
    public boolean setImagePath(String pathToImage) {
        mImagePath = pathToImage;
        setThisModified();
        return true;
    }


    @Override
    public String getImagePath() {
        return mImagePath;
    }


    @Override
    public boolean setAudioPath(String pathToAudio) {
        return false;
    }


    @Override
    public String getAudioPath() {
        return null;
    }


    @Override
    public String getText() {
        return null;
    }


    @Override
    public boolean setText(String text) {
        return false;
    }


    @Override
    public void setHasTemporaryMedia(boolean hasTemporaryMedia) {
        mHasTemporaryMedia = hasTemporaryMedia;
    }


    @Override
    public boolean hasTemporaryMedia() {
        return mHasTemporaryMedia;
    }


    @Override
    public String getName() {
        return mName;
    }


    @Override
    public void setName(String name) {
        mName = name;
    }


    @Override
    public String getFormattedValue() {
        File file = new File(getImagePath());
        return formatImageFileName(file);
    }


    @NonNull
    @VisibleForTesting
    static String formatImageFileName(@NonNull File file) {
        if (file.exists()) {
            return String.format("<img src=\"%s\">", file.getName());
        } else {
            return "";
        }
    }


    @Override
    public void setFormattedString(Collection col, String value) {
        setImagePath(getImageFullPath(col, value));
    }


    @NonNull
    @VisibleForTesting
    static String getImageFullPath(Collection col, String value) {
        String path = parseImageSrcFromHtml(value);
        if ("".equals(path)) {
            return "";
        }
        String mediaDir = col.getMedia().dir() + "/";
        return mediaDir + path;
    }


    @VisibleForTesting
    @CheckResult
    @NonNull
    static String parseImageSrcFromHtml(String html) {
        if (html == null) {
            return "";
        }
        try {
            Document doc = Jsoup.parseBodyFragment(html);
            Element image = doc.selectFirst("img[src]");
            if (image == null) {
                return "";
            }
            return image.attr("src");
        } catch (Exception e) {
            Timber.w(e);
            return "";
        }
    }
}
