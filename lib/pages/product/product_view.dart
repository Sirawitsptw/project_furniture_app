import 'package:cloud_firestore/cloud_firestore.dart';
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
  }

  Future<void> addToCart() async {
    CollectionReference cart = FirebaseFirestore.instance.collection('cart');
    return await cart.add({
      'nameCart': model.name,
      'priceCart': model.price,
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
          // โมเดลสินค้า
          Container(
            height: 350,
            width: 350,
            child: ModelViewer(
              src: model.model,
              ar: true,
              scale: '1 1 1',
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
                    // ฟังก์ชันเมื่อกดปุ่มซื้อ
                  },
                  style: ButtonStyle(
                    backgroundColor:
                        MaterialStateProperty.all<Color>(Colors.purple),
                    foregroundColor:
                        MaterialStateProperty.all<Color>(Colors.white),
                  ),
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
        child: Text('คำอธิบายสินค้า'),
      );
}
