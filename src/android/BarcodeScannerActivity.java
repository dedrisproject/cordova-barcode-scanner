package com.dedris.barcodescanner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.Image;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Full-screen scanner that decodes 1D linear barcodes using CameraX for the
 * camera feed and ML Kit (bundled, offline) for decoding.
 *
 * QR codes and other 2D symbologies are never enabled on the scanner, so they
 * are simply ignored even if they appear in front of the camera.
 */
public class BarcodeScannerActivity extends ComponentActivity {

    public static final String EXTRA_PROMPT = "prompt";
    public static final String EXTRA_FORMATS = "formats";
    public static final String EXTRA_BEEP = "beep";

    public static final String RESULT_TEXT = "text";
    public static final String RESULT_FORMAT = "format";
    public static final String RESULT_ERROR = "error";

    private static final String DEFAULT_PROMPT = "Point your camera at a barcode";

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private ProcessCameraProvider cameraProvider;
    private ActivityResultLauncher<String> permissionLauncher;

    private final AtomicBoolean handled = new AtomicBoolean(false);
    private boolean beepEnabled = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        beepEnabled = getIntent().getBooleanExtra(EXTRA_BEEP, true);
        String prompt = getIntent().getStringExtra(EXTRA_PROMPT);

        setContentView(buildContentView(prompt));

