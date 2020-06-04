/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

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

package androidx.test.runner;

import android.app.Instrumentation;
import android.os.Bundle;

import androidx.annotation.VisibleForTesting;
import androidx.multidex.MultiDex;
import androidx.test.internal.runner.MultiDexTestRequestBuilder;
import androidx.test.internal.runner.TestRequestBuilder;

/**
 * A class which allows loading MultiDex tests on Android < 21
 *
 * This is in androidx to override package-level functions
 */
@SuppressWarnings("unused") //referenced by build.gradle
public class MultiDexJUnitRunner extends AndroidJUnitRunner {
    // TODO: See if we can check the number of executed tests on a full run, and ensure this exceeds a threshold.
    // as instrumentation runs on my local machine occasionally fail to load com.ichi2.anki

    @Override
    public void onCreate(Bundle arguments) {
        MultiDex.installInstrumentation(getContext(), getTargetContext());
        super.onCreate(arguments);
    }


    @VisibleForTesting
    @Override
    TestRequestBuilder createTestRequestBuilder(Instrumentation instr, Bundle arguments) {
        return new MultiDexTestRequestBuilder(instr, arguments);
    }
}