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

import static com.tomgibara.android.veecheck.Veecheck.LOG_TAG;

import java.util.Map;

import com.tomgibara.android.veecheck.VeecheckActivity;
import com.tomgibara.android.veecheck.VeecheckNotifier;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;

/**
 * An implementation of {@link VeecheckNotifier} that directs a user to a
 * {@link VeecheckActivity}.
 * 
 * @author Tom Gibara
 *
 */

public class DefaultNotifier extends BaseNotifier {

	private final IntentFilter[] filters;

	private final Intent activityIntent;
	
	private final int iconId;
	private final int tickerId;
	private final int titleId;
	private final int messageId;
	
	/**
	 * Creates a new notifier that will direct the user to a confirmation
	 * activity that typically extends {@link VeecheckActivity}. If a null
	 * activity intent is supplied, the update activity will be used instead.
	 * 
	 * @param context the context of the notifier
	 * @param notificationId the id given to the notification created
	 * @param filters intent filters that the update intent must match, may be null
	 * @param activityIntent the activity to which the user will be directed by the notification
	 * @param iconId the id of a drawable resource to be displayed in the notification 
	 * @param tickerId the id of a string resource that provides the text displayed in the ticker
	 * @param titleId the id of a string resource that provides the notification title
	 * @param messageId the id of a string resource that provides the notification message
	 */
	
	public DefaultNotifier(Context context, int notificationId, IntentFilter[] filters, Intent activityIntent, int iconId, int tickerId, int titleId, int messageId) {
		super(context, notificationId);
		this.filters = filters;
		this.activityIntent = activityIntent;
		this.iconId = iconId;
		this.tickerId = tickerId;
		this.titleId = titleId;
		this.messageId = messageId;
	}
	
	@Override
	protected Notification createNotification(PendingIntent intent) {
		Notification n = new Notification(iconId, context.getText(tickerId), System.currentTimeMillis());
		n.contentIntent = intent;
		n.flags = Notification.FLAG_AUTO_CANCEL;
		n.setLatestEventInfo(
			context,
			context.getText(titleId),
			context.getText(messageId),
			intent);
		return n;
	}

	@Override
	protected PendingIntent createPendingIntent(Intent intent) {
		Intent activityIntent;
		if (this.activityIntent == null) {
			activityIntent = intent;
		} else {
			activityIntent = new Intent(this.activityIntent);
			activityIntent.putExtra(Intent.EXTRA_INTENT, intent);
		}
		return super.createPendingIntent(activityIntent);
	}
	
	@Override
	public Intent createIntent(String action, String data, String type, Map<String, String> extras) {
		if (filters != null && filters.length > 0) {
			//check values against the supplied intent filters
			Uri uri = data == null ? null : Uri.parse(data);
			String scheme = uri == null ? null : uri.getScheme();
			String resolvedType = type == null && "content".equals(scheme) ? context.getContentResolver().getType(uri) : type;
			for (IntentFilter filter : filters) {
				if (filter == null) continue;
				int result = filter.match(action, resolvedType, scheme, uri, null, LOG_TAG);
				if (result < 0) return null;
			}
		}
		//generate an intent from the supplied parameters
		return super.createIntent(action, data, type, extras);
	}
	
}
