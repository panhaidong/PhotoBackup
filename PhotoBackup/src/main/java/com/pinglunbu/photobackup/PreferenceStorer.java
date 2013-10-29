package com.pinglunbu.photobackup;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class PreferenceStorer {
    public static final String PREF_CONNECT = "connected";
    public static final String PREF_ALBUM = "album";
    public static final String PREF_WIFI_ONLY = "wifi_only";
    public static final String PREF_VERSION = "version";

    public static final String PREF_OAUTH2_TOKEN = "oauth2_token";
    public static final String PREF_OAUTH2_REFRESH_TOKEN = "oauth2_refresh_token";
    public static final String PREF_OAUTH2_EXPIRES = "oauth2_expires_in";
    public static final String PREF_OAUTH2_USER_ID = "oauth2_user_id";
    public static final String PREF_OAUTH2_USER_NAME = "oauth2_user_name";



    static final String PREF_ALBUM_TITLE = "sync_album_title";
    static final String PREF_ALBUM_ID = "sync_album_id";

    static final String PREF_MAX_SYNCED_ADD_DATE = "sync_max_synced_add_date";

    private Context mContext;

    public PreferenceStorer(Context context) {
        this.mContext = context;
    }

    public void setOAuthToken(String accessToken, String refreshToken, int expiresIn) {
        getCredential().edit()
                .putString(PREF_OAUTH2_TOKEN, accessToken)
                .putString(PREF_OAUTH2_REFRESH_TOKEN, refreshToken)
                .putInt(PREF_OAUTH2_EXPIRES, expiresIn)
                .commit();
    }

    public void setOAuthUser(String userId, String userName) {
        getCredential().edit()
                .putString(PREF_OAUTH2_USER_ID, userId)
                .putString(PREF_OAUTH2_USER_NAME, userName)
                .commit();
    }


    SharedPreferences getCredential() {
        return mContext.getSharedPreferences("credential", Context.MODE_PRIVATE);
    }

    SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public boolean hasToken() {
        return !getCredential().getString(PREF_OAUTH2_TOKEN, "").equals("");
    }

    public String getOAuthUserName() {
        return getCredential().getString(PREF_OAUTH2_USER_NAME, "");
    }

    public String getOAuthUserId() {
        return getCredential().getString(PREF_OAUTH2_USER_ID, "");
    }

    public String getOAuthToken() {
        return getCredential().getString(PREF_OAUTH2_TOKEN, "");
    }

    public String getOAuthRefreshToken() {
        return getCredential().getString(PREF_OAUTH2_REFRESH_TOKEN, "");
    }

    public int getOAuthExpiresIn() {
        return getCredential().getInt(PREF_OAUTH2_EXPIRES, 0);
    }

    public void setSyncTargetAlbumId(int id) {
        getPrefs().edit().putInt(PREF_ALBUM_ID, id).commit();
    }

    public int getSyncTargetAlbumId() {
        return getPrefs().getInt(PREF_ALBUM_ID, -1);
    }

    public void setSyncTargetAlbumTitle(String title) {
        getPrefs().edit().putString(PREF_ALBUM_TITLE, title).commit();
    }

    public String getSyncTargetAlbumTitle() {
        return getPrefs().getString(PREF_ALBUM_TITLE, "");
    }

    public void setMaxSyncedAddDate(long maxSyncedAddDate) {
        getPrefs().edit().putLong(PREF_MAX_SYNCED_ADD_DATE, maxSyncedAddDate).commit();
    }

    public long getMaxSyncedAddDate() {
        return getPrefs().getLong(PREF_MAX_SYNCED_ADD_DATE, -1);
    }

    public boolean isWifiOnly(BackupService syncService) {
        return (getPrefs().getBoolean(PREF_WIFI_ONLY, false));
    }

    public boolean isReadyForBackup() {
        return getOAuthUserId().length() > 0 && getSyncTargetAlbumId() > 0;
    }

    public void clearCredential() {
        getCredential().edit()
                .remove(PREF_OAUTH2_TOKEN)
                .remove(PREF_OAUTH2_REFRESH_TOKEN)
                .remove(PREF_OAUTH2_EXPIRES)
                .remove(PREF_OAUTH2_USER_ID)
                .remove(PREF_OAUTH2_USER_NAME)
                .commit();

        getPrefs().edit().remove(PREF_ALBUM_ID).remove(PREF_ALBUM_TITLE).remove(PREF_MAX_SYNCED_ADD_DATE).commit();
    }
}
