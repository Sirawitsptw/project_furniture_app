// Import ไลบรารีที่จำเป็นสำหรับการทำงาน
import 'dart:ui' as ui; // ใช้สำหรับจัดการกราฟิกระดับล่าง เช่น ui.Image
import 'dart:typed_data'; // ใช้สำหรับจัดการข้อมูลที่เป็น raw bytes เช่น Uint8List สำหรับรูปภาพ
import 'package:flutter/rendering.dart'; // ใช้สำหรับเข้าถึง Render Object เช่น RenderRepaintBoundary
import 'package:flutter/material.dart'; // ไลบรารีหลักสำหรับสร้าง UI ของ Flutter
import 'package:flutter/services.dart'; // ใช้สำหรับ MethodChannel เพื่อสื่อสารกับ Native
import 'dart:io'
    show Platform; // ใช้ 'Platform' เพื่อตรวจสอบระบบปฏิบัติการ (Android/iOS)

// Import หน้าจอถัดไปที่จะนำทางไป
import 'placement_screen.dart';

// คลาสโมเดลสำหรับเก็บข้อมูลการวัดที่ได้รับมาจากฝั่ง Native
class MeasurementData {
  // ป้ายกำกับของการวัด เช่น "Distance 1"
  final String label;
  // ระยะทางที่วัดได้ในหน่วยเมตร
  final double distance;
  // พิกัดแกน X บนหน้าจอ (แบบ logical pixels) สำหรับแสดงป้ายกำกับ
  final double midPointScreenX;
  // พิกัดแกน Y บนหน้าจอ (แบบ logical pixels) สำหรับแสดงป้ายกำกับ
  final double midPointScreenY;
  // Flag เพื่อตรวจสอบว่าข้อมูลที่ได้รับมาครั้งแรกนั้นสมบูรณ์หรือไม่
  final bool initiallyValid;

  // Constructor ปกติสำหรับสร้าง object ของ MeasurementData
  MeasurementData({
    required this.label,
    required this.distance,
    required this.midPointScreenX,
    required this.midPointScreenY,
    required this.initiallyValid,
  });

  // Factory Constructor: เป็นเมธอดพิเศษสำหรับสร้าง object จากข้อมูลดิบ (Map) ที่ส่งมาจากฝั่ง Native
  factory MeasurementData.fromMap(
      Map<dynamic, dynamic> map, double devicePixelRatio) {
    // ดึงค่าพิกัด X, Y ดิบ (raw pixel) จาก Map, ถ้าไม่มีให้ใช้ -1.0
    double rawX = map['midPointScreenX'] as double? ?? -1.0;
    double rawY = map['midPointScreenY'] as double? ?? -1.0;
    // ตรวจสอบว่าพิกัดที่ได้มาถูกต้องหรือไม่ (ไม่ติดลบ)
    bool isValidPayload = rawX >= 0 && rawY >= 0;
    // เตรียมตัวแปรสำหรับเก็บพิกัดทางตรรกะ (logical pixel)
    double logicalX = -1.0;
    double logicalY = -1.0;

    // ถ้าพิกัดดิบถูกต้อง ให้ทำการแปลงเป็นพิกัดทางตรรกะ
    if (isValidPayload) {
      // ป้องกันการหารด้วยศูนย์
      if (devicePixelRatio <= 0) {
        logicalX = rawX;
        logicalY = rawY;
      } else {
        // สูตรการแปลง: logical pixels = physical pixels / device pixel ratio
        logicalX = rawX / devicePixelRatio;
        logicalY = rawY / devicePixelRatio;
      }
    }
    // คืนค่า object MeasurementData ที่มีข้อมูลที่แปลงแล้ว
    return MeasurementData(
      label:
          map['label'] as String? ?? '', // ดึง label, ถ้าไม่มีให้ใช้สตริงว่าง
      distance:
          map['distance'] as double? ?? 0.0, // ดึง distance, ถ้าไม่มีให้ใช้ 0.0
      midPointScreenX: logicalX, // ใช้พิกัดที่แปลงแล้ว
      midPointScreenY: logicalY, // ใช้พิกัดที่แปลงแล้ว
      initiallyValid: isValidPayload, // เก็บสถานะความถูกต้องของข้อมูลเริ่มต้น
    );
  }

  // Getter: เป็น property ที่ใช้ตรวจสอบว่าป้ายกำกับนี้ควรจะแสดงบนหน้าจอหรือไม่
  bool get isVisibleOnScreen =>
      initiallyValid && midPointScreenX >= 0 && midPointScreenY >= 0;
}

