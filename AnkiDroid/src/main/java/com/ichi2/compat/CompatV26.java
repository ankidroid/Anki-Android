package com.ichi2.compat;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;

import timber.log.Timber;

/** Implementation of {@link Compat} for SDK level 26 */
@TargetApi(26)
public class CompatV26 extends CompatV23 implements Compat {

    /**
     * In Oreo and higher, you must create a channel for all notifications.
     * This will create the channel if it doesn't exist, or if it exists it will update the name.
     *
     * Note that once a channel is created, only the name may be changed as long as the application
     * is installed on the user device. All other settings are fully under user control.
     *
     * @param context the Context with a handle to the NotificationManager
     * @param id the unique (within the package) id the channel for programmatic access
     * @param name the user-visible name for the channel
     */
    @Override
    public void setupNotificationChannel(Context context, String id, String name) {
        Timber.i("Creating notification channel with id/name: %s/%s",id, name);
        NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT);
        notificationChannel.setShowBadge(true);
        notificationChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(notificationChannel);
    }
}
