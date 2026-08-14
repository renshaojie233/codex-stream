package com.limelight.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import com.limelight.R;

import java.util.Locale;

/** Temporarily gives Codex Stream ownership of three-finger gestures on Xiaomi devices. */
public final class XiaomiGestureGuard {
    public static final String PREFERENCE_KEY = "checkbox_xiaomi_three_finger_guard";

    private static final String STATE_PREFERENCES = "codex-xiaomi-gesture-guard";
    private static final String ACTIVE_KEY = "active";
    private static final String PROMPTED_KEY = "permission-prompted";
    private static final String ORIGINAL_PRESENT_PREFIX = "original-present:";
    private static final String ORIGINAL_VALUE_PREFIX = "original-value:";
    private static final String DISABLED_VALUE = "none";
    private static final String[] GESTURE_KEYS = {
            "three_gesture_down",
            "three_gesture_long_press",
            "three_gesture_horizontal_ltr",
            "three_gesture_horizontal_rtl",
            "enable_three_gesture"
    };

    private XiaomiGestureGuard() {}

    public static boolean isXiaomiDevice() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String brand = Build.BRAND == null ? "" : Build.BRAND;
        String identity = (manufacturer + " " + brand).toLowerCase(Locale.ROOT);
        return identity.contains("xiaomi") || identity.contains("redmi") || identity.contains("poco");
    }

    public static boolean isEnabled(Context context) {
        return android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREFERENCE_KEY, false);
    }

    public static boolean canWriteSystemSettings(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context);
    }

    public static void requestPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName())
            );
            activity.startActivity(intent);
        } catch (Exception unavailable) {
            activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    public static void promptForPermissionOnce(Activity activity) {
        if (!isXiaomiDevice() || !isEnabled(activity) || canWriteSystemSettings(activity)) {
            return;
        }
        SharedPreferences state = state(activity);
        if (state.getBoolean(PROMPTED_KEY, false)) {
            return;
        }
        state.edit().putBoolean(PROMPTED_KEY, true).apply();
        Toast.makeText(activity, R.string.toast_xiaomi_three_finger_permission, Toast.LENGTH_LONG).show();
        requestPermission(activity);
    }

    public static synchronized boolean activate(Context context) {
        if (!isXiaomiDevice() || !isEnabled(context) || !canWriteSystemSettings(context)) {
            return false;
        }
        SharedPreferences state = state(context);
        if (state.getBoolean(ACTIVE_KEY, false)) {
            return true;
        }

        SharedPreferences.Editor saved = state.edit();
        try {
            for (String key : GESTURE_KEYS) {
                String original = Settings.System.getString(context.getContentResolver(), key);
                saved.putBoolean(ORIGINAL_PRESENT_PREFIX + key, original != null);
                if (original != null) {
                    saved.putString(ORIGINAL_VALUE_PREFIX + key, original);
                }
            }
            saved.putBoolean(ACTIVE_KEY, true).commit();
            for (String key : GESTURE_KEYS) {
                boolean updated = Settings.System.putString(
                        context.getContentResolver(),
                        key,
                        "enable_three_gesture".equals(key) ? "0" : DISABLED_VALUE
                );
                if (!updated) {
                    restore(context);
                    return false;
                }
            }
            return true;
        } catch (RuntimeException denied) {
            restore(context);
            return false;
        }
    }

    public static synchronized void restore(Context context) {
        SharedPreferences state = state(context);
        if (!state.getBoolean(ACTIVE_KEY, false) || !canWriteSystemSettings(context)) {
            return;
        }
        for (String key : GESTURE_KEYS) {
            boolean wasPresent = state.getBoolean(ORIGINAL_PRESENT_PREFIX + key, false);
            String original = wasPresent ? state.getString(ORIGINAL_VALUE_PREFIX + key, null) : null;
            try {
                Settings.System.putString(context.getContentResolver(), key, original);
            } catch (RuntimeException ignored) {
                return;
            }
        }
        SharedPreferences.Editor cleared = state.edit().putBoolean(ACTIVE_KEY, false);
        for (String key : GESTURE_KEYS) {
            cleared.remove(ORIGINAL_PRESENT_PREFIX + key);
            cleared.remove(ORIGINAL_VALUE_PREFIX + key);
        }
        cleared.apply();
    }

    public static void restoreAfterInterruptedStream(Context context) {
        restore(context);
    }

    private static SharedPreferences state(Context context) {
        return context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE);
    }
}
