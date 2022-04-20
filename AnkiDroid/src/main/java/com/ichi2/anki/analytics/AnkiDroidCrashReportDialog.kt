/***************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 * Copyright (c) 2020 Mike Hardy <github@mikehardy.net>                                 *
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
package com.ichi2.anki.analytics

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import org.acra.config.ACRAConfigurationException
import org.acra.config.DialogConfiguration
import org.acra.config.DialogConfigurationBuilder
import org.acra.dialog.CrashReportDialog
import org.acra.dialog.CrashReportDialogHelper
import timber.log.Timber

/**
 * This file will appear to have static type errors because BaseCrashReportDialog extends android.support.XXX
 * instead of androidx.XXX . Details at {@see https://github.com/ankidroid/Anki-Android/wiki/Crash-Reports}
 */
@SuppressLint("Registered") // we are sufficiently registered in this special case
class AnkiDroidCrashReportDialog : CrashReportDialog(), DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    private var mAlwaysReportCheckBox: CheckBox? = null
    private var mUserComment: EditText? = null
    private var mHelper: CrashReportDialogHelper? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dialogBuilder = AlertDialog.Builder(this)
        try {
            val builder = AnkiDroidApp.getInstance()!!.getAcraCoreConfigBuilder()
            val dialogConfig = builder.getPluginConfigurationBuilder(DialogConfigurationBuilder::class.java).build() as DialogConfiguration
            dialogBuilder.setIcon(dialogConfig.resIcon())
            dialogBuilder.setTitle(dialogConfig.title())
            dialogBuilder.setPositiveButton(dialogConfig.positiveButtonText(), this@AnkiDroidCrashReportDialog)
            dialogBuilder.setNegativeButton(dialogConfig.negativeButtonText(), this@AnkiDroidCrashReportDialog)
        } catch (ace: ACRAConfigurationException) {
            Timber.e(ace, "Unable to initialize ACRA while creating ACRA dialog?")
        }
        mHelper = CrashReportDialogHelper(this, intent)
        dialogBuilder.setView(buildCustomView(savedInstanceState))
        val dialog = dialogBuilder.create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener(this)
        dialog.show()
    }

    /**
     * Build the custom view used by the dialog
     */
    override fun buildCustomView(savedInstanceState: Bundle?): View {
        val preferences = AnkiDroidApp.getSharedPrefs(this)
        val inflater = layoutInflater
        @SuppressLint("InflateParams") val rootView = // when you inflate into an alert dialog, you have no parent view
            inflater.inflate(R.layout.feedback, null)
        mAlwaysReportCheckBox = rootView.findViewById(R.id.alwaysReportCheckbox)
        mAlwaysReportCheckBox?.isChecked = preferences.getBoolean("autoreportCheckboxValue", true)
        mUserComment = rootView.findViewById(R.id.etFeedbackText)
        // Set user comment if reloading after the activity has been stopped
        if (savedInstanceState != null) {
            val savedValue = savedInstanceState.getString(STATE_COMMENT)
            if (savedValue != null) {
                mUserComment?.setText(savedValue)
            }
        }
        return rootView
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            // Next time don't tick the auto-report checkbox by default
            val autoReport = mAlwaysReportCheckBox!!.isChecked
            val preferences = AnkiDroidApp.getSharedPrefs(this)
            preferences.edit().putBoolean("autoreportCheckboxValue", autoReport).apply()
            // Set the autoreport value to true if ticked
            if (autoReport) {
                preferences.edit().putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, AnkiDroidApp.FEEDBACK_REPORT_ALWAYS).apply()
                AnkiDroidApp.getInstance()?.setAcraReportingMode(AnkiDroidApp.FEEDBACK_REPORT_ALWAYS)
            }
            // Send the crash report
            mHelper!!.sendCrash(mUserComment!!.text.toString(), "")
        } else {
            // If the user got to the dialog, they were not limited.
            // The limiter persists it's limit info *before* the user cancels.
            // Therefore, on cancel, purge limits to make sure the user may actually send in future.
            // Better to maybe send to many reports than definitely too few.
            AnkiDroidApp.deleteACRALimiterData(this)
            mHelper!!.cancelReports()
        }
        finish()
    }

    override fun onDismiss(dialog: DialogInterface) {
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mUserComment != null && mUserComment!!.text != null) {
            outState.putString(STATE_COMMENT, mUserComment!!.text.toString())
        }
    }

    companion object {
        private const val STATE_COMMENT = "comment"
    }
}
