package com.example.project_furnitureapp.samplerender.arcore;

// --- Imports --- (เหมือนเดิม)
import com.example.project_furnitureapp.samplerender.IndexBuffer;
import com.example.project_furnitureapp.samplerender.Mesh;
import com.example.project_furnitureapp.samplerender.SampleRender;
import com.example.project_furnitureapp.samplerender.Shader;
import com.example.project_furnitureapp.samplerender.Texture;
import com.example.project_furnitureapp.samplerender.VertexBuffer;
import com.example.project_furnitureapp.samplerender.GLError;
import com.google.ar.core.Camera;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.core.Trackable;
import android.opengl.Matrix;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
// import java.util.Map; // ไม่ได้ใช้ Map แล้ว
// import java.util.HashMap; // ไม่ได้ใช้ Map แล้ว
import java.io.IOException;
import android.util.Log;

/** Renders the detected AR planes. */
public class PlaneRenderer {
    private static final String TAG = PlaneRenderer.class.getSimpleName();

    // --- Constants ---
    private static final String VERTEX_SHADER_NAME = "shaders/plane.vert";
    private static final String FRAGMENT_SHADER_NAME = "shaders/plane.frag";
    // **** แก้ไข Path ให้ถูกต้อง ****
    private static final String TEXTURE_NAME = "texture/trigrid.png";

    // Uniform names
    private static final String TEXTURE_UNIFORM_NAME = "u_Texture";
    private static final String MODEL_MATRIX_UNIFORM_NAME = "u_Model";
    private static final String MODEL_VIEW_PROJECTION_MATRIX_UNIFORM_NAME = "u_ModelViewProjection";
    private static final String NORMAL_UNIFORM_NAME = "u_Normal"; // **** เปลี่ยนชื่อให้ตรง Shader ****
    private static final String GRID_CONTROL_UNIFORM_NAME = "u_GridControl";
    private static final String PLANE_UVS_UNIFORM_NAME = "u_PlaneUvMatrix"; // **** เปลี่ยนชื่อให้ตรง Shader ****

    // Vertex attributes
    // **** สำคัญ: ต้องตรงกับ a_XZPositionAlpha ใน Shader ****
    private static final int COORDS_PER_VERTEX = 3; // x, z, alpha
    private static final int BYTES_PER_FLOAT = Float.BYTES;
    private static final int BYTES_PER_INT = Integer.BYTES;

    // Buffer constants
    private static final int VERTS_PER_BOUNDARY_VERT = 1; // 1 vertex per boundary point for fan
    private static final int INDICES_PER_TRIANGLE = 3;
    private static final int INITIAL_BUFFER_BOUNDARY_VERTS = 128; // เพิ่มขนาดเผื่อ Polygon ซับซ้อน
    private static final int INITIAL_VERTEX_BUFFER_SIZE_BYTES = BYTES_PER_FLOAT * COORDS_PER_VERTEX * INITIAL_BUFFER_BOUNDARY_VERTS;
    private static final int INITIAL_INDEX_BUFFER_SIZE_BYTES = BYTES_PER_INT * INDICES_PER_TRIANGLE * (INITIAL_BUFFER_BOUNDARY_VERTS); // ประมาณการคร่าวๆ

    // Shader control
    private static final float[] GRID_CONTROL = {0.2f, 0.4f, 2.0f, 1.5f};
    private static final float FADE_RADIUS_M = 0.25f; // รัศมีที่ Alpha เริ่ม Fade

    // OpenGL resources
    private final Mesh mesh;
    private final IndexBuffer indexBufferObject;
    private final VertexBuffer vertexBufferObject;
    private final Shader shader;
    private final Texture texture;
    private final SampleRender render;

