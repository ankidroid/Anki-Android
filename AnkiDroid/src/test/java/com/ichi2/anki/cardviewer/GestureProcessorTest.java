/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.cardviewer;

import android.content.SharedPreferences;
import android.view.ViewConfiguration;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GestureProcessorTest implements ViewerCommand.CommandProcessor {

    private final GestureProcessor mSut = new GestureProcessor(this);
    private final List<ViewerCommand> mExecutedCommands = new ArrayList<>();

    @Override
    public boolean executeCommand(@NonNull ViewerCommand which) {
        this.mExecutedCommands.add(which);
        return true;
    }

    ViewerCommand singleResult() {
        assertThat(mExecutedCommands, hasSize(1));
        return mExecutedCommands.get(0);
    }

    private static MockedStatic<ViewConfiguration> utilities;

    @BeforeClass
    public static void before() {
        utilities = Mockito.mockStatic(ViewConfiguration.class);
        utilities.when(() -> ViewConfiguration.get(any())).thenReturn(mock(ViewConfiguration.class));
    }

    @AfterClass
    public static void after() {
        utilities.close();
    }

    @Test
    public void integrationTest() {
        SharedPreferences prefs = mock(SharedPreferences.class, Mockito.RETURNS_DEEP_STUBS);

        when(prefs.getString(nullable(String.class), nullable(String.class))).thenReturn("0");
        when(prefs.getString(eq("gestureTapCenter"), nullable(String.class))).thenReturn("1");
        when(prefs.getBoolean(eq("gestureCornerTouch"), ArgumentMatchers.anyBoolean())).thenReturn(true);

        mSut.init(prefs);

        mSut.onTap(100, 100, 50, 50);
        assertThat(singleResult(), is(ViewerCommand.COMMAND_SHOW_ANSWER));
    }
}
