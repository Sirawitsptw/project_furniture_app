/*
 * Copyright 2020 Google LLC
 * ... (license header) ...
 */
package com.example.project_furnitureapp.samplerender; // ตรวจสอบ package name

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.res.AssetManager;
import android.opengl.GLES30;
import android.opengl.GLException;
import android.util.Log;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Represents a GPU shader, the state of its associated uniforms, and some additional draw state.
 */
public class Shader implements Closeable {
    private static final String TAG = Shader.class.getSimpleName();

    /** BlendFactor enum (เหมือนเดิม) */
    public static enum BlendFactor {
        ZERO(GLES30.GL_ZERO), ONE(GLES30.GL_ONE), SRC_COLOR(GLES30.GL_SRC_COLOR),
        ONE_MINUS_SRC_COLOR(GLES30.GL_ONE_MINUS_SRC_COLOR), DST_COLOR(GLES30.GL_DST_COLOR),
        ONE_MINUS_DST_COLOR(GLES30.GL_ONE_MINUS_DST_COLOR), SRC_ALPHA(GLES30.GL_SRC_ALPHA),
        ONE_MINUS_SRC_ALPHA(GLES30.GL_ONE_MINUS_SRC_ALPHA), DST_ALPHA(GLES30.GL_DST_ALPHA),
        ONE_MINUS_DST_ALPHA(GLES30.GL_ONE_MINUS_DST_ALPHA), CONSTANT_COLOR(GLES30.GL_CONSTANT_COLOR),
        ONE_MINUS_CONSTANT_COLOR(GLES30.GL_ONE_MINUS_CONSTANT_COLOR), CONSTANT_ALPHA(GLES30.GL_CONSTANT_ALPHA),
        ONE_MINUS_CONSTANT_ALPHA(GLES30.GL_ONE_MINUS_CONSTANT_ALPHA);
        /* package-private */ final int glesEnum;
        private BlendFactor(int glesEnum) { this.glesEnum = glesEnum; }
    }

    private int programId = 0;
    private final Map<Integer, Uniform> uniforms = new HashMap<>();
    private int maxTextureUnit = 0;

    // Cache for uniform/attribute locations
    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private final Map<Integer, String> uniformNames = new HashMap<>();
    // **** เพิ่ม Cache สำหรับ Attribute Location ****
    private final Map<String, Integer> attributeLocations = new HashMap<>();


    // Draw state flags (เหมือนเดิม)
    private boolean depthTest = true;
    private boolean depthWrite = true;
    private boolean cullFace = true;
    private BlendFactor sourceRgbBlend = BlendFactor.ONE;
    private BlendFactor destRgbBlend = BlendFactor.ZERO;
    private BlendFactor sourceAlphaBlend = BlendFactor.ONE;
    private BlendFactor destAlphaBlend = BlendFactor.ZERO;

