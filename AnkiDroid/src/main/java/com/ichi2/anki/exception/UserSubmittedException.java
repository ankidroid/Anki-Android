package com.ichi2.anki.exception;

/** An exception sent by user for sending report manually */
public class UserSubmittedException extends RuntimeException {
    public UserSubmittedException(String message) {
        super(message);
    }
}
