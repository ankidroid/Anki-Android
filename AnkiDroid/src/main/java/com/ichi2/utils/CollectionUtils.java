package com.ichi2.utils;

import java.util.Collection;
import java.util.List;

public class CollectionUtils {
    /** Throws IndexOutOfBoundsException on empty list*/
    public static <T> T getLastListElement(List<T> l) {
        return l.get(l.size()-1);
    }
}