    /** Constructor (เหมือนเดิม) */
    public Shader(
            SampleRender render,
            String vertexShaderCode,
            String fragmentShaderCode,
            Map<String, String> defines) {
        // ... (โค้ดสร้าง shader program เหมือนเดิม) ...
        int vertexShaderId = 0; int fragmentShaderId = 0;
        String definesCode = createShaderDefinesCode(defines);
        try {
            vertexShaderId = createShader(GLES30.GL_VERTEX_SHADER, insertShaderDefinesCode(vertexShaderCode, definesCode));
            fragmentShaderId = createShader(GLES30.GL_FRAGMENT_SHADER, insertShaderDefinesCode(fragmentShaderCode, definesCode));
            programId = GLES30.glCreateProgram(); GLError.maybeThrowGLException("Shader program creation failed", "glCreateProgram");
            GLES30.glAttachShader(programId, vertexShaderId); GLError.maybeThrowGLException("Failed to attach vertex shader", "glAttachShader");
            GLES30.glAttachShader(programId, fragmentShaderId); GLError.maybeThrowGLException("Failed to attach fragment shader", "glAttachShader");
            GLES30.glLinkProgram(programId); GLError.maybeThrowGLException("Failed to link shader program", "glLinkProgram");
            final int[] linkStatus = new int[1]; GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == GLES30.GL_FALSE) { String infoLog = GLES30.glGetProgramInfoLog(programId); GLError.maybeLogGLError(Log.WARN, TAG, "Failed shader info log", "glGetProgramInfoLog"); throw new GLException(0, "Shader link failed: " + infoLog); }
        } catch (Throwable t) { close(); throw t; }
        finally {
            if (vertexShaderId != 0) { GLES30.glDeleteShader(vertexShaderId); GLError.maybeLogGLError(Log.WARN, TAG, "Failed free vertex shader", "glDeleteShader"); }
            if (fragmentShaderId != 0) { GLES30.glDeleteShader(fragmentShaderId); GLError.maybeLogGLError(Log.WARN, TAG, "Failed free fragment shader", "glDeleteShader"); }
        }
    }

    /** createFromAssets (เหมือนเดิม) */
    public static Shader createFromAssets(
            SampleRender render, String vertexShaderFileName, String fragmentShaderFileName, Map<String, String> defines)
            throws IOException {
        AssetManager assets = render.getAssets();
        return new Shader(render, inputStreamToString(assets.open(vertexShaderFileName)), inputStreamToString(assets.open(fragmentShaderFileName)), defines);
    }

    @Override public void close() { /* ... เหมือนเดิม ... */
        if (programId != 0) { GLES30.glDeleteProgram(programId); programId = 0; }
    }

    // --- Setters for draw state (เหมือนเดิม) ---
    public Shader setDepthTest(boolean depthTest) { this.depthTest = depthTest; return this; }
    public Shader setDepthWrite(boolean depthWrite) { this.depthWrite = depthWrite; return this; }
    public Shader setCullFace(boolean cullFace) { this.cullFace = cullFace; return this; }
    public Shader setBlend(BlendFactor sourceBlend, BlendFactor destBlend) { /* ... เหมือนเดิม ... */
        this.sourceRgbBlend = sourceBlend; this.destRgbBlend = destBlend; this.sourceAlphaBlend = sourceBlend; this.destAlphaBlend = destBlend; return this;
    }
    public Shader setBlend(BlendFactor sourceRgbBlend, BlendFactor destRgbBlend, BlendFactor sourceAlphaBlend, BlendFactor destAlphaBlend) { /* ... เหมือนเดิม ... */
        this.sourceRgbBlend = sourceRgbBlend; this.destRgbBlend = destRgbBlend; this.sourceAlphaBlend = sourceAlphaBlend; this.destAlphaBlend = destAlphaBlend; return this;
    }

    // --- Setters for uniforms (เหมือนเดิม) ---
    public Shader setTexture(String name, Texture texture) { /* ... เหมือนเดิม ... */
        int location = getUniformLocation(name); Uniform uniform = uniforms.get(location); int textureUnit;
        if (!(uniform instanceof UniformTexture)) { textureUnit = maxTextureUnit++; } else { textureUnit = ((UniformTexture) uniform).getTextureUnit(); }
        uniforms.put(location, new UniformTexture(textureUnit, texture)); return this;
    }
    public Shader setBool(String name, boolean v0) { int[] v={v0?1:0}; uniforms.put(getUniformLocation(name), new UniformInt(v)); return this; }
    public Shader setInt(String name, int v0) { int[] v={v0}; uniforms.put(getUniformLocation(name), new UniformInt(v)); return this; }
    public Shader setFloat(String name, float v0) { float[] v={v0}; uniforms.put(getUniformLocation(name), new Uniform1f(v)); return this; }
    public Shader setVec2(String name, float[] v) { if(v.length!=2) throw new IAEx("len!=2"); uniforms.put(getUniformLocation(name), new Uniform2f(v.clone())); return this; }
    public Shader setVec3(String name, float[] v) { if(v.length!=3) throw new IAEx("len!=3"); uniforms.put(getUniformLocation(name), new Uniform3f(v.clone())); return this; }
    public Shader setVec4(String name, float[] v) { if(v.length!=4) throw new IAEx("len!=4"); uniforms.put(getUniformLocation(name), new Uniform4f(v.clone())); return this; }
    public Shader setMat2(String name, float[] v) { if(v.length!=4) throw new IAEx("len!=4"); uniforms.put(getUniformLocation(name), new UniformMatrix2f(v.clone())); return this; }
    public Shader setMat3(String name, float[] v) { if(v.length!=9) throw new IAEx("len!=9"); uniforms.put(getUniformLocation(name), new UniformMatrix3f(v.clone())); return this; }
    public Shader setMat4(String name, float[] v) { if(v.length!=16) throw new IAEx("len!=16"); uniforms.put(getUniformLocation(name), new UniformMatrix4f(v.clone())); return this; }
    // Array setters... (เหมือนเดิม)
    public Shader setBoolArray(String name, boolean[] v) { int[] iv=new int[v.length]; for(int i=0;i<v.length;++i)iv[i]=v[i]?1:0; uniforms.put(getUniformLocation(name), new UniformInt(iv)); return this; }
    public Shader setIntArray(String name, int[] v) { uniforms.put(getUniformLocation(name), new UniformInt(v.clone())); return this; }
    public Shader setFloatArray(String name, float[] v) { uniforms.put(getUniformLocation(name), new Uniform1f(v.clone())); return this; }
    public Shader setVec2Array(String name, float[] v) { if(v.length%2!=0) throw new IAEx("len%2!=0"); uniforms.put(getUniformLocation(name), new Uniform2f(v.clone())); return this; }
    public Shader setVec3Array(String name, float[] v) { if(v.length%3!=0) throw new IAEx("len%3!=0"); uniforms.put(getUniformLocation(name), new Uniform3f(v.clone())); return this; }
    public Shader setVec4Array(String name, float[] v) { if(v.length%4!=0) throw new IAEx("len%4!=0"); uniforms.put(getUniformLocation(name), new Uniform4f(v.clone())); return this; }
    public Shader setMat2Array(String name, float[] v) { if(v.length%4!=0) throw new IAEx("len%4!=0"); uniforms.put(getUniformLocation(name), new UniformMatrix2f(v.clone())); return this; }
    public Shader setMat3Array(String name, float[] v) { if(v.length%9!=0) throw new IAEx("len%9!=0"); uniforms.put(getUniformLocation(name), new UniformMatrix3f(v.clone())); return this; }
    public Shader setMat4Array(String name, float[] v) { if(v.length%16!=0) throw new IAEx("len%16!=0"); uniforms.put(getUniformLocation(name), new UniformMatrix4f(v.clone())); return this; }


    /** lowLevelUse (เหมือนเดิม) */
    public void lowLevelUse() {
        if (programId == 0) throw new IllegalStateException("Freed shader");
        GLES30.glUseProgram(programId); GLError.maybeThrowGLException("Failed use program", "glUseProgram");
        GLES30.glBlendFuncSeparate(sourceRgbBlend.glesEnum, destRgbBlend.glesEnum, sourceAlphaBlend.glesEnum, destAlphaBlend.glesEnum); GLError.maybeThrowGLException("Failed set blend", "glBlendFuncSeparate");
        GLES30.glDepthMask(depthWrite); GLError.maybeThrowGLException("Failed set depth mask", "glDepthMask");
        if (depthTest) { GLES30.glEnable(GLES30.GL_DEPTH_TEST); GLError.maybeThrowGLException("Failed enable depth test", "glEnable"); }
        else { GLES30.glDisable(GLES30.GL_DEPTH_TEST); GLError.maybeThrowGLException("Failed disable depth test", "glDisable"); }
        if (cullFace) { GLES30.glEnable(GLES30.GL_CULL_FACE); GLError.maybeThrowGLException("Failed enable cull face", "glEnable"); }
        else { GLES30.glDisable(GLES30.GL_CULL_FACE); GLError.maybeThrowGLException("Failed disable cull face", "glDisable"); }
        try {
            ArrayList<Integer> obsoleteEntries = new ArrayList<>(uniforms.size());
            for (Map.Entry<Integer, Uniform> entry : uniforms.entrySet()) {
                try { entry.getValue().use(entry.getKey()); if (!(entry.getValue() instanceof UniformTexture)) obsoleteEntries.add(entry.getKey()); }
                catch (GLException e) { String name = uniformNames.get(entry.getKey()); throw new IllegalArgumentException("Error setting uniform `" + name + "'", e); }
            }
            uniforms.keySet().removeAll(obsoleteEntries);
        } finally { GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLError.maybeLogGLError(Log.WARN, TAG, "Failed set active texture", "glActiveTexture"); }
    }

    // --- Uniform Inner Classes (เหมือนเดิม) ---
    private static interface Uniform { void use(int location); }
    private static class UniformTexture implements Uniform {
        private final int textureUnit; private final Texture texture;
        public UniformTexture(int u, Texture t) {this.textureUnit=u; this.texture=t;}
        public int getTextureUnit() {return textureUnit;}
        @Override public void use(int loc) {
            if(texture.getTextureId()==0) throw new IllegalStateException("Freed texture");
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0+textureUnit); GLError.maybeThrowGLException("Failed set active texture", "glActiveTexture");
            // **** แก้ไข: ใช้ค่าคงที่ GLES11Ext แทน .glesEnum ****
            int targetEnum = (texture.getTarget() == Texture.Target.TEXTURE_EXTERNAL_OES) ? GLES11Ext.GL_TEXTURE_EXTERNAL_OES : texture.getTarget().glesEnum;
            GLES30.glBindTexture(targetEnum, texture.getTextureId()); GLError.maybeThrowGLException("Failed bind texture", "glBindTexture");
            GLES30.glUniform1i(loc, textureUnit); GLError.maybeThrowGLException("Failed set uniform1i", "glUniform1i");
        }
    }
    private static class UniformInt implements Uniform { final int[] v; public UniformInt(int[]v){this.v=v;} @Override public void use(int l){GLES30.glUniform1iv(l,v.length,v,0);GLError.maybeThrowGLException("Failed set uniform1iv","glUniform1iv");}}
    private static class Uniform1f implements Uniform { final float[] v; public Uniform1f(float[]v){this.v=v;} @Override public void use(int l){GLES30.glUniform1fv(l,v.length,v,0);GLError.maybeThrowGLException("Failed set uniform1fv","glUniform1fv");}}
    private static class Uniform2f implements Uniform { final float[] v; public Uniform2f(float[]v){this.v=v;} @Override public void use(int l){GLES30.glUniform2fv(l,v.length/2,v,0);GLError.maybeThrowGLException("Failed set uniform2fv","glUniform2fv");}}
    private static class Uniform3f implements Uniform { final float[] v; public Uniform3f(float[]v){this.v=v;} @Override public void use(int l){GLES30.glUniform3fv(l,v.length/3,v,0);GLError.maybeThrowGLException("Failed set uniform3fv","glUniform3fv");}}
    private static class Uniform4f implements Uniform { final float[] v; public Uniform4f(float[]v){this.v=v;} @Override public void use(int l){GLES30.glUniform4fv(l,v.length/4,v,0);GLError.maybeThrowGLException("Failed set uniform4fv","glUniform4fv");}}
    private static class UniformMatrix2f implements Uniform { final float[] v; public UniformMatrix2f(float[]v){this.v=v;} @Override public void use(int l){GLES30.glUniformMatrix2fv(l,v.length/4,false,v,0);GLError.maybeThrowGLException("Failed set uniformMatrix2fv","glUniformMatrix2fv");}}
    private static class UniformMatrix3f implements Uniform { final float[] v; public UniformMatrix3f(float[]v){this.v=v;} @Override public void use(int l){GLES30.glUniformMatrix3fv(l,v.length/9,false,v,0);GLError.maybeThrowGLException("Failed set uniformMatrix3fv","glUniformMatrix3fv");}}
    private static class UniformMatrix4f implements Uniform { final float[] v; public UniformMatrix4f(float[]v){this.v=v;} @Override public void use(int l){GLES30.glUniformMatrix4fv(l,v.length/16,false,v,0);GLError.maybeThrowGLException("Failed set uniformMatrix4fv","glUniformMatrix4fv");}}


    /**
     * Returns the location of a uniform variable. Caches results.
     * Returns -1 if the uniform name is not found.
     */
    // **** แก้ไข: เปลี่ยนเป็น public ****
    public int getUniformLocation(String name) {
        Integer locationObject = uniformLocations.get(name);
        if (locationObject != null) {
            return locationObject;
        }
        int location = GLES30.glGetUniformLocation(programId, name);
        // Don't throw exception here, let caller handle -1 if needed
        // GLError.maybeThrowGLException("Failed to find uniform", "glGetUniformLocation");
        // if (location == -1) {
        //     Log.w(TAG, "Shader uniform not found: " + name); // Log warning instead of throwing
        // }
        uniformLocations.put(name, Integer.valueOf(location));
        if (location != -1) { // Only cache name if found
             uniformNames.put(Integer.valueOf(location), name);
        }
        return location;
    }

    /**
     * Returns the location of an attribute variable. Caches results.
     * Returns -1 if the attribute name is not found.
     */
    // **** เพิ่มเมธอดนี้ ****
    public int getAttributeLocation(String name) {
        Integer locationObject = attributeLocations.get(name);
        if (locationObject != null) {
            return locationObject;
        }
        int location = GLES30.glGetAttribLocation(programId, name);
        // Don't throw exception here, let caller handle -1 if needed
        // GLError.maybeThrowGLException("Failed to find attribute", "glGetAttribLocation");
        // if (location == -1) {
        //     Log.w(TAG, "Shader attribute not found: " + name); // Log warning instead of throwing
        // }
        attributeLocations.put(name, Integer.valueOf(location));
        return location;
    }


    // --- Private static helper methods (เหมือนเดิม) ---
    private static int createShader(int type, String code) { /* ... */
        int shaderId = GLES30.glCreateShader(type); GLError.maybeThrowGLException("Shader creation failed", "glCreateShader");
        GLES30.glShaderSource(shaderId, code); GLError.maybeThrowGLException("Shader source failed", "glShaderSource");
        GLES30.glCompileShader(shaderId); GLError.maybeThrowGLException("Shader compilation failed", "glCompileShader");
        final int[] compileStatus = new int[1]; GLES30.glGetShaderiv(shaderId, GLES30.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == GLES30.GL_FALSE) { String infoLog = GLES30.glGetShaderInfoLog(shaderId); GLError.maybeLogGLError(Log.WARN, TAG, "Failed shader info log", "glGetShaderInfoLog"); GLES30.glDeleteShader(shaderId); GLError.maybeLogGLError(Log.WARN, TAG, "Failed free shader", "glDeleteShader"); throw new GLException(0, "Shader compilation failed: " + infoLog); }
        return shaderId;
    }
    private static String createShaderDefinesCode(Map<String, String> defines) { /* ... */
        if (defines == null) return ""; StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : defines.entrySet()) { b.append("#define " + e.getKey() + " " + e.getValue() + "\n"); } return b.toString();
    }
    private static String insertShaderDefinesCode(String sourceCode, String definesCode) { /* ... */
        String result = sourceCode.replaceAll("(?m)^(\\s*#\\s*version\\s+.*)$", "$1\n" + Matcher.quoteReplacement(definesCode));
        if (result.equals(sourceCode)) { return definesCode + sourceCode; } return result;
    }
    private static String inputStreamToString(InputStream stream) throws IOException { /* ... */
        InputStreamReader reader = new InputStreamReader(stream, UTF_8.name()); char[] buffer = new char[1024 * 4]; StringBuilder builder = new StringBuilder(); int amount;
        while ((amount = reader.read(buffer)) != -1) { builder.append(buffer, 0, amount); } reader.close(); return builder.toString();
    }

    // Helper for IllegalArgumentException
    private static class IAEx extends IllegalArgumentException { public IAEx(String m){super(m);} }

    // **** เพิ่ม Helper class สำหรับ OES Texture constant ****
    private static class GLES11Ext { public static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65; }
}
