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

package com.ichi2.anki.lint.utils;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.JavaContext;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;

import java.util.Collections;
import java.util.List;

public abstract class ImportStatementDetector extends Detector {

    public abstract void visitImportStatement(@NonNull JavaContext context, @NonNull UImportStatement node);


    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UImportStatement.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                // do not call super
                ImportStatementDetector.this.visitImportStatement(context, node);
            }
        };
    }
}
