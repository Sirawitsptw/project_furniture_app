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

  // จำนวนที่ผู้ใช้เลือกต่อแถว cart (key = cartDocId)
  final Map<String, int> productQuantities = {};
  // สต็อกคงเหลือตามสินค้าจริง (key = cartDocId)
  final Map<String, int> productStocks = {};

  void updateQuantity(String productId, int change) {
    final maxStock = productStocks[productId]; // อาจเป็น null ระหว่างโหลด
    final current = productQuantities[productId] ?? 1;

    int next = current + change;
    if (next < 1) next = 1;
    if (maxStock != null && next > maxStock) {
      next = maxStock;
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('จำนวนสูงสุดที่สั่งได้คือ $maxStock ชิ้น')),
        );
      }
    }
    setState(() {
      productQuantities[productId] = next;
    });
  }

  int _toInt(dynamic v, {int def = 0}) {
    if (v is int) return v;
    if (v is num) return v.toInt();
    if (v is String) return int.tryParse(v) ?? def;
    return def;
  }

  double _toDouble(dynamic v) {
    if (v is double) return v;
    if (v is num) return v.toDouble();
    if (v is String) return double.tryParse(v) ?? 0.0;
    return 0.0;
  }

  @override
  Widget build(BuildContext context) {
    final User? user = FirebaseAuth.instance.currentUser;
    final String userPhone = user?.phoneNumber ?? '';

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
            return const Center(child: Text('No items in the cart.'));
          }

          final cartItems = snapshot.data!.docs;

          return Column(
            children: [
              Expanded(
                child: ListView.builder(
                  itemCount: cartItems.length,
                  itemBuilder: (context, index) {
                    final item = cartItems[index];
                    final itemData = item.data() as Map<String, dynamic>;
                    final cartDocId = item.id;
                    productQuantities.putIfAbsent(cartDocId, () => 1);

                    // สตรีมสต็อกจากคอลเลกชัน product อิงชื่อสินค้า
                    return StreamBuilder<QuerySnapshot>(
                      stream: FirebaseFirestore.instance
                          .collection('product')
                          .where('name', isEqualTo: itemData['nameCart'])
                          .limit(1)
                          .snapshots(),
                      builder: (context, prodSnap) {
                        int stock = 0;
                        if (prodSnap.hasData && prodSnap.data!.docs.isNotEmpty) {
                          final prodData = prodSnap.data!.docs.first.data() as Map<String, dynamic>;
                          stock = _toInt(prodData['amount'], def: 0);
                        }
                        // อัปเดตแผนที่สต็อก (ไม่ต้อง setState เพราะรีบิลด์จาก Stream อยู่แล้ว)
                        productStocks[cartDocId] = stock;

                        // ถ้าเลือกไว้เกินสต็อก ให้บีบลงมาเท่าสต็อก
                        final currentQty = productQuantities[cartDocId] ?? 1;
                        if (stock > 0 && currentQty > stock) {
                          productQuantities[cartDocId] = stock;
                        }

                        final qty = productQuantities[cartDocId] ?? 1;
                        final unitPrice = _toInt(itemData['priceCart']);
                        final rowTotal = unitPrice * qty;

                        final bool outOfStock = stock <= 0;
                        final bool canInc = !outOfStock && (qty < stock);
                        final bool canDec = qty > 1;

                        return Opacity(
                          opacity: outOfStock ? 0.6 : 1,
                          child: Card(
                            margin: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
                            child: ListTile(
                              leading: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Radio<String>(
                                    value: cartDocId,
                                    groupValue: selectedProductId,
                                    onChanged: outOfStock
                                        ? null // สินค้าหมด: กันไม่ให้เลือกสั่ง
                                        : (value) {
                                            setState(() {
                                              selectedProductId = value;
                                              selectedProductData = itemData;
                                            });
                                          },
                                  ),
                                  Image.network(
                                    itemData['imgCart'],
                                    width: 50,
                                    height: 50,
                                    fit: BoxFit.cover,
                                    errorBuilder: (_, __, ___) => const Icon(Icons.broken_image),
                                  ),
                                ],
                              ),
                              title: Text(itemData['nameCart']),
                              subtitle: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const SizedBox(height: 4),
                                  Text(outOfStock
                                      ? 'สถานะ: สินค้าหมด'
                                      : 'คงเหลือ: $stock ชิ้น'),
                                  const SizedBox(height: 6),
                                  Row(
                                    children: [
                                      IconButton(
                                        icon: const Icon(Icons.remove),
                                        onPressed: canDec
                                            ? () => updateQuantity(cartDocId, -1)
                                            : null,
                                      ),
                                      Text('$qty'),
                                      IconButton(
                                        icon: const Icon(Icons.add),
                                        onPressed: canInc
                                            ? () => updateQuantity(cartDocId, 1)
                                            : null,
                                      ),
                                    ],
                                  ),
                                  Text('ราคา: $rowTotal บาท'),
                                ],
                              ),
                              trailing: IconButton(
                                icon: const Icon(Icons.remove_shopping_cart),
                                onPressed: () {
                                  FirebaseFirestore.instance
                                      .collection('cart')
                                      .doc(cartDocId)
                                      .delete();
                                },
                              ),
                              onTap: outOfStock
                                  ? null
                                  : () {
                                      setState(() {
                                        selectedProductId = cartDocId;
                                        selectedProductData = itemData;
                                      });
                                    },
                            ),
                          ),
                        );
                      },
                    );
                  },
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: ElevatedButton(
                  onPressed: (selectedProductData != null)
                      ? () {
                          final selId = selectedProductId!;
                          final selQty = productQuantities[selId] ?? 1;
                          final selStock = productStocks[selId] ?? 0;

                          if (selStock <= 0) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('สินค้าหมด ไม่สามารถสั่งซื้อได้')),
                            );
                            return;
                          }
                          if (selQty > selStock) {
                            setState(() {
                              productQuantities[selId] = selStock;
                            });
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(content: Text('ปรับจำนวนเป็น $selStock ตามสต็อกที่เหลือ')),
                            );
                            return;
                          }

                          final productCart = productModel(
                            name: selectedProductData!['nameCart'],
                            price: _toInt(selectedProductData!['priceCart']) * selQty,
                            imageUrl: selectedProductData!['imgCart'],
                            model: selectedProductData!['modelCart'],
                            desc: selectedProductData!['descCart'],
                            amount: selQty,
                            width: _toDouble(selectedProductData!['widthCart']),
                            height: _toDouble(selectedProductData!['heightCart']),
                            depth: _toDouble(selectedProductData!['depthCart']),
                            longest: _toDouble(selectedProductData!['longestCart']),
                          );

                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => OrderPage(product: productCart),
                            ),
                          );
                        }
                      : null,
                  child: const Text('สั่งซื้อ'),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
