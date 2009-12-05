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

import android.content.Intent;

/**
 * Persists the state of application update checking.
 * 
 * @author Tom Gibara
 */

public interface VeecheckState {

	/**
	 * The last time that the application was checked for version updates.
	 * 
	 * @return the time (based on {@link System#currentTimeMillis()} or -1L
	 */
	
	long getLastCheck();
	
	/**
	 * Records the that an application update was checked for.
	 *
	 * @param lastCheck the time (based on {@link System#currentTimeMillis()}
	 * at which the last application check was performed.
	 */
	
	void setLastCheckNow(long lastCheck);

	/**
	 * Records the application update intent that the user has requested to be
	 * ignored. Implementations are not required to persist the intent, they
	 * only need to store enough information about the intent to implement the
	 * {@link #isIgnoredIntent(Intent)} method.
	 * 
	 * @param intent the update intent that the user has chosen to ignore
	 */
	
	void setIgnoredIntent(Intent intent);
	
	/**
	 * Called to identify if the supplied intent should be ignored on behalf of
	 * the user.
	 * 
	 * @param intent an intent obtained from a versions document.
	 * 
	 * @return true if the supplied intent would match exactly the same intent
	 * filters as the intent supplied with {@link #setIgnoredIntent(Intent)}
	 */
	
	boolean isIgnoredIntent(Intent intent);

}
