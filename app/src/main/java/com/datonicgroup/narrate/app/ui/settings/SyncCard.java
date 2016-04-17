package com.datonicgroup.narrate.app.ui.settings;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.datonicgroup.narrate.app.R;
import com.datonicgroup.narrate.app.dataprovider.Settings;
import com.datonicgroup.narrate.app.dataprovider.api.googledrive.GoogleAccountsService;
import com.datonicgroup.narrate.app.dataprovider.providers.Contract;
import com.datonicgroup.narrate.app.dataprovider.sync.GoogleDriveSyncService;
import com.datonicgroup.narrate.app.dataprovider.sync.SyncHelper;
import com.datonicgroup.narrate.app.models.User;
import com.datonicgroup.narrate.app.ui.GlobalApplication;
import com.datonicgroup.narrate.app.ui.dialogs.AutoSyncIntervalDialog;
import com.datonicgroup.narrate.app.util.DateUtil;
import com.datonicgroup.narrate.app.util.PermissionsUtil;
import com.datonicgroup.narrate.app.util.SettingsUtil;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.UserRecoverableAuthException;

/**
 * Created by timothymiko on 1/6/15.
 */
public class SyncCard extends PreferenceCard implements View.OnClickListener {

    public static final int GDRIVE_REQUEST_AUTHORIZATION = 100;

    private SwitchPreference mGoogleDrivePref;
    private SwitchPreference mWifiSyncPref;
    private ButtonPreference mAutoSyncInterval;
    private ButtonPreference mGoogleDriveTest;

    private FragmentActivity mActivity;

    private AutoSyncIntervalDialog mIntervalDialog;

    public SyncCard(FragmentActivity activity) {
        super(activity);
        this.mActivity = activity;
    }

