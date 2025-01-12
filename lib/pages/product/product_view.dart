import 'package:arcore_flutter_plugin/arcore_flutter_plugin.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:project_furnitureapp/pages/home/product_model.dart';
import 'package:project_furnitureapp/pages/product/orderProduct.dart';
import 'package:vector_math/vector_math_64.dart';

class ProductView extends StatefulWidget {
  final productModel productmodel;
  ProductView({Key? key, required this.productmodel}) : super(key: key);

  @override
  State<ProductView> createState() => _ProductViewState();
}

class _ProductViewState extends State<ProductView> {
  late productModel model;
  late ArCoreController arCoreController;
  late ArCoreNode arCoreNode;

  @override
  void initState() {
    super.initState();
    model = widget.productmodel;
  }

  Future<void> addToCart() async {
    CollectionReference cart = FirebaseFirestore.instance.collection('cart');
    User? user = FirebaseAuth.instance.currentUser;
    String userEmail = user?.email ?? 'No email found';
    return await cart.add({
      'userEmail': userEmail,
      'nameCart': model.name,
      'priceCart': model.price,
      'imgCart': model.imageUrl,
      'timeCart': FieldValue.serverTimestamp(),
    }).then((value) {
      print("Product Added to Cart: ${value.id}");
    }).catchError((error) {
      print("Failed to add product to cart: $error");
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(model.name),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          // AR with Measurement
          Expanded(
            child: ArCoreView(
              onArCoreViewCreated: _onArCoreViewCreated,
              enableTapRecognizer: true,
            ),
          ),

          SizedBox(height: 20),

          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                nameprice(),
                SizedBox(height: 30),
                description(),
              ],
            ),
          ),

          SizedBox(height: 30),
          Spacer(),

          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                IconButton(
                    icon: Icon(Icons.shopping_basket, size: 40),
                    onPressed: () {
                      addToCart();
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('เพิ่มสินค้าลงในตะกร้าสำเร็จ'),
                          duration: Duration(seconds: 2),
                        ),
                      );
                    }),
                ElevatedButton(
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => OrderPage(product: model),
                      ),
                    );
                  },
                  style: ButtonStyle(),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 80, vertical: 10),
                    child: Text('สั่งซื้อ', style: TextStyle(fontSize: 18)),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ARCore View Created
  _onArCoreViewCreated(ArCoreController controller) {
    arCoreController = controller;
    arCoreController.onPlaneTap = _onPlaneTap;
    _addARModel();
  }

  // Function to add AR model to AR view
  _addARModel() async {
    final node = ArCoreReferenceNode(
      name: 'model',
      object3DFileName: model.model,
      position: Vector3(0, 0, 0),
      rotation: Vector4(0, 0, 0, 1),
    );
    arCoreController.addArCoreNode(node);
    arCoreNode = node;
  }

  // Function to handle plane tap for size measurement
  _onPlaneTap(List<ArCoreHitTestResult> hitTestResults) {
    if (hitTestResults.isNotEmpty) {
      // Handle measuring logic here
      ArCoreHitTestResult result = hitTestResults.first;
      print('Plane tapped at position: ${result.pose.translation}');
      // You can add more logic here to show measurements
    }
  }

  Widget nameprice() => Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                model.name,
                style: TextStyle(fontSize: 18),
              ),
            ],
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              Text(
                '${model.price} บาท',
                style: TextStyle(fontSize: 18),
              ),
            ],
          ),
        ],
      );

  Widget description() => Container(
        alignment: Alignment.centerLeft,
        child: Text(model.desc),
      );
}
