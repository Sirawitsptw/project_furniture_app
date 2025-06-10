/*
 * Copyright 2020 Google LLC // ข้อความลิขสิทธิ์ของ Google
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // แจ้งว่าใช้ Apache License 2.0
 * you may not use this file except in compliance with the License. // ห้ามใช้ไฟล์นี้ถ้าไม่ยอมรับ license
 * You may obtain a copy of the License at // สามารถดู license ได้ที่
 *
 *   http://www.apache.org/licenses/LICENSE-2.0 // URL ของ license
 *
 * Unless required by applicable law or agreed to in writing, software // ถ้าไม่ได้ระบุไว้เป็นลายลักษณ์อักษร
 * distributed under the License is distributed on an "AS IS" BASIS, // ซอฟต์แวร์นี้แจกแบบ "AS IS"
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // ไม่มีการรับประกันใดๆ
 * See the License for the specific language governing permissions and // ดูรายละเอียดใน license
 * limitations under the License. // ข้อจำกัดตาม license
 */
package com.example.project_furnitureapp.samplerender; // ประกาศ package ของไฟล์นี้

import android.opengl.GLES30; // import GLES30 สำหรับใช้งาน OpenGL ES 3.0
import android.util.Log; // import Log สำหรับเขียน log
import de.javagl.obj.Obj; // import Obj สำหรับอ่านไฟล์ .obj
import de.javagl.obj.ObjData; // import ObjData สำหรับดึงข้อมูลจาก obj
import de.javagl.obj.ObjReader; // import ObjReader สำหรับอ่านไฟล์ obj
import de.javagl.obj.ObjUtils; // import ObjUtils สำหรับแปลง obj
import java.io.Closeable; // import Closeable สำหรับปิด resource
import java.io.IOException; // import IOException สำหรับจัดการ exception
import java.io.InputStream; // import InputStream สำหรับอ่านไฟล์
import java.nio.FloatBuffer; // import FloatBuffer สำหรับเก็บข้อมูล float array
import java.nio.IntBuffer; // import IntBuffer สำหรับเก็บข้อมูล int array

/**
 * A collection of vertices, faces, and other attributes that define how to render a 3D object.
 *
 * <p>To render the mesh, use {@link SampleRender#draw()}.
 */
public class Mesh implements Closeable { // ประกาศคลาส Mesh และ implement Closeable
  private static final String TAG = Mesh.class.getSimpleName(); // ตัวแปร TAG สำหรับ log

  /**
   * The kind of primitive to render.
   *
   * <p>This determines how the data in {@link VertexBuffer}s are interpreted. See <a
   * href="https://www.khronos.org/opengl/wiki/Primitive">here</a> for more on how primitives
   * behave.
   */
  public enum PrimitiveMode { // enum สำหรับกำหนดรูปแบบ primitive ที่จะวาด
    POINTS(GLES30.GL_POINTS), // วาดแบบจุด
    LINE_STRIP(GLES30.GL_LINE_STRIP), // วาดแบบเส้นต่อเนื่อง
    LINE_LOOP(GLES30.GL_LINE_LOOP), // วาดแบบเส้นปิด loop
    LINES(GLES30.GL_LINES), // วาดแบบเส้น
    TRIANGLE_STRIP(GLES30.GL_TRIANGLE_STRIP), // วาดแบบแถบสามเหลี่ยม
    TRIANGLE_FAN(GLES30.GL_TRIANGLE_FAN), // วาดแบบพัดสามเหลี่ยม
    TRIANGLES(GLES30.GL_TRIANGLES); // วาดแบบสามเหลี่ยม

    /* package-private */
    final int glesEnum; // ตัวแปรเก็บค่า enum ของ OpenGL

    private PrimitiveMode(int glesEnum) { // constructor ของ enum
      this.glesEnum = glesEnum; // กำหนดค่า
    }
  }

  private final int[] vertexArrayId = {0}; // ตัวแปรเก็บ id ของ vertex array
  private final PrimitiveMode primitiveMode; // ตัวแปรเก็บ primitive mode
  private final IndexBuffer indexBuffer; // ตัวแปรเก็บ index buffer
  private final VertexBuffer[] vertexBuffers; // ตัวแปรเก็บ vertex buffer หลายตัว

