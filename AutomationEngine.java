package com.cloakflow.browser.automation;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AutomationEngine is a utility class for CloakFlow designed for UI test automation,
 * simulated user-input rendering verification, and resilient network handling.
 */
public final class AutomationEngine {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private AutomationEngine() {
        // Prevent direct instantiation
    }

    /**
     * Injects text into a target DOM input field character-by-character with randomized
     * delays (150ms to 300ms) to test input event responsiveness.
     *
     * @param webView     The target WebView instance.
     * @param selector    The CSS selector identifying the input element.
     * @param text        The text string to inject.
     * @param onComplete  Optional callback executed upon text injection completion.
     */
    public static void typeTextSimulated(WebView webView, String selector, String text, Runnable onComplete) {
        if (webView == null || selector == null || text == null) {
            return;
        }

        EXECUTOR.execute(() -> {
            // Clear existing value and focus the target element
            String initScript = String.format(
                    "(() => {" +
                    "  const el = document.querySelector('%s');" +
                    "  if (el) { el.focus(); el.value = ''; }" +
                    "})();",
                    escapeJsString(selector)
            );
            MAIN_HANDLER.post(() -> webView.evaluateJavascript(initScript, null));

            for (char ch : text.toCharArray()) {
                long delay = ThreadLocalRandom.current().nextLong(150, 301);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                String charScript = String.format(
                        "(() => {" +
                        "  const el = document.querySelector('%s');" +
                        "  if (el) {" +
                        "    el.value += '%s';" +
                        "    el.dispatchEvent(new Event('input', { bubbles: true }));" +
                        "    el.dispatchEvent(new Event('change', { bubbles: true }));" +
                        "  }" +
                        "})();",
                        escapeJsString(selector),
                        escapeJsString(String.valueOf(ch))
                );

                MAIN_HANDLER.post(() -> webView.evaluateJavascript(charScript, null));
            }

            if (onComplete != null) {
                MAIN_HANDLER.post(onComplete);
            }
        });
    }

    /**
     * Introduces a randomized execution micro-pause (1500ms to 3000ms) before
     * executing an action, allowing the page lifecycle and scripts to settle.
     *
     * @param action The navigation or submission task to execute after the delay.
     */
    public static void scheduleStepWithPause(Runnable action) {
        if (action == null) {
            return;
        }

        long delayMs = ThreadLocalRandom.current().nextLong(1500, 3001);
        MAIN_HANDLER.postDelayed(action, delayMs);
    }

    /**
     * Custom WebViewClient providing automatic retry handling when network timeouts
     * or connection dropouts occur during page operations.
     */
    public static class ResilientWebViewClient extends WebViewClient {

        private static final long RETRY_DELAY_MS = 2000L;

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            handleConnectionError(view, errorCode);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request != null && request.isForMainFrame()) {
                handleConnectionError(view, error.getErrorCode());
            }
        }

        private void handleConnectionError(WebView view, int errorCode) {
            if (errorCode == ERROR_CONNECT || errorCode == ERROR_TIMEOUT) {
                if (view != null) {
                    view.postDelayed(view::reload, RETRY_DELAY_MS);
                }
            }
        }
    }

    /**
     * Sanitizes strings for safe inline execution inside JavaScript snippets.
     */
    private static String escapeJsString(String value) {
        return value.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
    }
  }
