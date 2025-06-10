/*
 * Copyright 2020 Google LLC // ข้อความลิขสิทธิ์ของ Google
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.project_furnitureapp.samplerender; // ประกาศ package ของไฟล์นี้

import android.opengl.GLES30; // import GLES30 สำหรับใช้งาน OpenGL ES 3.0
import android.util.Log; // import Log สำหรับเขียน log
import java.io.Closeable; // import Closeable สำหรับปิด resource
import java.nio.Buffer; // import Buffer สำหรับจัดการ buffer ทั่วไป
import java.nio.ByteBuffer; // import ByteBuffer สำหรับ buffer แบบ byte
import java.nio.ByteOrder; // import ByteOrder สำหรับกำหนด endian (แม้จะไม่ได้ใช้ตรงๆ)
import java.nio.FloatBuffer; // import FloatBuffer สำหรับ buffer แบบ float
import java.nio.IntBuffer; // import IntBuffer สำหรับ buffer แบบ int
import java.nio.ShortBuffer; // import ShortBuffer สำหรับ buffer แบบ short


/** A buffer of data stored on the GPU. */ // คำอธิบายคลาส: buffer ที่เก็บข้อมูลบน GPU
public class GpuBuffer implements Closeable { // ประกาศคลาส GpuBuffer และ implement Closeable
    private static final String TAG = GpuBuffer.class.getSimpleName(); // ตัวแปร TAG สำหรับ log

    public static final int INT_SIZE = 4; // ขนาดของ int (byte)
    public static final int FLOAT_SIZE = 4; // ขนาดของ float (byte)
    public static final int SHORT_SIZE = 2; // ขนาดของ short (byte)

    private final int target; // ตัวแปรเก็บ target ของ buffer (เช่น GL_ARRAY_BUFFER)
    private final int numberOfBytesPerEntry; // จำนวน byte ต่อ 1 logical entry
    private final int[] bufferId = {0}; // ตัวแปรเก็บ buffer id (array สำหรับ glGenBuffers)
    private int size; // จำนวน logical entries ใน buffer
    private int capacity; // ความจุของ buffer (logical entries)

    public GpuBuffer(int target, int numberOfBytesPerEntry, Buffer entries) { // constructor รับ target, ขนาดต่อ entry, และ buffer ข้อมูล
        if (entries != null) {
            if (!entries.isDirect()) { throw new IllegalArgumentException("Buffer must be direct"); } // ต้องเป็น direct buffer
            if (entries.limit() == 0) { entries = null; } // ถ้า buffer ว่าง ให้เป็น null
        }

        this.target = target; // กำหนด target
        // Ensure bytes per entry is positive to avoid division by zero
        this.numberOfBytesPerEntry = Math.max(1, numberOfBytesPerEntry); // ป้องกันหารศูนย์

        if (entries == null) {
            this.size = 0; // ถ้าไม่มีข้อมูล ขนาดเป็น 0
            this.capacity = 0; // ความจุเป็น 0
        } else {
             // Calculate initial size based on buffer limit and element size
             int elementSize = getBufferElementSize(entries); // ขนาด element ของ buffer
             int totalBytes = entries.limit() * elementSize; // ขนาดรวม (byte)
             if (totalBytes % this.numberOfBytesPerEntry != 0) {
                 // Log a warning if data isn't perfectly divisible, size will be truncated
                 Log.w(TAG, "GpuBuffer init: Buffer total bytes (" + totalBytes
                          + ") not divisible by numberOfBytesPerEntry (" + this.numberOfBytesPerEntry
                          + "). Buffer type: " + entries.getClass().getName()
                          + ". Logical entry count will be truncated.");
             }
             this.size = totalBytes / this.numberOfBytesPerEntry; // จำนวน logical entry
             this.capacity = this.size; // ความจุเริ่มต้นเท่ากับขนาด
        }

        try {
            GLES30.glBindVertexArray(0); // Good practice before buffer ops // unbind VAO ก่อน
            GLError.maybeThrowGLException("Failed to unbind vertex array", "glBindVertexArray");

            GLES30.glGenBuffers(1, bufferId, 0); // Generate ID into bufferId[0] // สร้าง buffer id
            GLError.maybeThrowGLException("Failed to generate buffer", "glGenBuffers");
            if (bufferId[0] == 0) { throw new RuntimeException("glGenBuffers failed to generate a buffer ID."); }

            GLES30.glBindBuffer(target, bufferId[0]); // Bind the new buffer // bind buffer
            GLError.maybeThrowGLException("Failed to bind buffer object (id=" + bufferId[0] + ")", "glBindBuffer");

            // Allocate GPU memory and optionally upload initial data
            int initialAllocationBytes = this.capacity * this.numberOfBytesPerEntry; // ขนาดที่ต้องจอง (byte)
            if (entries != null && initialAllocationBytes > 0) {
                // **** เพิ่ม entries.rewind() ตรงนี้ ****
                entries.rewind(); // รีเซ็ต pointer ของ buffer
                // **** ^^^^^^^^^^^^^^^^^^^^^^^^^^^ ****
                GLES30.glBufferData(target, initialAllocationBytes, entries, GLES30.GL_DYNAMIC_DRAW); // ส่งข้อมูลไป GPU
                GLError.maybeThrowGLException("Failed to populate buffer object", "glBufferData");
            } else {
                // Allocate an empty buffer (or size 0 buffer) if no initial data
                GLES30.glBufferData(target, initialAllocationBytes, null, GLES30.GL_DYNAMIC_DRAW); // จอง buffer ว่าง
                GLError.maybeThrowGLException("Failed to allocate empty buffer object", "glBufferData");
            }

        } catch (Throwable t) {
            close(); // Ensure cleanup on failure // ปิด resource ถ้า error
            Log.e(TAG, "Error during GpuBuffer creation", t);
            // Re-throw as unchecked exception to avoid forcing callers to handle checked exceptions from GL
            throw new RuntimeException("GpuBuffer creation failed", t);
        }
    }

