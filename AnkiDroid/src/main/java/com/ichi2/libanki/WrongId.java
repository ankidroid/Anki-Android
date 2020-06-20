package com.ichi2.libanki;

public class WrongId extends RuntimeException {
    public WrongId(long id, String kind) {
        super(" No " + kind + " with id " + id);
    }
}
