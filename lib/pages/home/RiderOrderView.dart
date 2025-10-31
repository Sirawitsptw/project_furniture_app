import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

class RiderOrderView extends StatefulWidget {
  final Map<String, dynamic> orderData;

  const RiderOrderView({super.key, required this.orderData});

  @override
  State<RiderOrderView> createState() => RiderOrderViewState();
}

class RiderOrderViewState extends State<RiderOrderView> {
  // --- Delivery status ---
  late String selectedStatus;
  final List<String> deliveryOptions = const [
    'รอดำเนินการ',
    'กำลังจัดส่ง',
    'จัดส่งสำเร็จ',
    'จัดส่งไม่สำเร็จ'
  ];

  // สำหรับกรณีรับเอง
  final List<String> pickupOptions = const [
    'ลูกค้ามารับสินค้าเอง',
    'ลูกค้ารับสินค้าแล้ว',
  ];

  /// map ชื่อสถานะจัดส่ง/รับเอง -> ฟิลด์เวลาบนเอกสารหลัก
  final Map<String, String> statusTimeField = const {
    'กำลังจัดส่ง': 'shippingAt',
    'จัดส่งสำเร็จ': 'deliveredAt',
    'ลูกค้ารับสินค้าแล้ว': 'pickedUpAt', // เพิ่มสำหรับรับเอง
  };

  // --- Payment status ---
  late String selectedPaymentStatus;
  final List<String> paymentOptions = const [
    'รอชำระเงินปลายทาง',
    'ชำระเงินแล้ว',
  ];

  /// map ชื่อสถานะชำระเงิน -> ฟิลด์เวลาบนเอกสารหลัก
  final Map<String, String> paymentTimeField = const {
    'ชำระเงินแล้ว': 'paidAt',
  };

  // บอกว่าออเดอร์นี้เลือก "รับสินค้าด้วยตนเอง" หรือไม่
  late bool isPickup;

  @override
  void initState() {
    super.initState();

    isPickup =
        (widget.orderData['deliveryOption'] as String?) == 'รับสินค้าด้วยตนเอง';

    // ตั้งค่าเริ่มต้นของสถานะจัดส่ง/รับ
    final initialDeliveryStatus =
        (widget.orderData['deliveryStatus'] as String?);

    if (isPickup) {
      // ถ้าเป็นออเดอร์รับเอง และสถานะเดิมไม่ใช่ 2 ค่านี้ ให้ตั้งต้นเป็น "ลูกค้ามารับสินค้าเอง"
      if (initialDeliveryStatus == null ||
          !pickupOptions.contains(initialDeliveryStatus)) {
        selectedStatus = 'ลูกค้ามารับสินค้าเอง';
      } else {
        selectedStatus = initialDeliveryStatus;
      }
    } else {
      // ส่งถึงบ้าน: คงลิสต์เดิม
      selectedStatus = initialDeliveryStatus ?? 'กำลังจัดส่ง';
    }

    selectedPaymentStatus =
        (widget.orderData['paymentStatus'] as String?) ?? 'รอชำระเงินปลายทาง';
  }

