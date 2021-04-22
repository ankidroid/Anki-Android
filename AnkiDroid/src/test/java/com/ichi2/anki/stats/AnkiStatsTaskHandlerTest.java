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

import java.util.concurrent.ExecutionException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.mockito.Mockito.*;
import static org.robolectric.shadows.ShadowLooper.shadowMainLooper;

@RunWith(AndroidJUnit4.class)
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
