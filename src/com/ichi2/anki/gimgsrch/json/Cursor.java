
package com.ichi2.anki.gimgsrch.json;

import java.util.List;

/**
 * @author zaur
 *
 *      One of the JSON classes. generated, corrected by Zaur.
 *      Google Image search response.
 *
 */
public class Cursor{
   	private Number currentPageIndex;
   	private String estimatedResultCount;
   	private String moreResultsUrl;
   	private List<Page> pages;
   	private String resultCount;
   	private String searchResultTime;

 	public Number getCurrentPageIndex(){
		return this.currentPageIndex;
	}
	public void setCurrentPageIndex(Number currentPageIndex){
		this.currentPageIndex = currentPageIndex;
	}
 	public String getEstimatedResultCount(){
		return this.estimatedResultCount;
	}
	public void setEstimatedResultCount(String estimatedResultCount){
		this.estimatedResultCount = estimatedResultCount;
	}
 	public String getMoreResultsUrl(){
		return this.moreResultsUrl;
	}
	public void setMoreResultsUrl(String moreResultsUrl){
		this.moreResultsUrl = moreResultsUrl;
	}
 	public List<Page> getPages(){
		return this.pages;
	}
	public void setPages(List<Page> pages){
		this.pages = pages;
	}
 	public String getResultCount(){
		return this.resultCount;
	}
	public void setResultCount(String resultCount){
		this.resultCount = resultCount;
	}
 	public String getSearchResultTime(){
		return this.searchResultTime;
	}
	public void setSearchResultTime(String searchResultTime){
		this.searchResultTime = searchResultTime;
	}
}
