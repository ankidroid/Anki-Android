package com.ichi2.utils;

public class Triple<First, Second, Triple> {
    public final First first;
    public final Second second;
    public final Triple third;

    public Triple(First first, Second second, Triple third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }
}
