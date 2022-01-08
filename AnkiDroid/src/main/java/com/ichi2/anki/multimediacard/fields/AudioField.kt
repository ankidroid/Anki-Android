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

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of Audio field types
 */
public abstract class AudioField extends FieldBase implements IField {
    protected String mAudioPath;
    protected String mName;
    protected boolean mHasTemporaryMedia = false;

    protected static final String PATH_REGEX = "\\[sound:(.*)]";


    @Override
    public abstract EFieldType getType();


    @Override
    public boolean setType(EFieldType type) {
        return false;
    }


    @Override
    public abstract boolean isModified();


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
        return false;
    }


    @Override
    public String getImagePath() {
        return null;
    }


    @Override
    public boolean setAudioPath(String pathToAudio) {
        mAudioPath = pathToAudio;
        setThisModified();
        return true;
    }


    @Override
    public String getAudioPath() {
        return mAudioPath;
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
    public abstract void setHasTemporaryMedia(boolean hasTemporaryMedia);


    @Override
    public abstract boolean hasTemporaryMedia();


    @Override
    public abstract String getName();


    @Override
    public abstract void setName(String name);


    @Override
    public String getFormattedValue() {
        String formattedValue = "";
        if (getAudioPath() != null) {
            File file = new File(getAudioPath());
            if (file.exists()) {
                formattedValue = String.format("[sound:%s]", file.getName());
            }
        }

        return formattedValue;
    }


    @Override
    public void setFormattedString(Collection col, String value) {
        Pattern p = Pattern.compile(PATH_REGEX);
        Matcher m = p.matcher(value);
        String res = "";
        if (m.find()) {
            res = m.group(1);
        }
        String mediaDir = col.getMedia().dir() + "/";
        setAudioPath(mediaDir + res);
    }
}
