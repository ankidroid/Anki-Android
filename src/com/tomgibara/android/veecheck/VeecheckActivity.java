/*
 * Copyright 2008 Tom Gibara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tomgibara.android.veecheck;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Checkable;

/**
 * Base class for an activity which gives the user the option of continuing with
 * an upgrade.
 * 
 * @author Tom Gibara
 *
 */

public abstract class VeecheckActivity extends Activity implements OnClickListener {

	private Intent updateIntent;
	
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		View yesButton = getYesButton();
		View noButton = getNoButton();
		updateIntent = getUpdateIntent();
		if (updateIntent == null) {
			yesButton.setEnabled(false);
			noButton.setEnabled(false);
		} else {
			yesButton.setOnClickListener(this);
			noButton.setOnClickListener(this);
		}
	}

	/**
	 * Responds to the "yes" and "no" buttons
	 */
	
	public void onClick(View view) {
		Checkable stopCheckBox = getStopCheckBox();
		boolean stop = stopCheckBox == null ? false : stopCheckBox.isChecked();
		if (view == getYesButton()) {
			if (stop) createState().setIgnoredIntent(updateIntent);
			startActivity(updateIntent);
			finish();
		} else if (view == getNoButton()) {
			if (stop) createState().setIgnoredIntent(updateIntent);
			finish();
		}
	}
	
	/**
	 * The intent that will be used to start an activity if the user clicks okay.
	 */
	
	public Intent getUpdateIntent() {
		return getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
	}
	
	/**
	 * The veecheck state information as required by this activity.
	 * 
	 * @return the state information for this application
	 */
	
	abstract protected VeecheckState createState();
	
	/**
	 * The button inside this activity's content view which the user is invited
	 * to click on to continue. This method will be called immediately after the
	 * {@link #onCreate(Bundle)} method has been called.
	 * 
	 * @return a view in this activity's content view, never null
	 */
	
	abstract protected View getYesButton();

	/**
	 * The button inside this activity's content view which the user may click
	 * to cancel the update. This method will be called immediately after the
	 * {@link #onCreate(Bundle)} method has been called.
	 * 
	 * @return a view in this activity's content view, never null
	 */
	
	abstract protected View getNoButton();

	/**
	 * The checkable view inside this activity's content view which the user may
	 * click to opt out of receiving further notifications about the same update.
	 * This method will be called immediately after the {@link #onCreate(Bundle)} method
	 * has been called.
	 * 
	 * @return a checkable view in this activity's content view, or null
	 */
	
	abstract protected Checkable getStopCheckBox();

}
