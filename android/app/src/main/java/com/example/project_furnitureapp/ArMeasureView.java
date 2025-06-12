package com.example.project_furnitureapp; // ประกาศ package ของไฟล์นี้

// --- import ที่จำเป็นสำหรับ AR, OpenGL, Android, Flutter ---
import android.app.Activity; // สำหรับใช้งาน Activity
import android.content.Context; // สำหรับใช้งาน Context
import android.opengl.GLES30; // สำหรับเรียกใช้ OpenGL ES 3.0
import android.opengl.GLSurfaceView; // สำหรับแสดงผล OpenGL
import android.opengl.Matrix; // สำหรับคำนวณเมทริกซ์
import android.os.Handler; // สำหรับรันโค้ดบน thread อื่น
import android.os.Looper; // สำหรับดึง main thread
import android.view.MotionEvent; // สำหรับรับ event การสัมผัสหน้าจอ
import android.view.SurfaceHolder; // สำหรับจัดการ surface
import android.view.View; // สำหรับใช้งาน View
import androidx.annotation.NonNull; // สำหรับ annotation
import androidx.lifecycle.DefaultLifecycleObserver; // สำหรับสังเกต lifecycle
import androidx.lifecycle.Lifecycle; // สำหรับ lifecycle
import androidx.lifecycle.LifecycleOwner; // สำหรับ lifecycle owner
import com.google.ar.core.*; // สำหรับใช้งาน ARCore
import com.google.ar.core.exceptions.*; // สำหรับ exception ของ ARCore
import com.google.ar.core.ArCoreApk.InstallStatus; // สำหรับเช็คสถานะการติดตั้ง ARCore
import com.example.project_furnitureapp.samplerender.SampleRender; // สำหรับวาดกราฟิก
import com.example.project_furnitureapp.samplerender.arcore.PlaneRenderer; // สำหรับวาด plane
import com.example.project_furnitureapp.samplerender.PointRenderer; // สำหรับวาดจุด
import com.example.project_furnitureapp.samplerender.arcore.BackgroundRenderer; // สำหรับวาดกล้อง
import java.io.IOException; // สำหรับจัดการ exception
import java.util.ArrayList; // สำหรับ list
import java.util.Collections; // สำหรับ list
import java.util.HashMap; // สำหรับ map
import java.util.List; // สำหรับ list
import java.util.Map; // สำหรับ map
import javax.microedition.khronos.egl.EGLConfig; // สำหรับ config OpenGL
import javax.microedition.khronos.opengles.GL10; // สำหรับ context OpenGL
import io.flutter.plugin.common.BinaryMessenger; // สำหรับเชื่อมต่อกับ Flutter
import io.flutter.plugin.common.MethodChannel; // สำหรับสื่อสารกับ Flutter
import io.flutter.plugin.platform.PlatformView; // สำหรับฝัง View ใน Flutter

// คลาสหลักสำหรับฝัง ARCore ใน Flutter ผ่าน PlatformView
public class ArMeasureView implements PlatformView, DefaultLifecycleObserver, GLSurfaceView.Renderer, SurfaceHolder.Callback {

    private static final String CHANNEL_NAME = "ar_measurement_channel"; // ชื่อ channel สำหรับสื่อสารกับ Flutter

    private final Context context; // เก็บ context
    private final Activity activity; // เก็บ activity
    private final Lifecycle lifecycle; // เก็บ lifecycle
    private final Handler mainHandler = new Handler(Looper.getMainLooper()); // handler สำหรับ main thread

    private GLSurfaceView glSurfaceView; // view สำหรับแสดงผล OpenGL
    private MethodChannel methodChannel; // สำหรับสื่อสารกับ Flutter
    private DisplayRotationHelper displayRotationHelper; // ช่วยจัดการการหมุนหน้าจอ
    private TapHelper tapHelper; // ช่วยจัดการ event การแตะหน้าจอ
    private Session arSession; // session ของ ARCore
    private boolean installGlobalRequested = false; // flag สำหรับเช็คการติดตั้ง ARCore

    private SampleRender sampleRender; // สำหรับวาดกราฟิก
    private BackgroundRenderer backgroundRenderer; // สำหรับวาดกล้อง
    private PlaneRenderer planeRenderer; // สำหรับวาด plane
    private PointRenderer pointRenderer; // สำหรับวาดจุด

