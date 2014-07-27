
package com.ichi2.anki.exception;

public class APIVersionException extends Exception {
    private static final long serialVersionUID = 1819168207121655082L;


    public APIVersionException() {
    }


    public APIVersionException(String msg) {
        super(msg);
    }
}
