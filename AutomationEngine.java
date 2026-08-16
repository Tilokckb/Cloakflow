package com.example.cloakflow;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class AutomationEngine {
    private final Handler handler = new Handler(Looper.getMainLooper());

    // মানুষের মতো একটা একটা করে ক্যারেক্টার টাইপ করার মেকানিজম
    public void injectTextSlowly(WebView webView, String selector, String text) {
        final int[] index = {0};
        Runnable typeRunnable = new Runnable() {
            @Override
            public void run() {
                if (index[0] < text.length()) {
                    char c = text.charAt(index[0]);
                    String js = "var el = document.querySelector('" + selector + "'); if(el) { el.value += '" + c + "'; el.dispatchEvent(new Event('input', { bubbles: true })); }";
                    webView.evaluateJavascript(js, null);
                    index[0]++;
                    handler.postDelayed(this, 250); // ২৫০ মিলি-সেকেন্ড র্যান্ডম টাইপিং ডিলে
                }
            }
        };
        handler.post(typeRunnable);
    }

    // সুপার প্রক্সি অন-অফ করার সময় নেটওয়ার্ক ড্রপ প্রোটেকশন (ERR_INTERNET_DISCONNECTED ফিক্স)
    public static void setupNetworkResilience(WebView webView) {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // ২ সেকেন্ড পজ নিয়ে অটো-কানেক্ট করার ট্রিক
                new Handler(Looper.getMainLooper()).postDelayed(view::reload, 2000);
            }
        });
    }
}
