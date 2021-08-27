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

package com.ichi2.anki.noteeditor;

import android.content.Context;
import android.os.Bundle;
import android.util.Pair;
import android.util.SparseArray;
import android.view.AbsSavedState;
import android.view.View;

import com.ichi2.anki.FieldEditLine;
import com.ichi2.anki.NoteEditor;
import com.ichi2.anki.R;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Models;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;

import static com.ichi2.utils.MapUtil.getKeyByValue;

/** Responsible for recreating EditFieldLines after NoteEditor operations
 * This primarily exists so we can use saved instance state to repopulate the dynamically created FieldEditLine
 */
public class FieldState {

    private final NoteEditor mEditor;
    private List<View.BaseSavedState> mSavedFieldData;


    private FieldState(NoteEditor editor) {
        mEditor = editor;
    }

    private static boolean allowFieldRemapping(String[][] oldFields) {
        return oldFields.length > 2;
    }


    public static FieldState fromEditor(NoteEditor editor) {
        return new FieldState(editor);
    }


    @NonNull
    public List<FieldEditLine> loadFieldEditLines(FieldChangeType type) {
        List<FieldEditLine> fieldEditLines;
        if (type.mType == Type.INIT && mSavedFieldData != null) {
            fieldEditLines = recreateFieldsFromState();
            mSavedFieldData = null;
        } else {
            fieldEditLines = createFields(type);
        }
        for (FieldEditLine l : fieldEditLines) {
            l.setId(View.generateViewId());
        }

        if (type.mType == Type.CLEAR_KEEP_STICKY) {
            // we use the UI values here as the model will post-processing steps (newline -> br).
            String[] currentFieldStrings = mEditor.getCurrentFieldStrings();

            JSONArray flds = mEditor.getCurrentFields();
            for (int fldIdx = 0; fldIdx < flds.length(); fldIdx++) {
                if (flds.getJSONObject(fldIdx).getBoolean("sticky")) {
                    fieldEditLines.get(fldIdx).setContent(currentFieldStrings[fldIdx], type.mReplaceNewlines);
                }
            }
        }

        if (type.mType == Type.CHANGE_FIELD_COUNT) {
            String[] currentFieldStrings = mEditor.getCurrentFieldStrings();

            for (int i = 0; i < Math.min(currentFieldStrings.length, fieldEditLines.size()); i++) {
                fieldEditLines.get(i).setContent(currentFieldStrings[i], type.mReplaceNewlines);
            }
        }


        return fieldEditLines;
    }


    private List<FieldEditLine> recreateFieldsFromState() {
        List<FieldEditLine> editLines = new ArrayList<>(mSavedFieldData.size());
        for (AbsSavedState state : mSavedFieldData) {
            FieldEditLine edit_line_view = new FieldEditLine(mEditor);
            if (edit_line_view.getId() == 0) {
                edit_line_view.setId(View.generateViewId());
            }
            edit_line_view.loadState(state);
            editLines.add(edit_line_view);
        }
        return editLines;
    }


    @NonNull
    protected List<FieldEditLine> createFields(FieldChangeType type) {
        String[][] fields = getFields(type);

        List<FieldEditLine> editLines = new ArrayList<>(fields.length);
        for (int i = 0; i < fields.length; i++) {
            FieldEditLine edit_line_view = new FieldEditLine(mEditor);
            editLines.add(edit_line_view);
            edit_line_view.setName(fields[i][0]);
            edit_line_view.setContent(fields[i][1], type.mReplaceNewlines);
            edit_line_view.setOrd(i);
        }
        return editLines;
    }


    private String[][] getFields(FieldChangeType type) {
        if (type.mType == Type.REFRESH_WITH_MAP) {
            String[][] items = mEditor.getFieldsFromSelectedNote();
            Map<String, Pair<Integer, JSONObject>> fMapNew = Models.fieldMap(type.mNewModel);
            return FieldState.fromFieldMap(mEditor, items, fMapNew, type.mModelChangeFieldMap);
        }
        return mEditor.getFieldsFromSelectedNote();
    }


