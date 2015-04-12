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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.MenuItem;
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
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledProgressDialog;

import timber.log.Timber;

public class MyAccount extends AnkiActivity {
    private final static int STATE_ACCOUNT_SIGN_UP = 0;
    private final static int STATE_LOG_IN  = 1;
    private final static int STATE_LOGGED_IN = 2;

    private View mLoginToMyAccountView;
    private View mLoggedIntoMyAccountView;
    private View mRegisterView;

    private EditText mUsername;
    private EditText mPassword;

    private EditText mUsername1;
    private EditText mPassword1;
    private EditText mUsername2;
    private EditText mPassword2;

    private TextView mUsernameLoggedIn;
    private int mCurrentState;

    private MaterialDialog mProgressDialog;
    private StyledDialog mNoConnectionAlert;
    private StyledDialog mConnectionErrorAlert;
    private StyledDialog mInvalidUserPassAlert;
    private StyledDialog mRegisterAlert;
    private StyledDialog mErrorAlert;
    Toolbar mToolbar = null;


    private void switchToState(int newState) {
        mCurrentState = newState;
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

            case STATE_ACCOUNT_SIGN_UP:
                mToolbar = (Toolbar) mRegisterView.findViewById(R.id.toolbar);
                if (mToolbar!= null) {
                    mToolbar.setTitle(getString(R.string.sync_account));  // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                    setSupportActionBar(mToolbar);
                }
                setContentView(mRegisterView);
                break;
        }


        supportInvalidateOptionsMenu();  // Needed?
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        initAllContentViews();
        initAllAlertDialogs();

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
            Connection.login(loginListener, new Connection.Payload(new Object[] { username, password }));
        } else {
            mInvalidUserPassAlert.show();
        }
    }


    private void register() {
        // Hide soft keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mUsername.getWindowToken(), 0);

        String username = mUsername1.getText().toString().trim(); // trim spaces, issue 1586
        String password = mPassword1.getText().toString();

        if (!"".equalsIgnoreCase(username) && !"".equalsIgnoreCase(password)) {
            Connection.register(registerListener, new Connection.Payload(new Object[] { username, password }));
        } else {
            mInvalidUserPassAlert.show();
        }
    }


    private void logout() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        Editor editor = preferences.edit();
        editor.putString("username", "");
        editor.putString("hkey", "");
        editor.commit();
        //  force media resync on deauth
        try {
            getCol().getMedia().forceResync();
        } catch (SQLiteException e) {
            Timber.e("MyAccount.logout()  reinitializing media db due to sqlite error");
            getCol().getMedia()._initDB();
        }
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
                switchToState(STATE_ACCOUNT_SIGN_UP);
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

        mRegisterView = getLayoutInflater().inflate(R.layout.my_account_register, null);
        mUsername1 = (EditText) mRegisterView.findViewById(R.id.username1);
        mPassword1 = (EditText) mRegisterView.findViewById(R.id.password1);
        mUsername2 = (EditText) mRegisterView.findViewById(R.id.username2);
        mPassword2 = (EditText) mRegisterView.findViewById(R.id.password2);

        // Make the terms of use link clickable
        TextView terms = (TextView) mRegisterView.findViewById(R.id.terms_link);
        terms.setMovementMethod(LinkMovementMethod.getInstance());

        Button registerButton = (Button) mRegisterView.findViewById(R.id.register_button);
        registerButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mUsername1.getText().toString().length() > 0 && mPassword1.getText().toString().length() > 0
                        && mUsername1.getText().toString().equals(mUsername2.getText().toString())
                        && mPassword1.getText().toString().equals(mPassword2.getText().toString())) {
                    register();
                } else {
                    mRegisterAlert.show();
                }
            }

        });

        Button cancelButton = (Button) mRegisterView.findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToState(STATE_LOG_IN);
            }

        });
    }


    /**
     * Create AlertDialogs used on all the activity
     */
    private void initAllAlertDialogs() {
        Resources res = getResources();

        StyledDialog.Builder builder = new StyledDialog.Builder(this);

        // builder.setTitle(res.getString(R.string.connection_error_title));
        builder.setIcon(R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.youre_offline));
        builder.setPositiveButton(res.getString(R.string.dialog_ok), null);
        mNoConnectionAlert = builder.create();

        builder.setTitle(res.getString(R.string.register_title));
        builder.setIcon(R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.register_error));
        builder.setPositiveButton(res.getString(R.string.dialog_ok), null);
        mErrorAlert = builder.create();

        builder.setTitle(res.getString(R.string.register_title));
        builder.setIcon(R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.register_mismatch));
        builder.setPositiveButton(res.getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                mUsername1.setText("");
                mUsername2.setText("");
                mPassword1.setText("");
                mPassword2.setText("");
            }
        });
        mRegisterAlert = builder.create();

        builder = new StyledDialog.Builder(this);
        builder.setTitle(res.getString(R.string.log_in));
        builder.setIcon(R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.invalid_username_password));
        builder.setPositiveButton(res.getString(R.string.dialog_ok), null);
        mInvalidUserPassAlert = builder.create();

        builder = new StyledDialog.Builder(this);
        // builder.setTitle(res.getString(R.string.connection_error_title));
        builder.setIcon(R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.connection_error_message));
        builder.setPositiveButton(res.getString(R.string.retry), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                login();
            }
        });
        builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
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
                    if (mInvalidUserPassAlert != null) {
                        mInvalidUserPassAlert.show();
                    }
                } else {
                    if (mConnectionErrorAlert != null) {
                        mConnectionErrorAlert.show();
                    }
                }
            }
        }


        @Override
        public void onDisconnected() {
            if (mNoConnectionAlert != null) {
                mNoConnectionAlert.show();
            }
        }
    };

    Connection.TaskListener registerListener = new Connection.TaskListener() {

        @Override
        public void onProgressUpdate(Object... values) {
            // Pass
        }


        @Override
        public void onPreExecute() {
            Timber.d("registerListener.onPreExcecute()");
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = StyledProgressDialog.show(MyAccount.this, "",
                        getResources().getString(R.string.registering_message), false);
            }
        }


        @Override
        public void onPostExecute(Payload data) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }

            if (data.success) {
                Timber.i("User successfully registered!");
                saveUserInformation((String) data.data[0], (String) data.data[1]);

                Intent i = MyAccount.this.getIntent();
                if (i.hasExtra("notLoggedIn") && i.getExtras().getBoolean("notLoggedIn", false)) {
                    MyAccount.this.setResult(RESULT_OK, i);
                    finishWithAnimation(ActivityTransitionAnimation.FADE);
                } else {
                    // Show logged view
                    mUsernameLoggedIn.setText((String) data.data[0]);
                    setContentView(mLoggedIntoMyAccountView);
                }
            } else {
                mErrorAlert.show();
                if (data.data != null) {
                    String msg = ((String[]) data.data)[0];
                    Timber.e("User registration failed: %s", msg);
                    mErrorAlert.setMessage(msg);
                } else {
                    Timber.e("User registration failed");
                }
            }
        }

        @Override
        public void onDisconnected() {
            if (mNoConnectionAlert != null) {
                mNoConnectionAlert.show();
            }
        }
    };


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Timber.i("MyAccount - onBackPressed()");
            if (mCurrentState == STATE_ACCOUNT_SIGN_UP) {
                switchToState(STATE_LOG_IN);
                return true;
            } else {
                finish();
                ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.FADE);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

}
