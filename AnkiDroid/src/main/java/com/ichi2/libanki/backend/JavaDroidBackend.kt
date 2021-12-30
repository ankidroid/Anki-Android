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

package com.ichi2.libanki.backend;

import android.content.Context;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.TemplateManager;
import com.ichi2.libanki.backend.exception.BackendNotSupportedException;
import com.ichi2.libanki.backend.model.SchedTimingToday;
import com.ichi2.libanki.utils.Time;

import net.ankiweb.rsdroid.RustCleanup;

import BackendProto.Backend;
import androidx.annotation.NonNull;

/**
 * A class which implements the Rust backend functionality in Java - this is to allow moving our current Java code to
 * the rust-based interface so we are able to perform regression testing against the converted interface
 *
 * This also allows an easy switch of functionality once we are happy that there are no regressions
 */
@RustCleanup("After the rust conversion is complete - this will be removed")
public class JavaDroidBackend implements DroidBackend {
    @Override
    public Collection createCollection(@NonNull Context context, @NonNull DB db, String path, boolean server, boolean log, @NonNull Time time) {
        return new Collection(context, db, path, server, log, time, this);
    }


    @Override
    public DB openCollectionDatabase(String path) {
        return new DB(path);
    }


    @Override
    public void closeCollection(DB db, boolean downgradeToSchema11) {
        db.close();
    }


    @Override
    public boolean databaseCreationCreatesSchema() {
        return false;
    }


    @Override
    public boolean databaseCreationInitializesData() {
        return false;
    }


    @Override
    public boolean isUsingRustBackend() {
        return false;
    }


    @Override
    public void debugEnsureNoOpenPointers() {
        // no-op
    }


    @Override
    public SchedTimingToday sched_timing_today(long createdSecs, int createdMinsWest, long nowSecs, int nowMinsWest, int rolloverHour) throws BackendNotSupportedException {
        throw new BackendNotSupportedException();
    }


    @Override
    public int local_minutes_west(long timestampSeconds) throws BackendNotSupportedException {
        throw new BackendNotSupportedException();
    }


    @Override
    public void useNewTimezoneCode(Collection col) {
        // intentionally blank - unavailable on Java backend
    }


    @Override
    public @NonNull Backend.ExtractAVTagsOut extract_av_tags(@NonNull String text, boolean question_side) throws BackendNotSupportedException {
        throw new BackendNotSupportedException();
    }


    @Override
    public @NonNull Backend.RenderCardOut renderCardForTemplateManager(@NonNull TemplateManager.TemplateRenderContext templateRenderContext) throws BackendNotSupportedException {
        throw new BackendNotSupportedException();
    }
}
