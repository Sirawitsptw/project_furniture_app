/*
 * Copyright 2021 Google LLC // ข้อความลิขสิทธิ์ของ Google
 * ... (license header) ...
 */

package com.example.project_furnitureapp.samplerender.arcore; // ประกาศ package ของไฟล์นี้

// Correct the imports to use the local samplerender package
import com.example.project_furnitureapp.samplerender.Framebuffer; // import Framebuffer สำหรับจัดการ framebuffer
import com.example.project_furnitureapp.samplerender.Mesh; // import Mesh สำหรับวาด geometry
import com.example.project_furnitureapp.samplerender.SampleRender; // import SampleRender สำหรับวาดบนหน้าจอ
import com.example.project_furnitureapp.samplerender.Shader; // import Shader สำหรับจัดการ shader
import com.example.project_furnitureapp.samplerender.Texture; // import Texture สำหรับจัดการ texture
import com.example.project_furnitureapp.samplerender.VertexBuffer; // import VertexBuffer สำหรับเก็บ vertex
import com.example.project_furnitureapp.samplerender.GLError; // import GLError สำหรับเช็ค error

import com.google.ar.core.Coordinates2d; // import Coordinates2d สำหรับแปลงพิกัด
import com.google.ar.core.Frame; // import Frame สำหรับข้อมูลกล้อง
// import android.media.Image;
import android.opengl.GLES30; // import GLES30 สำหรับใช้งาน OpenGL ES 3.0
import java.io.IOException; // import IOException สำหรับจัดการ exception
import java.nio.ByteBuffer; // import ByteBuffer สำหรับ buffer แบบ byte
import java.nio.ByteOrder; // import ByteOrder สำหรับกำหนด endian
import java.nio.FloatBuffer; // import FloatBuffer สำหรับ buffer แบบ float
import java.util.HashMap; // import HashMap สำหรับเก็บข้อมูลแบบ key-value
import android.util.Log; // import Log สำหรับเขียน log

/**
 * Renders the AR camera background and optionally composited virtual scene.
 * This version draws the background manually using OpenGL calls.
 */ // คำอธิบายคลาส: สำหรับวาดกล้อง AR และฉากเสมือน
public class BackgroundRenderer { // ประกาศคลาส BackgroundRenderer
    private static final String TAG = BackgroundRenderer.class.getSimpleName(); // ตัวแปร TAG สำหรับ log

    // Constants for quad coordinates
    private static final int COORDS_BUFFER_SIZE = 2 * 4 * 4; // 2 floats/vertex * 4 vertices * sizeof(float)

    // Buffer for NDC coordinates (shared static)
    private static final FloatBuffer NDC_QUAD_COORDS_BUFFER; // buffer สำหรับตำแหน่ง NDC ของ quad
    // Buffer for virtual scene texture coordinates (shared static)
    private static final FloatBuffer VIRTUAL_SCENE_TEX_COORDS_BUFFER; // buffer สำหรับ texture coordinate ของ virtual scene

