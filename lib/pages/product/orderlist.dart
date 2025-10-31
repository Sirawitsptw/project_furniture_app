import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:firebase_auth/firebase_auth.dart';

class OrderList extends StatefulWidget {
  @override
  OrderListState createState() => OrderListState();
}

class OrderListState extends State<OrderList> {
  final CollectionReference order =
      FirebaseFirestore.instance.collection('order');

  @override
  Widget build(BuildContext context) {
    User? user = FirebaseAuth.instance.currentUser;
    String userPhone = user?.phoneNumber ?? '';
    return Scaffold(
      appBar: AppBar(
        title: Text('รายการคำสั่งซื้อ'),
        backgroundColor: Colors.deepPurple,
      ),
      body: StreamBuilder<QuerySnapshot>(
        stream: order.where('userPhone', isEqualTo: userPhone).snapshots(),
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return Center(child: Text('เกิดข้อผิดพลาด: ${snapshot.error}'));
          }
          if (!snapshot.hasData || snapshot.data!.docs.isEmpty) {
            return const Center(child: Text('ไม่มีคำสั่งซื้อ'));
          }

          return ListView(
            padding: const EdgeInsets.all(10),
            children: snapshot.data!.docs.map((doc) {
              final data = doc.data() as Map<String, dynamic>;
              return OrderCard(
                docId: doc.id,          // ส่ง id ไปใช้ลบ/คืนสต็อก
                data: data,
              );
            }).toList(),
          );
        },
      ),
    );
  }
}

class OrderCard extends StatelessWidget {
  final String docId;
  final Map<String, dynamic> data;

  const OrderCard({super.key, required this.docId, required this.data});

  int _toInt(dynamic v, {int def = 0}) {
    if (v is int) return v;
    if (v is num) return v.toInt();
    if (v is String) return int.tryParse(v) ?? def;
    return def;
  }

  Future<void> _cancelOrder(BuildContext context) async {
    final db = FirebaseFirestore.instance;

    final String productName = (data['nameOrderProduct'] ?? '').toString();
    final int qty = _toInt(data['quantityOrder'], def: 1).clamp(1, 9999);
    final docRef = db.collection('order').doc(docId);

    try {
      // 1) หา product ที่ชื่อเดียวกับรายการในออเดอร์
      final prodQ = await db
          .collection('product')
          .where('name', isEqualTo: productName)
          .limit(1)
          .get();

      final WriteBatch batch = db.batch();

      // 2) ถ้าเจอสินค้า -> คืนสต็อกตามจำนวนที่สั่ง
      if (prodQ.docs.isNotEmpty) {
        final productRef = prodQ.docs.first.reference;
        batch.update(productRef, {'amount': FieldValue.increment(qty)});
      }

      // 3) ลบคำสั่งซื้อ
      batch.delete(docRef);

      await batch.commit();

      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('ยกเลิกคำสั่งซื้อเรียบร้อย')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('ยกเลิกไม่สำเร็จ: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final deliveryStatus = (data['deliveryStatus'] ?? 'รอดำเนินการ').toString();
    // ไม่ให้ยกเลิกถ้าส่งสำเร็จ/รับแล้ว (ปรับตามที่ต้องการได้)
    final bool cancellable = deliveryStatus != 'จัดส่งสำเร็จ' &&
        deliveryStatus != 'ลูกค้ารับสินค้าแล้ว';

    return Card(
      color: Colors.grey[200],
      margin: const EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: Row(
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.black),
              ),
              child: data['imageUrl'] != null
                  ? Image.network(data['imageUrl'], fit: BoxFit.cover)
                  : const Center(child: Text('No Image')),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    data['nameOrderProduct'] ?? 'ชื่อสินค้า',
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                  Text('จำนวน: ${data['quantityOrder'] ?? 1}'),
                  Text('ราคารวม: ${data['priceOrder']} บาท'),
                  Text('สถานะการชำระเงิน: ${data['paymentStatus'] ?? 'รอชำระ'}'),
                  Text('สถานะการจัดส่ง: ${deliveryStatus}'),
                  const SizedBox(height: 8),
                  Align(
                    alignment: Alignment.centerRight,
                    child: OutlinedButton.icon(
                      icon: const Icon(Icons.cancel, color: Colors.red),
                      label: const Text('ยกเลิกคำสั่งซื้อ',
                          style: TextStyle(color: Colors.red)),
                      style: OutlinedButton.styleFrom(
                        side: const BorderSide(color: Colors.red),
                      ),
                      onPressed: !cancellable
                          ? null
                          : () async {
                              final confirm = await showDialog<bool>(
                                context: context,
                                builder: (ctx) => AlertDialog(
                                  title: const Text('ยืนยันการยกเลิก?'),
                                  content: const Text(
                                      'เมื่อยกเลิกแล้ว รายการนี้จะถูกลบ และสต็อกสินค้าจะถูกคืน'),
                                  actions: [
                                    TextButton(
                                      onPressed: () => Navigator.pop(ctx, false),
                                      child: const Text('ไม่'),
                                    ),
                                    TextButton(
                                      onPressed: () => Navigator.pop(ctx, true),
                                      child: const Text('ใช่, ยกเลิก'),
                                    ),
                                  ],
                                ),
                              );

                              if (confirm == true) {
                                await _cancelOrder(context);
                              }
                            },
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
