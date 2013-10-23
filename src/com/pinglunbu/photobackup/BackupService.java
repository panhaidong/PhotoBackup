package com.pinglunbu.photobackup;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;

import com.pinglunbu.photobackup.Consts.ServiceStatus;
import com.pinglunbu.photobackup.Utils.ProgressListener;

public class BackupService extends Service {
    private static final String TAG = Consts.TAG;
    protected PowerManager.WakeLock sWakeLock;
    protected WifiManager.WifiLock sWifiLock;

    private static ServiceStatus status = ServiceStatus.IDLE;
    private static PreferenceStorer storer;

    public static MainActivity activity;
    private static String message;

    private static int countOfNeedSyncItems;
    private static int countOfSyncedItems;
    private static int sizeOfCurPhoto;
    private static int sizeOfUploadedCurPhoto;
    private static String curPhotoName;

    public static String getCurPhotoName() {
        return curPhotoName;
    }

    public static int getCountOfNeedSyncItems() {
        return countOfNeedSyncItems;
    }

    public static int getCountOfSyncedItems() {
        return countOfSyncedItems;
    }

    public static int getSizeOfCurPhoto() {
        return sizeOfCurPhoto;
    }

    public static int getSizeOfUploadedCurPhoto() {
        return sizeOfUploadedCurPhoto;
    }


    public static ServiceStatus getStatus() {
        if (status == ServiceStatus.IDLE) {
            if (getCountOfNeedSyncItems() == 0)
                status = ServiceStatus.DONE;
        } else if (status == ServiceStatus.DONE) {
            if (getCountOfNeedSyncItems() > 0)
                status = ServiceStatus.IDLE;
        }
        return status;

    }

    public static String getMessage() {
        return message;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        handleIntent(intent);
        return START_NOT_STICKY;
    }

    SyncTask currentTask;

