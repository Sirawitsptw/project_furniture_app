/*
 * Copyright 2017 Google LLC // ข้อความลิขสิทธิ์ของ Google
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
package com.example.project_furnitureapp; // ประกาศ package ของไฟล์นี้

import android.app.Activity; // import Activity สำหรับใช้งาน activity
import android.content.Context; // import Context สำหรับใช้งาน context
import android.hardware.camera2.CameraAccessException; // import Exception สำหรับกล้อง
import android.hardware.camera2.CameraCharacteristics; // import สำหรับอ่านคุณสมบัติกล้อง
import android.hardware.camera2.CameraManager; // import สำหรับจัดการกล้อง
import android.hardware.display.DisplayManager; // import สำหรับจัดการ display
import android.hardware.display.DisplayManager.DisplayListener; // import สำหรับฟัง event display
import android.view.Display; // import สำหรับใช้งาน display
import android.view.Surface; // import สำหรับใช้งาน surface
import android.view.WindowManager; // import สำหรับจัดการ window
import com.google.ar.core.Session; // import Session ของ ARCore

/**
 * Helper to track the display rotations. In particular, the 180 degree rotations are not notified
 * by the onSurfaceChanged() callback, and thus they require listening to the android display
 * events.
 */
public final class DisplayRotationHelper implements DisplayListener { // ประกาศคลาส DisplayRotationHelper และ implement DisplayListener
  private boolean viewportChanged; // ตัวแปร flag ว่า viewport มีการเปลี่ยนแปลงหรือไม่
  private int viewportWidth; // ตัวแปรเก็บความกว้างของ viewport
  private int viewportHeight; // ตัวแปรเก็บความสูงของ viewport
  private final Display display; // ตัวแปรอ้างอิง display ปัจจุบัน
  private final DisplayManager displayManager; // ตัวแปรอ้างอิง DisplayManager
  private final CameraManager cameraManager; // ตัวแปรอ้างอิง CameraManager

