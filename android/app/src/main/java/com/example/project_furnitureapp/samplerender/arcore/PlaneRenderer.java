package com.example.project_furnitureapp.samplerender.arcore; // ประกาศ package ของไฟล์นี้

// --- Imports --- (เหมือนเดิม)
import com.example.project_furnitureapp.samplerender.IndexBuffer; // import IndexBuffer สำหรับเก็บ index ของ mesh
import com.example.project_furnitureapp.samplerender.Mesh; // import Mesh สำหรับวาด geometry
import com.example.project_furnitureapp.samplerender.SampleRender; // import SampleRender สำหรับวาดบนหน้าจอ
import com.example.project_furnitureapp.samplerender.Shader; // import Shader สำหรับจัดการ shader
import com.example.project_furnitureapp.samplerender.Texture; // import Texture สำหรับจัดการ texture
import com.example.project_furnitureapp.samplerender.VertexBuffer; // import VertexBuffer สำหรับเก็บ vertex
import com.example.project_furnitureapp.samplerender.GLError; // import GLError สำหรับเช็ค error
import com.google.ar.core.Camera; // import Camera ของ ARCore
import com.google.ar.core.Plane; // import Plane ของ ARCore
import com.google.ar.core.Pose; // import Pose ของ ARCore
import com.google.ar.core.TrackingState; // import TrackingState ของ ARCore
import com.google.ar.core.Trackable; // import Trackable ของ ARCore
import android.opengl.Matrix; // import Matrix สำหรับคำนวณเมทริกซ์
import java.nio.FloatBuffer; // import FloatBuffer สำหรับเก็บข้อมูล float
import java.util.Collection; // import Collection สำหรับเก็บ plane หลายตัว
import java.nio.ByteBuffer; // import ByteBuffer สำหรับสร้าง buffer
import java.nio.ByteOrder; // import ByteOrder สำหรับกำหนด endian
import java.nio.IntBuffer; // import IntBuffer สำหรับเก็บ index
// import java.util.Map; // ไม่ได้ใช้ Map แล้ว
// import java.util.HashMap; // ไม่ได้ใช้ Map แล้ว
import java.io.IOException; // import IOException สำหรับจัดการ exception
import android.util.Log; // import Log สำหรับเขียน log

/** Renders the detected AR planes. */ // คำอธิบายคลาส: สำหรับวาด plane ที่ตรวจจับได้
public class PlaneRenderer { // ประกาศคลาส PlaneRenderer
    private static final String TAG = PlaneRenderer.class.getSimpleName(); // ตัวแปร TAG สำหรับ log

    // --- Constants ---
    private static final String VERTEX_SHADER_NAME = "shaders/plane.vert"; // ชื่อไฟล์ vertex shader
    private static final String FRAGMENT_SHADER_NAME = "shaders/plane.frag"; // ชื่อไฟล์ fragment shader
    // **** แก้ไข Path ให้ถูกต้อง ****
    private static final String TEXTURE_NAME = "texture/trigrid.png"; // ชื่อไฟล์ texture

    // Uniform names
    private static final String TEXTURE_UNIFORM_NAME = "u_Texture"; // ชื่อ uniform สำหรับ texture
    private static final String MODEL_MATRIX_UNIFORM_NAME = "u_Model"; // ชื่อ uniform สำหรับ model matrix
    private static final String MODEL_VIEW_PROJECTION_MATRIX_UNIFORM_NAME = "u_ModelViewProjection"; // ชื่อ uniform สำหรับ mvp matrix
    private static final String NORMAL_UNIFORM_NAME = "u_Normal"; // **** เปลี่ยนชื่อให้ตรง Shader ****
    private static final String GRID_CONTROL_UNIFORM_NAME = "u_GridControl"; // ชื่อ uniform สำหรับ grid control
    private static final String PLANE_UVS_UNIFORM_NAME = "u_PlaneUvMatrix"; // **** เปลี่ยนชื่อให้ตรง Shader ****

    // Vertex attributes
    // **** สำคัญ: ต้องตรงกับ a_XZPositionAlpha ใน Shader ****
    private static final int COORDS_PER_VERTEX = 3; // x, z, alpha
    private static final int BYTES_PER_FLOAT = Float.BYTES; // ขนาด float (byte)
    private static final int BYTES_PER_INT = Integer.BYTES; // ขนาด int (byte)

