package com.pinglunbu.photobackup;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.pinglunbu.photobackup.Consts.Dialogs;
import com.umeng.analytics.MobclickAgent;
import com.umeng.update.UmengUpdateAgent;

public class MainActivity extends PreferenceActivity {

    private PreferenceStorer storer;
    public StatusPreference statusPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UmengUpdateAgent.update(this);
        storer = ((PBApplication) (getApplication())).getStorer();

        addPreferencesFromResource(R.xml.preferences);
        statusPref = new StatusPreference(this);
        getPreferenceScreen().addPreference(statusPref);
        updateConnected().setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object change) {
                boolean newValue = (Boolean) change;
                if (newValue) {
                    startActivity(new Intent(MainActivity.this, AuthActivity.class));
                } else {
                    showDialog(Dialogs.DISCONNECT.ordinal());
                }
                return false;
            }

        });



    }

    private void startBackupService() {
        startService(new Intent(this, BackupService.class));
    }

    private void stopBackupService() {
        startService(new Intent(this, BackupService.class).setAction(Intent.ACTION_SHUTDOWN));
    }

    private void updateStatus() {
        statusPref.update();
    }

    private void updateVersion() {
        Preference version = getPreferenceManager().findPreference(PreferenceStorer.PREF_VERSION);
        version.setSummary(getString(R.string.ui_version_summay, getString(R.string.version)));

    }

    private CheckBoxPreference updateConnected() {
        CheckBoxPreference connected = (CheckBoxPreference) getPreferenceManager().findPreference(
                PreferenceStorer.PREF_CONNECT);

        connected.setChecked(storer.hasToken());

        final String userName = storer.getOAuthUserName();
        String summary = connected.isChecked() ? getString(R.string.ui_connect_summary_connected, userName)
                : getString(R.string.ui_connect_summary);
        connected.setSummary(summary);

        return connected;
    }

    private void updateAlbums() {
        Preference album = getPreferenceManager().findPreference(PreferenceStorer.PREF_ALBUM);

        String summary = storer.getSyncTargetAlbumId() > 0 ? getString(R.string.ui_choose_album_summary_selected,
                storer.getSyncTargetAlbumTitle()) : getString(R.string.ui_choose_album_summary);

        album.setSummary(summary);
    }

    @Override
    protected void onResume() {
        super.onResume();
        BackupService.activity = this;
        startBackupService();
        updateStatus();
        updateConnected();
        updateAlbums();
        updateVersion();
        checkFirstRun();
        MobclickAgent.onResume(this);
    }

    private void checkFirstRun() {
        if (storer.isReadyForBackup() && storer.getMaxSyncedAddDate() < 0) {
            Cursor cursor = BackupService.getCursor(getApplicationContext(), storer);
            if (cursor.getCount() > 0) {
                showDialog(Dialogs.FIRST_RUN.ordinal());
            } else {
                storer.setMaxSyncedAddDate(0);
            }
            cursor.close();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
    }


    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        switch (Dialogs.values()[id]) {
            case LOADING_ALBUM:
                dialog.setTitle(R.string.ui_choose_album_title);
                break;
            default:
                break;
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        switch (Dialogs.values()[id]) {
            case DISCONNECT:
                return new AlertDialog.Builder(this).setCustomTitle(null)
                        .setMessage(R.string.ui_dialog_disconnect_msg)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                storer.clearCredential();
                                updateConnected();
                                updateStatus();
                            }
                        })
                        .create();
            case FIRST_RUN:

                Dialog dialog = new AlertDialog.Builder(this).setCustomTitle(null)
                        .setMessage(
                                getString(R.string.ui_dialog_first_run_msg,
                                        BackupService.getCountOfNeedSyncItems(getApplicationContext(), storer)))
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                long maxSyncedAddDate = BackupService.getMaxAddDate(getApplicationContext(), storer);
                                storer.setMaxSyncedAddDate(maxSyncedAddDate);
                                startBackupService();
                                updateStatus();
                            }
                        })
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                storer.setMaxSyncedAddDate(0);
                                startBackupService();
                                updateStatus();
                            }
                        })
                        .create();

                return dialog;
            default:
                return null;
        }
    }

    class StatusPreference extends Preference implements OnClickListener {
        private View mView;

        private Button mSyncButton;

        private ImageView mStatusIcon;

        private TextView mStatusLabel;
        private TextView mSyncDetailsLabel;
        private TextView mSyncDetailsLabel2;

        private View mSyncDetails;

        private ProgressBar mProgressBar;
        private ProgressBar mCurPhotoProgressBar;

        public StatusPreference(Context context) {
            super(context);
            setSelectable(false);
            setOrder(0);

            if (mView == null) {
                mView = getLayoutInflater().inflate(R.layout.view_sync_status, null, false);
                mSyncButton = (Button) mView.findViewById(R.id.btn_sync);
                mSyncButton.setOnClickListener(this);


                mStatusIcon = (ImageView) mView.findViewById(R.id.ic_status);
                mStatusLabel = (TextView) mView.findViewById(R.id.tv_status);
                mSyncDetails = mView.findViewById(R.id.details_sync);
                mSyncDetailsLabel = (TextView) mSyncDetails.findViewById(R.id.tv_sync_detail);
                mSyncDetailsLabel2 = (TextView) mSyncDetails.findViewById(R.id.tv_sync_detail2);
                mProgressBar = (ProgressBar) mSyncDetails.findViewById(android.R.id.progress);
                mCurPhotoProgressBar = (ProgressBar) mSyncDetails.findViewById(R.id.pg_singlePhotoProgress);
            }
        }

        public void update() {
            if (mView == null)
                return;

            mCurPhotoProgressBar.setVisibility(View.GONE);
            mSyncDetailsLabel2.setVisibility(View.GONE);
            if (storer.isReadyForBackup() && storer.getMaxSyncedAddDate() > -1)
                mSyncButton.setEnabled(true);
            else {
                mStatusIcon.setImageResource(R.drawable.ic_idle);
                mStatusLabel.setTextColor(getResources().getColor(R.color.status_sync));
                mStatusLabel.setText(R.string.status_init);
                mSyncButton.setEnabled(false);
                return;
            }


            mProgressBar.setIndeterminate(false);
            mProgressBar.setProgress(BackupService.getCountOfSyncedItems());
            mProgressBar.setMax(BackupService.getCountOfNeedSyncItems());

            switch (BackupService.getStatus()) {
                case RUNNING:
                    mStatusIcon.setImageResource(R.drawable.ic_syncing);
                    mStatusLabel.setTextColor(getResources().getColor(R.color.status_sync));
                    mStatusLabel.setText(R.string.status_running);
                    // mSyncButton.setEnabled(false);
                    mSyncButton.setText(R.string.ui_sync_button_label_stop);
                    mCurPhotoProgressBar.setVisibility(View.VISIBLE);
                    mCurPhotoProgressBar.setIndeterminate(false);
                    mCurPhotoProgressBar.setProgress(BackupService.getSizeOfUploadedCurPhoto());
                    mCurPhotoProgressBar.setMax(BackupService.getSizeOfCurPhoto());
                    mSyncDetailsLabel.setText(getString(R.string.status_backup_details,
                            BackupService.getCountOfSyncedItems(), BackupService.getCountOfNeedSyncItems()));

                    mSyncDetailsLabel2.setVisibility(View.VISIBLE);
                    mSyncDetailsLabel2.setText(getString(R.string.status_backup_details2,
                            BackupService.getCurPhotoName()));
                    break;
                case ERROR:
                    mStatusIcon.setImageResource(R.drawable.ic_error);
                    mStatusLabel.setTextColor(getResources().getColor(R.color.status_error));
                    mStatusLabel.setText(R.string.status_error);
                    // mSyncButton.setEnabled(true);
                    mSyncButton.setText(R.string.ui_sync_button_label_start);
                    mSyncDetailsLabel.setText(BackupService.getMessage());
                    break;
                case DONE:
                    mStatusIcon.setImageResource(R.drawable.ic_done);
                    mStatusLabel.setTextColor(getResources().getColor(R.color.status_done));
                    mStatusLabel.setText(R.string.status_done);
                    // mSyncButton.setEnabled(true);
                    mSyncButton.setText(R.string.ui_sync_button_label_start);
                    mSyncDetailsLabel.setText(getString(R.string.status_backup_details,
                            BackupService.getCountOfSyncedItems(), BackupService.getCountOfNeedSyncItems()));

                    break;
                default:
                    mStatusIcon.setImageResource(R.drawable.ic_idle);
                    mStatusLabel.setTextColor(getResources().getColor(R.color.status_idle));
                    mStatusLabel.setText(R.string.status_idle);
                    // mSyncButton.setEnabled(true);
                    mSyncButton.setText(R.string.ui_sync_button_label_start);
                    mSyncDetailsLabel.setText(getString(R.string.status_backup_details,
                            BackupService.getCountOfSyncedItems(), BackupService.getCountOfNeedSyncItems()));

            }


        }

        @Override
        public void onClick(View v) {
            if (v == mSyncButton) {
                if (mSyncButton.getText().equals(getResources().getString(R.string.ui_sync_button_label_start))) {
                    startBackupService();
                } else if (mSyncButton.getText().equals(getResources().getString(R.string.ui_sync_button_label_stop))) {
                    stopBackupService();
                }
            }
        }

        @Override
        public View getView(View convertView, ViewGroup parent) {
            return mView;
        }

    }

}
