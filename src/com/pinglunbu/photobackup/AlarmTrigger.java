package com.pinglunbu.photobackup;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlarmTrigger {

    static final int NORMAL = 1;

    private static final String TAG = "Alarm";

    static void scheduleNormalBackup(Context ctx) {
        scheduleSync(ctx, 1800, NORMAL);
    }

    static void scheduleNowBackup(Context ctx) {
        scheduleSync(ctx, 300, NORMAL);
    }

    static void cancel(Context ctx) {
        getAlarmManager(ctx).cancel(PendingIntent.getService(ctx, 0, new Intent(ctx, BackupService.class), 0));
    }


    private static void scheduleSync(Context ctx, int inSeconds, int source) {

        Log.v(TAG, "scheduleSync(" + ctx + ", " + inSeconds + ", " + source + ")");

        if (inSeconds > 0) {
            final long atTime = System.currentTimeMillis() + (inSeconds * 1000l);
            PendingIntent intent = PendingIntent.getService(ctx, 0, new Intent(ctx, BackupService.class), 0);
            getAlarmManager(ctx).set(AlarmManager.RTC_WAKEUP, atTime, intent);
        }
    }

    private static AlarmManager getAlarmManager(Context ctx) {
        return (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    }
}