  Future<void> updateDeliveryStatus(String newStatus) async {
    final String docId = widget.orderData['docId']; // ถ้าใช้ id แทน docId เปลี่ยนตรงนี้
    final db = FirebaseFirestore.instance;
    final orderRef = db.collection('order').doc(docId);

    final String? timeField = statusTimeField[newStatus];
    final serverTime = FieldValue.serverTimestamp();

    try {
      await db.runTransaction((tx) async {
        final snap = await tx.get(orderRef);
        final data = (snap.data() as Map<String, dynamic>?) ?? {};

        final update = <String, dynamic>{
          'deliveryStatus': newStatus,
          'statusChangedAt': serverTime, // อัปเดตทุกครั้งที่เปลี่ยนสถานะจัดส่ง/รับ
        };

        // ตั้งเวลาครั้งแรกเท่านั้น (เช่น shippingAt/deliveredAt/pickedUpAt)
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

  // อัปเดตสถานะชำระเงิน
  Future<void> updatePaymentStatus(String newStatus) async {
    final String docId = widget.orderData['docId']; // ถ้าใช้ id แทน docId เปลี่ยนตรงนี้
    final db = FirebaseFirestore.instance;
    final orderRef = db.collection('order').doc(docId);

    final String? timeField = paymentTimeField[newStatus];
    final serverTime = FieldValue.serverTimestamp();

    try {
      await db.runTransaction((tx) async {
        final snap = await tx.get(orderRef);
        final data = (snap.data() as Map<String, dynamic>?) ?? {};

        final update = <String, dynamic>{
          'paymentStatus': newStatus,
          'paymentChangedAt': serverTime, // อัปเดตทุกครั้งที่เปลี่ยนสถานะชำระเงิน
        };

        // เซ็ต paidAt แค่ครั้งแรกเมื่อเปลี่ยนเป็น "ชำระเงินแล้ว"
        if (timeField != null && data[timeField] == null) {
          update[timeField] = serverTime;
        }

        tx.update(orderRef, update);
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('อัปเดตสถานะการชำระเงินแล้ว')),
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
    final paymentDropdownEnabled =
        selectedPaymentStatus == 'รอชำระเงินปลายทาง';

    // เลือกชุดตัวเลือกสถานะจัดส่งตามโหมด
    final List<String> currentDeliveryOptions =
        isPickup ? pickupOptions : deliveryOptions;

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
                      buildText('📞 เบอร์โทรศัพท์: ${widget.orderData['phone']}'),
                      buildText('📦 สินค้า: ${widget.orderData['nameOrderProduct']}'),
                      buildText('📦 จำนวน: ${widget.orderData['quantityOrder']} ชิ้น'),
                      buildText('💵 ราคา: ${widget.orderData['priceOrder']} บาท'),
                      buildText('🚚 วิธีจัดส่ง: ${widget.orderData['deliveryOption']}'),
                      buildText('💳 วิธีชำระเงิน: ${widget.orderData['paymentMethod']}'),
                      const Divider(height: 30),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          const Text(
                            '💰 สถานะการชำระเงิน:',
                            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w500),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: paymentDropdownEnabled
                                ? _dropdownContainer(
                                    child: DropdownButtonHideUnderline(
                                      child: DropdownButton<String>(
                                        value: selectedPaymentStatus,
                                        isExpanded: true,
                                        icon: const Icon(Icons.arrow_drop_down),
                                        style: const TextStyle(fontSize: 16, color: Colors.black),
                                        items: paymentOptions.map((String value) {
                                          return DropdownMenuItem<String>(
                                            value: value,
                                            child: Text(value),
                                          );
                                        }).toList(),
                                        onChanged: (String? newValue) {
                                          if (newValue != null &&
                                              newValue != selectedPaymentStatus) {
                                            setState(() {
                                              selectedPaymentStatus = newValue;
                                            });
                                            updatePaymentStatus(newValue).then((_) {
                                              if (mounted) setState(() {});
                                            });
                                          }
                                        },
                                      ),
                                    ),
                                  )
                                : _dropdownContainer(
                                    // แสดงแบบอ่านอย่างเดียว เมื่อไม่ใช่ "รอชำระเงินปลายทาง"
                                    child: Padding(
                                      padding: const EdgeInsets.symmetric(
                                          vertical: 14, horizontal: 4),
                                      child: Text(
                                        selectedPaymentStatus,
                                        style: const TextStyle(
                                            fontSize: 16, color: Colors.black87),
                                      ),
                                    ),
                                  ),
                          ),
                        ],
                      ),

                      const SizedBox(height: 16),

                      // --- Delivery status row (เปลี่ยนตามโหมด) ---
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          Text(
                            isPickup ? '🛒 สถานะการรับสินค้า:' : '🚚 สถานะการจัดส่ง:',
                            style: const TextStyle(
                                fontSize: 18, fontWeight: FontWeight.w500),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: _dropdownContainer(
                              child: DropdownButtonHideUnderline(
                                child: DropdownButton<String>(
                                  value: selectedStatus,
                                  isExpanded: true,
                                  icon: const Icon(Icons.arrow_drop_down),
                                  style: const TextStyle(
                                      fontSize: 16, color: Colors.black),
                                  items: currentDeliveryOptions
                                      .map((String value) {
                                    return DropdownMenuItem<String>(
                                      value: value,
                                      child: Text(value),
                                    );
                                  }).toList(),
                                  onChanged: (String? newValue) {
                                    if (newValue != null &&
                                        newValue != selectedStatus) {
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

  Widget _dropdownContainer({required Widget child}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        border: Border.all(color: Colors.deepPurple),
        borderRadius: BorderRadius.circular(12),
      ),
      child: child,
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
