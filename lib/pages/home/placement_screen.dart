import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:model_viewer_plus/model_viewer_plus.dart';

import 'ar_measure_screen.dart';

class PlacementScreen extends StatefulWidget {
  final Uint8List backgroundImage;
  final String modelUrl;
  final List<MeasurementData> measurements;
  final double? modelDimension;

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

class _PlacementScreenState extends State<PlacementScreen> {
  Offset? _modelPosition;
  final double _modelViewerSize = 400.0;

  void _handleScreenTap(TapUpDetails details) {
    setState(() {
      _modelPosition = details.localPosition;
    });
  }

  String _getDisplayName(String? url) {
    if (url == null || url.isEmpty) return "Unknown Model";
    try {
      return Uri.parse(url)
          .pathSegments
          .lastWhere((s) => s.isNotEmpty, orElse: () => "Model");
    } catch (_) {
      return "Model";
    }
  }

  @override
  Widget build(BuildContext context) {
    // คำนวณระยะห่างกล้องแบบไดนามิกจากขนาดของโมเดล
    // สูตรนี้เป็นเพียงตัวอย่าง สามารถปรับได้ตามความเหมาะสม
    final double cameraRadius = 1.0 + (widget.modelDimension ?? 1.5);

    return Scaffold(
      appBar: AppBar(
        title: Text('Place Model: ${_getDisplayName(widget.modelUrl)}'),
        actions: [
          if (_modelPosition != null)
            IconButton(
              icon: const Icon(Icons.clear),
              onPressed: () => setState(() => _modelPosition = null),
              tooltip: 'Clear Model',
            )
        ],
      ),
      body: GestureDetector(
        onTapUp: _handleScreenTap,
        child: Stack(
          fit: StackFit.expand,
          children: [
            Image.memory(
              widget.backgroundImage,
              fit: BoxFit.cover,
              gaplessPlayback: true,
            ),
            ...widget.measurements
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
            if (_modelPosition != null)
              Positioned(
                left: _modelPosition!.dx - (_modelViewerSize / 2),
                top: _modelPosition!.dy - (_modelViewerSize / 2),
                child: SizedBox(
                  width: _modelViewerSize,
                  height: _modelViewerSize,
                  child: ModelViewer(
                    src: widget.modelUrl,
                    alt: "3D Model",
                    cameraControls: true,
                    disableZoom: true,
                    backgroundColor: Colors.transparent,
                    interactionPrompt: InteractionPrompt.none,
                    cameraOrbit: '0deg 75deg ${cameraRadius}m',
                  ),
                ),
              ),
            if (_modelPosition == null)
              Align(
                alignment: Alignment.bottomCenter,
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
