package com.cloakflow.browser;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.cloakflow.browser.automation.AutomationEngine;
import com.cloakflow.browser.licensing.LicenseManager;
import com.cloakflow.browser.util.DeviceSpoofer;
import com.cloakflow.browser.vision.OcrScanner;

/**
 * MainActivity is the primary lifecycle orchestrator for the CloakFlow web diagnostic suite.
 * It integrates device profiling, automation dispatchers, OCR processing, and licensing checks.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String DEFAULT_HOME_URL = "https://example.com/test-portal";
    private static final String LICENSING_GATEWAY_URL = "https://yourlicensinggateway.com";
    private static final String DESKTOP_USER_AGENT = 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private WebView webView;
    private LicenseManager licenseManager;
    private DeviceSpoofer.DeviceProfile currentProfile;
    private boolean isDesktopView = false;

    // Header buttons
    private ImageButton btnToggleView;
    private ImageButton btnForceHome;
    private ImageButton btnExtractUid;
    private ImageButton btnPrivacyGuard;
    private ImageButton btnExportCookies;
    private ImageButton btnDeepClean;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initWebView();
        initLicensingAndSession();
        setupHeaderActionListeners();
    }

    /**
     * Binds layout XML components to local field references.
     */
    private void initViews() {
        webView = findViewById(R.id.main_webview);
        btnToggleView = findViewById(R.id.btn_toggle_view);
        btnForceHome = findViewById(R.id.btn_force_home);
        btnExtractUid = findViewById(R.id.btn_extract_uid);
        btnPrivacyGuard = findViewById(R.id.btn_privacy_guard);
        btnExportCookies = findViewById(R.id.btn_export_cookies);
        btnDeepClean = findViewById(R.id.btn_deep_clean);
    }

    /**
     * Configures the WebView runtime settings, JavaScript interface, and resilient network client.
     */
    private void initWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Apply a randomized high-end device profile
        applyNewDeviceProfile();

        // Attach resilient WebViewClient for connection dropout recovery
        webView.setWebViewClient(new AutomationEngine.ResilientWebViewClient());
    }

    /**
     * Executes the hardware licensing handshake before granting workspace access.
     */
    private void initLicensingAndSession() {
        licenseManager = new LicenseManager(this, LICENSING_GATEWAY_URL);

        // Authenticate with server gateway
        licenseManager.validateLicense("CF-DIAG-DEFAULT-KEY", new LicenseManager.LicenseValidationCallback() {
            @Override
            public void onSuccess(String sessionToken) {
                Log.i(TAG, "License validation successful. Loading workspace route.");
                webView.loadUrl(DEFAULT_HOME_URL);
            }

            @Override
            public void onFailure(String errorMessage, boolean isDeviceMismatch) {
                Log.e(TAG, "License check failed: " + errorMessage);
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    /**
     * Configures action click listeners across the top navigation bar.
     */
    private void setupHeaderActionListeners() {
        // Button 1: Toggle Mobile / Desktop Viewports
        btnToggleView.setOnClickListener(v -> {
            isDesktopView = !isDesktopView;
            WebSettings settings = webView.getSettings();
            if (isDesktopView) {
                settings.setUserAgentString(DESKTOP_USER_AGENT);
                Toast.makeText(this, "Switched to Desktop Viewport", Toast.LENGTH_SHORT).show();
            } else {
                settings.setUserAgentString(currentProfile.getUserAgent());
                Toast.makeText(this, "Switched to Mobile Viewport (" + currentProfile.getModel() + ")", Toast.LENGTH_SHORT).show();
            }
            webView.reload();
        });

        // Button 2: Force Navigation to Home
        btnForceHome.setOnClickListener(v -> {
            webView.loadUrl(DEFAULT_HOME_URL);
            Toast.makeText(this, "Routing to Primary Home Endpoint", Toast.LENGTH_SHORT).show();
        });

        // Button 3: Extract Identity / UID Elements to Clipboard
        btnExtractUid.setOnClickListener(v -> extractUidFromDom());

        // Button 4: Privacy Guard / Shield Injection
        btnPrivacyGuard.setOnClickListener(v -> injectPrivacyShield());

        // Button 5: Export Active Cookies to Clipboard
        btnExportCookies.setOnClickListener(v -> exportSessionCookies());

        // Button 6: Deep Storage Wipe & Profile Refresh
        btnDeepClean.setOnClickListener(v -> executeDeepStorageClean());
    }

    /**
     * Evaluates DOM query to extract user identification tokens and copies them to the system clipboard.
     */
    private void extractUidFromDom() {
        String extractionScript = 
                "(() => {" +
                "  const el = document.querySelector('[data-user-id], [id*=\"user\"], [class*=\"profile-id\"], input[name=\"uid\"]');" +
                "  return el ? (el.value || el.innerText || el.getAttribute('data-user-id') || '') : '';" +
                "})();";

        webView.evaluateJavascript(extractionScript, value -> {
            String sanitizedValue = (value != null) ? value.replace("\"", "").trim() : "";
            if (!sanitizedValue.isEmpty() && !"null".equalsIgnoreCase(sanitizedValue)) {
                copyToClipboard("Extracted UID", sanitizedValue);
                Toast.makeText(this, "UID Copied: " + sanitizedValue, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No UID element detected in viewport", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Injects platform security script overrides into the current execution frame.
     */
    private void injectPrivacyShield() {
        String privacyScript = 
                "(() => {" +
                "  Object.defineProperty(navigator, 'webdriver', { get: () => undefined });" +
                "  if (window.navigator.connection) {" +
                "    Object.defineProperty(navigator.connection, 'rtt', { get: () => 50 });" +
                "  }" +
                "  console.log('[CloakFlow] Privacy guard profile deployed.');" +
                "})();";

        webView.evaluateJavascript(privacyScript, result -> 
                Toast.makeText(this, "Privacy Guard Scripts Injected", Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Queries CookieManager for active session cookies and copies them formatted to the clipboard.
     */
    private void exportSessionCookies() {
        String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(this, "No active web session loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        String cookies = CookieManager.getInstance().getCookie(currentUrl);
        if (cookies != null && !cookies.isEmpty()) {
            copyToClipboard("Session Cookies", cookies);
            Toast.makeText(this, "Session Cookies Copied to Clipboard", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No active cookies for current route", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Purges runtime caches, drops databases, wipes cookies, and assigns a new mock device profile.
     */
    private void executeDeepStorageClean() {
        DeviceSpoofer.clearSessionData(webView, success -> {
            applyNewDeviceProfile();
            webView.loadUrl(DEFAULT_HOME_URL);
            Toast.makeText(this, "Deep Clean Complete: New Profile (" + currentProfile.getModel() + ")", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Generates and applies a new mock device configuration profile to the WebView settings.
     */
    private void applyNewDeviceProfile() {
        currentProfile = DeviceSpoofer.generateMockProfile();
        webView.getSettings().setUserAgentString(currentProfile.getUserAgent());
        Log.i(TAG, "Assigned Mock Profile -> Model: " + currentProfile.getModel() + " | ID: " + currentProfile.getAndroidId());
    }

    /**
     * Programmatic dispatcher to trigger visual OCR verification for on-screen challenge elements.
     *
     * @param targetArea     The bounding box of the visual challenge inside the viewport.
     * @param inputSelector  The CSS selector of the text input field.
     * @param submitSelector Optional CSS selector for form submission.
     */
    public void dispatchOcrVerificationFlow(@NonNull Rect targetArea,
                                            @NonNull String inputSelector,
                                            @Nullable String submitSelector) {
        if (webView.getWidth() <= 0 || webView.getHeight() <= 0) {
            return;
        }

        Bitmap screenshot = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(screenshot);
        webView.draw(canvas);

        Bitmap croppedChallenge = OcrScanner.cropTargetRegion(screenshot, targetArea);
        if (croppedChallenge != null) {
            OcrScanner.processAndInject(croppedChallenge, webView, inputSelector, submitSelector, new OcrScanner.OcrResultCallback() {
                @Override
                public void onSuccess(String parsedText) {
                    Log.i(TAG, "OCR pipeline completed successfully. Parsed: " + parsedText);
                }

                @Override
                public void onFailure(Exception exception) {
                    Log.e(TAG, "OCR pipeline encountered a failure: " + exception.getMessage(), exception);
                }
            });
        }
    }

    /**
     * Helper to write string content to the system clipboard.
     */
    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText(label, text);
            clipboard.setPrimaryClip(clip);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
          }
