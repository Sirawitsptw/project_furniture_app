package com.example.project_furnitureapp.samplerender; // ประกาศ package ของไฟล์นี้

import android.opengl.GLES30; // import GLES30 สำหรับใช้งาน OpenGL ES 3.0
import android.opengl.Matrix; // import Matrix สำหรับคำนวณเมทริกซ์
import com.google.ar.core.Anchor; // import Anchor สำหรับจุดยึด AR
import com.google.ar.core.Pose; // import Pose สำหรับตำแหน่งและทิศทาง
import com.google.ar.core.TrackingState; // import TrackingState สำหรับสถานะการติดตาม
import java.io.Closeable; // import Closeable สำหรับปิด resource
import java.io.IOException; // import IOException สำหรับจัดการ exception
import java.nio.ByteBuffer; // import ByteBuffer สำหรับจัดการ buffer
import java.nio.ByteOrder; // import ByteOrder สำหรับกำหนด endian ของ buffer
import java.nio.FloatBuffer; // import FloatBuffer สำหรับเก็บข้อมูล float array
import java.util.List; // import List สำหรับเก็บ anchor หลายตัว
import android.util.Log; // import Log สำหรับเขียน log
import java.util.Arrays; // import Arrays สำหรับแปลง array เป็น string

public class PointRenderer implements Closeable { // ประกาศคลาส PointRenderer และ implement Closeable
    private static final String TAG = "PointRenderer"; // ตัวแปร TAG สำหรับ log
    private static final String POINT_DEBUG_TAG = "POINT_DEBUG"; // ตัวแปร TAG สำหรับ debug log

    private static final String VERTEX_SHADER_NAME = "shaders/point.vert"; // ชื่อไฟล์ vertex shader
    private static final String FRAGMENT_SHADER_NAME = "shaders/point.frag"; // ชื่อไฟล์ fragment shader

    private static final int COORDS_PER_VERTEX = 3; // จำนวน float ต่อ 1 vertex (x, y, z)
    private static final int BYTES_PER_FLOAT = Float.BYTES; // ขนาด 1 float (byte)
    private static final int VERTEX_STRIDE = COORDS_PER_VERTEX * BYTES_PER_FLOAT; // ขนาด stride ของ 1 vertex

    private final Shader shader; // ตัวแปรเก็บ shader
    private final VertexBuffer vertexBuffer; // ตัวแปรเก็บ vertex buffer

    private int positionHandle = -1; // ตัวแปรเก็บ location ของ attribute a_Position
    private int mvpMatrixHandle = -1; // ตัวแปรเก็บ location ของ uniform u_ModelViewProjection
    private int colorHandle = -1; // ตัวแปรเก็บ location ของ uniform u_Color
    private int pointSizeHandle = -1; // ตัวแปรเก็บ location ของ uniform u_PointSize

    private float[] color = {1.0f, 0.0f, 1.0f, 1.0f}; // สีของจุด (magenta)
    private float pointSize = 25.0f; // ขนาดของจุด

    private final FloatBuffer singlePointBuffer; // buffer สำหรับเก็บข้อมูลจุดเดียว

    public PointRenderer(SampleRender render) throws IOException { // constructor รับ SampleRender
        singlePointBuffer = ByteBuffer.allocateDirect(1 * COORDS_PER_VERTEX * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder()).asFloatBuffer(); // สร้าง buffer สำหรับ 1 จุด
        vertexBuffer = new VertexBuffer(render, COORDS_PER_VERTEX, null); // สร้าง vertex buffer

        Shader createdShader = null; // ตัวแปรชั่วคราวสำหรับ shader
        try {
            createdShader = Shader.createFromAssets(render, VERTEX_SHADER_NAME, FRAGMENT_SHADER_NAME, null) // โหลด shader จาก asset
                    .setDepthTest(false) // ปิด depth test
                    .setDepthWrite(false); // ปิด depth write

            createdShader.lowLevelUse(); // ใช้งาน shader
            positionHandle = createdShader.getAttributeLocation("a_Position"); // หา location ของ a_Position
            mvpMatrixHandle = createdShader.getUniformLocation("u_ModelViewProjection"); // หา location ของ u_ModelViewProjection
            colorHandle = createdShader.getUniformLocation("u_Color"); // หา location ของ u_Color
            pointSizeHandle = createdShader.getUniformLocation("u_PointSize"); // หา location ของ u_PointSize
            GLES30.glUseProgram(0); // ยกเลิกการใช้ shader

            Log.d(TAG, "Shader Handles: Position=" + positionHandle + ", MVP=" + mvpMatrixHandle + ", Color=" + colorHandle + ", Size=" + pointSizeHandle); // log ค่า handle
            if (positionHandle == -1 || mvpMatrixHandle == -1 || colorHandle == -1 || pointSizeHandle == -1) {
                 Log.e(TAG, "Essential shader locations not found!"); // log error ถ้าไม่เจอ handle
            }
            this.shader = createdShader; // กำหนด shader
        } catch (Throwable t) { Log.e(TAG, "Failed init shader", t); close(); throw new RuntimeException(t); } // ถ้า error ให้ปิด resource และ throw
    }