// StatefulWidget หลักของหน้าจอ AR Measurement
class ArMeasureScreen extends StatefulWidget {
  // URL ของโมเดล 3D ที่จะส่งต่อไปยังหน้า PlacementScreen (อาจจะเป็น null ถ้าเป็นโหมดวัดอย่างเดียว)
  final String? modelUrlToPlace;
  // ขนาดอ้างอิงของโมเดล เพื่อใช้ในการคำนวณมุมกล้องในหน้าถัดไป
  final double? modelDimension;

  // Constructor ของ Widget
  const ArMeasureScreen({super.key, this.modelUrlToPlace, this.modelDimension});

  @override
  State<ArMeasureScreen> createState() => _ArMeasureScreenState();
}

// State class ของ ArMeasureScreen ที่จัดการสถานะและการทำงานทั้งหมด
class _ArMeasureScreenState extends State<ArMeasureScreen> {
  // ชื่อของ MethodChannel ที่ต้องตรงกับฝั่ง Native Android
  static const String _channelName = 'ar_measurement_channel';
  // สร้าง instance ของ MethodChannel เพื่อใช้ในการสื่อสาร
  static const MethodChannel _channel = MethodChannel(_channelName);

  // GlobalKey ที่ใช้สำหรับอ้างอิงถึง Widget RepaintBoundary เพื่อจับภาพหน้าจอ
  final GlobalKey _arViewKey = GlobalKey();
  // List ที่ใช้เก็บข้อมูลการวัดทั้งหมดที่กำลังแสดงผลอยู่
  List<MeasurementData> _measurements = [];
  // String ที่ใช้เก็บข้อความสถานะเพื่อแสดงให้ผู้ใช้ทราบ
  String _statusText = 'Initializing AR...';
  // Flag ที่ใช้เป็น "ล็อค" เพื่อป้องกันการกดปุ่มซ้ำซ้อนขณะที่แอปกำลังประมวลผล
  bool _isActionFinished = false;

  @override
  void initState() {
    super.initState();
    // เมื่อ State ถูกสร้าง, ให้ลงทะเบียน MethodCallHandler เพื่อรอรับข้อมูลจากฝั่ง Native
    _channel.setMethodCallHandler(_handleMethodCall);
    // ตั้งค่าข้อความสถานะเริ่มต้น
    _statusText = 'Move phone to find planes for measurement.';
  }

  // เมธอดที่จะถูกเรียกอัตโนมัติเมื่อฝั่ง Native ส่งข้อมูลผ่านมาทาง MethodChannel
  Future<dynamic> _handleMethodCall(MethodCall call) {
    // ใช้ switch-case เพื่อแยกการกระทำตามชื่อเมธอดที่ Native ส่งมา
    switch (call.method) {
      // กรณีที่ Native ส่งผลการวัดมาให้
      case 'measurementSetResult':
        // แปลง arguments ที่ได้มา (ซึ่งเป็น List)
        final List<dynamic>? results = call.arguments as List<dynamic>?;
        // ถ้า results ไม่ใช่ null และ Widget ยังคงอยู่บนหน้าจอ (mounted)
        if (results != null && mounted) {
          // อัปเดต state ของแอป
          setState(() {
            // แปลง List ของ Map ที่ได้มาให้เป็น List ของ MeasurementData object
            _measurements = results
                .map((item) => MeasurementData.fromMap(
                    item as Map<dynamic, dynamic>,
                    MediaQuery.of(context)
                        .devicePixelRatio)) // ส่ง devicePixelRatio ไปด้วย
                .toList();
            // อัปเดตข้อความสถานะตามจำนวนการวัด
            if (!_isActionFinished) {
              _statusText = _measurements.isEmpty
                  ? 'Tap on detected planes to measure.' // ถ้ายังไม่มีการวัด
                  : '${_measurements.length} measurement(s) active.'; // ถ้ามีการวัดแล้ว
            }
          });
        }
        break;
      // กรณีที่ Native แจ้งว่าได้ล้างจุดทั้งหมดแล้ว
      case 'pointsCleared':
        if (mounted) {
          setState(() {
            // ล้าง List ข้อมูลการวัด
            _measurements.clear();
            // ปลดล็อคปุ่ม
            _isActionFinished = false;
            // อัปเดตข้อความสถานะ
            _statusText = 'Points cleared. Tap to place point 1.';
          });
        }
        break;
      // กรณีอื่นๆ ที่ไม่ได้กำหนดไว้
      default:
        break;
    }
    // คืนค่า Future เพื่อแสดงว่ารับทราบการเรียกแล้ว
    return Future.value();
  }

  // เมธอดสำหรับส่งคำสั่งล้างสถานะ AR ไปยังฝั่ง Native
  Future<void> _clearArState() async {
    try {
      // เรียกเมธอด 'clearPoints' บน MethodChannel
      await _channel.invokeMethod('clearPoints');
    } on PlatformException catch (e) {
      // จัดการข้อผิดพลาดถ้าการเรียก Platform Channel ล้มเหลว
      if (mounted) {
        setState(() => _statusText = "Error clearing state: ${e.message}");
      }
    }
  }