     // Helper to get element size from Buffer type
     private int getBufferElementSize(Buffer buffer) { // เมธอดช่วยคืนค่าขนาด element ของ buffer
         if (buffer instanceof java.nio.FloatBuffer) return FLOAT_SIZE; // ถ้าเป็น float
         if (buffer instanceof java.nio.IntBuffer) return INT_SIZE; // ถ้าเป็น int
         if (buffer instanceof java.nio.ShortBuffer) return SHORT_SIZE; // ถ้าเป็น short
         if (buffer instanceof java.nio.ByteBuffer) return 1; // ถ้าเป็น byte
         throw new IllegalArgumentException("Unsupported buffer type: " + buffer.getClass().getName()); // ถ้าไม่รู้จัก type
     }

    /** Updates the data in the buffer, reallocating if necessary. */ // คำอธิบายเมธอด: อัปเดตข้อมูลใน buffer
    public void set(Buffer entries) { // เมธอดเซ็ตข้อมูลใหม่ลง buffer
        if (entries != null && !entries.isDirect()) { throw new IllegalArgumentException("Buffer must be direct"); } // ต้องเป็น direct buffer

        int newSize; // Number of logical entries // จำนวน logical entry ใหม่
        int newSizeInBytes; // ขนาดใหม่ (byte)
        int elementSize = (entries == null) ? 0 : getBufferElementSize(entries); // ขนาด element

        // Calculate new size based on input buffer
        if (entries == null || entries.limit() == 0 || elementSize == 0) {
            newSize = 0; // ถ้าไม่มีข้อมูล
            newSizeInBytes = 0;
        } else {
             int totalBytes = entries.limit() * elementSize; // ขนาดรวม (byte)
             if (totalBytes % numberOfBytesPerEntry != 0) {
                 throw new IllegalArgumentException("New buffer total bytes ("+totalBytes+") not divisible by numberOfBytesPerEntry ("+numberOfBytesPerEntry+")");
             }
             newSize = totalBytes / numberOfBytesPerEntry; // จำนวน logical entry
             newSizeInBytes = newSize * numberOfBytesPerEntry; // ขนาดใหม่ (byte)
        }

        // Avoid GL calls if size is effectively 0
        // Reallocating with size 0 might be valid to free memory
        // if (newSize == 0) {
        //     size = 0;
        //     // Optionally call glBufferData with size 0?
        //     // GLES30.glBindBuffer(target, bufferId[0]);
        //     // GLES30.glBufferData(target, 0, null, GLES30.GL_DYNAMIC_DRAW);
        //     return;
        // }

        GLES30.glBindBuffer(target, bufferId[0]); // bind buffer
        GLError.maybeThrowGLException("Failed to bind buffer object (id=" + bufferId[0] + ") for set", "glBindBuffer");

        // Rewind buffer before reading data
        if(entries != null) {
            entries.rewind(); // รีเซ็ต pointer ของ buffer
        }

        // Check if reallocation is needed
        if (newSizeInBytes > (capacity * numberOfBytesPerEntry)) {
            // Reallocate with glBufferData
            GLES30.glBufferData(target, newSizeInBytes, entries, GLES30.GL_DYNAMIC_DRAW); // จอง buffer ใหม่
            GLError.maybeThrowGLException("Failed to reallocate buffer object using glBufferData", "glBufferData");
            capacity = newSize; // อัปเดตความจุ
        } else if (newSizeInBytes > 0){
            // Update existing buffer with glBufferSubData
            GLES30.glBufferSubData(target, 0, newSizeInBytes, entries); // อัปเดตข้อมูลใน buffer เดิม
            GLError.maybeThrowGLException("Failed to update buffer object using glBufferSubData", "glBufferSubData");
        } else {
             // Handle case where newSizeInBytes is 0 (e.g., set(null)) - might deallocate
             GLES30.glBufferData(target, 0, null, GLES30.GL_DYNAMIC_DRAW); // จอง buffer ขนาด 0 (ลบข้อมูล)
             GLError.maybeThrowGLException("Failed to clear buffer object using glBufferData", "glBufferData");
             capacity = 0; // Reset capacity if cleared // รีเซ็ตความจุ
        }
        size = newSize; // Update size // อัปเดตขนาด
    }

    /** Frees GPU resources. */ // คำอธิบายเมธอด: ปิด resource และคืน memory GPU
    @Override
    public void close() {
        if (bufferId[0] != 0) {
            GLES30.glDeleteBuffers(1, bufferId, 0); // Pass array directly // ลบ buffer
            GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free buffer object (id=" + bufferId[0] + ")", "glDeleteBuffers");
            bufferId[0] = 0; // Reset ID in array // เซ็ต id เป็น 0
        }
        size = 0; // เซ็ตขนาดเป็น 0
        capacity = 0; // เซ็ตความจุเป็น 0
    }

    // --- Package-Private Getters --- (Can be made public if needed by other packages)
    int getBufferId() { return bufferId[0]; } // คืนค่า buffer id
    int getTarget() { return target; } // คืนค่า target
    /** Returns the number of logical entries currently in the buffer. */ // คืนค่าจำนวน logical entry
    int getSize() { return size; } // คืนค่าขนาด
    /** Returns the size of the data currently in the buffer, in bytes. */ // คืนค่าขนาดข้อมูล (byte)
    int getSizeInBytes() { return size * numberOfBytesPerEntry; } // คืนค่าขนาดข้อมูล (byte)
}