package com.filestack.android;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.filestack.FileLink;
import com.filestack.Sources;
import com.filestack.StorageOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class UploadService extends IntentService {
    public static final String SERVICE_NAME = "Filestack Upload Service";
    public static final String PREF_NOTIFICATION_ID_COUNTER = "idCounter";
    public static final String TAG = "uploadService";

    public UploadService() {
        super(SERVICE_NAME);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onHandleIntent(Intent intent) {
        ArrayList<Selection> selections = (ArrayList<Selection>)
                intent.getSerializableExtra(FsConstants.EXTRA_SELECTION_LIST);
        StorageOptions storeOpts = (StorageOptions)
                intent.getSerializableExtra(FsConstants.EXTRA_STORE_OPTS);

        SharedPreferences prefs = getSharedPreferences(getClass().getName(), MODE_PRIVATE);
        int notifyId = prefs.getInt(PREF_NOTIFICATION_ID_COUNTER, 0);
        int total = selections.size();

        int i = 0;
        for (Selection item : selections) {
            String name = item.getName();
            String provider = item.getProvider();

            Log.d(TAG, "received: " + provider + " " + name);

            FileLink fileLink;
            if (isLocal(item)) {
                fileLink = uploadLocal(item, storeOpts);
            } else {
                fileLink = uploadCloud(item, storeOpts);
            }

            updateNotification(notifyId, ++i, total, name);
            sendBroadcast(item, fileLink);
        }

        prefs.edit().putInt(PREF_NOTIFICATION_ID_COUNTER, notifyId+1).apply();
    }

    private boolean isLocal(Selection item) {
        switch (item.getProvider()) {
            case Sources.CAMERA:
            case Sources.DEVICE:
                return true;
            default:
                return false;
        }
    }

    private FileLink uploadLocal(Selection item, StorageOptions storeOpts) {
        try {
            return Util.getClient().upload(item.getPath(), false, storeOpts);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private FileLink uploadCloud(Selection item, StorageOptions storeOpts) {
        try {
            return Util.getClient().storeCloudItem(item.getProvider(), item.getPath(), storeOpts);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void updateNotification(int id, int done, int total, String name) {
        Locale locale = Locale.getDefault();
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);

        if (total == done) {
            mBuilder.setContentTitle(String.format(locale, "Uploaded %d files", done));
            mBuilder.setSmallIcon(R.drawable.ic_menu_upload_done_white);
        } else {
            mBuilder.setContentTitle(String.format(locale, "Uploading %d/%d files", done, total));
            mBuilder.setSmallIcon(R.drawable.ic_menu_upload_white);
            mBuilder.setContentText(name);
            mBuilder.setProgress(total, done, false);
        }

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(id, mBuilder.build());
    }

    private void sendBroadcast(Selection selection, FileLink fileLink) {
        Intent intent = new Intent(FsConstants.BROADCAST_UPLOAD);
        intent.putExtra(FsConstants.EXTRA_SELECTION, selection);
        if (fileLink == null) {
            intent.putExtra(FsConstants.EXTRA_STATUS, FsConstants.STATUS_FAILED);
        } else {
            intent.putExtra(FsConstants.EXTRA_STATUS, FsConstants.STATUS_COMPLETE);
        }
        intent.putExtra(FsConstants.EXTRA_FILE_LINK, fileLink);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
