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
import java.util.Arrays;
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

public class ArMeasureView implements PlatformView, DefaultLifecycleObserver, GLSurfaceView.Renderer {

    private static final String TAG = "ArMeasureView";
    private static final String LIFECYCLE_TAG = "AR_LIFECYCLE_DEBUG";
    private static final String POINT_DEBUG_TAG = "POINT_DEBUG";
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
    private boolean installRequested = false;
    private SampleRender sampleRender;
    private BackgroundRenderer backgroundRenderer;
    private PlaneRenderer planeRenderer;
    private PointRenderer pointRenderer;

    private final Handler loadingMessagehandler = new Handler(Looper.getMainLooper());
    private final Runnable loadingMessageRunnable = () -> showToast("Searching for surfaces...");
    private boolean surfaceDetected = false;
    private final List<Anchor> anchors = Collections.synchronizedList(new ArrayList<Anchor>());
    private final Object anchorLock = new Object();

    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];

    // สถานะการ Freeze และข้อมูลที่เกี่ยวข้อง
    private volatile boolean arViewIsFrozen = false;
    private Frame lastCapturedFrame = null; // Frame ล่าสุดก่อนที่จะ Freeze
    private final float[] frozenViewMatrix = new float[16];
    private final float[] frozenProjectionMatrix = new float[16];
    // อาจจะต้องเก็บ List<Plane> ที่ snapshot ไว้ ณ ตอน freeze ด้วยถ้าจะ render plane ตอน freeze
    // private List<Plane> frozenPlanesSnapshot = new ArrayList<>();


    public ArMeasureView(Context context, Activity activity, Lifecycle lifecycle, BinaryMessenger messenger, int id, Map<String, Object> creationParams) {
        Log.d(LIFECYCLE_TAG, "====== Constructor START (ID: " + id + ") ======");
        this.context = context; this.activity = activity; this.lifecycle = lifecycle; this.messenger = messenger; this.viewId = id;
        Log.d(TAG, "Initializing ArMeasureView fields (ID: " + viewId + ")");
        try { this.methodChannel = new MethodChannel(messenger, CHANNEL_NAME); setupMethodCallHandler(); Log.d(TAG,"MC Init");} catch (Throwable t) { Log.e(TAG, "Error MC init", t); }
        try { this.displayRotationHelper = new DisplayRotationHelper(context); Log.d(TAG,"DRH Init");} catch (Throwable t) { Log.e(TAG, "Error DRH init", t); }
        try { this.tapHelper = new TapHelper(context); Log.d(TAG,"TH Init");} catch (Throwable t) { Log.e(TAG, "Error TH init", t); }
        try { this.glSurfaceView = createSurfaceView(); } catch (Throwable t) { Log.e(TAG, "Error creating GLSurfaceView", t); this.glSurfaceView = null; }
        try { if (this.lifecycle != null) { this.lifecycle.addObserver(this); } else { Log.w(TAG, "Lifecycle NULL"); } } catch (Throwable t) { Log.e(TAG, "Error adding Observer", t); }
        setupTouchListener();
        Log.d(LIFECYCLE_TAG, "====== Constructor FINISHED (ID: " + this.viewId + ") ======");
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
        if (glSurfaceView != null && tapHelper != null) { glSurfaceView.setOnTouchListener(tapHelper); Log.d(TAG, "Touch listener set."); }
        else { Log.e(TAG, "Cannot set touch listener - null view or helper"); }
    }

    @Override public View getView() { return glSurfaceView != null ? glSurfaceView : new View(context); }

    @Override
    public void dispose() {
        Log.d(TAG, "Disposing " + TAG + " (ID: " + viewId + ")");
        if (this.lifecycle != null) { this.lifecycle.removeObserver(this); }
        if (glSurfaceView != null) {
            glSurfaceView.queueEvent(() -> {
                 Log.d(TAG, "Releasing GL thread resources...");
                 if (arSession != null) { arSession.close(); arSession = null; }
                 if (planeRenderer != null) { planeRenderer.close(); planeRenderer = null; }
                 if (backgroundRenderer != null) { backgroundRenderer.close(); backgroundRenderer = null; }
                 if (pointRenderer != null) { pointRenderer.close(); pointRenderer = null; }
                 lastCapturedFrame = null; // Clear captured frame
            });
        } else if (arSession != null) {
            arSession.close(); arSession = null;
            lastCapturedFrame = null;
        }
        if (methodChannel != null) { methodChannel.setMethodCallHandler(null); }
        loadingMessagehandler.removeCallbacks(loadingMessageRunnable);
    }

    @Override public void onResume(@NonNull LifecycleOwner owner) {
        Log.d(LIFECYCLE_TAG, "====== onResume START ======");
        if (!CameraPermissionHelper.hasCameraPermission(activity)) { CameraPermissionHelper.requestCameraPermission(activity); Log.d(LIFECYCLE_TAG,"onResume FINISHED (Perm Req)"); return; }
        if (arSession == null) {
             try {
                 InstallStatus status = ArCoreApk.getInstance().requestInstall(activity, !installRequested);
                 if (status == InstallStatus.INSTALL_REQUESTED) { installRequested = true; Log.i(TAG,"Install requested"); Log.d(LIFECYCLE_TAG,"onResume FINISHED (Install Req)"); return; }
                 if (status != InstallStatus.INSTALLED) { Log.e(TAG, "ARCore status: " + status); showToast("Error: ARCore not installed."); Log.d(LIFECYCLE_TAG,"onResume FINISHED (Install Failed)"); return; }
                 arSession = new Session(context); Config cfg = new Config(arSession);
                 cfg.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
                 cfg.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                 arSession.configure(cfg); Log.d(TAG, "AR Session configured.");
             } catch (Exception e) { Log.e(TAG, "Session creation failed", e); showToast("Failed AR session"); arSession = null; Log.d(LIFECYCLE_TAG,"onResume FINISHED (Sess Create Ex)"); return; }
        }
        try {
            arViewIsFrozen = false; // Ensure not frozen on resume
            if (arSession != null) arSession.resume(); else {Log.e(TAG,"Cannot resume null session"); Log.d(LIFECYCLE_TAG,"onResume FINISHED (Sess Null)"); return;} Log.d(TAG, "AR Session resumed.");
        } catch (Exception e) { Log.e(TAG, "Session resume failed", e); showToast("Failed resume session"); Log.d(LIFECYCLE_TAG,"onResume FINISHED (Sess Resume Ex)"); return; }

        if (glSurfaceView != null) { glSurfaceView.onResume(); glSurfaceView.requestRender(); }
        if (displayRotationHelper != null) { displayRotationHelper.onResume(); }
        surfaceDetected = false; loadingMessagehandler.removeCallbacks(loadingMessageRunnable); loadingMessagehandler.postDelayed(loadingMessageRunnable, TimeUnit.SECONDS.toMillis(5));
        Log.d(LIFECYCLE_TAG, "====== onResume FINISHED ======");
    }

    @Override public void onPause(@NonNull LifecycleOwner owner) {
        Log.d(LIFECYCLE_TAG, "====== onPause CALLED ======");
        if (displayRotationHelper != null) { displayRotationHelper.onPause(); }
        if (glSurfaceView != null) { glSurfaceView.onPause(); }
        if (arSession != null && !arViewIsFrozen) { // Only pause session if not in frozen state (or handle differently if needed)
            arSession.pause();
            Log.d(TAG, "AR Session paused normally.");
        } else if (arSession != null && arViewIsFrozen) {
            Log.d(TAG, "AR Session not paused due to frozen state. Consider implications.");
            // If app goes to background in frozen state, ARCore session might still be active.
            // Depending on requirements, you might want to force pause here, or handle resume carefully.
            // For now, let it be. If unfreezing is implemented, it should resume the session.
        }
        loadingMessagehandler.removeCallbacks(loadingMessageRunnable);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(LIFECYCLE_TAG, "====== onSurfaceCreated CALLED ======");
        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f); GLES30.glEnable(GLES30.GL_DEPTH_TEST); GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        try {
            AssetManager assetManager = context.getAssets(); if (assetManager == null) throw new IOException("AssetManager null");
            if (this.glSurfaceView == null) throw new IllegalStateException("GLSurfaceView null");
            SampleRender.Renderer dummyRenderer = new SampleRender.Renderer() {
                @Override public void onSurfaceCreated(SampleRender r) {}
                @Override public void onSurfaceChanged(SampleRender r, int w, int h) {}
                @Override public void onDrawFrame(SampleRender r) {}
            };
            sampleRender = new SampleRender(this.glSurfaceView, dummyRenderer, assetManager); if (sampleRender == null) throw new RuntimeException("SampleRender null");
            backgroundRenderer = new BackgroundRenderer(sampleRender); if (backgroundRenderer == null) throw new RuntimeException("BackgroundRenderer null");
            planeRenderer = new PlaneRenderer(sampleRender); if (planeRenderer == null) throw new RuntimeException("PlaneRenderer null");
            pointRenderer = new PointRenderer(sampleRender); if (pointRenderer == null) throw new RuntimeException("PointRenderer null");
            pointRenderer.setColor(new float[]{1.0f, 0.0f, 1.0f, 1.0f});
            pointRenderer.setPointSize(25.0f);
        } catch (Throwable t) { Log.e(TAG, "!!! EXCEPTION in onSurfaceCreated !!!", t); showToast("Error initializing renderers"); sampleRender = null; backgroundRenderer = null; planeRenderer = null; pointRenderer = null; closeSession(); }
        Log.d(LIFECYCLE_TAG, "====== onSurfaceCreated FINISHED ======");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(LIFECYCLE_TAG, "====== onSurfaceChanged CALLED ("+width+"x"+height+") ======");
        GLES30.glViewport(0, 0, width, height);
        if (displayRotationHelper != null) { displayRotationHelper.onSurfaceChanged(width, height); }
        if (sampleRender != null) { sampleRender.setViewport(width, height); }
        if (glSurfaceView != null) { glSurfaceView.requestRender(); }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        if (arViewIsFrozen) {
            // --- FROZEN STATE ---
            if (lastCapturedFrame == null || backgroundRenderer == null || pointRenderer == null || sampleRender == null) {
                return;
            }
            backgroundRenderer.draw(lastCapturedFrame); // Draw the frozen background

            GLES30.glDepthMask(true);
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);

            // Draw existing points using the frozen matrices
            synchronized (anchorLock) {
                if (!anchors.isEmpty()) {
                    pointRenderer.drawPoints(anchors, frozenViewMatrix, frozenProjectionMatrix);
                }
            }

            // TODO: Handle taps for model placement (using frozenViewMatrix, frozenProjectionMatrix)
            if (tapHelper != null) {
                MotionEvent tap = tapHelper.poll();
                if (tap != null) {
                    Log.d(TAG, "Tap occurred in frozen mode. (Model placement TBD)");
                    // handleTapForModelPlacement(tap, frozenViewMatrix, frozenProjectionMatrix, glSurfaceView.getWidth(), glSurfaceView.getHeight());
                }
            }

        } else {
            // --- LIVE AR STATE ---
            if (arSession == null || backgroundRenderer == null || planeRenderer == null || pointRenderer == null || displayRotationHelper == null || sampleRender == null) {
                return;
            }
            try {
                displayRotationHelper.updateSessionIfNeeded(arSession);
                int textureId = backgroundRenderer.getTextureId();
                if (textureId <= 0) { Log.w(TAG, "Invalid camera texture ID: " + textureId); return; }
                arSession.setCameraTextureName(textureId);

                Frame frame = arSession.update(); // Update session and get current frame
                if (frame == null) { return; }
                
                this.lastCapturedFrame = frame; // Capture the frame

                Camera camera = frame.getCamera();
                if (camera == null) { return; }

                backgroundRenderer.draw(frame);

                GLES30.glDepthMask(true);
                GLES30.glEnable(GLES30.GL_DEPTH_TEST);
                TrackingState cameraTrackingState = camera.getTrackingState();

                if (cameraTrackingState == TrackingState.TRACKING) {
                    camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);
                    camera.getViewMatrix(viewMatrix, 0);

                    // Store current matrices in case we freeze in this frame
                    System.arraycopy(viewMatrix, 0, frozenViewMatrix, 0, 16);
                    System.arraycopy(projectionMatrix, 0, frozenProjectionMatrix, 0, 16);

                    planeRenderer.drawPlanes(arSession.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projectionMatrix);

                    synchronized (anchorLock) {
                        if (!anchors.isEmpty()) {
                            pointRenderer.drawPoints(anchors, viewMatrix, projectionMatrix);
                        }
                    }

                    if (tapHelper != null) {
                        MotionEvent tap = tapHelper.poll();
                        if (tap != null) {
                            handleTap(frame, camera, tap);
                        }
                    }
                } else {
                     Log.d(TAG, "Camera not tracking. Skipping plane/point rendering.");
                }
                handleLoadingMessage(frame);
            } catch (Throwable t) {
                Log.e(TAG, "Exception on the OpenGL thread in onDrawFrame (Live AR)", t);
                GLError.maybeLogGLError(Log.ERROR, TAG, "GL Error during Live AR onDrawFrame", t.getMessage());
            }
        }
    }

    private void handleLoadingMessage(Frame frame) {
        if (arViewIsFrozen || frame == null) return; // Don't show loading message if frozen
        if (!surfaceDetected) {
            for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                if (plane.getTrackingState() == TrackingState.TRACKING && plane.getSubsumedBy() == null) {
                    surfaceDetected = true; loadingMessagehandler.removeCallbacks(loadingMessageRunnable);
                    showToast("Surface detected. Tap to place points."); Log.i(TAG, "Surface detected."); break;
                }
            }
        }
    }

    private void handleTap(Frame frame, Camera camera, MotionEvent tap) {
        if (arViewIsFrozen) { // Do not place new measurement points if frozen
            Log.d(TAG, "Tap ignored for measurement points: AR view is frozen. (Tap will be for model placement)");
            // Here you would call handleTapForModelPlacement(...)
            return;
        }

        if (frame == null || camera == null || tap == null || camera.getTrackingState() != TrackingState.TRACKING) return;
        Log.d(TAG, "Handling tap event for measurement."); List<HitResult> hitResultList = frame.hitTest(tap); Anchor newAnchor = null;
        for (HitResult hit : hitResultList) {
            Trackable trackable = hit.getTrackable(); Pose hitPose = hit.getHitPose();
            if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hitPose) && ((Plane) trackable).getSubsumedBy() == null) {
                try { newAnchor = hit.createAnchor(); Log.i(TAG, "Anchor created at: " + newAnchor.getPose()); break; }
                catch (Exception e) { Log.e(TAG, "Error creating anchor", e); }
            }
        }
        if (newAnchor != null) {
            synchronized (anchorLock) {
                if (anchors.size() >= 4) {
                     Log.d(TAG, "Max 4 anchors reached. Clearing all to start new measurements.");
                     List<Anchor> anchorsToDetach = new ArrayList<>(anchors);
                     anchors.clear();
                     for(Anchor oldAnchor : anchorsToDetach) oldAnchor.detach();
                }
                anchors.add(newAnchor);
            }
            showToast("Placed point " + anchors.size());
            Log.i(TAG, "Anchor added. Total anchors: " + anchors.size());
            if (anchors.size() == 2 || anchors.size() == 4) {
                calculateAndSendDistancesToFlutter(camera);
            }
        } else { Log.w(TAG, "Tap did not hit a valid plane for measurement."); }
    }

    private void calculateAndSendDistancesToFlutter(Camera camera) {
        List<Map<String, Object>> measurementsData = new ArrayList<>();
        float[] currentViewMatrixToUse = arViewIsFrozen ? frozenViewMatrix : viewMatrix;
        float[] currentProjMatrixToUse = arViewIsFrozen ? frozenProjectionMatrix : projectionMatrix;

        synchronized (anchorLock) {
            if (anchors.size() >= 2) {
                measurementsData.add(getMeasurementDataForPair(anchors.get(0), anchors.get(1), camera, "Distance 1", currentViewMatrixToUse, currentProjMatrixToUse));
            }
            if (anchors.size() == 4) {
                measurementsData.add(getMeasurementDataForPair(anchors.get(2), anchors.get(3), camera, "Distance 2", currentViewMatrixToUse, currentProjMatrixToUse));
            }
        }
        if (!measurementsData.isEmpty() && methodChannel != null) {
            Log.d(TAG, "Sending " + measurementsData.size() + " measurements to Flutter: " + measurementsData.toString());
            mainHandler.post(() -> methodChannel.invokeMethod("measurementSetResult", measurementsData));
        } else if (measurementsData.isEmpty()) { Log.w(TAG, "No valid anchor pairs to measure at this time."); }
        else { Log.e(TAG,"MethodChannel null, cannot send measurementSetResult"); }
    }

    private Map<String, Object> getMeasurementDataForPair(Anchor anchor1, Anchor anchor2, Camera camera, String label, float[] mvMatrix, float[] projMatrix) {
        Pose pose1 = anchor1.getPose(); Pose pose2 = anchor2.getPose();
        float dx = pose1.tx() - pose2.tx(); float dy = pose1.ty() - pose2.ty(); float dz = pose1.tz() - pose2.tz();
        double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

        float midX = (pose1.tx() + pose2.tx()) / 2.0f;
        float midY = (pose1.ty() + pose2.ty()) / 2.0f;
        float midZ = (pose1.tz() + pose2.tz()) / 2.0f;
        float[] midPoint3D = {midX, midY, midZ, 1.0f};
        float[] midPoint2D = new float[2];

        Camera cameraToUse = (this.lastCapturedFrame != null && this.lastCapturedFrame.getCamera() != null && arViewIsFrozen) ? this.lastCapturedFrame.getCamera() : camera;

        if (glSurfaceView != null && cameraToUse != null && (arViewIsFrozen || cameraToUse.getTrackingState() == TrackingState.TRACKING)) {
             int currentScreenWidth = glSurfaceView.getWidth();
             int currentScreenHeight = glSurfaceView.getHeight();
             projectWorldToScreen(midPoint3D, mvMatrix, projMatrix, currentScreenWidth, currentScreenHeight, midPoint2D);
             Log.d(TAG, label + " MidPoint 3D: " + midPoint3D[0]+","+midPoint3D[1]+","+midPoint3D[2] +
                        " -> Screen 2D: " + Arrays.toString(midPoint2D) +
                        " (Screen: " + currentScreenWidth + "x" + currentScreenHeight + ")");
        } else {
             midPoint2D[0] = -1; midPoint2D[1] = -1;
             Log.w(TAG, "Cannot project to screen for "+label+": View="+glSurfaceView+", Cam="+cameraToUse+ (cameraToUse != null ? ", Tracking="+cameraToUse.getTrackingState() : ""));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("label", label); data.put("distance", distance);
        data.put("midPointScreenX", (double) midPoint2D[0]);
        data.put("midPointScreenY", (double) midPoint2D[1]);
        // Log.i(TAG, label + ": " + String.format("%.2f m", distance) + " (Mid 2D: X=" + midPoint2D[0] + ", Y=" + midPoint2D[1] + ")");
        // showToast(label + ": " + String.format("%.2f m", distance)); // อาจจะถี่ไป
        return data;
    }

    private void projectWorldToScreen(float[] worldPoint4D, float[] viewMatrixIn, float[] projectionMatrixIn, int screenWidth, int screenHeight, float[] outScreenPoint2D) {
        if (worldPoint4D == null || worldPoint4D.length < 4 || viewMatrixIn == null || projectionMatrixIn == null || outScreenPoint2D == null || outScreenPoint2D.length < 2) {
            Log.e(TAG, "projectWorldToScreen: Invalid input array parameters.");
            if (outScreenPoint2D != null && outScreenPoint2D.length >=2) { outScreenPoint2D[0] = -1; outScreenPoint2D[1] = -1; }
            return;
        }
        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.e(TAG, "projectWorldToScreen: Invalid screen dimensions (" + screenWidth + "x" + screenHeight + ").");
            outScreenPoint2D[0] = -1; outScreenPoint2D[1] = -1;
            return;
        }

        float[] viewSpacePoint = new float[4];
        Matrix.multiplyMV(viewSpacePoint, 0, viewMatrixIn, 0, worldPoint4D, 0);
        float[] clipSpacePoint = new float[4];
        Matrix.multiplyMV(clipSpacePoint, 0, projectionMatrixIn, 0, viewSpacePoint, 0);

        if (clipSpacePoint[3] <= 0.0f) {
            Log.w(TAG, "projectWorldToScreen: clipSpacePoint.w is " + clipSpacePoint[3] + ". Setting coords to (-1, -1).");
            outScreenPoint2D[0] = -1; outScreenPoint2D[1] = -1;
            return;
        }
        float ndcX = clipSpacePoint[0] / clipSpacePoint[3];
        float ndcY = clipSpacePoint[1] / clipSpacePoint[3];
        outScreenPoint2D[0] = (ndcX + 1.0f) / 2.0f * screenWidth;
        outScreenPoint2D[1] = (1.0f - ndcY) / 2.0f * screenHeight;
    }

    private void freezeArViewNative() {
        if (arSession == null) {
            Log.w(TAG, "Cannot freeze: session is null.");
            return;
        }
        if (arViewIsFrozen) {
            Log.i(TAG, "AR View is already frozen.");
            return;
        }

        Log.i(TAG, "Attempting to freeze AR view native components...");
        // Capture current matrices from the GL thread context if possible or use last known good
        // This copy should ideally be from values updated in onDrawFrame right before freezing
        System.arraycopy(this.viewMatrix, 0, this.frozenViewMatrix, 0, 16);
        System.arraycopy(this.projectionMatrix, 0, this.frozenProjectionMatrix, 0, 16);

        // lastCapturedFrame is already being updated in onDrawFrame, so it should be recent.
        if (this.lastCapturedFrame == null) {
            Log.e(TAG, "lastCapturedFrame is null during freeze. Background might not be correct.");
            // As a fallback, try to get a frame now (might not work if called from non-GL thread directly)
            // This is mostly a safety, relies on onDrawFrame's capture.
        }

        this.arViewIsFrozen = true;
        Log.i(TAG, "AR view is now flagged as frozen. onDrawFrame will use lastCapturedFrame and frozenMatrices.");
        // We do not call arSession.pause() here. We control updates via arViewIsFrozen in onDrawFrame.
    }


    private void handleClearPoints() {
        clearAnchorsInternal();
        showToast("Points cleared");
        Log.i(TAG, "Points cleared via call.");
        this.arViewIsFrozen = false; // Unfreeze when points are cleared
        this.surfaceDetected = false; // Reset surface detection
        loadingMessagehandler.removeCallbacks(loadingMessageRunnable);
        loadingMessagehandler.postDelayed(loadingMessageRunnable, TimeUnit.SECONDS.toMillis(1)); // Show searching again

        if (methodChannel != null) { mainHandler.post(() -> methodChannel.invokeMethod("pointsCleared", null)); }
        else { Log.e(TAG,"MethodChannel null, cannot send pointsCleared"); }
    }

    private void clearAnchorsInternal() {
        List<Anchor> anchorsToDetach;
        synchronized (anchorLock) { if (anchors.isEmpty()) return; anchorsToDetach = new ArrayList<>(anchors); anchors.clear(); }
        for (Anchor a : anchorsToDetach) { a.detach(); }
        Log.i(TAG, "Internal anchors detached.");
    }

    private void setupMethodCallHandler() {
         if (methodChannel != null) {
             methodChannel.setMethodCallHandler((call, result) -> {
                 Log.d(TAG, "Method call received: " + call.method + ", Args: " + call.arguments);
                 switch (call.method) {
                     case "clearPoints":
                        mainHandler.post(() -> { // Ensure UI-related logic runs on main thread if needed by showToast
                            handleClearPoints();
                            result.success(null);
                        });
                        break;
                     case "freezeArView":
                        // Freezing logic might involve GL resources or state, consider queueEvent if needed
                        // For now, setting a flag is generally safe.
                        freezeArViewNative();
                        result.success(null);
                        break;
                     default:
                        result.notImplemented();
                        break;
                 }
             }); Log.d(TAG, "Method Call Handler set.");
         } else { Log.e(TAG, "MethodChannel is null, cannot set MethodCallHandler."); }
    }
    private void showToast(final String message) { mainHandler.post(() -> { if (context != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); }); }
    private void closeSession() { if (arSession != null) { Log.w(TAG, "Closing AR session explicitly."); arSession.close(); arSession = null; } }
}