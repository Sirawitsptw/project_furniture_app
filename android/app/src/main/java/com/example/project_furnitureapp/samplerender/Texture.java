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

import android.graphics.Bitmap; // import Bitmap สำหรับจัดการรูปภาพ
import android.graphics.BitmapFactory; // import BitmapFactory สำหรับ decode รูปภาพ
import android.opengl.GLES11Ext; // import GLES11Ext สำหรับใช้งาน OpenGL extension
import android.opengl.GLES30; // import GLES30 สำหรับใช้งาน OpenGL ES 3.0
import android.util.Log; // import Log สำหรับเขียน log
import java.io.Closeable; // import Closeable สำหรับปิด resource
import java.io.IOException; // import IOException สำหรับจัดการ exception
import java.nio.ByteBuffer; // import ByteBuffer สำหรับเก็บข้อมูล byte

/** A GPU-side texture. */ // คำอธิบายคลาส: texture ที่อยู่บน GPU
public class Texture implements Closeable { // ประกาศคลาส Texture และ implement Closeable
  private static final String TAG = Texture.class.getSimpleName(); // ตัวแปร TAG สำหรับ log

  private final int[] textureId = {0}; // ตัวแปรเก็บ texture id ของ OpenGL
  private final Target target; // ตัวแปรเก็บประเภท target ของ texture

  /**
   * Describes the way the texture's edges are rendered.
   *
   * @see <a
   *     href="https://www.khronos.org/registry/OpenGL-Refpages/es3.0/html/glTexParameter.xhtml">GL_TEXTURE_WRAP_S</a>.
   */
  public enum WrapMode { // enum สำหรับกำหนดรูปแบบการ wrap ขอบ texture
    CLAMP_TO_EDGE(GLES30.GL_CLAMP_TO_EDGE), // ขอบติดขอบ
    MIRRORED_REPEAT(GLES30.GL_MIRRORED_REPEAT), // ขอบสะท้อน
    REPEAT(GLES30.GL_REPEAT); // ขอบวนซ้ำ

    /* package-private */
    final int glesEnum; // ตัวแปรเก็บค่า enum ของ OpenGL

    private WrapMode(int glesEnum) { // constructor ของ enum
      this.glesEnum = glesEnum; // กำหนดค่า
    }
  }

  /**
   * Describes the target this texture is bound to.
   *
   * @see <a
   *     href="https://www.khronos.org/registry/OpenGL-Refpages/es3.0/html/glBindTexture.xhtml">glBindTexture</a>.
   */
  public enum Target { // enum สำหรับกำหนดประเภท target ของ texture
    TEXTURE_2D(GLES30.GL_TEXTURE_2D), // texture 2D
    TEXTURE_EXTERNAL_OES(GLES11Ext.GL_TEXTURE_EXTERNAL_OES), // texture จาก external OES
    TEXTURE_CUBE_MAP(GLES30.GL_TEXTURE_CUBE_MAP); // texture แบบ cube map

    final int glesEnum; // ตัวแปรเก็บค่า enum ของ OpenGL

    private Target(int glesEnum) { // constructor ของ enum
      this.glesEnum = glesEnum; // กำหนดค่า
    }
  }

  /**
   * Describes the color format of the texture.
   *
   * @see <a
   *     href="https://www.khronos.org/registry/OpenGL-Refpages/es3.0/html/glTexImage2D.xhtml">glTexImage2d</a>.
   */
  public enum ColorFormat { // enum สำหรับกำหนดรูปแบบสีของ texture
    LINEAR(GLES30.GL_RGBA8), // สี RGBA ปกติ
    SRGB(GLES30.GL_SRGB8_ALPHA8); // สีแบบ sRGB

    final int glesEnum; // ตัวแปรเก็บค่า enum ของ OpenGL

    private ColorFormat(int glesEnum) { // constructor ของ enum
      this.glesEnum = glesEnum; // กำหนดค่า
    }
  }

  /**
   * Construct an empty {@link Texture}.
   *
   * <p>Since {@link Texture}s created in this way are not populated with data, this method is
   * mostly only useful for creating {@link Target.TEXTURE_EXTERNAL_OES} textures. See {@link
   * #createFromAsset} if you want a texture with data.
   */
  public Texture(SampleRender render, Target target, WrapMode wrapMode) { // constructor สำหรับสร้าง texture ว่าง
    this(render, target, wrapMode, /*useMipmaps=*/ true); // เรียก constructor อีกตัว โดยใช้ mipmaps
  }