  // เมธอดสำหรับจับภาพหน้าจอและนำทางไปยังหน้าถัดไป
  Future<void> _captureAndProceed() async {
    // ถ้ากำลังประมวลผลอยู่ ให้ยกเลิกการทำงาน
    if (_isActionFinished) return;
    // ตั้งค่าล็อค เพื่อป้องกันการกดซ้ำ
    setState(() => _isActionFinished = true);

    // แสดง Dialog ที่มี loading indicator
    showDialog(
      context: context,
      barrierDismissible: false, // ไม่ให้ปิด dialog ด้วยการแตะข้างนอก
      builder: (BuildContext context) =>
          const Center(child: CircularProgressIndicator()),
    );

    try {
      // 1. หา Render Object ของ RepaintBoundary โดยใช้ GlobalKey
      final RenderRepaintBoundary boundary = _arViewKey.currentContext!
          .findRenderObject() as RenderRepaintBoundary;
      // 2. แปลง Render Object เป็น ui.Image
      final ui.Image image = await boundary.toImage(
          pixelRatio: MediaQuery.of(context)
              .devicePixelRatio); // ใช้ pixelRatio ของจอเพื่อให้ได้ภาพความละเอียดสูงสุด
      // 3. แปลง ui.Image เป็น ByteData ในรูปแบบ PNG
      final ByteData? byteData =
          await image.toByteData(format: ui.ImageByteFormat.png);
      // ถ้าแปลงข้อมูลไม่ได้ ให้โยน exception
      if (byteData == null) throw Exception("Could not get byte data");
      // 4. แปลง ByteData เป็น Uint8List ซึ่งเป็นข้อมูลรูปภาพที่พร้อมใช้งาน
      final Uint8List pngBytes = byteData.buffer.asUint8List();

      // ปิด Dialog loading
      Navigator.of(context).pop();

      // ตรวจสอบอีกครั้งว่า Widget ยังอยู่บนหน้าจอหรือไม่
      if (!mounted) return;
      // 5. นำทางไปยัง PlacementScreen พร้อมกับส่งข้อมูลที่จำเป็นทั้งหมดไป
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (context) => PlacementScreen(
            backgroundImage: pngBytes, // ข้อมูลรูปภาพพื้นหลัง
            modelUrl: widget.modelUrlToPlace!, // URL ของโมเดล
            measurements: _measurements, // ข้อมูลการวัด
            modelDimension: widget.modelDimension, // ขนาดอ้างอิงของโมเดล
          ),
        ),
      );

