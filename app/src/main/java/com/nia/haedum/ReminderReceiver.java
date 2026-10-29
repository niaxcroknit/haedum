package com.nia.haedum;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 예약해 둔 알람이 울릴 때, 그리고 폰 재부팅·시간 변경·앱 업데이트 후 알람을 다시 걸 때 호출됨 */
public class ReminderReceiver extends BroadcastReceiver {
    static final String ACTION_FIRE = "com.nia.haedum.FIRE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_FIRE.equals(intent.getAction())) {
            Reminders.fire(context);
        } else {
            Reminders.reschedule(context);
        }
    }
}
