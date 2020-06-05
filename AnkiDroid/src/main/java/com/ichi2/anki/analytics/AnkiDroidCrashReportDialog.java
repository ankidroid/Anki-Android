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

package com.ichi2.anki.analytics;

import android.annotation.SuppressLint;
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

/**
 * This file will appear to have static type errors because BaseCrashReportDialog extends android.support.XXX
 * instead of androidx.XXX . Details at {@see https://github.com/ankidroid/Anki-Android/wiki/Crash-Reports}
 */
@SuppressLint("Registered") // we are sufficiently registered in this special case
public class AnkiDroidCrashReportDialog extends BaseCrashReportDialog implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    private static final String STATE_COMMENT = "comment";
    private CheckBox mAlwaysReportCheckBox;
    private EditText mUserComment;

    @Override
    protected void init(Bundle savedInstanceState) {
        super.init(savedInstanceState);

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        try {
            CoreConfigurationBuilder builder = AnkiDroidApp.getInstance().getAcraCoreConfigBuilder();
            DialogConfiguration dialogConfig =
                    (DialogConfiguration)builder.getPluginConfigurationBuilder((DialogConfigurationBuilder.class)).build();

            dialogBuilder.setIcon(dialogConfig.resIcon());
            dialogBuilder.setTitle(dialogConfig.title());
            dialogBuilder.setPositiveButton(dialogConfig.positiveButtonText(), AnkiDroidCrashReportDialog.this);
            dialogBuilder.setNegativeButton(dialogConfig.negativeButtonText(), AnkiDroidCrashReportDialog.this);
        }
        catch (ACRAConfigurationException ace) {
            Timber.e(ace, "Unable to initialize ACRA while creating ACRA dialog?");
        }
        dialogBuilder.setView(buildCustomView(savedInstanceState));

        AlertDialog dialog = dialogBuilder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(this);
        dialog.show();
    }

    /**
     * Build the custom view used by the dialog
     */
    private View buildCustomView(Bundle savedInstanceState) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(this);
        LayoutInflater inflater = getLayoutInflater();
        @SuppressLint("InflateParams") // when you inflate into an alert dialog, you have no parent view
        View rootView = inflater.inflate(R.layout.feedback, null);
        mAlwaysReportCheckBox = rootView.findViewById(R.id.alwaysReportCheckbox);
        mAlwaysReportCheckBox.setChecked(preferences.getBoolean("autoreportCheckboxValue", true));
        mUserComment = rootView.findViewById(R.id.etFeedbackText);
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
            preferences.edit().putBoolean("autoreportCheckboxValue", autoReport).apply();
            // Set the autoreport value to true if ticked
            if (autoReport) {
                preferences.edit().putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, AnkiDroidApp.FEEDBACK_REPORT_ALWAYS).apply();
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
