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

import static com.tomgibara.android.veecheck.Veecheck.LOG_TAG;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

/**
 * An abstract base class for {@link Service}s that perform the task of checking
 * for new application versions. Extensions should implement the
 * {@link #createNotifier()} method.
 * 
 * @author Tom Gibara
 *
 */

public abstract class VeecheckService extends Service {
	
	/**
	 * This handler is used to post back a {@link Runnable} that responds to
	 * the termination of a {@link VeecheckThread }.
	 */
	
	private final Handler handler = new Handler();
	
	/**
	 * Records the currently running {@link VeecheckThread}. Only one such
	 * thread should be existent at any given time; there is no sensible reason
	 * to concurrently check for updates to a single application.
	 */
	
	private VeecheckThread thread = null;
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		Log.d(LOG_TAG, "Service started");
		
		String action = intent.getAction();
		Uri data = intent.getData();
		
		Log.v(LOG_TAG, "Service received action: " + action);
		//stop ourselves if this wasn't the right action and we're not busy
		String checkAction = Veecheck.getCheckAction(this);
		if (!checkAction.equals(action) || data == null) {
			synchronized (this) {
				if (thread == null) {
					Log.d(LOG_TAG, "Stopping service due to invalid action.");
					stopSelf(startId);
				}
			}
			return;
		}
		
		synchronized (this) {
			if (thread == null) {
				Log.d(LOG_TAG, "Starting service thread.");
				thread = new VeecheckThread(this, data);
				thread.start();
			} else {
				Log.d(LOG_TAG, "Not checking due to check in progress.");
			}
		}
	}

	/**
	 * This service cannot be bound.
	 */
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	/**
	 * Concrete extensions of this class should implement this method to provide
	 * a {@link VeecheckNotifier} that can be used when intent information has
	 * been obtained for the current application version.
	 * 
	 * @return a {@link VeecheckNotifier}, never null
	 */
	
	protected abstract VeecheckNotifier createNotifier();

	/**
	 * Supplies an object that can persist the state information needed by
	 * {@link VeecheckService} within the given context.
	 *
	 * @return the state of the veecheck service
	 */
	
	protected abstract VeecheckState createState();
	
	/**
	 * This method called by a {@link VeecheckThread} immediately prior to its
	 * termination.
	 * 
	 * @param result the result obtained by parsing the versions document
	 */
	
	void notifyAndStop(final VeecheckResult result) {
		handler.post(new Runnable() {
			public void run() {
				Log.d(LOG_TAG, "Service thread reports completion.");
				try {
					notifyOfResult(result);
				} finally {
					synchronized (VeecheckService.this) {
						thread = null;
						stopSelf();
					}
					Log.d(LOG_TAG, "Service stopping.");
				}
			}
		});
	}
	
	/**
	 * Do the work of notifying the user that a new version of the application
	 * is available (if necessary).
	 * 
	 * @param result the result from parsing the versions document, may be null
	 */
	
	private void notifyOfResult(VeecheckResult result) {
		if (result == null) return;

		VeecheckNotifier notifier = createNotifier();
		Intent intent = notifier.createIntent(
				result.action,
				result.data,
				result.type,
				result.extras
				);
		
		if (intent == null) {
			Log.d(LOG_TAG, "No intent generated.");
			return;
		}
		
		if (createState().isIgnoredIntent(intent)) {
			Log.d(LOG_TAG, "User ignoring intent.");
			return;
		}
		
		Notification notification = notifier.createNotification(intent);
		if (notification == null) {
			Log.d(LOG_TAG, "Notification declined.");
			return;
		}
		
		int id = notifier.getNotificationId();
		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		manager.notify(id, notification);
		Log.d(LOG_TAG, "Notification issued.");
	}
	
}
