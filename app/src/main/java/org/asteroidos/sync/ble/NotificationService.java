/*
 * Copyright (C) 2016 - Florent Revest <revestflo@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.asteroidos.sync.ble;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.idevicesinc.sweetblue.BleDevice;

import org.asteroidos.sync.NotificationPreferences;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public class NotificationService implements BleDevice.ReadWriteListener {
    private static final UUID notificationUpdateCharac   = UUID.fromString("00009001-0000-0000-0000-00a57e401d05");
    private static final UUID notificationFeedbackCharac = UUID.fromString("00009002-0000-0000-0000-00a57e401d05");

    private Context mCtx;
    private BleDevice mDevice;

    private NotificationReceiver mNReceiver;

    public NotificationService(Context ctx, BleDevice device)
    {
        mDevice = device;
        mCtx = ctx;
    }

    public void sync() {
        mDevice.enableNotify(notificationFeedbackCharac);

        mNReceiver = new NotificationReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("org.asteroidos.sync.NOTIFICATION_LISTENER");
        mCtx.registerReceiver(mNReceiver, filter);
    }

    public void unsync() {
        mDevice.disableNotify(notificationFeedbackCharac);
        try {
            mCtx.unregisterReceiver(mNReceiver);
        } catch (IllegalArgumentException ignored) {}
    }

    @Override
    public void onEvent(ReadWriteEvent e) {
        if(!e.wasSuccess())
            Log.e("NotificationService", e.status().toString());
    }

    class NotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String event = intent.getStringExtra("event");
            if (Objects.equals(event, "posted")) {
                String packageName = intent.getStringExtra("packageName");
                NotificationPreferences.putPackageToSeen(context, packageName);
                NotificationPreferences.NotificationOption notificationOption =
                        NotificationPreferences.getNotificationPreferenceForApp(context, packageName);
                if (notificationOption == NotificationPreferences.NotificationOption.NO_NOTIFICATIONS)
                    return;

                int id = intent.getIntExtra("id", 0);
                String appName = intent.getStringExtra("appName");
                String appIcon = intent.getStringExtra("appIcon");
                String summary = intent.getStringExtra("summary");
                String body = intent.getStringExtra("body");
                String vibration;
                if (notificationOption == NotificationPreferences.NotificationOption.SILENT_NOTIFICATION)
                    vibration = "none";
                else if (notificationOption == null
                        || notificationOption == NotificationPreferences.NotificationOption.NORMAL_VIBRATION
                        || notificationOption == NotificationPreferences.NotificationOption.DEFAULT)
                    vibration = "normal";
                else if (notificationOption == NotificationPreferences.NotificationOption.STRONG_VIBRATION)
                    vibration = "strong";
                else
                    throw new IllegalArgumentException("Not all options handled");

                String xmlRequest = "<insert>" +
                        "<pn>" + packageName + "</pn>" +
                        "<vb>" + vibration + "</vb>" +
                        "<id>" + id + "</id>" +
                        "<an>" + appName + "</an>" +
                        "<ai>" + appIcon + "</ai>" +
                        "<su>" + summary + "</su>" +
                        "<bo>" + body + "</bo>" +
                        "</insert>";

                byte[] data = xmlRequest.getBytes(StandardCharsets.UTF_8);
                mDevice.write(notificationUpdateCharac, data, NotificationService.this);
            } else if (Objects.equals(event, "removed")) {
                int id = intent.getIntExtra("id", 0);

                String xmlRequest = "<removed>" +
                        "<id>" + id + "</id>" +
                        "</removed>";

                byte[] data = xmlRequest.getBytes(StandardCharsets.UTF_8);
                mDevice.write(notificationUpdateCharac, data, NotificationService.this);
            }
        }
    }
}
