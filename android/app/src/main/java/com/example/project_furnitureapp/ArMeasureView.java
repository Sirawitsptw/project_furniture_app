package com.example.project_furnitureapp;

import android.app.Activity; // import class Activity สำหรับใช้งาน activity ของ Android
import android.content.Context; // import class Context สำหรับใช้งาน context
import android.content.res.AssetManager; // import สำหรับเข้าถึง asset
import android.opengl.GLES30; // import สำหรับใช้งาน OpenGL ES 3.0
import android.opengl.GLSurfaceView; // import สำหรับใช้งาน GLSurfaceView
import android.opengl.Matrix; // import สำหรับ matrix operation
import android.os.Handler; // import สำหรับ Handler (thread/main thread)
import android.os.Looper; // import สำหรับ Looper (main thread)
import android.util.Log; // import สำหรับ log ข้อความ
import android.view.MotionEvent; // import สำหรับ event การสัมผัสหน้าจอ
import android.view.SurfaceHolder; // import สำหรับ surface callback
import android.view.View; // import สำหรับ view ทั่วไป
import android.widget.Toast; // import สำหรับแสดง Toast message
import androidx.annotation.NonNull; // import สำหรับ annotation @NonNull
import androidx.lifecycle.DefaultLifecycleObserver; // import สำหรับ observer lifecycle
import androidx.lifecycle.Lifecycle; // import สำหรับ lifecycle
import androidx.lifecycle.LifecycleOwner; // import สำหรับ owner ของ lifecycle
import com.google.ar.core.*; // import ทุก class ใน package ar.core (Session, Frame, Camera, Plane, Anchor ฯลฯ)
import com.google.ar.core.exceptions.*; // import exception ของ ar.core
import com.google.ar.core.ArCoreApk.InstallStatus; // import enum InstallStatus
import com.example.project_furnitureapp.samplerender.SampleRender; // import SampleRender (renderer)
import com.example.project_furnitureapp.samplerender.arcore.PlaneRenderer; // import PlaneRenderer
import com.example.project_furnitureapp.samplerender.PointRenderer; // import PointRenderer
import com.example.project_furnitureapp.samplerender.GLError; // import GLError
import com.example.project_furnitureapp.samplerender.arcore.BackgroundRenderer; // import BackgroundRenderer
import java.io.IOException; // import IOException
import java.util.ArrayList; // import ArrayList
import java.util.Collections; // import Collections
import java.util.HashMap; // import HashMap
import java.util.List; // import List
import java.util.Map; // import Map
import java.util.concurrent.TimeUnit; // import TimeUnit
import javax.microedition.khronos.egl.EGLConfig; // import EGLConfig
import javax.microedition.khronos.opengles.GL10; // import GL10
import io.flutter.plugin.common.BinaryMessenger; // import BinaryMessenger สำหรับ Flutter channel
import io.flutter.plugin.common.MethodChannel; // import MethodChannel สำหรับ Flutter channel
import io.flutter.plugin.platform.PlatformView; // import PlatformView สำหรับฝัง native view ใน Flutter

public class ArMeasureView implements PlatformView, DefaultLifecycleObserver, GLSurfaceView.Renderer, SurfaceHolder.Callback { // ประกาศคลาสหลัก สืบทอด interface หลายตัว

    private static final String TAG = "ArMeasureView"; // ตัวแปร TAG สำหรับ log
    private static final String LIFECYCLE_TAG = "AR_LIFECYCLE_DEBUG"; // TAG สำหรับ log lifecycle
    private static final String CHANNEL_NAME = "ar_measurement_channel"; // ชื่อ channel สำหรับ Flutter

    private final Context context; // เก็บ context ของแอป
    private final Activity activity; // เก็บ activity
    private final Lifecycle lifecycle; // เก็บ lifecycle
    private final BinaryMessenger messenger; // เก็บ messenger สำหรับ Flutter channel
    private final int viewId; // เก็บ id ของ view
    private final Handler mainHandler = new Handler(Looper.getMainLooper()); // Handler สำหรับรันบน main thread
    private GLSurfaceView glSurfaceView; // GLSurfaceView สำหรับแสดงผล AR
    private MethodChannel methodChannel; // MethodChannel สำหรับสื่อสารกับ Flutter
    private DisplayRotationHelper displayRotationHelper; // Helper สำหรับจัดการการหมุนหน้าจอ
    private TapHelper tapHelper; // Helper สำหรับจัดการ tap event
    private Session arSession; // Session ของ ARCore
    private boolean installGlobalRequested = false; // flag สำหรับเช็คว่าขอ install ARCore ไปแล้วหรือยัง

