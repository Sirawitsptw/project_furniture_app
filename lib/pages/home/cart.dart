import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:project_furnitureapp/pages/home/product_model.dart';
import 'package:project_furnitureapp/pages/product/orderProduct.dart';

class CartPage extends StatefulWidget {
  const CartPage({super.key});

  @override
  State<CartPage> createState() => CartPageState();
}

class CartPageState extends State<CartPage> {
  String? selectedProductId;
  Map<String, dynamic>? selectedProductData;

  @override
  Widget build(BuildContext context) {
    User? user = FirebaseAuth.instance.currentUser;
    String userEmail = user?.email ?? '';
    return Scaffold(
      body: StreamBuilder<QuerySnapshot>(
        stream: FirebaseFirestore.instance
            .collection('cart')
            .where('userEmail', isEqualTo: userEmail)
            .snapshots(),
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return Center(child: Text('Error: ${snapshot.error}'));
          }

          if (!snapshot.hasData || snapshot.data!.docs.isEmpty) {
            return Center(child: Text('No items in the cart.'));
          }

          var cartItems = snapshot.data!.docs;

          return Column(
            children: [
              Expanded(
                child: ListView.builder(
                  itemCount: cartItems.length,
                  itemBuilder: (context, index) {
                    var item = cartItems[index];
                    var itemData = item.data() as Map<String, dynamic>;
                    return Card(
                      margin: EdgeInsets.symmetric(vertical: 8, horizontal: 16),
                      child: ListTile(
                        leading: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Radio<String>(
                              value: item.id,
                              groupValue: selectedProductId,
                              onChanged: (value) {
                                setState(() {
                                  selectedProductId = value;
                                  selectedProductData = itemData;
                                });
                              },
                            ),
                            Image.network(itemData['imgCart'],
                                width: 50, height: 50, fit: BoxFit.cover),
                          ],
                        ),
                        title: Text(itemData['nameCart']),
                        subtitle: Text('${itemData['priceCart']} บาท'),
                        trailing: IconButton(
                          icon: Icon(Icons.remove_shopping_cart),
                          onPressed: () {
                            FirebaseFirestore.instance
                                .collection('cart')
                                .doc(item.id)
                                .delete();
                          },
                        ),
                      ),
                    );
                  },
                ),
              ),
              Padding(
                padding: EdgeInsets.all(16.0),
                child: ElevatedButton(
                  onPressed: selectedProductData != null
                      ? () {
                          productModel product = productModel(
                            name: selectedProductData!['nameCart'],
                            price: selectedProductData!['priceCart'],
                            imageUrl: selectedProductData!['imgCart'],
                            model: selectedProductData!['modelCart'],
                            desc: selectedProductData!['descCart'],
                          );

                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => OrderPage(product: product),
                            ),
                          );
                        }
                      : null,
                  child: Text('สั่งซื้อ'),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
