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

import java.io.Serializable;

/**
 * General interface for a field of any type.
 */
public interface IField extends Serializable {
    EFieldType getType();


    boolean setType(EFieldType type);


    boolean isModified();


    // For mixed type
    String getHtml();


    boolean setHtml(String html);


    // For image type. Resets type.
    // Makes no sense to call when type is not image.
    // the same for other groups below.
    boolean setImagePath(String pathToImage);


    String getImagePath();


    // For Audio type
    boolean setAudioPath(String pathToAudio);


    String getAudioPath();


    // For Text type
    String getText();


    boolean setText(String text);


    /**
     * Mark if the current media path is temporary and if it should be deleted once the media has been processed.
     * 
     * @param hasTemporaryMedia True if the media is temporary, False if it is existing media.
     * @return
     */
    void setHasTemporaryMedia(boolean hasTemporaryMedia);


    boolean hasTemporaryMedia();


    String getName();


    void setName(String name);


    /**
     * Returns the formatted value for this field. Each implementation of IField should return in a format which will be
     * used to store in the database
     * 
     * @return
     */
    String getFormattedValue();


    /**
     * @param col Collection - bad abstraction, used to obtain media directory only.
     * @param value The HTML to send to the field.
     */
    void setFormattedString(Collection col, String value);
}
