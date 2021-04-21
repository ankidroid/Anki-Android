package com.ichi2.anki.stats;

import android.os.AsyncTask;
import android.util.Pair;
import android.widget.TextView;

import com.ichi2.libanki.Collection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

import java.util.concurrent.ExecutionException;

import static org.mockito.Mockito.*;
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;
import static org.robolectric.shadows.ShadowLooper.shadowMainLooper;

@RunWith(RobolectricTestRunner.class)
@LooperMode(PAUSED)
public class AnkiStatsTaskHandlerTest {

    @Mock
    private Collection col;

    @Mock
    private TextView view;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(col.getDb()).thenReturn(null);
    }

    @Test
    public void testCreateReviewSummaryStatistics() throws ExecutionException, InterruptedException {
        Mockito.verify(col, atMost(0)).getDb();
        AsyncTask<Pair<Collection, TextView>, Void, String> result = AnkiStatsTaskHandler
                .createReviewSummaryStatistics(col, view);

        result.get();
        shadowMainLooper().idle();

        Mockito.verify(col, atLeast(1)).getDb();
    }
}
