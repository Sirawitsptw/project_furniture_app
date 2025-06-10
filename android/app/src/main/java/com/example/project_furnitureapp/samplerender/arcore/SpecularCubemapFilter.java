/*
 * Copyright 2021 Google LLC // ข้อความลิขสิทธิ์ของ Google
 * ... (license header) ...
 */
package com.example.project_furnitureapp.samplerender.arcore; // ประกาศ package ของไฟล์นี้

// Imports... (including samplerender types, GLError, core Image, opengl, Log, nio, graphics.ImageFormat etc.)
import android.media.Image; // import Image สำหรับจัดการภาพ
import android.media.Image.Plane; // import Plane สำหรับข้อมูลแต่ละ channel ของ Image
import android.opengl.GLES30; // import GLES30 สำหรับใช้งาน OpenGL ES 3.0
import android.util.Log; // import Log สำหรับเขียน log

import com.example.project_furnitureapp.samplerender.Framebuffer; // import Framebuffer
import com.example.project_furnitureapp.samplerender.GLError; // import GLError
import com.example.project_furnitureapp.samplerender.Mesh; // import Mesh
import com.example.project_furnitureapp.samplerender.SampleRender; // import SampleRender
import com.example.project_furnitureapp.samplerender.Shader; // import Shader
import com.example.project_furnitureapp.samplerender.Texture; // import Texture
import com.example.project_furnitureapp.samplerender.VertexBuffer; // import VertexBuffer

import java.io.Closeable; // import Closeable สำหรับปิด resource
import java.io.IOException; // import IOException สำหรับจัดการ exception
import java.nio.ByteBuffer; // import ByteBuffer สำหรับ buffer แบบ byte
import java.nio.ByteOrder; // import ByteOrder สำหรับกำหนด endian
import java.nio.FloatBuffer; // import FloatBuffer สำหรับ buffer แบบ float
import java.util.ArrayList; // import ArrayList สำหรับเก็บข้อมูล
import java.util.HashMap; // import HashMap สำหรับเก็บข้อมูลแบบ key-value
import java.util.Iterator; // import Iterator สำหรับวนลูป

import android.graphics.ImageFormat; // Import ImageFormat
import static java.lang.Math.min; // import min
import static java.lang.Math.max; // import max

/**
 * Filters an environmental HDR cubemap into a prefiltered specular cubemap.
 */ // คำอธิบายคลาส: ฟิลเตอร์ cubemap HDR ให้เป็น specular cubemap
public class SpecularCubemapFilter implements Closeable { // ประกาศคลาส SpecularCubemapFilter และ implement Closeable
    private static final String TAG = SpecularCubemapFilter.class.getSimpleName(); // ตัวแปร TAG สำหรับ log

    // Constants...
    private static final int COMPONENTS_PER_VERTEX = 2; // จำนวน float ต่อ 1 vertex (x, y)
    private static final int NUMBER_OF_VERTICES = 4; // จำนวน vertex ของ quad
    private static final int FLOAT_SIZE = 4; // ขนาด float (byte)
    private static final int COORDS_BUFFER_SIZE = COMPONENTS_PER_VERTEX * NUMBER_OF_VERTICES * FLOAT_SIZE; // ขนาด buffer สำหรับ quad
    private static final int NUMBER_OF_CUBE_FACES = 6; // จำนวนหน้าของ cubemap
    private static final int[] CUBEMAP_FACE_ORDER = { // ลำดับหน้าของ cubemap
        GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X, GLES30.GL_TEXTURE_CUBE_MAP_NEGATIVE_X,
        GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, GLES30.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y,
        GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, GLES30.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z };
    private static final FloatBuffer COORDS_BUFFER =
            ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer(); // buffer สำหรับ quad
    static { COORDS_BUFFER.put(new float[] { -1f, -1f, +1f, -1f, -1f, +1f, +1f, +1f }); } // กำหนดตำแหน่ง vertex ของ quad
    private static final String[] ATTACHMENT_LOCATION_DEFINES = { // ชื่อ define สำหรับแต่ละหน้าของ cubemap
        "PX_LOCATION", "NX_LOCATION", "PY_LOCATION", "NY_LOCATION", "PZ_LOCATION", "NZ_LOCATION", };
    private static final int[] ATTACHMENT_ENUMS = { // enum สำหรับแต่ละ color attachment
        GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_COLOR_ATTACHMENT1, GLES30.GL_COLOR_ATTACHMENT2,
        GLES30.GL_COLOR_ATTACHMENT3, GLES30.GL_COLOR_ATTACHMENT4, GLES30.GL_COLOR_ATTACHMENT5, };

