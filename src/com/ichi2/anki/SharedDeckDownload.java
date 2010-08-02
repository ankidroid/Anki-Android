package com.ichi2.anki;

import android.os.Parcel;
import android.os.Parcelable;

public class SharedDeckDownload extends Download implements Parcelable {

	public static final int UPDATE = 5;
	
	private int id;
	private String filename;
	
	public SharedDeckDownload(String title)
	{
		super(title);
		setStatus(UPDATE);
	}
	
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
	
	/**
	 * Parcel methods
	 */
	
	public SharedDeckDownload(Parcel in) {
		super(in);
		readFromParcel(in);
	}
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		super.writeToParcel(dest, flags);
		dest.writeInt(id);
		dest.writeString(filename);
	}
	
	protected void readFromParcel(Parcel in) {
		super.readFromParcel(in);
		id = in.readInt();
		filename = in.readString();
	}
	
	public static final Parcelable.Creator<SharedDeckDownload> CREATOR = new Parcelable.Creator<SharedDeckDownload>() {
		
		public SharedDeckDownload createFromParcel(Parcel in) {
			return new SharedDeckDownload(in);
		}

		public SharedDeckDownload[] newArray(int size) {
			return new SharedDeckDownload[size];
		}
	};
}
