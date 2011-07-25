package com.koushikdutta.desktopsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PhoneStateChangedReceiver extends BroadcastReceiver {
    private static final String LOGTAG = PhoneStateChangedReceiver.class.getSimpleName();
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(LOGTAG, "===== Starting PhoneStateChangedReceiver =====");
        Intent i = new Intent(context, SyncService.class);
        i.putExtra("reason", "phone");
        context.startService(i);
    }
}
