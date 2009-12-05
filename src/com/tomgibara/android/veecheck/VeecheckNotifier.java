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

import java.util.Map;

import android.app.Notification;
import android.content.Intent;

/**
 * Implementations of this interface are responsible for converting information
 * supplied in veecheck xml documents into Notification objects. Instances of
 * this interface may expect to be called on to produce only one
 * {@link android.app.Notification}, though this cannot be guaranteed.
 * 
 * @author Tom Gibara
 *
 */

public interface VeecheckNotifier {

	/**
	 * Implementations should return valid {@link android.app.Notification}
	 * object based on freely on the intent supplied, or null if no
	 * notification should arise.
	 * 
	 * @param intent the intent that the notification should facilitate
	 * @return a notification or null
	 */
	
	Notification createNotification(Intent intent);
	
	/**
	 * May be called after {@link #createNotification(String, String, String, Map)}
	 * to provide an id to be supplied to the
	 * {@link android.app.NotificationManager#notify(int, Notification)} method.
	 * 
	 * @return an id for the notification
	 */
	
	int getNotificationId();

	/**
	 * Constructs an intent from information retrieved by the service. The intent
	 * returned by this method is not necessarily the intent performed directly
	 * in response to activating the notification. Implementations of this
	 * interface should override this method to ensure that the intent parameters
	 * are safely constrained.
	 * 
	 * @param action the putative action of the intent
	 * @param data the putative data of the intent
	 * @param type the putative type of the intent
	 * @param extras the putative extras of the intent
	 * 
	 * @return an intent generated from the information supplied, or null
	 */
	
	Intent createIntent(String action, String data, String type, Map<String, String> extras);

}
