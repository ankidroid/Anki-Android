package com.ichi2.anki.exception;

/** An exception for manual reporting to ACRA */
public class ManuallyReportedException extends RuntimeException {
    public ManuallyReportedException(String message) {
        super(message);
    }
}
