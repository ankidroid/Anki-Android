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
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.ichi2.utils.HttpUtility;
import com.tomgibara.android.veecheck.util.PrefSettings;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class ErrorReporter extends Activity {
    protected static String REPORT_ASK = "2";
    protected static String REPORT_NEVER = "1";
    protected static String REPORT_ALWAYS = "0";
    protected static String STATE_WAITING = "0";
    protected static String STATE_UPLOADING = "1";
    protected static String STATE_SUCCESSFUL = "2";
    protected static String STATE_FAILED = "3";
	
	// This is used to group the batch of bugs and notes sent on the server side
	protected long mNonce;
	protected List<HashMap<String, String>> mErrorReports;
    protected SimpleAdapter mErrorAdapter;
    protected ListView mErrorListView;
    
    @Override
    public void onBackPressed() {
        deleteFiles(true, false);
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getBaseContext();
        SharedPreferences sharedPreferences = PrefSettings.getSharedPrefs(context);
        String reportErrorMode = sharedPreferences.getString("reportErrorMode", REPORT_ASK);
        
        mNonce = UUID.randomUUID().getMostSignificantBits();

        if (reportErrorMode.equals(REPORT_ALWAYS)) { // Always report
            try {
                sendErrorReport();
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, e.toString());
            }

            deleteFiles(true, false);
            setResult(RESULT_OK);
            finish();

            return;
        } else if (reportErrorMode.equals(REPORT_NEVER)) { // Never report
            deleteFiles(false, false);
            setResult(RESULT_OK);
            finish();

            return;
        } else { // Prompt, default behaviour
            setContentView(R.layout.feedback);

            // TextView tvErrorText = (TextView) findViewById(R.id.tvErrorText);
            Button btnSendAll = (Button) findViewById(R.id.btnFeedbackSendAll);
            Button btnSendMostRecent = (Button) findViewById(R.id.btnFeedbackSendLatest);
            Button btnClearAll = (Button) findViewById(R.id.btnFeedbackClearAll);
            
            ListView mErrorListView = (ListView) findViewById(R.id.lvErrorList);

            getErrorFiles();
            int numErrors = mErrorReports.size();
            if (numErrors == 0) {
                mErrorListView.setVisibility(View.GONE);
                btnSendMostRecent.setVisibility(View.GONE);
                btnClearAll.setVisibility(View.GONE);
                btnSendAll.setText("Send us your feedback");
            } else {
                if (numErrors == 1) {
                    btnSendMostRecent.setVisibility(View.GONE);
                }
            
                mErrorAdapter = new SimpleAdapter(this, mErrorReports,
                        R.layout.error_item, new String[] {"name", "state"}, new int[] {
                                R.id.error_item_text, R.id.error_item_progress });
                mErrorAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {
                    @Override
                    public boolean setViewValue(View view, Object arg1, String text) {
                        if (view.getId() == R.id.error_item_progress) {
                            if (text.equals(STATE_UPLOADING)) {
                                view.setVisibility(View.VISIBLE);
                            } else {
                                view.setVisibility(View.GONE);
                            }
                            return true;
                        /*} else if (view.getId() == R.id.error_item_progress) {
                            if (text.equals(STATE_SUCCESSFUL)) {
                                ImageView iv = (ImageView)view;
                                iv.setImageResource(R.drawable.ic_bullet_key_permission);
                                view.setVisibility(View.VISIBLE);
                            } else if (text.equals(STATE_FAILED)) {
                                ImageView iv = (ImageView)view;
                                iv.setImageResource(R.drawable.ic_delete);
                                view.setVisibility(View.VISIBLE);
                            } else {
                                view.setVisibility(View.GONE);
                            }
                            return true;*/
                        }
                        return false;
                    }
                });
    
                mErrorListView.setAdapter(mErrorAdapter);
                refreshErrorListView();
            }

            btnSendAll.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        sendErrorReport();
                    } catch (Exception e) {
                        Log.e(AnkiDroidApp.TAG, e.toString());
                    }
                    refreshErrorListView();
                }
            });

            btnSendMostRecent.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        deleteFiles(false, true);
                        refreshErrorListView();
                        sendErrorReport();
                    } catch (Exception e) {
                        Log.e(AnkiDroidApp.TAG, e.toString());
                    }
                    refreshErrorListView();
                }
            });

            btnClearAll.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteFiles(false, false);
                    refreshErrorListView();
                }
            });
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    private void refreshErrorListView() {
        mErrorAdapter.notifyDataSetChanged();
    }
    
    private void getErrorFiles() {
        mErrorReports = new ArrayList<HashMap<String, String>>();
        String[] errors = fileList();

        for (String file : errors) {
            if (file.endsWith(".stacktrace")) {
                HashMap<String, String> error = new HashMap<String, String>();
                error.put("name", file);
                error.put("state", STATE_WAITING);
                error.put("result", "");
                mErrorReports.add(error);
            }
        }
    }

    private void deleteFiles(boolean onlyProcessed, boolean keepLatest) {
        
        for (int i = (keepLatest? 1: 0); i < mErrorReports.size(); ) {
            try {
                String errorState = mErrorReports.get(i).get("state");
                if (!onlyProcessed || errorState.equals(STATE_SUCCESSFUL)) {
                    deleteFile(mErrorReports.get(i).get("name"));
                    mErrorReports.remove(i);
                } else {
                    i++;
                }
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, String.format("Could not delete file: %s", mErrorReports.get(i)));
            }
        }
    }

    private void sendErrorReport() throws IOException {
        final String url = getString(R.string.error_post_url);
        
        for (int i = 0; i < mErrorReports.size(); i++) {
            HashMap<String, String> error = mErrorReports.get(i);
            try {
                String filename = error.get("name");
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
                
                postReport(i, pairs);
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
            int errorIndex = (Integer)data.data[1];
            mErrorReports.get(errorIndex).put("state", STATE_SUCCESSFUL);
            refreshErrorListView();
            Log.i(AnkiDroidApp.TAG, "Send error report " + errorIndex + " finished, result: " + ((String) data.result));
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

    private void postReport(int index, List<NameValuePair> values) {
        final String url = getString(R.string.error_post_url);
        mErrorReports.get(index).put("state", STATE_UPLOADING);
        Connection.sendErrorReport(sendListener, new Connection.Payload(new Object[] {url, index, values}));
    }

    private void postFeedback(List<NameValuePair> values) {
        final String url = getString(R.string.feedback_post_url);
        Connection.sendErrorReport(sendListener, new Connection.Payload(new Object[] {url, values}));
    }
}
