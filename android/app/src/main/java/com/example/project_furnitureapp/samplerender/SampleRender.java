/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.project_furnitureapp.samplerender; // ตรวจสอบ package name ให้ตรงกับโปรเจกต์ของคุณ

import android.content.res.AssetManager;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Log; // เพิ่ม Log import ถ้าต้องการใช้

import javax.microedition.khronos.egl.EGLConfig; // อาจจะไม่ต้องใช้แล้ว
import javax.microedition.khronos.opengles.GL10; // อาจจะไม่ต้องใช้แล้ว

/**
 * A SampleRender context. Provides basic rendering methods and helpers.
 * (คำอธิบายอาจจะต้องปรับปรุงตามการใช้งานจริง)
 */
public class SampleRender {
    private static final String TAG = SampleRender.class.getSimpleName();

    private final AssetManager assetManager;

    // viewportWidth/Height อาจจะยังจำเป็น ถ้า SampleRender ใช้ในการคำนวณบางอย่าง
    // แต่การตั้งค่า viewport หลักๆ จะทำใน ArMeasureView.onSurfaceChanged
    private int viewportWidth = 1;
    private int viewportHeight = 1;

    /**
     * Constructs a SampleRender object.
     * Note: This version assumes the calling class (e.g., ArMeasureView)
     * is responsible for setting up the GLSurfaceView and its Renderer.
     * This constructor mainly stores the AssetManager.
     *
     * @param glSurfaceView Android GLSurfaceView (อาจจะไม่จำเป็นต้องใช้โดยตรงใน constructor นี้แล้ว)
     * @param renderer Renderer implementation (อาจจะไม่จำเป็นต้องใช้โดยตรงใน constructor นี้แล้ว)
     * @param assetManager AssetManager for loading Android resources (สำคัญ)
     */
    public SampleRender(GLSurfaceView glSurfaceView, Renderer renderer, AssetManager assetManager) {
        // **** เก็บเฉพาะ AssetManager ****
        this.assetManager = assetManager;
        Log.d(TAG, "SampleRender created. AssetManager stored.");

        // **** ลบ/คอมเมนต์ ส่วนที่ตั้งค่า GLSurfaceView ซ้ำซ้อนออกทั้งหมด ****
        /*
        glSurfaceView.setPreserveEGLContextOnPause(true);
        glSurfaceView.setEGLContextClientVersion(3); // Causes IllegalStateException if called after setRenderer
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Causes IllegalStateException
        glSurfaceView.setRenderer( // Causes IllegalStateException
            new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                    // This internal renderer is likely redundant now
                    GLES30.glEnable(GLES30.GL_BLEND);
                    GLError.maybeThrowGLException("Failed to enable blending", "glEnable");
                    if (renderer != null) {
                         renderer.onSurfaceCreated(SampleRender.this); // Forward call
                    }
                }

                @Override
                public void onSurfaceChanged(GL10 gl, int w, int h) {
                    // This internal renderer is likely redundant now
                    viewportWidth = w; // Store viewport size if needed by SampleRender methods
                    viewportHeight = h;
                     if (renderer != null) {
                         renderer.onSurfaceChanged(SampleRender.this, w, h); // Forward call
                     }
                }

                @Override
                public void onDrawFrame(GL10 gl) {
                    // This internal renderer is likely redundant now
                    // Clearing should be handled by the main renderer (ArMeasureView)
                    // clear(null, 0f, 0f, 0f, 1f);
                     if (renderer != null) {
                         renderer.onDrawFrame(SampleRender.this); // Forward call
                     }
                }
            });
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY); // Causes IllegalStateException
        glSurfaceView.setWillNotDraw(false); // Causes IllegalStateException
        */
        // **** สิ้นสุดส่วนที่ลบ/คอมเมนต์ ****

        // อาจจะยังต้อง init viewport size? หรือรอ onSurfaceChanged ของ ArMeasureView?
        // For safety, initialize to 1x1 until onSurfaceChanged is called in the main renderer.
        this.viewportWidth = 1;
        this.viewportHeight = 1;
    }

    /** Draw a {@link Mesh} with the specified {@link Shader}. Uses default framebuffer. */
    public void draw(Mesh mesh, Shader shader) {
        draw(mesh, shader, /*framebuffer=*/ null);
    }

    /** Draw a {@link Mesh} with the specified {@link Shader} to the given {@link Framebuffer}. */
    public void draw(Mesh mesh, Shader shader, Framebuffer framebuffer) {
        // This method likely still works as it handles GL state for drawing
        useFramebuffer(framebuffer); // Bind target framebuffer
        shader.lowLevelUse();      // Use the shader program
        mesh.lowLevelDraw();       // Draw the mesh
    }

    /** Clear the given framebuffer. */
    public void clear(Framebuffer framebuffer, float r, float g, float b, float a) {
        // This method likely still works
        useFramebuffer(framebuffer);
        GLES30.glClearColor(r, g, b, a);
        GLError.maybeThrowGLException("Failed to set clear color", "glClearColor");
        GLES30.glDepthMask(true); // Ensure depth writing is enabled for clear
        GLError.maybeThrowGLException("Failed to set depth write mask", "glDepthMask");
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        GLError.maybeThrowGLException("Failed to clear framebuffer", "glClear");
    }

    /**
     * Interface for rendering callbacks (Specific to this SampleRender class).
     * Note: This might be redundant if the main class implements GLSurfaceView.Renderer directly.
     */
    public static interface Renderer {
        public void onSurfaceCreated(SampleRender render);
        public void onSurfaceChanged(SampleRender render, int width, int height);
        public void onDrawFrame(SampleRender render);
    }

    /** Provides access to the AssetManager passed during construction. */
    /* package-private */ // Keep package-private or make public if needed elsewhere
    public AssetManager getAssets() { // Made public for easier access from renderers
        return assetManager;
    }

    /** Binds the specified framebuffer and sets the viewport. */
    private void useFramebuffer(Framebuffer framebuffer) {
        int framebufferId;
        int viewportWidth;
        int viewportHeight;
        if (framebuffer == null) {
            framebufferId = 0; // Default framebuffer
            // Use the stored viewport dimensions (should be updated by main renderer's onSurfaceChanged)
            viewportWidth = this.viewportWidth;
            viewportHeight = this.viewportHeight;
        } else {
            framebufferId = framebuffer.getFramebufferId();
            viewportWidth = framebuffer.getWidth();
            viewportHeight = framebuffer.getHeight();
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebufferId);
        GLError.maybeThrowGLException("Failed to bind framebuffer", "glBindFramebuffer");
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight);
        GLError.maybeThrowGLException("Failed to set viewport dimensions", "glViewport");

        // Store the dimensions if using default framebuffer? No, main renderer handles that.
        // if (framebuffer == null) {
        //     this.viewportWidth = viewportWidth;
        //     this.viewportHeight = viewportHeight;
        // }
    }

     // **** เพิ่มเมธอดนี้เพื่อให้ ArMeasureView อัปเดตขนาด Viewport ได้ ****
     // (จำเป็นเพราะเราลบ internal renderer ที่เคยอัปเดต viewportWidth/Height ออกไป)
     /** Updates the stored viewport dimensions. Should be called from the main renderer's onSurfaceChanged. */
     public void setViewport(int width, int height) {
          this.viewportWidth = width;
          this.viewportHeight = height;
          Log.d(TAG, "Viewport updated in SampleRender: " + width + "x" + height);
     }
}
