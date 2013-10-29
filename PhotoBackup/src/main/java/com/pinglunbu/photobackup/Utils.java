package com.pinglunbu.photobackup;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import natalya.net.EasySSLSocketFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

public class Utils {
    private static int getOrientation(Map<String, String> info) {
        if (info.get(MediaStore.Images.Media.ORIENTATION) != null) {
            int orientation = Integer.parseInt(info.get(MediaStore.Images.Media.ORIENTATION));
            if (orientation != 0) {
                return orientation;
            }
        }
        return 0;
    }

    private static byte[] readBytes(Map<String, String> info) throws IOException {
        File file = new File(info.get(MediaStore.Images.Media.DATA));
        FileInputStream fin = new FileInputStream(file);
        byte fileContent[] = new byte[(int) file.length()];
        fin.read(fileContent);
        fin.close();
        return fileContent;
    }

    public static byte[] readFile(Map<String, String> info, Context mContext) throws Exception {
        byte[] bytes;
        int orientation = getOrientation(info);
        if (orientation != 0) {
            try {
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        Integer.parseInt(info.get(MediaStore.Images.Media._ID)));

                Bitmap bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), uri);
                if (bitmap == null)
                    return null;
                bitmap = rotate(bitmap, orientation);
                bytes = generateBitstream(bitmap, Bitmap.CompressFormat.JPEG, 85);
            } catch (Error error) {
                bytes = readBytes(info);
            }
        } else {
            bytes = readBytes(info);
        }
        return bytes;

    }

    public static void upload(String name, PreferenceStorer storer, Context mContext, byte[] bytes,
            DefaultHttpClient client, ProgressListener listener) throws Exception {
        String url = String.format("https://api.douban.com/v2/album/%s", storer.getSyncTargetAlbumId());

        HttpPost req = new HttpPost(url);

        ProgressableMultipartEntity mp = new ProgressableMultipartEntity(listener);
        try {
            mp.addPart("desc", new StringBody(name, Charset.forName(HTTP.UTF_8)));
        } catch (UnsupportedEncodingException e1) {
        }

        mp.addPart("image", new ByteArrayBody(bytes, "image/jpeg", name));

        req.setEntity(mp);
        req.addHeader("Authorization", String.format("Bearer %s", storer.getOAuthToken()));

        HttpResponse response = client.execute(req);
        checkStatusCode(response, mContext);
    }

    private static String checkStatusCode(HttpResponse response, Context mContext) throws Exception {
        final int statusCode = response.getStatusLine().getStatusCode();
        final HttpEntity entity = response.getEntity();
        String result = EntityUtils.toString(entity);

        Log.d(Consts.TAG, "statusCode = " + statusCode);
        if (statusCode > 300) {
            JSONObject json = new JSONObject(result);
            switch (json.optInt("code")) {
                case 1001:
                    throw new Exception(mContext.getString(R.string.error_no_album));
            }

        }
        return result;
    }

    public static String crawl(HttpUriRequest req, Context mContext) throws Exception {
        DefaultHttpClient client = Utils.getHttpClient();
        HttpResponse response = client.execute(req);
        String result = checkStatusCode(response, mContext);
        return result;
    }

    public static List<Album> loadAlbums(PreferenceStorer storer, Context mContext) throws Exception {
        String url = String.format("https://api.douban.com/v2/album/user_created/%s", storer.getOAuthUserId());
        HttpGet req = new HttpGet(url);

        req.addHeader("Authorization", String.format("Bearer %s", storer.getOAuthToken()));

        DefaultHttpClient client = Utils.getHttpClient();
        HttpResponse response = client.execute(req);
        String result = checkStatusCode(response, mContext);

        JSONObject json = new JSONObject(result);
        JSONArray array = json.getJSONArray("albums");

        List<Album> albums = new ArrayList<Album>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            Album album = new Album();
            album.id = o.optInt("id");
            album.title = o.optString("title");
            album.privacy = o.optString("privacy");
            albums.add(album);
        }
        return albums;
    }

    public static Album createAlbum(PreferenceStorer storer, String title, Context mContext) throws Exception {
        String url = String.format("https://api.douban.com/v2/albums");
        HttpPost req = new HttpPost(url);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("title", title));
        params.add(new BasicNameValuePair("privacy", "private"));
        req.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));

        req.addHeader("Authorization", String.format("Bearer %s", storer.getOAuthToken()));

        DefaultHttpClient client = Utils.getHttpClient();
        HttpResponse response = client.execute(req);
        String result = checkStatusCode(response, mContext);

        JSONObject json = new JSONObject(result);
        Album album = new Album();
        album.title = json.optString("title");
        album.id = json.optInt("id");
        album.privacy = json.optString("privacy");
        return album;

    }

    public static Bitmap rotate(Bitmap bmp, float angle) {
        Matrix matrixRotateLeft = new Matrix();
        matrixRotateLeft.setRotate(angle);
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrixRotateLeft, true);
    }

    public static byte[] generateBitstream(Bitmap src, Bitmap.CompressFormat format, int quality) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        src.compress(format, quality, os);
        return os.toByteArray();
    }

    private static final int DEFAULT_MAX_CONNECTIONS = 10;
    private static final int DEFAULT_SOCKET_TIMEOUT = 20 * 1000;
    private static final int DEFAULT_SOCKET_BUFFER_SIZE = 8192;

    private static int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private static int socketTimeout = DEFAULT_SOCKET_TIMEOUT;

    public static DefaultHttpClient getHttpClient() {

        BasicHttpParams httpParams = new BasicHttpParams();

        ConnManagerParams.setTimeout(httpParams, socketTimeout);
        ConnManagerParams.setMaxConnectionsPerRoute(httpParams, new ConnPerRouteBean(maxConnections));
        ConnManagerParams.setMaxTotalConnections(httpParams, DEFAULT_MAX_CONNECTIONS);

        HttpConnectionParams.setSoTimeout(httpParams, socketTimeout * 6);
        HttpConnectionParams.setConnectionTimeout(httpParams, socketTimeout);
        HttpConnectionParams.setTcpNoDelay(httpParams, true);
        HttpConnectionParams.setSocketBufferSize(httpParams, DEFAULT_SOCKET_BUFFER_SIZE);

        HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", new EasySSLSocketFactory(), 443));
        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(httpParams, schemeRegistry);

        DefaultHttpClient httpClient = new DefaultHttpClient(cm, httpParams);
        return httpClient;
    }

    public interface ProgressListener {
        void transferred(long num);
    }

    static class ProgressableMultipartEntity extends MultipartEntity {
        private ProgressListener listener;

        public ProgressableMultipartEntity(ProgressListener listener) {
            super();
            this.listener = listener;
        }

        @Override
        public void writeTo(final OutputStream outstream) throws IOException {
            super.writeTo(new HttpPostOutputStream(outstream, this.listener));
        }

        public class HttpPostOutputStream extends FilterOutputStream {
            private final ProgressListener listener;
            private long transferred;

            public HttpPostOutputStream(final OutputStream out, final ProgressListener listener) {
                super(out);
                this.listener = listener;
                this.transferred = 0;
            }

            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
                this.transferred += len;
                if (this.listener != null)
                    this.listener.transferred(this.transferred);
            }

            public void write(int b) throws IOException {
                out.write(b);
                this.transferred++;
                if (this.listener != null)
                    this.listener.transferred(this.transferred);
            }
        }
    }

}
