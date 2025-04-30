/*
 * Copyright 2020 Google LLC
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
package com.example.project_furnitureapp.samplerender;

import android.opengl.GLES30;
import android.util.Log;
import java.io.Closeable;
import java.nio.Buffer;
import java.nio.ByteBuffer; // Import needed for helper
import java.nio.ByteOrder; // Import needed for helper (though not used directly now)
import java.nio.FloatBuffer; // Import needed for helper
import java.nio.IntBuffer; // Import needed for helper
import java.nio.ShortBuffer; // Import needed for helper


/** A buffer of data stored on the GPU. */
public class GpuBuffer implements Closeable {
    private static final String TAG = GpuBuffer.class.getSimpleName();

    public static final int INT_SIZE = 4;
    public static final int FLOAT_SIZE = 4;
    public static final int SHORT_SIZE = 2;

    private final int target; // e.g., GLES30.GL_ARRAY_BUFFER, GLES30.GL_ELEMENT_ARRAY_BUFFER
    private final int numberOfBytesPerEntry; // Total bytes for one logical entry
    private final int[] bufferId = {0}; // Use array for glGenBuffers output
    private int size; // Number of logical entries
    private int capacity; // Capacity in number of logical entries

    public GpuBuffer(int target, int numberOfBytesPerEntry, Buffer entries) {
        if (entries != null) {
            if (!entries.isDirect()) { throw new IllegalArgumentException("Buffer must be direct"); }
            if (entries.limit() == 0) { entries = null; } // Treat empty buffer as null for init
        }

        this.target = target;
        // Ensure bytes per entry is positive to avoid division by zero
        this.numberOfBytesPerEntry = Math.max(1, numberOfBytesPerEntry);

        if (entries == null) {
            this.size = 0;
            this.capacity = 0;
        } else {
             // Calculate initial size based on buffer limit and element size
             int elementSize = getBufferElementSize(entries);
             int totalBytes = entries.limit() * elementSize;
             if (totalBytes % this.numberOfBytesPerEntry != 0) {
                 // Log a warning if data isn't perfectly divisible, size will be truncated
                 Log.w(TAG, "GpuBuffer init: Buffer total bytes (" + totalBytes
                          + ") not divisible by numberOfBytesPerEntry (" + this.numberOfBytesPerEntry
                          + "). Buffer type: " + entries.getClass().getName()
                          + ". Logical entry count will be truncated.");
             }
             this.size = totalBytes / this.numberOfBytesPerEntry; // Calculate logical entry count
             this.capacity = this.size; // Initial capacity matches initial size
        }

        try {
            GLES30.glBindVertexArray(0); // Good practice before buffer ops
            GLError.maybeThrowGLException("Failed to unbind vertex array", "glBindVertexArray");

            GLES30.glGenBuffers(1, bufferId, 0); // Generate ID into bufferId[0]
            GLError.maybeThrowGLException("Failed to generate buffer", "glGenBuffers");
            if (bufferId[0] == 0) { throw new RuntimeException("glGenBuffers failed to generate a buffer ID."); }

            GLES30.glBindBuffer(target, bufferId[0]); // Bind the new buffer
            GLError.maybeThrowGLException("Failed to bind buffer object (id=" + bufferId[0] + ")", "glBindBuffer");

            // Allocate GPU memory and optionally upload initial data
            int initialAllocationBytes = this.capacity * this.numberOfBytesPerEntry;
            if (entries != null && initialAllocationBytes > 0) {
                // **** เพิ่ม entries.rewind() ตรงนี้ ****
                entries.rewind();
                // **** ^^^^^^^^^^^^^^^^^^^^^^^^^^^ ****
                GLES30.glBufferData(target, initialAllocationBytes, entries, GLES30.GL_DYNAMIC_DRAW);
                GLError.maybeThrowGLException("Failed to populate buffer object", "glBufferData");
            } else {
                // Allocate an empty buffer (or size 0 buffer) if no initial data
                GLES30.glBufferData(target, initialAllocationBytes, null, GLES30.GL_DYNAMIC_DRAW);
                GLError.maybeThrowGLException("Failed to allocate empty buffer object", "glBufferData");
            }

        } catch (Throwable t) {
            close(); // Ensure cleanup on failure
            Log.e(TAG, "Error during GpuBuffer creation", t);
            // Re-throw as unchecked exception to avoid forcing callers to handle checked exceptions from GL
            throw new RuntimeException("GpuBuffer creation failed", t);
        }
    }