  /**
   * Constructs the DisplayRotationHelper but does not register the listener yet.
   *
   * @param context the Android {@link Context}.
   */
  public DisplayRotationHelper(Context context) { // constructor รับ context
    displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE); // ดึง DisplayManager จาก system service
    cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE); // ดึง CameraManager จาก system service
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE); // ดึง WindowManager จาก system service
    display = windowManager.getDefaultDisplay(); // ดึง display หลัก
  }

  /** Registers the display listener. Should be called from {@link Activity#onResume()}. */
  public void onResume() { // เมธอดสำหรับลงทะเบียน listener เมื่อ resume
    displayManager.registerDisplayListener(this, null); // ลงทะเบียน listener กับ DisplayManager
  }

  /** Unregisters the display listener. Should be called from {@link Activity#onPause()}. */
  public void onPause() { // เมธอดสำหรับยกเลิก listener เมื่อ pause
    displayManager.unregisterDisplayListener(this); // ยกเลิก listener กับ DisplayManager
  }

  /**
   * Records a change in surface dimensions. This will be later used by {@link
   * #updateSessionIfNeeded(Session)}. Should be called from {@link
   * android.opengl.GLSurfaceView.Renderer
   * #onSurfaceChanged(javax.microedition.khronos.opengles.GL10, int, int)}.
   *
   * @param width the updated width of the surface.
   * @param height the updated height of the surface.
   */
  public void onSurfaceChanged(int width, int height) { // เมธอดสำหรับบันทึกขนาด surface ที่เปลี่ยน
    viewportWidth = width; // กำหนดความกว้างใหม่
    viewportHeight = height; // กำหนดความสูงใหม่
    viewportChanged = true; // ตั้ง flag ว่ามีการเปลี่ยนแปลง
  }

  /**
   * Updates the session display geometry if a change was posted either by {@link
   * #onSurfaceChanged(int, int)} call or by {@link #onDisplayChanged(int)} system callback. This
   * function should be called explicitly before each call to {@link Session#update()}. This
   * function will also clear the 'pending update' (viewportChanged) flag.
   *
   * @param session the {@link Session} object to update if display geometry changed.
   */
  public void updateSessionIfNeeded(Session session) { // เมธอดสำหรับอัปเดต geometry ของ session ถ้ามีการเปลี่ยนแปลง
    if (viewportChanged) { // ถ้ามีการเปลี่ยนแปลง
      int displayRotation = display.getRotation(); // อ่านค่าการหมุนของ display
      session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight); // อัปเดต geometry ให้ session
      viewportChanged = false; // เคลียร์ flag
    }
  }

  /**
   *  Returns the aspect ratio of the GL surface viewport while accounting for the display rotation
   *  relative to the device camera sensor orientation.
   */
  public float getCameraSensorRelativeViewportAspectRatio(String cameraId) { // เมธอดคืนค่า aspect ratio ของ viewport เทียบกับ sensor
    float aspectRatio; // ตัวแปรเก็บค่า aspect ratio
    int cameraSensorToDisplayRotation = getCameraSensorToDisplayRotation(cameraId); // อ่านค่าการหมุนของ sensor เทียบกับ display
    switch (cameraSensorToDisplayRotation) { // เช็คค่าการหมุน
      case 90:
      case 270:
        aspectRatio = (float) viewportHeight / (float) viewportWidth; // ถ้า 90 หรือ 270 องศา ให้สลับ width/height
        break;
      case 0:
      case 180:
        aspectRatio = (float) viewportWidth / (float) viewportHeight; // ถ้า 0 หรือ 180 องศา ใช้ width/height ปกติ
        break;
      default:
        throw new RuntimeException("Unhandled rotation: " + cameraSensorToDisplayRotation); // ถ้าไม่ตรงกับที่รองรับ ให้ throw error
    }
    return aspectRatio; // คืนค่า aspect ratio
  }

  /**
   * Returns the rotation of the back-facing camera with respect to the display. The value is one of
   * 0, 90, 180, 270.
   */
  public int getCameraSensorToDisplayRotation(String cameraId) { // เมธอดคืนค่าการหมุนของ sensor เทียบกับ display
    CameraCharacteristics characteristics; // ตัวแปรเก็บคุณสมบัติกล้อง
    try {
      characteristics = cameraManager.getCameraCharacteristics(cameraId); // อ่านคุณสมบัติกล้อง
    } catch (CameraAccessException e) {
      throw new RuntimeException("Unable to determine display orientation", e); // ถ้าอ่านไม่ได้ ให้ throw error
    }

    // Camera sensor orientation.
    int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION); // อ่านค่าการหมุนของ sensor

    // Current display orientation.
    int displayOrientation = toDegrees(display.getRotation()); // อ่านค่าการหมุนของ display

    // Make sure we return 0, 90, 180, or 270 degrees.
    return (sensorOrientation - displayOrientation + 360) % 360; // คืนค่าการหมุนที่ normalize แล้ว
  }

  private int toDegrees(int rotation) { // เมธอดแปลงค่าการหมุนจาก constant เป็นองศา
    switch (rotation) { // เช็คค่าการหมุน
      case Surface.ROTATION_0:
        return 0; // หมุน 0 องศา
      case Surface.ROTATION_90:
        return 90; // หมุน 90 องศา
      case Surface.ROTATION_180:
        return 180; // หมุน 180 องศา
      case Surface.ROTATION_270:
        return 270; // หมุน 270 องศา
      default:
        throw new RuntimeException("Unknown rotation " + rotation); // ถ้าไม่รู้จัก ให้ throw error
    }
  }

  @Override
  public void onDisplayAdded(int displayId) {} // เมธอด callback เมื่อมี display ถูกเพิ่ม (ไม่ได้ใช้งาน)

  @Override
  public void onDisplayRemoved(int displayId) {} // เมธอด callback เมื่อมี display ถูกลบ (ไม่ได้ใช้งาน)

  @Override
  public void onDisplayChanged(int displayId) { // เมธอด callback เมื่อ display มีการเปลี่ยนแปลง
    viewportChanged = true; // ตั้ง flag ว่ามีการเปลี่ยนแปลง
  }
}