    @Override
    protected void init() {
        super.init();

        setTitle(R.string.sync);
        setSwitchEnabled(true);

        mIntervalDialog = new AutoSyncIntervalDialog();
        mIntervalDialog.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (Settings.getAutoSyncInterval() == -1)
                    mAutoSyncInterval.setButtonText(R.string.none);
                else {
                    mAutoSyncInterval.setButtonText(Settings.getAutoSyncInterval() / DateUtil.HOUR_IN_SECONDS + " " + getResources().getString(R.string.hours));
                }
            }
        });

        mGoogleDrivePref = new SwitchPreference(getContext());
        mWifiSyncPref = new SwitchPreference(getContext());
        mAutoSyncInterval = new ButtonPreference(getContext());

        mGoogleDrivePref.setTitle(R.string.google_drive_title);
        mWifiSyncPref.setTitle(R.string.sync_data_network_descrip);
        mAutoSyncInterval.setTitle(R.string.sync_interval);

        mGoogleDrivePref.setTag(0);
        mWifiSyncPref.setTag(2);
        mAutoSyncInterval.setTag(4);

        mGoogleDrivePref.setOnCheckedChangedListener(this);
        mWifiSyncPref.setOnCheckedChangedListener(this);

        mGoogleDrivePref.setOnCheckedChangedListener(this);
        mWifiSyncPref.setOnCheckedChangedListener(this);

        if (Settings.getAutoSyncInterval() == -1)
            mAutoSyncInterval.setButtonText(R.string.none);
        else {
            mAutoSyncInterval.setButtonText(Settings.getAutoSyncInterval() / DateUtil.HOUR_IN_SECONDS + " " + getResources().getString(R.string.hours));
        }

        mAutoSyncInterval.setOnClickListener(this);

        addView(mGoogleDrivePref);
        addView(mWifiSyncPref);
        addView(mAutoSyncInterval);

        mTitle.setChecked(Settings.getSyncEnabled());
        mGoogleDrivePref.setChecked(Settings.getGoogleDriveSyncEnabled());
        mWifiSyncPref.setChecked(Settings.getSyncOnMobileData());
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.settings_title:
                if (PermissionsUtil.checkAndRequest(mActivity, Manifest.permission.GET_ACCOUNTS, 100, R.string.permission_explanation_get_accounts, null)) {
                    if (PermissionsUtil.checkAndRequest(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE, 100, R.string.permission_explanation_write_storage, null)) {
                        super.onCheckedChanged(buttonView, isChecked);
                        Settings.setSyncEnabled(isChecked);
                        if (isChecked) {
                            if (Settings.getGoogleDriveSyncEnabled()) {
                                enableSync();
                            }
                        } else {
                            cancelSync();
                        }
                    } else {
                        mTitle.setChecked(false);
                    }
                } else {
                    mTitle.setChecked(false);
                }
                return;
        }

        super.onCheckedChanged(buttonView, isChecked);
        switch ((Integer) buttonView.getTag()) {
            case 0:
                if (PermissionsUtil.checkAndRequest(mActivity, Manifest.permission.GET_ACCOUNTS, 100, R.string.permission_explanation_get_accounts, null)) {
                    onGoogleDriveChanged(isChecked);
                } else {
                    mGoogleDrivePref.setChecked(false);
                }
                break;
            case 1:
                break;
            case 2:
                Settings.setSyncOnMobileData(isChecked);
                break;
        }
    }

    @Override
    public void onClick(View v) {
        switch ((Integer) v.getTag()) {
            case 3:
                break;
            case 4:
                mIntervalDialog.show(mActivity.getSupportFragmentManager(), "SyncIntervalDialog");
                break;
        }
    }

    private void onGoogleDriveChanged(boolean enabled) {
        if (enabled) {
            if (!Settings.getGoogleDriveSyncEnabled()) {
                new Thread(mEnableDriveSyncRunnable).start();
            }
        } else {
            if (Settings.getGoogleDriveSyncEnabled()) {
                Settings.setGoogleDriveSyncEnabled(false);
                cancelSync();
            }
        }
    }

    private Runnable mEnableDriveSyncRunnable = new Runnable() {
        @Override
        public void run() {
            try {

                String email = Settings.getEmail();
                Settings.setGoogleAccountName(email);

                GoogleAccountsService.getAuthToken("https://www.googleapis.com/auth/drive.appfolder");

                GoogleDriveSyncService.shared().setup(new GoogleDriveSyncService.GoogleDriveSyncSetupInterface() {
                    @Override
                    public void onSetupComplete() {

                        Settings.setGoogleDriveSyncEnabled(true);

                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                enableSync();
                            }
                        });
                    }

                    @Override
                    public void onSetupFailure() {

                        Settings.setGoogleDriveSyncEnabled(false);
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mGoogleDrivePref.setChecked(false);
                            }
                        });
                    }
                });

            } catch (final UserRecoverableAuthException e) {
                e.printStackTrace();
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mGoogleDrivePref.setChecked(false);
                            mActivity.startActivityForResult(e.getIntent(), GDRIVE_REQUEST_AUTHORIZATION);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                });
            } catch (GoogleAuthException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    public void onResume() {
    }

    public void onActivityResult(int resultCode) {
        if (resultCode == Activity.RESULT_OK) {
            mGoogleDrivePref.setChecked(true);
            Settings.setGoogleDriveSyncEnabled(true);
            enableSync();
        } else {
            mGoogleDrivePref.setChecked(false);
        }
    }

    private void enableSync() {
        Account acc = User.getAccount();


        ContentResolver.setIsSyncable(acc, Contract.AUTHORITY, 1);
        ContentResolver.setSyncAutomatically(acc, Contract.AUTHORITY, true);
        ContentResolver.removePeriodicSync(acc, Contract.AUTHORITY, Bundle.EMPTY);

        long interval = Settings.getAutoSyncInterval();

        if (interval > 0) {
            ContentResolver.addPeriodicSync(acc, Contract.AUTHORITY, Bundle.EMPTY, interval);
        }

        Bundle b = new Bundle();
        b.putBoolean("resync_files", true);

        SyncHelper.requestManualSync(acc, b);

        Toast.makeText(GlobalApplication.getAppContext(), GlobalApplication.getAppContext().getString(R.string.data_resyncing), Toast.LENGTH_SHORT).show();
    }

    private void cancelSync() {

        Account acc = User.getAccount();

        ContentResolver.setIsSyncable(acc, Contract.AUTHORITY, 0);
        ContentResolver.setSyncAutomatically(acc, Contract.AUTHORITY, false);

        SettingsUtil.setSyncStatus("N/A");
    }

}
