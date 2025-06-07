package com.example.project_furnitureapp;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import com.google.ar.core.*;
import com.google.ar.core.exceptions.*;
import com.google.ar.core.ArCoreApk.InstallStatus;
import com.example.project_furnitureapp.samplerender.SampleRender;
import com.example.project_furnitureapp.samplerender.arcore.PlaneRenderer;
import com.example.project_furnitureapp.samplerender.PointRenderer;
import com.example.project_furnitureapp.samplerender.GLError;
import com.example.project_furnitureapp.samplerender.arcore.BackgroundRenderer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.platform.PlatformView;

public class ArMeasureView implements PlatformView, DefaultLifecycleObserver, GLSurfaceView.Renderer, SurfaceHolder.Callback {

    private static final String TAG = "ArMeasureView";
    private static final String LIFECYCLE_TAG = "AR_LIFECYCLE_DEBUG";
    private static final String CHANNEL_NAME = "ar_measurement_channel";

    private final Context context;
    private final Activity activity;
    private final Lifecycle lifecycle;
    private final BinaryMessenger messenger;
    private final int viewId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private GLSurfaceView glSurfaceView;
    private MethodChannel methodChannel;
    private DisplayRotationHelper displayRotationHelper;
    private TapHelper tapHelper;
    private Session arSession;
    private boolean installGlobalRequested = false;

    private SampleRender sampleRender;
    private BackgroundRenderer backgroundRenderer;
    private PlaneRenderer planeRenderer;
    private PointRenderer pointRenderer;

    private final Handler surfaceSearchHandler = new Handler(Looper.getMainLooper());
    private final Runnable surfaceSearchRunnable = () -> showToast("Searching for surfaces...");
    private boolean arCoreIsTracking = false;

