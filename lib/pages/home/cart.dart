import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:project_furnitureapp/pages/product/orderProduct.dart';
import 'product_model.dart';

class CartPage extends StatefulWidget {
  const CartPage({super.key});

  @override
  State<CartPage> createState() => CartPageState();
}

class CartPageState extends State<CartPage> {
  String? selectedProductId;
  Map<String, dynamic>? selectedProductData;
  Map<String, int> productQuantities = {};

  void updateQuantity(String productId, int change) {
    setState(() {
      productQuantities[productId] =
          (productQuantities[productId] ?? 1) + change;
      if (productQuantities[productId]! < 1) {
        productQuantities[productId] = 1;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    User? user = FirebaseAuth.instance.currentUser;
    String userPhone = user?.phoneNumber ?? '';
    return Scaffold(
      body: StreamBuilder<QuerySnapshot>(
        stream: FirebaseFirestore.instance
            .collection('cart')
            .where('userPhone', isEqualTo: userPhone)
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
                    String productId = item.id;
                    productQuantities.putIfAbsent(productId, () => 1);

                    return Card(
                      margin: EdgeInsets.symmetric(vertical: 8, horizontal: 16),
                      child: ListTile(
                        leading: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Radio<String>(
                              value: productId,
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
                        subtitle: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                IconButton(
                                  icon: Icon(Icons.remove),
                                  onPressed: () =>
                                      updateQuantity(productId, -1),
                                ),
                                Text('${productQuantities[productId]}'),
                                IconButton(
                                  icon: Icon(Icons.add),
                                  onPressed: () => updateQuantity(productId, 1),
                                ),
                              ],
                            ),
                            Text(
                                'ราคา: ${(int.parse(itemData['priceCart'].toString()) * productQuantities[productId]!)} บาท'),
                          ],
                        ),
                        trailing: IconButton(
                          icon: Icon(Icons.remove_shopping_cart),
                          onPressed: () {
                            FirebaseFirestore.instance
                                .collection('cart')
                                .doc(productId)
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
                          productModel productCart = productModel(
                            name: selectedProductData!['nameCart'],
                            price: int.parse(selectedProductData!['priceCart']
                                    .toString()) *
                                productQuantities[selectedProductId]!,
                            imageUrl: selectedProductData!['imgCart'],
                            model: selectedProductData!['modelCart'],
                            desc: selectedProductData!['descCart'],
                            amount: productQuantities[selectedProductId]!,
                            width: selectedProductData!['widthCart'],
                            height: selectedProductData!['heightCart'],
                            depth: selectedProductData!['depthCart'],
                            longest: selectedProductData!['longestCart'],
                          );

                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) =>
                                  OrderPage(product: productCart),
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
