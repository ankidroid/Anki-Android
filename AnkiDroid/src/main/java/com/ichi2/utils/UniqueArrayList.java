/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils;


import org.apache.commons.collections4.list.SetUniqueList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.TreeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * A collection of items that doesn't allow duplicate items, and allows fast random access, lookup, maintaining order, and sorting.
 * <p>
 * The {@link List<E>} interface makes certain assumptions/requirements. This
 * implementation breaks these in certain ways, but this is merely the result of
 * rejecting duplicates. Each violation is explained in the method, but it
 * should not affect you.
 *
 * This class does also implement the {@link Set<E>} interface, and as a result
 * you should bear in mind that Sets require immutable objects to function correctly.
 *
 * @implNote The implementation of this class extends {@link SetUniqueList<E>} and adds the ability to define a comparator
 * to be used to judge uniqueness of elements, and allows sorting.
 * <p>
 * Data structures used internally:
 * - {@link ArrayList<E>} to enable fast random access, sorting, and maintaining order of items during iteration
 * - {@link TreeSet<E>} if a comparator is given or a {@link HashSet<E>} otherwise.
 */
public class UniqueArrayList<E> extends SetUniqueList<E> implements List<E>, Set<E> {

    /**
     * Internal list used in {@link SetUniqueList<E>} implementation.
     * <p>
     * This is the same list as the one used internally in {@link SetUniqueList<E>}. We keep a reference to it here in
     * order to be able to sort it. {@link SetUniqueList<E>} implementation needs to make sure the internal {@link Set}
     * and {@link List} don't get out of sync, and {@link SetUniqueList<E>} cannot be sorted via {@link Collections#sort(List)}
     * or {@link SetUniqueList#sort(Comparator)} both will throw an exception, due to a limitation on this class {@link java.util.ListIterator}
     *
     * Sorting can be only done via {@link UniqueArrayList#sort()} or {@link UniqueArrayList#sort(Comparator)}.
     *
     * Modification to this list reference should be done with cautious to avoid having the internal {@link Set} out of sync
     */
    private final List<E> mList;


    /**
     * Constructor that wraps (not copies) the List and specifies the set to use.
     * <p>
     * The set and list must both be correctly initialised to the same elements.
     *
     * @param set  the set to decorate, must not be null
     * @param list  the list to decorate, must not be null
     * @throws NullPointerException if set or list is null
     */
    protected UniqueArrayList(final List<E> list, final Set<E> set) {
        super(list, set);
        mList = list;
    }


    /**
     * Constructs a new empty {@link UniqueArrayList}
     */
    public UniqueArrayList() {
        this(new ArrayList<>(), new HashSet<>());
    }


    /**
     * Constructs a new {@link UniqueArrayList} containing the elements of the specified collection
     *
     * @param source the source collection that will be used to construct UniqueArrayList
     */
    public static <E> UniqueArrayList<E> from(final List<E> source) {
        return UniqueArrayList.from(source, null);
    }

    /**
     * Constructs a new {@link UniqueArrayList} containing the elements of the specified collection, with an optional
     * comparator to be used to judge uniqueness.
     *
     * @implNote Modified implantation of {@link SetUniqueList#setUniqueList(List)} to :
     * - support using comparators to check for uniqueness.
     * - make a copy of the list passed.
     *
     * @param source the source collection that will be used to construct UniqueArrayList
     * @param comparator used to judge uniqueness
     */
    public static <E> UniqueArrayList<E> from(@NonNull List<E> source, @Nullable Comparator<? super E> comparator) {
        if (source == null) {
            throw new NullPointerException("List must not be null");
        }

        Set<E> set;
        if (comparator == null) {
            set = new HashSet<>();
        } else {
            set = new TreeSet<>(comparator);
        }

        final UniqueArrayList<E> sl = new UniqueArrayList<>(new ArrayList<>(), set);

        sl.addAll(source);

        return sl;
    }


    /**
     * Sorts the list into ascending order, according to the
     * {@linkplain Comparable natural ordering} of its elements.
     * All elements in the list must implement the {@link Comparable}
     * interface.  Furthermore, all elements in the list must be
     * <i>mutually comparable</i> (that is, {@code e1.compareTo(e2)}
     * must not throw a {@code ClassCastException} for any elements
     * {@code e1} and {@code e2} in the list).
     *
     * @see #sort(Comparator)
     */
    public void sort() {
        sort(null);
    }

    /**
     * Sorts the list according to the order induced by the specified comparator.
     * All elements in the list must be <i>mutually comparable</i> using the
     * specified comparator (that is, {@code c.compare(e1, e2)} must not throw
     * a {@code ClassCastException} for any elements {@code e1} and {@code e2}
     * in the list).
     *
     * <p>This sort is guaranteed to be <i>stable</i>:  equal elements will
     * not be reordered as a result of the sort.
     *
     * <p>The specified list must be modifiable, but need not be resizable.
     *
     * @implNote
     * DO NOT call {@link Collections#sort(List, Comparator)} using this list directly
     * this can throw due to a limitation on setting items on the  {@link ListIterator}
     * returned by {@link #listIterator()}
     *
     * @param  c the comparator to determine the order of the list.  A
     *        {@code null} value indicates that the elements' <i>natural
     *        ordering</i> should be used.
     *
     * @see Collections#sort(List, Comparator)
     */
    @Override
    public void sort(@Nullable Comparator<? super E> c) {
        Object[] elements = mList.toArray();
        Arrays.sort(elements, (Comparator) c);
        ListIterator<E> i = mList.listIterator();
        for (Object element : elements) {
            i.next();
            i.set((E) element);
        }
    }


    @NonNull
    @Override
    @RequiresApi(24)
    public Spliterator<E> spliterator() {
        return super.spliterator();
    }
}
