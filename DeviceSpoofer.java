package com.cloakflow.browser.util;

import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebStorage;
import android.webkit.WebView;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * DeviceSpoofer is a testing helper utility for CloakFlow.
 * It provides methods for session storage cleanup and generates
 * matched device/User-Agent configuration profiles for browser rendering tests.
 */
public final class DeviceSpoofer {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Represents a mocked device environment configuration.
     */
    public static final class DeviceProfile {
        private final String model;
        private final String androidId;
        private final String userAgent;

        public DeviceProfile(String model, String androidId, String userAgent) {
            this.model = model;
            this.androidId = androidId;
            this.userAgent = userAgent;
        }

        public String getModel() {
            return model;
        }

        public String getAndroidId() {
            return androidId;
        }

        public String getUserAgent() {
            return userAgent;
        }
    }

    /**
     * Definition pair linking a device model to its standard User-Agent.
     */
    private static final class ModelConfigPair {
        final String model;
        final String userAgent;

        ModelConfigPair(String model, String userAgent) {
            this.model = model;
            this.userAgent = userAgent;
        }
    }

    // Preset configurations for high-end device emulation
    private static final List<ModelConfigPair> PRESET_CONFIGS = new ArrayList<>();

    static {
        PRESET_CONFIGS.add(new ModelConfigPair(
                "Pixel 8 Pro",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        ));
        PRESET_CONFIGS.add(new ModelConfigPair(
                "SM-S928B",
                "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        ));
        PRESET_CONFIGS.add(new ModelConfigPair(
                "23116PN5BC",
                "Mozilla/5.0 (Linux; Android 14; 23116PN5BC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        ));
    }

    private DeviceSpoofer() {
        // Prevent direct instantiation
    }

    /**
     * Clears all session databases, runtime local storage, cache, and cookies.
     *
     * @param webView  The active WebView instance to clear.
     * @param callback Optional callback triggered when cookie removal is complete.
     */
    public static void clearSessionData(WebView webView, ValueCallback<Boolean> callback) {
        if (webView != null) {
            webView.clearCache(true);
            webView.clearFormData();
            webView.clearHistory();
        }

        // Wipe HTML5 local storage, indexedDB, and database storage
        WebStorage.getInstance().deleteAllData();

        // Clear active and stored cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(success -> {
            cookieManager.flush();
            if (callback != null) {
                callback.onReceiveValue(success);
            }
        });
    }

    /**
     * Generates a randomized mock device profile with a paired User-Agent
     * and a randomized pseudo 64-bit Android ID string.
     *
     * @return DeviceProfile containing the matched model, user-agent, and mock ID.
     */
    public static DeviceProfile generateMockProfile() {
        int index = RANDOM.nextInt(PRESET_CONFIGS.size());
        ModelConfigPair selectedConfig = PRESET_CONFIGS.get(index);
        String mockAndroidId = generateMockAndroidId();

        return new DeviceProfile(
                selectedConfig.model,
                mockAndroidId,
                selectedConfig.userAgent
        );
    }

    /**
     * Generates a pseudo-random 16-character hexadecimal string representing a mock Android ID.
     *
     * @return 16-character hex string.
     */
    private static String generateMockAndroidId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(16);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
