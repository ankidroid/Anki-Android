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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.ichi2.themes.StyledProgressDialog;

import timber.log.Timber;

public class MyAccount extends AnkiActivity {
    private final static int STATE_LOG_IN  = 1;
    private final static int STATE_LOGGED_IN = 2;

    private View mLoginToMyAccountView;
    private View mLoggedIntoMyAccountView;

    private EditText mUsername;
    private EditText mPassword;

    private TextView mUsernameLoggedIn;

    private MaterialDialog mProgressDialog;
    Toolbar mToolbar = null;


    private void switchToState(int newState) {
        switch (newState) {
            case STATE_LOGGED_IN:
                String username = AnkiDroidApp.getSharedPrefs(getBaseContext()).getString("username", "");
                mUsernameLoggedIn.setText(username);
                mToolbar = (Toolbar) mLoggedIntoMyAccountView.findViewById(R.id.toolbar);
                if (mToolbar!= null) {
                    mToolbar.setTitle(getString(R.string.sync_account));  // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                    setSupportActionBar(mToolbar);
                }
                setContentView(mLoggedIntoMyAccountView);
                break;

            case STATE_LOG_IN:
                mToolbar = (Toolbar) mLoginToMyAccountView.findViewById(R.id.toolbar);
                if (mToolbar!= null) {
                    mToolbar.setTitle(getString(R.string.sync_account));  // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                    setSupportActionBar(mToolbar);
                }
                setContentView(mLoginToMyAccountView);
                break;
        }


        supportInvalidateOptionsMenu();  // Needed?
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mayOpenUrl(Uri.parse(getResources().getString(R.string.register_url)));
        initAllContentViews();

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        if (preferences.getString("hkey", "").length() > 0) {
            switchToState(STATE_LOGGED_IN);
        } else {
            switchToState(STATE_LOG_IN);
        }
    }


    // Commented awaiting the resolution of the next issue: http://code.google.com/p/anki/issues/detail?id=1932
    // private boolean isUsernameAndPasswordValid(String username, String password) {
    // return isLoginFieldValid(username) && isLoginFieldValid(password);
    // }
    //
    //
    // private boolean isLoginFieldValid(String loginField) {
    // boolean loginFieldValid = false;
    //
    // if (loginField.length() >= 2 && loginField.matches("[A-Za-z0-9]+")) {
    // loginFieldValid = true;
    // }
    //
    // return loginFieldValid;
    // }

    private void saveUserInformation(String username, String hkey) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        Editor editor = preferences.edit();
        editor.putString("username", username);
        editor.putString("hkey", hkey);
        editor.commit();
    }


    private void login() {
        // Hide soft keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mUsername.getWindowToken(), 0);

        String username = mUsername.getText().toString().trim(); // trim spaces, issue 1586
        String password = mPassword.getText().toString();

        /*
         * Commented awaiting the resolution of the next issue: http://code.google.com/p/anki/issues/detail?id=1932
         * if(isUsernameAndPasswordValid(username, password)) { Connection.login(loginListener, new
         * Connection.Payload(new Object[] {username, password})); } else { mInvalidUserPassAlert.show(); }
         */

        if (!"".equalsIgnoreCase(username) && !"".equalsIgnoreCase(password)) {
            Connection.login(loginListener, new Connection.Payload(new Object[]{username, password}));
        } else {
            UIUtils.showSimpleSnackbar(this, R.string.invalid_username_password, true);
        }
    }


    private void logout() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        Editor editor = preferences.edit();
        editor.putString("username", "");
        editor.putString("hkey", "");
        editor.commit();
        //  force media resync on deauth
        getCol().getMedia().forceResync();
        switchToState(STATE_LOG_IN);
    }


    private void resetPassword() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(getResources().getString(R.string.resetpw_url)));
        startActivity(intent);
    }


    private void initAllContentViews() {
        mLoginToMyAccountView = getLayoutInflater().inflate(R.layout.my_account, null);
        mUsername = (EditText) mLoginToMyAccountView.findViewById(R.id.username);
        mPassword = (EditText) mLoginToMyAccountView.findViewById(R.id.password);

        Button loginButton = (Button) mLoginToMyAccountView.findViewById(R.id.login_button);
        loginButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                login();
            }

        });

        Button resetPWButton = (Button) mLoginToMyAccountView.findViewById(R.id.reset_password_button);
        resetPWButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                resetPassword();
            }
        });

        Button signUpButton = (Button) mLoginToMyAccountView.findViewById(R.id.sign_up_button);
        signUpButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl(Uri.parse(getResources().getString(R.string.register_url)));
            }

        });

        mLoggedIntoMyAccountView = getLayoutInflater().inflate(R.layout.my_account_logged_in, null);
        mUsernameLoggedIn = (TextView) mLoggedIntoMyAccountView.findViewById(R.id.username_logged_in);
        Button logoutButton = (Button) mLoggedIntoMyAccountView.findViewById(R.id.logout_button);
        logoutButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                logout();
            }

        });
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
            Timber.d("loginListener.onPreExcecute()");
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = StyledProgressDialog.show(MyAccount.this, "",
                        getResources().getString(R.string.alert_logging_message), false);
            }
        }


        @Override
        public void onPostExecute(Payload data) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }

            if (data.success) {
                Timber.i("User successfully logged in!");
                saveUserInformation((String) data.data[0], (String) data.data[1]);

                Intent i = MyAccount.this.getIntent();
                if (i.hasExtra("notLoggedIn") && i.getExtras().getBoolean("notLoggedIn", false)) {
                    MyAccount.this.setResult(RESULT_OK, i);
                    finishWithAnimation(ActivityTransitionAnimation.FADE);
                } else {
                    // Show logged view
                    mUsernameLoggedIn.setText((String) data.data[0]);
                    switchToState(STATE_LOGGED_IN);
                }
            } else {
                Timber.e("Login failed, error code %d",data.returnType);
                if (data.returnType == 403) {
                    UIUtils.showSimpleSnackbar(MyAccount.this, R.string.invalid_username_password, true);
                } else {
                    UIUtils.showSimpleSnackbar(MyAccount.this, R.string.connection_error_message, true);
                }
            }
        }


        @Override
        public void onDisconnected() {
            UIUtils.showSimpleSnackbar(MyAccount.this, R.string.youre_offline, true);
        }
    };


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Timber.i("MyAccount - onBackPressed()");
            finishWithAnimation(ActivityTransitionAnimation.FADE);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

}