    private final List<Anchor> measurementAnchors = Collections.synchronizedList(new ArrayList<Anchor>()); // รายการ anchor สำหรับการวัด
    private final Object anchorLock = new Object(); // object สำหรับ lock การเข้าถึง anchor

    private final float[] projectionMatrix = new float[16]; // เมทริกซ์ projection
    private final float[] viewMatrix = new float[16]; // เมทริกซ์ view

    // Constructor สำหรับสร้าง View
    public ArMeasureView(Context context, Activity activity, Lifecycle lifecycle, BinaryMessenger messenger, int id, Map<String, Object> creationParams) {
        this.context = context; // กำหนด context
        this.activity = activity; // กำหนด activity
        this.lifecycle = lifecycle; // กำหนด lifecycle

        this.methodChannel = new MethodChannel(messenger, CHANNEL_NAME); // สร้าง method channel
        setupMethodCallHandler(); // ตั้ง handler สำหรับรับคำสั่งจาก Flutter
        this.displayRotationHelper = new DisplayRotationHelper(context); // สร้าง helper สำหรับหมุนหน้าจอ
        this.tapHelper = new TapHelper(context); // สร้าง helper สำหรับแตะหน้าจอ

        this.glSurfaceView = createSurfaceView(); // สร้าง GLSurfaceView
        if (this.glSurfaceView != null && this.glSurfaceView.getHolder() != null) {
            this.glSurfaceView.getHolder().addCallback(this); // เพิ่ม callback สำหรับ surface
        }
        this.lifecycle.addObserver(this); // เพิ่ม observer สำหรับ lifecycle
        setupTouchListener(); // ตั้ง listener สำหรับแตะหน้าจอ
    }

