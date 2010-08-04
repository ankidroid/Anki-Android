package com.ichi2.anki;

import java.util.HashMap;

import android.util.Log;

public class SharedDeck extends HashMap<String, Object>{

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
		if(facts == 1)
		{
			put("facts", this.facts + " " + AnkiDroidApp.getAppResources().getString(R.string.fact));
		}
		else
		{
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
	
	public void prettyLog()
	{
		Log.i(TAG, "SHARED DECK:");
		Log.i(TAG, "		username = " + this.username);
		Log.i(TAG, "		title = " + this.title);
		Log.i(TAG, "		description = " + this.description);
		Log.i(TAG, "		tags = " + this.tags);
		Log.i(TAG, "		version = " + this.version);
		Log.i(TAG, "		facts = " + this.facts);
		Log.i(TAG, "		size = " + this.size);
		Log.i(TAG, "		count = " + this.count);
		Log.i(TAG, "		modified = " + this.modified);
		Log.i(TAG, "		fileName = " + this.fileName);
	}
}
