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

import android.content.Context;
import android.content.Intent;

/**
 * 
 * Holds constants relevant to the classes in this package.
 * 
 * @author Tom Gibara
 */

public class Veecheck {

	/** Tag used for logging */
	
	public static final String LOG_TAG = "veecheck";
	
	/** The XML namespace for the documents parsed by this package. */
	
	public static final String XML_NAMESPACE = "http://www.tomgibara.com/android/veecheck/SCHEMA.1";
	
	private static String getPackageQualifiedAction(Context context, String action) {
		return context.getPackageName() + ".VEECHECK_" + action;
	}
	
	/**
	 * The action of an {@link Intent} that (in this context) will trigger a
	 * {@link VeecheckReceiver } to reschedule period checks for new application
	 * versions.
	 */
	
	public static String getRescheduleAction(Context context) {
		return getPackageQualifiedAction(context, "RESCHEDULE_CHECKS");
	}
	
	/**
	 * The action of an {@link Intent} that (in this context) will trigger a
	 * {@link VeecheckReceiver} to evaluate whether a check for a new version
	 * of the application should be performed.
	 */
	
	public static String getConsiderAction(Context context) {
		return getPackageQualifiedAction(context, "CONSIDER_CHECK");
	}

	/**
	 * The action of an {@link Intent} that (in this context( can be used to
	 * start a {@link VeecheckService} and check for a new application version.
	 */
	
	public static String getCheckAction(Context context) {
		return getPackageQualifiedAction(context, "PERFORM_CHECK");
	}
	
}
