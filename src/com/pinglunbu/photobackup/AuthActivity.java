package com.pinglunbu.photobackup;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.pinglunbu.photobackup.Consts.Dialogs;

public class AuthActivity extends Activity {
    private WebView mWebview;
    private ProgressDialog mProgress;
    private PreferenceStorer storer;

    public static final String TAG = Consts.TAG;

    @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_auth);


        String authUrl = String.format("%s?client_id=%s&redirect_uri=%s&response_type=code", Consts.AUTH_URL,
                Consts.API_KEY, Consts.CALLBACK_URL);

        mWebview = (WebView) findViewById(R.id.webview);
        mWebview.getSettings().setJavaScriptEnabled(true);
        mWebview.getSettings().setLoadsImagesAutomatically(true);
        storer = ((PBApplication) (getApplication())).getStorer();
        mProgress = new ProgressDialog(this);
        mProgress.setIndeterminate(true);
        mProgress.setMessage(getString(R.string.ui_connect_logining));
        mProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mWebview.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        mWebview.clearSslPreferences();

        mWebview.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showConnectionError(description);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Log.w(TAG, "onReceiveSslError(" + error + ")");
                // pre-froyo devices don't trust the cert used by google
                // see
                // https://knowledge.verisign.com/support/mpki-for-ssl-support/index?page=content&id=SO17511&actp=AGENT_REFERAL
                if (error.getPrimaryError() == SslError.SSL_IDMISMATCH
                        && Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
                    handler.proceed();
                } else {
                    handler.cancel();
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!isFinishing())
                    mProgress.show();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!isFinishing())
                    mProgress.dismiss();
            }

            @Override
            public boolean shouldOverrideUrlLoading(final WebView view, String url) {
                if (url.startsWith(Consts.CALLBACK_URL)) {
                    String verifier = Uri.parse(url).getQueryParameter(Consts.OAUTH_VERIFIER);
                    new RequestTokenTask().execute(verifier);
                    return true;
                } else {
                    return false;
                }
            }
        });
        removeAllCookies();
        mWebview.loadUrl(authUrl);
    }

    class RequestTokenTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showDialog(Dialogs.REQUEST_TOKEN.ordinal());
        }

        @Override
        protected String doInBackground(String... verifier) {

            try {
                HttpPost req = new HttpPost(Consts.TOKEN_URL);
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair("client_id", Consts.API_KEY));
                params.add(new BasicNameValuePair("client_secret", Consts.API_SECRET));
                params.add(new BasicNameValuePair("redirect_uri", Consts.CALLBACK_URL));
                params.add(new BasicNameValuePair("grant_type", "authorization_code"));
                params.add(new BasicNameValuePair("code", verifier[0]));
                req.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));

                String resp = Utils.crawl(req, getApplicationContext());

                try {
                    JSONObject json = new JSONObject(resp);
                    String accessToken = json.optString("access_token");
                    int expiresIn = json.optInt("expires_in");
                    String refreshToken = json.optString("refresh_token");
                    String userId = json.optString("douban_user_id");
                    String userName = json.optString("douban_user_name");


                    Log.d(TAG, "userId=" + userId);

                    storer.setOAuthToken(accessToken, refreshToken, expiresIn);
                    storer.setOAuthUser(userId, userName);

                } catch (Exception e) {
                    ((PBApplication) getApplication()).showErrorDialog(AuthActivity.this, e.getMessage());
                    finish();
                }

                return resp;


            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }

        }

        @Override
        protected void onPostExecute(String resp) {
            dismissDialog(Dialogs.REQUEST_TOKEN.ordinal());
            Intent intent = new Intent(AuthActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }


    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        switch (Dialogs.values()[id]) {
            case REQUEST_TOKEN:
                ProgressDialog req = new ProgressDialog(this);
                req.setMessage(getString(R.string.ui_connect_ing));
                req.setIndeterminate(true);
                req.setCancelable(false);
                return req;
            default:
                return null;
        }
    }

    private void showConnectionError(final String message) {
        if (isFinishing())
            return;
        new AlertDialog.Builder(this).setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .create()
                .show();
    }

    private void removeAllCookies() {
        CookieSyncManager.createInstance(this);
        CookieManager.getInstance().removeAllCookie();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWebview.stopLoading();
        mProgress.dismiss();
    }
}