    // Buffer constants
    private static final int VERTS_PER_BOUNDARY_VERT = 1; // 1 vertex ต่อ boundary point
    private static final int INDICES_PER_TRIANGLE = 3; // 3 index ต่อ triangle
    private static final int INITIAL_BUFFER_BOUNDARY_VERTS = 128; // ขนาด buffer เริ่มต้น
    private static final int INITIAL_VERTEX_BUFFER_SIZE_BYTES = BYTES_PER_FLOAT * COORDS_PER_VERTEX * INITIAL_BUFFER_BOUNDARY_VERTS; // ขนาด buffer vertex เริ่มต้น
    private static final int INITIAL_INDEX_BUFFER_SIZE_BYTES = BYTES_PER_INT * INDICES_PER_TRIANGLE * (INITIAL_BUFFER_BOUNDARY_VERTS); // ขนาด buffer index เริ่มต้น

    // Shader control
    private static final float[] GRID_CONTROL = {0.2f, 0.4f, 2.0f, 1.5f}; // ค่าควบคุม grid
    private static final float FADE_RADIUS_M = 0.25f; // รัศมีที่ alpha เริ่ม fade

    // OpenGL resources
    private final Mesh mesh; // mesh สำหรับวาด plane
    private final IndexBuffer indexBufferObject; // index buffer สำหรับ mesh
    private final VertexBuffer vertexBufferObject; // vertex buffer สำหรับ mesh
    private final Shader shader; // shader สำหรับวาด plane
    private final Texture texture; // texture สำหรับ grid
    private final SampleRender render; // ตัวแปร SampleRender

    // Reusable Buffers and Matrices
    private FloatBuffer vertexBuffer = allocateFloatBuffer(INITIAL_VERTEX_BUFFER_SIZE_BYTES); // buffer สำหรับ vertex
    private IntBuffer indexBuffer = allocateIntBuffer(INITIAL_INDEX_BUFFER_SIZE_BYTES); // buffer สำหรับ index
    private final float[] viewMatrix = new float[16]; // เมทริกซ์ view
    private final float[] modelMatrix = new float[16]; // เมทริกซ์ model
    private final float[] modelViewMatrix = new float[16]; // เมทริกซ์ model-view
    private final float[] modelViewProjectionMatrix = new float[16]; // เมทริกซ์ mvp
    // **** เพิ่ม Matrix สำหรับ UV ****
    private final float[] planeAngleUvMatrix = new float[4]; // 2x2 rotation matrix สำหรับ UV
    // **** เพิ่ม Vector สำหรับ Normal ****
    private final float[] planeNormal = new float[3]; // เวกเตอร์ normal ของ plane


    public PlaneRenderer(SampleRender render) throws IOException { // constructor รับ SampleRender
        this.render = render; // กำหนด render
        try {
            texture = Texture.createFromAsset(render, TEXTURE_NAME, Texture.WrapMode.REPEAT, Texture.ColorFormat.LINEAR); // โหลด texture
            shader = Shader.createFromAssets(render, VERTEX_SHADER_NAME, FRAGMENT_SHADER_NAME, null)
                        .setBlend(Shader.BlendFactor.SRC_ALPHA, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA) // ตั้งค่า blend
                        .setDepthWrite(false) // ปิด depth write
                        .setTexture(TEXTURE_UNIFORM_NAME, texture); // ตั้งค่า texture uniform

            indexBufferObject = new IndexBuffer(render, null); // สร้าง index buffer
            // **** สำคัญ: สร้าง VertexBuffer ให้มี 3 components ต่อ vertex ****
            vertexBufferObject = new VertexBuffer(render, COORDS_PER_VERTEX, null); // สร้าง vertex buffer
            VertexBuffer[] vertexBuffers = {vertexBufferObject}; // array ของ vertex buffer
            mesh = new Mesh(render, Mesh.PrimitiveMode.TRIANGLES, indexBufferObject, vertexBuffers); // สร้าง mesh

        } catch (IOException e) { Log.e(TAG,"Failed init", e); close(); throw e; } // ถ้า error ให้ปิด resource
          catch (Throwable t) { Log.e(TAG,"Failed init", t); close(); throw new RuntimeException(t); }
    }

