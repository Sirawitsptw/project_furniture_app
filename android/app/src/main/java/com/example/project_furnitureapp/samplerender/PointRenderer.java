package com.example.project_furnitureapp.samplerender; // **** แก้ไข Package ****

import android.opengl.GLES30;
import android.opengl.Matrix; // **** เพิ่ม Import Matrix ****
import com.google.ar.core.Anchor; // **** เพิ่ม Import Anchor ****
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState; // **** เพิ่ม Import TrackingState ****
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List; // **** เพิ่ม Import List ****
import android.util.Log;
import java.util.Arrays;

// **** ไม่ต้อง Import คลาสใน Package เดียวกัน ****
// import com.example.project_furnitureapp.samplerender.Shader;
// import com.example.project_furnitureapp.samplerender.VertexBuffer;
// import com.example.project_furnitureapp.samplerender.GLError;
// import com.example.project_furnitureapp.samplerender.SampleRender; // รับเป็น parameter

public class PointRenderer implements Closeable {
    private static final String TAG = "PointRenderer";
    private static final String POINT_DEBUG_TAG = "POINT_DEBUG";

    private static final String VERTEX_SHADER_NAME = "shaders/point.vert";
    private static final String FRAGMENT_SHADER_NAME = "shaders/point.frag";

    private static final int COORDS_PER_VERTEX = 3;
    private static final int BYTES_PER_FLOAT = Float.BYTES;
    private static final int VERTEX_STRIDE = COORDS_PER_VERTEX * BYTES_PER_FLOAT;

    private final Shader shader;
    private final VertexBuffer vertexBuffer;

    private int positionHandle = -1;
    private int mvpMatrixHandle = -1;
    private int colorHandle = -1;
    private int pointSizeHandle = -1;

    private float[] color = {1.0f, 0.0f, 1.0f, 1.0f}; // Magenta
    private float pointSize = 25.0f;

    private final FloatBuffer singlePointBuffer;

    public PointRenderer(SampleRender render) throws IOException {
        singlePointBuffer = ByteBuffer.allocateDirect(1 * COORDS_PER_VERTEX * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer = new VertexBuffer(render, COORDS_PER_VERTEX, null);

        Shader createdShader = null;
        try {
            createdShader = Shader.createFromAssets(render, VERTEX_SHADER_NAME, FRAGMENT_SHADER_NAME, null)
                    .setDepthTest(false)
                    .setDepthWrite(false);

            createdShader.lowLevelUse();
            positionHandle = createdShader.getAttributeLocation("a_Position");
            mvpMatrixHandle = createdShader.getUniformLocation("u_ModelViewProjection");
            colorHandle = createdShader.getUniformLocation("u_Color");
            pointSizeHandle = createdShader.getUniformLocation("u_PointSize");
            GLES30.glUseProgram(0);

            Log.d(TAG, "Shader Handles: Position=" + positionHandle + ", MVP=" + mvpMatrixHandle + ", Color=" + colorHandle + ", Size=" + pointSizeHandle);
            if (positionHandle == -1 || mvpMatrixHandle == -1 || colorHandle == -1 || pointSizeHandle == -1) {
                 Log.e(TAG, "Essential shader locations not found!");
            }
            this.shader = createdShader;
        } catch (Throwable t) { Log.e(TAG, "Failed init shader", t); close(); throw new RuntimeException(t); }
    }

    public void setColor(float[] rgba) { this.color = rgba.clone(); }
    public void setPointSize(float size) { this.pointSize = size; }

    public void drawPoints(List<Anchor> anchors, float[] viewMatrix, float[] projectionMatrix) {
        if (anchors == null || anchors.isEmpty() || shader == null || positionHandle == -1 || mvpMatrixHandle == -1 || colorHandle == -1 || pointSizeHandle == -1) {
            return;
        }

        shader.lowLevelUse();

        GLES30.glUniform4fv(colorHandle, 1, color, 0);
        GLES30.glUniform1f(pointSizeHandle, pointSize);
        GLError.maybeLogGLError(Log.ERROR, TAG, "PointRenderer: Set uniforms", "glUniform");

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffer.getBufferId());
        GLES30.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, VERTEX_STRIDE, 0);
        GLES30.glEnableVertexAttribArray(positionHandle);
        GLError.maybeLogGLError(Log.ERROR, TAG, "PointRenderer: Enable vertex attrib", "glVertexAttribPointer/glEnable");

        float[] modelMatrix = new float[16];
        float[] mvpMatrix = new float[16];

        for (Anchor anchor : anchors) {
            if (anchor.getTrackingState() == TrackingState.TRACKING) {
                anchor.getPose().toMatrix(modelMatrix, 0);

                float[] tempViewProduct = new float[16];
                Matrix.multiplyMM(tempViewProduct, 0, viewMatrix, 0, modelMatrix, 0);
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempViewProduct, 0);

                GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

                singlePointBuffer.clear();
                // **** แก้ไข: ส่ง 0,0,0 สำหรับ a_Position เพราะตำแหน่งจริงถูกจัดการด้วย Model Matrix ใน MVP แล้ว ****
                singlePointBuffer.put(0f).put(0f).put(0f);
                singlePointBuffer.flip();
                vertexBuffer.set(singlePointBuffer);

                if (vertexBuffer.getNumberOfVertices() > 0) {
                    Log.d(POINT_DEBUG_TAG, "Drawing point for anchor: " + anchor.getPose().toString() + " with MVP: " + Arrays.toString(mvpMatrix));
                    GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 1);
                    GLError.maybeLogGLError(Log.ERROR, TAG, "PointRenderer: After glDrawArrays (Point)", "glDrawArrays");
                }
            }
        }

        GLES30.glDisableVertexAttribArray(positionHandle);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glUseProgram(0);
    }

    @Override
    public void close() {
        if (shader != null) shader.close();
        if (vertexBuffer != null) vertexBuffer.close();
    }
}