  public Texture(SampleRender render, Target target, WrapMode wrapMode, boolean useMipmaps) { // constructor หลัก
    this.target = target; // กำหนด target

    GLES30.glGenTextures(1, textureId, 0); // สร้าง texture id ใหม่
    GLError.maybeThrowGLException("Texture creation failed", "glGenTextures"); // เช็ค error

    int minFilter = useMipmaps ? GLES30.GL_LINEAR_MIPMAP_LINEAR : GLES30.GL_LINEAR; // เลือก filter ตามว่าใช้ mipmaps หรือไม่

    try {
      GLES30.glBindTexture(target.glesEnum, textureId[0]); // bind texture
      GLError.maybeThrowGLException("Failed to bind texture", "glBindTexture"); // เช็ค error
      GLES30.glTexParameteri(target.glesEnum, GLES30.GL_TEXTURE_MIN_FILTER, minFilter); // ตั้งค่า min filter
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri"); // เช็ค error
      GLES30.glTexParameteri(target.glesEnum, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR); // ตั้งค่า mag filter
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri"); // เช็ค error

      GLES30.glTexParameteri(target.glesEnum, GLES30.GL_TEXTURE_WRAP_S, wrapMode.glesEnum); // ตั้งค่า wrap S
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri"); // เช็ค error
      GLES30.glTexParameteri(target.glesEnum, GLES30.GL_TEXTURE_WRAP_T, wrapMode.glesEnum); // ตั้งค่า wrap T
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri"); // เช็ค error
    } catch (Throwable t) {
      close(); // ถ้ามี error ให้ปิด resource
      throw t; // ขว้าง exception ต่อ
    }
  }

  /** Create a texture from the given asset file name. */ // คำอธิบายเมธอด: สร้าง texture จาก asset
  public static Texture createFromAsset(
      SampleRender render, String assetFileName, WrapMode wrapMode, ColorFormat colorFormat)
      throws IOException { // เมธอด static สำหรับสร้าง texture จาก asset
    Texture texture = new Texture(render, Target.TEXTURE_2D, wrapMode); // สร้าง texture ว่าง
    Bitmap bitmap = null; // ตัวแปรเก็บ bitmap
    try {
      // The following lines up to glTexImage2D could technically be replaced with
      // GLUtils.texImage2d, but this method does not allow for loading sRGB images.

      // Load and convert the bitmap and copy its contents to a direct ByteBuffer. Despite its name,
      // the ARGB_8888 config is actually stored in RGBA order.
      bitmap =
          convertBitmapToConfig(
              BitmapFactory.decodeStream(render.getAssets().open(assetFileName)),
              Bitmap.Config.ARGB_8888); // โหลดและแปลง bitmap เป็น ARGB_8888
      ByteBuffer buffer = ByteBuffer.allocateDirect(bitmap.getByteCount()); // สร้าง buffer สำหรับเก็บข้อมูล bitmap
      bitmap.copyPixelsToBuffer(buffer); // คัดลอก pixel ลง buffer
      buffer.rewind(); // รีเซ็ต pointer ของ buffer

      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture.getTextureId()); // bind texture
      GLError.maybeThrowGLException("Failed to bind texture", "glBindTexture"); // เช็ค error
      GLES30.glTexImage2D(
          GLES30.GL_TEXTURE_2D,
          /*level=*/ 0,
          colorFormat.glesEnum,
          bitmap.getWidth(),
          bitmap.getHeight(),
          /*border=*/ 0,
          GLES30.GL_RGBA,
          GLES30.GL_UNSIGNED_BYTE,
          buffer); // ส่งข้อมูล texture ไปยัง GPU
      GLError.maybeThrowGLException("Failed to populate texture data", "glTexImage2D"); // เช็ค error
      GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D); // สร้าง mipmaps
      GLError.maybeThrowGLException("Failed to generate mipmaps", "glGenerateMipmap"); // เช็ค error
    } catch (Throwable t) {
      texture.close(); // ถ้ามี error ให้ปิด resource
      throw t; // ขว้าง exception ต่อ
    } finally {
      if (bitmap != null) {
        bitmap.recycle(); // คืน memory ของ bitmap
      }
    }
    return texture; // คืนค่า texture ที่สร้าง
  }

  @Override
  public void close() { // เมธอดปิด resource
    if (textureId[0] != 0) { // ถ้ามี texture id
      GLES30.glDeleteTextures(1, textureId, 0); // ลบ texture ออกจาก GPU
      GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free texture", "glDeleteTextures"); // log ถ้ามี error
      textureId[0] = 0; // เซ็ต id เป็น 0
    }
  }

  /** Retrieve the native texture ID. */ // คำอธิบายเมธอด: คืนค่า texture id
  public int getTextureId() {
    return textureId[0]; // คืนค่า texture id
  }

  /* package-private */
  Target getTarget() { // เมธอดคืนค่า target ของ texture
    return target; // คืนค่า target
  }

  private static Bitmap convertBitmapToConfig(Bitmap bitmap, Bitmap.Config config) { // เมธอดแปลง config ของ bitmap
    // We use this method instead of BitmapFactory.Options.outConfig to support a minimum of Android
    // API level 24.
    if (bitmap.getConfig() == config) { // ถ้า config ตรงกัน
      return bitmap; // คืน bitmap เดิม
    }
    Bitmap result = bitmap.copy(config, /*isMutable=*/ false); // สร้าง bitmap ใหม่ด้วย config ที่ต้องการ
    bitmap.recycle(); // คืน memory ของ bitmap เดิม
    return result; // คืน bitmap ใหม่
  }
}