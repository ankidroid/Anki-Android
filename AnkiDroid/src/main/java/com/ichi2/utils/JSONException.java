package com.ichi2.utils;



/**
   Similar to JSONException in meaning, but unchecked */
public class JSONException extends RuntimeException {

    private JSONException exc = null;

    public JSONException(String s) {
        super(s);
    }

    public JSONException() {
        super();
    }

    public JSONException(Throwable e) {
        super(e);
    }

    public JSONException(JSONException e) {
        super(e);
        exc = e;
    }

    public JSONException asException() {
        if (exc!=null) {
            return exc;
        } else {
            return new JSONException(toString());
        }
    }

    public void throwAsException() throws JSONException {
        throw asException();
    }
}
