/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.lint.rules;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.JavaContext;

import com.android.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UVariable;

import java.util.Collections;
import java.util.List;

public abstract class JavaFieldNamingPatternDetector extends Detector implements Detector.UastScanner {

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new VariableNamingHandler(context);
    }


    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UVariable.class);
    }

    private class VariableNamingHandler extends UElementHandler {

        private final JavaContext mContext;


        public VariableNamingHandler(JavaContext context) {
            this.mContext = context;
        }


        @Override
        public void visitVariable(@NonNull UVariable node) {
            // Only apply naming patterns to Java
            if (mContext.file.getAbsolutePath().endsWith(".kt")) {
                return;
            }

            // HACK: Using visitField didn't return any results
            if (!(node instanceof UField)) {
                return;
            }

            if (!isApplicable(node)) {
                return;
            }

            String variableName = node.getName();

            if (variableName == null) {
                return;
            }

            if (meetsNamingStandards(variableName)) {
                return;
            }

            reportVariable(mContext, node, variableName);
        }
    }


    /** If the lint check is applicable to the given variable */
    protected abstract boolean isApplicable(@NonNull UVariable variable);

    protected abstract boolean meetsNamingStandards(@NonNull String variableName);

    /** Report the problematic variable to the lint checker */
    protected abstract void reportVariable(@NonNull JavaContext context, @NonNull UVariable node, @NonNull String variableName);
}