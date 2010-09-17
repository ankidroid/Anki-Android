package com.ichi2.anki;

import java.util.HashMap;

import android.os.Parcel;
import android.os.Parcelable;

public class Download extends HashMap<String,Object> implements Parcelable {

	private static final long serialVersionUID = 1L;

	public static final String TAG = "AnkiDroid";

	// Status names
	public static final String STATUSES[] = {"Downloading", "Paused", "Complete", "Cancelled", "Error"};

	// Status codes
	public static final int START = -1;
	public static final int DOWNLOADING = 0;
	public static final int PAUSED = 1;
	public static final int COMPLETE = 2;
	public static final int CANCELLED = 3;
	public static final int ERROR = 4;

	// Download's title
	protected String title;
	// Download URL
	protected String url;
	// Size of download in bytes
	protected long size;
	// Number of bytes downloaded
	protected long downloaded;
	// Current status of download
	protected int status; 

	public Download(String title)
	{
		this.title = title;
		size = -1;
		downloaded = 0;
		status = START;
	}
	
	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
		put("filename", url.toString());
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
		/*
		float sizeToShow = size;
		int divs = 0;
		while(sizeToShow > 1000)
		{
			sizeToShow = sizeToShow / 1000;
			divs++;
		}
		
		DecimalFormat dec = new DecimalFormat("#.##");
		switch(divs)
		{
			case 0:
				put("size", dec.format(sizeToShow) + "B");
				break;
				
			case 1:
				put("size", dec.format(sizeToShow) + "KB");
				break;
				
			case 2:
				put("size", dec.format(sizeToShow) + "MB");
				break;
				
			case 3:
				put("size", dec.format(sizeToShow) + "GB");
				break;
		}
		*/
	}

	public long getDownloaded() {
		return downloaded;
	}

	public void setDownloaded(long downloaded) {
		this.downloaded = downloaded;
		/*
		float downloadedToShow = downloaded;
		int divs = 0;
		while(downloadedToShow > 1000)
		{
			downloadedToShow = downloadedToShow / 1000;
			divs++;
		}
		
		DecimalFormat dec = new DecimalFormat("#.##");
		switch(divs)
		{
			case 0:
				put("downloaded", dec.format(downloadedToShow) + "B");
				break;
				
			case 1:
				put("downloaded", dec.format(downloadedToShow) + "KB");
				break;
				
			case 2:
				put("downloaded", dec.format(downloadedToShow) + "MB");
				break;
				
			case 3:
				put("downloaded", dec.format(downloadedToShow) + "GB");
				break;
		}
		*/
	}

	public String getEstTimeToCompletion() {
		return "";
	}

	public void setEstTimeToCompletion(double estTime) {
		// pass
	}

	public int getProgress() 
	{
		return (int) (((float)downloaded / size) * 100);
	}
	
	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	/********************************************************************
	 * Parcel methods													*
	 ********************************************************************/
	
	public Download(Parcel in) {
		readFromParcel(in);
	}
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(url);
		dest.writeLong(size);
		dest.writeLong(downloaded);
		dest.writeInt(status);
		dest.writeString(title);
	}
	
	protected void readFromParcel(Parcel in) {
		url = in.readString();
		size = in.readLong();
		downloaded = in.readLong();
		status = in.readInt();
		title = in.readString();
	}
	
	public static final Parcelable.Creator<Download> CREATOR = new Parcelable.Creator<Download>() {
		
		public Download createFromParcel(Parcel in) {
			return new Download(in);
		}

		public Download[] newArray(int size) {
			return new Download[size];
		}
	};
}
