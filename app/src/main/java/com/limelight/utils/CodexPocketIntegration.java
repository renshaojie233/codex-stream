package com.limelight.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.limelight.nvstream.http.ComputerDetails;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CodexPocketIntegration {
    public static final String EXTRA_HOST = "com.codexpocket.stream.extra.HOST";
    public static final String EXTRA_TOKEN = "com.codexpocket.stream.extra.TOKEN";
    public static final String EXTRA_GATEWAY_PORT = "com.codexpocket.stream.extra.GATEWAY_PORT";

    private static final String PREFERENCES = "codex-pocket-integration";
    private static final String TOKEN_PREFIX = "token:";
    private static final String PORT_PREFIX = "port:";
    private static final String GLOBAL_TOKEN = "global-token";
    private static final String GLOBAL_PORT = "global-port";
    private static final String TRACKPAD_INITIALIZED = "mac-trackpad-initialized";
    private static final String SAFE_GESTURE_DEFAULT_INITIALIZED = "safe-gesture-default-initialized";
    private static final Set<String> MANAGED_NAMES = new HashSet<>(Arrays.asList(
            "Workstation", "Agilex", "RSJ PC"
    ));
    private static final Map<String, String> MANAGED_HOST_BY_NAME = new HashMap<>();

    static {
        MANAGED_HOST_BY_NAME.put("Workstation", "100.115.211.82");
        MANAGED_HOST_BY_NAME.put("Agilex", "100.64.202.98");
        MANAGED_HOST_BY_NAME.put("RSJ PC", "100.77.122.104");
    }

    private CodexPocketIntegration() {}

    public static void rememberHost(Context context, String host, String token, int gatewayPort) {
        configureMacTrackpadDefaults(context);
        if (host == null || host.isEmpty() || token == null || token.isEmpty()) {
            return;
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(TOKEN_PREFIX + host, token)
                .putInt(PORT_PREFIX + host, gatewayPort)
                .putString(GLOBAL_TOKEN, token)
                .putInt(GLOBAL_PORT, gatewayPort)
                .apply();
    }

    private static void configureMacTrackpadDefaults(Context context) {
        SharedPreferences integrationPreferences =
                context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor integrationEditor = integrationPreferences.edit();
        if (!integrationPreferences.getBoolean(TRACKPAD_INITIALIZED, false)) {
            defaultPreferences
                    .edit()
                    .putBoolean("checkbox_touchscreen_trackpad", true)
                    .putBoolean("checkbox_absolute_mouse_mode", false)
                    .putBoolean("checkbox_show_onscreen_controls", false)
                    .apply();
            integrationEditor.putBoolean(TRACKPAD_INITIALIZED, true);
        }
        if (!integrationPreferences.getBoolean(SAFE_GESTURE_DEFAULT_INITIALIZED, false)) {
            // 1.3.0 prompted for system-setting access during app startup on some
            // HyperOS builds. Reset the guard once so 1.3.1 always starts safely.
            defaultPreferences.edit()
                    .putBoolean(XiaomiGestureGuard.PREFERENCE_KEY, false)
                    .apply();
            integrationEditor.putBoolean(SAFE_GESTURE_DEFAULT_INITIALIZED, true);
        }
        integrationEditor.apply();
    }

    public static boolean isManagedComputer(ComputerDetails computer) {
        return managedComputerName(computer) != null;
    }

    public static String managedComputerName(ComputerDetails computer) {
        if (computer == null) {
            return null;
        }
        for (Map.Entry<String, String> managed : MANAGED_HOST_BY_NAME.entrySet()) {
            if (computerHasAddress(computer, managed.getValue())) {
                return managed.getKey();
            }
        }
        if (computer.name != null && MANAGED_NAMES.contains(computer.name)) {
            return computer.name;
        }
        return null;
    }

    public static boolean shouldReplaceManagedComputer(
            ComputerDetails existing,
            ComputerDetails candidate
    ) {
        return managedComputerScore(candidate) > managedComputerScore(existing);
    }

    private static int managedComputerScore(ComputerDetails computer) {
        if (computer == null) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        String name = managedComputerName(computer);
        String preferredHost = name == null ? null : MANAGED_HOST_BY_NAME.get(name);
        if (preferredHost != null && computerHasAddress(computer, preferredHost)) {
            score += 20;
        }
        if (computer.state == ComputerDetails.State.ONLINE) {
            score += 100;
        } else if (computer.state == ComputerDetails.State.UNKNOWN) {
            score += 50;
        }
        if (computer.pairState == com.limelight.nvstream.http.PairingManager.PairState.PAIRED) {
            score += 3;
        }
        if (computer.activeAddress != null) {
            score += 1;
        }
        return score;
    }

    private static boolean computerHasAddress(ComputerDetails computer, String host) {
        return addressMatches(computer.activeAddress, host) ||
                addressMatches(computer.manualAddress, host) ||
                addressMatches(computer.localAddress, host) ||
                addressMatches(computer.remoteAddress, host);
    }

    private static boolean addressMatches(ComputerDetails.AddressTuple address, String host) {
        return address != null && host.equals(address.address);
    }

    public static void approvePairingAsync(Context context, ComputerDetails computer, String pin) {
        if (computer == null || pin == null) {
            return;
        }
        final String managedName = managedComputerName(computer);
        final String managedHost = managedName == null ? null : MANAGED_HOST_BY_NAME.get(managedName);
        final String host = managedHost != null
                ? managedHost
                : computer.activeAddress == null ? null : computer.activeAddress.address;
        if (host == null || host.isEmpty()) {
            return;
        }
        final SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        final String token = preferences.getString(
                TOKEN_PREFIX + host,
                preferences.getString(GLOBAL_TOKEN, null)
        );
        final int port = preferences.getInt(
                PORT_PREFIX + host,
                preferences.getInt(GLOBAL_PORT, 8790)
        );
        if (token == null || token.isEmpty()) {
            return;
        }

        Thread thread = new Thread(() -> {
            // PairingManager starts the host-side exchange on the caller thread.
            // Retry briefly so the approval lands after Sunshine has registered
            // the pending Moonlight client, without requiring a Web UI round-trip.
            for (int attempt = 0; attempt < 8; attempt++) {
                try {
                    Thread.sleep(attempt == 0 ? 350 : 450);
                    if (postPairingApproval(host, port, token, pin)) {
                        return;
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ignored) {
                    // The direct gateway may still be starting; the next bounded
                    // attempt will retry without delaying the pairing UI.
                }
            }
        }, "Codex Stream pairing approval");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean postPairingApproval(
            String host,
            int port,
            String token,
            String pin
    ) throws Exception {
        URL endpoint = new URL("http", host, port, "/remote/extreme/pair");
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setConnectTimeout(1_500);
        connection.setReadTimeout(1_500);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setDoOutput(true);
        byte[] payload = ("{\"pin\":\"" + pin + "\",\"name\":\"Codex Stream\"}")
                .getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(payload.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
        }
        int status = connection.getResponseCode();
        connection.disconnect();
        return status >= 200 && status < 300;
    }
}
