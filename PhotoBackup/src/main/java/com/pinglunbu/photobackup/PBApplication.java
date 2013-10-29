package com.pinglunbu.photobackup;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

public class PBApplication extends Application {
    private PreferenceStorer storer;

    @Override
    public void onCreate() {
        super.onCreate();
        storer = new PreferenceStorer(getApplicationContext());
    }

    public PreferenceStorer getStorer() {
        return storer;
    }

    private static Handler exceptionHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            String message = msg.getData().getString("msg");
            if (message != null) {
                try {
                    Activity act = (Activity) msg.obj;
                    Toast.makeText(act, message, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                }
            }
        }
    };

    public void showErrorDialog(Activity act, String message) {
        Message msg = new Message();
        Bundle bundle = new Bundle();
        bundle.putString("msg", message);
        msg.setData(bundle);
        msg.obj = act;
        exceptionHandler.sendMessage(msg);
    }

}
