package com.example.cloakflow;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebView;
import java.util.UUID;
import java.util.Random;

public class DeviceSpoofer {
    
    // ওয়ান-ক্লিক ডিপ ক্লিন ও সেশন ক্যাশ পুরোপুরি মুছে ফেলা
    public static void clearSessionData(Context context, WebView webView) {
        webView.post(() -> {
            webView.clearCache(true);
            webView.clearHistory();
            webView.clearFormData();
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        });
    }

    // র্যান্ডম ডিভাইস আইডি জেনারেটর
    public static String generateMockAndroidId() {
        return UUID.randomUUID().toString().substring(0, 16).replace("-", "");
    }

    // ডিভাইস আইডির সাথে একদম ম্যাচিং করা প্রিমিয়াম ইউজার-এজেন্ট (UA) সুইপার
    public static String getMatchingUserAgent() {
        String[] models = {"SM-S928B", "SM-A546B", "XIAOMI-14Pro", "VIVO-X100"};
        String randomModel = models[new Random().nextInt(models.length)];
        
        if (randomModel.equals("SM-S928B")) {
            return "Mozilla/5.0 (Linux; Android 14; SAMSUNG SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/24.0 Chrome/120.0.6099.230 Mobile Safari/537.36";
        } else if (randomModel.equals("XIAOMI-14Pro")) {
            return "Mozilla/5.0 (Linux; Android 14; Xiaomi 14 Pro Build/UKQ1.230804.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36";
        }
        return "Mozilla/5.0 (Linux; Android 13; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";
    }
}