    // Inner classes Chunk, ChunkIterable, ImportanceSampleCacheEntry... (remain the same)
    private static class Chunk { /* ... as before ... */
        public final int chunkIndex; public final int chunkSize; public final int firstFaceIndex;
        public Chunk(int chunkIndex, int maxChunkSize) {
            this.chunkIndex = chunkIndex; this.firstFaceIndex = chunkIndex * maxChunkSize;
            this.chunkSize = min(maxChunkSize, NUMBER_OF_CUBE_FACES - this.firstFaceIndex);
        }
    }
    private static class ChunkIterable implements Iterable<Chunk> { /* ... as before ... */
         public final int maxChunkSize; public final int numberOfChunks;
         public ChunkIterable(int maxNumberOfColorAttachments) {
             this.maxChunkSize = min(maxNumberOfColorAttachments, NUMBER_OF_CUBE_FACES);
             int numChunks = NUMBER_OF_CUBE_FACES / this.maxChunkSize;
             if (NUMBER_OF_CUBE_FACES % this.maxChunkSize != 0) { numChunks++; }
             this.numberOfChunks = numChunks;
         }
         @Override public Iterator<Chunk> iterator() { /* ... as before ... */
             return new Iterator<Chunk>() {
                 private Chunk chunk = new Chunk(0, maxChunkSize);
                 @Override public boolean hasNext() { return chunk.chunkIndex < numberOfChunks; }
                 @Override public Chunk next() { Chunk r = this.chunk; this.chunk = new Chunk(r.chunkIndex + 1, maxChunkSize); return r; }
             };
         }
    }
    private static class ImportanceSampleCacheEntry { /* ... as before ... */
        public float[] direction; public float contribution; public float level;
    }

    // Fields
    private final int resolution; // ความละเอียดของ cubemap
    private final int numberOfImportanceSamples; // จำนวน sample สำหรับ importance sampling
    private final int numberOfMipmapLevels; // จำนวน mipmap levels

    private final Texture radianceCubemap; // texture สำหรับ radiance cubemap
    private final Texture ldCubemap; // The filtered result // texture สำหรับผลลัพธ์ที่ฟิลเตอร์แล้ว
    private final Shader[] shaders; // One per chunk // shader สำหรับแต่ละ chunk
    private final Mesh mesh; // Fullscreen quad // mesh สำหรับ quad เต็มจอ
    private int[][] framebuffers; // [mipmapLevel][chunk] // framebuffer สำหรับแต่ละ mipmap และ chunk

    // **แก้ไข:** เพิ่ม field render
    private final SampleRender render; // ตัวแปรเก็บ SampleRender

