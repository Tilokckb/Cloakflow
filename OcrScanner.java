package com.cloakflow.browser.vision;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/**
 * OcrScanner handles client-side visual evaluation using Google ML Kit.
 * It provides methods to crop visual challenge regions from bitmaps,
 * extract alphanumeric sequences, and inject the parsed results into a WebView.
 */
public final class OcrScanner {

    private static final String TAG = "OcrScanner";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final TextRecognizer RECOGNIZER = 
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    public interface OcrResultCallback {
        void onSuccess(String parsedText);
        void onFailure(Exception exception);
    }

    private OcrScanner() {
        // Prevent direct instantiation
    }

    /**
     * Safely crops a rectangular region from a source Bitmap based on target coordinates.
     *
     * @param sourceBitmap The full rendered screenshot or viewport bitmap.
     * @param cropArea     The rectangular coordinates defining the validation image box.
     * @return A cropped Bitmap matching the bounded region, or null if coordinates are invalid.
     */
    @Nullable
    public static Bitmap cropTargetRegion(@NonNull Bitmap sourceBitmap, @NonNull Rect cropArea) {
        try {
            int x = Math.max(0, cropArea.left);
            int y = Math.max(0, cropArea.top);
            int width = Math.min(cropArea.width(), sourceBitmap.getWidth() - x);
            int height = Math.min(cropArea.height(), sourceBitmap.getHeight() - y);

            if (width <= 0 || height <= 0) {
                Log.e(TAG, "Crop failed: Invalid calculated dimensions (" + width + "x" + height + ")");
                return null;
            }

            return Bitmap.createBitmap(sourceBitmap, x, y, width, height);
        } catch (Exception e) {
            Log.e(TAG, "Exception while cropping bitmap: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Processes an image bitmap using Google ML Kit, extracts alphanumeric characters,
     * injects the resolved string into the WebView DOM element, and optionally triggers a form submit.
     *
     * @param targetBitmap   The cropped verification image bitmap.
     * @param webView        The target WebView receiving the input.
     * @param inputSelector  The CSS selector of the text input field.
     * @param submitSelector Optional CSS selector for the submit button (can be null).
     * @param callback       Callback returning the sanitized text or error details.
     */
    public static void processAndInject(@NonNull Bitmap targetBitmap,
                                        @NonNull WebView webView,
                                        @NonNull String inputSelector,
                                        @Nullable String submitSelector,
                                        @Nullable OcrResultCallback callback) {
        try {
            InputImage inputImage = InputImage.fromBitmap(targetBitmap, 0);

            RECOGNIZER.process(inputImage)
                    .addOnSuccessListener(visionText -> {
                        String rawText = visionText.getText();
                        // Filter whitespace and non-alphanumeric noise
                        String sanitizedText = rawText.replaceAll("\\s+", "");

                        Log.d(TAG, "OCR Recognition Success. Extracted text: [" + sanitizedText + "]");

                        MAIN_HANDLER.post(() -> {
                            injectTextAndSubmit(webView, inputSelector, submitSelector, sanitizedText);
                            if (callback != null) {
                                callback.onSuccess(sanitizedText);
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "OCR Recognition failed: " + e.getMessage(), e);
                        MAIN_HANDLER.post(() -> {
                            if (callback != null) {
                                callback.onFailure(e);
                            }
                        });
                    });
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ML Kit InputImage: " + e.getMessage(), e);
            if (callback != null) {
                callback.onFailure(e);
            }
        }
    }

    /**
     * Injects the decoded string into the designated DOM field and fires standard change events.
     */
    private static void injectTextAndSubmit(@NonNull WebView webView,
                                            @NonNull String inputSelector,
                                            @Nullable String submitSelector,
                                            @NonNull String value) {
        String safeInputSelector = escapeJsString(inputSelector);
        String safeValue = escapeJsString(value);
        String safeSubmitSelector = submitSelector != null ? escapeJsString(submitSelector) : null;

        StringBuilder scriptBuilder = new StringBuilder();
        scriptBuilder.append("(() => {")
                     .append("  const inputEl = document.querySelector('").append(safeInputSelector).append("');")
                     .append("  if (inputEl) {")
                     .append("    inputEl.value = '").append(safeValue).append("';")
                     .append("    inputEl.dispatchEvent(new Event('input', { bubbles: true }));")
                     .append("    inputEl.dispatchEvent(new Event('change', { bubbles: true }));")
                     .append("  }");

        if (safeSubmitSelector != null) {
            scriptBuilder.append("  const submitEl = document.querySelector('").append(safeSubmitSelector).append("');")
                         .append("  if (submitEl) {")
                         .append("    submitEl.click();")
                         .append("  }");
        }

        scriptBuilder.append("})();");

        webView.evaluateJavascript(scriptBuilder.toString(), null);
    }

    private static String escapeJsString(String value) {
        return value.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
    }
              }
