package com.cloakflow.browser.licensing;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * LicenseManager handles hardware identifier generation (HWID lock), license validation,
 * encrypted session persistence, and single-device integrity enforcement.
 */
public class LicenseManager {

    private static final String TAG = "LicenseManager";
    private static final String PREFS_FILE = "cloakflow_secure_vault";
    private static final String KEY_SESSION_TOKEN = "auth_session_token";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final Context context;
    private final String gatewayBaseUrl;
    private final OkHttpClient httpClient;
    private final Handler mainHandler;

    public interface LicenseValidationCallback {
        void onSuccess(String sessionToken);
        void onFailure(String errorMessage, boolean isDeviceMismatch);
    }

    public LicenseManager(@NonNull Context context, @NonNull String gatewayBaseUrl) {
        this.context = context.getApplicationContext();
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Generates a deterministic SHA-256 hardware identifier (HWID) based on
     * the system Android ID and unique build fingerprint parameters.
     *
     * @return Hex-encoded SHA-256 hardware fingerprint string.
     */
    private String generateDeviceHwid() {
        String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        if (androidId == null) {
            androidId = "UNKNOWN_ID";
        }

        String rawSignature = androidId + ":" + Build.FINGERPRINT + ":" + Build.MANUFACTURER + ":" + Build.MODEL;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawSignature.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Cryptographic algorithm unavailable for HWID generation", e);
            return String.valueOf(rawSignature.hashCode());
        }
    }

    /**
     * Executes an asynchronous hardware binding handshake to validate the license.
     *
     * @param licenseKey The alphanumeric license or activation key.
     * @param callback   Callback to handle UI unlocking or policy enforcement errors.
     */
    public void validateLicense(@NonNull String licenseKey, @NonNull LicenseValidationCallback callback) {
        String hwid = generateDeviceHwid();

        JSONObject payload = new JSONObject();
        try {
            payload.put("license_key", licenseKey);
            payload.put("hwid", hwid);
            payload.put("platform", "Android");
            payload.put("os_version", Build.VERSION.RELEASE);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to assemble licensing JSON payload: " + e.getMessage(), e);
            callback.onFailure("Payload Assembly Error", false);
            return;
        }

        RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(gatewayBaseUrl + "/api/v1/license/verify")
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Licensing transaction timed out or network dropped: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onFailure("Connection timeout / Network unreachable", false));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        throw new IOException("Empty response received from validation server");
                    }

                    String rawJson = responseBody.string();
                    JSONObject jsonResponse = new JSONObject(rawJson);

                    boolean isValid = jsonResponse.optBoolean("valid", false);
                    String statusMessage = jsonResponse.optString("status_message", "UNKNOWN");
                    String sessionToken = jsonResponse.optString("session_token", "");

                    if (isValid && !sessionToken.isEmpty()) {
                        saveSessionToken(sessionToken);
                        Log.i(TAG, "Hardware signature verified. License authorized successfully.");
                        mainHandler.post(() -> callback.onSuccess(sessionToken));
                    } else {
                        boolean isMismatch = "DEVICE_MISMATCH".equalsIgnoreCase(statusMessage) || response.code() == 403;
                        String errorDescription = isMismatch 
                                ? "Single Device Policy Enforcement Fault: Hardware signature mismatch."
                                : "License Verification Failed: " + statusMessage;

                        Log.w(TAG, "Authorization rejected: " + errorDescription);
                        mainHandler.post(() -> callback.onFailure(errorDescription, isMismatch));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse licensing server payload: " + e.getMessage(), e);
                    mainHandler.post(() -> callback.onFailure("Invalid validation response format", false));
                }
            }
        });
    }

    /**
     * Persists the active authorization session token into EncryptedSharedPreferences.
     */
    private void saveSessionToken(String sessionToken) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences securePreferences = EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            securePreferences.edit().putString(KEY_SESSION_TOKEN, sessionToken).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to write session token to secure storage", e);
        }
    }

    /**
     * Retrieves the stored authorization session token.
     *
     * @return Stored token string, or null if unauthenticated.
     */
    public String getStoredSessionToken() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences securePreferences = EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            return securePreferences.getString(KEY_SESSION_TOKEN, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read session token from secure storage", e);
            return null;
        }
    }
                                 }