    protected void handleIntent(final Intent intent) {
        Log.d(TAG, intent.toString());
        storer = new PreferenceStorer(getApplicationContext());
        if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
            Log.d(TAG, "shutdown service...");

            AlarmTrigger.cancel(getApplicationContext());
            status = ServiceStatus.IDLE;
            stopSelf();
            activity.statusPref.update();
        } else {
            synchronized (BackupService.class) {
                if (status != ServiceStatus.RUNNING) {
                    Log.d(TAG, "starting task...");
                    if (storer.isReadyForBackup() && storer.getMaxSyncedAddDate() > -1) {
                        status = ServiceStatus.RUNNING;
                        if (currentTask == null || currentTask.getStatus() == Status.FINISHED) {
                            currentTask = new SyncTask();
                            currentTask.execute();
                        }
                    }
                }
            }
        }
    }

    private class SyncTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            publishProgress();
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, "Service doInBackground... start... " + this);
            Cursor cursor = getCursor(getApplicationContext(), storer);
            countOfNeedSyncItems = cursor.getCount();
            countOfSyncedItems = 0;
            sizeOfCurPhoto = 0;
            sizeOfUploadedCurPhoto = 0;
            publishProgress();

            try {
                if (countOfNeedSyncItems > 0) {
                    acquireLocks(true);

                    DefaultHttpClient client = Utils.getHttpClient();
                    client.setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy() {

                        @Override
                        public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                            long keepAlive = super.getKeepAliveDuration(response, context);
                            if (keepAlive == -1) {
                                keepAlive = 10000;
                            }
                            return keepAlive;
                        }

                    });

                    while (cursor.moveToNext() && status == ServiceStatus.RUNNING) {

                        Map<String, String> info = new HashMap<String, String>();
                        for (int i = 0; i < cursor.getColumnCount(); i++) {
                            info.put(cursor.getColumnName(i), cursor.getString(i));
                        }
                        curPhotoName = info.get(MediaStore.Images.Media.DISPLAY_NAME);
                        Log.d(TAG, "Service doInBackground... upload..." + info.get(MediaStore.Images.Media.TITLE)
                                + " &&& " + this);

                        byte[] bytes = Utils.readFile(info, getApplicationContext());

                        if (bytes == null || bytes.length == 0)
                            continue;

                        sizeOfCurPhoto = bytes.length;
                        publishProgress();
                        Utils.upload(curPhotoName, storer, getApplicationContext(), bytes, client,
                                new ProgressListener() {

                                    @Override
                                    public void transferred(long num) {
                                        sizeOfUploadedCurPhoto = (int) num;
                                        publishProgress();
                                    }
                                });

                        storer.setMaxSyncedAddDate(Math.max(
                                Long.parseLong(info.get(MediaStore.Images.Media.DATE_ADDED)),
                                storer.getMaxSyncedAddDate()));
                        countOfSyncedItems++;
                        publishProgress();
                    }
                }
                status = ServiceStatus.DONE;
                AlarmTrigger.scheduleNormalBackup(getApplicationContext());
            } catch (Exception e) {
                e.printStackTrace();
                message = e.getMessage();
                status = ServiceStatus.ERROR;
                AlarmTrigger.scheduleNowBackup(getApplicationContext());
            } finally {
                releaseLocks();
            }

            publishProgress();
            try {
                cursor.close();
            } catch (Exception e) {

            }
            Log.d(TAG, "Service doInBackground...end");
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... params) {
            if (activity != null) {
                activity.statusPref.update();
            }
        }

        @Override
        protected void onPostExecute(Void d) {
            countOfNeedSyncItems = 0;
            countOfSyncedItems = 0;
            sizeOfCurPhoto = 0;
            sizeOfUploadedCurPhoto = 0;

        }
    }

    public static int getCountOfNeedSyncItems(Context mContext, PreferenceStorer storer) {
        Cursor cursor = getCursor(mContext, storer);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    public static long getMaxAddDate(Context mContext, PreferenceStorer storer) {
        Cursor cursor = getCursor(mContext, storer);
        long maxSyncedAddDate = 0;
        while (cursor.moveToNext()) {

            Map<String, String> info = new HashMap<String, String>();
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                info.put(cursor.getColumnName(i), cursor.getString(i));
            }
            maxSyncedAddDate = Math.max(Long.parseLong(info.get(MediaStore.Images.Media.DATE_ADDED)),
                    storer.getMaxSyncedAddDate());
        }

        cursor.close();
        return maxSyncedAddDate;
    }

    public static Cursor getCursor(Context mContext, PreferenceStorer storer) {
        return mContext.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null,
                String.format("%s > ?", MediaStore.Images.Media.DATE_ADDED),
                new String[] { String.valueOf(storer.getMaxSyncedAddDate()) }, MediaStore.Images.Media.DATE_ADDED);
    }

    protected NotificationManager getNotifier() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    protected ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    protected void acquireLocks(boolean background) throws Exception {
        if (sWakeLock == null) {
            PowerManager pMgr = (PowerManager) getSystemService(POWER_SERVICE);
            sWakeLock = pMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }
        sWakeLock.acquire();

        WifiManager wMgr = (WifiManager) getSystemService(WIFI_SERVICE);
        if (wMgr.isWifiEnabled() && getConnectivityManager().getNetworkInfo(ConnectivityManager.TYPE_WIFI) != null
                && getConnectivityManager().getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {

            if (sWifiLock == null) {
                sWifiLock = wMgr.createWifiLock(TAG);
            }
            sWifiLock.acquire();
        } else if (background && storer.isWifiOnly(this)) {
            throw new Exception(getString(R.string.error_wifi_only_no_connection));
        }

        NetworkInfo active = getConnectivityManager().getActiveNetworkInfo();

        if (active == null || !active.isConnectedOrConnecting()) {
            throw new Exception(getString(R.string.error_no_connection));
        }
    }

    protected void releaseLocks() {
        if (sWakeLock != null && sWakeLock.isHeld())
            sWakeLock.release();
        if (sWifiLock != null && sWifiLock.isHeld())
            sWifiLock.release();
    }
}
