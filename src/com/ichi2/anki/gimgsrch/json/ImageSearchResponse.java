
package com.ichi2.anki.gimgsrch.json;


public class ImageSearchResponse{
   	private ResponseData responseData;
   	private String responseDetails;
   	private Number responseStatus;
   	private boolean Ok = false;

   	public void setOk(boolean ok)
        {
            Ok = ok;
        }

   	public boolean getOk()
        {
   	    return Ok;
        }
   	
   	
 	public ResponseData getResponseData(){
		return this.responseData;
	}
	public void setResponseData(ResponseData responseData){
		this.responseData = responseData;
	}
 	public String getResponseDetails(){
		return this.responseDetails;
	}
	public void setResponseDetails(String responseDetails){
		this.responseDetails = responseDetails;
	}
 	public Number getResponseStatus(){
		return this.responseStatus;
	}
	public void setResponseStatus(Number responseStatus){
		this.responseStatus = responseStatus;
	}
}
