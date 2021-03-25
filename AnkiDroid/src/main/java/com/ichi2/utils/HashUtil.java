package com.ichi2.utils;

import java.util.HashMap;
import java.util.HashSet;

public class HashUtil {
    /**
     * @param size Number of elements expected in the hash structure
     * @return Initial capacity for the hash structure. Copied from HashMap code
     */
    private static int capacity(int size) {
        return Math.max((int) (size/.75f) + 1, 16);
    }

    public static <T> HashSet<T> HashSetInit(int size) {
        return new HashSet<T>(capacity(size));
    }
    public static <T, U> HashMap<T, U> HashMapInit(int size) {
        return new HashMap<T, U>(capacity(size));
    }
}