    public void setColor(float[] rgba) { this.color = rgba.clone(); } // เมธอดตั้งค่าสี
    public void setPointSize(float size) { this.pointSize = size; } // เมธอดตั้งค่าขนาดจุด

    public void drawPoints(List<Anchor> anchors, float[] viewMatrix, float[] projectionMatrix) { // เมธอดวาดจุด
        if (anchors == null || anchors.isEmpty() || shader == null || positionHandle == -1 || mvpMatrixHandle == -1 || colorHandle == -1 || pointSizeHandle == -1) {
            return; // ถ้าไม่มี anchor หรือ shader ไม่พร้อม ให้ return
        }

        shader.lowLevelUse(); // ใช้งาน shader

        GLES30.glUniform4fv(colorHandle, 1, color, 0); // ส่งค่าสีไปยัง shader
        GLES30.glUniform1f(pointSizeHandle, pointSize); // ส่งขนาดจุดไปยัง shader
        GLError.maybeLogGLError(Log.ERROR, TAG, "PointRenderer: Set uniforms", "glUniform"); // log error ถ้ามี

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffer.getBufferId()); // bind vertex buffer
        GLES30.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, VERTEX_STRIDE, 0); // กำหนด pointer สำหรับ attribute
        GLES30.glEnableVertexAttribArray(positionHandle); // เปิดใช้งาน attribute
        GLError.maybeLogGLError(Log.ERROR, TAG, "PointRenderer: Enable vertex attrib", "glVertexAttribPointer/glEnable"); // log error ถ้ามี

        float[] modelMatrix = new float[16]; // เมทริกซ์ model
        float[] mvpMatrix = new float[16]; // เมทริกซ์ MVP

        for (Anchor anchor : anchors) { // วนลูปทุก anchor
            if (anchor.getTrackingState() == TrackingState.TRACKING) { // ถ้า anchor กำลังถูก track
                anchor.getPose().toMatrix(modelMatrix, 0); // แปลง pose เป็นเมทริกซ์ model

                float[] tempViewProduct = new float[16]; // เมทริกซ์ชั่วคราว
                Matrix.multiplyMM(tempViewProduct, 0, viewMatrix, 0, modelMatrix, 0); // คูณ view * model
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempViewProduct, 0); // คูณ projection * (view * model)

                GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0); // ส่งเมทริกซ์ MVP ไปยัง shader

                singlePointBuffer.clear(); // ล้าง buffer
                // **** แก้ไข: ส่ง 0,0,0 สำหรับ a_Position เพราะตำแหน่งจริงถูกจัดการด้วย Model Matrix ใน MVP แล้ว ****
                singlePointBuffer.put(0f).put(0f).put(0f); // ใส่ค่าจุด (0,0,0)
                singlePointBuffer.flip(); // กลับ pointer ไปต้น buffer
                vertexBuffer.set(singlePointBuffer); // ส่ง buffer ไปยัง vertexBuffer

                if (vertexBuffer.getNumberOfVertices() > 0) { // ถ้ามี vertex
                    Log.d(POINT_DEBUG_TAG, "Drawing point for anchor: " + anchor.getPose().toString() + " with MVP: " + Arrays.toString(mvpMatrix)); // log ข้อมูล anchor และ mvp
                    GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 1); // วาดจุด
                    GLError.maybeLogGLError(Log.ERROR, TAG, "PointRenderer: After glDrawArrays (Point)", "glDrawArrays"); // log error ถ้ามี
                }
            }
        }

        GLES30.glDisableVertexAttribArray(positionHandle); // ปิดใช้งาน attribute
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0); // unbind buffer
        GLES30.glUseProgram(0); // ยกเลิกการใช้ shader
    }

    @Override
    public void close() { // เมธอดปิด resource
        if (shader != null) shader.close(); // ปิด shader ถ้ามี
        if (vertexBuffer != null) vertexBuffer.close(); // ปิด vertexBuffer ถ้ามี
    }
}
