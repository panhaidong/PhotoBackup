package com.pinglunbu.photobackup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class BackupBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = Consts.TAG;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Log.v(TAG, "onReceive(" + ctx + "," + intent + ")");

        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            AlarmTrigger.scheduleNormalBackup(ctx);
        } else if (intent.getAction().equals("com.android.camera.NEW_PICTURE")) {
            AlarmTrigger.scheduleNowBackup(ctx);
        } else if (intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE")) {
            ConnectivityManager conMan = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = conMan.getActiveNetworkInfo();
            if (netInfo != null && netInfo.getType() == ConnectivityManager.TYPE_WIFI)
                AlarmTrigger.scheduleNowBackup(ctx);
        }
    }
}
