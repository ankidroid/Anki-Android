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
 * @author zaur This is one of the classes, automatically generated to transform json replies from glosbe.com This is
 *         the root class, from which response starts.
 */
public class Response {
    private String dest;
    private String from;
    private String phrase;
    private String result;
    private List<Tuc> tuc;


    public String getDest() {
        return this.dest;
    }


    public void setDest(String dest) {
        this.dest = dest;
    }


    public String getFrom() {
        return this.from;
    }


    public void setFrom(String from) {
        this.from = from;
    }


    public String getPhrase() {
        return this.phrase;
    }


    public void setPhrase(String phrase) {
        this.phrase = phrase;
    }


    public String getResult() {
        return this.result;
    }


    public void setResult(String result) {
        this.result = result;
    }


    public List<Tuc> getTuc() {
        return this.tuc;
    }


    public void setTuc(List<Tuc> tuc) {
        this.tuc = tuc;
    }
}
