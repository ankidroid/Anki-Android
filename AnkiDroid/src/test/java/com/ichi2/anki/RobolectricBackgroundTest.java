package com.ichi2.anki;

public class RobolectricBackgroundTest extends RobolectricAbstractTest {
    public void setUp() {
        runTasksInBackground();
        super.setUp();
    }
}
