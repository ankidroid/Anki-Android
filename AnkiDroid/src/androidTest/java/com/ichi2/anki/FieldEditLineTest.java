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

import android.Manifest;
import android.content.Intent;
import android.os.Parcelable;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(AndroidJUnit4.class)
public class FieldEditLineTest {

    @Rule
    public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Rule public ActivityScenarioRule<NoteEditor> activityRule = new ActivityScenarioRule<>(getNoteEditorIntent());

    @NonNull
    private Intent getNoteEditorIntent() {
        Intent intent = new Intent();
        intent.setClass(AnkiDroidApp.getInstance(), NoteEditor.class);
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER);
        return intent;
    }


    @Test
    public void testSetters() {
        FieldEditLine line = getFieldEditLine();

        line.setContent("Hello");
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

        toSave.setContent("Hello");
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
        activityRule.getScenario().onActivity(a -> l.set(new FieldEditLine(a)));
        return l.get();
    }
}
