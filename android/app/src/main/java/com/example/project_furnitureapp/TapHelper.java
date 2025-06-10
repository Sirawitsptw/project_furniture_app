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

import android.content.Context; // import Context สำหรับใช้งาน context
import android.view.GestureDetector; // import GestureDetector สำหรับตรวจจับ gesture
import android.view.MotionEvent; // import MotionEvent สำหรับ event การสัมผัส
import android.view.View; // import View สำหรับใช้งาน view
import android.view.View.OnTouchListener; // import OnTouchListener สำหรับรับ event สัมผัส
import java.util.concurrent.ArrayBlockingQueue; // import ArrayBlockingQueue สำหรับ queue แบบจำกัดขนาด
import java.util.concurrent.BlockingQueue; // import BlockingQueue สำหรับ queue ที่ block ได้

/**
 * Helper to detect taps using Android GestureDetector, and pass the taps between UI thread and // คำอธิบายคลาส: ช่วยตรวจจับ tap ด้วย GestureDetector และส่ง tap ระหว่าง UI กับ render thread
 * render thread.
 */
public final class TapHelper implements OnTouchListener { // ประกาศคลาส TapHelper และ implement OnTouchListener
  private final GestureDetector gestureDetector; // ตัวแปร GestureDetector สำหรับตรวจจับ gesture
  private final BlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16); // queue สำหรับเก็บ tap สูงสุด 16 อัน

  /**
   * Creates the tap helper. // คำอธิบาย constructor
   *
   * @param context the application's context. // อธิบาย parameter context
   */
  public TapHelper(Context context) { // constructor รับ context
    gestureDetector = // สร้าง GestureDetector
        new GestureDetector(
            context, // ใช้ context ที่รับมา
            new GestureDetector.SimpleOnGestureListener() { // ใช้ listener แบบง่าย
              @Override
              public boolean onSingleTapUp(MotionEvent e) { // เมื่อมี tap เดี่ยว
                // Queue tap if there is space. Tap is lost if queue is full.
                queuedSingleTaps.offer(e); // ใส่ event ลง queue ถ้ามีที่ว่าง
                return true; // คืนค่า true ว่า event ถูกจัดการแล้ว
              }

              @Override
              public boolean onDown(MotionEvent e) { // เมื่อมีการแตะหน้าจอ
                return true; // คืนค่า true เพื่อบอกว่า event นี้สนใจ
              }
            });
  }

  /**
   * Polls for a tap. // คำอธิบายเมธอด poll
   *
   * @return if a tap was queued, a MotionEvent for the tap. Otherwise null if no taps are queued. // คืน MotionEvent ถ้ามี tap ใน queue, ถ้าไม่มีคืน null
   */
  public MotionEvent poll() { // เมธอดดึง tap ออกจาก queue
    return queuedSingleTaps.poll(); // คืนค่า tap ตัวแรกใน queue หรือ null ถ้าไม่มี
  }

  @Override
  public boolean onTouch(View view, MotionEvent motionEvent) { // เมธอด onTouch สำหรับรับ event สัมผัส
    return gestureDetector.onTouchEvent(motionEvent); // ส่ง event ให้ gestureDetector จัดการ
  }
}