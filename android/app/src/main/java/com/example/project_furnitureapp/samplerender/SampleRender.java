/*
 * Copyright 2020 Google LLC // ข้อความลิขสิทธิ์ของ Google
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // แจ้งว่าใช้ Apache License 2.0
 * you may not use this file except in compliance with the License. // ห้ามใช้ไฟล์นี้ถ้าไม่ยอมรับ license
 * You may obtain a copy of the License at // สามารถดู license ได้ที่
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // URL ของ license
 *
 * Unless required by applicable law or agreed to in writing, software // ถ้าไม่ได้ระบุไว้เป็นลายลักษณ์อักษร
 * distributed under the License is distributed on an "AS IS" BASIS, // ซอฟต์แวร์นี้แจกแบบ "AS IS"
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // ไม่มีการรับประกันใดๆ
 * See the License for the specific language governing permissions and // ดูรายละเอียดใน license
 * limitations under the License. // ข้อจำกัดตาม license
 */
package com.example.project_furnitureapp.samplerender; // ประกาศ package ของไฟล์นี้

import android.content.res.AssetManager; // import AssetManager สำหรับโหลด resource
import android.opengl.GLES30; // import GLES30 สำหรับใช้งาน OpenGL ES 3.0
import android.opengl.GLSurfaceView; // import GLSurfaceView สำหรับแสดงผล OpenGL
import android.util.Log; // import Log สำหรับเขียน log

import javax.microedition.khronos.egl.EGLConfig; // import EGLConfig (อาจจะไม่ได้ใช้แล้ว)
import javax.microedition.khronos.opengles.GL10; // import GL10 (อาจจะไม่ได้ใช้แล้ว)

/**
 * A SampleRender context. Provides basic rendering methods and helpers.
 * (คำอธิบายอาจจะต้องปรับปรุงตามการใช้งานจริง)
 */
public class SampleRender { // ประกาศคลาส SampleRender
    private static final String TAG = SampleRender.class.getSimpleName(); // ตัวแปร TAG สำหรับ log

    private final AssetManager assetManager; // ตัวแปรเก็บ AssetManager

    // viewportWidth/Height อาจจะยังจำเป็น ถ้า SampleRender ใช้ในการคำนวณบางอย่าง
    // แต่การตั้งค่า viewport หลักๆ จะทำใน ArMeasureView.onSurfaceChanged
    private int viewportWidth = 1; // ตัวแปรเก็บความกว้าง viewport เริ่มต้น 1
    private int viewportHeight = 1; // ตัวแปรเก็บความสูง viewport เริ่มต้น 1

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
    public SampleRender(GLSurfaceView glSurfaceView, Renderer renderer, AssetManager assetManager) { // constructor รับ GLSurfaceView, Renderer, AssetManager
        // **** เก็บเฉพาะ AssetManager ****
        this.assetManager = assetManager; // กำหนดค่า AssetManager
        Log.d(TAG, "SampleRender created. AssetManager stored."); // log ว่าสร้าง SampleRender แล้ว

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
        this.viewportWidth = 1; // กำหนดค่าเริ่มต้น viewportWidth
        this.viewportHeight = 1; // กำหนดค่าเริ่มต้น viewportHeight
    }

    /** Draw a {@link Mesh} with the specified {@link Shader}. Uses default framebuffer. */ // คำอธิบายเมธอด: วาด Mesh ด้วย Shader ที่กำหนด (ใช้ framebuffer หลัก)
    public void draw(Mesh mesh, Shader shader) { // เมธอดวาด mesh
        draw(mesh, shader, /*framebuffer=*/ null); // เรียกเมธอด draw อีกตัวโดยไม่ระบุ framebuffer
    }

    /** Draw a {@link Mesh} with the specified {@link Shader} to the given {@link Framebuffer}. */ // คำอธิบายเมธอด: วาด Mesh ด้วย Shader ไปยัง framebuffer ที่กำหนด
    public void draw(Mesh mesh, Shader shader, Framebuffer framebuffer) { // เมธอดวาด mesh ไปยัง framebuffer
        // This method likely still works as it handles GL state for drawing
        useFramebuffer(framebuffer); // Bind target framebuffer
        shader.lowLevelUse();      // Use the shader program
        mesh.lowLevelDraw();       // Draw the mesh
    }

