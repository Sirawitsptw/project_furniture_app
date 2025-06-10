package com.example.project_furnitureapp; // ประกาศ package ของไฟล์นี้

import android.app.Activity; // import Activity สำหรับใช้งาน activity
import android.os.Bundle; // import Bundle สำหรับจัดการข้อมูลขณะสร้าง activity
import android.util.Log; // import Log สำหรับเขียน log
import android.widget.Toast; // import Toast สำหรับแสดงข้อความ popup
import androidx.annotation.NonNull; // import @NonNull สำหรับ annotation
import androidx.lifecycle.Lifecycle; // import Lifecycle สำหรับจัดการ lifecycle

import io.flutter.embedding.android.FlutterActivity; // import FlutterActivity สำหรับฝัง Flutter ใน Android
import io.flutter.embedding.engine.FlutterEngine; // import FlutterEngine สำหรับจัดการ engine ของ Flutter
import io.flutter.plugin.common.MethodChannel; // import MethodChannel สำหรับสื่อสารกับ Flutter (ไม่ได้ใช้ในไฟล์นี้)
import io.flutter.plugin.common.BinaryMessenger; // import BinaryMessenger สำหรับส่ง message ระหว่าง Flutter กับ native

public class MainActivity extends FlutterActivity { // ประกาศคลาส MainActivity สืบทอดจาก FlutterActivity
    private static final String TAG = "MainActivity"; // ตัวแปร TAG สำหรับ log
    // **** ใช้ ID นี้ให้ตรงกับ main.dart ****
    private static final String AR_VIEW_TYPE = "ar_view"; // กำหนดชื่อ view type สำหรับ PlatformView
    // private static final String CHANNEL_NAME = "ar_measurement_channel"; // อาจจะไม่ต้องใช้ Channel ที่นี่แล้ว (คอมเมนต์ไว้)

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) { // เมธอดสำหรับ config FlutterEngine
        super.configureFlutterEngine(flutterEngine); // เรียก super เพื่อให้ Flutter ทำงานปกติ
        Log.d(TAG, "Configuring Flutter Engine in MainActivity"); // log ว่ากำลัง config engine

        // Get necessary components from FlutterEngine and Activity
        BinaryMessenger messenger = flutterEngine.getDartExecutor().getBinaryMessenger(); // ดึง messenger สำหรับสื่อสารกับ Flutter
        // **** กำหนดค่า activity และ lifecycle ****
        Activity activity = this; // กำหนด activity เป็นตัวเอง
        Lifecycle lifecycle = getLifecycle(); // ใช้ getLifecycle() ของ FlutterActivity

        // Register the PlatformViewFactory with correct arguments
        try {
            flutterEngine
                    .getPlatformViewsController() // ดึง controller สำหรับ PlatformView
                    .getRegistry() // ดึง registry สำหรับลงทะเบียน view
                    .registerViewFactory(
                        AR_VIEW_TYPE, // ใช้ ID ที่ถูกต้อง ("ar_view")
                        // **** แก้ไข: ใช้ ArMeasureViewFactory ตัวเต็ม ****
                        new ArMeasureViewFactory(messenger, activity, lifecycle) // สร้าง factory สำหรับ AR View
                    );
            Log.i(TAG, "ArMeasureViewFactory registered with view type: " + AR_VIEW_TYPE); // log ว่าลงทะเบียนสำเร็จ

        } catch (Throwable t) {
             Log.e(TAG, "EXCEPTION during registerViewFactory", t); // log error ถ้าลงทะเบียนไม่สำเร็จ
             // Handle exception appropriately, maybe show an error to the user
             Toast.makeText(this, "Error setting up AR View component.", Toast.LENGTH_LONG).show(); // แจ้งเตือนผู้ใช้ถ้ามี error
        }

        // Permission check (ยังคงทำที่นี่ได้ หรือให้ ArMeasureView ทำอย่างเดียวก็ได้)
        if (!CameraPermissionHelper.hasCameraPermission(this)) { // ถ้ายังไม่มี permission กล้อง
            Log.d(TAG, "Requesting camera permission from MainActivity."); // log ว่ากำลังขอ permission
            CameraPermissionHelper.requestCameraPermission(this); // ขอ permission กล้อง
        } else {
            Log.d(TAG, "Camera permission already granted."); // log ว่าได้ permission แล้ว
        }
    }

    // Handle permission results
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { // เมธอดจัดการผลการขอ permission
        if (!CameraPermissionHelper.hasCameraPermission(this)) { // ถ้ายังไม่ได้รับ permission
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show(); // แจ้งเตือนผู้ใช้
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) { // ถ้าผู้ใช้ติ๊ก "Don't ask again"
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this); // เปิดหน้าตั้งค่า permission ให้ผู้ใช้
            }
            // finish(); // Optional: Close app if permission denied and essential (คอมเมนต์ไว้)
        }
        // It's important to call super even if we handle the result.
        // Flutter plugins might also be listening for permission results.
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // เรียก super เพื่อให้ Flutter จัดการต่อ
    }
}