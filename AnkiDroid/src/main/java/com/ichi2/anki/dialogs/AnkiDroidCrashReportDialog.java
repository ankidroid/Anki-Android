/***************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.anki.dialogs;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import org.acra.config.ACRAConfigurationException;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfiguration;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.dialog.BaseCrashReportDialog;

import timber.log.Timber;

public class AnkiDroidCrashReportDialog extends BaseCrashReportDialog implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    private static final String STATE_COMMENT = "comment";
    CheckBox mAlwaysReportCheckBox;
    EditText mUserComment;

    AlertDialog mDialog;

    @Override
    protected void init(Bundle savedInstanceState) {
        super.init(savedInstanceState);

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        try {
            CoreConfigurationBuilder builder = AnkiDroidApp.getInstance().getAcraCoreConfigBuilder();
            DialogConfiguration dialogConfig =
                    (DialogConfiguration)builder.getPluginConfigurationBuilder((DialogConfigurationBuilder.class)).build();

            dialogBuilder.setPositiveButton(dialogConfig.resPositiveButtonText(), AnkiDroidCrashReportDialog.this);
            dialogBuilder.setNegativeButton(dialogConfig.resNegativeButtonText(), AnkiDroidCrashReportDialog.this);
        }
        catch (ACRAConfigurationException ace) {
            Timber.e(ace, "Unable to initialize ACRA while creating ACRA dialog?");
        }
        dialogBuilder.setView(buildCustomView(savedInstanceState));

        mDialog = dialogBuilder.create();
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.setOnDismissListener(this);
        mDialog.show();
    }

    /**
     * Build the custom view used by the dialog
     * @param savedInstanceState
     * @return
     */
    private View buildCustomView(Bundle savedInstanceState) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(this);
        LayoutInflater inflater = getLayoutInflater();
        View rootView = inflater.inflate(R.layout.feedback, null);
        mAlwaysReportCheckBox = (CheckBox) rootView.findViewById(R.id.alwaysReportCheckbox);
        mAlwaysReportCheckBox.setChecked(preferences.getBoolean("autoreportCheckboxValue", true));
        mUserComment = (EditText) rootView.findViewById(R.id.etFeedbackText);
        // Set user comment if reloading after the activity has been stopped
        if (savedInstanceState != null) {
            String savedValue = savedInstanceState.getString(STATE_COMMENT);
            if (savedValue != null) {
                mUserComment.setText(savedValue);
            }
        }
        return rootView;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            // Next time don't tick the auto-report checkbox by default
            boolean autoReport = mAlwaysReportCheckBox.isChecked();
            SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(this);
            preferences.edit().putBoolean("autoreportCheckboxValue", autoReport).commit();
            // Set the autoreport value to true if ticked
            if (autoReport) {
                preferences.edit().putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, AnkiDroidApp.FEEDBACK_REPORT_ALWAYS).commit();
                AnkiDroidApp.getInstance().setAcraReportingMode(AnkiDroidApp.FEEDBACK_REPORT_ALWAYS);
            }
            // Send the crash report
            sendCrash(mUserComment.getText().toString(), "");
        } else {
            cancelReports();
        }

        finish();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mUserComment != null && mUserComment.getText() != null) {
            outState.putString(STATE_COMMENT, mUserComment.getText().toString());
        }
    }
}