    private static FloatBuffer allocateFloatBuffer(int capacityInBytes) { return ByteBuffer.allocateDirect(capacityInBytes).order(ByteOrder.nativeOrder()).asFloatBuffer(); } // สร้าง FloatBuffer
    private static IntBuffer allocateIntBuffer(int capacityInBytes) { return ByteBuffer.allocateDirect(capacityInBytes).order(ByteOrder.nativeOrder()).asIntBuffer(); } // สร้าง IntBuffer

    /** Updates vertex and index buffers based on the plane's polygon. */ // เมธอดอัปเดต vertex/index buffer ตาม polygon ของ plane
    private void updatePlaneParameters(Plane plane) {
        FloatBuffer polygon = plane.getPolygon(); // (x,z) pairs ใน local frame
        if (polygon == null || polygon.limit() < 6) { // ต้องมีอย่างน้อย 3 จุด
            vertexBufferObject.set(null); indexBufferObject.set(null); return; // ถ้าไม่พอให้ clear buffer
        }

        polygon.rewind(); // รีเซ็ต pointer
        int numBoundaryVertices = polygon.limit() / 2; // จำนวนจุด boundary
        int numVertices = numBoundaryVertices; // จำนวน vertex
        int numIndices = Math.max(0, numBoundaryVertices - 2) * INDICES_PER_TRIANGLE; // จำนวน index

        // Ensure buffers have enough capacity
        int neededVBufferSize = numVertices * COORDS_PER_VERTEX; // ขนาด buffer vertex ที่ต้องใช้
        if (vertexBuffer.capacity() < neededVBufferSize) {
             vertexBuffer = allocateFloatBuffer(neededVBufferSize * BYTES_PER_FLOAT); // ขยาย buffer ถ้าไม่พอ
        }
        int neededIBufferSize = numIndices; // ขนาด buffer index ที่ต้องใช้
        if (indexBuffer.capacity() < neededIBufferSize) {
             indexBuffer = allocateIntBuffer(neededIBufferSize * BYTES_PER_INT); // ขยาย buffer ถ้าไม่พอ
        }

        vertexBuffer.clear().limit(neededVBufferSize); // เตรียม buffer สำหรับเขียน
        indexBuffer.clear().limit(neededIBufferSize); // เตรียม buffer สำหรับเขียน

        // --- **** แก้ไข: สร้าง Vertex Data (x, z, alpha) **** ---
        // Calculate alpha based on distance to center (simple fade)
        Pose centerPose = plane.getCenterPose(); // จุดศูนย์กลางของ plane

        for (int i = 0; i < numBoundaryVertices; ++i) {
            float x = polygon.get(); // อ่านค่า x
            float z = polygon.get(); // อ่านค่า z
            // Calculate distance from center (0,0) ใน local XZ
            float distance = (float) Math.sqrt(x * x + z * z); // ระยะห่างจากจุดศูนย์กลาง
            // Calculate alpha: 1 ใกล้ center, fade เป็น 0 ที่ FADE_RADIUS_M
            float alpha = 1.0f - Math.min(1.0f, distance / FADE_RADIUS_M); // คำนวณ alpha
            // Put (x, z, alpha) ลง buffer
            vertexBuffer.put(x).put(z).put(alpha);
        }
        vertexBuffer.flip(); // เตรียม buffer สำหรับอ่าน

        // --- Create indices for triangle fan ---
        for (int i = 1; i < numBoundaryVertices - 1; ++i) {
            indexBuffer.put(0).put(i).put(i + 1); // ใส่ index สำหรับแต่ละ triangle
        }
        indexBuffer.flip(); // เตรียม buffer สำหรับอ่าน

        // Update GPU buffers
        vertexBufferObject.set(vertexBuffer); // ส่ง vertex buffer ไป GPU
        indexBufferObject.set(indexBuffer); // ส่ง index buffer ไป GPU
    }


