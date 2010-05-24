package com.ichi2.anki;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class ErrorReporter extends Activity {
	public static String TAG = "ErrorReporter";

	private ArrayList<String> getErrorFiles() {
		ArrayList<String> files = new ArrayList<String>();
		String[] errors = fileList();

		for (String file : errors) {
			if (file.endsWith(".stacktrace"))
				files.add(file);
		}

		return files;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "OnCreate");

		super.onCreate(savedInstanceState);
		setContentView(R.layout.email_error);

		int noErrors = getErrorFiles().size();

		TextView tvErrorText = (TextView) findViewById(R.id.tvErrorText);
		Button btnOk = (Button) findViewById(R.id.btnSendEmail);
		Button btnCancel = (Button) findViewById(R.id.btnIgnoreError);

		btnOk.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				try {
					sendErrorReport();
				} catch (Exception e) {
					Log.e(TAG, e.toString());
				}

				deleteFiles();
				setResult(RESULT_OK);
				finish();
			}
		});

		btnCancel.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				deleteFiles();
				setResult(RESULT_OK);
				finish();
			}
		});

		String errorText = String.format(getString(R.string.error_message), noErrors);
		tvErrorText.setText(errorText);
	}
	
	private void deleteFiles() {
		ArrayList<String> files = getErrorFiles();
		
		for(String file : files) {
			try {
				deleteFile(file);
			}
			catch(Exception e) {
				Log.e(TAG, String.format("Could not delete file: %s", file));
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

				if (fi == null)
					continue;

				int ch;

				while ((ch = fi.read()) != -1) {
					report.append((char) ch);
				}

				fi.close();
				
				report.append(String.format("--> END REPORT %d <--", count++));
			} catch (Exception ex) {
				Log.e(TAG, ex.toString());
			}
		}

		sendEmail(report.toString());
	}
	
	private void sendEmail(String body) {
		Intent sendIntent = new Intent(Intent.ACTION_SEND);
		String subject = String.format("Bug Report on %s", new Date());
		sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {getString(R.string.error_email)});
		sendIntent.putExtra(Intent.EXTRA_TEXT, body);
		sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
		sendIntent.setType("message/rfc822");
		
		startActivity( Intent.createChooser(sendIntent, "Send Error Report") );
	}
}
