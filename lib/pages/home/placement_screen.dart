// Import ไลบรารีที่จำเป็น
import 'dart:typed_data'; // ใช้สำหรับ Uint8List ซึ่งเป็นข้อมูลรูปภาพที่ส่งมาจากหน้า AR
import 'package:flutter/material.dart'; // ไลบรารีหลักสำหรับสร้าง UI
import 'package:model_viewer_plus/model_viewer_plus.dart'; // Import Widget สำหรับแสดงผลโมเดล 3 มิติ

// Import เพื่อให้รู้จักคลาส MeasurementData สำหรับแสดงผลการวัดซ้ำ
import 'ar_measure_screen.dart';

// StatefulWidget หลักของหน้าจอวางโมเดล
class PlacementScreen extends StatefulWidget {
  // final: ตัวแปรเหล่านี้รับค่ามาจากหน้าก่อนหน้า (ArMeasureScreen) และจะไม่เปลี่ยนแปลง
  // ข้อมูลรูปภาพพื้นหลังที่ถ่ายมาจากหน้า AR ในรูปแบบ raw bytes
  final Uint8List backgroundImage;
  // URL ของโมเดล 3D ที่จะนำมาแสดง
  final String modelUrl;
  // ข้อมูลการวัดทั้งหมด เพื่อนำมาแสดงผลซ้ำบนภาพพื้นหลัง
  final List<MeasurementData> measurements;
  // ขนาดอ้างอิงของโมเดล (อาจจะเป็น null) เพื่อใช้คำนวณมุมกล้องเริ่มต้น
  final double? modelDimension;

  // Constructor ของ Widget, รับค่าทั้งหมดที่กล่าวมาข้างต้น
  const PlacementScreen({
    super.key,
    required this.backgroundImage,
    required this.modelUrl,
    required this.measurements,
    this.modelDimension,
  });

  @override
  State<PlacementScreen> createState() => _PlacementScreenState();
}

// State class ของ PlacementScreen
class _PlacementScreenState extends State<PlacementScreen> {
  // Offset? (nullable): เก็บพิกัด (x, y) บนหน้าจอที่ผู้ใช้แตะเพื่อวางโมเดล
  // ถ้าเป็น null หมายความว่ายังไม่มีการวางโมเดล
  Offset? _modelPosition;
  // ขนาด (ความกว้างและความสูง) ของกรอบ ModelViewer เป็นค่าคงที่
  final double _modelViewerSize = 400.0;

  // เมธอดที่จะถูกเรียกเมื่อผู้ใช้ยกนิ้วขึ้นจากการแตะหน้าจอ
  void _handleScreenTap(TapUpDetails details) {
    // อัปเดต state ของแอป
    setState(() {
      // บันทึกตำแหน่งที่แตะ (localPosition คือพิกัดเทียบกับ Widget ที่โดนแตะ)
      _modelPosition = details.localPosition;
    });
  }

  // ฟังก์ชันเสริมสำหรับพยายามดึงชื่อไฟล์จาก URL เพื่อนำมาแสดงผล
  String _getDisplayName(String? url) {
    // ถ้า URL เป็น null หรือว่างเปล่า ให้คืนค่า "Unknown Model"
    if (url == null || url.isEmpty) return "Unknown Model";
    try {
      // ใช้ Uri.parse เพื่อแยกส่วนประกอบของ URL
      // .pathSegments จะได้ List ของส่วนต่างๆ ใน path
      // .lastWhere(...) คือการหาส่วนสุดท้ายของ path ที่ไม่ว่างเปล่า (ชื่อไฟล์)
      return Uri.parse(url)
          .pathSegments
          .lastWhere((s) => s.isNotEmpty, orElse: () => "Model");
    } catch (_) {
      // ถ้าการ parse URL ล้มเหลว ให้คืนค่า "Model"
      return "Model";
    }
  }

