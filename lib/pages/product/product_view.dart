import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:project_furnitureapp/pages/home/product_model.dart';
import 'package:model_viewer_plus/model_viewer_plus.dart';
import 'package:project_furnitureapp/pages/product/orderProduct.dart';
import 'package:project_furnitureapp/pages/home/ar_measure_screen.dart';
import 'dart:io' show Platform;

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

  Future<void> addToCart() async {
    CollectionReference cart = FirebaseFirestore.instance.collection('cart');
    User? user = FirebaseAuth.instance.currentUser;
    String userPhone = user?.phoneNumber ?? '';
    try {
      await cart.add({
        'userPhone': userPhone,
        'nameCart': model.name,
        'priceCart': model.price,
        'imgCart': model.imageUrl,
        'descCart': model.desc,
        'modelCart': model.model,
        'timeCart': FieldValue.serverTimestamp(),
      });
      print("Product Added to Cart");
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('เพิ่มสินค้าลงในตะกร้าสำเร็จ'),
            duration: Duration(seconds: 2),
          ),
        );
      }
    } catch (error) {
      print("Failed to add product to cart: $error");
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('เกิดข้อผิดพลาด: ไม่สามารถเพิ่มสินค้าได้'),
            duration: Duration(seconds: 2),
          ),
        );
      }
    }
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
          // Model Viewer
          Container(
            height: 350,
            width: 350, // ใช้ขนาดเดิม
            child: ModelViewer(
              src: model.model,
              ar: false,
              autoRotate: true,
              cameraControls: true,
              // scale: '1 1 1',
            ),
          ),

          SizedBox(height: 20),

          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20.0),
            child: Column(
              children: [
                nameprice(),
                SizedBox(height: 20),
                description(),
              ],
            ),
          ),

          SizedBox(height: 30),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20.0),
            child: ElevatedButton.icon(
              icon: Icon(Icons.camera_alt),
              label: Text('AR'),
              style: ElevatedButton.styleFrom(
                minimumSize: Size(double.infinity, 45),
              ),
              onPressed: () {
                if (Platform.isAndroid) {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                        builder: (context) => const ArMeasureScreen()),
                  );
                } else {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('ERROR'),
                      duration: Duration(seconds: 3),
                    ),
                  );
                }
              },
            ),
          ),
          Expanded(child: Container()),
          Padding(
            padding:
                const EdgeInsets.symmetric(horizontal: 20.0, vertical: 15.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                IconButton(
                    icon: Icon(Icons.shopping_basket, size: 40),
                    onPressed: addToCart),
                ElevatedButton(
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => OrderPage(product: model),
                      ),
                    );
                  },
                  style: ButtonStyle(
                    backgroundColor:
                        MaterialStateProperty.all<Color>(Colors.purple),
                    foregroundColor:
                        MaterialStateProperty.all<Color>(Colors.white),
                    padding: MaterialStateProperty.all<EdgeInsets>(
                        EdgeInsets.symmetric(horizontal: 80, vertical: 10)),
                    textStyle: MaterialStateProperty.all<TextStyle>(
                        TextStyle(fontSize: 18)),
                  ),
                  child: Text('สั่งซื้อ'),
                ),
              ],
            ),
          ),
          // **** สิ้นสุดปุ่มเดิม ****
        ],
      ),
    );
  }

  // --- Widgets เดิม ---
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
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text("จำนวนสินค้า ${model.amount} ชิ้น"),
            SizedBox(height: 10),
            Text(model.desc),
          ],
        ),
      );
}
