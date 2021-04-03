
package com.ichi2.anki.exception;

import timber.log.Timber;

public class ConfirmModSchemaException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -9215098969154590797L;


    public ConfirmModSchemaException() {
    }


    /**
     * Add the current exception to log.
     */
    public void log() {
        Timber.v(this);
    }
}
