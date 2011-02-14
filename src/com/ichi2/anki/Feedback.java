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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.ichi2.utils.HttpUtility;

public class Feedback extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(AnkiDroidApp.TAG, "OnCreate - Feedback");

        super.onCreate(savedInstanceState);

        setContentView(R.layout.feedback);
        
        Button btnOk = (Button) findViewById(R.id.btnFeedbackOk);
        Button btnCancel = (Button) findViewById(R.id.btnFeedbackCancel);
        
        btnOk.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if(sendFeedback()) {
                    	setResult(RESULT_OK);
                        finish();
                        return;
                    }
                } catch (Exception e) {
                    Log.e(AnkiDroidApp.TAG, e.toString());
                }

                new AlertDialog.Builder(v.getContext())
                	.setTitle(R.string.feedback_title)
	        		.setMessage(R.string.feedback_result_fail)
	        		.setPositiveButton(android.R.string.ok, null)
	        		.show();
            }
        });

        btnCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    private Boolean sendFeedback() {
    	EditText etFeedbackText = (EditText) findViewById(R.id.etFeedbackText);
    	String text = etFeedbackText.getText().toString().trim();
    	
    	if(text.equals(""))
    		return false;
    	
    	Date ts = new Date();
    	TimeZone tz = TimeZone.getDefault();
    	
        SimpleDateFormat df1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
        SimpleDateFormat df2 = new SimpleDateFormat("Z", Locale.US);
        
        df1.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        String feedbacksentutc = String.format("%s", df1.format(ts));
        String feedbacksenttzoffset = String.format("%s", df2.format(ts));
        String feedbacksenttz = String.format("%s", tz.getID());
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        
        pairs.add(new BasicNameValuePair("feedbacksentutc", feedbacksentutc));
        pairs.add(new BasicNameValuePair("feedbacksenttzoffset", feedbacksenttzoffset));
        pairs.add(new BasicNameValuePair("feedbacksenttzoffset", feedbacksenttz));
       	pairs.add(new BasicNameValuePair("feedback", text));
        
       	return HttpUtility.postReport(getString(R.string.feedback_post_url), pairs);
    }
}