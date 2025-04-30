/*
 * Copyright 2021 Google LLC
 * ... (license header) ...
 */
package com.example.project_furnitureapp.samplerender.arcore;

// Imports... (including samplerender types, GLError, core Image, opengl, Log, nio, graphics.ImageFormat etc.)
import android.media.Image;
import android.media.Image.Plane;
import android.opengl.GLES30;
import android.util.Log;

import com.example.project_furnitureapp.samplerender.Framebuffer;
import com.example.project_furnitureapp.samplerender.GLError;
import com.example.project_furnitureapp.samplerender.Mesh;
import com.example.project_furnitureapp.samplerender.SampleRender;
import com.example.project_furnitureapp.samplerender.Shader;
import com.example.project_furnitureapp.samplerender.Texture;
import com.example.project_furnitureapp.samplerender.VertexBuffer;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import android.graphics.ImageFormat; // Import ImageFormat
import static java.lang.Math.min;
import static java.lang.Math.max;

/**
 * Filters an environmental HDR cubemap into a prefiltered specular cubemap.
 */
public class SpecularCubemapFilter implements Closeable {
    private static final String TAG = SpecularCubemapFilter.class.getSimpleName();

    // Constants...
    private static final int COMPONENTS_PER_VERTEX = 2;
    private static final int NUMBER_OF_VERTICES = 4;
    private static final int FLOAT_SIZE = 4;
    private static final int COORDS_BUFFER_SIZE = COMPONENTS_PER_VERTEX * NUMBER_OF_VERTICES * FLOAT_SIZE;
    private static final int NUMBER_OF_CUBE_FACES = 6;
    private static final int[] CUBEMAP_FACE_ORDER = {
        GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X, GLES30.GL_TEXTURE_CUBE_MAP_NEGATIVE_X,
        GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, GLES30.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y,
        GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, GLES30.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z };
    private static final FloatBuffer COORDS_BUFFER =
            ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();
    static { COORDS_BUFFER.put(new float[] { -1f, -1f, +1f, -1f, -1f, +1f, +1f, +1f }); }
    private static final String[] ATTACHMENT_LOCATION_DEFINES = {
        "PX_LOCATION", "NX_LOCATION", "PY_LOCATION", "NY_LOCATION", "PZ_LOCATION", "NZ_LOCATION", };
    private static final int[] ATTACHMENT_ENUMS = {
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
    private final int resolution;
    private final int numberOfImportanceSamples;
    private final int numberOfMipmapLevels;

    private final Texture radianceCubemap;
    private final Texture ldCubemap; // The filtered result
    private final Shader[] shaders; // One per chunk
    private final Mesh mesh; // Fullscreen quad
    private int[][] framebuffers; // [mipmapLevel][chunk]

    // **แก้ไข:** เพิ่ม field render
    private final SampleRender render;

    public SpecularCubemapFilter(SampleRender render, int resolution, int numberOfImportanceSamples) throws IOException {
        this.render = render; // **แก้ไข:** เก็บ instance
        this.resolution = resolution;
        this.numberOfImportanceSamples = numberOfImportanceSamples;
        // Calculate mipmap levels based on log2
        this.numberOfMipmapLevels = log2(resolution) + 1;

        try {
            radianceCubemap = new Texture(render, Texture.Target.TEXTURE_CUBE_MAP, Texture.WrapMode.CLAMP_TO_EDGE, true); // Enable mipmaps for radiance
            ldCubemap = new Texture(render, Texture.Target.TEXTURE_CUBE_MAP, Texture.WrapMode.CLAMP_TO_EDGE, true); // Enable mipmaps for result

            ChunkIterable chunks = new ChunkIterable(getMaxColorAttachments());
            initializeLdCubemap(); // Initialize storage for result
            shaders = createShaders(render, chunks); // Create shaders needed for filtering
            framebuffers = createFramebuffers(chunks); // Create FBOs for rendering to ldCubemap mips

            VertexBuffer coordsBuffer = new VertexBuffer(render, COMPONENTS_PER_VERTEX, COORDS_BUFFER);
            mesh = new Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, new VertexBuffer[] {coordsBuffer});

        } catch (Throwable t) {
            Log.e(TAG, "Failed during SpecularCubemapFilter initialization", t);
            close();
            throw new RuntimeException("Failed to initialize SpecularCubemapFilter", t);
        }
    }

