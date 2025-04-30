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
import com.example.project_furnitureapp.samplerender.LineRenderer;
import com.example.project_furnitureapp.samplerender.GLError;
import com.example.project_furnitureapp.samplerender.arcore.BackgroundRenderer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    private static final String LINE_DEBUG_TAG = "LINE_DEBUG";
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
    private LineRenderer lineRenderer;

    private final Handler loadingMessagehandler = new Handler(Looper.getMainLooper());
    private final Runnable loadingMessageRunnable = () -> showToast("Searching for surfaces...");
    private boolean surfaceDetected = false;

    private final List<Anchor> anchors = Collections.synchronizedList(new ArrayList<Anchor>());
    private final Object anchorLock = new Object();

    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];


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
                 if (lineRenderer != null) { lineRenderer.close(); lineRenderer = null; }
            });
        } else if (arSession != null) { arSession.close(); arSession = null; }
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
        try { if (arSession != null) arSession.resume(); else {Log.e(TAG,"Cannot resume null session"); Log.d(LIFECYCLE_TAG,"onResume FINISHED (Sess Null)"); return;} Log.d(TAG, "AR Session resumed."); }
        catch (Exception e) { Log.e(TAG, "Session resume failed", e); showToast("Failed resume session"); Log.d(LIFECYCLE_TAG,"onResume FINISHED (Sess Resume Ex)"); return; }
        if (glSurfaceView != null) { glSurfaceView.onResume(); glSurfaceView.requestRender(); }
        if (displayRotationHelper != null) { displayRotationHelper.onResume(); }
        surfaceDetected = false; loadingMessagehandler.removeCallbacks(loadingMessageRunnable); loadingMessagehandler.postDelayed(loadingMessageRunnable, TimeUnit.SECONDS.toMillis(5));
        Log.d(LIFECYCLE_TAG, "====== onResume FINISHED ======");
    }
    @Override public void onPause(@NonNull LifecycleOwner owner) {
        Log.d(LIFECYCLE_TAG, "====== onPause CALLED ======");
        if (displayRotationHelper != null) { displayRotationHelper.onPause(); }
        if (glSurfaceView != null) { glSurfaceView.onPause(); }
        if (arSession != null) { arSession.pause(); }
        loadingMessagehandler.removeCallbacks(loadingMessageRunnable);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(LIFECYCLE_TAG, "====== onSurfaceCreated CALLED ======");
        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f); GLES30.glEnable(GLES30.GL_DEPTH_TEST); GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        try {
            AssetManager assetManager = context.getAssets(); if (assetManager == null) throw new IOException("AssetManager null");
            if (this.glSurfaceView == null) throw new IllegalStateException("GLSurfaceView null");
            SampleRender.Renderer dummy = new SampleRender.Renderer() { @Override public void onSurfaceCreated(SampleRender r){} @Override public void onSurfaceChanged(SampleRender r, int w, int h){} @Override public void onDrawFrame(SampleRender r){} };
            sampleRender = new SampleRender(this.glSurfaceView, dummy, assetManager); if (sampleRender == null) throw new RuntimeException("SampleRender null"); Log.d(TAG, "SampleRender created.");
            backgroundRenderer = new BackgroundRenderer(sampleRender); if (backgroundRenderer == null) throw new RuntimeException("BackgroundRenderer null"); Log.d(TAG, "BackgroundRenderer created.");
            planeRenderer = new PlaneRenderer(sampleRender); if (planeRenderer == null) throw new RuntimeException("PlaneRenderer null"); Log.d(TAG, "PlaneRenderer created.");
            lineRenderer = new LineRenderer(sampleRender); if (lineRenderer == null) throw new RuntimeException("LineRenderer null"); Log.d(TAG, "LineRenderer created.");
        } catch (Throwable t) { Log.e(TAG, "!!! EXCEPTION in onSurfaceCreated !!!", t); showToast("Error initializing renderers"); sampleRender = null; backgroundRenderer = null; planeRenderer = null; lineRenderer = null; closeSession(); }
        Log.d(LIFECYCLE_TAG, "====== onSurfaceCreated FINISHED ======");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(LIFECYCLE_TAG, "====== onSurfaceChanged CALLED ("+width+"x"+height+") ======");
        GLES30.glViewport(0, 0, width, height);
        if (displayRotationHelper != null) { displayRotationHelper.onSurfaceChanged(width, height); }
        if (sampleRender != null) { sampleRender.setViewport(width, height); }
        if (glSurfaceView != null) { glSurfaceView.requestRender(); Log.d(TAG, "Requested render from onSurfaceChanged"); }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        if (arSession == null || backgroundRenderer == null || planeRenderer == null || lineRenderer == null || displayRotationHelper == null) {
            return;
        }

        try {
            displayRotationHelper.updateSessionIfNeeded(arSession);
            int textureId = backgroundRenderer.getTextureId();
            if (textureId <= 0) { Log.w(TAG, "Invalid camera texture ID: " + textureId); return; }
            arSession.setCameraTextureName(textureId);

            Frame frame = arSession.update();
            if (frame == null) return;
            Camera camera = frame.getCamera();
            if (camera == null) return;

            backgroundRenderer.draw(frame);

            GLES30.glDepthMask(true); GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            TrackingState cameraTrackingState = camera.getTrackingState();

            if (cameraTrackingState == TrackingState.TRACKING) {
                camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);
                camera.getViewMatrix(viewMatrix, 0);

                planeRenderer.drawPlanes(arSession.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projectionMatrix);

                Pose pose1 = null;
                Pose pose2 = null;
                boolean shouldDrawLine = false;

                synchronized (anchorLock) {
                    if (anchors.size() == 2) {
                        pose1 = anchors.get(0).getPose();
                        pose2 = anchors.get(1).getPose();
                        shouldDrawLine = true;
                    }
                }

                if (shouldDrawLine && pose1 != null && pose2 != null) {
                    Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
                    Log.d(LINE_DEBUG_TAG, "Have 2 anchors. Updating and drawing line...");
                    Log.d(LINE_DEBUG_TAG, "Anchor 1 Pose: " + pose1.toString());
                    Log.d(LINE_DEBUG_TAG, "Anchor 2 Pose: " + pose2.toString());
                    Log.d(LINE_DEBUG_TAG, "MVP Matrix: " + Arrays.toString(modelViewProjectionMatrix));
                    lineRenderer.updateLine(pose1, pose2);
                    Log.d(LINE_DEBUG_TAG, "Calling lineRenderer.draw()...");
                    lineRenderer.draw(modelViewProjectionMatrix);
                    GLError.maybeLogGLError(Log.ERROR, LINE_DEBUG_TAG, "After lineRenderer.draw()", "GL Error?");
                    Log.d(LINE_DEBUG_TAG, "Finished lineRenderer.draw().");
                } else {
                    if (lineRenderer != null) { lineRenderer.updateLine(null, null); }
                }

                if (tapHelper != null) { MotionEvent tap = tapHelper.poll(); if (tap != null) { handleTap(frame, camera, tap); } }
                else { Log.w(TAG, "tapHelper null"); }

            } else {
                 if (lineRenderer != null) { lineRenderer.updateLine(null, null); }
            }

            handleLoadingMessage(frame);

        } catch (Throwable t) {
            Log.e(TAG, "Exception on the OpenGL thread in onDrawFrame", t);
            GLError.maybeLogGLError(Log.ERROR, TAG, "GL Error during Exception in onDrawFrame", t.getMessage());
        }
    }

    private void handleLoadingMessage(Frame frame) {
        if (frame == null) return;
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
        if (frame == null || camera == null || tap == null || camera.getTrackingState() != TrackingState.TRACKING) return;
        Log.d(TAG, "Handling tap event."); List<HitResult> hitResultList = frame.hitTest(tap); Anchor newAnchor = null;
        for (HitResult hit : hitResultList) {
            Trackable trackable = hit.getTrackable(); Pose hitPose = hit.getHitPose();
            if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hitPose) && ((Plane) trackable).getSubsumedBy() == null) {
                try { newAnchor = hit.createAnchor(); Log.i(TAG, "Anchor created at: " + newAnchor.getPose()); break; }
                catch (Exception e) { Log.e(TAG, "Error creating anchor", e); }
            }
        }
        if (newAnchor != null) {
            synchronized (anchorLock) {
                if (anchors.size() >= 2) {
                     List<Anchor> anchorsToDetach = new ArrayList<>(anchors);
                     anchors.clear();
                     mainHandler.post(() -> {
                         Log.d(TAG, "Detaching old anchors from handleTap...");
                         for(Anchor oldAnchor : anchorsToDetach) {
                             oldAnchor.detach();
                         }
                     });
                }
                anchors.add(newAnchor);
            }

            showToast("Placed point " + anchors.size());
            Log.i(TAG, "Anchor added (check size later)");
            calculateAndSendDistance();
        } else { Log.w(TAG, "Tap did not hit a valid plane."); }
    }

    private void calculateAndSendDistance() {
        Pose pose1 = null;
        Pose pose2 = null;
        boolean hasTwoAnchors = false;
        synchronized (anchorLock) {
            if (anchors.size() == 2) {
                pose1 = anchors.get(0).getPose();
                pose2 = anchors.get(1).getPose();
                hasTwoAnchors = true;
            }
        }
        if (hasTwoAnchors && pose1 != null && pose2 != null) {
            float dx = pose1.tx() - pose2.tx(), dy = pose1.ty() - pose2.ty(), dz = pose1.tz() - pose2.tz();
            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz); Log.i(TAG, "Distance: " + dist + " m");
            showToast(String.format("Distance: %.2f m", dist));
            mainHandler.post(() -> { if (methodChannel != null) methodChannel.invokeMethod("measurementResult", dist); else Log.e(TAG,"MC null"); });
        } else { Log.w(TAG, "CalcDist: Not exactly 2 anchors when calculating."); }
    }

    private void handleClearPoints() {
         clearAnchorsInternal();
         showToast("Points cleared"); Log.i(TAG, "Points cleared via call.");
         mainHandler.post(() -> { if (methodChannel != null) methodChannel.invokeMethod("pointsCleared", null); else Log.e(TAG,"MC null"); });
    }

    private void clearAnchorsInternal() {
        List<Anchor> anchorsToDetach;
        synchronized (anchorLock) {
            if (anchors.isEmpty()) return;
            Log.d(TAG, "Detaching " + anchors.size() + " anchors.");
            anchorsToDetach = new ArrayList<>(anchors);
            anchors.clear();
        }
        for (Anchor a : anchorsToDetach) {
            a.detach();
        }
        surfaceDetected = false;
        loadingMessagehandler.removeCallbacks(loadingMessageRunnable);
        loadingMessagehandler.postDelayed(loadingMessageRunnable, TimeUnit.SECONDS.toMillis(1)); Log.i(TAG, "Internal anchors cleared.");
        if(lineRenderer != null) {
            Log.d(LINE_DEBUG_TAG, "Clearing line in clearAnchorsInternal");
            lineRenderer.updateLine(null, null);
        }
    }

    private void setupMethodCallHandler() {
         if (methodChannel != null) {
             methodChannel.setMethodCallHandler((call, result) -> {
                 Log.d(TAG, "Method call received: " + call.method);
                 switch (call.method) {
                     case "clearPoints":
                         handleClearPoints();
                         result.success(null);
                         break;
                     default: result.notImplemented(); break;
                 }
             }); Log.d(TAG, "Method Call Handler set.");
         } else { Log.e(TAG, "MethodChannel is null, cannot set MethodCallHandler."); }
    }
    private void showToast(final String message) {
         mainHandler.post(() -> { if (context != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); });
    }
    private void closeSession() {
         if (arSession != null) { Log.w(TAG, "Closing AR session."); arSession.close(); arSession = null; }
    }

}