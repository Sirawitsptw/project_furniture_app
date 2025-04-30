/*
 * Copyright 2020 Google LLC
 * ... (license header) ...
 */
package com.example.project_furnitureapp.samplerender;

import android.opengl.GLES30;
import java.io.Closeable;
import java.nio.IntBuffer; // Uses IntBuffer for indices

/**
 * A list of vertex indices stored GPU-side.
 * ... (javadoc comments) ...
 */
public class IndexBuffer implements Closeable { // Class is already public
    private final GpuBuffer buffer;

    /**
     * Construct an {@link IndexBuffer} populated with initial data.
     * ... (javadoc comments) ...
     */
    public IndexBuffer(SampleRender render, IntBuffer entries) {
        // Use GpuBuffer.INT_SIZE since we are using IntBuffer
        buffer = new GpuBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, GpuBuffer.INT_SIZE, entries);
    }

    /**
     * Populate with new data.
     * ... (javadoc comments) ...
     */
    public void set(IntBuffer entries) {
        buffer.set(entries);
    }

    @Override
    public void close() {
        buffer.close();
    }

    /** Returns the underlying GPU buffer ID. */
    /* package-private */
    int getBufferId() {
        return buffer.getBufferId();
    }

    /** Returns the number of indices in the buffer. */
    // **แก้ไข:** เปลี่ยน visibility เป็น public
    public int getSize() {
        return buffer.getSize();
    }
}