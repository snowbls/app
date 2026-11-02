package com.snowbls.jinguukan;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;

final class Notifier {
    static final String CH = "msg";
    private static final int FLAGS = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    static void ensureChannel(Context c) {
        NotificationChannel ch = new NotificationChannel(CH, "메시지", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("단톡과 개인톡 새 메시지");
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    static void post(Context c, String title, String body, int id, String who, String room) {
        ensureChannel(c);
        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("room", room == null ? "" : room);
        PendingIntent pi = PendingIntent.getActivity(c, id, open, FLAGS);
        Notification.Builder b = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title == null ? "" : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new Notification.BigTextStyle().bigText(body == null ? "" : body))
                .setColor(0xFF2FBF5B)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(pi);
        Bitmap face = avatar(c, who);
        if (face != null) b.setLargeIcon(face);
        c.getSystemService(NotificationManager.class).notify(id, b.build());
    }

    private static Bitmap avatar(Context c, String who) {
        if (who == null || who.isEmpty()) return null;
        String[] names = {"img/pf/" + who + ".png", "img/pf/" + who + ".jpg"};
        for (String n : names) {
            try (InputStream in = c.getAssets().open(n)) {
                Bitmap bm = BitmapFactory.decodeStream(in);
                if (bm != null) return bm;
            } catch (Exception ignored) { }
        }
        return null;
    }

    static void schedule(Context c, String json) {
        c.getSharedPreferences("alarms", Context.MODE_PRIVATE).edit().putString("json", json == null ? "[]" : json).apply();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        SharedPreferences sp = c.getSharedPreferences("alarms", Context.MODE_PRIVATE);
        for (String s : sp.getString("ids", "").split(",")) {
            if (s.isEmpty()) continue;
            try { am.cancel(PendingIntent.getBroadcast(c, Integer.parseInt(s), new Intent(c, AlarmReceiver.class), FLAGS)); }
            catch (Exception ignored) { }
        }
        StringBuilder ids = new StringBuilder();
        try {
            JSONArray arr = new JSONArray(json == null ? "[]" : json);
            long now = System.currentTimeMillis();
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.getJSONObject(k);
                long at = o.getLong("at");
                if (at <= now) continue;
                int id = o.getInt("id");
                int rc = o.optInt("rc", id);
                Intent i = new Intent(c, AlarmReceiver.class)
                        .putExtra("title", o.optString("title"))
                        .putExtra("body", o.optString("body"))
                        .putExtra("who", o.optString("who"))
                        .putExtra("room", o.optString("room"))
                        .putExtra("id", id);
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, PendingIntent.getBroadcast(c, rc, i, FLAGS));
                if (ids.length() > 0) ids.append(',');
                ids.append(rc);
            }
        } catch (Exception ignored) { }
        sp.edit().putString("ids", ids.toString()).apply();
    }

    static void reschedule(Context c) {
        schedule(c, c.getSharedPreferences("alarms", Context.MODE_PRIVATE).getString("json", "[]"));
    }

    static void clearAll(Context c) {
        c.getSystemService(NotificationManager.class).cancelAll();
    }
}