    static {
        final float[] ndcCoords = { -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f }; // ตำแหน่ง NDC ของ quad
        NDC_QUAD_COORDS_BUFFER = ByteBuffer.allocateDirect(ndcCoords.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(); // สร้าง buffer
        NDC_QUAD_COORDS_BUFFER.put(ndcCoords).position(0); // ใส่ข้อมูลลง buffer

        final float[] virtualSceneTexCoords = { 0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f }; // texture coordinate ของ virtual scene
        VIRTUAL_SCENE_TEX_COORDS_BUFFER = ByteBuffer.allocateDirect(virtualSceneTexCoords.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(); // สร้าง buffer
        VIRTUAL_SCENE_TEX_COORDS_BUFFER.put(virtualSceneTexCoords).position(0); // ใส่ข้อมูลลง buffer
    }

    // Buffers and OpenGL resources
    // Buffer for transformed camera texture coordinates (instance specific)
    private final FloatBuffer cameraTexCoords = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(); // buffer สำหรับ camera UV

    // **** เก็บ VertexBuffer ที่จำเป็นไว้เป็น Field ****
    private final VertexBuffer screenCoordsVertexBuffer; // VertexBuffer สำหรับตำแหน่ง NDC
    private final VertexBuffer cameraTexCoordsVertexBuffer; // VertexBuffer สำหรับ camera UV
    // private final VertexBuffer virtualSceneTexCoordsVertexBuffer; // ไม่ได้ใช้โดยตรงใน draw() แบบ manual
    private final Mesh mesh; // Mesh สำหรับวาด virtual scene

    private Shader backgroundShader; // Shader สำหรับวาด background
    private Shader occlusionShader; // Shader สำหรับวาด occlusion
    private final Texture cameraDepthTexture; // Texture สำหรับ depth ของกล้อง
    private final Texture cameraColorTexture; // External OES // Texture สำหรับภาพกล้อง
    private Texture depthColorPaletteTexture; // Texture สำหรับ color palette ของ depth

    // State flags
    private boolean useDepthVisualization = false; // flag สำหรับเปิด/ปิด depth visualization
    private boolean useOcclusion = false; // flag สำหรับเปิด/ปิด occlusion
    private float aspectRatio = 1.0f; // อัตราส่วนภาพ

    // Reference to SampleRender
    private final SampleRender render; // ตัวแปร SampleRender

    public BackgroundRenderer(SampleRender render) throws IOException { // constructor รับ SampleRender
        this.render = render; // กำหนด render
        try {
            cameraColorTexture = new Texture(render, Texture.Target.TEXTURE_EXTERNAL_OES, Texture.WrapMode.CLAMP_TO_EDGE, false); // สร้าง texture สำหรับกล้อง
            cameraDepthTexture = new Texture(render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE, false); // สร้าง texture สำหรับ depth

            // **** สร้างและเก็บ Vertex Buffers ที่จำเป็น ****
            this.screenCoordsVertexBuffer = new VertexBuffer(render, 2, NDC_QUAD_COORDS_BUFFER); // VertexBuffer สำหรับตำแหน่ง NDC
            this.cameraTexCoordsVertexBuffer = new VertexBuffer(render, 2, null); // VertexBuffer สำหรับ camera UV (จะ update ทีหลัง)
            VertexBuffer virtualSceneTexCoordsVertexBuffer = new VertexBuffer(render, 2, VIRTUAL_SCENE_TEX_COORDS_BUFFER); // VertexBuffer สำหรับ virtual scene UV

            // สร้าง Mesh (อาจจะยังจำเป็นสำหรับ drawVirtualScene)
            VertexBuffer[] vertexBuffers = { this.screenCoordsVertexBuffer, this.cameraTexCoordsVertexBuffer, virtualSceneTexCoordsVertexBuffer }; // รวม VertexBuffer
            mesh = new Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, vertexBuffers); // สร้าง mesh

            // Initialize shaders
            setUseDepthVisualization(false); // ตั้งค่า depth visualization
            setUseOcclusion(false); // ตั้งค่า occlusion

        } catch (IOException e) { Log.e(TAG, "Failed init", e); close(); throw e; } // ถ้า error ให้ปิด resource
          catch (Throwable t) { Log.e(TAG, "Failed init", t); close(); throw new RuntimeException(t); }
    }


    public void setUseDepthVisualization(boolean useDepthVisualization) throws IOException { // เมธอดตั้งค่า depth visualization
        // ... (โค้ด setUseDepthVisualization เหมือนเดิม) ...
        if (backgroundShader != null && this.useDepthVisualization == useDepthVisualization) return; // ถ้า state เดิมเหมือนเดิม ไม่ต้องทำอะไร
        if (backgroundShader != null) { backgroundShader.close(); backgroundShader = null; } // ปิด shader เดิม
        this.useDepthVisualization = useDepthVisualization; // ตั้งค่า flag
        if (useDepthVisualization) {
            if (depthColorPaletteTexture == null) { depthColorPaletteTexture = Texture.createFromAsset(render, "models/depth_color_palette.png", Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.LINEAR); } // โหลด palette
            backgroundShader = Shader.createFromAssets(render, "shaders/background_show_depth_color_visualization.vert", "shaders/background_show_depth_color_visualization.frag", null)
                    .setTexture("u_CameraDepthTexture", cameraDepthTexture).setTexture("u_ColorMap", depthColorPaletteTexture)
                    .setDepthTest(false).setDepthWrite(false); // สร้าง shader สำหรับ depth
        } else {
            backgroundShader = Shader.createFromAssets(render, "shaders/background_show_camera.vert", "shaders/background_show_camera.frag", null)
                    .setTexture("u_CameraColorTexture", cameraColorTexture)
                    .setDepthTest(false).setDepthWrite(false); // สร้าง shader สำหรับกล้อง
        }
        GLError.maybeLogGLError(Log.DEBUG, TAG, "After setUseDepthVisualization", "Set depth vis: " + useDepthVisualization); // log
    }

    public void setUseOcclusion(boolean useOcclusion) throws IOException { // เมธอดตั้งค่า occlusion
         if (occlusionShader != null && this.useOcclusion == useOcclusion) return; // ถ้า state เดิมเหมือนเดิม ไม่ต้องทำอะไร
         if (occlusionShader != null) { occlusionShader.close(); occlusionShader = null; } // ปิด shader เดิม
         this.useOcclusion = useOcclusion; // ตั้งค่า flag
         HashMap<String, String> defines = new HashMap<>(); defines.put("USE_OCCLUSION", useOcclusion ? "1" : "0"); // ใส่ define
         occlusionShader = Shader.createFromAssets(render, "shaders/occlusion.vert", "shaders/occlusion.frag", defines)
                 .setDepthTest(false).setDepthWrite(false).setBlend(Shader.BlendFactor.SRC_ALPHA, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA); // สร้าง shader
         if (useOcclusion) { occlusionShader.setTexture("u_CameraDepthTexture", cameraDepthTexture).setFloat("u_DepthAspectRatio", aspectRatio); } // ตั้งค่า texture และ aspect ratio
         GLError.maybeLogGLError(Log.DEBUG, TAG, "After setUseOcclusion", "Set occlusion: " + useOcclusion); // log
    }

    public void updateDisplayGeometry(Frame frame) { // เมธอดอัปเดต geometry ของกล้อง
        // คำนวณ UV ที่ถูกต้องลงใน cameraTexCoords (FloatBuffer)
        // แล้วอัปเดต cameraTexCoordsVertexBuffer
        if (frame.hasDisplayGeometryChanged()) { // ถ้า geometry เปลี่ยน
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, NDC_QUAD_COORDS_BUFFER, Coordinates2d.TEXTURE_NORMALIZED, cameraTexCoords); // แปลงพิกัด
            // **** สำคัญ: อัปเดต VertexBuffer ที่เก็บ UV ****
            cameraTexCoordsVertexBuffer.set(cameraTexCoords); // อัปเดต buffer
        }
    }