    // Reusable Buffers and Matrices
    private FloatBuffer vertexBuffer = allocateFloatBuffer(INITIAL_VERTEX_BUFFER_SIZE_BYTES);
    private IntBuffer indexBuffer = allocateIntBuffer(INITIAL_INDEX_BUFFER_SIZE_BYTES);
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];
    // **** เพิ่ม Matrix สำหรับ UV ****
    private final float[] planeAngleUvMatrix = new float[4]; // 2x2 rotation matrix
    // **** เพิ่ม Vector สำหรับ Normal ****
    private final float[] planeNormal = new float[3];


    public PlaneRenderer(SampleRender render) throws IOException {
        this.render = render;
        try {
            texture = Texture.createFromAsset(render, TEXTURE_NAME, Texture.WrapMode.REPEAT, Texture.ColorFormat.LINEAR);
            shader = Shader.createFromAssets(render, VERTEX_SHADER_NAME, FRAGMENT_SHADER_NAME, null)
                        .setBlend(Shader.BlendFactor.SRC_ALPHA, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA)
                        .setDepthWrite(false)
                        .setTexture(TEXTURE_UNIFORM_NAME, texture); // ตั้งค่า Texture Uniform

            indexBufferObject = new IndexBuffer(render, null);
            // **** สำคัญ: สร้าง VertexBuffer ให้มี 3 components ต่อ vertex ****
            vertexBufferObject = new VertexBuffer(render, COORDS_PER_VERTEX, null);
            VertexBuffer[] vertexBuffers = {vertexBufferObject};
            mesh = new Mesh(render, Mesh.PrimitiveMode.TRIANGLES, indexBufferObject, vertexBuffers);

        } catch (IOException e) { Log.e(TAG,"Failed init", e); close(); throw e; }
          catch (Throwable t) { Log.e(TAG,"Failed init", t); close(); throw new RuntimeException(t); }
    }

    private static FloatBuffer allocateFloatBuffer(int capacityInBytes) { return ByteBuffer.allocateDirect(capacityInBytes).order(ByteOrder.nativeOrder()).asFloatBuffer(); }
    private static IntBuffer allocateIntBuffer(int capacityInBytes) { return ByteBuffer.allocateDirect(capacityInBytes).order(ByteOrder.nativeOrder()).asIntBuffer(); }

    /** Updates vertex and index buffers based on the plane's polygon. */
    private void updatePlaneParameters(Plane plane) {
        FloatBuffer polygon = plane.getPolygon(); // (x,z) pairs in plane's local frame
        if (polygon == null || polygon.limit() < 6) { // Need at least 3 vertices
            vertexBufferObject.set(null); indexBufferObject.set(null); return;
        }

        polygon.rewind();
        int numBoundaryVertices = polygon.limit() / 2;
        int numVertices = numBoundaryVertices; // For triangle fan
        int numIndices = Math.max(0, numBoundaryVertices - 2) * INDICES_PER_TRIANGLE;

        // Ensure buffers have enough capacity
        int neededVBufferSize = numVertices * COORDS_PER_VERTEX;
        if (vertexBuffer.capacity() < neededVBufferSize) {
             vertexBuffer = allocateFloatBuffer(neededVBufferSize * BYTES_PER_FLOAT);
        }
        int neededIBufferSize = numIndices;
        if (indexBuffer.capacity() < neededIBufferSize) {
             indexBuffer = allocateIntBuffer(neededIBufferSize * BYTES_PER_INT);
        }

        vertexBuffer.clear().limit(neededVBufferSize);
        indexBuffer.clear().limit(neededIBufferSize);

        // --- **** แก้ไข: สร้าง Vertex Data (x, z, alpha) **** ---
        // Calculate alpha based on distance to center (simple fade)
        Pose centerPose = plane.getCenterPose(); // Center is origin in local frame (0,0,0)

        for (int i = 0; i < numBoundaryVertices; ++i) {
            float x = polygon.get();
            float z = polygon.get();
            // Calculate distance from center (0,0) in local XZ plane
            float distance = (float) Math.sqrt(x * x + z * z);
            // Calculate alpha: 1 near center, fading to 0 at FADE_RADIUS_M
            float alpha = 1.0f - Math.min(1.0f, distance / FADE_RADIUS_M);
            // Put (x, z, alpha) into the buffer
            vertexBuffer.put(x).put(z).put(alpha);
        }
        vertexBuffer.flip(); // Ready for reading

        // --- Create indices for triangle fan ---
        for (int i = 1; i < numBoundaryVertices - 1; ++i) {
            indexBuffer.put(0).put(i).put(i + 1);
        }
        indexBuffer.flip(); // Ready for reading

        // Update GPU buffers
        vertexBufferObject.set(vertexBuffer);
        indexBufferObject.set(indexBuffer);
    }


    /** Renders the detected planes. */
    public void drawPlanes(Collection<Plane> allPlanes, Pose cameraPose, float[] cameraProjection) {
        cameraPose.inverse().toMatrix(viewMatrix, 0); // Calculate view matrix once

        shader.lowLevelUse(); // Activate shader once for all planes

        for (Plane plane : allPlanes) {
            if (plane.getTrackingState() != TrackingState.TRACKING || plane.getSubsumedBy() != null) {
                continue; // Skip planes not being tracked or subsumed
            }

            try {
                updatePlaneParameters(plane); // Update buffers for this plane
            } catch (Exception e) { Log.e(TAG, "Error updating plane params", e); continue; }

            if (vertexBufferObject.getNumberOfVertices() == 0 || indexBufferObject.getSize() == 0) {
                continue; // Skip if buffers are empty after update
            }

            // --- Calculate Matrices ---
            plane.getCenterPose().toMatrix(modelMatrix, 0);
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraProjection, 0, modelViewMatrix, 0);

            // --- **** เพิ่ม: Calculate and Set Uniforms **** ---
            // Set Model Matrix (used for world position calculation in vertex shader)
            shader.setMat4(MODEL_MATRIX_UNIFORM_NAME, modelMatrix);

            // Set ModelViewProjection Matrix
            shader.setMat4(MODEL_VIEW_PROJECTION_MATRIX_UNIFORM_NAME, modelViewProjectionMatrix);

            // Set Grid Control Uniform
            shader.setVec4(GRID_CONTROL_UNIFORM_NAME, GRID_CONTROL);

            // Calculate and Set Normal Vector Uniform (u_Normal)
            // Get the normal in world coordinates (Y axis of the plane's pose)
            plane.getCenterPose().getTransformedAxis(1, 1.0f, planeNormal, 0); // Axis 1 is Y
            shader.setVec3(NORMAL_UNIFORM_NAME, planeNormal);

            // Calculate and Set Plane UV Matrix Uniform (u_PlaneUvMatrix)
            // This rotates the UVs based on the plane's orientation around Y axis
            Pose planePose = plane.getCenterPose();
            float angle = (float) Math.atan2(planePose.qx(), planePose.qw()) * 2; // Simplified angle from quaternion
            float cosAngle = (float) Math.cos(angle);
            float sinAngle = (float) Math.sin(angle);
            // Create 2x2 rotation matrix (column-major order for OpenGL)
            planeAngleUvMatrix[0] = cosAngle;  planeAngleUvMatrix[1] = sinAngle;
            planeAngleUvMatrix[2] = -sinAngle; planeAngleUvMatrix[3] = cosAngle;
            shader.setMat2(PLANE_UVS_UNIFORM_NAME, planeAngleUvMatrix);
            // --- **** สิ้นสุดการตั้งค่า Uniforms **** ---

            // --- Draw the mesh ---
            this.render.draw(mesh, shader); // Use SampleRender to draw
            // GLError.maybeLogGLError(Log.DEBUG, TAG, "After drawing plane", ""); // Optional Log

        } // End loop through planes

        // No need to unbind shader here if SampleRender handles it or next draw call uses a different shader
    }

    /** Calculate the normal distance to plane from cameraPose */
    public static float calculateDistanceToPlane(Pose planePose, Pose cameraPose) {
        float[] normal = new float[3]; float cx=cameraPose.tx(), cy=cameraPose.ty(), cz=cameraPose.tz();
        planePose.getTransformedAxis(1, 1.0f, normal, 0);
        return (cx-planePose.tx())*normal[0] + (cy-planePose.ty())*normal[1] + (cz-planePose.tz())*normal[2];
    }

    /** Releases OpenGL resources */
    public void close() {
        Log.d(TAG, "Closing PlaneRenderer resources.");
        if (shader != null) { shader.close(); } // Texture is closed by Shader
    }
}