    // สร้าง GLSurfaceView สำหรับแสดงผล OpenGL
    private GLSurfaceView createSurfaceView() {
        GLSurfaceView surfaceView = new GLSurfaceView(context); // สร้าง GLSurfaceView
        surfaceView.setPreserveEGLContextOnPause(true); // ให้ EGL context คงอยู่ตอน pause
        surfaceView.setEGLContextClientVersion(3); // ใช้ OpenGL ES 3.0
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // ตั้งค่า config
        surfaceView.setRenderer(this); // ตั้ง renderer
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY); // วาดต่อเนื่อง
        surfaceView.setWillNotDraw(false); // อนุญาตให้วาด
        return surfaceView; // คืนค่า
    }

    // ตั้ง listener สำหรับแตะหน้าจอ
    private void setupTouchListener() {
        if (glSurfaceView != null && tapHelper != null) {
            glSurfaceView.setOnTouchListener(tapHelper); // ตั้ง tapHelper เป็น listener
        }
    }

    // คืนค่า View สำหรับฝังใน Flutter
    @Override
    public View getView() {
        return glSurfaceView; // คืนค่า GLSurfaceView
    }

    // Callback สำหรับ SurfaceHolder (ไม่ใช้ในที่นี้)
    @Override public void surfaceCreated(@NonNull SurfaceHolder holder) {}
    @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) {}

    // ปิดและล้าง resource เมื่อ dispose
    @Override
    public void dispose() {
        if (this.lifecycle != null) {
            this.lifecycle.removeObserver(this); // ลบ observer
        }
        if (glSurfaceView != null) {
            glSurfaceView.queueEvent(this::closeSessionAndRenderers); // ปิด session และ renderer บน GL thread
        } else {
            closeSessionAndRenderers(); // ปิดทันที
        }
        if (methodChannel != null) {
            methodChannel.setMethodCallHandler(null); // ปิด handler
        }
    }

    // Lifecycle: Resume
    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        if (!CameraPermissionHelper.hasCameraPermission(activity)) {
            CameraPermissionHelper.requestCameraPermission(activity); // ขอ permission กล้อง
            return;
        }
        if (arSession == null) {
            try {
                InstallStatus installStatus = ArCoreApk.getInstance().requestInstall(activity, !installGlobalRequested); // ขอ install ARCore
                if (installStatus == InstallStatus.INSTALL_REQUESTED) {
                    installGlobalRequested = true; // รอการติดตั้ง
                    return;
                }
                if (installStatus != InstallStatus.INSTALLED) { return; } // ถ้ายังไม่ติดตั้ง

                arSession = new Session(context); // สร้าง session
                Config config = new Config(arSession); // สร้าง config
                config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL); // ค้นหา plane ทั้งแนวนอนและแนวตั้ง
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE); // ใช้ภาพกล้องล่าสุด
                arSession.configure(config); // ตั้งค่า session
            } catch (Exception e) {
                arSession = null; // ถ้า error ให้ลบ session
                return;
            }
        }
        try {
            arSession.resume(); // resume session
        } catch (Exception e) { return; }

        if (glSurfaceView != null) {
            glSurfaceView.onResume(); // resume GLSurfaceView
            glSurfaceView.requestRender(); // ขอให้วาดใหม่
        }
        if (displayRotationHelper != null) {
            displayRotationHelper.onResume(); // resume helper
        }
    }

    // Lifecycle: Pause
    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        if (displayRotationHelper != null) displayRotationHelper.onPause(); // pause helper
        if (glSurfaceView != null) glSurfaceView.onPause(); // pause GLSurfaceView
        if (arSession != null) arSession.pause(); // pause session
    }

    // Callback เมื่อ Surface ถูกสร้าง
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f); // ตั้งสีพื้นหลัง
        GLES30.glEnable(GLES30.GL_DEPTH_TEST); // เปิด depth test
        GLES30.glEnable(GLES30.GL_BLEND); // เปิด blend
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA); // ตั้ง blend mode
        try {
            // สร้าง renderer และตัวช่วยวาดต่างๆ
            SampleRender.Renderer dummyRenderer = new SampleRender.Renderer() {
                @Override public void onSurfaceCreated(SampleRender render) {}
                @Override public void onSurfaceChanged(SampleRender render, int width, int height) {}
                @Override public void onDrawFrame(SampleRender render) {}
            };
            sampleRender = new SampleRender(glSurfaceView, dummyRenderer, context.getAssets()); // สร้าง SampleRender
            backgroundRenderer = new BackgroundRenderer(sampleRender); // สร้าง BackgroundRenderer
            planeRenderer = new PlaneRenderer(sampleRender); // สร้าง PlaneRenderer
            pointRenderer = new PointRenderer(sampleRender); // สร้าง PointRenderer
            pointRenderer.setColor(new float[]{1.0f, 0.0f, 1.0f, 1.0f}); // ตั้งสีจุด
            pointRenderer.setPointSize(25.0f); // ตั้งขนาดจุด
        } catch (IOException e) {
            closeSessionAndRenderers(); // ถ้า error ให้ปิด resource
        }
    }

    // ปิด session และ renderer ทั้งหมด
    private void closeSessionAndRenderers() {
        if (arSession != null) {
            arSession.close(); // ปิด session
            arSession = null;
        }
        planeRenderer = null;
        backgroundRenderer = null;
        pointRenderer = null;
        sampleRender = null;
    }

    // Callback เมื่อขนาด Surface เปลี่ยน
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES30.glViewport(0, 0, width, height); // ตั้ง viewport
        displayRotationHelper.onSurfaceChanged(width, height); // แจ้ง helper
        if (sampleRender != null) sampleRender.setViewport(width, height); // ตั้ง viewport ใน SampleRender
    }

    // Callback หลักสำหรับวาดแต่ละเฟรม
    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT); // ล้างหน้าจอ

        if (arSession == null) return; // ถ้าไม่มี session ไม่ต้องวาด

        try {
            displayRotationHelper.updateSessionIfNeeded(arSession); // อัปเดต session ถ้าจำเป็น
            arSession.setCameraTextureName(backgroundRenderer.getTextureId()); // ตั้ง texture ของกล้อง

            Frame frame = arSession.update(); // อัปเดต frame
            Camera camera = frame.getCamera(); // ดึงกล้อง

            backgroundRenderer.draw(frame); // วาดกล้อง

            if (camera.getTrackingState() == TrackingState.TRACKING) { // ถ้ากล้องกำลัง track
                camera.getViewMatrix(viewMatrix, 0); // ดึง view matrix
                camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f); // ดึง projection matrix

                planeRenderer.drawPlanes(arSession.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projectionMatrix); // วาด plane
                synchronized (anchorLock) {
                    if (!measurementAnchors.isEmpty()) {
                        pointRenderer.drawPoints(measurementAnchors, viewMatrix, projectionMatrix); // วาดจุด
                    }
                }

                MotionEvent tap = tapHelper.poll(); // ตรวจสอบการแตะหน้าจอ
                if (tap != null) {
                    handleTapForMeasurement(frame, camera, tap); // จัดการการแตะเพื่อวัด
                }
            }
        } catch (Throwable t) {
            // Error handling can be added here if needed.
        }
    }

    // จัดการการแตะหน้าจอเพื่อวัดระยะ
    private void handleTapForMeasurement(Frame frame, Camera camera, MotionEvent tap) {
        if (camera.getTrackingState() != TrackingState.TRACKING) return; // ถ้าไม่ได้ track ไม่ต้องทำอะไร

        List<HitResult> hitResultList = frame.hitTest(tap); // หาจุดที่แตะบน plane
        Anchor newAnchor = null;
        for (HitResult hit : hitResultList) {
            Trackable trackable = hit.getTrackable();
            if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose()) && ((Plane) trackable).getSubsumedBy() == null) {
                try {
                    newAnchor = hit.createAnchor(); // สร้าง anchor ที่ตำแหน่งที่แตะ
                    break;
                } catch (Exception e) { /* Do nothing */ }
            }
        }

        if (newAnchor != null) {
            synchronized (anchorLock) {
                // --- นำโลจิกการวัด 4 จุดกลับมา ---
                // ถ้ามี anchor ครบ 4 แล้ว ให้ล้างทั้งหมดเพื่อเริ่มวัดชุดใหม่
                if (measurementAnchors.size() >= 4) {
                    List<Anchor> toDetach = new ArrayList<>(measurementAnchors);
                    measurementAnchors.clear();
                    for(Anchor a : toDetach) a.detach();
                }
                measurementAnchors.add(newAnchor); // เพิ่ม anchor ใหม่
            }

            // เมื่อมี Anchor ครบ 2 หรือ 4 จุด ให้คำนวณระยะทาง
            if (measurementAnchors.size() == 2 || measurementAnchors.size() == 4) {
                calculateAndSendDistancesToFlutter(camera); // คำนวณและส่งผลไป Flutter
            }
        }
    }

    // คำนวณระยะทางระหว่าง anchor และส่งข้อมูลไป Flutter
    private void calculateAndSendDistancesToFlutter(Camera camera) {
        List<Map<String, Object>> measurementsData = new ArrayList<>();
        synchronized (anchorLock) {
            // คำนวณระยะทางสำหรับคู่ที่ 1 (anchor 0, 1)
            if (measurementAnchors.size() >= 2) {
                measurementsData.add(getMeasurementDataForPair(measurementAnchors.get(0), measurementAnchors.get(1), camera, "Distance 1", viewMatrix, projectionMatrix));
            }
            // --- นำโลจิกการคำนวณระยะที่ 2 กลับมา ---
            // คำนวณระยะทางสำหรับคู่ที่ 2 (anchor 2, 3)
            if (measurementAnchors.size() == 4) {
                measurementsData.add(getMeasurementDataForPair(measurementAnchors.get(2), measurementAnchors.get(3), camera, "Distance 2", viewMatrix, projectionMatrix));
            }
        }
        if (!measurementsData.isEmpty() && methodChannel != null) {
            mainHandler.post(() -> methodChannel.invokeMethod("measurementSetResult", measurementsData)); // ส่งข้อมูลไป Flutter
        }
    }

    // สร้างข้อมูลการวัดสำหรับ anchor 2 จุด
    private Map<String, Object> getMeasurementDataForPair(Anchor anchor1, Anchor anchor2, Camera cameraForInfo, String label, float[] mvMatrix, float[] projMatrix) {
        Pose pose1 = anchor1.getPose(); // pose ของ anchor 1
        Pose pose2 = anchor2.getPose(); // pose ของ anchor 2
        float dx = pose1.tx() - pose2.tx(); // ความต่าง x
        float dy = pose1.ty() - pose2.ty(); // ความต่าง y
        float dz = pose1.tz() - pose2.tz(); // ความต่าง z
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz); // คำนวณระยะทาง
        float midX = (pose1.tx() + pose2.tx()) / 2.0f; // จุดกึ่งกลาง x
        float midY = (pose1.ty() + pose2.ty()) / 2.0f; // จุดกึ่งกลาง y
        float midZ = (pose1.tz() + pose2.tz()) / 2.0f; // จุดกึ่งกลาง z
        float[] midPoint3D = {midX, midY, midZ, 1.0f}; // จุดกึ่งกลางใน world (homogeneous)
        float[] midPoint2D = new float[2]; // สำหรับเก็บจุดบนหน้าจอ

        if (glSurfaceView != null && cameraForInfo != null && (cameraForInfo.getTrackingState() == TrackingState.TRACKING)) {
            int currentScreenWidth = glSurfaceView.getWidth(); // ความกว้างหน้าจอ
            int currentScreenHeight = glSurfaceView.getHeight(); // ความสูงหน้าจอ
            projectWorldToScreen(midPoint3D, mvMatrix, projMatrix, currentScreenWidth, currentScreenHeight, midPoint2D); // แปลงจุด 3D เป็น 2D
        } else {
            midPoint2D[0] = -1;
            midPoint2D[1] = -1;
        }
        Map<String, Object> data = new HashMap<>(); // สร้าง map สำหรับข้อมูล
        data.put("label", label); // ป้ายกำกับ
        data.put("distance", distance); // ระยะทาง
        data.put("midPointScreenX", (double) midPoint2D[0]); // พิกัด X บนหน้าจอ
        data.put("midPointScreenY", (double) midPoint2D[1]); // พิกัด Y บนหน้าจอ
        return data; // คืนค่า
    }
    
    // แปลงจุด 3D ในโลก เป็นจุด 2D บนหน้าจอ
    private void projectWorldToScreen(float[] worldPoint4D, float[] viewMatrixIn, float[] projectionMatrixIn, int screenWidth, int screenHeight, float[] outScreenPoint2D) {
        if (worldPoint4D == null || worldPoint4D.length < 4 || viewMatrixIn == null || projectionMatrixIn == null || outScreenPoint2D == null || outScreenPoint2D.length < 2) {
            if (outScreenPoint2D != null && outScreenPoint2D.length >=2) { outScreenPoint2D[0] = -1; outScreenPoint2D[1] = -1; } return;
        }
        if (screenWidth <= 0 || screenHeight <= 0) { outScreenPoint2D[0] = -1; outScreenPoint2D[1] = -1; return; }
        float[] viewSpacePoint = new float[4]; Matrix.multiplyMV(viewSpacePoint, 0, viewMatrixIn, 0, worldPoint4D, 0); // world -> view
        float[] clipSpacePoint = new float[4]; Matrix.multiplyMV(clipSpacePoint, 0, projectionMatrixIn, 0, viewSpacePoint, 0); // view -> clip
        if (clipSpacePoint[3] <= 0.0f) { outScreenPoint2D[0] = -1; outScreenPoint2D[1] = -1; return; }
        float ndcX = clipSpacePoint[0] / clipSpacePoint[3]; float ndcY = clipSpacePoint[1] / clipSpacePoint[3]; // แปลงเป็น NDC
        outScreenPoint2D[0] = (ndcX + 1.0f) / 2.0f * screenWidth; // NDC -> screen X
        outScreenPoint2D[1] = (1.0f - ndcY) / 2.0f * screenHeight; // NDC -> screen Y
    }

    // จัดการคำสั่งล้างจุดวัดจาก Flutter
    private void handleClearPoints() {
        clearAnchorsInternal(); // ลบ anchor ทั้งหมด
        if (methodChannel != null) mainHandler.post(() -> methodChannel.invokeMethod("pointsCleared", null)); // แจ้ง Flutter
    }

    // ลบ anchor ทั้งหมด
    private void clearAnchorsInternal() {
        synchronized(anchorLock) {
            if (!measurementAnchors.isEmpty()) {
                for (Anchor a : measurementAnchors) a.detach(); // ลบ anchor
                measurementAnchors.clear(); // ล้าง list
            }
        }
    }

    // ตั้ง handler สำหรับรับ method call จาก Flutter
    private void setupMethodCallHandler() {
        if (methodChannel != null) {
            methodChannel.setMethodCallHandler((call, methodResult) -> {
                mainHandler.post(() -> {
                    switch (call.method) {
                        case "clearPoints":
                           handleClearPoints(); // ล้างจุดวัด
                           methodResult.success(null); // ตอบกลับ Flutter
                           break;
                        default:
                           methodResult.notImplemented(); // ไม่รองรับ method อื่น
                           break;
                    }
                });
            });
        }
    }
}