    // Optional: updateCameraDepthTexture

    // **** แก้ไข: เมธอด draw() ให้วาดโดยตรง ****
    /** Draws the camera image or depth visualization background manually. */
    public void draw(Frame frame) { // เมธอดวาด background ของกล้อง
        updateDisplayGeometry(frame); // คำนวณ/อัปเดต UV ลง cameraTexCoordsVertexBuffer

        Shader shaderToUse = backgroundShader; // ควรจะเป็น background_show_camera โดย default
        if (shaderToUse == null) {
            Log.e(TAG, "Background shader is null during draw()");
            return;
        }
        if (cameraColorTexture == null || screenCoordsVertexBuffer == null || cameraTexCoordsVertexBuffer == null) {
             Log.e(TAG, "Cannot draw background, required resources are null.");
             return;
        }

        // --- วาด Background ด้วยตัวเอง ไม่ผ่าน Mesh ---
        shaderToUse.lowLevelUse(); // Activate the shader program
        GLError.maybeLogGLError(Log.ERROR, TAG, "After lowLevelUse", "GL Error?");

        GLES30.glDisable(GLES30.GL_DEPTH_TEST); // ปิด depth test
        GLES30.glDepthMask(false); // ปิด depth write

        // 1. ตั้งค่า Texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); // เลือก texture unit 0
        GLError.maybeLogGLError(Log.ERROR, TAG, "After glActiveTexture", "GL Error?");
        // ใช้ cameraColorTexture ที่เป็น field ของคลาสนี้
        // **** ใช้ค่าคงที่ GLES11Ext.GL_TEXTURE_EXTERNAL_OES แทน .glesEnum ****
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraColorTexture.getTextureId()); // bind texture กล้อง
        GLError.maybeLogGLError(Log.ERROR, TAG, "After glBindTexture", "GL Error?");
        // ใช้ชื่อ uniform ที่ถูกต้องจาก shader ("u_CameraColorTexture")
        int textureUniformLocation = shaderToUse.getUniformLocation("u_CameraColorTexture"); // หาตำแหน่ง uniform
        if (textureUniformLocation != -1) {
             GLES30.glUniform1i(textureUniformLocation, 0); // บอก shader ให้ใช้ Texture Unit 0
             GLError.maybeLogGLError(Log.ERROR, TAG, "After glUniform1i", "GL Error?");
        } else {
             Log.w(TAG, "Uniform u_CameraColorTexture not found in shader.");
        }

