package com.example.cloakflow;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

public class SmsApiHandler {
    private final OkHttpClient client = new OkHttpClient();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isPolling = false;

    // সার্ভার থেকে ডাইনামিকলি নাম্বার তুলে আনা এবং ফেসবুক বক্সে বসানো
    public void fetchNumber(String serverUrl, String provider, String country, WebView webView) {
        new Thread(() -> {
            try {
                String url = serverUrl + "/api/get-number?provider=" + provider + "&country=" + country;
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                if (response.body() != null) {
                    JSONObject json = new JSONObject(response.body().string());
                    if (json.getBoolean("success")) {
                        String number = json.getString("number");
                        String orderId = json.getString("id");
                        handler.post(() -> {
                            webView.evaluateJavascript("document.querySelector('input[type=\"tel\"], input[name=\"reg_phone_1__\"]').value = '" + number + "';", null);
                            startOtpPolling(serverUrl, provider, orderId, webView);
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ৩ সেকেন্ড পর পর ওটিপি কোড ট্র্যাকিং লুপ
    private void startOtpPolling(String serverUrl, String provider, String orderId, WebView webView) {
        isPolling = true;
        Runnable pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPolling) return;
                new Thread(() -> {
                    try {
                        String url = serverUrl + "/api/get-otp?provider=" + provider + "&orderId=" + orderId;
                        Request request = new Request.Builder().url(url).build();
                        Response response = client.newCall(request).execute();
                        if (response.body() != null) {
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.getBoolean("success") && json.getString("status").equals("OK")) {
                                String otpCode = json.getString("otp");
                                isPolling = false;
                                handler.post(() -> webView.evaluateJavascript("document.querySelector('input[name=\"code\"]').value = '" + otpCode + "';", null));
                            } else {
                                handler.postDelayed(this, 3000); // ওটিপি না আসা পর্যন্ত প্রতি ৩ সেকেন্ডে লুপ চলবে
                            }
                        }
                    } catch (Exception e) {
                        handler.postDelayed(this, 3000);
                    }
                }).start();
            }
        };
        handler.post(pollingRunnable);
    }
}