  // เมธอด build UI หลักของหน้าจอ
  @override
  Widget build(BuildContext context) {
    // คำนวณระยะห่างของกล้องใน ModelViewer แบบไดนามิก
    // เพื่อให้โมเดลมีขนาดเริ่มต้นที่พอดีกับหน้าจอ
    // สูตรนี้เป็นเพียงตัวอย่าง สามารถปรับได้ตามความเหมาะสม
    final double cameraRadius = 1.0 + (widget.modelDimension ?? 1.5);

    // คืนค่า Scaffold ซึ่งเป็นโครงสร้างหลักของหน้าจอ
    return Scaffold(
      appBar: AppBar(
        // แสดงชื่อโมเดลที่ได้จาก _getDisplayName
        title: Text('Place Model: ${_getDisplayName(widget.modelUrl)}'),
        // actions คือ Widget ที่จะแสดงท้าย AppBar
        actions: [
          // จะแสดงปุ่มลบก็ต่อเมื่อมีการวางโมเดลแล้ว (_modelPosition != null)
          if (_modelPosition != null)
            IconButton(
              icon: const Icon(Icons.clear),
              // เมื่อกดปุ่ม ให้ตั้งค่า _modelPosition เป็น null และอัปเดต UI เพื่อซ่อนโมเดล
              onPressed: () => setState(() => _modelPosition = null),
              tooltip: 'Clear Model',
            )
        ],
      ),
      // GestureDetector: Widget ที่ใช้ดักจับ gesture ต่างๆ ในที่นี้คือการแตะ
      body: GestureDetector(
        onTapUp:
            _handleScreenTap, // เมื่อมีการแตะ ให้เรียกเมธอด _handleScreenTap
        // Stack: Widget ที่ใช้วาง UI ซ้อนกันเป็นชั้นๆ
        child: Stack(
          fit: StackFit.expand, // ให้ Stack ขยายเต็มพื้นที่ของ parent
          children: [
            // ชั้นที่ 1: ภาพพื้นหลัง
            Image.memory(
              widget
                  .backgroundImage, // แสดงรูปภาพจากข้อมูล Uint8List ที่ได้รับมา
              fit: BoxFit
                  .cover, // ให้ภาพขยายเต็มพื้นที่โดยรักษาสัดส่วน (อาจมีบางส่วนล้น)
              gaplessPlayback: true, // ช่วยให้ภาพไม่กระพริบเมื่อมีการ setState
            ),
            // ชั้นที่ 2: ป้ายการวัด (วาดซ้ำเพื่อให้เกิดความต่อเนื่อง)
            // ใช้ '...' (spread operator) เพื่อนำ Widget ทั้งหมดใน List มาใส่ใน Stack
            ...widget.measurements
                .where((data) =>
                    data.isVisibleOnScreen) // กรองเอาเฉพาะข้อมูลที่ควรแสดง
                .map((data) {
              // แปลงข้อมูลแต่ละชิ้นให้เป็น Positioned Widget
              return Positioned(
                left: data.midPointScreenX, // กำหนดตำแหน่งแกน X
                top: data.midPointScreenY, // กำหนดตำแหน่งแกน Y
                child: FractionalTranslation(
                  translation: const Offset(-0.5,
                      -0.5), // จัดให้จุดกึ่งกลางของ Widget อยู่ตรงกับพิกัด
                  child: IgnorePointer(
                    // ทำให้ Widget นี้ไม่รับการสัมผัส
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                          color: Colors.deepPurple.withOpacity(0.9),
                          borderRadius: BorderRadius.circular(4),
                          boxShadow: [
                            BoxShadow(
                                color: Colors.black.withOpacity(0.5),
                                blurRadius: 4,
                                offset: const Offset(1.5, 1.5))
                          ]),
                      child: Text(
                        '${data.label}: ${data.distance.toStringAsFixed(2)} m',
                        style: const TextStyle(
                            color: Colors.white,
                            fontSize: 14,
                            fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                ),
              );
            }).toList(),
            // ชั้นที่ 3: โมเดล 3 มิติ (จะแสดงก็ต่อเมื่อ _modelPosition ไม่ใช่ null)
            if (_modelPosition != null)
              Positioned(
                // กำหนดตำแหน่ง left, top ของกรอบโมเดล
                // โดยลบครึ่งหนึ่งของขนาด _modelViewerSize เพื่อให้จุดที่ผู้ใช้แตะเป็นจุดศูนย์กลาง
                left: _modelPosition!.dx - (_modelViewerSize / 2),
                top: _modelPosition!.dy - (_modelViewerSize / 2),
                // SizedBox ใช้เพื่อกำหนดขนาดของ ModelViewer
                child: SizedBox(
                  width: _modelViewerSize,
                  height: _modelViewerSize,
                  // ModelViewer: Widget สำหรับแสดงโมเดล 3 มิติ
                  child: ModelViewer(
                    src: widget.modelUrl, // URL ของไฟล์โมเดล
                    alt: "3D Model", // ข้อความอธิบาย
                    cameraControls: true, // อนุญาตให้ผู้ใช้หมุนดูโมเดลได้
                    disableZoom: true, // ปิดการซูม
                    backgroundColor: Colors.transparent, // พื้นหลังโปร่งใส
                    interactionPrompt: InteractionPrompt
                        .none, // ไม่ต้องแสดง prompt แนะนำการใช้งาน
                    cameraOrbit:
                        '0deg 75deg ${cameraRadius}m', // กำหนดมุมกล้องและระยะห่างเริ่มต้น
                  ),
                ),
              ),
            // ชั้นที่ 4: ข้อความแนะนำ (จะแสดงก็ต่อเมื่อยังไม่มีการวางโมเดล)
            if (_modelPosition == null)
              Align(
                alignment: Alignment.bottomCenter, // จัดตำแหน่งไว้ด้านล่างสุด
                child: Container(
                  margin: const EdgeInsets.all(20),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                      color: Colors.black.withOpacity(0.75),
                      borderRadius: BorderRadius.circular(8)),
                  child: const Text('Tap anywhere to place the model',
                      style: TextStyle(color: Colors.white, fontSize: 16),
                      textAlign: TextAlign.center),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