    private SampleRender sampleRender; // Renderer หลัก
    private BackgroundRenderer backgroundRenderer; // Renderer สำหรับพื้นหลัง (กล้อง)
    private PlaneRenderer planeRenderer; // Renderer สำหรับ plane
    private PointRenderer pointRenderer; // Renderer สำหรับจุดวัด

    private final Handler surfaceSearchHandler = new Handler(Looper.getMainLooper()); // Handler สำหรับค้นหา surface
    private final Runnable surfaceSearchRunnable = () -> showToast("Searching for surfaces..."); // Runnable สำหรับแสดงข้อความค้นหา surface
    private boolean arCoreIsTracking = false; // flag สำหรับเช็คว่า ARCore tracking อยู่หรือไม่

    private final List<Anchor> measurementAnchors = Collections.synchronizedList(new ArrayList<Anchor>()); // รายการ anchor สำหรับจุดวัด
    private final Object anchorLock = new Object(); // object สำหรับ lock การเข้าถึง anchor

    private final float[] projectionMatrix = new float[16]; // matrix สำหรับ projection
    private final float[] viewMatrix = new float[16]; // matrix สำหรับ view

    private volatile boolean arViewIsFrozen = false; // flag สำหรับเช็คว่า view ถูก freeze หรือยัง
    private Frame lastTrackedFrame = null; // เก็บ frame ล่าสุดที่ tracking ได้
    private final float[] frozenViewMatrix = new float[16]; // view matrix ตอน freeze
    private final float[] frozenProjectionMatrix = new float[16]; // projection matrix ตอน freeze

    public ArMeasureView(Context context, Activity activity, Lifecycle lifecycle, BinaryMessenger messenger, int id, Map<String, Object> creationParams) { // constructor
        this.context = context; // กำหนด context
        this.activity = activity; // กำหนด activity
        this.lifecycle = lifecycle; // กำหนด lifecycle
        this.messenger = messenger; // กำหนด messenger
        this.viewId = id; // กำหนด viewId
        try { 
            this.methodChannel = new MethodChannel(messenger, CHANNEL_NAME); // สร้าง method channel สำหรับ Flutter
            setupMethodCallHandler(); // ตั้ง handler สำหรับรับ method call จาก Flutter
        } catch (Throwable t) { 
            Log.e(TAG, "Error MC init", t); // log error ถ้าสร้าง channel ไม่สำเร็จ
        }
        try { 
            this.displayRotationHelper = new DisplayRotationHelper(context); // สร้าง helper สำหรับหมุนหน้าจอ
        } catch (Throwable t) { 
            Log.e(TAG, "Error DRH init", t); // log error ถ้าสร้างไม่สำเร็จ
        }
        try { 
            this.tapHelper = new TapHelper(context); // สร้าง helper สำหรับ tap
        } catch (Throwable t) { 
            Log.e(TAG, "Error TH init", t); // log error ถ้าสร้างไม่สำเร็จ
        }
        try {
            this.glSurfaceView = createSurfaceView(); // สร้าง GLSurfaceView
            if (this.glSurfaceView != null && this.glSurfaceView.getHolder() != null) {
                this.glSurfaceView.getHolder().addCallback(this); // เพิ่ม callback สำหรับ surface event
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error creating GLSurfaceView", t); // log error ถ้าสร้างไม่สำเร็จ
            this.glSurfaceView = null; // set ให้เป็น null
        }
        if (this.lifecycle != null) {
            this.lifecycle.addObserver(this); // เพิ่ม observer ให้ lifecycle
        } else {
            Log.w(TAG, "Lifecycle NULL"); // log เตือนถ้า lifecycle เป็น null
        }
        setupTouchListener(); // ตั้ง touch listener ให้ glSurfaceView
    }

    private GLSurfaceView createSurfaceView() { // เมธอดสร้าง GLSurfaceView
        GLSurfaceView surfaceView = new GLSurfaceView(context); // สร้าง GLSurfaceView ใหม่
        surfaceView.setPreserveEGLContextOnPause(true); // ให้ EGL context คงอยู่ตอน pause
        surfaceView.setEGLContextClientVersion(3); // ใช้ OpenGL ES 3.0
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // กำหนด config ของ surface
        surfaceView.setRenderer(this); // set renderer เป็นคลาสนี้
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY); // render ตลอดเวลา
        surfaceView.setWillNotDraw(false); // อนุญาตให้วาด
        return surfaceView; // คืนค่า surfaceView
    }

