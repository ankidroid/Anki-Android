package com.ichi2.anki.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ichi2.anki.services.NotificationService;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Intent serviceIntent = new Intent(context, NotificationService.class);
        context.startService(serviceIntent);
    }
}