        // 2. ตั้งค่า Vertex Attributes
        // Attribute Location 0: a_Position (ใช้ screenCoordsVertexBuffer)
        int positionAttributeLocation = shaderToUse.getAttributeLocation("a_Position"); // หาตำแหน่ง attribute
        if (positionAttributeLocation != -1) {
             GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, screenCoordsVertexBuffer.getBufferId()); // bind buffer
             // numberOfEntriesPerVertex คือ 2 (x, y) สำหรับ screen coords
             GLES30.glVertexAttribPointer(positionAttributeLocation, screenCoordsVertexBuffer.getNumberOfEntriesPerVertex(), GLES30.GL_FLOAT, false, 0, 0); // กำหนด pointer
             GLES30.glEnableVertexAttribArray(positionAttributeLocation); // เปิดใช้งาน attribute
             GLError.maybeLogGLError(Log.ERROR, TAG, "After setup a_Position", "GL Error?");
        } else { Log.w(TAG, "Attribute a_Position not found"); }

        // Attribute Location 1: a_CameraTexCoord (ใช้ cameraTexCoordsVertexBuffer ที่ update แล้ว)
        int texCoordAttributeLocation = shaderToUse.getAttributeLocation("a_CameraTexCoord"); // หาตำแหน่ง attribute
        if (texCoordAttributeLocation != -1) {
             GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, cameraTexCoordsVertexBuffer.getBufferId()); // bind buffer
             // numberOfEntriesPerVertex คือ 2 (u, v) สำหรับ camera tex coords
             GLES30.glVertexAttribPointer(texCoordAttributeLocation, cameraTexCoordsVertexBuffer.getNumberOfEntriesPerVertex(), GLES30.GL_FLOAT, false, 0, 0); // กำหนด pointer
             GLES30.glEnableVertexAttribArray(texCoordAttributeLocation); // เปิดใช้งาน attribute
             GLError.maybeLogGLError(Log.ERROR, TAG, "After setup a_CameraTexCoord", "GL Error?");
        } else { Log.w(TAG, "Attribute a_CameraTexCoord not found"); }

        // 3. วาด Quad (ใช้ Triangle Strip, 4 vertices)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4); // วาด quad
        GLError.maybeLogGLError(Log.ERROR, TAG, "After glDrawArrays (Manual Background)", "GL Error?");

        // 4. Cleanup State
        if (positionAttributeLocation != -1) GLES30.glDisableVertexAttribArray(positionAttributeLocation); // ปิด attribute
        if (texCoordAttributeLocation != -1) GLES30.glDisableVertexAttribArray(texCoordAttributeLocation); // ปิด attribute
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0); // Unbind buffer
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0); // Unbind texture
        GLES30.glUseProgram(0); // Unbind shader program

        // Restore depth state
        GLES30.glDepthMask(true); // เปิด depth write กลับ
        GLES30.glEnable(GLES30.GL_DEPTH_TEST); // เปิด depth test กลับ
        // --- สิ้นสุดการวาด Background ด้วยตัวเอง ---

        // GLError.maybeLogGLError(Log.DEBUG, TAG, "After drawBackground (Manual)", "Finished drawing background manually");
    }


    /** Draws the virtual scene composited over the background */
    public void drawVirtualScene(Framebuffer virtualSceneFramebuffer, float zNear, float zFar) { // เมธอดวาดฉากเสมือนทับ background
        // เมธอดนี้ยังคงใช้ render.draw(mesh, ...) เพราะซับซ้อนกว่า
        // ถ้า draw() แบบ manual ด้านบนทำงานได้ เมธอดนี้ก็น่าจะยังทำงานได้
        Shader shaderToUse = occlusionShader; // เลือก shader สำหรับ occlusion
        if (shaderToUse == null) { Log.e(TAG, "Occlusion shader null"); return; } // ถ้าไม่มี shader ให้ return
        shaderToUse.setTexture("u_VirtualSceneColorTexture", virtualSceneFramebuffer.getColorTexture()); // ตั้งค่า texture สี
        if (useOcclusion) {
            shaderToUse.setTexture("u_VirtualSceneDepthTexture", virtualSceneFramebuffer.getDepthTexture())
                  .setTexture("u_CameraDepthTexture", cameraDepthTexture)
                  .setFloat("u_ZNear", zNear).setFloat("u_ZFar", zFar).setFloat("u_DepthAspectRatio", aspectRatio); // ตั้งค่า uniform สำหรับ occlusion
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST); GLES30.glDepthMask(false); GLES30.glEnable(GLES30.GL_BLEND); // ตั้งค่า GL state
        render.draw(mesh, shaderToUse); // ยังคงใช้ render.draw สำหรับส่วนนี้
        GLES30.glDisable(GLES30.GL_BLEND); GLES30.glDepthMask(true); GLES30.glEnable(GLES30.GL_DEPTH_TEST); // คืนค่า GL state
        GLError.maybeLogGLError(Log.DEBUG, TAG, "After drawVirtualScene", ""); // log
    }

    /** Returns the texture ID of the camera color texture (TEXTURE_EXTERNAL_OES) */
    public int getTextureId() { // คืนค่า texture id ของกล้อง
        return cameraColorTexture != null ? cameraColorTexture.getTextureId() : 0;
    }
    public Texture getCameraColorTexture() { return cameraColorTexture; } // คืนค่า texture ของกล้อง
    public Texture getCameraDepthTexture() { return cameraDepthTexture; } // คืนค่า texture ของ depth

    public void close() { // เมธอดปิด resource
        Log.d(TAG, "Closing BackgroundRenderer resources."); // log
        if (backgroundShader != null) { backgroundShader.close(); backgroundShader = null; } // ปิด shader
        if (occlusionShader != null) { occlusionShader.close(); occlusionShader = null; } // ปิด shader
        if (depthColorPaletteTexture != null) { depthColorPaletteTexture.close(); depthColorPaletteTexture = null; } // ปิด texture
        // Textures, Mesh, VertexBuffers ถูกจัดการโดย SampleRender/Texture/Mesh/VertexBuffer classes
    }

    // Helper class for OES texture constant
    private static class GLES11Ext { public static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65; } // ค่าคงที่สำหรับ OES texture
}
