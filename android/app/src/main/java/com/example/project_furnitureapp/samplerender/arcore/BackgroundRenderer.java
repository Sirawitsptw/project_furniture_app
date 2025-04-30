/*
 * Copyright 2021 Google LLC
 * ... (license header) ...
 */

package com.example.project_furnitureapp.samplerender.arcore;

// Correct the imports to use the local samplerender package
import com.example.project_furnitureapp.samplerender.Framebuffer;
import com.example.project_furnitureapp.samplerender.Mesh;
import com.example.project_furnitureapp.samplerender.SampleRender;
import com.example.project_furnitureapp.samplerender.Shader;
import com.example.project_furnitureapp.samplerender.Texture;
import com.example.project_furnitureapp.samplerender.VertexBuffer;
import com.example.project_furnitureapp.samplerender.GLError;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
// import android.media.Image;
import android.opengl.GLES30;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import android.util.Log;

/**
 * Renders the AR camera background and optionally composited virtual scene.
 * This version draws the background manually using OpenGL calls.
 */
public class BackgroundRenderer {
    private static final String TAG = BackgroundRenderer.class.getSimpleName();

    // Constants for quad coordinates
    private static final int COORDS_BUFFER_SIZE = 2 * 4 * 4; // 2 floats/vertex * 4 vertices * sizeof(float)

    // Buffer for NDC coordinates (shared static)
    private static final FloatBuffer NDC_QUAD_COORDS_BUFFER;
    // Buffer for virtual scene texture coordinates (shared static)
    private static final FloatBuffer VIRTUAL_SCENE_TEX_COORDS_BUFFER;

