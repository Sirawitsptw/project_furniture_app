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
import android.opengl.GLException; // import GLException สำหรับจัดการ error ของ OpenGL
import android.opengl.GLU; // import GLU สำหรับแปลง error code เป็นข้อความ
import android.util.Log; // import Log สำหรับเขียน log
import java.util.ArrayList; // import ArrayList สำหรับเก็บ error code
import java.util.Iterator; // import Iterator สำหรับวนลูป
import java.util.List; // import List สำหรับเก็บ error code

/** Module for handling OpenGL errors. */ // คำอธิบายคลาส: โมดูลสำหรับจัดการ error ของ OpenGL
public class GLError { // ประกาศคลาส GLError
  /** Throws a {@link GLException} if a GL error occurred. */ // เมธอด: ถ้ามี error ให้ throw exception
  public static void maybeThrowGLException(String reason, String api) { // เมธอดตรวจสอบและ throw exception ถ้ามี error
    List<Integer> errorCodes = getGlErrors(); // ดึง error code ทั้งหมด
    if (errorCodes != null) { // ถ้ามี error
      throw new GLException(errorCodes.get(0), formatErrorMessage(reason, api, errorCodes)); // ขว้าง exception พร้อมข้อความ
    }
  }

  /** Logs a message with the given logcat priority if a GL error occurred. */ // เมธอด: log ข้อความถ้ามี error
  public static void maybeLogGLError(int priority, String tag, String reason, String api) { // เมธอด log error ถ้ามี
    List<Integer> errorCodes = getGlErrors(); // ดึง error code ทั้งหมด
    if (errorCodes != null) { // ถ้ามี error
      Log.println(priority, tag, formatErrorMessage(reason, api, errorCodes)); // log ข้อความ
    }
  }

  private static String formatErrorMessage(String reason, String api, List<Integer> errorCodes) { // เมธอดสร้างข้อความ error
    StringBuilder builder = new StringBuilder(String.format("%s: %s: ", reason, api)); // สร้างข้อความเริ่มต้น
    Iterator<Integer> iterator = errorCodes.iterator(); // เตรียมวนลูป error code
    while (iterator.hasNext()) { // วนลูปทุก error code
      int errorCode = iterator.next(); // ดึง error code
      builder.append(String.format("%s (%d)", GLU.gluErrorString(errorCode), errorCode)); // แปลงเป็นข้อความ
      if (iterator.hasNext()) { // ถ้ายังมี error code ต่อไป
        builder.append(", "); // เพิ่ม comma
      }
    }
    return builder.toString(); // คืนค่าข้อความ
  }

  private static List<Integer> getGlErrors() { // เมธอดดึง error code ทั้งหมดจาก OpenGL
    int errorCode = GLES30.glGetError(); // ดึง error code แรก
    // Shortcut for no errors
    if (errorCode == GLES30.GL_NO_ERROR) { // ถ้าไม่มี error
      return null; // คืนค่า null
    }
    List<Integer> errorCodes = new ArrayList<>(); // สร้าง list สำหรับเก็บ error code
    errorCodes.add(errorCode); // เพิ่ม error code แรก
    while (true) { // วนลูปดึง error code จนกว่าจะหมด
      errorCode = GLES30.glGetError(); // ดึง error code ถัดไป
      if (errorCode == GLES30.GL_NO_ERROR) { // ถ้าไม่มี error แล้ว
        break; // ออกจากลูป
      }
      errorCodes.add(errorCode); // เพิ่ม error code ลง list
    }
    return errorCodes; // คืนค่า list ของ error code
  }

  private GLError() {} // constructor private เพื่อไม่ให้สร้าง instance
}