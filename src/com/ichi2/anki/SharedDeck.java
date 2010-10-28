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

import android.util.Log;

import java.util.HashMap;

@SuppressWarnings("serial")
public class SharedDeck extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    protected int id;
    protected String username;
    protected String title;
    protected String description;
    protected String tags;
    protected int version;
    protected int facts;
    protected int size;
    protected int count;
    protected double modified;
    protected String fileName;


    public int getId() {
        return id;
    }


    public void setId(int id) {
        this.id = id;
    }


    public String getUsername() {
        return username;
    }


    public void setUsername(String username) {
        this.username = username;
    }


    public String getTitle() {
        return title;
    }


    public void setTitle(String title) {
        this.title = title;
        put("title", this.title);
    }


    public String getDescription() {
        return description;
    }


    public void setDescription(String description) {
        this.description = description;
    }


    public String getTags() {
        return tags;
    }


    public void setTags(String tags) {
        this.tags = tags;
    }


    public int getVersion() {
        return version;
    }


    public void setVersion(int version) {
        this.version = version;
    }


    public int getFacts() {
        return facts;
    }


    public void setFacts(int facts) {
        this.facts = facts;
        if (facts == 1) {
            put("facts", this.facts + " " + AnkiDroidApp.getAppResources().getString(R.string.fact));
        } else {
            put("facts", this.facts + " " + AnkiDroidApp.getAppResources().getString(R.string.facts));
        }
    }


    public int getSize() {
        return size;
    }


    public void setSize(int size) {
        this.size = size;
    }


    public int getCount() {
        return count;
    }


    public void setCount(int count) {
        this.count = count;
    }


    public double getModified() {
        return modified;
    }


    public void setModified(double modified) {
        this.modified = modified;
    }


    public String getFileName() {
        return fileName;
    }


    public void setFileName(String fileName) {
        this.fileName = fileName;
    }


    public void prettyLog() {
        Log.i(AnkiDroidApp.TAG, "SHARED DECK:");
        Log.i(AnkiDroidApp.TAG, "		username = " + username);
        Log.i(AnkiDroidApp.TAG, "		title = " + title);
        Log.i(AnkiDroidApp.TAG, "		description = " + description);
        Log.i(AnkiDroidApp.TAG, "		tags = " + tags);
        Log.i(AnkiDroidApp.TAG, "		version = " + version);
        Log.i(AnkiDroidApp.TAG, "		facts = " + facts);
        Log.i(AnkiDroidApp.TAG, "		size = " + size);
        Log.i(AnkiDroidApp.TAG, "		count = " + count);
        Log.i(AnkiDroidApp.TAG, "		modified = " + modified);
        Log.i(AnkiDroidApp.TAG, "		fileName = " + fileName);
    }
}
