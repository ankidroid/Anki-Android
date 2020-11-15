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

package com.ichi2.anki;

import android.os.Parcelable;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class FieldEditLineTest extends NoteEditorTest {

    @Test
    public void testSetters() {
        FieldEditLine line = getFieldEditLine();

        line.setContent("Hello", true);
        line.setName("Name");
        line.setOrd(5);
        FieldEditText text = line.getEditText();
        assertThat(text.getOrd(), is(5));
        assertThat(text.getText().toString(), is("Hello"));
        assertThat(line.getName(), is("Name"));
    }


    @Test
    public void testSaveRestore() {
        FieldEditLine toSave = getFieldEditLine();

        toSave.setContent("Hello", true);
        toSave.setName("Name");
        toSave.setOrd(5);

        Parcelable b = toSave.onSaveInstanceState();

        FieldEditLine restored = getFieldEditLine();
        restored.onRestoreInstanceState(b);

        FieldEditText text = restored.getEditText();
        assertThat(text.getOrd(), is(5));
        assertThat(text.getText().toString(), is("Hello"));
        assertThat(toSave.getName(), is("Name"));
    }


    @NonNull
    protected FieldEditLine getFieldEditLine() {
        AtomicReference<FieldEditLine> l = new AtomicReference<>();
        mActivityRule.getScenario().onActivity(a -> l.set(new FieldEditLine(a)));
        return l.get();
    }
}
