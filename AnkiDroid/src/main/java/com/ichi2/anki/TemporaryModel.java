/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>                                   *
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

package com.ichi2.anki;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import timber.log.Timber;

import com.ichi2.compat.CompatHelper;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;


@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes"})
public class TemporaryModel {

    public static final String INTENT_MODEL_FILENAME = "editedModelFilename";
    private ArrayList<Object[]> mTemplateChanges = new ArrayList<>();
    private String mEditedModelFileName = null;
    private JSONObject mEditedModel;


    public TemporaryModel(JSONObject model) {
        Timber.d("Constructor called with model");
        mEditedModel = model;
    }


    public static TemporaryModel fromBundle(Bundle bundle) {
        String mEditedModelFileName = bundle.getString(INTENT_MODEL_FILENAME);
        // Bundle.getString is @Nullable, so we have to check.
        // If we return null then onCollectionLoaded() just will load original from database
        if (mEditedModelFileName == null) {
            Timber.d("fromBundle() - model file name under key %s", INTENT_MODEL_FILENAME);
            return null;
        }

        Timber.d("onCreate() loading saved model file %s", mEditedModelFileName);
        TemporaryModel model = new TemporaryModel((getTempModel(mEditedModelFileName)));
        return model;
    }


    public Bundle toBundle() {
        Bundle outState = new Bundle();
        outState.putString(INTENT_MODEL_FILENAME,
                saveTempModel(AnkiDroidApp.getInstance().getApplicationContext(), mEditedModel));
        return outState;
    }


    public JSONObject getModel() {
        return mEditedModel;
    }


    public void setEditedModelFileName(String fileName) {
        mEditedModelFileName = fileName;
    }


    public String getEditedModelFileName() {
        return mEditedModelFileName;
    }


    /**
     * Save the current model to a temp file in the application internal cache directory
     * @return String representing the absolute path of the saved file, or null if there was a problem
     */
    public static @Nullable
    String saveTempModel(@NonNull Context context, @NonNull JSONObject tempModel) {
        Timber.d("saveTempModel() saving tempModel");
        File tempModelFile;
        try (ByteArrayInputStream source = new ByteArrayInputStream(tempModel.toString().getBytes())) {
            tempModelFile = File.createTempFile("editedTemplate", ".json", context.getCacheDir());
            CompatHelper.getCompat().copyFile(source, tempModelFile.getAbsolutePath());
        } catch (IOException ioe) {
            Timber.e(ioe, "Unable to create+write temp file for model");
            return null;
        }
        return tempModelFile.getAbsolutePath();
    }


    /**
     * Get the model temporarily saved into the file represented by the given path
     * @return JSONObject holding the model, or null if there was a problem
     */
    public static @Nullable JSONObject getTempModel(@NonNull String tempModelFileName) {
        Timber.d("getTempModel() fetching tempModel %s", tempModelFileName);
        try (ByteArrayOutputStream target = new ByteArrayOutputStream()) {
            CompatHelper.getCompat().copyFile(tempModelFileName, target);
            return new JSONObject(target.toString());
        } catch (IOException | JSONException e) {
            Timber.e(e, "Unable to read+parse tempModel from file %s", tempModelFileName);
            return null;
        }
    }


    /** Clear any temp model files saved into internal cache directory */
    public static int clearTempModelFiles() {
        int deleteCount = 0;
        for (File c : AnkiDroidApp.getInstance().getCacheDir().listFiles()) {
            if (c.getAbsolutePath().endsWith("json") && c.getAbsolutePath().contains("editedTemplate")) {
                if (!c.delete()) {
                    Timber.w("Unable to delete temp file %s", c.getAbsolutePath());
                } else {
                    deleteCount++;
                    Timber.d("Deleted temp model file %s", c.getAbsolutePath());
                }
            }
        }
        return deleteCount;
    }
}