    public SpecularCubemapFilter(SampleRender render, int resolution, int numberOfImportanceSamples) throws IOException { // constructor รับ SampleRender, ความละเอียด, จำนวน sample
        this.render = render; // **แก้ไข:** เก็บ instance
        this.resolution = resolution; // กำหนดความละเอียด
        this.numberOfImportanceSamples = numberOfImportanceSamples; // กำหนดจำนวน sample
        // Calculate mipmap levels based on log2
        this.numberOfMipmapLevels = log2(resolution) + 1; // คำนวณจำนวน mipmap levels

        try {
            radianceCubemap = new Texture(render, Texture.Target.TEXTURE_CUBE_MAP, Texture.WrapMode.CLAMP_TO_EDGE, true); // Enable mipmaps for radiance
            ldCubemap = new Texture(render, Texture.Target.TEXTURE_CUBE_MAP, Texture.WrapMode.CLAMP_TO_EDGE, true); // Enable mipmaps for result

            ChunkIterable chunks = new ChunkIterable(getMaxColorAttachments()); // สร้าง chunks ตามจำนวน color attachment
            initializeLdCubemap(); // Initialize storage for result // เตรียมพื้นที่เก็บผลลัพธ์
            shaders = createShaders(render, chunks); // Create shaders needed for filtering // สร้าง shader สำหรับแต่ละ chunk
            framebuffers = createFramebuffers(chunks); // Create FBOs for rendering to ldCubemap mips // สร้าง framebuffer สำหรับแต่ละ mipmap/chunk

            VertexBuffer coordsBuffer = new VertexBuffer(render, COMPONENTS_PER_VERTEX, COORDS_BUFFER); // สร้าง vertex buffer สำหรับ quad
            mesh = new Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, new VertexBuffer[] {coordsBuffer}); // สร้าง mesh quad

        } catch (Throwable t) {
            Log.e(TAG, "Failed during SpecularCubemapFilter initialization", t); // log error
            close(); // ปิด resource
            throw new RuntimeException("Failed to initialize SpecularCubemapFilter", t); // ขว้าง exception
        }
    }

    @Override
    public void close() { // เมธอดปิด resource
        Log.d(TAG, "Closing SpecularCubemapFilter resources."); // log
        if (framebuffers != null) {
            for (int level = 0; level < framebuffers.length; ++level) {
                if (framebuffers[level] != null && framebuffers[level].length > 0) {
                    GLES30.glDeleteFramebuffers(framebuffers[level].length, framebuffers[level], 0); // ลบ framebuffer
                }
            }
            framebuffers = null; // เซ็ตเป็น null
        }
        if (radianceCubemap != null) radianceCubemap.close(); // ปิด radiance cubemap
        if (ldCubemap != null) ldCubemap.close(); // ปิด ld cubemap
        if (shaders != null) {
            for (Shader shader : shaders) {
                if (shader != null) shader.close(); // ปิด shader
            }
        }
        // Mesh and vertex buffer are likely managed elsewhere
    }

    /** Updates the radiance cubemap and runs the filter. */ // เมธอดอัปเดต radiance cubemap และรันฟิลเตอร์
    public void update(Image[] images) { // เมธอดอัปเดต cubemap จาก array ของ Image
        // **แก้ไข:** ใช้ค่าคงที่ int สำหรับ format
        final int IMAGE_FORMAT_RGBA_FP16 = 22; // 0x16

        try {
            // --- 1. Update Radiance Cubemap ---
            GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, radianceCubemap.getTextureId()); // bind radiance cubemap
            GLError.maybeThrowGLException("Failed to bind radiance cubemap texture", "glBindTexture"); // เช็ค error

            if (images == null || images.length != NUMBER_OF_CUBE_FACES) { /* ... error handling ... */
                throw new IllegalArgumentException("Invalid input images array"); // ขว้าง exception ถ้า images ไม่ครบ 6 หน้า
            }

            for (int i = 0; i < NUMBER_OF_CUBE_FACES; ++i) { // วนลูปทุกหน้า
                int faceTarget = CUBEMAP_FACE_ORDER[i]; // เลือก target ของหน้า
                Image image = images[i]; // ดึง image
                if (image == null) throw new IllegalArgumentException("Input image " + i + " is null"); // ถ้า image เป็น null ขว้าง exception
                Plane plane = image.getPlanes()[0]; // ดึง plane แรก

                // **แก้ไข:** ใช้ค่าคงที่ int
                if (image.getFormat() != IMAGE_FORMAT_RGBA_FP16) { /* ... error handling ... */
                     throw new IllegalArgumentException("Unexpected image format for face " + i + ": " + image.getFormat()); // ขว้าง exception ถ้า format ไม่ถูกต้อง
                }
                if (image.getHeight() != image.getWidth() || image.getHeight() != resolution) { /* ... error handling ... */
                    throw new IllegalArgumentException("Invalid image dimensions for face " + i); // ขว้าง exception ถ้าขนาดไม่ถูกต้อง
                }

                GLES30.glTexImage2D(faceTarget, 0, GLES30.GL_RGBA16F, resolution, resolution, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, plane.getBuffer()); // ส่งข้อมูลไปยัง cubemap
                GLError.maybeThrowGLException("Failed to populate cubemap face " + i, "glTexImage2D"); // เช็ค error
            }

            // Generate mipmaps for the source radiance cubemap
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_CUBE_MAP); // สร้าง mipmaps
            GLError.maybeThrowGLException("Failed to generate mipmaps for radiance cubemap", "glGenerateMipmap");

             // Set filtering for radiance map sampling (important for quality)
             GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR); // ตั้งค่า min filter
             GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR); // ตั้งค่า mag filter


            // --- 2. Filter into LD Cubemap ---
            GLES30.glDisable(GLES30.GL_DEPTH_TEST); // ปิด depth test
            GLES30.glDepthMask(false); // ปิด depth write
            GLError.maybeLogGLError(Log.DEBUG, TAG, "Setup before filtering loop", "glDepthMask"); // log

            ChunkIterable chunks = new ChunkIterable(getMaxColorAttachments()); // สร้าง chunks

            for (int level = 0; level < numberOfMipmapLevels; ++level) { // วนลูปทุก mipmap level
                int mipmapResolution = resolution >> level; // คำนวณขนาด
                if (mipmapResolution == 0) continue; // Skip if resolution becomes 0

                GLES30.glViewport(0, 0, mipmapResolution, mipmapResolution); // ตั้งค่า viewport
                GLError.maybeLogGLError(Log.DEBUG, TAG, "Viewport set for level " + level, "glViewport"); // log

                for (Chunk chunk : chunks) { // วนลูปทุก chunk
                    // Check if framebuffer exists for this level/chunk
                    if (framebuffers == null || level >= framebuffers.length || chunk.chunkIndex >= framebuffers[level].length) {
                         Log.e(TAG, "Framebuffer array access out of bounds for level " + level + ", chunk " + chunk.chunkIndex); // log error
                         continue; // Skip this chunk if FBO doesn't exist
                    }
                    int fbo = framebuffers[level][chunk.chunkIndex]; // ดึง framebuffer id
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo); // bind framebuffer
                    GLError.maybeLogGLError(Log.DEBUG, TAG, "Bound FBO " + fbo + " for level " + level + ", chunk " + chunk.chunkIndex, "glBindFramebuffer"); // log

                    Shader shader = shaders[chunk.chunkIndex]; // ดึง shader
                    if (shader == null) {
                         Log.e(TAG, "Shader is null for chunk " + chunk.chunkIndex); // log error
                         continue; // Skip if shader doesn't exist
                    }
                    shader.lowLevelUse(); // ใช้งาน shader
                    GLError.maybeLogGLError(Log.DEBUG, TAG, "Used shader for chunk " + chunk.chunkIndex, "glUseProgram"); // log

                    float perceptualRoughness = (float) level / (float) max(1, numberOfMipmapLevels - 1); // Avoid division by zero // คำนวณ perceptual roughness
                    float roughness = perceptualRoughness * perceptualRoughness; // คำนวณ roughness
                    shader.setFloat("u_Roughness", roughness); // ส่งค่า roughness ไปยัง shader
                    GLError.maybeLogGLError(Log.DEBUG, TAG, "Set roughness for level " + level, "glUniform1f"); // log

                    // **แก้ไข:** ใช้ this.render
                    this.render.draw(mesh, shader); // วาด mesh ด้วย shader
                    GLError.maybeLogGLError(Log.DEBUG, TAG, "Drew mesh for level " + level + ", chunk " + chunk.chunkIndex, "glDrawArrays"); // log
                }
            }

            // Restore state
            // Note: Viewport should be reset by the calling renderer (e.g., ArMeasureView.onSurfaceChanged)
            GLES30.glDepthMask(true); // เปิด depth write กลับ
            GLES30.glEnable(GLES30.GL_DEPTH_TEST); // เปิด depth test กลับ
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); // bind framebuffer หลัก
            GLError.maybeLogGLError(Log.DEBUG, TAG, "Restored GL state after filtering", "glBindFramebuffer"); // log

        } catch (Throwable t) {
            Log.e(TAG, "Error during cubemap update and filtering", t); // log error
            // Handle error appropriately
        } finally {
            if (images != null) {
                for (Image image : images) {
                    if (image != null) image.close(); // ปิด image ทุกตัว
                }
            }
        }
    }


    public int getNumberOfMipmapLevels() { return numberOfMipmapLevels; } // คืนค่าจำนวน mipmap levels
    public Texture getFilteredCubemapTexture() { return ldCubemap; } // คืนค่า texture ที่ฟิลเตอร์แล้ว

    // --- Private Helper Methods ---

    private void initializeLdCubemap() { /* ... as before, ensure parameters set ... */
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, ldCubemap.getTextureId()); // bind ld cubemap
        GLError.maybeThrowGLException("Could not bind LD cubemap texture", "glBindTexture"); // เช็ค error
        for (int level = 0; level < numberOfMipmapLevels; ++level) { // วนลูปทุก mipmap level
            int mipmapResolution = resolution >> level; // คำนวณขนาด
             if (mipmapResolution == 0) mipmapResolution = 1; // Min size 1x1
            for (int face = 0; face < NUMBER_OF_CUBE_FACES; ++face) { // วนลูปทุกหน้า
                 int faceTarget = CUBEMAP_FACE_ORDER[face]; // ดึง target ของหน้า
                GLES30.glTexImage2D(faceTarget, level, GLES30.GL_RGBA16F, mipmapResolution, mipmapResolution, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null); // จองพื้นที่
                GLError.maybeThrowGLException("Could not initialize LD cubemap mipmap level " + level + ", face " + face, "glTexImage2D"); // เช็ค error
            }
        }
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR); // ตั้งค่า min filter
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR); // ตั้งค่า mag filter
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE); // ตั้งค่า wrap S
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE); // ตั้งค่า wrap T
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE); // ตั้งค่า wrap R
        GLError.maybeLogGLError(Log.DEBUG, TAG, "Set LD Cubemap texture parameters", "glTexParameteri"); // log
    }

    private Shader[] createShaders(SampleRender render, ChunkIterable chunks) throws IOException { /* ... as before ... */
        ImportanceSampleCacheEntry[][] importanceSampleCaches = generateImportanceSampleCaches(); // สร้าง importance sample cache
        HashMap<String, String> commonDefines = new HashMap<>(); // สร้าง defines ร่วม
        commonDefines.put("NUMBER_OF_IMPORTANCE_SAMPLES", Integer.toString(numberOfImportanceSamples)); // ใส่ define จำนวน sample
        commonDefines.put("NUMBER_OF_MIPMAP_LEVELS", Integer.toString(numberOfMipmapLevels)); // ใส่ define จำนวน mipmap
        Shader[] createdShaders = new Shader[chunks.numberOfChunks]; // Use different name // สร้าง array สำหรับ shader
        try {
            for (Chunk chunk : chunks) { // วนลูปทุก chunk
                HashMap<String, String> defines = new HashMap<>(commonDefines); // copy defines
                for (int location = 0; location < chunk.chunkSize; ++location) { // วนลูปทุก attachment
                    defines.put(ATTACHMENT_LOCATION_DEFINES[chunk.firstFaceIndex + location], Integer.toString(location)); // ใส่ define สำหรับแต่ละหน้า
                }
                createdShaders[chunk.chunkIndex] = Shader.createFromAssets(render, "shaders/cubemap_filter.vert", "shaders/cubemap_filter.frag", defines)
                            .setTexture("u_Cubemap", radianceCubemap)
                            .setDepthTest(false).setDepthWrite(false); // สร้าง shader และตั้งค่า
                // Set uniforms after creation
                Shader currentShader = createdShaders[chunk.chunkIndex]; // ดึง shader
                for (int i = 0; i < importanceSampleCaches.length; ++i) { // วนลูปทุก cache
                    ImportanceSampleCacheEntry[] cache = importanceSampleCaches[i]; // ดึง cache
                    String cacheName = "u_ImportanceSampleCaches[" + i + "]"; // ชื่อ uniform
                    currentShader.setInt(cacheName + ".number_of_entries", cache.length); // ส่งจำนวน entry
                    for (int j = 0; j < cache.length; ++j) { // วนลูปทุก entry
                        ImportanceSampleCacheEntry entry = cache[j]; // ดึง entry
                        String entryName = cacheName + ".entries[" + j + "]"; // ชื่อ uniform
                        currentShader.setVec3(entryName + ".direction", entry.direction)
                                     .setFloat(entryName + ".contribution", entry.contribution)
                                     .setFloat(entryName + ".level", entry.level); // ส่งค่าไปยัง shader
                    }
                }
                 GLError.maybeLogGLError(Log.DEBUG, TAG, "Finished setting uniforms for shader chunk " + chunk.chunkIndex, "glUniform*"); // log
            }
        } catch (IOException | RuntimeException e) {
             for (Shader s : createdShaders) { if (s != null) s.close(); } throw e; // ปิด shader ถ้า error
        }
        return createdShaders; // คืนค่า shaders
    }

    private int[][] createFramebuffers(ChunkIterable chunks) { /* ... as before, ensure GL checks ... */
        int[][] createdFramebuffers = new int[numberOfMipmapLevels][chunks.numberOfChunks]; // สร้าง array สำหรับ framebuffer
        try {
            for (int level = 0; level < numberOfMipmapLevels; ++level) { // วนลูปทุก mipmap level
                GLES30.glGenFramebuffers(chunks.numberOfChunks, createdFramebuffers[level], 0); // สร้าง framebuffer
                GLError.maybeThrowGLException("Could not create framebuffers level " + level, "glGenFramebuffers"); // เช็ค error
                for (Chunk chunk : chunks) { // วนลูปทุก chunk
                    int fbo = createdFramebuffers[level][chunk.chunkIndex]; // ดึง framebuffer id
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo); // bind framebuffer
                    GLError.maybeThrowGLException("Could not bind FBO " + fbo, "glBindFramebuffer"); // เช็ค error
                    if (chunk.chunkSize > 1) { /* set glDrawBuffers */
                        int[] drawBuffers = new int[chunk.chunkSize]; // สร้าง array สำหรับ draw buffer
                        for(int i=0; i<chunk.chunkSize; ++i) drawBuffers[i] = ATTACHMENT_ENUMS[i]; // ใส่ค่า
                        GLES30.glDrawBuffers(chunk.chunkSize, drawBuffers, 0); // ตั้งค่า draw buffer
                        GLError.maybeThrowGLException("Could not set draw buffers FBO " + fbo, "glDrawBuffers"); // เช็ค error
                    }
                    for (int attachment = 0; attachment < chunk.chunkSize; ++attachment) { // วนลูปทุก attachment
                        int faceIndex = chunk.firstFaceIndex + attachment; // ดึง index ของหน้า
                        int faceTarget = CUBEMAP_FACE_ORDER[faceIndex]; // ดึง target ของหน้า
                        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, ATTACHMENT_ENUMS[attachment], faceTarget, ldCubemap.getTextureId(), level); // bind texture กับ framebuffer
                        GLError.maybeThrowGLException("Could not attach texture face "+faceIndex+" level "+level, "glFramebufferTexture2D"); // เช็ค error
                    }
                    int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER); // ตรวจสอบสถานะ framebuffer
                    if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) { throw new RuntimeException("FBO incomplete: 0x" + Integer.toHexString(status)); } // ขว้าง exception ถ้าไม่สมบูรณ์
                }
            }
        } catch(RuntimeException e) {
             // Clean up partially created FBOs
             if (createdFramebuffers != null) {
                 for(int lvl=0; lvl<createdFramebuffers.length; ++lvl) {
                     if (createdFramebuffers[lvl] != null && createdFramebuffers[lvl].length > 0) {
                         GLES30.glDeleteFramebuffers(createdFramebuffers[lvl].length, createdFramebuffers[lvl], 0); // ลบ framebuffer
                     }
                 }
             }
             throw e; // Re-throw
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); // bind framebuffer หลัก
        }
        return createdFramebuffers; // คืนค่า framebuffer
    }

    private ImportanceSampleCacheEntry[][] generateImportanceSampleCaches() { /* ... logic as before, with float LOD calculation fix ... */
        ImportanceSampleCacheEntry[][] result = new ImportanceSampleCacheEntry[numberOfMipmapLevels - 1][]; // สร้าง array สำหรับ cache
        for (int i = 0; i < numberOfMipmapLevels - 1; ++i) { // วนลูปทุก mipmap level (ยกเว้น 0)
            int mipmapLevelInt = i + 1; // Use integer loop variable name
            float perceptualRoughness = (float) mipmapLevelInt / (float) max(1, numberOfMipmapLevels - 1); // คำนวณ perceptual roughness
            float roughness = perceptualRoughness * perceptualRoughness; // คำนวณ roughness
            roughness = max(roughness, 0.001f); // ป้องกันค่า 0

            int currentMipResolution = this.resolution >> mipmapLevelInt; // คำนวณขนาด mipmap
            if (currentMipResolution == 0) currentMipResolution = 1; // ป้องกัน 0
            float log4omegaP = log4((4.0f * PI_F) / (6 * currentMipResolution * currentMipResolution)); // คำนวณ log4omegaP
            float inverseNumberOfSamples = 1f / numberOfImportanceSamples; // คำนวณ 1/N

            ArrayList<ImportanceSampleCacheEntry> cache = new ArrayList<>(numberOfImportanceSamples); // สร้าง list สำหรับ cache
            float totalWeight = 0f; // น้ำหนักรวม

            for (int sampleIndex = 0; sampleIndex < numberOfImportanceSamples; ++sampleIndex) { // วนลูปทุก sample
                float[] u = hammersley(sampleIndex, inverseNumberOfSamples); // คำนวณ hammersley
                float[] h = hemisphereImportanceSampleDggx(u, roughness); // คำนวณ hemisphere sample
                float noh = h[2]; // ค่า z
                if (noh <= 0) continue; // ข้ามถ้า noh <= 0
                float noh2 = noh * noh; // noh^2
                float nol = 2f * noh2 - 1f; // nol

                if (nol > 0) {
                    ImportanceSampleCacheEntry entry = new ImportanceSampleCacheEntry(); // สร้าง entry
                    entry.direction = new float[] {2f * noh * h[0], 2 * noh * h[1], nol}; // ทิศทาง

                    float pdf = distributionGgx(noh, roughness) * noh; // คำนวณ pdf
                    if (pdf <= 1e-6f) continue; // ข้ามถ้า pdf ต่ำมาก

                    float calculatedMipLevel = 0.0f; // **แก้ไข:** ใช้ชื่อตัวแปรใหม่
                    if (numberOfImportanceSamples > 0) {
                        float omegaS = 1.0f / (numberOfImportanceSamples * pdf); // คำนวณ omegaS
                        float omegaP = (4.0f * PI_F) / (6.0f * currentMipResolution * currentMipResolution); // คำนวณ omegaP
                         if (omegaP > 1e-8f && omegaS / omegaP > 0) { // Avoid invalid log input
                             calculatedMipLevel = 0.5f * log2(omegaS / omegaP); // คำนวณ mip level
                         }
                    }

                    // **แก้ไข:** ใช้ calculatedMipLevel
                    entry.level = max(0.0f, min(calculatedMipLevel, (float) (numberOfMipmapLevels - 1))); // จำกัดค่าให้อยู่ในช่วง
                    entry.contribution = nol; // น้ำหนัก
                    cache.add(entry); // เพิ่ม entry
                    totalWeight += entry.contribution; // รวม weight
                }
            }
            if (totalWeight > 0) { for (ImportanceSampleCacheEntry entry : cache) { entry.contribution /= totalWeight; } } // normalize weight
            result[i] = new ImportanceSampleCacheEntry[cache.size()]; // สร้าง array
            cache.toArray(result[i]); // แปลง list เป็น array
        }
        return result; // คืนค่า cache
    }

    // Static Math helpers... (log2, log4, sqrt, sin, cos, hammersley, hemisphereImportanceSampleDggx, distributionGgx)
    private static int getMaxColorAttachments() { /* ... as before ... */
        int[] result = new int[1]; GLES30.glGetIntegerv(GLES30.GL_MAX_COLOR_ATTACHMENTS, result, 0); // ดึงจำนวน color attachment สูงสุด
        GLError.maybeThrowGLException("Failed to get max color attachments", "glGetIntegerv"); return min(result[0], 8); } // จำกัดไม่เกิน 8
    private static final float PI_F = (float) Math.PI; // ค่าคงที่ PI
    private static int log2(int value) { if (value <= 0) return 0; return 31 - Integer.numberOfLeadingZeros(value); } // log2 สำหรับ int
    private static float log2(float value) { if (value <= 0) return -Float.MAX_VALUE; return (float)(Math.log(value) / Math.log(2.0)); } // Overload for float log2
    private static float log4(float value) { if (value <= 0) return -Float.MAX_VALUE; return (float) (Math.log(value) / Math.log(4.0)); } // log4
    private static float sqrt(float value) { if (value < 0) return 0; return (float) Math.sqrt(value); } // sqrt
    private static float sin(float value) { return (float) Math.sin(value); } // sin
    private static float cos(float value) { return (float) Math.cos(value); } // cos
    private static float[] hammersley(int i, float iN) { /* ... as before ... */
        float tof = 0.5f / 0x80000000L; long bits = i;
        bits = (bits << 16) | (bits >>> 16); bits = ((bits & 0x55555555L) << 1) | ((bits & 0xAAAAAAAAL) >>> 1);
        bits = ((bits & 0x33333333L) << 2) | ((bits & 0xCCCCCCCCL) >>> 2); bits = ((bits & 0x0F0F0F0FL) << 4) | ((bits & 0xF0F0F0F0L) >>> 4);
        bits = ((bits & 0x00FF00FFL) << 8) | ((bits & 0xFF00FF00L) >>> 8); return new float[] {i * iN, bits * tof}; }
    private static float[] hemisphereImportanceSampleDggx(float[] u, float a) { /* ... as before ... */
        float a2 = a*a; float phi = 2.0f * PI_F * u[0]; float cosTheta2 = (1f - u[1]) / (1f + (a2 - 1f) * u[1]);
        float cosTheta = sqrt(cosTheta2); float sinTheta = sqrt(1f - cosTheta2);
        return new float[] {sinTheta * cos(phi), sinTheta * sin(phi), cosTheta}; }
    private static float distributionGgx(float noh, float a) { /* ... as before ... */
        float a2 = a*a; float noh2 = noh*noh; float den = noh2 * (a2 - 1f) + 1f;
        if (den * den < 1e-8f) return Float.MAX_VALUE; // Avoid division by zero, return large value? Check convention
        return a2 / (PI_F * den * den); }

} // End class SpecularCubemapFilter