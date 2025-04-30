package com.example.project_furnitureapp;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.StandardMessageCodec;
import io.flutter.plugin.platform.PlatformView;
import io.flutter.plugin.platform.PlatformViewFactory;
import java.util.Map;
import android.util.Log;

public class ArMeasureViewFactory extends PlatformViewFactory {
    private static final String TAG = "ArMeasureViewFactory";

    private final BinaryMessenger messenger;
    private final Activity activity;
    private final Lifecycle lifecycle;

    public ArMeasureViewFactory(BinaryMessenger messenger, Activity activity, Lifecycle lifecycle) {
        super(StandardMessageCodec.INSTANCE);
        Log.d(TAG, "====== Constructor CALLED ======");
        this.messenger = messenger;
        this.activity = activity;
        this.lifecycle = lifecycle;
        Log.d(TAG, "====== Constructor FINISHED ======");
    }

    @NonNull
    @Override
    public PlatformView create(@NonNull Context context, int viewId, @Nullable Object args) {
        Log.d(TAG, "====== create CALLED (View ID: " + viewId + ") ======");
        Map<String, Object> creationParams = (args instanceof Map) ? (Map<String, Object>) args : null;
        // **** สร้าง ArMeasureView (ตัวเต็ม) ****
        ArMeasureView view = new ArMeasureView(context, activity, lifecycle, messenger, viewId, creationParams);
        Log.d(TAG, "====== create FINISHED (View ID: " + viewId + ") ======");
        return view;
    }
}