      // เมื่อกลับมาจากหน้า PlacementScreen, ให้ปลดล็อคปุ่ม
      setState(() => _isActionFinished = false);
    } catch (e) {
      // ถ้าเกิดข้อผิดพลาดระหว่างกระบวนการ
      // ปิด Dialog loading
      Navigator.of(context).pop();
      // แสดง SnackBar เพื่อแจ้งข้อผิดพลาด
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to capture screen: $e')),
      );
      // ปลดล็อคปุ่ม
      setState(() => _isActionFinished = false);
    }
  }

  // เมธอด build UI หลักของหน้าจอ
  @override
  Widget build(BuildContext context) {
    // เตรียม Widget สำหรับ AR View
    Widget arViewWidget;
    // ตรวจสอบว่าเป็น Android หรือไม่
    if (Platform.isAndroid) {
      // ถ้าเป็น Android, ให้สร้าง AndroidView
      arViewWidget = const AndroidView(
        viewType:
            'ar_view', // ID ของ View ที่ต้องตรงกับที่ลงทะเบียนไว้ใน MainActivity.java
        layoutDirection: TextDirection.ltr,
        creationParamsCodec:
            StandardMessageCodec(), // Codec มาตรฐานสำหรับส่งข้อมูลตอนสร้าง
      );
    } else {
      // ถ้าไม่ใช่ Android, ให้แสดงข้อความแจ้งเตือน
      arViewWidget = const Center(
          child: Text('AR features are only available on Android.'));
    }

    // คืนค่า Scaffold ซึ่งเป็นโครงสร้างหลักของหน้าจอ
    return Scaffold(
      appBar: AppBar(
        // ตั้งชื่อ AppBar ตามโหมดการทำงาน
        title: Text(
            widget.modelUrlToPlace == null || widget.modelUrlToPlace!.isEmpty
                ? 'AR Measurement' // โหมดวัดอย่างเดียว
                : 'Measure & Place'), // โหมดวัดและวางโมเดล
      ),
      // ใช้ Stack เพื่อวาง Widget ซ้อนกัน
      body: Stack(
        children: [
          // Widget ชั้นล่างสุด: AR View
          Positioned.fill(
            // ให้ Widget ขยายเต็มพื้นที่
            child: RepaintBoundary(
              // ครอบ AR View และป้ายกำกับไว้
              key: _arViewKey, // ผูก key ไว้เพื่อใช้ในการจับภาพ
              child: Stack(
                children: [
                  // แสดง AR View ที่เตรียมไว้
                  Positioned.fill(child: arViewWidget),
                  // วาดป้ายกำกับทับลงบน AR View
                  // ใช้ '...' (spread operator) เพื่อนำ Widget ทั้งหมดใน List มาใส่ใน Stack
                  ..._measurements
                      .where((data) => data
                          .isVisibleOnScreen) // กรองเอาเฉพาะข้อมูลที่ควรแสดง
                      .map((data) {
                    // แปลงข้อมูลแต่ละชิ้นให้เป็น Positioned Widget
                    return Positioned(
                      left: data.midPointScreenX, // กำหนดตำแหน่งแกน X
                      top: data.midPointScreenY, // กำหนดตำแหน่งแกน Y
                      child: FractionalTranslation(
                        // ใช้เพื่อจัดให้จุดกึ่งกลางของ Widget อยู่ตรงกับพิกัด (left, top)
                        translation: const Offset(-0.5, -0.5),
                        child: IgnorePointer(
                          // ทำให้ Widget นี้ไม่รับการสัมผัส (ให้ทะลุไปที่ AR View ด้านหลัง)
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 8, vertical: 4),
                            decoration: BoxDecoration(
                                // ตกแต่งกล่องข้อความ
                                color: Colors.deepPurple.withOpacity(0.9),
                                borderRadius: BorderRadius.circular(4),
                                boxShadow: [
                                  BoxShadow(
                                      color: Colors.black.withOpacity(0.5),
                                      blurRadius: 4,
                                      offset: const Offset(1.5, 1.5))
                                ]),
                            child: Text(
                              // แสดงข้อความการวัด
                              '${data.label}: ${data.distance.toStringAsFixed(2)} m', // Format ตัวเลขทศนิยม 2 ตำแหน่ง
                              style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 14,
                                  fontWeight: FontWeight.bold),
                            ),
                          ),
                        ),
                      ),
                    );
                  }).toList(), // แปลงผลลัพธ์ของ map ให้เป็น List ของ Widgets
                ],
              ),
            ),
          ),
          // Widget ชั้นบน: ข้อความสถานะ
          Positioned(
            bottom: 100, // จัดตำแหน่งด้านล่าง
            left: 20,
            right: 20,
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                  color: Colors.black.withOpacity(0.75),
                  borderRadius: BorderRadius.circular(8)),
              child: Text(_statusText, // แสดงข้อความสถานะ
                  style: const TextStyle(color: Colors.white, fontSize: 16),
                  textAlign: TextAlign.center),
            ),
          ),
          // Widget ชั้นบน: ปุ่มล้างค่า
          Positioned(
            bottom: 20,
            right: 20,
            child: FloatingActionButton(
              onPressed: _clearArState, // เมื่อกดให้เรียกเมธอดล้างค่า
              tooltip: 'Clear Measurements',
              backgroundColor: Colors.redAccent,
              heroTag:
                  'clearButton', // heroTag ต้องไม่ซ้ำกันสำหรับแต่ละ FAB ในหน้าจอเดียวกัน
              child: const Icon(Icons.delete_forever),
            ),
          ),
          // Widget ชั้นบน: ปุ่ม FINISH (จะแสดงก็ต่อเมื่อยังไม่ได้กด)
          if (!_isActionFinished)
            Positioned(
              bottom: 20,
              left: 20,
              child: FloatingActionButton.extended(
                // ถ้าไม่มี modelUrl ให้ส่งมา (โหมดวัดอย่างเดียว) ปุ่มจะถูกปิดใช้งาน (เป็นสีเทา)
                onPressed: (widget.modelUrlToPlace != null &&
                        widget.modelUrlToPlace!.isNotEmpty)
                    ? _captureAndProceed
                    : null,
                tooltip: 'Finish Measurements & Place Model',
                // ตั้งค่าสีตามสถานะการใช้งาน
                backgroundColor: (widget.modelUrlToPlace != null &&
                        widget.modelUrlToPlace!.isNotEmpty)
                    ? Colors.green
                    : Colors.grey,
                heroTag: 'finishButton',
                icon: const Icon(Icons.check_circle_outline),
                label: const Text("FINISH"),
              ),
            ),
        ],
      ),
    );
  }
}
