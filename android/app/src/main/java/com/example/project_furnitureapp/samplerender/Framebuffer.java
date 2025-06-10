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
import java.io.Closeable; // import Closeable สำหรับปิด resource

/** A framebuffer associated with a texture. */ // คำอธิบายคลาส: framebuffer ที่ผูกกับ texture
public class Framebuffer implements Closeable { // ประกาศคลาส Framebuffer และ implement Closeable
  private static final String TAG = Framebuffer.class.getSimpleName(); // ตัวแปร TAG สำหรับ log

  private final int[] framebufferId = {0}; // ตัวแปรเก็บ id ของ framebuffer (array สำหรับ glGenFramebuffers)
  private final Texture colorTexture; // ตัวแปรเก็บ texture สำหรับสี
  private final Texture depthTexture; // ตัวแปรเก็บ texture สำหรับ depth
  private int width = -1; // ตัวแปรเก็บความกว้างของ framebuffer
  private int height = -1; // ตัวแปรเก็บความสูงของ framebuffer

  /**
   * Constructs a {@link Framebuffer} which renders internally to a texture.
   *
   * <p>In order to render to the {@link Framebuffer}, use {@link SampleRender#draw(Mesh, Shader,
   * Framebuffer)}.
   */
  public Framebuffer(SampleRender render, int width, int height) { // constructor รับ SampleRender, ความกว้าง, ความสูง
    try {
      colorTexture =
          new Texture(
              render,
              Texture.Target.TEXTURE_2D,
              Texture.WrapMode.CLAMP_TO_EDGE,
              /*useMipmaps=*/ false); // สร้าง texture สำหรับสี
      depthTexture =
          new Texture(
              render,
              Texture.Target.TEXTURE_2D,
              Texture.WrapMode.CLAMP_TO_EDGE,
              /*useMipmaps=*/ false); // สร้าง texture สำหรับ depth

      // Set parameters of the depth texture so that it's readable by shaders.
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTexture.getTextureId()); // bind depth texture
      GLError.maybeThrowGLException("Failed to bind depth texture", "glBindTexture"); // เช็ค error
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_NONE); // ตั้งค่า compare mode
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri"); // เช็ค error
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST); // ตั้งค่า min filter
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri"); // เช็ค error
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST); // ตั้งค่า mag filter
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri"); // เช็ค error

      // Set initial dimensions.
      resize(width, height); // กำหนดขนาดเริ่มต้น

      // Create framebuffer object and bind to the color and depth textures.
      GLES30.glGenFramebuffers(1, framebufferId, 0); // สร้าง framebuffer
      GLError.maybeThrowGLException("Framebuffer creation failed", "glGenFramebuffers"); // เช็ค error
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebufferId[0]); // bind framebuffer
      GLError.maybeThrowGLException("Failed to bind framebuffer", "glBindFramebuffer"); // เช็ค error
      GLES30.glFramebufferTexture2D(
          GLES30.GL_FRAMEBUFFER,
          GLES30.GL_COLOR_ATTACHMENT0,
          GLES30.GL_TEXTURE_2D,
          colorTexture.getTextureId(),
          /*level=*/ 0); // bind color texture กับ framebuffer
      GLError.maybeThrowGLException(
          "Failed to bind color texture to framebuffer", "glFramebufferTexture2D"); // เช็ค error
      GLES30.glFramebufferTexture2D(
          GLES30.GL_FRAMEBUFFER,
          GLES30.GL_DEPTH_ATTACHMENT,
          GLES30.GL_TEXTURE_2D,
          depthTexture.getTextureId(),
          /*level=*/ 0); // bind depth texture กับ framebuffer
      GLError.maybeThrowGLException(
          "Failed to bind depth texture to framebuffer", "glFramebufferTexture2D"); // เช็ค error

      int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER); // ตรวจสอบสถานะ framebuffer
      if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) { // ถ้าไม่สมบูรณ์
        throw new IllegalStateException("Framebuffer construction not complete: code " + status); // ขว้าง exception
      }
    } catch (Throwable t) { // ถ้ามี error
      close(); // ปิด resource
      throw t; // ขว้าง exception ต่อ
    }
  }

  @Override
  public void close() { // เมธอดปิด resource
    if (framebufferId[0] != 0) { // ถ้ามี framebuffer
      GLES30.glDeleteFramebuffers(1, framebufferId, 0); // ลบ framebuffer
      GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free framebuffer", "glDeleteFramebuffers"); // log error ถ้ามี
      framebufferId[0] = 0; // เซ็ต id เป็น 0
    }
    colorTexture.close(); // ปิด color texture
    depthTexture.close(); // ปิด depth texture
  }

  /** Resizes the framebuffer to the given dimensions. */ // คำอธิบายเมธอด: ปรับขนาด framebuffer
  public void resize(int width, int height) { // เมธอดปรับขนาด framebuffer
    if (this.width == width && this.height == height) { // ถ้าขนาดเท่าเดิม
      return; // ไม่ต้องทำอะไร
    }
    this.width = width; // กำหนดความกว้างใหม่
    this.height = height; // กำหนดความสูงใหม่

    // Color texture
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture.getTextureId()); // bind color texture
    GLError.maybeThrowGLException("Failed to bind color texture", "glBindTexture"); // เช็ค error
    GLES30.glTexImage2D(
        GLES30.GL_TEXTURE_2D,
        /*level=*/ 0,
        GLES30.GL_RGBA,
        width,
        height,
        /*border=*/ 0,
        GLES30.GL_RGBA,
        GLES30.GL_UNSIGNED_BYTE,
        /*pixels=*/ null); // กำหนดขนาดและ format ของ color texture
    GLError.maybeThrowGLException("Failed to specify color texture format", "glTexImage2D"); // เช็ค error

    // Depth texture
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTexture.getTextureId()); // bind depth texture
    GLError.maybeThrowGLException("Failed to bind depth texture", "glBindTexture"); // เช็ค error
    GLES30.glTexImage2D(
        GLES30.GL_TEXTURE_2D,
        /*level=*/ 0,
        GLES30.GL_DEPTH_COMPONENT32F,
        width,
        height,
        /*border=*/ 0,
        GLES30.GL_DEPTH_COMPONENT,
        GLES30.GL_FLOAT,
        /*pixels=*/ null); // กำหนดขนาดและ format ของ depth texture
    GLError.maybeThrowGLException("Failed to specify depth texture format", "glTexImage2D"); // เช็ค error
  }

  /** Returns the color texture associated with this framebuffer. */ // เมธอดคืนค่า color texture
  public Texture getColorTexture() {
    return colorTexture;
  }

  /** Returns the depth texture associated with this framebuffer. */ // เมธอดคืนค่า depth texture
  public Texture getDepthTexture() {
    return depthTexture;
  }

  /** Returns the width of the framebuffer. */ // เมธอดคืนค่าความกว้าง
  public int getWidth() {
    return width;
  }

  /** Returns the height of the framebuffer. */ // เมธอดคืนค่าความสูง
  public int getHeight() {
    return height;
  }

  /* package-private */
  int getFramebufferId() { // เมธอดคืนค่า framebuffer id
    return framebufferId[0];
  }
}