    private static String[][] fromFieldMap(Context context, String[][] oldFields, Map<String, Pair<Integer, JSONObject>> fMapNew, Map<Integer, Integer> modelChangeFieldMap) {
        // Build array of label/values to provide to field EditText views
        String[][] fields = new String[fMapNew.size()][2];
        for (String fname : fMapNew.keySet()) {
            Pair<Integer, JSONObject> fieldPair = fMapNew.get(fname);
            if (fieldPair == null) {
                continue;
            }
            // Field index of new note type
            Integer i = fieldPair.first;
            // Add values from old note type if they exist in map, otherwise make the new field empty
            if (modelChangeFieldMap.containsValue(i)) {
                // Get index of field from old note type given the field index of new note type
                Integer j = getKeyByValue(modelChangeFieldMap, i);
                if (j == null) {
                    continue;
                }
                // Set the new field label text
                if (allowFieldRemapping(oldFields)) {
                    // Show the content of old field if remapping is enabled
                    fields[i][0] = String.format(context.getResources().getString(R.string.field_remapping), fname, oldFields[j][0]);
                } else {
                    fields[i][0] = fname;
                }

                // Set the new field label value
                fields[i][1] = oldFields[j][1];
            } else {
                // No values from old note type exist in the mapping
                fields[i][0] = fname;
                fields[i][1] = "";
            }
        }
        return fields;
    }


    public void setInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }

        if (!savedInstanceState.containsKey("customViewIds") || !savedInstanceState.containsKey("android:viewHierarchyState")) {
            return;
        }

        ArrayList<Integer> customViewIds = savedInstanceState.getIntegerArrayList("customViewIds");
        Bundle viewHierarchyState = savedInstanceState.getBundle("android:viewHierarchyState");

        if (customViewIds == null || viewHierarchyState == null) {
            return;
        }

        SparseArray<?> views = (SparseArray<?>) viewHierarchyState.get("android:views");

        if (views == null) {
            return;
        }

        List<View.BaseSavedState> important = new ArrayList<>(customViewIds.size());
        for (Integer i : customViewIds) {
            important.add((View.BaseSavedState) views.get(i));
        }

        mSavedFieldData = important;
    }


    /** How fields should be changed when the UI is rebuilt */
    public static class FieldChangeType {
        private final Type mType;

        private Map<Integer, Integer> mModelChangeFieldMap;
        private Model mNewModel;
        private final boolean mReplaceNewlines;

        public FieldChangeType(Type type, boolean replaceNewlines) {
            this.mType = type;
            this.mReplaceNewlines = replaceNewlines;
        }

        public static FieldChangeType refreshWithMap(Model newModel, Map<Integer, Integer> modelChangeFieldMap, boolean replaceNewlines) {
            FieldChangeType typeClass = new FieldChangeType(Type.REFRESH_WITH_MAP, replaceNewlines);
            typeClass.mNewModel = newModel;
            typeClass.mModelChangeFieldMap = modelChangeFieldMap;
            return typeClass;
        }

        public static FieldChangeType refresh(boolean replaceNewlines) {
            return fromType(FieldState.Type.REFRESH, replaceNewlines);
        }


        public static FieldChangeType refreshWithStickyFields(boolean replaceNewlines) {
            return fromType(Type.CLEAR_KEEP_STICKY, replaceNewlines);
        }


        public static FieldChangeType changeFieldCount(boolean replaceNewlines) {
            return fromType(Type.CHANGE_FIELD_COUNT, replaceNewlines);
        }

        public static FieldChangeType onActivityCreation(boolean replaceNewlines) {
            return fromType(Type.INIT, replaceNewlines);
        }

        private static FieldChangeType fromType(Type type, boolean replaceNewlines) {
            return new FieldChangeType(type, replaceNewlines);
        }
    }

    public enum Type {
        INIT,
        CLEAR_KEEP_STICKY,
        CHANGE_FIELD_COUNT,
        REFRESH,
        REFRESH_WITH_MAP,
    }
}
