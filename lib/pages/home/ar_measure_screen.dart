import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:io' show Platform;

class MeasurementData {
  final String label;
  final double distance;
  final double midPointScreenX;
  final double midPointScreenY;
  final bool initiallyValid;

  MeasurementData({
    required this.label,
    required this.distance,
    required this.midPointScreenX,
    required this.midPointScreenY,
    required this.initiallyValid,
  });

  factory MeasurementData.fromMap(
      Map<dynamic, dynamic> map, double devicePixelRatio) {
    double rawX = map['midPointScreenX'] as double? ?? -1.0;
    double rawY = map['midPointScreenY'] as double? ?? -1.0;
    bool isValid = rawX >= 0 && rawY >= 0;

    return MeasurementData(
      label: map['label'] as String? ?? '',
      distance: map['distance'] as double? ?? 0.0,
      midPointScreenX: isValid ? rawX / devicePixelRatio : -1.0,
      midPointScreenY: isValid ? rawY / devicePixelRatio : -1.0,
      initiallyValid: isValid,
    );
  }

  bool get isVisibleOnScreen {
    return initiallyValid && midPointScreenX >= 0 && midPointScreenY >= 0;
  }
}

class ArMeasureScreen extends StatefulWidget {
  const ArMeasureScreen({super.key});

  @override
  State<ArMeasureScreen> createState() => _ArMeasureScreenState();
}

class _ArMeasureScreenState extends State<ArMeasureScreen> {
  static const String _channelName = 'ar_measurement_channel';
  static const MethodChannel _channel = MethodChannel(_channelName);

  List<MeasurementData> _measurements = [];
  String _statusText = 'Tap on detected planes to place points.';
  double _devicePixelRatio = 1.0;
  bool _isArFrozen = false; // สถานะการ Freeze AR View

