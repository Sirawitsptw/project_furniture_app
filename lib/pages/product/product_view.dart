import 'package:flutter/material.dart';
import 'package:project_furnitureapp/pages/home/product_model.dart';
import 'package:model_viewer_plus/model_viewer_plus.dart';

class ProductView extends StatefulWidget {
  final productModel productmodel;
  ProductView({Key? key, required this.productmodel}) : super(key: key);

  @override
  State<ProductView> createState() => _ProductViewState();
}

class _ProductViewState extends State<ProductView> {
  late productModel model;

  @override
  void initState() {
    super.initState();
    model = widget.productmodel;
    print('Model URL: ${model.model}');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(model.name),
      ),
      body: Center(
        child: ModelViewer(
          src: 'https://modelviewer.dev/shared-assets/models/Astronaut.glb',
          ar: true,
          scale: '1 1 1',
        ),
      ),
    );
  }
}
