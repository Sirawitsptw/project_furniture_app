import 'package:flutter/material.dart';
import 'package:project_furnitureapp/pages/home/product_model.dart';

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
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(
          title: Text(model.name == null ? '????' : model.name),
        ),
        body: Center(
          child: Text(model.imageUrl),
        ));
  }
}
