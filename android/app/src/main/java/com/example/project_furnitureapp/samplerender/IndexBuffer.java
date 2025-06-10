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
public class IndexBuffer implements Closeable { // ประกาศคลาส IndexBuffer และ implement Closeable
    private final GpuBuffer buffer; // ตัวแปรเก็บ GpuBuffer (buffer จริงบน GPU)

    /**
     * Construct an {@link IndexBuffer} populated with initial data.
     * ... (javadoc comments) ...
     */
    public IndexBuffer(SampleRender render, IntBuffer entries) { // constructor รับ renderer และข้อมูล index
        // Use GpuBuffer.INT_SIZE since we are using IntBuffer
        buffer = new GpuBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, GpuBuffer.INT_SIZE, entries); // สร้าง buffer จริงบน GPU
    }

    /**
     * Populate with new data.
     * ... (javadoc comments) ...
     */
    public void set(IntBuffer entries) { // เมธอดสำหรับเซ็ตข้อมูลใหม่ลง buffer
        buffer.set(entries); // ส่งข้อมูลใหม่ไปยัง GpuBuffer
    }

    @Override
    public void close() { // เมธอดปิด resource
        buffer.close(); // ปิด GpuBuffer
    }

    /** Returns the underlying GPU buffer ID. */
    /* package-private */
    int getBufferId() { // เมธอดคืนค่า buffer id ของ GpuBuffer
        return buffer.getBufferId(); // คืนค่า buffer id
    }

    /** Returns the number of indices in the buffer. */
    // **แก้ไข:** เปลี่ยน visibility เป็น public
    public int getSize() { // เมธอดคืนค่าจำนวน index ใน buffer
        return buffer.getSize(); // คืนค่าจำนวน index
    }
}