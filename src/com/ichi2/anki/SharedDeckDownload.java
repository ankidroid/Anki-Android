package com.ichi2.anki;

import android.os.Parcel;
import android.os.Parcelable;

public class SharedDeckDownload extends Download implements Parcelable {

	private static final long serialVersionUID = 1L;

	public static final int UPDATE = 5;
	
	private int id;
	private String filename;
	private int numUpdatedCards;
	
	public SharedDeckDownload(String title)
	{
		super(title);
	}
	
	public SharedDeckDownload(int id, String title) {
		super(title);
		this.id = id;
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
	
	public int getNumUpdatedCards() {
		return numUpdatedCards;
	}

	public void setNumUpdatedCards(int numUpdatedCards) {
		this.numUpdatedCards = numUpdatedCards;
	}
	
	/********************************************************************
	 * Parcel methods													*
	 ********************************************************************/
	
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
		dest.writeInt(numUpdatedCards);
	}
	
	protected void readFromParcel(Parcel in) {
		super.readFromParcel(in);
		id = in.readInt();
		filename = in.readString();
		numUpdatedCards = in.readInt();
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
