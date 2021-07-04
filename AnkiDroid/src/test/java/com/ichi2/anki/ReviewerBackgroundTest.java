package com.ichi2.anki;

import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.anki.exception.ConfirmModSchemaException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

import java.util.Arrays;

import androidx.test.core.app.ActivityScenario;
import timber.log.Timber;

import static com.ichi2.anki.AbstractFlashcardViewer.RESULT_DEFAULT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class ReviewerBackgroundTest extends RobolectricTest {
    @ParameterizedRobolectricTestRunner.Parameter
    public int schedVersion;

    @ParameterizedRobolectricTestRunner.Parameters(name = "SchedV{0}")
    public static java.util.Collection<Object[]> initParameters() {
        // This does one run with schedVersion injected as 1, and one run as 2
        return Arrays.asList(new Object[][] { { 1 }, { 2 } });
    }

    @Before
    @Override
    public void setUp() {
        super.setUp();
        try {
            Timber.d("scheduler version is %d", schedVersion);
            getCol().changeSchedulerVer(schedVersion);
        } catch (ConfirmModSchemaException e) {
            throw new RuntimeException("Could not change schedVer", e);
        }
    }


    @Test
    public void verifyNormalStartup() {
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> assertNotNull("Collection should be non-null", reviewer.getCol()));
        }
    }

    @Test
    public void exitCommandWorksAfterControlsAreBlocked() {
        ensureCollectionLoadIsSynchronous();
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> {
                reviewer.blockControls(true);
                reviewer.executeCommand(ViewerCommand.COMMAND_EXIT);
            });
            assertThat(scenario.getResult().getResultCode(), is(RESULT_DEFAULT));
        }
    }
}
