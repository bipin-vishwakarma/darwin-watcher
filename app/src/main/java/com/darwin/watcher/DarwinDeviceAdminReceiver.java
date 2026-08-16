package com.darwin.watcher;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class DarwinDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "DarwinDeviceAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, "🛡️ Device Admin permission active.");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.w(TAG, "⚠️ Device Admin disabled.");
    }
}
