package com.example.project_furnitureapp;

import android.app.Activity; // **** เพิ่ม Import ****
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle; // **** เพิ่ม Import ****

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.BinaryMessenger;

public class MainActivity extends FlutterActivity {
    private static final String TAG = "MainActivity";
    // **** ใช้ ID นี้ให้ตรงกับ main.dart ****
    private static final String AR_VIEW_TYPE = "ar_view";
    // private static final String CHANNEL_NAME = "ar_measurement_channel"; // อาจจะไม่ต้องใช้ Channel ที่นี่แล้ว

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        Log.d(TAG, "Configuring Flutter Engine in MainActivity");

        // Get necessary components from FlutterEngine and Activity
        BinaryMessenger messenger = flutterEngine.getDartExecutor().getBinaryMessenger();
        // **** กำหนดค่า activity และ lifecycle ****
        Activity activity = this;
        Lifecycle lifecycle = getLifecycle(); // ใช้ getLifecycle() ของ FlutterActivity

        // Register the PlatformViewFactory with correct arguments
        try {
            flutterEngine
                    .getPlatformViewsController()
                    .getRegistry()
                    .registerViewFactory(
                        AR_VIEW_TYPE, // ใช้ ID ที่ถูกต้อง ("ar_view")
                        // **** แก้ไข: ใช้ ArMeasureViewFactory ตัวเต็ม ****
                        new ArMeasureViewFactory(messenger, activity, lifecycle)
                    );
            Log.i(TAG, "ArMeasureViewFactory registered with view type: " + AR_VIEW_TYPE);

        } catch (Throwable t) {
             Log.e(TAG, "EXCEPTION during registerViewFactory", t);
             // Handle exception appropriately, maybe show an error to the user
             Toast.makeText(this, "Error setting up AR View component.", Toast.LENGTH_LONG).show();
        }


        // Permission check (ยังคงทำที่นี่ได้ หรือให้ ArMeasureView ทำอย่างเดียวก็ได้)
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Log.d(TAG, "Requesting camera permission from MainActivity.");
            CameraPermissionHelper.requestCameraPermission(this);
        } else {
            Log.d(TAG, "Camera permission already granted.");
        }
    }

    // Handle permission results
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            // finish(); // Optional: Close app if permission denied and essential
        }
        // It's important to call super even if we handle the result.
        // Flutter plugins might also be listening for permission results.
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}