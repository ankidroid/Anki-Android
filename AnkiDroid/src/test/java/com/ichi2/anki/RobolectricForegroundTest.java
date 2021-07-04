package com.ichi2.anki;

public class RobolectricForegroundTest extends RobolectricAbstractTest {
    public void setUp() {
        runTasksInForeground();
        super.setUp();
    }
}
