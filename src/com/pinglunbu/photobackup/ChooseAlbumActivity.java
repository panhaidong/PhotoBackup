package com.pinglunbu.photobackup;

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.TextView;

import com.pinglunbu.photobackup.Consts.Dialogs;

public class ChooseAlbumActivity extends ListActivity {

    PreferenceStorer storer;
    private List<Album> albums = new ArrayList<Album>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            setTheme(android.R.style.Theme_Holo_Dialog);
        super.onCreate(savedInstanceState);


        storer = ((PBApplication) (getApplication())).getStorer();
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                showDialog(Dialogs.LOADING_ALBUM.ordinal());
            }

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    albums.clear();
                    albums.addAll(Utils.loadAlbums(storer, getApplicationContext()));
                } catch (Exception e) {
                    e.printStackTrace();
                    ((PBApplication) getApplication()).showErrorDialog(ChooseAlbumActivity.this, e.getMessage());
                    finish();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                dismissDialog(Dialogs.LOADING_ALBUM.ordinal());

                getListView().setAdapter(new BaseAdapter() {

                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {

                        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
                        TextView title = (TextView) view.findViewById(android.R.id.text1);
                        TextView summary = (TextView) view.findViewById(android.R.id.text2);
                        if (position < albums.size() && storer.getSyncTargetAlbumId() == albums.get(position).id) {
                            title.setTypeface(null, Typeface.BOLD);
                            summary.setTypeface(null, Typeface.BOLD);
                        } else {
                            title.setTypeface(null, Typeface.NORMAL);
                            summary.setTypeface(null, Typeface.NORMAL);
                        }


                        if (position < albums.size()) {
                            title.setText(albums.get(position).title);
                            String privacy = getPrivacy(albums.get(position).privacy);
                            summary.setText(privacy);
                        } else {
                            title.setText(R.string.ui_album_create);
                        }
                        return view;
                    }



                    @Override
                    public long getItemId(int position) {
                        // TODO Auto-generated method stub
                        return 0;
                    }

                    @Override
                    public Object getItem(int position) {
                        // TODO Auto-generated method stub
                        return null;
                    }

                    @Override
                    public int getCount() {
                        return albums.size() + 1;
                    }
                });

                getListView().setOnItemClickListener(new OnItemClickListener() {

                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                        if (position < albums.size()) {
                            if ("private".equals(albums.get(position).privacy)) {
                                storer.setSyncTargetAlbumId(albums.get(position).id);
                                storer.setSyncTargetAlbumTitle(albums.get(position).title);
                                finish();
                            } else {
                                String privacy = getPrivacy(albums.get(position).privacy);
                                Dialog dialog = new AlertDialog.Builder(ChooseAlbumActivity.this).setCustomTitle(null)
                                        .setMessage(getString(R.string.ui_dialog_choose_public_album_warn, privacy))
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                storer.setSyncTargetAlbumId(albums.get(position).id);
                                                storer.setSyncTargetAlbumTitle(albums.get(position).title);
                                                finish();
                                            }
                                        })
                                        .create();
                                dialog.show();
                            }

                        } else {
                            showDialog(Dialogs.CREATE_ALBUM.ordinal());
                        }

                    }
                });

            }

        }.execute();

    }

    private String getPrivacy(String p) {
        String privacy = getString(R.string.ui_album_privacy_public);
        if ("private".equalsIgnoreCase(p)) {
            privacy = getString(R.string.ui_album_privacy_private);
        } else if ("friend".equalsIgnoreCase(p)) {
            privacy = getString(R.string.ui_album_privacy_friend);
        }
        return privacy;
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        switch (Dialogs.values()[id]) {
            case LOADING_ALBUM:
                ProgressDialog req = new ProgressDialog(this);
                req.setMessage(getString(R.string.ui_loading_album));
                req.setIndeterminate(true);
                req.setCancelable(false);
                return req;
            case CREATING_ALBUM:
                req = new ProgressDialog(this);
                req.setMessage(getString(R.string.ui_album_create));
                req.setIndeterminate(true);
                req.setCancelable(false);
                return req;
            case CREATE_ALBUM:
                View view = getLayoutInflater().inflate(R.layout.view_create_album, null);
                final EditText et_albumTitle = (EditText) view.findViewById(R.id.et_albumTitle);

                return new AlertDialog.Builder(this).setTitle(R.string.ui_album_create_title)
                        .setView(view)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                new AsyncTask<Void, Void, Void>() {

                                    @Override
                                    protected void onPreExecute() {
                                        showDialog(Dialogs.LOADING_ALBUM.ordinal());
                                    }

                                    @Override
                                    protected Void doInBackground(Void... params) {
                                        try {
                                            String title = et_albumTitle.getText().toString().trim();
                                            if (title.length() > 0) {
                                                Album album = Utils.createAlbum(storer, title, getApplicationContext());
                                                storer.setSyncTargetAlbumId(album.id);
                                                storer.setSyncTargetAlbumTitle(album.title);
                                            }
                                        } catch (Exception e) {
                                            ((PBApplication) getApplication()).showErrorDialog(
                                                    ChooseAlbumActivity.this, e.getMessage());
                                            finish();
                                        }
                                        return null;
                                    }

                                    @Override
                                    protected void onPostExecute(Void v) {
                                        dismissDialog(Dialogs.LOADING_ALBUM.ordinal());
                                        finish();
                                    }

                                }.execute();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        })
                        .create();

            default:
                return null;
        }
    }
}
