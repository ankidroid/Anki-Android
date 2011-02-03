/***************************************************************************************
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ErrorReporter extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Log.i(AnkiDroidApp.TAG, "OnCreate");

        super.onCreate(savedInstanceState);
        Context context = getBaseContext();
        SharedPreferences sharedPreferences = PrefSettings.getSharedPrefs(context);
        String reportErrorMode = sharedPreferences.getString("reportErrorMode", "2");

        if (reportErrorMode.equals("0")) { // Always report
            try {
                sendErrorReport();
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, e.toString());
            }

            deleteFiles();
            setResult(RESULT_OK);
            finish();

            return;
        } else if (reportErrorMode.equals("1")) { // Never report
            deleteFiles();
            setResult(RESULT_OK);
            finish();

            return;
        } else { // Prompt, default behaviour
            setContentView(R.layout.email_error);

            int numErrors = getErrorFiles().size();

            TextView tvErrorText = (TextView) findViewById(R.id.tvErrorText);
            Button btnOk = (Button) findViewById(R.id.btnSendEmail);
            Button btnCancel = (Button) findViewById(R.id.btnIgnoreError);

            btnOk.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        sendErrorReport();
                    } catch (Exception e) {
                        Log.e(AnkiDroidApp.TAG, e.toString());
                    }

                    deleteFiles();
                    setResult(RESULT_OK);
                    finish();
                }
            });

            btnCancel.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteFiles();
                    setResult(RESULT_OK);
                    finish();
                }
            });

            String errorText;
            if (numErrors == 1) {
                errorText = getString(R.string.error_message);
            } else {
                errorText = String.format(getString(R.string.errors_message), numErrors);
            }
            tvErrorText.setText(errorText);
        }
    }


    private ArrayList<String> getErrorFiles() {
        ArrayList<String> files = new ArrayList<String>();
        String[] errors = fileList();

        for (String file : errors) {
            if (file.endsWith(".stacktrace")) {
                files.add(file);
            }
        }

        return files;
    }


    private void deleteFiles() {
        ArrayList<String> files = getErrorFiles();

        for (String file : files) {
            try {
                deleteFile(file);
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, String.format("Could not delete file: %s", file));
            }
        }
    }


    private void sendErrorReport() throws IOException {
        ArrayList<String> files = getErrorFiles();
        StringBuilder report = new StringBuilder();
        int count = 1;

        for (String filename : files) {
            try {
                report.append(String.format("--> BEGIN REPORT %d <--\n", count));

                FileInputStream fi = openFileInput(filename);

                if (fi == null) {
                    continue;
                }

                int ch;

                while ((ch = fi.read()) != -1) {
                    report.append((char) ch);
                }

                fi.close();

                report.append(String.format("--> END REPORT %d <--", count++));
            } catch (Exception ex) {
                Log.e(AnkiDroidApp.TAG, ex.toString());
            }
        }

        sendEmail(report.toString());
    }


    private void sendEmail(String body) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        SimpleDateFormat df1 = new SimpleDateFormat("EEE MMM dd HH:mm:ss ", Locale.US);
        SimpleDateFormat df2 = new SimpleDateFormat(" yyyy", Locale.US);
        Date ts = new Date();
        TimeZone tz = TimeZone.getDefault();
        String subject = String.format("Bug Report on %s%s%s", df1.format(ts), tz.getID(), df2.format(ts));
        sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { getString(R.string.error_email) });
        sendIntent.putExtra(Intent.EXTRA_TEXT, body);
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        sendIntent.setType("message/rfc822");

        startActivity(Intent.createChooser(sendIntent, "Send Error Report"));
    }
}