        barcodeScanner = BarcodeScanning.getClient(buildScannerOptions());
        cameraExecutor = Executors.newSingleThreadExecutor();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startCamera();
                    } else {
                        failAndFinish("Camera permission was denied");
                    }
                });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // ----------------------------------------------------------------- UI ---

    private View buildContentView(@Nullable String prompt) {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.BLACK);

        previewView = new PreviewView(this);
        previewView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(previewView);

        // Centered aiming line (barcodes are wide, so a horizontal guide helps).
        View laser = new View(this);
        FrameLayout.LayoutParams laserParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        laserParams.gravity = Gravity.CENTER;
        laserParams.leftMargin = dp(48);
        laserParams.rightMargin = dp(48);
        laser.setLayoutParams(laserParams);
        laser.setBackgroundColor(Color.parseColor("#CCFF3B30"));
        root.addView(laser);

        TextView promptView = new TextView(this);
        FrameLayout.LayoutParams promptParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        promptParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        promptParams.topMargin = dp(48);
        promptParams.leftMargin = dp(24);
        promptParams.rightMargin = dp(24);
        promptView.setLayoutParams(promptParams);
        promptView.setText(prompt != null && !prompt.isEmpty() ? prompt : DEFAULT_PROMPT);
        promptView.setTextColor(Color.WHITE);
        promptView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        promptView.setGravity(Gravity.CENTER);
        promptView.setBackgroundColor(Color.parseColor("#99000000"));
        promptView.setPadding(dp(16), dp(10), dp(16), dp(10));
        root.addView(promptView);

        Button cancelButton = new Button(this);
        FrameLayout.LayoutParams cancelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        cancelParams.bottomMargin = dp(48);
        cancelButton.setLayoutParams(cancelParams);
        cancelButton.setText(android.R.string.cancel);
        cancelButton.setOnClickListener(v -> cancelAndFinish());
        root.addView(cancelButton);

        return root;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------- Scanner ---

    private BarcodeScannerOptions buildScannerOptions() {
        int[] formats = resolveFormats(getIntent().getStringExtra(EXTRA_FORMATS));
        BarcodeScannerOptions.Builder builder = new BarcodeScannerOptions.Builder();
        if (formats.length == 1) {
            builder.setBarcodeFormats(formats[0]);
        } else {
            int[] rest = new int[formats.length - 1];
            System.arraycopy(formats, 1, rest, 0, rest.length);
            builder.setBarcodeFormats(formats[0], rest);
        }
        return builder.build();
    }

    /**
     * Resolves the requested format names to ML Kit format flags, restricted to
     * 1D symbologies. Unknown or 2D formats (e.g. QR_CODE) are ignored. If the
     * caller does not request anything valid, every supported 1D format is used.
     */
    private int[] resolveFormats(@Nullable String formatsJson) {
        List<Integer> selected = new ArrayList<>();
        if (formatsJson != null) {
            try {
                JSONArray array = new JSONArray(formatsJson);
                for (int i = 0; i < array.length(); i++) {
                    int flag = formatFlag(array.optString(i, ""));
                    if (flag != 0 && !selected.contains(flag)) {
                        selected.add(flag);
                    }
                }
            } catch (Exception ignored) {
                // Fall through to defaults.
            }
        }

        if (selected.isEmpty()) {
            selected.add(Barcode.FORMAT_CODE_128);
            selected.add(Barcode.FORMAT_CODE_39);
            selected.add(Barcode.FORMAT_CODE_93);
            selected.add(Barcode.FORMAT_CODABAR);
            selected.add(Barcode.FORMAT_EAN_13);
            selected.add(Barcode.FORMAT_EAN_8);
            selected.add(Barcode.FORMAT_ITF);
            selected.add(Barcode.FORMAT_UPC_A);
            selected.add(Barcode.FORMAT_UPC_E);
        }

        int[] result = new int[selected.size()];
        for (int i = 0; i < selected.size(); i++) {
            result[i] = selected.get(i);
        }
        return result;
    }

    /** Maps a public format name to a 1D ML Kit flag, or 0 if not a 1D format. */
    private int formatFlag(String name) {
        switch (name.trim().toUpperCase()) {
            case "CODE_128": return Barcode.FORMAT_CODE_128;
            case "CODE_39": return Barcode.FORMAT_CODE_39;
            case "CODE_93": return Barcode.FORMAT_CODE_93;
            case "CODABAR": return Barcode.FORMAT_CODABAR;
            case "EAN_13": return Barcode.FORMAT_EAN_13;
            case "EAN_8": return Barcode.FORMAT_EAN_8;
            case "ITF": return Barcode.FORMAT_ITF;
            case "UPC_A": return Barcode.FORMAT_UPC_A;
            case "UPC_E": return Barcode.FORMAT_UPC_E;
            default: return 0; // QR_CODE / AZTEC / DATA_MATRIX / PDF417 / unknown
        }
    }

    private String formatName(int format) {
        switch (format) {
            case Barcode.FORMAT_CODE_128: return "CODE_128";
            case Barcode.FORMAT_CODE_39: return "CODE_39";
            case Barcode.FORMAT_CODE_93: return "CODE_93";
            case Barcode.FORMAT_CODABAR: return "CODABAR";
            case Barcode.FORMAT_EAN_13: return "EAN_13";
            case Barcode.FORMAT_EAN_8: return "EAN_8";
            case Barcode.FORMAT_ITF: return "ITF";
            case Barcode.FORMAT_UPC_A: return "UPC_A";
            case Barcode.FORMAT_UPC_E: return "UPC_E";
            default: return "UNKNOWN";
        }
    }

    // -------------------------------------------------------------- Camera ---

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindUseCases();
            } catch (Exception e) {
                failAndFinish("Unable to start the camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases() {
        if (cameraProvider == null || handled.get()) {
            return;
        }

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyze);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
        } catch (Exception e) {
            failAndFinish("Unable to bind the camera: " + e.getMessage());
        }
    }

    @ExperimentalGetImage
    private void analyze(@NonNull ImageProxy imageProxy) {
        if (handled.get()) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (handled.get() || barcodes.isEmpty()) {
                        return;
                    }
                    Barcode barcode = barcodes.get(0);
                    String value = barcode.getRawValue();
                    if (value == null) {
                        value = barcode.getDisplayValue();
                    }
                    if (value != null) {
                        onBarcodeFound(value, formatName(barcode.getFormat()));
                    }
                })
                .addOnFailureListener(e -> {
                    // Single-frame failures are expected; just keep scanning.
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onBarcodeFound(final String text, final String format) {
        if (!handled.compareAndSet(false, true)) {
            return;
        }
        runOnUiThread(() -> {
            beep();
            Intent data = new Intent();
            data.putExtra(RESULT_TEXT, text);
            data.putExtra(RESULT_FORMAT, format);
            setResult(Activity.RESULT_OK, data);
            finish();
        });
    }

    private void beep() {
        if (!beepEnabled) {
            return;
        }
        try {
            ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
        } catch (Exception ignored) {
            // Some devices throw if the audio stream is unavailable; ignore.
        }
    }

    // ------------------------------------------------------------- Finish ---

    @Override
    public void onBackPressed() {
        cancelAndFinish();
    }

    private void cancelAndFinish() {
        if (!handled.compareAndSet(false, true)) {
            return;
        }
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    private void failAndFinish(String message) {
        if (!handled.compareAndSet(false, true)) {
            return;
        }
        Intent data = new Intent();
        data.putExtra(RESULT_ERROR, message);
        setResult(Activity.RESULT_CANCELED, data);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception ignored) {
            }
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
    }
}
