package com.example.project_furnitureapp; // ประกาศ package ของไฟล์นี้

import android.app.Activity; // import Activity สำหรับใช้งาน activity
import android.content.Context; // import Context สำหรับใช้งาน context
import androidx.annotation.NonNull; // import @NonNull สำหรับ annotation
import androidx.annotation.Nullable; // import @Nullable สำหรับ annotation
import androidx.lifecycle.Lifecycle; // import Lifecycle สำหรับจัดการ lifecycle
import io.flutter.plugin.common.BinaryMessenger; // import BinaryMessenger สำหรับสื่อสารกับ Flutter
import io.flutter.plugin.common.StandardMessageCodec; // import StandardMessageCodec สำหรับ encode/decode ข้อมูล
import io.flutter.plugin.platform.PlatformView; // import PlatformView สำหรับสร้าง native view
import io.flutter.plugin.platform.PlatformViewFactory; // import PlatformViewFactory สำหรับสร้าง factory ของ native view
import java.util.Map; // import Map สำหรับเก็บ creation params
import android.util.Log; // import Log สำหรับเขียน log

public class ArMeasureViewFactory extends PlatformViewFactory { // ประกาศคลาส ArMeasureViewFactory สืบทอดจาก PlatformViewFactory
    private static final String TAG = "ArMeasureViewFactory"; // ตัวแปร TAG สำหรับ log

    private final BinaryMessenger messenger; // ตัวแปรเก็บ messenger สำหรับสื่อสารกับ Flutter
    private final Activity activity; // ตัวแปรเก็บ activity
    private final Lifecycle lifecycle; // ตัวแปรเก็บ lifecycle

    public ArMeasureViewFactory(BinaryMessenger messenger, Activity activity, Lifecycle lifecycle) { // constructor รับ messenger, activity, lifecycle
        super(StandardMessageCodec.INSTANCE); // เรียก constructor แม่ กำหนด message codec
        Log.d(TAG, "====== Constructor CALLED ======"); // log ว่า constructor ถูกเรียก
        this.messenger = messenger; // กำหนด messenger
        this.activity = activity; // กำหนด activity
        this.lifecycle = lifecycle; // กำหนด lifecycle
        Log.d(TAG, "====== Constructor FINISHED ======"); // log ว่า constructor ทำงานเสร็จ
    }

    @NonNull
    @Override
    public PlatformView create(@NonNull Context context, int viewId, @Nullable Object args) { // เมธอดสร้าง PlatformView
        Log.d(TAG, "====== create CALLED (View ID: " + viewId + ") ======"); // log ว่า create ถูกเรียก
        Map<String, Object> creationParams = (args instanceof Map) ? (Map<String, Object>) args : null; // แปลง args เป็น Map ถ้าใช่
        // **** สร้าง ArMeasureView (ตัวเต็ม) ****
        ArMeasureView view = new ArMeasureView(context, activity, lifecycle, messenger, viewId, creationParams); // สร้าง ArMeasureView
        Log.d(TAG, "====== create FINISHED (View ID: " + viewId + ") ======"); // log ว่าสร้างเสร็จ
        return view; // คืนค่า view ที่สร้าง
    }
}