    @Override
    public void close() {
        Log.d(TAG, "Closing SpecularCubemapFilter resources.");
        if (framebuffers != null) {
            for (int level = 0; level < framebuffers.length; ++level) {
                if (framebuffers[level] != null && framebuffers[level].length > 0) {
                    GLES30.glDeleteFramebuffers(framebuffers[level].length, framebuffers[level], 0);
                }
            }
            framebuffers = null;
        }
        if (radianceCubemap != null) radianceCubemap.close();
        if (ldCubemap != null) ldCubemap.close();
        if (shaders != null) {
            for (Shader shader : shaders) {
                if (shader != null) shader.close();
            }
        }
        // Mesh and vertex buffer are likely managed elsewhere
    }

    /** Updates the radiance cubemap and runs the filter. */
    public void update(Image[] images) {
        // **แก้ไข:** ใช้ค่าคงที่ int สำหรับ format
        final int IMAGE_FORMAT_RGBA_FP16 = 22; // 0x16

        try {
            // --- 1. Update Radiance Cubemap ---
            GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, radianceCubemap.getTextureId());
            GLError.maybeThrowGLException("Failed to bind radiance cubemap texture", "glBindTexture");

            if (images == null || images.length != NUMBER_OF_CUBE_FACES) { /* ... error handling ... */
                throw new IllegalArgumentException("Invalid input images array");
            }

            for (int i = 0; i < NUMBER_OF_CUBE_FACES; ++i) {
                int faceTarget = CUBEMAP_FACE_ORDER[i];
                Image image = images[i];
                if (image == null) throw new IllegalArgumentException("Input image " + i + " is null");
                Plane plane = image.getPlanes()[0];

                // **แก้ไข:** ใช้ค่าคงที่ int
                if (image.getFormat() != IMAGE_FORMAT_RGBA_FP16) { /* ... error handling ... */
                     throw new IllegalArgumentException("Unexpected image format for face " + i + ": " + image.getFormat());
                }
                if (image.getHeight() != image.getWidth() || image.getHeight() != resolution) { /* ... error handling ... */
                    throw new IllegalArgumentException("Invalid image dimensions for face " + i);
                }

                GLES30.glTexImage2D(faceTarget, 0, GLES30.GL_RGBA16F, resolution, resolution, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, plane.getBuffer());
                GLError.maybeThrowGLException("Failed to populate cubemap face " + i, "glTexImage2D");
            }

            // Generate mipmaps for the source radiance cubemap
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_CUBE_MAP);
            GLError.maybeThrowGLException("Failed to generate mipmaps for radiance cubemap", "glGenerateMipmap");

