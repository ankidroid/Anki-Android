package com.ichi2.utils;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;

import androidx.annotation.CheckResult;

public class CollectionUtils {
    /** Throws IndexOutOfBoundsException on empty list*/
    public static <T> T getLastListElement(List<T> l) {
        return l.get(l.size()-1);
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

    public interface Predicate<T> {
        boolean apply(T t);
    }

    /**
     * @param filter Whether to add the element or not
     * @param it An iterator returning things to add to C
     * @param <T> Type of elements to copy from iterator to collection
     */
    @CheckResult
    public static <T> ArrayList<T> filter(Iterable<T> it, Predicate<T> filter) {
        return filterAndAdd(new ArrayList<>(), it, filter);
    }

    /**
     * @param filter Whether to add the element or not
     * @param c An iterator returning things to add to C
     * @param <T> Type of elements to copy from iterator to collection
     */
    @CheckResult
    public static <T> ArrayList<T> filter(T[] c, Predicate<T> filter) {
        ArrayList<T> list = new ArrayList<>(c.length);
        for (T t: c) {
            if (filter.apply(t)) {
                list.add(t);
            }
        }
        return list;
    }
    /**
     * @param filter Whether to add the element or not
     * @param c An iterator returning things to add to C
     * @param <T> Type of elements to copy from iterator to collection
     */
    @CheckResult
    public static <T> ArrayList<T> filter(Collection<T> c, Predicate<T> filter) {
        return filterAndAdd(new ArrayList<>(c.size()), c, filter);
    }

    /**
     * @param c A collection in which to add elements of it
     * @param filter Whether to add the element or not
     * @param it An iterator returning things to add to C
     * @param <T> Type of elements to copy from iterator to collection
     */
    public static <T, C extends Collection<T>> C filterAndAdd(C c, Iterable<T> it, Predicate<T> filter) {
        for (T elt : it) {
            if (filter.apply(elt)) {
                c.add(elt);
            }
        }
        return c;
    }

    public interface Function<K, V> {
        V apply(K k);
    }

    /**
     * @param en An iterator returning things to add to C
     * @param <T> Type of elements to copy from iterator to collection
     */
    public static <F, T> List<T> map(Enumeration<F> en, Function<F, T> fun) {
        List<T> l = new ArrayList<>();
        while (en.hasMoreElements()) {
            l.add(fun.apply(en.nextElement()));
        }
        return l;
    }

    /**
     * @param arr an array of element of type F
     * @param <T> Type of elements to copy from iterator to collection
     */
    public static <F, T> ArrayList<T> map(long[] arr, LongToFunctor<T> fun) {
        ArrayList<T> list = new ArrayList<>(arr.length);
        for (long l : arr) {
            list.add(fun.apply(l));
        }
        return list;
    }

    /**
     * @param c An iterator returning things to add to C
     * @param <T> Type of elements to copy from iterator to collection
     */
    public static <F, T> ArrayList<T> map(Collection<F> c, Function<F, T> fun) {
        return mapAndAdd(new ArrayList(c.size()), c, fun);
    }

    /**
     * @param it An iterator returning things to add to C
     * @param <T> Type of elements to copy from iterator to collection
     */
    public static <F, T> ArrayList<T> map(Iterable<F> it, Function<F, T> fun) {
        return mapAndAdd(new ArrayList(), it, fun);
    }

    /**
     * @param c A collection in which to add elements of it
     * @param it An iterator returning things to add to C
     * @param <T> Type of elements to copy from iterator to collection
     */
    public static <F, T, C extends Collection<T>> C mapAndAdd(C c, Iterable<F> it, Function<F, T> fun) {
        for (F elt : it) {
            c.add(fun.apply(elt));
        }
        return c;
    }

    /**
     * @param c A collection in which to add elements of it
     * @param it An iterator returning things to add to C
     * @param <T> Type of elements to copy from iterator to collection
     */
    public static <F, T> void addAll(Collection<T> c, F[] it, Function<F, T> fun) {
        for (F elt : it) {
            c.add(fun.apply(elt));
        }
    }

    public interface Thunk<T> {
        T apply();
    }

    public interface LongToFunctor<T> {
        T apply(long l);
    }

    /**
     * @param cursor A cursor with elements to move
     * @param fun Function to get elements form the cursor
     * @param <T> Type of elements to copy from iterator to collection
     */
    public static <T> ArrayList<T> map(Cursor cursor, Thunk<T> fun) {
        ArrayList<T> l = new ArrayList<>();
        while (cursor.moveToNext()) {
            l.add(fun.apply());
        }
        return l;
    }

    /**
     * @param c A collection in which to add elements of it
     * @param matcher A matcher returning usable values
     * @param fun Function to get elements form the cursor
     * @param <T> Type of elements to copy from iterator to collection
     */
    public static <T, C extends Collection<T>> C mapAndAdd(C c, Matcher matcher, FunctionalInterfaces.Function<Matcher, T> fun) {
        while (matcher.find()) {
            c.add(fun.apply(matcher));
        }
        return c;
    }
}
