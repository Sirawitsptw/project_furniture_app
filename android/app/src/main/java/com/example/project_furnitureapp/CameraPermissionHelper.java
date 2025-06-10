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

import android.Manifest; // import สำหรับใช้งาน constant permission กล้อง
import android.app.Activity; // import Activity สำหรับใช้งาน activity
import android.content.Intent; // import Intent สำหรับเปิดหน้าต่างใหม่
import android.content.pm.PackageManager; // import สำหรับตรวจสอบ permission
import android.net.Uri; // import Uri สำหรับสร้างข้อมูล package
import android.provider.Settings; // import Settings สำหรับเปิดหน้าตั้งค่า
import androidx.core.app.ActivityCompat; // import ActivityCompat สำหรับขอ permission
import androidx.core.content.ContextCompat; // import ContextCompat สำหรับเช็ค permission

/** Helper to ask camera permission. */ // คำอธิบายคลาส: ตัวช่วยขอ permission กล้อง
public final class CameraPermissionHelper { // ประกาศคลาส CameraPermissionHelper แบบ final (ห้ามสืบทอด)
  private static final int CAMERA_PERMISSION_CODE = 0; // กำหนดรหัส request สำหรับ permission กล้อง
  private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA; // กำหนดชื่อ permission กล้อง

  /** Check to see we have the necessary permissions for this app. */ // คำอธิบายเมธอด: เช็คว่ามี permission กล้องหรือไม่
  public static boolean hasCameraPermission(Activity activity) { // เมธอดเช็ค permission กล้อง
    return ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) // เช็ค permission กล้อง
        == PackageManager.PERMISSION_GRANTED; // คืน true ถ้าได้รับอนุญาต
  }

  /** Check to see we have the necessary permissions for this app, and ask for them if we don't. */ // คำอธิบายเมธอด: ขอ permission ถ้ายังไม่ได้รับ
  public static void requestCameraPermission(Activity activity) { // เมธอดขอ permission กล้อง
    ActivityCompat.requestPermissions( // เรียกขอ permission
        activity, new String[] {CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE); // ขอ permission กล้อง ด้วยรหัสที่กำหนด
  }

  /** Check to see if we need to show the rationale for this permission. */ // คำอธิบายเมธอด: เช็คว่าต้องอธิบายเหตุผลการขอ permission หรือไม่
  public static boolean shouldShowRequestPermissionRationale(Activity activity) { // เมธอดเช็คว่าต้องแสดง rationale หรือไม่
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA_PERMISSION); // คืน true ถ้าต้องแสดง
  }

  /** Launch Application Setting to grant permission. */ // คำอธิบายเมธอด: เปิดหน้าตั้งค่าแอปเพื่อให้ผู้ใช้กดอนุญาตเอง
  public static void launchPermissionSettings(Activity activity) { // เมธอดเปิดหน้าตั้งค่าแอป
    Intent intent = new Intent(); // สร้าง intent ใหม่
    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); // กำหนด action ให้เปิดหน้าตั้งค่าแอป
    intent.setData(Uri.fromParts("package", activity.getPackageName(), null)); // กำหนดข้อมูล package ของแอปนี้
    activity.startActivity(intent); // สั่งเปิดหน้าตั้งค่า
  }
}