    /** Clear the given framebuffer. */ // คำอธิบายเมธอด: ล้าง framebuffer ที่กำหนด
    public void clear(Framebuffer framebuffer, float r, float g, float b, float a) { // เมธอดล้าง framebuffer
        // This method likely still works
        useFramebuffer(framebuffer); // bind framebuffer
        GLES30.glClearColor(r, g, b, a); // ตั้งค่าสีที่ใช้ล้าง
        GLError.maybeThrowGLException("Failed to set clear color", "glClearColor"); // เช็ค error
        GLES30.glDepthMask(true); // Ensure depth writing is enabled for clear
        GLError.maybeThrowGLException("Failed to set depth write mask", "glDepthMask"); // เช็ค error
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT); // ล้างทั้งสีและ depth
        GLError.maybeThrowGLException("Failed to clear framebuffer", "glClear"); // เช็ค error
    }

    /**
     * Interface for rendering callbacks (Specific to this SampleRender class).
     * Note: This might be redundant if the main class implements GLSurfaceView.Renderer directly.
     */
    public static interface Renderer { // interface สำหรับ callback การ render
        public void onSurfaceCreated(SampleRender render); // callback เมื่อ surface ถูกสร้าง
        public void onSurfaceChanged(SampleRender render, int width, int height); // callback เมื่อ surface เปลี่ยนขนาด
        public void onDrawFrame(SampleRender render); // callback เมื่อวาด frame
    }

    /** Provides access to the AssetManager passed during construction. */ // คำอธิบายเมธอด: คืนค่า AssetManager
    /* package-private */ // Keep package-private or make public if needed elsewhere
    public AssetManager getAssets() { // Made public for easier access from renderers
        return assetManager; // คืนค่า AssetManager
    }

    /** Binds the specified framebuffer and sets the viewport. */ // คำอธิบายเมธอด: bind framebuffer และตั้งค่า viewport
    private void useFramebuffer(Framebuffer framebuffer) { // เมธอด bind framebuffer
        int framebufferId; // ตัวแปรเก็บ id ของ framebuffer
        int viewportWidth; // ตัวแปรเก็บความกว้าง viewport
        int viewportHeight; // ตัวแปรเก็บความสูง viewport
        if (framebuffer == null) { // ถ้าไม่ได้ระบุ framebuffer
            framebufferId = 0; // Default framebuffer
            // Use the stored viewport dimensions (should be updated by main renderer's onSurfaceChanged)
            viewportWidth = this.viewportWidth; // ใช้ค่าที่เก็บไว้
            viewportHeight = this.viewportHeight; // ใช้ค่าที่เก็บไว้
        } else { // ถ้ามี framebuffer
            framebufferId = framebuffer.getFramebufferId(); // ดึง id ของ framebuffer
            viewportWidth = framebuffer.getWidth(); // ดึงความกว้าง
            viewportHeight = framebuffer.getHeight(); // ดึงความสูง
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebufferId); // bind framebuffer
        GLError.maybeThrowGLException("Failed to bind framebuffer", "glBindFramebuffer"); // เช็ค error
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight); // ตั้งค่า viewport
        GLError.maybeThrowGLException("Failed to set viewport dimensions", "glViewport"); // เช็ค error

        // Store the dimensions if using default framebuffer? No, main renderer handles that.
        // if (framebuffer == null) {
        //     this.viewportWidth = viewportWidth;
        //     this.viewportHeight = viewportHeight;
        // }
    }

     // **** เพิ่มเมธอดนี้เพื่อให้ ArMeasureView อัปเดตขนาด Viewport ได้ ****
     // (จำเป็นเพราะเราลบ internal renderer ที่เคยอัปเดต viewportWidth/Height ออกไป)
     /** Updates the stored viewport dimensions. Should be called from the main renderer's onSurfaceChanged. */ // คำอธิบายเมธอด: อัปเดตขนาด viewport ที่เก็บไว้
     public void setViewport(int width, int height) { // เมธอดอัปเดตขนาด viewport
          this.viewportWidth = width; // กำหนดความกว้างใหม่
          this.viewportHeight = height; // กำหนดความสูงใหม่
          Log.d(TAG, "Viewport updated in SampleRender: " + width + "x" + height); // log ขนาดใหม่
     }
}
