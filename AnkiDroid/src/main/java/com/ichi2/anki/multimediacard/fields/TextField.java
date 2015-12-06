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

/**
 * Text Field implementation.
 */
public class TextField extends FieldBase implements IField {
    private static final long serialVersionUID = -6508967905716947525L;
    String mText = "";
    private String mName;


    @Override
    public EFieldType getType() {
        return EFieldType.TEXT;
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
        return false;
    }


    @Override
    public String getImagePath() {
        return null;
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
        return mText;
    }


    @Override
    public boolean setText(String text) {
        mText = text;
        setThisModified();
        return true;
    }


    @Override
    public void setHasTemporaryMedia(boolean hasTemporaryMedia) {
    }


    @Override
    public boolean hasTemporaryMedia() {
        // TODO Auto-generated method stub
        return false;
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
        return getText();
    }


    @Override
    public void setFormattedString(Collection col, String value) {
        mText = value;
    }
}