    private void setupTouchListener() { // เมธอดตั้ง touch listener
        if (glSurfaceView != null && tapHelper != null) {
            glSurfaceView.setOnTouchListener(tapHelper); // set touch listener ให้ glSurfaceView
        } else {
            Log.e(TAG, "Cannot set touch listener - glSurfaceView or tapHelper is null"); // log error ถ้าไม่ได้ set
        }
    }

    @Override 
    public View getView() { 
        return glSurfaceView != null ? glSurfaceView : new View(context); // คืน glSurfaceView ถ้ามี ไม่งั้นคืน view เปล่า
    }

    @Override 
    public void surfaceCreated(@NonNull SurfaceHolder holder) { 
        Log.d(TAG, "GLSurfaceView Surface CREATED for viewId: " + viewId); // log เมื่อ surface ถูกสร้าง
    }
    
    @Override 
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) { 
        Log.d(TAG, "GLSurfaceView Surface CHANGED: " + width + "x" + height + " for viewId: " + viewId); // log เมื่อ surface เปลี่ยนขนาด
    }
    
    @Override 
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) { 
        Log.d(TAG, "GLSurfaceView Surface DESTROYED for viewId: " + viewId); // log เมื่อ surface ถูกทำลาย
    }

    @Override
    public void dispose() { // เมธอดถูกเรียกเมื่อ Flutter จะ dispose view นี้
        Log.d(LIFECYCLE_TAG, "ArMeasureView dispose() called for viewId: " + viewId); // log
        if (this.lifecycle != null) {
            this.lifecycle.removeObserver(this); // เอา observer ออกจาก lifecycle
        }
        if (glSurfaceView != null) {
            glSurfaceView.queueEvent(this::closeSessionAndRenderers); // สั่งปิด session และ renderer บน GL thread
        } else {
            closeSessionAndRenderers(); // ถ้าไม่มี glSurfaceView เรียกปิดตรงนี้เลย
        }
        if (methodChannel != null) {
            methodChannel.setMethodCallHandler(null); // เอา handler ออกจาก channel
        }
        surfaceSearchHandler.removeCallbacks(surfaceSearchRunnable); // เอา callback ออกจาก handler
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) { // ถูกเรียกเมื่อ lifecycle resume
        Log.d(LIFECYCLE_TAG, "ArMeasureView onResume() for viewId: " + viewId); // log
        arViewIsFrozen = false; // set ว่าไม่ freeze
        arCoreIsTracking = false; // set ว่าไม่ tracking
        if (!CameraPermissionHelper.hasCameraPermission(activity)) { // ถ้าไม่มี permission กล้อง
            CameraPermissionHelper.requestCameraPermission(activity); // ขอ permission
            return; // ออก
        }
        if (arSession == null) { // ถ้า session ยังไม่ถูกสร้าง
            try {
                InstallStatus installStatus = ArCoreApk.getInstance().requestInstall(activity, !installGlobalRequested); // ขอ install ARCore
                if (installStatus == InstallStatus.INSTALL_REQUESTED) { 
                    installGlobalRequested = true; // set flag ว่าเคยขอ install แล้ว
                    return; // ออก
                }
                if (installStatus != InstallStatus.INSTALLED) { 
                    showToast("ARCore not installed. Status: " + installStatus); // แจ้งเตือนถ้าไม่ได้ติดตั้ง
                    return; // ออก
                }
                arSession = new Session(context); // สร้าง session ใหม่
                Config config = new Config(arSession); // สร้าง config
                config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL); // หา plane ทั้งแนวนอนและแนวตั้ง
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE); // ใช้ภาพกล้องล่าสุด
                arSession.configure(config); // กำหนด config ให้ session
            } catch (Exception e) { 
                Log.e(TAG, "Session creation/config failed", e); // log error ถ้าสร้าง session ไม่สำเร็จ
                showToast("Failed AR session"); // แจ้งเตือน
                arSession = null; // set session เป็น null
                return; // ออก
            }
        }
        try { 
            arSession.resume(); // resume session
        } catch (Exception e) { 
            Log.e(TAG, "Failed to resume AR session", e); // log error ถ้า resume ไม่สำเร็จ
            showToast("Failed to resume AR"); // แจ้งเตือน
            return; // ออก
        }
        if (glSurfaceView != null) { 
            glSurfaceView.onResume(); // resume glSurfaceView
            glSurfaceView.requestRender(); // ขอ render
        }
        if (displayRotationHelper != null) {
            displayRotationHelper.onResume(); // resume displayRotationHelper
        }
        surfaceSearchHandler.removeCallbacks(surfaceSearchRunnable); // เอา callback ออก
        surfaceSearchHandler.postDelayed(surfaceSearchRunnable, 1000); // ตั้ง callback ใหม่หลัง 1 วินาที
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) { // ถูกเรียกเมื่อ lifecycle pause
        Log.d(LIFECYCLE_TAG, "ArMeasureView onPause() for viewId: " + viewId); // log
        if (displayRotationHelper != null) {
            displayRotationHelper.onPause(); // pause displayRotationHelper
        }
        if (glSurfaceView != null) {
            glSurfaceView.onPause(); // pause glSurfaceView
        }
        if (arSession != null) {
            arSession.pause(); // pause session
        }
        surfaceSearchHandler.removeCallbacks(surfaceSearchRunnable); // เอา callback ออก
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) { // ถูกเรียกเมื่อ surface ถูกสร้าง
        Log.d(LIFECYCLE_TAG, "ARView GLSurfaceView.Renderer.onSurfaceCreated for viewId: " + viewId); // log
        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f); // กำหนดสีพื้นหลัง
        GLES30.glEnable(GLES30.GL_DEPTH_TEST); // เปิด depth test
        GLES30.glEnable(GLES30.GL_BLEND); // เปิด blend
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA); // กำหนด blend function
        try {
            AssetManager assetManager = context.getAssets(); // ดึง asset manager
            SampleRender.Renderer dummyRenderer = new SampleRender.Renderer() { // สร้าง renderer เปล่า
                @Override 
                public void onSurfaceCreated(SampleRender render) {}
                
                @Override 
                public void onSurfaceChanged(SampleRender render, int width, int height) {}
                
                @Override 
                public void onDrawFrame(SampleRender render) {}
            };
            sampleRender = new SampleRender(this.glSurfaceView, dummyRenderer, assetManager); // สร้าง sampleRender
            backgroundRenderer = new BackgroundRenderer(sampleRender); // สร้าง backgroundRenderer
            planeRenderer = new PlaneRenderer(sampleRender); // สร้าง planeRenderer
            pointRenderer = new PointRenderer(sampleRender); // สร้าง pointRenderer
            pointRenderer.setColor(new float[]{1.0f, 0.0f, 1.0f, 1.0f}); // กำหนดสีจุดวัด
            pointRenderer.setPointSize(25.0f); // กำหนดขนาดจุดวัด
        } catch (Throwable t) { 
            Log.e(TAG, "EXCEPTION in onSurfaceCreated", t); // log error ถ้าสร้าง renderer ไม่สำเร็จ
            closeSessionAndRenderers(); // ปิด session และ renderer
        }
    }

    private void closeSessionAndRenderers() { // เมธอดสำหรับปิด session และ renderer
        Log.d(TAG, "Closing session and nullifying ARCore renderers for viewId: " + viewId); // log
        if (arSession != null) { 
            arSession.close(); // ปิด session
            arSession = null; // set เป็น null
        }
        if (planeRenderer != null) { 
            planeRenderer.close(); // ปิด planeRenderer
            planeRenderer = null; // set เป็น null
        }
        if (backgroundRenderer != null) { 
            backgroundRenderer.close(); // ปิด backgroundRenderer
            backgroundRenderer = null; // set เป็น null
        }
        if (pointRenderer != null) { 
            pointRenderer.close(); // ปิด pointRenderer
            pointRenderer = null; // set เป็น null
        }
        sampleRender = null; // set sampleRender เป็น null
        lastTrackedFrame = null; // set lastTrackedFrame เป็น null
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) { // ถูกเรียกเมื่อ surface เปลี่ยนขนาด
        Log.d(LIFECYCLE_TAG, "ARView GLSurfaceView.Renderer.onSurfaceChanged: " + width + "x" + height + " for viewId: " + viewId); // log
        GLES30.glViewport(0, 0, width, height); // กำหนด viewport
        if (displayRotationHelper != null) {
            displayRotationHelper.onSurfaceChanged(width, height); // แจ้ง displayRotationHelper
        }
        if (sampleRender != null) {
            sampleRender.setViewport(width, height); // แจ้ง sampleRender
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) { // ถูกเรียกทุก frame เพื่อวาด AR
        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f); // กำหนดสีพื้นหลัง
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT); // ล้าง buffer

        if (arViewIsFrozen) { // ถ้า freeze view
            if (lastTrackedFrame == null || backgroundRenderer == null || pointRenderer == null) {
                 Log.w(TAG, "onDrawFrame (Frozen): Missing components for rendering frozen scene."); // log ถ้าขาด component
                 return; // ออก
            }
            backgroundRenderer.draw(lastTrackedFrame); // วาดพื้นหลังจาก frame ล่าสุด
            GLES30.glDepthMask(true); 
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            synchronized (anchorLock) {
                if (!measurementAnchors.isEmpty()) {
                    pointRenderer.drawPoints(measurementAnchors, frozenViewMatrix, frozenProjectionMatrix); // วาดจุดวัด
                }
            }
        } else { // Live AR State
            if (arSession == null || backgroundRenderer == null || planeRenderer == null || pointRenderer == null || displayRotationHelper == null || sampleRender == null) {
                Log.w(TAG, "onDrawFrame (Live): Missing essential AR components."); // log ถ้าขาด component
                return; // ออก
            }
            try {
                displayRotationHelper.updateSessionIfNeeded(arSession); // อัปเดต session ถ้าจำเป็น
                int textureId = backgroundRenderer.getTextureId(); // ดึง texture id
                if (textureId <= 0) { 
                    return; // ออกถ้า texture id ไม่ถูกต้อง
                }
                arSession.setCameraTextureName(textureId); // set texture ให้ session

                Frame frame = arSession.update(); // อัปเดต frame
                if (frame == null) { 
                    return; // ออกถ้าไม่มี frame
                }
                Camera camera = frame.getCamera(); // ดึงกล้องจาก frame
                if (camera == null) { 
                    return; // ออกถ้าไม่มีกล้อง
                }

                backgroundRenderer.draw(frame); // วาดพื้นหลัง

                TrackingState cameraTrackingState = camera.getTrackingState(); // เช็คสถานะ tracking
                if (cameraTrackingState == TrackingState.TRACKING) {
                    this.lastTrackedFrame = frame; // เก็บ frame ล่าสุด
                    camera.getViewMatrix(viewMatrix, 0); // ดึง view matrix
                    camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f); // ดึง projection matrix

                    if (!arCoreIsTracking) {
                        arCoreIsTracking = true; // set ว่า tracking แล้ว
                        surfaceSearchHandler.removeCallbacks(surfaceSearchRunnable); // เอา callback ออก
                        showToast("Surface detected. Ready."); // แจ้งเตือน
                        Log.i(TAG, "ARCore Tracking State: TRACKING. Surface detected."); // log
                    }
                    planeRenderer.drawPlanes(arSession.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projectionMatrix); // วาด plane
                    synchronized (anchorLock) {
                        if (!measurementAnchors.isEmpty()) {
                            pointRenderer.drawPoints(measurementAnchors, viewMatrix, projectionMatrix); // วาดจุดวัด
                        }
                    }
                    if (tapHelper != null) {
                        MotionEvent tap = tapHelper.poll(); // ดึง tap event
                        if (tap != null) {
                            handleTapForMeasurement(frame, camera, tap); // จัดการ tap
                        }
                    }
                } else {
                    if (arCoreIsTracking) {
                        arCoreIsTracking = false; // set ว่าไม่ tracking
                        showToast("Tracking lost. Move device slowly."); // แจ้งเตือน
                        Log.w(TAG, "ARCore Tracking State: Lost tracking. Now: " + cameraTrackingState); // log
                    }
                    if (!surfaceSearchHandler.hasCallbacks(surfaceSearchRunnable)) {
                         surfaceSearchHandler.postDelayed(surfaceSearchRunnable, 500); // ตั้ง callback ใหม่
                    }
                }
            } catch (Throwable t) { 
                Log.e(TAG, "Exception in onDrawFrame (Live AR)", t); // log error ถ้าวาดไม่สำเร็จ
            }
        }
    }

    private void handleTapForMeasurement(Frame frame, Camera camera, MotionEvent tap) { // เมธอดจัดการ tap เพื่อวัด
        if (arViewIsFrozen) { // ถ้า freeze อยู่
            return; // ออก
        }
        if (frame == null || camera == null || tap == null || camera.getTrackingState() != TrackingState.TRACKING) { // ถ้าเงื่อนไขไม่ครบ
             if (camera != null && camera.getTrackingState() != TrackingState.TRACKING) {
                showToast("Cannot place point: Scene not stable."); // แจ้งเตือน
            }
            return; // ออก
        }
        Log.d(TAG, "Native: Handling tap for measurement at X=" + tap.getX() + ", Y=" + tap.getY()); // log
        List<HitResult> hitResultList = frame.hitTest(tap); // หาว่า tap ไปโดนอะไรในโลก 3D
        Anchor newAnchor = null; // ตัวแปร anchor ใหม่
        for (HitResult hit : hitResultList) {
            Trackable trackable = hit.getTrackable(); // ดึง trackable
            Pose hitPose = hit.getHitPose(); // ดึง pose
            if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hitPose) && ((Plane) trackable).getSubsumedBy() == null) {
                try { 
                    newAnchor = hit.createAnchor(); // สร้าง anchor ใหม่
                    break; // ออกจาก loop
                }
                catch (Exception e) { 
                    Log.e(TAG, "Error creating measurement anchor", e); // log error ถ้าสร้าง anchor ไม่สำเร็จ
                }
            }
        }
        if (newAnchor != null) {
            synchronized (anchorLock) {
                if (measurementAnchors.size() >= 4) { // ถ้ามี anchor เกิน 4
                    List<Anchor> toDetach = new ArrayList<>(measurementAnchors); // copy anchor เดิม
                    measurementAnchors.clear(); // ล้าง anchor เดิม
                    for(Anchor a : toDetach) {
                        a.detach(); // detach anchor เดิม
                    }
                }
                measurementAnchors.add(newAnchor); // เพิ่ม anchor ใหม่
            }
            showToast("Placed point " + measurementAnchors.size()); // แจ้งเตือน
            if (measurementAnchors.size() == 2 || measurementAnchors.size() == 4) { // ถ้ามี 2 หรือ 4 จุด
                calculateAndSendDistancesToFlutter(camera); // คำนวณและส่งผลไป Flutter
            }
        } else { 
            Log.w(TAG, "Native: Tap did not hit a valid plane for measurement."); // log ถ้าไม่โดน plane
        }
    }

    private void calculateAndSendDistancesToFlutter(Camera camera) { // เมธอดคำนวณระยะและส่งไป Flutter
        List<Map<String, Object>> measurementsData = new ArrayList<>(); // สร้าง list สำหรับเก็บข้อมูล
        float[] currentViewMatrixToUse = arViewIsFrozen ? frozenViewMatrix : viewMatrix; // เลือก matrix ที่จะใช้
        float[] currentProjMatrixToUse = arViewIsFrozen ? frozenProjectionMatrix : projectionMatrix; // เลือก matrix ที่จะใช้
        synchronized (anchorLock) {
            if (measurementAnchors.size() >= 2) {
                measurementsData.add(getMeasurementDataForPair(measurementAnchors.get(0), measurementAnchors.get(1), camera, "Distance 1", currentViewMatrixToUse, currentProjMatrixToUse)); // คำนวณระยะคู่แรก
            }
            if (measurementAnchors.size() == 4) {
                measurementsData.add(getMeasurementDataForPair(measurementAnchors.get(2), measurementAnchors.get(3), camera, "Distance 2", currentViewMatrixToUse, currentProjMatrixToUse)); // คำนวณระยะคู่ที่สอง
            }
        }
        if (!measurementsData.isEmpty() && methodChannel != null) {
            mainHandler.post(() -> methodChannel.invokeMethod("measurementSetResult", measurementsData)); // ส่งข้อมูลไป Flutter
        }
    }

    private Map<String, Object> getMeasurementDataForPair(Anchor anchor1, Anchor anchor2, Camera cameraForInfo, String label, float[] mvMatrix, float[] projMatrix) { // เมธอดคำนวณข้อมูลระยะ
        Pose pose1 = anchor1.getPose(); // ดึง pose ของ anchor1
        Pose pose2 = anchor2.getPose(); // ดึง pose ของ anchor2
        float dx = pose1.tx() - pose2.tx(); // คำนวณระยะ x
        float dy = pose1.ty() - pose2.ty(); // คำนวณระยะ y
        float dz = pose1.tz() - pose2.tz(); // คำนวณระยะ z
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz); // คำนวณระยะห่าง
        float midX = (pose1.tx() + pose2.tx()) / 2.0f; // หาค่าเฉลี่ย x
        float midY = (pose1.ty() + pose2.ty()) / 2.0f; // หาค่าเฉลี่ย y
        float midZ = (pose1.tz() + pose2.tz()) / 2.0f; // หาค่าเฉลี่ย z
        float[] midPoint3D = {midX, midY, midZ, 1.0f}; // สร้างจุดกลาง 3D
        float[] midPoint2D = new float[2]; // เตรียม array สำหรับจุดกลาง 2D
        Camera cameraToUse = (this.lastTrackedFrame != null && this.lastTrackedFrame.getCamera() != null && arViewIsFrozen) ? this.lastTrackedFrame.getCamera() : cameraForInfo; // เลือกกล้องที่จะใช้
        if (glSurfaceView != null && cameraToUse != null && (arViewIsFrozen || cameraToUse.getTrackingState() == TrackingState.TRACKING)) {
            int currentScreenWidth = glSurfaceView.getWidth(); // ดึงความกว้างหน้าจอ
            int currentScreenHeight = glSurfaceView.getHeight(); // ดึงความสูงหน้าจอ
            projectWorldToScreen(midPoint3D, mvMatrix, projMatrix, currentScreenWidth, currentScreenHeight, midPoint2D); // แปลงจุด 3D เป็น 2D
        } else { 
            midPoint2D[0] = -1; // ถ้าไม่ได้ ให้เป็น -1
            midPoint2D[1] = -1; 
        }
        Map<String, Object> data = new HashMap<>(); // สร้าง map สำหรับเก็บข้อมูล
        data.put("label", label); // ใส่ label
        data.put("distance", distance); // ใส่ระยะ
        data.put("midPointScreenX", (double) midPoint2D[0]); // ใส่ตำแหน่ง x
        data.put("midPointScreenY", (double) midPoint2D[1]); // ใส่ตำแหน่ง y
        return data; // คืนค่า
    }

    private void projectWorldToScreen(float[] worldPoint4D, float[] viewMatrixIn, float[] projectionMatrixIn, int screenWidth, int screenHeight, float[] outScreenPoint2D) { // เมธอดแปลงจุด 3D เป็น 2D
        if (worldPoint4D == null || worldPoint4D.length < 4 || viewMatrixIn == null || projectionMatrixIn == null || outScreenPoint2D == null || outScreenPoint2D.length < 2) {
            if (outScreenPoint2D != null && outScreenPoint2D.length >=2) { 
                outScreenPoint2D[0] = -1; 
                outScreenPoint2D[1] = -1; 
            } 
            return; // ออกถ้าเงื่อนไขไม่ครบ
        }
        if (screenWidth <= 0 || screenHeight <= 0) { 
            outScreenPoint2D[0] = -1; 
            outScreenPoint2D[1] = -1; 
            return; // ออกถ้าขนาดหน้าจอไม่ถูกต้อง
        }
        float[] viewSpacePoint = new float[4]; 
        Matrix.multiplyMV(viewSpacePoint, 0, viewMatrixIn, 0, worldPoint4D, 0); // แปลงจุดไป view space
        float[] clipSpacePoint = new float[4]; 
        Matrix.multiplyMV(clipSpacePoint, 0, projectionMatrixIn, 0, viewSpacePoint, 0); // แปลงจุดไป clip space
        if (clipSpacePoint[3] <= 0.0f) { 
            outScreenPoint2D[0] = -1; 
            outScreenPoint2D[1] = -1; 
            return; // ออกถ้า w <= 0
        }
        float ndcX = clipSpacePoint[0] / clipSpacePoint[3]; // คำนวณ normalized device coordinate x
        float ndcY = clipSpacePoint[1] / clipSpacePoint[3]; // คำนวณ normalized device coordinate y
        outScreenPoint2D[0] = (ndcX + 1.0f) / 2.0f * screenWidth; // แปลงเป็นตำแหน่ง x บนหน้าจอ
        outScreenPoint2D[1] = (1.0f - ndcY) / 2.0f * screenHeight; // แปลงเป็นตำแหน่ง y บนหน้าจอ
    }

    private void freezeArViewNative() { // เมธอด freeze AR view
        if (arSession == null) { 
            Log.w(TAG, "Cannot freeze: Session null."); // log ถ้าไม่มี session
            return; // ออก
        }
        if (arViewIsFrozen) { 
            Log.i(TAG, "Already frozen."); // log ถ้า freeze อยู่แล้ว
            return; // ออก
        }

        if (this.lastTrackedFrame == null || this.lastTrackedFrame.getCamera() == null ||
            this.lastTrackedFrame.getCamera().getTrackingState() != TrackingState.TRACKING) {
            Log.e(TAG, "Cannot freeze: Last available frame was not from a stable tracking state. Current ARCore Tracking: " + arCoreIsTracking);
            mainHandler.post(() -> showToast("Cannot freeze: Scene not stable. Move device and try again.")); // แจ้งเตือน
            return; // ออก
        }
        
        Log.i(TAG, "Freezing AR view using last tracked frame."); // log
        Camera lastValidCamera = this.lastTrackedFrame.getCamera(); // ดึงกล้องจาก frame ล่าสุด
        lastValidCamera.getViewMatrix(this.frozenViewMatrix, 0); // ดึง view matrix
        lastValidCamera.getProjectionMatrix(this.frozenProjectionMatrix, 0, 0.1f, 100.0f); // ดึง projection matrix
        
        this.arViewIsFrozen = true; // set ว่า freeze แล้ว
        Log.i(TAG, "AR view is now frozen. Ready for model placement."); // log
    }

    private void handleClearPoints() { // เมธอดล้างจุดวัดทั้งหมด
        clearAnchorsInternal(); // ล้าง anchor
        mainHandler.post(() -> showToast("Measurements cleared")); // แจ้งเตือน
        this.arViewIsFrozen = false; // set ว่าไม่ freeze
        this.arCoreIsTracking = false; // set ว่าไม่ tracking

        surfaceSearchHandler.removeCallbacks(surfaceSearchRunnable); // เอา callback ออก
        surfaceSearchHandler.postDelayed(surfaceSearchRunnable, 1000); // ตั้ง callback ใหม่
        if (methodChannel != null) {
            mainHandler.post(() -> methodChannel.invokeMethod("pointsCleared", null)); // แจ้ง Flutter ว่าล้างจุดแล้ว
        }
    }

    private void clearAnchorsInternal() { // เมธอดล้าง anchor ทั้งหมด
        synchronized(anchorLock) {
            if (!measurementAnchors.isEmpty()) {
                for (Anchor a : measurementAnchors) {
                    a.detach(); // detach anchor
                }
                measurementAnchors.clear(); // ล้าง list
            }
        }
        Log.i(TAG, "Internal measurement anchors cleared."); // log
    }

    private void setupMethodCallHandler() { // เมธอดตั้ง handler สำหรับรับ method call จาก Flutter
        if (methodChannel != null) {
            methodChannel.setMethodCallHandler((call, methodResult) -> {
                mainHandler.post(() -> {
                    Log.d(TAG, "Method call received: " + call.method + ", Args: " + call.arguments); // log
                    switch (call.method) {
                        case "clearPoints":
                           handleClearPoints(); // ล้างจุด
                           methodResult.success(null); // ตอบกลับ Flutter
                           break;
                        case "freezeArView":
                           freezeArViewNative(); // freeze view
                           methodResult.success(null); // ตอบกลับ Flutter
                           break;
                        default:
                           methodResult.notImplemented(); // ตอบกลับ Flutter ว่าไม่มีเมธอดนี้
                           break;
                    }
                });
            });
        }
    }
    
    private void showToast(final String message) { // เมธอดแสดง Toast
        mainHandler.post(() -> {
            if (context != null) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); // แสดง Toast
            }
        });
    }
    
    private void closeSession() { // เมธอดปิด session
        if (arSession != null) { 
            arSession.close(); // ปิด session
            arSession = null; // set เป็น null
        }
    }
}