  /**
   * Construct a {@link Mesh}.
   *
   * <p>The data in the given {@link IndexBuffer} and {@link VertexBuffer}s does not need to be
   * finalized; they may be freely changed throughout the lifetime of a {@link Mesh} using their
   * respective {@code set()} methods.
   *
   * <p>The ordering of the {@code vertexBuffers} is significant. Their array indices will
   * correspond to their attribute locations, which must be taken into account in shader code. The
   * <a href="https://www.khronos.org/opengl/wiki/Layout_Qualifier_(GLSL)">layout qualifier</a> must
   * be used in the vertex shader code to explicitly associate attributes with these indices.
   */
  public Mesh(
      SampleRender render, // ตัวแปร SampleRender
      PrimitiveMode primitiveMode, // primitive mode ที่จะใช้วาด
      IndexBuffer indexBuffer, // index buffer
      VertexBuffer[] vertexBuffers) { // array ของ vertex buffer
    if (vertexBuffers == null || vertexBuffers.length == 0) { // ถ้าไม่มี vertex buffer
      throw new IllegalArgumentException("Must pass at least one vertex buffer"); // ขว้าง exception
    }

    this.primitiveMode = primitiveMode; // กำหนด primitive mode
    this.indexBuffer = indexBuffer; // กำหนด index buffer
    this.vertexBuffers = vertexBuffers; // กำหนด vertex buffer

    try {
      // Create vertex array
      GLES30.glGenVertexArrays(1, vertexArrayId, 0); // สร้าง vertex array
      GLError.maybeThrowGLException("Failed to generate a vertex array", "glGenVertexArrays"); // เช็ค error

      // Bind vertex array
      GLES30.glBindVertexArray(vertexArrayId[0]); // bind vertex array
      GLError.maybeThrowGLException("Failed to bind vertex array object", "glBindVertexArray"); // เช็ค error

      if (indexBuffer != null) { // ถ้ามี index buffer
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexBuffer.getBufferId()); // bind index buffer
      }

      for (int i = 0; i < vertexBuffers.length; ++i) { // วนลูปทุก vertex buffer
        // Bind each vertex buffer to vertex array
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffers[i].getBufferId()); // bind vertex buffer
        GLError.maybeThrowGLException("Failed to bind vertex buffer", "glBindBuffer"); // เช็ค error
        GLES30.glVertexAttribPointer(
            i, vertexBuffers[i].getNumberOfEntriesPerVertex(), GLES30.GL_FLOAT, false, 0, 0); // กำหนด pointer สำหรับ attribute
        GLError.maybeThrowGLException(
            "Failed to associate vertex buffer with vertex array", "glVertexAttribPointer"); // เช็ค error
        GLES30.glEnableVertexAttribArray(i); // เปิดใช้งาน attribute
        GLError.maybeThrowGLException(
            "Failed to enable vertex buffer", "glEnableVertexAttribArray"); // เช็ค error
      }
    } catch (Throwable t) { // ถ้ามี error
      close(); // ปิด resource
      throw t; // ขว้าง exception ต่อ
    }
  }

  /**
   * Constructs a {@link Mesh} from the given Wavefront OBJ file.
   *
   * <p>The {@link Mesh} will be constructed with three attributes, indexed in the order of local
   * coordinates (location 0, vec3), texture coordinates (location 1, vec2), and vertex normals
   * (location 2, vec3).
   */
  public static Mesh createFromAsset(SampleRender render, String assetFileName) throws IOException { // เมธอด static สำหรับสร้าง mesh จากไฟล์ .obj
    try (InputStream inputStream = render.getAssets().open(assetFileName)) { // เปิดไฟล์ asset
      Obj obj = ObjUtils.convertToRenderable(ObjReader.read(inputStream)); // อ่านและแปลง obj

      // Obtain the data from the OBJ, as direct buffers:
      IntBuffer vertexIndices = ObjData.getFaceVertexIndices(obj, /*numVerticesPerFace=*/ 3); // ดึง index ของ vertex
      FloatBuffer localCoordinates = ObjData.getVertices(obj); // ดึงตำแหน่ง vertex
      FloatBuffer textureCoordinates = ObjData.getTexCoords(obj, /*dimensions=*/ 2); // ดึง texture coordinate
      FloatBuffer normals = ObjData.getNormals(obj); // ดึง normal

      VertexBuffer[] vertexBuffers = { // สร้าง vertex buffer สำหรับแต่ละ attribute
        new VertexBuffer(render, 3, localCoordinates),
        new VertexBuffer(render, 2, textureCoordinates),
        new VertexBuffer(render, 3, normals),
      };

      IndexBuffer indexBuffer = new IndexBuffer(render, vertexIndices); // สร้าง index buffer

      return new Mesh(render, Mesh.PrimitiveMode.TRIANGLES, indexBuffer, vertexBuffers); // คืน mesh ที่สร้าง
    }
  }

  @Override
  public void close() { // เมธอดปิด resource
    if (vertexArrayId[0] != 0) { // ถ้ามี vertex array
      GLES30.glDeleteVertexArrays(1, vertexArrayId, 0); // ลบ vertex array
      GLError.maybeLogGLError(
          Log.WARN, TAG, "Failed to free vertex array object", "glDeleteVertexArrays"); // log error ถ้ามี
    }
  }

  /**
   * Draws the mesh. Don't call this directly unless you are doing low level OpenGL code; instead,
   * prefer {@link SampleRender#draw}.
   */
  public void lowLevelDraw() { // เมธอดวาด mesh (ระดับ low-level)
    if (vertexArrayId[0] == 0) { // ถ้า vertex array ถูกลบแล้ว
      throw new IllegalStateException("Tried to draw a freed Mesh"); // ขว้าง exception
    }

    GLES30.glBindVertexArray(vertexArrayId[0]); // bind vertex array
    GLError.maybeThrowGLException("Failed to bind vertex array object", "glBindVertexArray"); // เช็ค error
    if (indexBuffer == null) { // ถ้าไม่มี index buffer
      // Sanity check for debugging
      int vertexCount = vertexBuffers[0].getNumberOfVertices(); // จำนวน vertex
      for (int i = 1; i < vertexBuffers.length; ++i) { // เช็คทุก vertex buffer
        int iterCount = vertexBuffers[i].getNumberOfVertices(); // จำนวน vertex ของ buffer นี้
        if (iterCount != vertexCount) { // ถ้าไม่เท่ากัน
          throw new IllegalStateException(
              String.format(
                  "Vertex buffers have mismatching numbers of vertices ([0] has %d but [%d] has"
                      + " %d)",
                  vertexCount, i, iterCount)); // ขว้าง exception
        }
      }
      GLES30.glDrawArrays(primitiveMode.glesEnum, 0, vertexCount); // วาดแบบไม่ใช้ index
      GLError.maybeThrowGLException("Failed to draw vertex array object", "glDrawArrays"); // เช็ค error
    } else { // ถ้ามี index buffer
      GLES30.glDrawElements(
          primitiveMode.glesEnum, indexBuffer.getSize(), GLES30.GL_UNSIGNED_INT, 0); // วาดแบบใช้ index
      GLError.maybeThrowGLException(
          "Failed to draw vertex array object with indices", "glDrawElements"); // เช็ค error
    }
  }
}