    private final List<Anchor> measurementAnchors = Collections.synchronizedList(new ArrayList<Anchor>());
    private final Object anchorLock = new Object();

    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];

    private volatile boolean arViewIsFrozen = false;
    private Frame lastTrackedFrame = null;
    private final float[] frozenViewMatrix = new float[16];
    private final float[] frozenProjectionMatrix = new float[16];

    public ArMeasureView(Context context, Activity activity, Lifecycle lifecycle, BinaryMessenger messenger, int id, Map<String, Object> creationParams) {
        this.context = context; this.activity = activity; this.lifecycle = lifecycle; this.messenger = messenger; this.viewId = id;
        try { this.methodChannel = new MethodChannel(messenger, CHANNEL_NAME); setupMethodCallHandler(); } catch (Throwable t) { Log.e(TAG, "Error MC init", t); }
        try { this.displayRotationHelper = new DisplayRotationHelper(context); } catch (Throwable t) { Log.e(TAG, "Error DRH init", t); }
        try { this.tapHelper = new TapHelper(context); } catch (Throwable t) { Log.e(TAG, "Error TH init", t); }
        try {
            this.glSurfaceView = createSurfaceView();
            if (this.glSurfaceView != null && this.glSurfaceView.getHolder() != null) {
                this.glSurfaceView.getHolder().addCallback(this);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error creating GLSurfaceView", t); this.glSurfaceView = null;
        }
        if (this.lifecycle != null) this.lifecycle.addObserver(this); else Log.w(TAG, "Lifecycle NULL");
        setupTouchListener();
    }

    private GLSurfaceView createSurfaceView() {
        GLSurfaceView surfaceView = new GLSurfaceView(context);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(3);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);
        return surfaceView;
    }

    private void setupTouchListener() {
        if (glSurfaceView != null && tapHelper != null) {
            glSurfaceView.setOnTouchListener(tapHelper);
        } else {
            Log.e(TAG, "Cannot set touch listener - glSurfaceView or tapHelper is null");
        }
    }

    @Override public View getView() { return glSurfaceView != null ? glSurfaceView : new View(context); }

    @Override public void surfaceCreated(@NonNull SurfaceHolder holder) { Log.d(TAG, "GLSurfaceView Surface CREATED for viewId: " + viewId); }
    @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) { Log.d(TAG, "GLSurfaceView Surface CHANGED: " + width + "x" + height + " for viewId: " + viewId); }
    @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) { Log.d(TAG, "GLSurfaceView Surface DESTROYED for viewId: " + viewId); }

    @Override
    public void dispose() {
        Log.d(LIFECYCLE_TAG, "ArMeasureView dispose() called for viewId: " + viewId);
        if (this.lifecycle != null) this.lifecycle.removeObserver(this);
        if (glSurfaceView != null) {
            glSurfaceView.queueEvent(this::closeSessionAndRenderers);
        } else {
            closeSessionAndRenderers();
        }
        if (methodChannel != null) methodChannel.setMethodCallHandler(null);
        surfaceSearchHandler.removeCallbacks(surfaceSearchRunnable);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        Log.d(LIFECYCLE_TAG, "ArMeasureView onResume() for viewId: " + viewId);
        arViewIsFrozen = false; arCoreIsTracking = false;
        if (!CameraPermissionHelper.hasCameraPermission(activity)) { CameraPermissionHelper.requestCameraPermission(activity); return; }
        if (arSession == null) {
            try {
                InstallStatus installStatus = ArCoreApk.getInstance().requestInstall(activity, !installGlobalRequested);
                if (installStatus == InstallStatus.INSTALL_REQUESTED) { installGlobalRequested = true; return; }
                if (installStatus != InstallStatus.INSTALLED) { showToast("ARCore not installed. Status: " + installStatus); return; }
                arSession = new Session(context); Config config = new Config(arSession);
                config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                arSession.configure(config);
            } catch (Exception e) { Log.e(TAG, "Session creation/config failed", e); showToast("Failed AR session"); arSession = null; return; }
        }
        try { arSession.resume(); } catch (Exception e) { Log.e(TAG, "Failed to resume AR session", e); showToast("Failed to resume AR"); return; }
        if (glSurfaceView != null) { glSurfaceView.onResume(); glSurfaceView.requestRender(); }
        if (displayRotationHelper != null) displayRotationHelper.onResume();
        surfaceSearchHandler.removeCallbacks(surfaceSearchRunnable);
        surfaceSearchHandler.postDelayed(surfaceSearchRunnable, 1000);
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        Log.d(LIFECYCLE_TAG, "ArMeasureView onPause() for viewId: " + viewId);
        if (displayRotationHelper != null) displayRotationHelper.onPause();
        if (glSurfaceView != null) glSurfaceView.onPause();
        if (arSession != null) arSession.pause();
        surfaceSearchHandler.removeCallbacks(surfaceSearchRunnable);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(LIFECYCLE_TAG, "ARView GLSurfaceView.Renderer.onSurfaceCreated for viewId: " + viewId);
        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST); GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        try {
            AssetManager assetManager = context.getAssets();
            SampleRender.Renderer dummyRenderer = new SampleRender.Renderer() {
                @Override public void onSurfaceCreated(SampleRender render) {}
                @Override public void onSurfaceChanged(SampleRender render, int width, int height) {}
                @Override public void onDrawFrame(SampleRender render) {}
            };
            sampleRender = new SampleRender(this.glSurfaceView, dummyRenderer, assetManager);
            backgroundRenderer = new BackgroundRenderer(sampleRender);
            planeRenderer = new PlaneRenderer(sampleRender);
            pointRenderer = new PointRenderer(sampleRender);
            pointRenderer.setColor(new float[]{1.0f, 0.0f, 1.0f, 1.0f});
            pointRenderer.setPointSize(25.0f);
        } catch (Throwable t) { Log.e(TAG, "EXCEPTION in onSurfaceCreated", t); closeSessionAndRenderers(); }
    }

    private void closeSessionAndRenderers() {
        Log.d(TAG, "Closing session and nullifying ARCore renderers for viewId: " + viewId);
        if (arSession != null) { arSession.close(); arSession = null; }
        if (planeRenderer != null) { planeRenderer.close(); planeRenderer = null; }
        if (backgroundRenderer != null) { backgroundRenderer.close(); backgroundRenderer = null; }
        if (pointRenderer != null) { pointRenderer.close(); pointRenderer = null; }
        sampleRender = null;
        lastTrackedFrame = null;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(LIFECYCLE_TAG, "ARView GLSurfaceView.Renderer.onSurfaceChanged: " + width + "x" + height + " for viewId: " + viewId);
        GLES30.glViewport(0, 0, width, height);
        if (displayRotationHelper != null) displayRotationHelper.onSurfaceChanged(width, height);
        if (sampleRender != null) sampleRender.setViewport(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        if (arViewIsFrozen) {
            if (lastTrackedFrame == null || backgroundRenderer == null || pointRenderer == null) {
                 Log.w(TAG, "onDrawFrame (Frozen): Missing components for rendering frozen scene.");
                 return;
            }
            backgroundRenderer.draw(lastTrackedFrame);
            GLES30.glDepthMask(true); GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            synchronized (anchorLock) {
                if (!measurementAnchors.isEmpty()) {
                    pointRenderer.drawPoints(measurementAnchors, frozenViewMatrix, frozenProjectionMatrix);
                }
            }
        } else { // Live AR State
            if (arSession == null || backgroundRenderer == null || planeRenderer == null || pointRenderer == null || displayRotationHelper == null || sampleRender == null) {
                Log.w(TAG, "onDrawFrame (Live): Missing essential AR components.");
                return;
            }
            try {
                displayRotationHelper.updateSessionIfNeeded(arSession);
                int textureId = backgroundRenderer.getTextureId();
                if (textureId <= 0) { return; }
                arSession.setCameraTextureName(textureId);

                Frame frame = arSession.update();
                if (frame == null) { return; }
                Camera camera = frame.getCamera();
                if (camera == null) { return; }

                backgroundRenderer.draw(frame);

                TrackingState cameraTrackingState = camera.getTrackingState();
                if (cameraTrackingState == TrackingState.TRACKING) {
                    this.lastTrackedFrame = frame;
                    camera.getViewMatrix(viewMatrix, 0);
                    camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

                    if (!arCoreIsTracking) {
                        arCoreIsTracking = true;
                        surfaceSearchHandler.removeCallbacks(surfaceSearchRunnable);
                        showToast("Surface detected. Ready.");
                        Log.i(TAG, "ARCore Tracking State: TRACKING. Surface detected.");
                    }
                    planeRenderer.drawPlanes(arSession.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projectionMatrix);
                    synchronized (anchorLock) {
                        if (!measurementAnchors.isEmpty()) {
                            pointRenderer.drawPoints(measurementAnchors, viewMatrix, projectionMatrix);
                        }
                    }
                    if (tapHelper != null) {
                        MotionEvent tap = tapHelper.poll();
                        if (tap != null) handleTapForMeasurement(frame, camera, tap);
                    }
                } else {
                    if (arCoreIsTracking) {
                        arCoreIsTracking = false;
                        showToast("Tracking lost. Move device slowly.");
                        Log.w(TAG, "ARCore Tracking State: Lost tracking. Now: " + cameraTrackingState);
                    }
                    if (!surfaceSearchHandler.hasCallbacks(surfaceSearchRunnable)) {
                         surfaceSearchHandler.postDelayed(surfaceSearchRunnable, 500);
                    }
                }
            } catch (Throwable t) { Log.e(TAG, "Exception in onDrawFrame (Live AR)", t); }
        }
    }

    private void handleTapForMeasurement(Frame frame, Camera camera, MotionEvent tap) {
        if (arViewIsFrozen) return;
        if (frame == null || camera == null || tap == null || camera.getTrackingState() != TrackingState.TRACKING) {
             if (camera != null && camera.getTrackingState() != TrackingState.TRACKING) {
                showToast("Cannot place point: Scene not stable.");
            }
            return;
        }
        Log.d(TAG, "Native: Handling tap for measurement at X=" + tap.getX() + ", Y=" + tap.getY());
        List<HitResult> hitResultList = frame.hitTest(tap);
        Anchor newAnchor = null;
        for (HitResult hit : hitResultList) {
            Trackable trackable = hit.getTrackable(); Pose hitPose = hit.getHitPose();
            if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hitPose) && ((Plane) trackable).getSubsumedBy() == null) {
                try { newAnchor = hit.createAnchor(); break; }
                catch (Exception e) { Log.e(TAG, "Error creating measurement anchor", e); }
            }
        }
        if (newAnchor != null) {
            synchronized (anchorLock) {
                if (measurementAnchors.size() >= 4) {
                    List<Anchor> toDetach = new ArrayList<>(measurementAnchors);
                    measurementAnchors.clear();
                    for(Anchor a : toDetach) a.detach();
                }
                measurementAnchors.add(newAnchor);
            }
            showToast("Placed point " + measurementAnchors.size());
            if (measurementAnchors.size() == 2 || measurementAnchors.size() == 4) {
                calculateAndSendDistancesToFlutter(camera);
            }
        } else { Log.w(TAG, "Native: Tap did not hit a valid plane for measurement."); }
    }

    private void calculateAndSendDistancesToFlutter(Camera camera) {
        List<Map<String, Object>> measurementsData = new ArrayList<>();
        float[] currentViewMatrixToUse = arViewIsFrozen ? frozenViewMatrix : viewMatrix;
        float[] currentProjMatrixToUse = arViewIsFrozen ? frozenProjectionMatrix : projectionMatrix;
        synchronized (anchorLock) {
            if (measurementAnchors.size() >= 2) {
                measurementsData.add(getMeasurementDataForPair(measurementAnchors.get(0), measurementAnchors.get(1), camera, "Distance 1", currentViewMatrixToUse, currentProjMatrixToUse));
            }
            if (measurementAnchors.size() == 4) {
                measurementsData.add(getMeasurementDataForPair(measurementAnchors.get(2), measurementAnchors.get(3), camera, "Distance 2", currentViewMatrixToUse, currentProjMatrixToUse));
            }
        }
        if (!measurementsData.isEmpty() && methodChannel != null) {
            mainHandler.post(() -> methodChannel.invokeMethod("measurementSetResult", measurementsData));
        }
    }

    private Map<String, Object> getMeasurementDataForPair(Anchor anchor1, Anchor anchor2, Camera cameraForInfo, String label, float[] mvMatrix, float[] projMatrix) {
        Pose pose1 = anchor1.getPose(); Pose pose2 = anchor2.getPose();
        float dx = pose1.tx() - pose2.tx(); float dy = pose1.ty() - pose2.ty(); float dz = pose1.tz() - pose2.tz();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float midX = (pose1.tx() + pose2.tx()) / 2.0f; float midY = (pose1.ty() + pose2.ty()) / 2.0f; float midZ = (pose1.tz() + pose2.tz()) / 2.0f;
        float[] midPoint3D = {midX, midY, midZ, 1.0f}; float[] midPoint2D = new float[2];
        Camera cameraToUse = (this.lastTrackedFrame != null && this.lastTrackedFrame.getCamera() != null && arViewIsFrozen) ? this.lastTrackedFrame.getCamera() : cameraForInfo;
        if (glSurfaceView != null && cameraToUse != null && (arViewIsFrozen || cameraToUse.getTrackingState() == TrackingState.TRACKING)) {
            int currentScreenWidth = glSurfaceView.getWidth(); int currentScreenHeight = glSurfaceView.getHeight();
            projectWorldToScreen(midPoint3D, mvMatrix, projMatrix, currentScreenWidth, currentScreenHeight, midPoint2D);
        } else { midPoint2D[0] = -1; midPoint2D[1] = -1; }
        Map<String, Object> data = new HashMap<>();
        data.put("label", label); data.put("distance", distance);
        data.put("midPointScreenX", (double) midPoint2D[0]); data.put("midPointScreenY", (double) midPoint2D[1]);
        return data;
    }

    private void projectWorldToScreen(float[] worldPoint4D, float[] viewMatrixIn, float[] projectionMatrixIn, int screenWidth, int screenHeight, float[] outScreenPoint2D) {
        if (worldPoint4D == null || worldPoint4D.length < 4 || viewMatrixIn == null || projectionMatrixIn == null || outScreenPoint2D == null || outScreenPoint2D.length < 2) {
            if (outScreenPoint2D != null && outScreenPoint2D.length >=2) { outScreenPoint2D[0] = -1; outScreenPoint2D[1] = -1; } return;
        }
        if (screenWidth <= 0 || screenHeight <= 0) { outScreenPoint2D[0] = -1; outScreenPoint2D[1] = -1; return; }
        float[] viewSpacePoint = new float[4]; Matrix.multiplyMV(viewSpacePoint, 0, viewMatrixIn, 0, worldPoint4D, 0);
        float[] clipSpacePoint = new float[4]; Matrix.multiplyMV(clipSpacePoint, 0, projectionMatrixIn, 0, viewSpacePoint, 0);
        if (clipSpacePoint[3] <= 0.0f) { outScreenPoint2D[0] = -1; outScreenPoint2D[1] = -1; return; }
        float ndcX = clipSpacePoint[0] / clipSpacePoint[3]; float ndcY = clipSpacePoint[1] / clipSpacePoint[3];
        outScreenPoint2D[0] = (ndcX + 1.0f) / 2.0f * screenWidth; outScreenPoint2D[1] = (1.0f - ndcY) / 2.0f * screenHeight;
    }

    private void freezeArViewNative() {
        if (arSession == null) { Log.w(TAG, "Cannot freeze: Session null."); return; }
        if (arViewIsFrozen) { Log.i(TAG, "Already frozen."); return; }

        if (this.lastTrackedFrame == null || this.lastTrackedFrame.getCamera() == null ||
            this.lastTrackedFrame.getCamera().getTrackingState() != TrackingState.TRACKING) {
            Log.e(TAG, "Cannot freeze: Last available frame was not from a stable tracking state. Current ARCore Tracking: " + arCoreIsTracking);
            mainHandler.post(() -> showToast("Cannot freeze: Scene not stable. Move device and try again."));
            return;
        }
        
        Log.i(TAG, "Freezing AR view using last tracked frame.");
        Camera lastValidCamera = this.lastTrackedFrame.getCamera();
        lastValidCamera.getViewMatrix(this.frozenViewMatrix, 0);
        lastValidCamera.getProjectionMatrix(this.frozenProjectionMatrix, 0, 0.1f, 100.0f);
        
        this.arViewIsFrozen = true;
        Log.i(TAG, "AR view is now frozen. Ready for model placement.");
    }

    private void handleClearPoints() {
        clearAnchorsInternal();
        mainHandler.post(() -> showToast("Measurements cleared"));
        this.arViewIsFrozen = false; this.arCoreIsTracking = false;

        surfaceSearchHandler.removeCallbacks(surfaceSearchRunnable);
        surfaceSearchHandler.postDelayed(surfaceSearchRunnable, 1000);
        if (methodChannel != null) mainHandler.post(() -> methodChannel.invokeMethod("pointsCleared", null));
    }



    private void clearAnchorsInternal() {
        synchronized(anchorLock) {
            if (!measurementAnchors.isEmpty()) {
                for (Anchor a : measurementAnchors) a.detach();
                measurementAnchors.clear();
            }
        }
        Log.i(TAG, "Internal measurement anchors cleared.");
    }

    private void setupMethodCallHandler() {
        if (methodChannel != null) {
            methodChannel.setMethodCallHandler((call, methodResult) -> {
                mainHandler.post(() -> {
                    Log.d(TAG, "Method call received: " + call.method + ", Args: " + call.arguments);
                    switch (call.method) {
                        case "clearPoints":
                           handleClearPoints(); methodResult.success(null); break;
                        case "freezeArView":
                           freezeArViewNative(); methodResult.success(null); break;
                        default:
                           methodResult.notImplemented(); break;
                    }
                });
            });
        }
    }
    private void showToast(final String message) {
        mainHandler.post(() -> {
            if (context != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }
    private void closeSession() {
        if (arSession != null) { arSession.close(); arSession = null; }
    }
}