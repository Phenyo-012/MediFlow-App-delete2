package com.medicationinventory.ui.scanner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.medicationinventory.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Scans product identifiers with a CameraX preview and ML Kit barcode analyzer, with manual text
 * entry available when camera access is denied or unavailable. Frame analysis runs on a dedicated
 * executor and atomic guards prevent concurrent image work or multiple activity results; the chosen
 * value and detected format are returned to the calling receipt or issue form without changing that
 * form until its caller accepts the result.
 */
public final class BarcodeScannerActivity extends AppCompatActivity {
    public static final String EXTRA_SCANNED_VALUE =
            "com.medicationinventory.EXTRA_SCANNED_VALUE";
    public static final String EXTRA_SCANNED_FORMAT =
            "com.medicationinventory.EXTRA_SCANNED_FORMAT";

    private PreviewView cameraPreview;
    private TextView scanStatus;
    private MaterialButton cameraPermissionButton;
    private MaterialButton torchButton;
    private TextInputEditText manualCodeInput;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private BarcodeScanner barcodeScanner;
    private ExecutorService analysisExecutor;
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final AtomicBoolean resultDelivered = new AtomicBoolean(false);

    // Keep callback fields after the referenced instance fields (no illegal forward reference).
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            cameraPermissionButton.setVisibility(View.GONE);
                            startCamera();
                        } else {
                            scanStatus.setText("Camera access is off. Enter a code below, "
                                    + "or enable camera permission.");
                            cameraPermissionButton.setVisibility(View.VISIBLE);
                        }
                    });

    @Override

    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_barcode_scanner);

        cameraPreview = findViewById(R.id.barcodeCameraPreview);
        scanStatus = findViewById(R.id.barcodeScanStatus);
        cameraPermissionButton = findViewById(R.id.barcodeCameraPermissionButton);
        torchButton = findViewById(R.id.barcodeTorchButton);
        manualCodeInput = findViewById(R.id.barcodeManualInput);

        findViewById(R.id.barcodeBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.barcodeManualSubmit).setOnClickListener(v -> submitManualCode());
        cameraPermissionButton.setOnClickListener(v -> requestCamera());
        torchButton.setOnClickListener(v -> toggleTorch());
        torchButton.setEnabled(false);

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODE_93, Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E, Barcode.FORMAT_ITF,
                        Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX,
                        Barcode.FORMAT_PDF417, Barcode.FORMAT_AZTEC)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        analysisExecutor = Executors.newSingleThreadExecutor();
        requestCamera();
    }


    private void requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            cameraPermissionButton.setVisibility(View.GONE);
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }


    private void startCamera() {
        if (isFinishing() || isDestroyed()) return;
        final ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyzeFrame);
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                torchButton.setEnabled(camera.getCameraInfo().hasFlashUnit());
                scanStatus.setText("Align a barcode or QR code inside the camera frame.");
            } catch (Exception error) {
                scanStatus.setText("Camera unavailable. You can enter the code manually.");
                cameraPermissionButton.setVisibility(View.GONE);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Converts one camera frame to an ML Kit image and scans it off the UI thread. The processing
     * guard drops overlapping frames; every path closes the ImageProxy and releases that guard so
     * CameraX can continue delivering frames.
     */
    @OptIn(markerClass = ExperimentalGetImage.class)

    private void analyzeFrame(@NonNull ImageProxy frame) {
        if (resultDelivered.get() || !processing.compareAndSet(false, true)) {
            frame.close();
            return;
        }
        android.media.Image mediaImage = frame.getImage();
        if (mediaImage == null) {
            processing.set(false);
            frame.close();
            return;
        }
        try {
            InputImage image = InputImage.fromMediaImage(
                    mediaImage, frame.getImageInfo().getRotationDegrees());
            barcodeScanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            String code = ScanTextPolicy.searchableValue(barcode.getRawValue());
                            if (code != null && resultDelivered.compareAndSet(false, true)) {
                                deliver(code, barcode.getFormat());
                                break;
                            }
                        }
                    })
                    .addOnFailureListener(error -> {
                        // Bad frames are common; leave the camera active for retry.
                    })
                    .addOnCompleteListener(task -> {
                        processing.set(false);
                        frame.close();
                    });
        } catch (Exception error) {
            processing.set(false);
            frame.close();
        }
    }


    private void submitManualCode() {
        CharSequence text = manualCodeInput.getText();
        String code = ScanTextPolicy.searchableValue(
                text == null ? null : text.toString());
        if (code == null) {
            manualCodeInput.setError("Enter a code up to 256 characters, on one line.");
            return;
        }
        if (resultDelivered.compareAndSet(false, true)) deliver(code, Barcode.FORMAT_UNKNOWN);
    }


    /** Returns one validated result to the caller and finishes, ignoring later detections in the same scan session. */
    private void deliver(String code, int format) {
        if (isFinishing() || isDestroyed()) return;
        Intent data = new Intent();
        data.putExtra(EXTRA_SCANNED_VALUE, code);
        data.putExtra(EXTRA_SCANNED_FORMAT, format);
        setResult(Activity.RESULT_OK, data);
        finish();
    }


    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) return;
        Integer current = camera.getCameraInfo().getTorchState().getValue();
        boolean next = current == null || current != androidx.camera.core.TorchState.ON;
        camera.getCameraControl().enableTorch(next);
    }

    @Override
    protected void onDestroy() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (analysisExecutor != null) analysisExecutor.shutdown();
        if (barcodeScanner != null) barcodeScanner.close();
        super.onDestroy();
    }
}