    static {
        final float[] ndcCoords = { -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f };
        NDC_QUAD_COORDS_BUFFER = ByteBuffer.allocateDirect(ndcCoords.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        NDC_QUAD_COORDS_BUFFER.put(ndcCoords).position(0);

        final float[] virtualSceneTexCoords = { 0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f };
        VIRTUAL_SCENE_TEX_COORDS_BUFFER = ByteBuffer.allocateDirect(virtualSceneTexCoords.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        VIRTUAL_SCENE_TEX_COORDS_BUFFER.put(virtualSceneTexCoords).position(0);
    }

    // Buffers and OpenGL resources
    // Buffer for transformed camera texture coordinates (instance specific)
    private final FloatBuffer cameraTexCoords = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

    // **** เก็บ VertexBuffer ที่จำเป็นไว้เป็น Field ****
    private final VertexBuffer screenCoordsVertexBuffer;
    private final VertexBuffer cameraTexCoordsVertexBuffer;
    // private final VertexBuffer virtualSceneTexCoordsVertexBuffer; // ไม่ได้ใช้โดยตรงใน draw() แบบ manual
    private final Mesh mesh; // ยังคงสร้าง Mesh ไว้เผื่อใช้กับ drawVirtualScene

    private Shader backgroundShader;
    private Shader occlusionShader;
    private final Texture cameraDepthTexture;
    private final Texture cameraColorTexture; // External OES
    private Texture depthColorPaletteTexture;

    // State flags
    private boolean useDepthVisualization = false;
    private boolean useOcclusion = false;
    private float aspectRatio = 1.0f;

    // Reference to SampleRender
    private final SampleRender render;

    public BackgroundRenderer(SampleRender render) throws IOException {
        this.render = render;
        try {
            cameraColorTexture = new Texture(render, Texture.Target.TEXTURE_EXTERNAL_OES, Texture.WrapMode.CLAMP_TO_EDGE, false);
            cameraDepthTexture = new Texture(render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE, false);

            // **** สร้างและเก็บ Vertex Buffers ที่จำเป็น ****
            this.screenCoordsVertexBuffer = new VertexBuffer(render, 2, NDC_QUAD_COORDS_BUFFER);
            this.cameraTexCoordsVertexBuffer = new VertexBuffer(render, 2, null); // Populated by updateDisplayGeometry
            VertexBuffer virtualSceneTexCoordsVertexBuffer = new VertexBuffer(render, 2, VIRTUAL_SCENE_TEX_COORDS_BUFFER);

            // สร้าง Mesh (อาจจะยังจำเป็นสำหรับ drawVirtualScene)
            VertexBuffer[] vertexBuffers = { this.screenCoordsVertexBuffer, this.cameraTexCoordsVertexBuffer, virtualSceneTexCoordsVertexBuffer };
            mesh = new Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, vertexBuffers);

            // Initialize shaders
            setUseDepthVisualization(false);
            setUseOcclusion(false);

        } catch (IOException e) { Log.e(TAG, "Failed init", e); close(); throw e; }
          catch (Throwable t) { Log.e(TAG, "Failed init", t); close(); throw new RuntimeException(t); }
    }


    public void setUseDepthVisualization(boolean useDepthVisualization) throws IOException {
        // ... (โค้ด setUseDepthVisualization เหมือนเดิม) ...
        if (backgroundShader != null && this.useDepthVisualization == useDepthVisualization) return;
        if (backgroundShader != null) { backgroundShader.close(); backgroundShader = null; }
        this.useDepthVisualization = useDepthVisualization;
        if (useDepthVisualization) {
            if (depthColorPaletteTexture == null) { depthColorPaletteTexture = Texture.createFromAsset(render, "models/depth_color_palette.png", Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.LINEAR); }
            backgroundShader = Shader.createFromAssets(render, "shaders/background_show_depth_color_visualization.vert", "shaders/background_show_depth_color_visualization.frag", null)
                    .setTexture("u_CameraDepthTexture", cameraDepthTexture).setTexture("u_ColorMap", depthColorPaletteTexture)
                    .setDepthTest(false).setDepthWrite(false);
        } else {
            backgroundShader = Shader.createFromAssets(render, "shaders/background_show_camera.vert", "shaders/background_show_camera.frag", null)
                    .setTexture("u_CameraColorTexture", cameraColorTexture)
                    .setDepthTest(false).setDepthWrite(false);
        }
        GLError.maybeLogGLError(Log.DEBUG, TAG, "After setUseDepthVisualization", "Set depth vis: " + useDepthVisualization);
    }

    public void setUseOcclusion(boolean useOcclusion) throws IOException {
        // ... (โค้ด setUseOcclusion เหมือนเดิม) ...
         if (occlusionShader != null && this.useOcclusion == useOcclusion) return;
         if (occlusionShader != null) { occlusionShader.close(); occlusionShader = null; }
         this.useOcclusion = useOcclusion;
         HashMap<String, String> defines = new HashMap<>(); defines.put("USE_OCCLUSION", useOcclusion ? "1" : "0");
         occlusionShader = Shader.createFromAssets(render, "shaders/occlusion.vert", "shaders/occlusion.frag", defines)
                 .setDepthTest(false).setDepthWrite(false).setBlend(Shader.BlendFactor.SRC_ALPHA, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA);
         if (useOcclusion) { occlusionShader.setTexture("u_CameraDepthTexture", cameraDepthTexture).setFloat("u_DepthAspectRatio", aspectRatio); }
         GLError.maybeLogGLError(Log.DEBUG, TAG, "After setUseOcclusion", "Set occlusion: " + useOcclusion);
    }

    public void updateDisplayGeometry(Frame frame) {
        // คำนวณ UV ที่ถูกต้องลงใน cameraTexCoords (FloatBuffer)
        // แล้วอัปเดต cameraTexCoordsVertexBuffer
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, NDC_QUAD_COORDS_BUFFER, Coordinates2d.TEXTURE_NORMALIZED, cameraTexCoords);
            // **** สำคัญ: อัปเดต VertexBuffer ที่เก็บ UV ****
            cameraTexCoordsVertexBuffer.set(cameraTexCoords);
        }
    }

    // Optional: updateCameraDepthTexture

    // **** แก้ไข: เมธอด draw() ให้วาดโดยตรง ****
    /** Draws the camera image or depth visualization background manually. */
    public void draw(Frame frame) {
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

        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);

        // 1. ตั้งค่า Texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLError.maybeLogGLError(Log.ERROR, TAG, "After glActiveTexture", "GL Error?");
        // ใช้ cameraColorTexture ที่เป็น field ของคลาสนี้
        // **** ใช้ค่าคงที่ GLES11Ext.GL_TEXTURE_EXTERNAL_OES แทน .glesEnum ****
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraColorTexture.getTextureId());
        GLError.maybeLogGLError(Log.ERROR, TAG, "After glBindTexture", "GL Error?");
        // ใช้ชื่อ uniform ที่ถูกต้องจาก shader ("u_CameraColorTexture")
        int textureUniformLocation = shaderToUse.getUniformLocation("u_CameraColorTexture");
        if (textureUniformLocation != -1) {
             GLES30.glUniform1i(textureUniformLocation, 0); // บอก shader ให้ใช้ Texture Unit 0
             GLError.maybeLogGLError(Log.ERROR, TAG, "After glUniform1i", "GL Error?");
        } else {
             Log.w(TAG, "Uniform u_CameraColorTexture not found in shader.");
        }

        // 2. ตั้งค่า Vertex Attributes
        // Attribute Location 0: a_Position (ใช้ screenCoordsVertexBuffer)
        int positionAttributeLocation = shaderToUse.getAttributeLocation("a_Position");
        if (positionAttributeLocation != -1) {
             GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, screenCoordsVertexBuffer.getBufferId());
             // numberOfEntriesPerVertex คือ 2 (x, y) สำหรับ screen coords
             GLES30.glVertexAttribPointer(positionAttributeLocation, screenCoordsVertexBuffer.getNumberOfEntriesPerVertex(), GLES30.GL_FLOAT, false, 0, 0);
             GLES30.glEnableVertexAttribArray(positionAttributeLocation);
             GLError.maybeLogGLError(Log.ERROR, TAG, "After setup a_Position", "GL Error?");
        } else { Log.w(TAG, "Attribute a_Position not found"); }

        // Attribute Location 1: a_CameraTexCoord (ใช้ cameraTexCoordsVertexBuffer ที่ update แล้ว)
        int texCoordAttributeLocation = shaderToUse.getAttributeLocation("a_CameraTexCoord");
        if (texCoordAttributeLocation != -1) {
             GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, cameraTexCoordsVertexBuffer.getBufferId());
             // numberOfEntriesPerVertex คือ 2 (u, v) สำหรับ camera tex coords
             GLES30.glVertexAttribPointer(texCoordAttributeLocation, cameraTexCoordsVertexBuffer.getNumberOfEntriesPerVertex(), GLES30.GL_FLOAT, false, 0, 0);
             GLES30.glEnableVertexAttribArray(texCoordAttributeLocation);
             GLError.maybeLogGLError(Log.ERROR, TAG, "After setup a_CameraTexCoord", "GL Error?");
        } else { Log.w(TAG, "Attribute a_CameraTexCoord not found"); }

        // 3. วาด Quad (ใช้ Triangle Strip, 4 vertices)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLError.maybeLogGLError(Log.ERROR, TAG, "After glDrawArrays (Manual Background)", "GL Error?");

        // 4. Cleanup State
        if (positionAttributeLocation != -1) GLES30.glDisableVertexAttribArray(positionAttributeLocation);
        if (texCoordAttributeLocation != -1) GLES30.glDisableVertexAttribArray(texCoordAttributeLocation);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0); // Unbind buffer
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0); // Unbind texture
        GLES30.glUseProgram(0); // Unbind shader program

        // Restore depth state
        GLES30.glDepthMask(true);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        // --- สิ้นสุดการวาด Background ด้วยตัวเอง ---

        // GLError.maybeLogGLError(Log.DEBUG, TAG, "After drawBackground (Manual)", "Finished drawing background manually");
    }


    /** Draws the virtual scene composited over the background */
    public void drawVirtualScene(Framebuffer virtualSceneFramebuffer, float zNear, float zFar) {
        // เมธอดนี้ยังคงใช้ render.draw(mesh, ...) เพราะซับซ้อนกว่า
        // ถ้า draw() แบบ manual ด้านบนทำงานได้ เมธอดนี้ก็น่าจะยังทำงานได้
        Shader shaderToUse = occlusionShader;
        if (shaderToUse == null) { Log.e(TAG, "Occlusion shader null"); return; }
        shaderToUse.setTexture("u_VirtualSceneColorTexture", virtualSceneFramebuffer.getColorTexture());
        if (useOcclusion) {
            shaderToUse.setTexture("u_VirtualSceneDepthTexture", virtualSceneFramebuffer.getDepthTexture())
                  .setTexture("u_CameraDepthTexture", cameraDepthTexture)
                  .setFloat("u_ZNear", zNear).setFloat("u_ZFar", zFar).setFloat("u_DepthAspectRatio", aspectRatio);
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST); GLES30.glDepthMask(false); GLES30.glEnable(GLES30.GL_BLEND);
        render.draw(mesh, shaderToUse); // ยังคงใช้ render.draw สำหรับส่วนนี้
        GLES30.glDisable(GLES30.GL_BLEND); GLES30.glDepthMask(true); GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLError.maybeLogGLError(Log.DEBUG, TAG, "After drawVirtualScene", "");
    }

    /** Returns the texture ID of the camera color texture (TEXTURE_EXTERNAL_OES) */
    public int getTextureId() {
        return cameraColorTexture != null ? cameraColorTexture.getTextureId() : 0;
    }
    public Texture getCameraColorTexture() { return cameraColorTexture; }
    public Texture getCameraDepthTexture() { return cameraDepthTexture; }

    public void close() {
        Log.d(TAG, "Closing BackgroundRenderer resources.");
        if (backgroundShader != null) { backgroundShader.close(); backgroundShader = null; }
        if (occlusionShader != null) { occlusionShader.close(); occlusionShader = null; }
        if (depthColorPaletteTexture != null) { depthColorPaletteTexture.close(); depthColorPaletteTexture = null; }
        // Textures, Mesh, VertexBuffers ถูกจัดการโดย SampleRender/Texture/Mesh/VertexBuffer classes
    }

    // Helper class for OES texture constant
    private static class GLES11Ext { public static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65; }
}
