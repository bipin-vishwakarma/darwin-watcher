package com.darwin.watcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

public final class RingtoneFinder {
    private static final String TAG = "RingtoneFinder";
    private static Ringtone ringtone = null;
    private static int previousVolume = -1;

    private RingtoneFinder() { }

    public static synchronized boolean isPlaying() {
        return ringtone != null && ringtone.isPlaying();
    }

    public static synchronized void start(Context context) {
        stop(context);
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                previousVolume = am.getStreamVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0);
            }

            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }

            ringtone = RingtoneManager.getRingtone(context.getApplicationContext(), alarmUri);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                    ringtone.setAudioAttributes(attributes);
                } else {
                    ringtone.setStreamType(AudioManager.STREAM_ALARM);
                }
                if (Build.VERSION.SDK_INT >= 28) {
                    ringtone.setLooping(true);
                }
                ringtone.play();
                Log.i(TAG, "🔊 Siren alarm started at max volume.");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start ringtone finder", t);
        }
    }

    public static synchronized void stop(Context context) {
        try {
            if (ringtone != null) {
                if (ringtone.isPlaying()) {
                    ringtone.stop();
                }
                ringtone = null;
            }
            if (previousVolume >= 0) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0);
                }
                previousVolume = -1;
            }
            Log.i(TAG, "Ringtone finder stopped.");
        } catch (Throwable ignored) { }
    }
}