  @override
  void initState() {
    super.initState();
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final newDevicePixelRatio = MediaQuery.of(context).devicePixelRatio;
    if (_devicePixelRatio != newDevicePixelRatio) {
      // ใช้ setState ที่นี่ได้ เพราะ didChangeDependencies สามารถเรียก setState ได้
      setState(() {
        _devicePixelRatio = newDevicePixelRatio;
      });
      print("Device Pixel Ratio updated: $_devicePixelRatio");
    }
  }

  Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'measurementSetResult':
        final List<dynamic>? results = call.arguments as List<dynamic>?;
        if (results != null && mounted) {
          setState(() {
            _measurements = results
                .map((item) => MeasurementData.fromMap(
                    item as Map<dynamic, dynamic>, _devicePixelRatio))
                .toList();
            _measurements.forEach((m) {
              print(
                  "Processed Measurement: Label=${m.label}, Dist=${m.distance.toStringAsFixed(2)}, X=${m.midPointScreenX.toStringAsFixed(2)}, Y=${m.midPointScreenY.toStringAsFixed(2)}, InitiallyValid=${m.initiallyValid}, IsVisible=${m.isVisibleOnScreen}");
            });
            if (!_isArFrozen) {
              // อัปเดต status text เฉพาะตอนที่ AR ยังไม่ถูก freeze
              if (_measurements.where((m) => m.initiallyValid).isEmpty) {
                _statusText = 'Place 2 or 4 points to measure.';
              } else {
                _statusText =
                    '${_measurements.where((m) => m.initiallyValid).length} measurement(s) active.';
              }
            }
          });
        }
        print("Received measurement set (raw): $results");
        break;
      case 'pointsCleared':
        if (mounted) {
          setState(() {
            _measurements.clear();
            _isArFrozen =
                false; // เมื่อล้างจุด ควรจะยกเลิกการ freeze ด้วย (ถ้าต้องการ)
            _statusText = 'Points cleared. Tap to place point 1.';
          });
        }
        print("Points cleared by native.");
        break;
      default:
        print('Unknown method call received: ${call.method}');
    }
  }

  Future<void> _clearPoints() async {
    try {
      await _channel.invokeMethod(
          'clearPoints'); // Native จะมีการตั้ง arViewIsFrozen = false ด้วย
      if (mounted) {
        // อัปเดต UI ฝั่ง Flutter ทันทีด้วย
        setState(() {
          _measurements.clear();
          _isArFrozen = false;
          _statusText = 'Points cleared. Tap to place point 1.';
        });
      }
      print("Invoked clearPoints");
    } on PlatformException catch (e) {
      print("Failed to invoke clearPoints: '${e.message}'.");
      if (mounted) {
        setState(() {
          _statusText = "Error clearing points: ${e.message}";
        });
      }
    }
  }

  Future<void> _toggleFreezeArView() async {
    if (_isArFrozen) {
      print(
          "AR is already frozen. To unfreeze, clear points or implement unfreeze logic.");
      setState(() {
        _statusText = "View is frozen. Clear points to restart.";
      });
    } else {
      if (_measurements.where((m) => m.initiallyValid).isEmpty) {
        if (mounted) {
          setState(() {
            _statusText = "Place at least one measurement before finishing.";
          });
        }
        return;
      }
      try {
        await _channel.invokeMethod('freezeArView');
        if (mounted) {
          setState(() {
            _isArFrozen = true;
            _statusText =
                'Measurement finished. View is frozen.\n(Next step: Implement 3D model placement)';
          });
        }
        print("AR view freeze command sent.");
      } on PlatformException catch (e) {
        print("Failed to invoke freezeArView: '${e.message}'.");
        if (mounted) {
          setState(() {
            _statusText = "Error freezing view: ${e.message}";
          });
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_devicePixelRatio == 1.0 &&
        MediaQuery.of(context).devicePixelRatio != 1.0 &&
        mounted) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted &&
            MediaQuery.of(context).devicePixelRatio != _devicePixelRatio) {
          setState(() {
            _devicePixelRatio = MediaQuery.of(context).devicePixelRatio;
            print("Device Pixel Ratio updated in build: $_devicePixelRatio");
          });
        }
      });
    }

    Widget arViewWidget;
    if (Platform.isAndroid) {
      arViewWidget = AndroidView(
        viewType: 'ar_view',
        layoutDirection: TextDirection.ltr,
        creationParams: const <String, dynamic>{},
        creationParamsCodec: const StandardMessageCodec(),
        onPlatformViewCreated: (int id) {
          print("AR PlatformView with ID $id created.");
        },
      );
    } else {
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
          Positioned.fill(child: arViewWidget),
          Positioned(
            bottom: 80, // ปรับตำแหน่ง status text ให้สูงขึ้นเล็กน้อย
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
          ..._measurements.where((data) => data.isVisibleOnScreen).map((data) {
            return Positioned(
              left: data.midPointScreenX,
              top: data.midPointScreenY,
              child: FractionalTranslation(
                translation: const Offset(-0.5, -0.5),
                child: IgnorePointer(
                  child: Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                        color: Colors.deepPurple.withOpacity(0.8),
                        borderRadius: BorderRadius.circular(4),
                        boxShadow: [
                          BoxShadow(
                              color: Colors.black.withOpacity(0.3),
                              blurRadius: 3,
                              offset: const Offset(1, 1))
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

          // ปุ่มควบคุม: Clear และ Finish/Unfreeze
          Positioned(
            bottom: 20,
            right: 20,
            child: FloatingActionButton(
              onPressed: _clearPoints,
              tooltip: 'Clear Points & Unfreeze',
              backgroundColor: Colors.redAccent,
              heroTag: 'clearButton', // Add unique heroTag
              child: const Icon(Icons.delete_forever),
            ),
          ),
          if (!_isArFrozen) // แสดงปุ่ม Finish เฉพาะเมื่อยังไม่ Freeze
            Positioned(
              bottom: 20,
              left: 20,
              child: FloatingActionButton(
                onPressed: _toggleFreezeArView,
                tooltip: 'Finish Measurement',
                backgroundColor: Colors.green,
                heroTag: 'finishButton', // Add unique heroTag
                child: const Icon(Icons.check_circle_outline),
              ),
            ),
        ],
      ),
    );
  }
}
