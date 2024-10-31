import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:project_furnitureapp/pages/home/product_model.dart';

class OrderPage extends StatelessWidget {
  final productModel product;

  OrderPage({Key? key, required this.product}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Order ${product.name}'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Product: ${product.name}', style: TextStyle(fontSize: 20)),
            SizedBox(height: 10),
            Text('Price: ${product.price} บาท', style: TextStyle(fontSize: 20)),
            SizedBox(height: 20),
            // Add other order details or input fields as needed
          ],
        ),
      ),
    );
  }
}
