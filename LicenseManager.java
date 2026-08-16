package com.example.cloakflow;

import android.content.Context;
import android.provider.Settings;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

public class LicenseManager {
    private final OkHttpClient client = new OkHttpClient();

    // ফোনের পারসিস্টেন্ট ইউনিক হার্ডওয়্যার আইডি (HWID) জেনারেটর
    public static String getHWID(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    // সার্ভারে কি এবং HWID পাঠিয়ে লক ভেরিফিকেশন করা
    public void verifyLicense(String serverUrl, String key, String hwid, LicenseCallback callback) {
        new Thread(() -> {
            try {
                String url = serverUrl + "/api/verify-access?key=" + key + "&hwid=" + hwid;
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                if (response.body() != null) {
                    JSONObject json = new JSONObject(response.body().string());
                    callback.onResult(json.getBoolean("success"), json.optString("message", ""));
                } else {
                    callback.onResult(false, "Server Connection Timeout");
                }
            } catch (Exception e) {
                callback.onResult(false, e.getMessage());
            }
        }).start();
    }

    public interface LicenseCallback {
        void onResult(boolean success, String errorMsg);
    }
}
