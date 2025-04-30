    package com.example.project_furnitureapp.samplerender;

    import android.opengl.GLES30;
    import com.google.ar.core.Pose;
    import java.io.Closeable;
    import java.io.IOException;
    import java.nio.ByteBuffer;
    import java.nio.ByteOrder;
    import java.nio.FloatBuffer;
    import android.util.Log;
    import java.util.Arrays;

    public class LineRenderer implements Closeable {
        private static final String TAG = "LineRenderer";
        private static final String LINE_DEBUG_TAG = "LINE_DEBUG";

        // ... (Constants และ Fields เหมือนเดิม) ...
        private static final String VERTEX_SHADER_NAME = "shaders/line.vert";
        private static final String FRAGMENT_SHADER_NAME = "shaders/line.frag";
        private static final int COORDS_PER_VERTEX = 3;
        private static final int BYTES_PER_FLOAT = Float.BYTES;
        private static final int VERTEX_STRIDE = COORDS_PER_VERTEX * BYTES_PER_FLOAT;
        private final Shader shader;
        private final VertexBuffer vertexBuffer;
        private FloatBuffer lineVertices;
        private int positionHandle = -1;
        private int mvpMatrixHandle = -1;
        private int colorHandle = -1;
        private float[] color = {1.0f, 0.0f, 0.0f, 1.0f}; // RED

        public LineRenderer(SampleRender render) throws IOException {
            // ... (Constructor เหมือนเดิม เพิ่ม Log Handles) ...
            lineVertices = ByteBuffer.allocateDirect(2 * COORDS_PER_VERTEX * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertexBuffer = new VertexBuffer(render, COORDS_PER_VERTEX, null);
            Shader createdShader = null;
            try {
                createdShader = Shader.createFromAssets(render, VERTEX_SHADER_NAME, FRAGMENT_SHADER_NAME, null);
                createdShader.lowLevelUse();
                positionHandle = createdShader.getAttributeLocation("a_Position");
                mvpMatrixHandle = createdShader.getUniformLocation("u_ModelViewProjection");
                colorHandle = createdShader.getUniformLocation("u_Color");
                GLES30.glUseProgram(0);
                Log.d(TAG, "Shader Handles: Position=" + positionHandle + ", MVP=" + mvpMatrixHandle + ", Color=" + colorHandle); // **** Log Handles ****
                if (positionHandle == -1 || mvpMatrixHandle == -1) { Log.e(TAG, "Essential shader locations not found!"); }
                this.shader = createdShader;
            } catch (Throwable t) { Log.e(TAG, "Failed init shader", t); close(); throw new RuntimeException(t); }
        }

        public void updateLine(Pose startPose, Pose endPose) { /* ... เหมือนเดิม ... */
            if (startPose == null || endPose == null) { Log.d(LINE_DEBUG_TAG, "updateLine: Clearing vertices."); vertexBuffer.set(null); return; }
            lineVertices.clear();
            float sx = startPose.tx(), sy = startPose.ty(), sz = startPose.tz(); float ex = endPose.tx(), ey = endPose.ty(), ez = endPose.tz();
            lineVertices.put(sx).put(sy).put(sz); lineVertices.put(ex).put(ey).put(ez); lineVertices.flip();
            Log.d(LINE_DEBUG_TAG, "updateLine: Start(" + sx + "," + sy + "," + sz + ") End(" + ex + "," + ey + "," + ez + ")");
            vertexBuffer.set(lineVertices); // เรียก set ของ VertexBuffer
        }

        public void setColor(float[] rgba) { /* ... เหมือนเดิม ... */ }

        /**
         * วาดจุด (แทนเส้น)
         * @param modelViewProjectionMatrix Matrix MVP
         */
        public void draw(float[] modelViewProjectionMatrix) {
            // **** เพิ่ม Log ตรวจสอบเงื่อนไข ****
            int numVertices = vertexBuffer.getNumberOfVertices();
            boolean shaderReady = shader != null;
            boolean handlesReady = positionHandle != -1 && mvpMatrixHandle != -1;
            Log.d(LINE_DEBUG_TAG, "draw() Prereq check: numVertices=" + numVertices + ", shaderReady=" + shaderReady + ", handlesReady=" + handlesReady + " (Pos=" + positionHandle + ", MVP=" + mvpMatrixHandle + ")");

            if (numVertices < 2 || !shaderReady || !handlesReady) {
                Log.w(LINE_DEBUG_TAG, "Skipping draw: prerequisites not met."); // Log เดิม
                return;
            }

            // ... (โค้ดส่วนที่เหลือของ draw เหมือนเดิม ที่วาด GL_POINTS และปิด Depth Test) ...
            boolean wasDepthTestEnabled = GLES30.glIsEnabled(GLES30.GL_DEPTH_TEST);
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
            // Log.d(LINE_DEBUG_TAG, "draw: Depth Test DISABLED");
            shader.lowLevelUse();
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);

            GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjectionMatrix, 0);
            GLES30.glUniform4fv(colorHandle, 1, color, 0); // ส่งสีแดงเข้าไป (แม้ shader จะไม่ใช้)
            GLError.maybeLogGLError(Log.ERROR, TAG, "LineRenderer: Set uniforms", "glUniform");

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffer.getBufferId());
            GLES30.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, VERTEX_STRIDE, 0);
            GLES30.glEnableVertexAttribArray(positionHandle);
            GLError.maybeLogGLError(Log.ERROR, TAG, "LineRenderer: Enable vertex attrib", "glVertexAttribPointer/glEnable");

            Log.d(LINE_DEBUG_TAG, "draw: Calling glDrawArrays(GL_POINTS, 0, 2)");
            GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 2);
            GLError.maybeLogGLError(Log.ERROR, TAG, "LineRenderer: After glDrawArrays (Points)", "glDrawArrays");

            GLES30.glFlush();
            // Log.d(LINE_DEBUG_TAG, "draw: glFlush() called.");

            GLES30.glDisableVertexAttribArray(positionHandle);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
            if (wasDepthTestEnabled) { GLES30.glEnable(GLES30.GL_DEPTH_TEST); /* Log.d(LINE_DEBUG_TAG, "draw: Depth Test RE-ENABLED"); */ }
            // else { Log.d(LINE_DEBUG_TAG, "draw: Depth Test remains DISABLED"); }
        }

        @Override
        public void close() { /* ... เหมือนเดิม ... */
            if (shader != null) { shader.close(); }
            if (vertexBuffer != null) { vertexBuffer.close(); }
        }
    }
    