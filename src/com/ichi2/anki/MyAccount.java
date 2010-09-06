package com.ichi2.anki;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

public class MyAccount extends Activity {

	private static final String TAG = "AnkiDroid";
	
	private View mLoginToMyAccountView;
	private View mLoggedIntoMyAccountView;
	
	private EditText mUsername;
	private EditText mPassword;
	
	private TextView mUsernameLoggedIn;
	
	private ProgressDialog mProgressDialog;
	private AlertDialog mNoConnectionAlert;
	private AlertDialog mConnectionErrorAlert;
	private AlertDialog mInvalidUserPassAlert;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		
		initAllContentViews();
		initAllAlertDialogs();
		
		if(AnkiDroidApp.isUserLoggedIn())
		{
			SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
			String username = preferences.getString("username", "");
			mUsernameLoggedIn.setText(username);
			setContentView(mLoggedIntoMyAccountView);
		}
		else
		{
			setContentView(mLoginToMyAccountView);
		}
			
	}
	
	private boolean isUsernameAndPasswordValid(String username, String password)
	{
		return isLoginFieldValid(username) && isLoginFieldValid(password);
	}
	
	private boolean isLoginFieldValid(String loginField)
	{
		boolean loginFieldValid = false;
		
		if(loginField.length() >= 2 && loginField.matches("[A-Za-z0-9]+"))
		{
			loginFieldValid = true;
		}
		
		return loginFieldValid;
	}
	
	private void saveUserInformation(String username, String password)
	{
		SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
		Editor editor = preferences.edit();
		editor.putString("username", username);
		editor.putString("password", password);
		editor.commit();
	}
	
	private void login()
	{
		// Hide soft keyboard
		InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); 
		inputMethodManager.hideSoftInputFromWindow(mUsername.getWindowToken(), 0);
		
		String username = mUsername.getText().toString();
		String password = mPassword.getText().toString();
		
		Log.i(TAG, "Username = " + username);
		Log.i(TAG, "Password = " + password);
		
		/* Commented awaiting the resolution of the next issue: http://code.google.com/p/anki/issues/detail?id=1932
		if(isUsernameAndPasswordValid(username, password))
		{
			Connection.login(loginListener, new Connection.Payload(new Object[] {username, password}));
		}
		else
		{
			mInvalidUserPassAlert.show();
		}
		*/
		
		if(!"".equalsIgnoreCase(username) && !"".equalsIgnoreCase(password))
		{
			Connection.login(loginListener, new Connection.Payload(new Object[] {username, password}));
		}
		else
		{
			mInvalidUserPassAlert.show();
		}
	}
	
	private void logout()
	{
		SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
		Editor editor = preferences.edit();
		editor.putString("username", "");
		editor.putString("password", "");
		editor.commit();
		
		setContentView(mLoginToMyAccountView);
	}
	
	private void initAllContentViews()
	{
		mLoginToMyAccountView = getLayoutInflater().inflate(R.layout.my_account, null);
		mUsername = (EditText) mLoginToMyAccountView.findViewById(R.id.username);
		mPassword = (EditText) mLoginToMyAccountView.findViewById(R.id.password);
		
		Button loginButton = (Button) mLoginToMyAccountView.findViewById(R.id.login_button);
		loginButton.setOnClickListener(new OnClickListener() 
		{

			@Override
			public void onClick(View v) 
			{
				login();
			}
			
		});
		
		Button signUpButton = (Button) mLoginToMyAccountView.findViewById(R.id.sign_up_button);
		signUpButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.ankionline_sign_up_url))));
			}
			
		});
		
		
		mLoggedIntoMyAccountView = getLayoutInflater().inflate(R.layout.my_account_logged_in, null);
		mUsernameLoggedIn = (TextView) mLoggedIntoMyAccountView.findViewById(R.id.username_logged_in);
		Button logoutButton = (Button) mLoggedIntoMyAccountView.findViewById(R.id.logout_button);
		logoutButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				logout();
			}
			
		});
	}
	
	/**
	 * Create AlertDialogs used on all the activity
	 */
	private void initAllAlertDialogs()
	{
		Resources res = getResources();
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(res.getString(R.string.connection_error_title));
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setMessage(res.getString(R.string.connection_needed));
		builder.setPositiveButton(res.getString(R.string.ok), null);
		mNoConnectionAlert = builder.create();
		
		builder.setTitle(res.getString(R.string.log_in));
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setMessage(res.getString(R.string.invalid_username_password));
		mInvalidUserPassAlert = builder.create();
		
		builder.setTitle(res.getString(R.string.connection_error_title));
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setMessage(res.getString(R.string.connection_error_message));
		builder.setPositiveButton(res.getString(R.string.retry), new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				login();
			}
		});
		builder.setNegativeButton(res.getString(R.string.cancel), null);
		mConnectionErrorAlert = builder.create();
	}
	
	/**
	 * Listeners
	 */
	Connection.TaskListener loginListener = new Connection.TaskListener() {
		
		@Override
		public void onProgressUpdate(Object... values) {
			// Pass
		}
		
		@Override
		public void onPreExecute() {
			Log.i(TAG, "onPreExcecute");
			if(mProgressDialog == null || !mProgressDialog.isShowing())
			{
				mProgressDialog = ProgressDialog.show(MyAccount.this, "", getResources().getString(R.string.alert_logging_message), true);
			}
		}
		
		@Override
		public void onPostExecute(Payload data) {
			Log.i(TAG, "onPostExecute, succes = " + data.success);
			if(mProgressDialog != null)
			{
				mProgressDialog.dismiss();
			}
			
			if(data.success)
			{
				saveUserInformation((String) data.data[0], (String) data.data[1]);
				
				Log.i(TAG, "User successfully logged!");
				
				//Show logged view
				mUsernameLoggedIn.setText((String) data.data[0]);
				setContentView(mLoggedIntoMyAccountView);
				
			}
			else
			{
				if(data.returnType == AnkiDroidProxy.LOGIN_INVALID_USER_PASS)
				{
					if(mInvalidUserPassAlert != null)
					{
						mInvalidUserPassAlert.show();
					}
				}
				else
				{
					if(mConnectionErrorAlert != null)
					{
						mConnectionErrorAlert.show();
					}
				}
			}
		}
		
		@Override
		public void onDisconnected() {
			if(mNoConnectionAlert != null)
			{
				mNoConnectionAlert.show();
			}
		}
	};
	
}