    /** Renders the detected planes. */ // เมธอดวาด plane ทั้งหมด
    public void drawPlanes(Collection<Plane> allPlanes, Pose cameraPose, float[] cameraProjection) {
        cameraPose.inverse().toMatrix(viewMatrix, 0); // คำนวณ view matrix

        shader.lowLevelUse(); // เปิดใช้งาน shader

        for (Plane plane : allPlanes) { // วนลูปทุก plane
            if (plane.getTrackingState() != TrackingState.TRACKING || plane.getSubsumedBy() != null) {
                continue; // ข้าม plane ที่ไม่ได้ track หรือถูก subsume
            }

            try {
                updatePlaneParameters(plane); // อัปเดต buffer สำหรับ plane นี้
            } catch (Exception e) { Log.e(TAG, "Error updating plane params", e); continue; }

            if (vertexBufferObject.getNumberOfVertices() == 0 || indexBufferObject.getSize() == 0) {
                continue; // ข้ามถ้า buffer ว่าง
            }

            // --- Calculate Matrices ---
            plane.getCenterPose().toMatrix(modelMatrix, 0); // คำนวณ model matrix
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0); // คำนวณ model-view
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraProjection, 0, modelViewMatrix, 0); // คำนวณ mvp

            // --- **** เพิ่ม: Calculate and Set Uniforms **** ---
            // Set Model Matrix (ใช้ใน vertex shader)
            shader.setMat4(MODEL_MATRIX_UNIFORM_NAME, modelMatrix);

            // Set ModelViewProjection Matrix
            shader.setMat4(MODEL_VIEW_PROJECTION_MATRIX_UNIFORM_NAME, modelViewProjectionMatrix);

            // Set Grid Control Uniform
            shader.setVec4(GRID_CONTROL_UNIFORM_NAME, GRID_CONTROL);

            // Calculate and Set Normal Vector Uniform (u_Normal)
            // Get the normal ใน world coordinates (แกน Y ของ plane)
            plane.getCenterPose().getTransformedAxis(1, 1.0f, planeNormal, 0); // ดึง normal
            shader.setVec3(NORMAL_UNIFORM_NAME, planeNormal);

            // Calculate and Set Plane UV Matrix Uniform (u_PlaneUvMatrix)
            // หมุน UV ตาม orientation ของ plane รอบแกน Y
            Pose planePose = plane.getCenterPose(); // pose ของ plane
            float angle = (float) Math.atan2(planePose.qx(), planePose.qw()) * 2; // คำนวณมุมจาก quaternion
            float cosAngle = (float) Math.cos(angle); // cos ของมุม
            float sinAngle = (float) Math.sin(angle); // sin ของมุม
            // สร้าง 2x2 rotation matrix (column-major สำหรับ OpenGL)
            planeAngleUvMatrix[0] = cosAngle;  planeAngleUvMatrix[1] = sinAngle;
            planeAngleUvMatrix[2] = -sinAngle; planeAngleUvMatrix[3] = cosAngle;
            shader.setMat2(PLANE_UVS_UNIFORM_NAME, planeAngleUvMatrix); // ส่ง matrix ไป shader
            // --- **** สิ้นสุดการตั้งค่า Uniforms **** ---

            // --- Draw the mesh ---
            this.render.draw(mesh, shader); // วาด mesh ด้วย shader
            // GLError.maybeLogGLError(Log.DEBUG, TAG, "After drawing plane", ""); // log error (ถ้าต้องการ)

        } // End loop through planes

        // ไม่ต้อง unbind shader ถ้า SampleRender จัดการเอง
    }

    /** Calculate the normal distance to plane from cameraPose */ // เมธอดคำนวณระยะห่างแนว normal จากกล้องถึง plane
    public static float calculateDistanceToPlane(Pose planePose, Pose cameraPose) {
        float[] normal = new float[3]; float cx=cameraPose.tx(), cy=cameraPose.ty(), cz=cameraPose.tz(); // ดึงตำแหน่งกล้อง
        planePose.getTransformedAxis(1, 1.0f, normal, 0); // ดึง normal ของ plane
        return (cx-planePose.tx())*normal[0] + (cy-planePose.ty())*normal[1] + (cz-planePose.tz())*normal[2]; // dot product
    }

    /** Releases OpenGL resources */ // เมธอดปิด resource
    public void close() {
        Log.d(TAG, "Closing PlaneRenderer resources."); // log
        if (shader != null) { shader.close(); } // ปิด shader (texture ถูกปิดโดย shader)
    }
}
