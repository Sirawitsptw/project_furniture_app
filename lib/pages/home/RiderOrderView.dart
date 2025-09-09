import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

class RiderOrderView extends StatefulWidget {
  final Map<String, dynamic> orderData;

  const RiderOrderView({super.key, required this.orderData});

  @override
  State<RiderOrderView> createState() => RiderOrderViewState();
}

class RiderOrderViewState extends State<RiderOrderView> {
  late String selectedStatus;
  final List<String> deliveryOptions = ['รอดำเนินการ', 'กำลังจัดส่ง', 'จัดส่งสำเร็จ'];

  /// map ชื่อสถานะ -> ฟิลด์เวลาบนเอกสารหลัก (ไม่มี orderedAt แล้ว)
  final Map<String, String> statusTimeField = const {
    'กำลังจัดส่ง': 'shippingAt',
    'จัดส่งสำเร็จ': 'deliveredAt',
  };

  @override
  void initState() {
    super.initState();
    selectedStatus = widget.orderData['deliveryStatus'] ?? 'กำลังจัดส่ง';
  }

  Future<void> updateDeliveryStatus(String newStatus) async {
    final String docId = widget.orderData['docId']; // ถ้าใช้ id แทน docId เปลี่ยนตรงนี้
    final db = FirebaseFirestore.instance;
    final orderRef = db.collection('order').doc(docId);

    final String? timeField = statusTimeField[newStatus];
    final serverTime = FieldValue.serverTimestamp();

    try {
      // ใช้ Transaction เพื่อ "ตั้งเวลาเฉพาะครั้งแรก" ของแต่ละสถานะ (ไม่เขียนทับ)
      await db.runTransaction((tx) async {
        final snap = await tx.get(orderRef);
        final data = (snap.data() as Map<String, dynamic>?) ?? {};

        final update = <String, dynamic>{
          'deliveryStatus': newStatus,
          'statusChangedAt': serverTime, // อัปเดตทุกครั้งที่มีการเปลี่ยนสถานะ
        };

        if (timeField != null && data[timeField] == null) {
          update[timeField] = serverTime;
        }

        tx.update(orderRef, update);
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('อัปเดตสถานะเรียบร้อยแล้ว')),
        );
      }
    } on FirebaseException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('อัปเดตไม่สำเร็จ: ${e.message}')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('รายละเอียดคำสั่งซื้อ'),
        backgroundColor: Colors.deepPurple,
        foregroundColor: Colors.white,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: SingleChildScrollView(
          child: Column(
            children: [
              Center(
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(16),
                  child: Image.network(
                    widget.orderData['imageUrl'],
                    width: 200,
                    height: 200,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stackTrace) =>
                        const Icon(Icons.broken_image, size: 100),
                  ),
                ),
              ),
              const SizedBox(height: 20),
              Card(
                elevation: 4,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      buildText('👤 ลูกค้า: ${widget.orderData['nameCustomer']}'),
                      buildText('🏠 ที่อยู่: ${widget.orderData['address']}'),
                      buildText('📦 สินค้า: ${widget.orderData['nameOrderProduct']}'),
                      buildText('💳 วิธีชำระเงิน: ${widget.orderData['paymentMethod']}'),
                      buildText('✅ สถานะการชำระเงิน: ${widget.orderData['paymentStatus']}'),
                      const Divider(height: 30),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          const Text(
                            '🚚 สถานะการจัดส่ง:',
                            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w500),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 12),
                              decoration: BoxDecoration(
                                border: Border.all(color: Colors.deepPurple),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: DropdownButtonHideUnderline(
                                child: DropdownButton<String>(
                                  value: selectedStatus,
                                  isExpanded: true,
                                  icon: const Icon(Icons.arrow_drop_down),
                                  style: const TextStyle(fontSize: 16, color: Colors.black),
                                  items: deliveryOptions.map((String value) {
                                    return DropdownMenuItem<String>(
                                      value: value,
                                      child: Text(value),
                                    );
                                  }).toList(),
                                  onChanged: (String? newValue) {
                                    if (newValue != null && newValue != selectedStatus) {
                                      setState(() {
                                        selectedStatus = newValue;
                                      });
                                      updateDeliveryStatus(newValue);
                                    }
                                  },
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget buildText(String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Text(
        text,
        style: const TextStyle(fontSize: 18),
      ),
    );
  }
}
