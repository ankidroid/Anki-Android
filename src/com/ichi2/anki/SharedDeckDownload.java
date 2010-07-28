package com.ichi2.anki;

public class SharedDeckDownload extends Download {

	public static final int UPDATE = 5;
	
	private int id;
	private String filename;
	
	public SharedDeckDownload(int id, String title, long downloaded) {
		super(title, downloaded);
		this.id = id;
	}
	
	public SharedDeckDownload(int id, String title, String filename, long size) {
		super(title);
		setSize(size);
		this.id = id;
		this.filename = filename;
	}
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}
	
	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}
}
