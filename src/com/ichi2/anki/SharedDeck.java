
package com.ichi2.anki;

import android.util.Log;

import java.util.HashMap;

@SuppressWarnings("serial")
public class SharedDeck extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    private static final String TAG = "AnkidroidSharedDecks";
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
        Log.i(TAG, "SHARED DECK:");
        Log.i(TAG, "		username = " + username);
        Log.i(TAG, "		title = " + title);
        Log.i(TAG, "		description = " + description);
        Log.i(TAG, "		tags = " + tags);
        Log.i(TAG, "		version = " + version);
        Log.i(TAG, "		facts = " + facts);
        Log.i(TAG, "		size = " + size);
        Log.i(TAG, "		count = " + count);
        Log.i(TAG, "		modified = " + modified);
        Log.i(TAG, "		fileName = " + fileName);
    }
}
