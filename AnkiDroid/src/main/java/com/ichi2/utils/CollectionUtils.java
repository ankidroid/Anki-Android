package com.ichi2.utils;

import java.util.Collection;
import java.util.List;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollections;
import it.unimi.dsi.fastutil.longs.LongList;

public class CollectionUtils {
    /** Throws IndexOutOfBoundsException on empty list*/
    public static <T> T getLastListElement(List<T> l) {
        return l.get(l.size()-1);
    }

    public static long getLastListElement(LongList l) {
        return l.getLong(l.size()-1);
    }

    /**
     * @param c A collection in which to add elements of it
     * @param it An iterator returning things to add to C
     * @param <T> Type of elements to copy from iterator to collection
     */
    public static <T> void addAll(Collection<T> c, Iterable<T> it) {
        for (T elt : it) {
            c.add(elt);
        }
    }

    public static LongArrayList singleton(long l) {
        LongArrayList list = new LongArrayList(1);
        list.add(l);
        return list;
    }
}
