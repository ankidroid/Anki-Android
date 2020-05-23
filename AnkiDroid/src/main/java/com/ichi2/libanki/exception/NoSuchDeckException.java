package com.ichi2.libanki.exception;

/**
 * A deck was accessed which did not exist.
 * <br/>
 * Remarks: We use this checked exception to mimic an Optional before Java 1.8.
 */
public class NoSuchDeckException extends Exception {
    public NoSuchDeckException() {
    }
}
