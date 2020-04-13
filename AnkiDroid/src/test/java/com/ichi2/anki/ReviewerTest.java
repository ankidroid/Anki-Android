package com.ichi2.anki;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReviewerTest extends RobolectricTest {

    @Test
    public void verifyStartupNoCollection() {
        try (ActivityScenario<NullCollectionReviewer> scenario = ActivityScenario.launch(NullCollectionReviewer.class)) {
            scenario.onActivity(reviewer -> assertNull("Collection should have been null", reviewer.getCol()));
        }
    }

    @Test
    public void verifyNormalStartup() {
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> assertNotNull("Collection should be non-null", reviewer.getCol()));
        }
    }
}

