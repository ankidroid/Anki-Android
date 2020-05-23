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
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.web.HostNumFactory;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.utils.AdaptionUtil;

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
                mToolbar = mLoggedIntoMyAccountView.findViewById(R.id.toolbar);
                if (mToolbar!= null) {
                    mToolbar.setTitle(getString(R.string.sync_account));  // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                    setSupportActionBar(mToolbar);
                }
                setContentView(mLoggedIntoMyAccountView);
                break;

            case STATE_LOG_IN:
                mToolbar = mLoginToMyAccountView.findViewById(R.id.toolbar);
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

    private void saveUserInformation(String username, String hkey) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        Editor editor = preferences.edit();
        editor.putString("username", username);
        editor.putString("hkey", hkey);
        editor.apply();
    }


    private void login() {
        // Hide soft keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mUsername.getWindowToken(), 0);

        String username = mUsername.getText().toString().trim(); // trim spaces, issue 1586
        String password = mPassword.getText().toString();

        if (!"".equalsIgnoreCase(username) && !"".equalsIgnoreCase(password)) {
            Connection.login(loginListener, new Connection.Payload(new Object[]{username, password,
                    HostNumFactory.getInstance(this) }));
        } else {
            UIUtils.showSimpleSnackbar(this, R.string.invalid_username_password, true);
        }
    }


    private void logout() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        Editor editor = preferences.edit();
        editor.putString("username", "");
        editor.putString("hkey", "");
        editor.apply();
        HostNumFactory.getInstance(this).reset();
        //  force media resync on deauth
        getCol().getMedia().forceResync();
        switchToState(STATE_LOG_IN);
    }


    private void resetPassword() {
        if (AdaptionUtil.hasWebBrowser(this)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(getResources().getString(R.string.resetpw_url)));
            startActivityWithoutAnimation(intent);
        } else {
            UIUtils.showThemedToast(this, getResources().getString(R.string.no_browser_notification) + getResources().getString(R.string.resetpw_url), false);
        }
    }


    private void initAllContentViews() {
        mLoginToMyAccountView = getLayoutInflater().inflate(R.layout.my_account, null);
        mUsername = mLoginToMyAccountView.findViewById(R.id.username);
        mPassword = mLoginToMyAccountView.findViewById(R.id.password);

        Button loginButton = mLoginToMyAccountView.findViewById(R.id.login_button);
        loginButton.setOnClickListener(v -> login());

        Button resetPWButton = mLoginToMyAccountView.findViewById(R.id.reset_password_button);
        resetPWButton.setOnClickListener(v -> resetPassword());

        Button signUpButton = mLoginToMyAccountView.findViewById(R.id.sign_up_button);
        Uri url = Uri.parse(getResources().getString(R.string.register_url));
        signUpButton.setOnClickListener(v -> openUrl(url));

        mLoggedIntoMyAccountView = getLayoutInflater().inflate(R.layout.my_account_logged_in, null);
        mUsernameLoggedIn = mLoggedIntoMyAccountView.findViewById(R.id.username_logged_in);
        Button logoutButton = mLoggedIntoMyAccountView.findViewById(R.id.logout_button);
        logoutButton.setOnClickListener(v -> logout());
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
            Timber.d("loginListener.onPreExecute()");
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
                Timber.e("Login failed, error code %d", data.returnType);
                if (data.returnType == 403) {
                    UIUtils.showSimpleSnackbar(MyAccount.this, R.string.invalid_username_password, true);
                } else {
                    String message = getResources().getString(R.string.connection_error_message);
                    Object[] result = (Object [])data.result;
                    if (result.length > 1 && result[1] instanceof Exception) {
                        showSimpleMessageDialog(message, ((Exception)result[1]).getLocalizedMessage(), false);
                    } else {
                        UIUtils.showSimpleSnackbar(MyAccount.this, message, false);
                    }
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
