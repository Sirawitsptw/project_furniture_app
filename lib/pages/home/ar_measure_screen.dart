import 'dart:ui' as ui;
import 'dart:typed_data';
import 'package:flutter/rendering.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:io' show Platform;

// TODO: แก้ 'your_project_name' ให้เป็นชื่อโปรเจกต์ของคุณเพื่อให้ path ถูกต้อง
import 'placement_screen.dart';

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
    bool isValidPayload = rawX >= 0 && rawY >= 0;
    double logicalX = -1.0;
    double logicalY = -1.0;

    if (isValidPayload) {
      if (devicePixelRatio <= 0) {
        logicalX = rawX;
        logicalY = rawY;
      } else {
        logicalX = rawX / devicePixelRatio;
        logicalY = rawY / devicePixelRatio;
      }
    }
    return MeasurementData(
      label: map['label'] as String? ?? '',
      distance: map['distance'] as double? ?? 0.0,
      midPointScreenX: logicalX,
      midPointScreenY: logicalY,
      initiallyValid: isValidPayload,
    );
  }
  bool get isVisibleOnScreen =>
      initiallyValid && midPointScreenX >= 0 && midPointScreenY >= 0;
}

class ArMeasureScreen extends StatefulWidget {
  final String? modelUrlToPlace;
  final double? modelDimension;

  const ArMeasureScreen({super.key, this.modelUrlToPlace, this.modelDimension});

  @override
  State<ArMeasureScreen> createState() => _ArMeasureScreenState();
}

class _ArMeasureScreenState extends State<ArMeasureScreen> {
  static const String _channelName = 'ar_measurement_channel';
  static const MethodChannel _channel = MethodChannel(_channelName);

  final GlobalKey _arViewKey = GlobalKey();
  List<MeasurementData> _measurements = [];
  String _statusText = 'Initializing AR...';
  bool _isActionFinished = false;

  @override
  void initState() {
    super.initState();
    _channel.setMethodCallHandler(_handleMethodCall);
    _statusText = 'Move phone to find planes for measurement.';
  }

  Future<dynamic> _handleMethodCall(MethodCall call) {
    switch (call.method) {
      case 'measurementSetResult':
        final List<dynamic>? results = call.arguments as List<dynamic>?;
        if (results != null && mounted) {
          setState(() {
            _measurements = results
                .map((item) => MeasurementData.fromMap(
                    item as Map<dynamic, dynamic>,
                    MediaQuery.of(context).devicePixelRatio))
                .toList();
            if (!_isActionFinished) {
              _statusText = _measurements.isEmpty
                  ? 'Tap on detected planes to measure.'
                  : '${_measurements.length} measurement(s) active.';
            }
          });
        }
        break;
      case 'pointsCleared':
        if (mounted) {
          setState(() {
            _measurements.clear();
            _isActionFinished = false;
            _statusText = 'Points cleared. Tap to place point 1.';
          });
        }
        break;
      default:
        break;
    }
    return Future.value();
  }

  Future<void> _clearArState() async {
    try {
      await _channel.invokeMethod('clearPoints');
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => _statusText = "Error clearing state: ${e.message}");
      }
    }
  }

  Future<void> _captureAndProceed() async {
    if (_isActionFinished) return;
    setState(() => _isActionFinished = true);

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext context) =>
          const Center(child: CircularProgressIndicator()),
    );

    try {
      final RenderRepaintBoundary boundary = _arViewKey.currentContext!
          .findRenderObject() as RenderRepaintBoundary;
      final ui.Image image = await boundary.toImage(
          pixelRatio: MediaQuery.of(context).devicePixelRatio);
      final ByteData? byteData =
          await image.toByteData(format: ui.ImageByteFormat.png);
      if (byteData == null) throw Exception("Could not get byte data");
      final Uint8List pngBytes = byteData.buffer.asUint8List();

      Navigator.of(context).pop();

      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (context) => PlacementScreen(
            backgroundImage: pngBytes,
            modelUrl: widget.modelUrlToPlace!,
            measurements: _measurements,
            modelDimension: widget.modelDimension,
          ),
        ),
      );

      setState(() => _isActionFinished = false);
    } catch (e) {
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to capture screen: $e')),
      );
      setState(() => _isActionFinished = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    Widget arViewWidget;
    if (Platform.isAndroid) {
      arViewWidget = const AndroidView(
        viewType: 'ar_view',
        layoutDirection: TextDirection.ltr,
        creationParamsCodec: StandardMessageCodec(),
      );
    } else {
      arViewWidget = const Center(
          child: Text('AR features are only available on Android.'));
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(
            widget.modelUrlToPlace == null || widget.modelUrlToPlace!.isEmpty
                ? 'AR Measurement'
                : 'Measure & Place'),
      ),
      body: Stack(
        children: [
          Positioned.fill(
            child: RepaintBoundary(
              key: _arViewKey,
              child: Stack(
                children: [
                  Positioned.fill(child: arViewWidget),
                  ..._measurements
                      .where((data) => data.isVisibleOnScreen)
                      .map((data) {
                    return Positioned(
                      left: data.midPointScreenX,
                      top: data.midPointScreenY,
                      child: FractionalTranslation(
                        translation: const Offset(-0.5, -0.5),
                        child: IgnorePointer(
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
                ],
              ),
            ),
          ),
          Positioned(
            bottom: 100,
            left: 20,
            right: 20,
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                  color: Colors.black.withOpacity(0.75),
                  borderRadius: BorderRadius.circular(8)),
              child: Text(_statusText,
                  style: const TextStyle(color: Colors.white, fontSize: 16),
                  textAlign: TextAlign.center),
            ),
          ),
          Positioned(
            bottom: 20,
            right: 20,
            child: FloatingActionButton(
              onPressed: _clearArState,
              tooltip: 'Clear Measurements',
              backgroundColor: Colors.redAccent,
              heroTag: 'clearButton',
              child: const Icon(Icons.delete_forever),
            ),
          ),
          if (!_isActionFinished)
            Positioned(
              bottom: 20,
              left: 20,
              child: FloatingActionButton.extended(
                onPressed: (widget.modelUrlToPlace != null &&
                        widget.modelUrlToPlace!.isNotEmpty)
                    ? _captureAndProceed
                    : null,
                tooltip: 'Finish Measurements & Place Model',
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
