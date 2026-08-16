package com.example.cloakflow;

import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private final String SERVER_URL = "http://yourserver.com:3000"; // আপনার হোস্টিং আইপি বা ইউআরএল দিয়ে রিপ্লেস করুন

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.main_webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        
        // নেটওয়ার্ক প্রটেকশন অন করা
        AutomationEngine.setupNetworkResilience(webView);

        // ৬ নম্বর ট্র্যাশ বাটন: স্পুফ এবং ডিপ ডিলিট সেশন অ্যাকশন
        ImageButton btnClean = findViewById(R.id.btn_deep_clean);
        btnClean.setOnClickListener(v -> {
            DeviceSpoofer.clearSessionData(MainActivity.this, webView);
            String mockUA = DeviceSpoofer.getMatchingUserAgent();
            webView.getSettings().setUserAgentString(mockUA);
            webView.loadUrl("https://facebook.com");
            Toast.makeText(this, "Cleaned! New Device Profile Generated.", Toast.LENGTH_SHORT).show();
        });

        // ৫ নম্বর কুকি বাটন: ১-ক্লিক এক্সপোর্ট টু ক্লিপবোর্ড সেশন
        ImageButton btnCookies = findViewById(R.id.btn_export_cookies);
        btnCookies.setOnClickListener(v -> {
            String cookies = CookieManager.getInstance().getCookie(webView.getUrl());
            if (cookies != null) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("CloakFlowCookies", cookies);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Netscape Cookies Copied!", Toast.LENGTH_SHORT).show();
            }
        });

        // ২ নম্বর হোম বাটন: রিফ্রেশ রুট
        ImageButton btnHome = findViewById(R.id.btn_force_home);
        btnHome.setOnClickListener(v -> webView.loadUrl("https://facebook.com"));

        // প্রাথমিক সেটআপে ফেসবুক লোড করা
        webView.loadUrl("https://facebook.com");
    }
}
