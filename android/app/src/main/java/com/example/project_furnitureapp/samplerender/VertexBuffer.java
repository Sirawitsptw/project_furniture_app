package com.example.project_furnitureapp.samplerender;

import android.opengl.GLES30;
import java.io.Closeable;
import java.nio.FloatBuffer;
import android.util.Log;

public class VertexBuffer implements Closeable {
    private static final String TAG = "VertexBuffer";
    private final GpuBuffer buffer;
    private final int numberOfEntriesPerVertex; // จำนวน float ต่อ 1 vertex

    public VertexBuffer(SampleRender render, int numberOfEntriesPerVertex, FloatBuffer entries) {
        if (numberOfEntriesPerVertex <= 0) {
            throw new IllegalArgumentException("Number of entries per vertex must be positive.");
        }
        if (entries != null && entries.limit() % numberOfEntriesPerVertex != 0) {
            throw new IllegalArgumentException("Buffer size mismatch");
        }
        this.numberOfEntriesPerVertex = numberOfEntriesPerVertex;
        // สร้าง GpuBuffer โดยบอกขนาดเป็น byte ของ 1 vertex เต็มๆ
        buffer = new GpuBuffer(GLES30.GL_ARRAY_BUFFER, numberOfEntriesPerVertex * GpuBuffer.FLOAT_SIZE, entries);
    }

    public void set(FloatBuffer entries) {
        int newVertexCount = 0;
        if (entries != null && numberOfEntriesPerVertex > 0) {
            if (entries.limit() % numberOfEntriesPerVertex != 0) {
                Log.e(TAG, "set() - Buffer size mismatch! Limit=" + entries.limit() + ", EntriesPerVertex=" + numberOfEntriesPerVertex);
                buffer.set(null); // Clear buffer on mismatch
                return;
            }
            newVertexCount = entries.limit() / numberOfEntriesPerVertex;
        }
        Log.d(TAG, "set() called. entries limit: " + (entries != null ? entries.limit() : "null") + ", newVertexCount: " + newVertexCount);
        buffer.set(entries); // Pass buffer to GpuBuffer
        Log.d(TAG, "set() finished. Underlying GpuBuffer size (logical entries): " + buffer.getSize());
    }

    @Override public void close() { buffer.close(); }
    public int getBufferId() { return buffer.getBufferId(); }
    public int getNumberOfEntriesPerVertex() { return numberOfEntriesPerVertex; }

    /** Returns the total number of vertices in the buffer. */
    public int getNumberOfVertices() {
        // **** แก้ไข: คืนค่า size จาก GpuBuffer โดยตรง ****
        // เพราะ GpuBuffer.getSize() เก็บจำนวน logical entries (vertices) ไว้อยู่แล้ว
        int vertexCount = buffer.getSize();
        Log.d(TAG, "getNumberOfVertices() called. Returning GpuBuffer size (logical entries): " + vertexCount);
        return vertexCount;

        // --- โค้ดเดิมที่คำนวณผิด ---
        // int gpuBufferSize = buffer.getSize(); // นี่คือจำนวน logical entries ไม่ใช่จำนวน float ทั้งหมด
        // int calculatedVertices = gpuBufferSize / numberOfEntriesPerVertex; // เอา entries มาหาร entries per vertex อีกที -> ผิด
        // Log.d(TAG, "getNumberOfVertices() called. GpuBuffer size (logical entries): " + gpuBufferSize + ", EntriesPerVertex: " + numberOfEntriesPerVertex + ", Calculated Vertices: " + calculatedVertices);
        // return calculatedVertices;
    }
}
