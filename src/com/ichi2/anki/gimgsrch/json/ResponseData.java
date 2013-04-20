
package com.ichi2.anki.gimgsrch.json;

import java.util.List;

/**
 * @author zaur
 *
 *      One of the JSON classes. generated, corrected by Zaur.
 *      Google Image search response.
 *
 */
public class ResponseData{
   	private Cursor cursor;
   	private List<Result> results;

 	public Cursor getCursor(){
		return this.cursor;
	}
	public void setCursor(Cursor cursor){
		this.cursor = cursor;
	}
 	public List<Result> getResults(){
		return this.results;
	}
	public void setResults(List<Result> results){
		this.results = results;
	}
}
