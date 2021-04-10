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

package com.ichi2.anki.multimediacard.glosbe.json;

import java.util.List;

/**
 * This is one of the classes, automatically generated to transform json replies from glosbe.com
 */
public class Tuc {
    private List<Number> mAuthors;
    private Number mMeaningId;
    private List<Meaning> mMeanings;
    private Phrase mPhrase;


    public List<Number> getAuthors() {
        return this.mAuthors;
    }


    public void setAuthors(List<Number> authors) {
        this.mAuthors = authors;
    }


    public Number getMeaningId() {
        return this.mMeaningId;
    }


    public void setMeaningId(Number meaningId) {
        this.mMeaningId = meaningId;
    }


    public List<Meaning> getMeanings() {
        return this.mMeanings;
    }


    public void setMeanings(List<Meaning> meanings) {
        this.mMeanings = meanings;
    }


    public Phrase getPhrase() {
        return this.mPhrase;
    }


    public void setPhrase(Phrase phrase) {
        this.mPhrase = phrase;
    }
}
