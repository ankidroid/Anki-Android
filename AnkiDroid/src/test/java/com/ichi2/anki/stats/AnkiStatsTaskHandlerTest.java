/****************************************************************************************
 * Copyright (c) 2021 Rodrigo Silva <dev,rodrigosp@gmail.com>                              *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.stats;

import android.os.AsyncTask;
import android.util.Pair;
import android.widget.TextView;

import com.ichi2.anki.RobolectricTest;
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

@RunWith(AndroidJUnit4.class)
public class AnkiStatsTaskHandlerTest extends RobolectricTest {

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
        advanceRobolectricLooper();

        Mockito.verify(col, atLeast(1)).getDb();
    }
}
