package com.example.cloakflow;

import android.graphics.Bitmap;
import android.webkit.WebView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class OcrScanner {
    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    // অন-স্ক্রিন লেটার ক্যাপচা ক্রপ, স্ক্যান এবং বক্সে অটো-পেস্ট মেকানিজম
    public void scanAndPasteCaptcha(Bitmap bitmap, WebView webView) {
        if (bitmap == null) return;
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String cleanText = visionText.getText().replaceAll("\\s+", ""); // স্পেস রিমুভ ফিল্টার
                    if (!cleanText.isEmpty()) {
                        webView.evaluateJavascript("var box = document.querySelector('input[name=\"captcha_response\"], input[id=\"captcha_text_box\"]'); if(box) { box.value = '" + cleanText + "'; }", null);
                    }
                })
                .addOnFailureListener(Throwable::printStackTrace);
    }
}
