import 'package:flutter/material.dart';
import 'package:flutter/services.dart'; // For MethodChannel
import 'dart:io' show Platform; // For checking platform

class ArMeasureScreen extends StatefulWidget {
  const ArMeasureScreen({super.key});

  @override
  State<ArMeasureScreen> createState() => _ArMeasureScreenState();
}

class _ArMeasureScreenState extends State<ArMeasureScreen> {
  // ชื่อ Channel ต้องตรงกับฝั่ง Native Android
  static const String _channelName = 'ar_measurement_channel';
  static const MethodChannel _channel = MethodChannel(_channelName);

  double? _lastMeasurement; // เก็บค่าระยะทางล่าสุดที่วัดได้
  String _statusText =
      'Tap on detected planes to place points.'; // ข้อความสถานะเริ่มต้น

  @override
  void initState() {
    super.initState();
    // ตั้งค่า Handler เพื่อรับข้อมูลจาก Native
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  // Handler สำหรับรับข้อมูล/event จาก Native Code
  Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'measurementResult':
        final double distance = call.arguments as double;
        // ใช้ mounted check ก่อนเรียก setState เพื่อความปลอดภัย
        if (mounted) {
          setState(() {
            _lastMeasurement = distance;
            _statusText = 'Distance: ${distance.toStringAsFixed(2)} meters';
          });
        }
        print("Received measurement: $distance");
        break;
      case 'pointsCleared':
        if (mounted) {
          setState(() {
            _lastMeasurement = null; // เคลียร์ค่าที่แสดงผล
            _statusText = 'Points cleared. Tap to place point 1.';
          });
        }
        print("Points cleared by native.");
        break;
      // สามารถเพิ่ม case อื่นๆ สำหรับ event จาก Native ได้
      default:
        print('Unknown method call received: ${call.method}');
    }
  }

  // ฟังก์ชันส่งคำสั่ง "clearPoints" ไปยัง Native
  Future<void> _clearPoints() async {
    try {
      // ส่งคำสั่งไปให้ ArMeasureView จัดการ
      await _channel.invokeMethod('clearPoints');
      print("Invoked clearPoints");
      // อัปเดต UI ทันที หรือรอ event 'pointsCleared' กลับมาก็ได้
      // ถ้าต้องการอัปเดตทันที:
      // if (mounted) {
      //   setState(() {
      //     _lastMeasurement = null;
      //     _statusText = 'Points cleared. Tap to place point 1.';
      //   });
      // }
    } on PlatformException catch (e) {
      print("Failed to invoke clearPoints: '${e.message}'.");
      if (mounted) {
        setState(() {
          _statusText = "Error clearing points: ${e.message}";
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    // สร้าง Widget สำหรับแสดง AR View เฉพาะบน Android
    Widget arViewWidget;
    if (Platform.isAndroid) {
      arViewWidget = AndroidView(
        viewType: 'ar_view', // ชื่อต้องตรงกับที่ register ใน MainActivity
        layoutDirection: TextDirection.ltr,
        creationParams: const <String,
            dynamic>{}, // ส่ง parameter เริ่มต้น (ถ้ามี)
        creationParamsCodec: const StandardMessageCodec(),
        onPlatformViewCreated: (int id) {
          print("AR PlatformView with ID $id created.");
          // สามารถส่งคำสั่งเริ่มต้นไปให้ View ได้ที่นี่ (ถ้าจำเป็น)
        },
        // ไม่ต้องใส่ gestureRecognizers เพราะ Native View จัดการ Tap เอง
      );
    } else {
      // แสดงข้อความบน Platform อื่น
      arViewWidget = const Center(
        child: Text('AR Measurement is only available on Android.'),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('AR Measurement'),
      ),
      body: Stack(
        children: [
          // AR View เต็มหน้าจอ
          Positioned.fill(child: arViewWidget),

          // UI แสดงผลสถานะ
          Positioned(
            bottom: 80, // ตำแหน่งจากด้านล่าง
            left: 20,
            right: 20,
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.black.withOpacity(0.6),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                _statusText,
                style: const TextStyle(color: Colors.white, fontSize: 16),
                textAlign: TextAlign.center,
              ),
            ),
          ),

          // ปุ่ม Clear Points
          Positioned(
            bottom: 20,
            right: 20,
            child: FloatingActionButton(
              onPressed: _clearPoints,
              tooltip: 'Clear Points',
              child: const Icon(Icons.clear),
            ),
          ),
        ],
      ),
    );
  }
}
