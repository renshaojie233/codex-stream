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
import java.util.HashSet;
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
    private static final Set<String> MANAGED_NAMES = new HashSet<>(Arrays.asList(
            "Workstation", "Agilex", "RSJ PC"
    ));
    private static final Set<String> MANAGED_HOSTS = new HashSet<>(Arrays.asList(
            "100.115.211.82", "100.64.202.98", "100.77.122.104"
    ));

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
        if (integrationPreferences.getBoolean(TRACKPAD_INITIALIZED, false)) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean("checkbox_touchscreen_trackpad", true)
                .putBoolean("checkbox_absolute_mouse_mode", false)
                .putBoolean("checkbox_show_onscreen_controls", false)
                .apply();
        integrationPreferences.edit().putBoolean(TRACKPAD_INITIALIZED, true).apply();
    }

    public static boolean isManagedComputer(ComputerDetails computer) {
        if (computer == null) {
            return false;
        }
        if (computer.name != null && MANAGED_NAMES.contains(computer.name)) {
            return true;
        }
        return addressIsManaged(computer.activeAddress) ||
                addressIsManaged(computer.manualAddress) ||
                addressIsManaged(computer.localAddress) ||
                addressIsManaged(computer.remoteAddress);
    }

    private static boolean addressIsManaged(ComputerDetails.AddressTuple address) {
        return address != null && MANAGED_HOSTS.contains(address.address);
    }

    public static void approvePairingAsync(Context context, ComputerDetails computer, String pin) {
        if (computer == null || computer.activeAddress == null || pin == null) {
            return;
        }
        final String host = computer.activeAddress.address;
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
