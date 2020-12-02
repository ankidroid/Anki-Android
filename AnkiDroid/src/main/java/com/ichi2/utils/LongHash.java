package com.ichi2.utils;

/**
 * A very simple hashing function which simply ignore the higher bits. Since most longs in AnkiDroid are timestamp,
 * we can hope they are distributed well-enough that ignoring higher bits does not change the number of collision.
 */
public class LongHash implements it.unimi.dsi.fastutil.longs.LongHash.Strategy  {
    private LongHash(){}
    public static final LongHash LONG_HASH = new LongHash();
    @Override
    public int hashCode(long e) {
        return (int) e;
    }


    @Override
    public boolean equals(long a, long b) {
        return a == b;
    }
}
