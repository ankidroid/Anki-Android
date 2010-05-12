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
package com.tomgibara.android.veecheck.util;

import java.util.Map;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.tomgibara.android.veecheck.VeecheckNotifier;

/**
 * A convenient abstract base class implementation for {@link VeecheckNotifier}.
 * Extensions must override the {@link #createNotification(PendingIntent)}
 * method and should probably override the {@link #createIntent(String, String, String, Map)}
 * method too for added security.
 * 
 * @author Tom Gibara
 *
 */

public abstract class BaseNotifier implements VeecheckNotifier {

	/**
	 * The context in which this {@link VeecheckNotifier} is operating.
	 */
	
	protected final Context context;
	
	/**
	 * The id to be returned next from {@link #getNotificationId()}.
	 */
	
	protected int notificationId;
	
	public BaseNotifier(Context context, int notificationId) {
		this.context = context;
		this.notificationId = notificationId;
	}

	public int getNotificationId() {
		return notificationId;
	}
	
	public Notification createNotification(Intent intent) {
		return createNotification( createPendingIntent(intent) );
	}

	/**
	 * Concrete extensions of this class are required to implement this method.
	 * Implementations should customize the notification for their application.
	 * 
	 * @param intent the content of the notification
	 * 
	 * @return a notification, or null if no notification should be raised
	 */
	
	abstract protected Notification createNotification(PendingIntent intent);
	
	protected PendingIntent createPendingIntent(Intent intent) {
		return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
	}
	
	/**
	 * Constructs an {@link Intent} (in the natural way) from the supplied
	 * parameters.
	 * 
	 * @param action the action of an intent
	 * @param data the data for an intent
	 * @param type the type for an intent
	 * @param extras the extra information for an intent
	 * 
	 * @return an intent generated from the supplied parameters, or null.
	 */
	
	public Intent createIntent(String action, String data, String type, Map<String, String> extras) {
		Intent intent = new Intent(action);
		Uri uri = data == null ? null : Uri.parse(data);
		if (type == null) {
			intent.setData(uri);
		} else {
			intent.setDataAndType(uri, type);
		}
		for (Map.Entry<String, String> entry : extras.entrySet()) {
			intent.putExtra(entry.getKey(), entry.getValue());
		}
		return intent;
	}
	
}
