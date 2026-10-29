package com.nia.haedum;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 주기 초과 알림.
 * 집안일마다 "마지막으로 한 날 + 주기 + 1일" 오전 9시에 한 번 알려줌. (예: 30일 주기 → 31일째 9시)
 * 서버 없이 폰 안에서만 동작. 정확한 알람 권한 없이 쓰는 방식이라 몇 분 늦게 올 수 있음.
 */
final class Reminders {
    static final String PREFS = "haedum";
    private static final String KEY_CHORES = "chores";
    private static final String CHANNEL = "overdue";
    private static final LocalTime NOTIFY_AT = LocalTime.of(9, 0);

    private Reminders() { }

    static void save(Context c, String json) {
        prefs(c).edit().putString(KEY_CHORES, json == null ? "[]" : json).apply();
    }

    static boolean hasCycle(String json) {
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) if (a.getJSONObject(i).optInt("cycle", 0) > 0) return true;
        } catch (Exception ignored) { }
        return false;
    }

    /** 이 집안일의 알림 날짜 (주기를 넘긴 첫날). 주기가 없으면 null */
    private static LocalDate dueDate(JSONObject o) {
        int cycle = o.optInt("cycle", 0);
        String last = o.optString("last", "");
        if (cycle <= 0 || last.isEmpty()) return null;
        try {
            return LocalDate.parse(last).plusDays(cycle + 1L);
        } catch (Exception e) {
            return null;
        }
    }

    private static String sentKey(JSONObject o) {
        return "sent:" + o.optString("id") + ":" + o.optString("last");
    }

    /** 다음 알림 시각에 알람 하나만 걸어 둠. 알람이 울리면 fire() 가 알림을 보내고 다시 여기로 옴 */
    static void reschedule(Context c) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = null;
        SharedPreferences p = prefs(c);
        try {
            JSONArray a = new JSONArray(p.getString(KEY_CHORES, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                LocalDate due = dueDate(o);
                if (due == null) continue;
                LocalDateTime at = due.atTime(NOTIFY_AT);
                if (due.equals(now.toLocalDate()) && !at.isAfter(now)) {
                    // 오늘이 알림 날인데 9시가 지났고 아직 안 보냈으면 바로 보냄
                    if (!p.getBoolean(sentKey(o), false)) at = now.plusSeconds(5);
                    else continue;
                } else if (!at.isAfter(now)) {
                    continue; // 이미 지난 날은 다시 안 알림
                }
                if (next == null || at.isBefore(next)) next = at;
            }
        } catch (Exception ignored) { }

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(c);
        am.cancel(pi);
        if (next == null) return;
        long when = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }

    /** 알람이 울렸을 때: 오늘이 알림 날인 집안일을 알림으로 보내고 다음 알람을 검 */
    static void fire(Context c) {
        LocalDate today = LocalDate.now();
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor ed = p.edit();
        try {
            JSONArray a = new JSONArray(p.getString(KEY_CHORES, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                LocalDate due = dueDate(o);
                if (due == null || !due.equals(today) || p.getBoolean(sentKey(o), false)) continue;
                notify(c, o);
                ed.putBoolean(sentKey(o), true);
            }
        } catch (Exception ignored) { }
        ed.apply();
        reschedule(c);
    }

    private static void notify(Context c, JSONObject o) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        ensureChannel(c);
        String emoji = o.optString("emoji", "");
        String name = o.optString("name", "집안일");
        int cycle = o.optInt("cycle", 0);
        LocalDate last = LocalDate.parse(o.optString("last"));
        String title = (emoji.isEmpty() ? "" : emoji + " ") + name + ", 할 때 됐어요";
        String text = cycle + "일 주기를 넘겼어요 · 마지막 " + last.getMonthValue() + "월 " + last.getDayOfMonth() + "일";

        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_haedum)
                .setColor(ContextCompat.getColor(c, R.color.icon_bg))
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            NotificationManagerCompat.from(c).notify(o.optString("id").hashCode(), b.build());
        } catch (SecurityException ignored) { }
    }

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(c.getString(R.string.channel_desc));
        nm.createNotificationChannel(ch);
    }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ReminderReceiver.ACTION_FIRE);
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