     // Helper to get element size from Buffer type
     private int getBufferElementSize(Buffer buffer) {
         if (buffer instanceof java.nio.FloatBuffer) return FLOAT_SIZE;
         if (buffer instanceof java.nio.IntBuffer) return INT_SIZE;
         if (buffer instanceof java.nio.ShortBuffer) return SHORT_SIZE;
         if (buffer instanceof java.nio.ByteBuffer) return 1;
         throw new IllegalArgumentException("Unsupported buffer type: " + buffer.getClass().getName());
     }

    /** Updates the data in the buffer, reallocating if necessary. */
    public void set(Buffer entries) {
        if (entries != null && !entries.isDirect()) { throw new IllegalArgumentException("Buffer must be direct"); }

        int newSize; // Number of logical entries
        int newSizeInBytes;
        int elementSize = (entries == null) ? 0 : getBufferElementSize(entries);

        // Calculate new size based on input buffer
        if (entries == null || entries.limit() == 0 || elementSize == 0) {
            newSize = 0;
            newSizeInBytes = 0;
        } else {
             int totalBytes = entries.limit() * elementSize;
             if (totalBytes % numberOfBytesPerEntry != 0) {
                 throw new IllegalArgumentException("New buffer total bytes ("+totalBytes+") not divisible by numberOfBytesPerEntry ("+numberOfBytesPerEntry+")");
             }
             newSize = totalBytes / numberOfBytesPerEntry;
             newSizeInBytes = newSize * numberOfBytesPerEntry;
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

        GLES30.glBindBuffer(target, bufferId[0]);
        GLError.maybeThrowGLException("Failed to bind buffer object (id=" + bufferId[0] + ") for set", "glBindBuffer");

        // Rewind buffer before reading data
        if(entries != null) {
            entries.rewind();
        }

        // Check if reallocation is needed
        if (newSizeInBytes > (capacity * numberOfBytesPerEntry)) {
            // Reallocate with glBufferData
            GLES30.glBufferData(target, newSizeInBytes, entries, GLES30.GL_DYNAMIC_DRAW);
            GLError.maybeThrowGLException("Failed to reallocate buffer object using glBufferData", "glBufferData");
            capacity = newSize; // Update capacity
        } else if (newSizeInBytes > 0){
            // Update existing buffer with glBufferSubData
            GLES30.glBufferSubData(target, 0, newSizeInBytes, entries);
            GLError.maybeThrowGLException("Failed to update buffer object using glBufferSubData", "glBufferSubData");
        } else {
             // Handle case where newSizeInBytes is 0 (e.g., set(null)) - might deallocate
             GLES30.glBufferData(target, 0, null, GLES30.GL_DYNAMIC_DRAW);
             GLError.maybeThrowGLException("Failed to clear buffer object using glBufferData", "glBufferData");
             capacity = 0; // Reset capacity if cleared
        }
        size = newSize; // Update size
    }

    /** Frees GPU resources. */
    @Override
    public void close() {
        if (bufferId[0] != 0) {
            GLES30.glDeleteBuffers(1, bufferId, 0); // Pass array directly
            GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free buffer object (id=" + bufferId[0] + ")", "glDeleteBuffers");
            bufferId[0] = 0; // Reset ID in array
        }
        size = 0;
        capacity = 0;
    }

    // --- Package-Private Getters --- (Can be made public if needed by other packages)
    int getBufferId() { return bufferId[0]; }
    int getTarget() { return target; }
    /** Returns the number of logical entries currently in the buffer. */
    int getSize() { return size; }
    /** Returns the size of the data currently in the buffer, in bytes. */
    int getSizeInBytes() { return size * numberOfBytesPerEntry; }
}