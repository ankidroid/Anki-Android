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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

public class ErrorReporter extends Activity {
	protected static String REPORT_ASK = "2";
	protected static String REPORT_NEVER = "1";
	protected static String REPORT_ALWAYS = "0";
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(AnkiDroidApp.TAG, "OnCreate");

        super.onCreate(savedInstanceState);
        Context context = getBaseContext();
        SharedPreferences sharedPreferences = PrefSettings.getSharedPrefs(context);
        String reportErrorMode = sharedPreferences.getString("reportErrorMode", REPORT_ASK);

        if (reportErrorMode.equals(REPORT_ALWAYS)) { // Always report
            try {
                sendErrorReport();
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, e.toString());
            }

            deleteFiles();
            setResult(RESULT_OK);
            finish();

            return;
        } else if (reportErrorMode.equals(REPORT_NEVER)) { // Never report
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

            tvErrorText.setText(getResources().getQuantityString(R.plurals.error_message, numErrors, numErrors));
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

        for (String filename : files) {
            try {
            	Date ts = new Date();
            	TimeZone tz = TimeZone.getDefault();
            	String singleLine;
            	
                SimpleDateFormat df1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
                SimpleDateFormat df2 = new SimpleDateFormat("Z", Locale.US);
                
                df1.setTimeZone(TimeZone.getTimeZone("UTC"));
                
                String reportsentutc = String.format("%s", df1.format(ts));
                String reportsenttzoffset = String.format("%s", df2.format(ts));
                String reportsenttz = String.format("%s", tz.getID());
                
            	BufferedReader br = new BufferedReader(new InputStreamReader(openFileInput(filename)));
                List<NameValuePair> pairs = new ArrayList<NameValuePair>();
                
                pairs.add(new BasicNameValuePair("reportsentutc", reportsentutc));
                pairs.add(new BasicNameValuePair("reportsenttzoffset", reportsenttzoffset));
                pairs.add(new BasicNameValuePair("reportsenttz", reportsenttz));
                
                while((singleLine=br.readLine())!=null) {
                	int indexOfEquals = singleLine.indexOf('=');
                	
                	if(indexOfEquals==-1)
                		continue;
                	
                	String key = singleLine.substring(0, indexOfEquals).toLowerCase();
                	String value = singleLine.substring(indexOfEquals+1,singleLine.length());
                	
                	if(key.equals("stacktrace")) {
                		StringBuilder sb = new StringBuilder(value);
                		
                		while((singleLine=br.readLine())!=null) {
                			sb.append(singleLine);
                			sb.append("\n");
                		}
                		
                		value = sb.toString();
                	}
                	
                	pairs.add(new BasicNameValuePair(key, value));
                }

                br.close();
                
                postReport(pairs);
                
            } catch (Exception ex) {
                Log.e(AnkiDroidApp.TAG, ex.toString());
            }
        }
    }


    Connection.TaskListener sendListener = new Connection.TaskListener() {

        @Override
        public void onDisconnected() {
            // TODO: Inform the user that the connection was lost before the post was completed
        }

        @Override
        public void onPostExecute(Payload data) {
            Log.i(AnkiDroidApp.TAG, "Send error report finished.");
            // TODO: Report success/failure and any server side message
        }

        @Override
        public void onPreExecute() {
            // pass
            
        }

        @Override
        public void onProgressUpdate(Object... values) {
            // pass, no progress update while posting
        }
    };

    private void postReport(List<NameValuePair> values) {
        final String url = getString(R.string.error_post_url);
        
        Connection.sendErrorReport(sendListener, new Connection.Payload(new Object[] {url, values}));
    }
}
