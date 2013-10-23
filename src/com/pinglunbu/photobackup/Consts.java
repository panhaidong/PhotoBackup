package com.pinglunbu.photobackup;

public class Consts {

    public static final String TAG = "photo_backup";

    public static final String API_KEY = "043c62969cd3e2840cb527c3d67ba78c";
    public static final String API_SECRET = "d00c6da5fcaf9ddc";

    public static final String AUTH_URL = "https://www.douban.com/service/auth2/auth";
    public static final String TOKEN_URL = "https://www.douban.com/service/auth2/token";
    protected static final String CALLBACK_URL = "http://photobackup";
    protected static final String OAUTH_VERIFIER = "code";

    public static enum Dialogs {
        ABOUT, DISCONNECT, REQUEST_TOKEN, CONNECT, LOADING_ALBUM, CREATE_ALBUM, CREATING_ALBUM, FIRST_RUN,
        CHOOSE_PUBLIC_ALBUM
    }

    public static enum ServiceStatus {
        IDLE, RUNNING, ERROR, DONE
    }



}
