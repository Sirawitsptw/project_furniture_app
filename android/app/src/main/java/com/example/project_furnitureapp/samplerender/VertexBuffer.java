package com.example.project_furnitureapp.samplerender; // ประกาศ package ของไฟล์นี้

import android.opengl.GLES30; // import GLES30 สำหรับใช้งาน OpenGL ES 3.0
import java.io.Closeable; // import Closeable สำหรับปิด resource
import java.nio.FloatBuffer; // import FloatBuffer สำหรับเก็บข้อมูล float array
import android.util.Log; // import Log สำหรับเขียน log

public class VertexBuffer implements Closeable { // ประกาศคลาส VertexBuffer และ implement Closeable
    private static final String TAG = "VertexBuffer"; // ตัวแปร TAG สำหรับ log
    private final GpuBuffer buffer; // ตัวแปรเก็บ GpuBuffer (buffer จริงบน GPU)
    private final int numberOfEntriesPerVertex; // จำนวน float ต่อ 1 vertex

    public VertexBuffer(SampleRender render, int numberOfEntriesPerVertex, FloatBuffer entries) { // constructor รับ renderer, จำนวน float ต่อ vertex, และข้อมูล
        if (numberOfEntriesPerVertex <= 0) { // ถ้าจำนวน float ต่อ vertex <= 0
            throw new IllegalArgumentException("Number of entries per vertex must be positive."); // ขว้าง exception
        }
        if (entries != null && entries.limit() % numberOfEntriesPerVertex != 0) { // ถ้า buffer ไม่หารลงตัวกับจำนวน float ต่อ vertex
            throw new IllegalArgumentException("Buffer size mismatch"); // ขว้าง exception
        }
        this.numberOfEntriesPerVertex = numberOfEntriesPerVertex; // กำหนดค่าให้ field
        // สร้าง GpuBuffer โดยบอกขนาดเป็น byte ของ 1 vertex เต็มๆ
        buffer = new GpuBuffer(GLES30.GL_ARRAY_BUFFER, numberOfEntriesPerVertex * GpuBuffer.FLOAT_SIZE, entries); // สร้าง buffer จริงบน GPU
    }

    public void set(FloatBuffer entries) { // เมธอดสำหรับเซ็ตข้อมูลใหม่ลง buffer
        int newVertexCount = 0; // ตัวแปรเก็บจำนวน vertex ใหม่
        if (entries != null && numberOfEntriesPerVertex > 0) { // ถ้ามีข้อมูลและจำนวน float ต่อ vertex > 0
            if (entries.limit() % numberOfEntriesPerVertex != 0) { // ถ้า buffer ไม่หารลงตัว
                Log.e(TAG, "set() - Buffer size mismatch! Limit=" + entries.limit() + ", EntriesPerVertex=" + numberOfEntriesPerVertex); // log error
                buffer.set(null); // Clear buffer on mismatch
                return; // ออก
            }
            newVertexCount = entries.limit() / numberOfEntriesPerVertex; // คำนวณจำนวน vertex ใหม่
        }
        Log.d(TAG, "set() called. entries limit: " + (entries != null ? entries.limit() : "null") + ", newVertexCount: " + newVertexCount); // log
        buffer.set(entries); // Pass buffer to GpuBuffer
        Log.d(TAG, "set() finished. Underlying GpuBuffer size (logical entries): " + buffer.getSize()); // log
    }

    @Override public void close() { buffer.close(); } // เมธอดปิด resource (เรียก close ของ GpuBuffer)
    public int getBufferId() { return buffer.getBufferId(); } // คืนค่า buffer id ของ GpuBuffer
    public int getNumberOfEntriesPerVertex() { return numberOfEntriesPerVertex; } // คืนค่าจำนวน float ต่อ vertex

    /** Returns the total number of vertices in the buffer. */ // คำอธิบายเมธอด
    public int getNumberOfVertices() { // เมธอดคืนค่าจำนวน vertex ทั้งหมด
        // **** แก้ไข: คืนค่า size จาก GpuBuffer โดยตรง ****
        // เพราะ GpuBuffer.getSize() เก็บจำนวน logical entries (vertices) ไว้อยู่แล้ว
        int vertexCount = buffer.getSize(); // ดึงจำนวน vertex จาก GpuBuffer
        Log.d(TAG, "getNumberOfVertices() called. Returning GpuBuffer size (logical entries): " + vertexCount); // log
        return vertexCount; // คืนค่าจำนวน vertex

        // --- โค้ดเดิมที่คำนวณผิด ---
        // int gpuBufferSize = buffer.getSize(); // นี่คือจำนวน logical entries ไม่ใช่จำนวน float ทั้งหมด
        // int calculatedVertices = gpuBufferSize / numberOfEntriesPerVertex; // เอา entries มาหาร entries per vertex อีกที -> ผิด
        // Log.d(TAG, "getNumberOfVertices() called. GpuBuffer size (logical entries): " + gpuBufferSize + ", EntriesPerVertex: " + numberOfEntriesPerVertex + ", Calculated Vertices: " + calculatedVertices);
        // return calculatedVertices;
    }
}
