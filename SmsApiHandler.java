package com.cloakflow.browser.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * SmsApiHandler handles asynchronous communication with a multi-factor verification
 * backend service, token status polling, and dynamic DOM injection into WebView.
 */
public class SmsApiHandler {

    private static final String TAG = "SmsApiHandler";
    private static final long POLLING_INTERVAL_MS = 3000L;

    private final OkHttpClient httpClient;
    private final Handler mainHandler;
    private final String gatewayBaseUrl;
    
    private final AtomicBoolean isManuallyOverridden = new AtomicBoolean(false);
    private Runnable activePollingTask;

    public interface TelephonyResponseCallback {
        void onSuccess(String orderId, String phoneNumber);
        void onFailure(Exception exception);
    }

    public interface TokenPollCallback {
        void onTokenReceived(String token);
        void onFailure(Exception exception);
    }

    public SmsApiHandler(String gatewayBaseUrl) {
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Sends an asynchronous POST request to provision telephony data for testing.
     *
     * @param provider    The service provider ID or name.
     * @param countryId   The regional country code identifier.
     * @param serviceType The targeted verification service tag.
     * @param callback    Callback returning the assigned order ID and phone number.
     */
    public void requestTelephonyData(String provider, String countryId, String serviceType,
                                     TelephonyResponseCallback callback) {
        RequestBody formBody = new FormBody.Builder()
                .add("provider", provider)
                .add("countryId", countryId)
                .add("serviceType", serviceType)
                .build();

        Request request = new Request.Builder()
                .url(gatewayBaseUrl + "/api/v1/telephony/order")
                .post(formBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Telephony provisioning network error: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onFailure(e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        throw new IOException("Server error response: " + response.code());
                    }

                    String jsonString = responseBody.string();
                    JSONObject json = new JSONObject(jsonString);

                    String orderId = json.optString("order_id", "");
                    String phoneNumber = json.optString("phone_number", "");

                    if (orderId.isEmpty() || phoneNumber.isEmpty()) {
                        throw new JSONException("Malformed JSON: missing order_id or phone_number");
                    }

                    mainHandler.post(() -> callback.onSuccess(orderId, phoneNumber));
                } catch (Exception e) {
                    Log.e(TAG, "Telephony provisioning parse error: " + e.getMessage(), e);
                    mainHandler.post(() -> callback.onFailure(e));
                }
            }
        });
    }

    /**
     * Starts a recurring polling task that checks the gateway for incoming verification tokens
     * every 3 seconds and automatically injects the resolved code into the WebView.
     *
     * @param orderId       The unique transaction/order ID to poll.
     * @param webView       The active WebView instance receiving the token.
     * @param inputSelector The CSS selector of the input field.
     * @param tokenCallback Optional callback notifying completion or failures.
     */
    public void startTokenPolling(String orderId, WebView webView, String inputSelector,
                                  TokenPollCallback tokenCallback) {
        stopPolling();
        isManuallyOverridden.set(false);

        activePollingTask = new Runnable() {
            @Override
            public void run() {
                if (isManuallyOverridden.get()) {
                    Log.i(TAG, "Polling loop aborted due to manual override.");
                    stopPolling();
                    return;
                }

                HttpUrl url = HttpUrl.parse(gatewayBaseUrl + "/api/v1/telephony/status")
                        .newBuilder()
                        .addQueryParameter("order_id", orderId)
                        .build();

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.w(TAG, "Transient polling error: " + e.getMessage());
                        scheduleNextPoll();
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        try (ResponseBody responseBody = response.body()) {
                            if (!response.isSuccessful() || responseBody == null) {
                                scheduleNextPoll();
                                return;
                            }

                            String responseData = responseBody.string();
                            JSONObject json = new JSONObject(responseData);
                            String status = json.optString("status", "PENDING");

                            if ("READY".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)) {
                                String token = json.optString("token", "");
                                if (!token.isEmpty()) {
                                    mainHandler.post(() -> {
                                        injectTokenIntoWebView(webView, inputSelector, token);
                                        if (tokenCallback != null) {
                                            tokenCallback.onTokenReceived(token);
                                        }
                                    });
                                    stopPolling();
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error while parsing polling response: " + e.getMessage(), e);
                        }

                        scheduleNextPoll();
                    }
                });
            }

            private void scheduleNextPoll() {
                if (activePollingTask != null && !isManuallyOverridden.get()) {
                    mainHandler.postDelayed(activePollingTask, POLLING_INTERVAL_MS);
                }
            }
        };

        mainHandler.post(activePollingTask);
    }

    /**
     * Injects a token into the designated DOM field and fires standard JavaScript events.
     */
    private void injectTokenIntoWebView(WebView webView, String inputSelector, String token) {
        if (webView == null || inputSelector == null || token == null) {
            return;
        }

        String safeSelector = escapeJsString(inputSelector);
        String safeToken = escapeJsString(token);

        String injectionScript = String.format(
                "(() => {" +
                "  const el = document.querySelector('%s');" +
                "  if (el) {" +
                "    el.value = '%s';" +
                "    el.dispatchEvent(new Event('input', { bubbles: true }));" +
                "    el.dispatchEvent(new Event('change', { bubbles: true }));" +
                "  }" +
                "})();",
                safeSelector,
                safeToken
        );

        webView.evaluateJavascript(injectionScript, null);
    }

    /**
     * Allows a manual override to stop the automated polling cycle and inject custom input.
     *
     * @param webView       The target WebView instance.
     * @param inputSelector The CSS selector of the input field.
     * @param manualToken   The custom verification string to inject.
     */
    public void setManualOverride(WebView webView, String inputSelector, String manualToken) {
        isManuallyOverridden.set(true);
        stopPolling();

        if (webView != null && inputSelector != null && manualToken != null) {
            mainHandler.post(() -> injectTokenIntoWebView(webView, inputSelector, manualToken));
        }
    }

    /**
     * Halts any active polling cycle.
     */
    public void stopPolling() {
        if (activePollingTask != null) {
            mainHandler.removeCallbacks(activePollingTask);
            activePollingTask = null;
        }
    }

    private static String escapeJsString(String value) {
        return value.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
    }
                    }
