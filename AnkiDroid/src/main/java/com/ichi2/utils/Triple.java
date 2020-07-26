package com.ichi2.utils;

/**
 * Copied from Pair code*/

import java.util.Objects;

import androidx.core.util.ObjectsCompat;

/**
 * Container to ease passing around a tuple of two objects. This object provides a sensible
 * implementation of equals(), returning true if equals() is true on each of the contained
 * objects.
 */
public class Triple<F, S, T> {
    public final F first;
    public final S second;
    public final T third;

    /**
     * Constructor for a Pair.
     *
     * @param first the first object in the Triple
     * @param second the second object in the Triple
     * @param second the second object in the Triple
     */
    public Triple(F first, S second, T third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    /**
     * Checks the two objects for equality by delegating to their respective
     * {@link Object#equals(Object)} methods.
     *
     * @param o the {@link android.util.Pair} to which this one is to be checked for equality
     * @return true if the underlying objects of the Pair are both considered
     *         equal
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Triple)) {
            return false;
        }
        Triple<?, ?, ?> p = (Triple<?, ?, ?>) o;
        return ObjectsCompat.equals(p.first, first) && ObjectsCompat.equals(p.second, second) && ObjectsCompat.equals(p.third, third);
    }

    /**
     * Compute a hash code using the hash codes of the underlying objects
     *
     * @return a hashcode of the Pair
     */
    @Override
    public int hashCode() {
        return (first == null ? 0 : first.hashCode()) ^ (second == null ? 0 : second.hashCode()) ^ (third == null ? 0 : third.hashCode());
    }

    @Override
    public String toString() {
        return "Third{" + String.valueOf(first) + " " + String.valueOf(second) + " " + String.valueOf(third) + "}";
    }

    /**
     * Convenience method for creating an appropriately typed triple.
     * @param a the first object in the Triple
     * @param b the second object in the triple
     * @return a Pair that is templatized with the types of a and b
     */
    public static <A, B, C> Triple<A, B, C> create(A a, B b, C c) {
        return new Triple<A, B, C>(a, b, c);
    }
}
