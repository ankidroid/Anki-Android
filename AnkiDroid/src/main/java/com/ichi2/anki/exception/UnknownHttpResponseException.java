package com.ichi2.anki.exception;

@SuppressWarnings("serial")
public class UnknownHttpResponseException extends Exception {
    private final Integer mCode;
    public UnknownHttpResponseException(String message, Integer code) {
        super(message);
        mCode = code;
    }
    public int getResponseCode() {
        return mCode;
    }
}