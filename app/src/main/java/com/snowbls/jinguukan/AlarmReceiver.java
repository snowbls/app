package com.snowbls.jinguukan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent i) {
        if (MainActivity.foreground) return;
        Notifier.post(ctx, i.getStringExtra("title"), i.getStringExtra("body"),
                i.getIntExtra("id", 1), i.getStringExtra("who"), i.getStringExtra("room"));
    }
}