             // Set filtering for radiance map sampling (important for quality)
             GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
             GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);


            // --- 2. Filter into LD Cubemap ---
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
            GLES30.glDepthMask(false);
            GLError.maybeLogGLError(Log.DEBUG, TAG, "Setup before filtering loop", "glDepthMask");

            ChunkIterable chunks = new ChunkIterable(getMaxColorAttachments());

            for (int level = 0; level < numberOfMipmapLevels; ++level) {
                int mipmapResolution = resolution >> level;
                if (mipmapResolution == 0) continue; // Skip if resolution becomes 0

                GLES30.glViewport(0, 0, mipmapResolution, mipmapResolution);
                GLError.maybeLogGLError(Log.DEBUG, TAG, "Viewport set for level " + level, "glViewport");

                for (Chunk chunk : chunks) {
                    // Check if framebuffer exists for this level/chunk
                    if (framebuffers == null || level >= framebuffers.length || chunk.chunkIndex >= framebuffers[level].length) {
                         Log.e(TAG, "Framebuffer array access out of bounds for level " + level + ", chunk " + chunk.chunkIndex);
                         continue; // Skip this chunk if FBO doesn't exist
                    }
                    int fbo = framebuffers[level][chunk.chunkIndex];
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
                    GLError.maybeLogGLError(Log.DEBUG, TAG, "Bound FBO " + fbo + " for level " + level + ", chunk " + chunk.chunkIndex, "glBindFramebuffer");

                    Shader shader = shaders[chunk.chunkIndex];
                    if (shader == null) {
                         Log.e(TAG, "Shader is null for chunk " + chunk.chunkIndex);
                         continue; // Skip if shader doesn't exist
                    }
                    shader.lowLevelUse();
                    GLError.maybeLogGLError(Log.DEBUG, TAG, "Used shader for chunk " + chunk.chunkIndex, "glUseProgram");

                    float perceptualRoughness = (float) level / (float) max(1, numberOfMipmapLevels - 1); // Avoid division by zero
                    float roughness = perceptualRoughness * perceptualRoughness;
                    shader.setFloat("u_Roughness", roughness);
                    GLError.maybeLogGLError(Log.DEBUG, TAG, "Set roughness for level " + level, "glUniform1f");

                    // **แก้ไข:** ใช้ this.render
                    this.render.draw(mesh, shader);
                    GLError.maybeLogGLError(Log.DEBUG, TAG, "Drew mesh for level " + level + ", chunk " + chunk.chunkIndex, "glDrawArrays");
                }
            }

            // Restore state
            // Note: Viewport should be reset by the calling renderer (e.g., ArMeasureView.onSurfaceChanged)
            GLES30.glDepthMask(true);
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
            GLError.maybeLogGLError(Log.DEBUG, TAG, "Restored GL state after filtering", "glBindFramebuffer");

        } catch (Throwable t) {
            Log.e(TAG, "Error during cubemap update and filtering", t);
            // Handle error appropriately
        } finally {
            if (images != null) {
                for (Image image : images) {
                    if (image != null) image.close();
                }
            }
        }
    }


    public int getNumberOfMipmapLevels() { return numberOfMipmapLevels; }
    public Texture getFilteredCubemapTexture() { return ldCubemap; }

    // --- Private Helper Methods ---

    private void initializeLdCubemap() { /* ... as before, ensure parameters set ... */
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, ldCubemap.getTextureId());
        GLError.maybeThrowGLException("Could not bind LD cubemap texture", "glBindTexture");
        for (int level = 0; level < numberOfMipmapLevels; ++level) {
            int mipmapResolution = resolution >> level;
             if (mipmapResolution == 0) mipmapResolution = 1; // Min size 1x1
            for (int face = 0; face < NUMBER_OF_CUBE_FACES; ++face) {
                 int faceTarget = CUBEMAP_FACE_ORDER[face];
                GLES30.glTexImage2D(faceTarget, level, GLES30.GL_RGBA16F, mipmapResolution, mipmapResolution, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null);
                GLError.maybeThrowGLException("Could not initialize LD cubemap mipmap level " + level + ", face " + face, "glTexImage2D");
            }
        }
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE);
        GLError.maybeLogGLError(Log.DEBUG, TAG, "Set LD Cubemap texture parameters", "glTexParameteri");
    }

    private Shader[] createShaders(SampleRender render, ChunkIterable chunks) throws IOException { /* ... as before ... */
        ImportanceSampleCacheEntry[][] importanceSampleCaches = generateImportanceSampleCaches();
        HashMap<String, String> commonDefines = new HashMap<>();
        commonDefines.put("NUMBER_OF_IMPORTANCE_SAMPLES", Integer.toString(numberOfImportanceSamples));
        commonDefines.put("NUMBER_OF_MIPMAP_LEVELS", Integer.toString(numberOfMipmapLevels));
        Shader[] createdShaders = new Shader[chunks.numberOfChunks]; // Use different name
        try {
            for (Chunk chunk : chunks) {
                HashMap<String, String> defines = new HashMap<>(commonDefines);
                for (int location = 0; location < chunk.chunkSize; ++location) {
                    defines.put(ATTACHMENT_LOCATION_DEFINES[chunk.firstFaceIndex + location], Integer.toString(location));
                }
                createdShaders[chunk.chunkIndex] = Shader.createFromAssets(render, "shaders/cubemap_filter.vert", "shaders/cubemap_filter.frag", defines)
                            .setTexture("u_Cubemap", radianceCubemap)
                            .setDepthTest(false).setDepthWrite(false);
                // Set uniforms after creation
                Shader currentShader = createdShaders[chunk.chunkIndex];
                for (int i = 0; i < importanceSampleCaches.length; ++i) {
                    ImportanceSampleCacheEntry[] cache = importanceSampleCaches[i];
                    String cacheName = "u_ImportanceSampleCaches[" + i + "]";
                    currentShader.setInt(cacheName + ".number_of_entries", cache.length);
                    for (int j = 0; j < cache.length; ++j) {
                        ImportanceSampleCacheEntry entry = cache[j];
                        String entryName = cacheName + ".entries[" + j + "]";
                        currentShader.setVec3(entryName + ".direction", entry.direction)
                                     .setFloat(entryName + ".contribution", entry.contribution)
                                     .setFloat(entryName + ".level", entry.level);
                    }
                }
                 GLError.maybeLogGLError(Log.DEBUG, TAG, "Finished setting uniforms for shader chunk " + chunk.chunkIndex, "glUniform*");
            }
        } catch (IOException | RuntimeException e) {
             for (Shader s : createdShaders) { if (s != null) s.close(); } throw e;
        }
        return createdShaders;
    }

    private int[][] createFramebuffers(ChunkIterable chunks) { /* ... as before, ensure GL checks ... */
        int[][] createdFramebuffers = new int[numberOfMipmapLevels][chunks.numberOfChunks];
        try {
            for (int level = 0; level < numberOfMipmapLevels; ++level) {
                GLES30.glGenFramebuffers(chunks.numberOfChunks, createdFramebuffers[level], 0);
                GLError.maybeThrowGLException("Could not create framebuffers level " + level, "glGenFramebuffers");
                for (Chunk chunk : chunks) {
                    int fbo = createdFramebuffers[level][chunk.chunkIndex];
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
                    GLError.maybeThrowGLException("Could not bind FBO " + fbo, "glBindFramebuffer");
                    if (chunk.chunkSize > 1) { /* set glDrawBuffers */
                        int[] drawBuffers = new int[chunk.chunkSize];
                        for(int i=0; i<chunk.chunkSize; ++i) drawBuffers[i] = ATTACHMENT_ENUMS[i];
                        GLES30.glDrawBuffers(chunk.chunkSize, drawBuffers, 0);
                        GLError.maybeThrowGLException("Could not set draw buffers FBO " + fbo, "glDrawBuffers");
                    }
                    for (int attachment = 0; attachment < chunk.chunkSize; ++attachment) {
                        int faceIndex = chunk.firstFaceIndex + attachment;
                        int faceTarget = CUBEMAP_FACE_ORDER[faceIndex];
                        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, ATTACHMENT_ENUMS[attachment], faceTarget, ldCubemap.getTextureId(), level);
                        GLError.maybeThrowGLException("Could not attach texture face "+faceIndex+" level "+level, "glFramebufferTexture2D");
                    }
                    int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
                    if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) { throw new RuntimeException("FBO incomplete: 0x" + Integer.toHexString(status)); }
                }
            }
        } catch(RuntimeException e) {
             // Clean up partially created FBOs
             if (createdFramebuffers != null) {
                 for(int lvl=0; lvl<createdFramebuffers.length; ++lvl) {
                     if (createdFramebuffers[lvl] != null && createdFramebuffers[lvl].length > 0) {
                         GLES30.glDeleteFramebuffers(createdFramebuffers[lvl].length, createdFramebuffers[lvl], 0);
                     }
                 }
             }
             throw e; // Re-throw
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        }
        return createdFramebuffers;
    }

    private ImportanceSampleCacheEntry[][] generateImportanceSampleCaches() { /* ... logic as before, with float LOD calculation fix ... */
        ImportanceSampleCacheEntry[][] result = new ImportanceSampleCacheEntry[numberOfMipmapLevels - 1][];
        for (int i = 0; i < numberOfMipmapLevels - 1; ++i) {
            int mipmapLevelInt = i + 1; // Use integer loop variable name
            float perceptualRoughness = (float) mipmapLevelInt / (float) max(1, numberOfMipmapLevels - 1);
            float roughness = perceptualRoughness * perceptualRoughness;
            roughness = max(roughness, 0.001f);

            int currentMipResolution = this.resolution >> mipmapLevelInt;
            if (currentMipResolution == 0) currentMipResolution = 1;
            float log4omegaP = log4((4.0f * PI_F) / (6 * currentMipResolution * currentMipResolution));
            float inverseNumberOfSamples = 1f / numberOfImportanceSamples;

            ArrayList<ImportanceSampleCacheEntry> cache = new ArrayList<>(numberOfImportanceSamples);
            float totalWeight = 0f;

            for (int sampleIndex = 0; sampleIndex < numberOfImportanceSamples; ++sampleIndex) {
                float[] u = hammersley(sampleIndex, inverseNumberOfSamples);
                float[] h = hemisphereImportanceSampleDggx(u, roughness);
                float noh = h[2];
                if (noh <= 0) continue;
                float noh2 = noh * noh;
                float nol = 2f * noh2 - 1f;

                if (nol > 0) {
                    ImportanceSampleCacheEntry entry = new ImportanceSampleCacheEntry();
                    entry.direction = new float[] {2f * noh * h[0], 2 * noh * h[1], nol};

                    float pdf = distributionGgx(noh, roughness) * noh;
                    if (pdf <= 1e-6f) continue;

                    float calculatedMipLevel = 0.0f; // **แก้ไข:** ใช้ชื่อตัวแปรใหม่
                    if (numberOfImportanceSamples > 0) {
                        float omegaS = 1.0f / (numberOfImportanceSamples * pdf);
                        float omegaP = (4.0f * PI_F) / (6.0f * currentMipResolution * currentMipResolution);
                         if (omegaP > 1e-8f && omegaS / omegaP > 0) { // Avoid invalid log input
                             calculatedMipLevel = 0.5f * log2(omegaS / omegaP);
                         }
                    }

                    // **แก้ไข:** ใช้ calculatedMipLevel
                    entry.level = max(0.0f, min(calculatedMipLevel, (float) (numberOfMipmapLevels - 1)));
                    entry.contribution = nol;
                    cache.add(entry);
                    totalWeight += entry.contribution;
                }
            }
            if (totalWeight > 0) { for (ImportanceSampleCacheEntry entry : cache) { entry.contribution /= totalWeight; } }
            result[i] = new ImportanceSampleCacheEntry[cache.size()];
            cache.toArray(result[i]);
        }
        return result;
    }

    // Static Math helpers... (log2, log4, sqrt, sin, cos, hammersley, hemisphereImportanceSampleDggx, distributionGgx)
    private static int getMaxColorAttachments() { /* ... as before ... */
        int[] result = new int[1]; GLES30.glGetIntegerv(GLES30.GL_MAX_COLOR_ATTACHMENTS, result, 0);
        GLError.maybeThrowGLException("Failed to get max color attachments", "glGetIntegerv"); return min(result[0], 8); }
    private static final float PI_F = (float) Math.PI;
    private static int log2(int value) { if (value <= 0) return 0; return 31 - Integer.numberOfLeadingZeros(value); }
    private static float log2(float value) { if (value <= 0) return -Float.MAX_VALUE; return (float)(Math.log(value) / Math.log(2.0)); } // Overload for float log2
    private static float log4(float value) { if (value <= 0) return -Float.MAX_VALUE; return (float) (Math.log(value) / Math.log(4.0)); }
    private static float sqrt(float value) { if (value < 0) return 0; return (float) Math.sqrt(value); }
    private static float sin(float value) { return (float) Math.sin(value); }
    private static float cos(float value) { return (float) Math.cos(value); }
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