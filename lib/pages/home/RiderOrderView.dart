import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

class RiderOrderView extends StatefulWidget {
  final Map<String, dynamic> orderData;

  const RiderOrderView({super.key, required this.orderData});

  @override
  State<RiderOrderView> createState() => RiderOrderViewState();
}

class RiderOrderViewState extends State<RiderOrderView> {
  // --- Delivery status (รวม "ลูกค้ารับสินค้าแล้ว" ให้เท่ากับ "จัดส่งสำเร็จ") ---
  late String selectedStatus;
  final List<String> deliveryOptions = const [
    'รอดำเนินการ',
    'กำลังจัดส่ง',
    'จัดส่งสำเร็จ',
    'จัดส่งไม่สำเร็จ',
  ];

  /// map ชื่อสถานะ -> ฟิลด์เวลาในเอกสารหลัก
  /// เพิ่ม failedAt สำหรับ "จัดส่งไม่สำเร็จ"
  /// (เผื่อย้อนหลัง ถ้ามีค่า "ลูกค้ารับสินค้าแล้ว" จะ map เป็น deliveredAt)
  final Map<String, String> statusTimeField = const {
    'กำลังจัดส่ง': 'shippingAt',
    'จัดส่งสำเร็จ': 'deliveredAt',
    'ลูกค้ารับสินค้าแล้ว': 'deliveredAt',
    'จัดส่งไม่สำเร็จ': 'failedAt',
  };

  // --- Payment status ---
  late String selectedPaymentStatus;
  final List<String> paymentOptions = const [
    'รอชำระเงินปลายทาง',
    'ชำระเงินแล้ว',
  ];

  /// map สถานะชำระเงิน -> ฟิลด์เวลา
  final Map<String, String> paymentTimeField = const {
    'ชำระเงินแล้ว': 'paidAt',
  };

  @override
  void initState() {
    super.initState();

    // ตั้งค่าเริ่มต้นสถานะจัดส่ง; ถ้าเดิมเป็น "ลูกค้ารับสินค้าแล้ว" → ใช้ "จัดส่งสำเร็จ"
    final initialDeliveryStatus =
        (widget.orderData['deliveryStatus'] as String?) ?? 'กำลังจัดส่ง';
    selectedStatus = (initialDeliveryStatus == 'ลูกค้ารับสินค้าแล้ว')
        ? 'จัดส่งสำเร็จ'
        : initialDeliveryStatus;

    selectedPaymentStatus =
        (widget.orderData['paymentStatus'] as String?) ?? 'รอชำระเงินปลายทาง';
  }

  Future<void> updateDeliveryStatus(String newStatus) async {
    final String docId = widget.orderData['docId'] as String; // ต้องมี docId ใน order
    final orderRef =
        FirebaseFirestore.instance.collection('order').doc(docId);

    final String? timeField = statusTimeField[newStatus];
    final serverTime = FieldValue.serverTimestamp();

    try {
      await FirebaseFirestore.instance.runTransaction((tx) async {
        final snap = await tx.get(orderRef);
        final data = (snap.data() as Map<String, dynamic>?) ?? {};

        final update = <String, dynamic>{
          'deliveryStatus': newStatus,
          'statusChangedAt': serverTime, // log ทุกครั้งที่เปลี่ยนสถานะจัดส่ง
        };

        // เซ็ตเวลาครั้งแรกเท่านั้นสำหรับคีย์เวลาเฉพาะสถานะ
        if (timeField != null && data[timeField] == null) {
          update[timeField] = serverTime;
        }

        // กรณี newStatus = "จัดส่งสำเร็จ" ให้ลบค่าพลาดก่อนหน้า (ถ้ามี) เพื่อความสะอาดของข้อมูล
        if (newStatus == 'จัดส่งสำเร็จ' && data['failedAt'] != null) {
          update['failedAt'] = null;
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

  Future<void> updatePaymentStatus(String newStatus) async {
    final String docId = widget.orderData['docId'] as String;
    final orderRef =
        FirebaseFirestore.instance.collection('order').doc(docId);

    final String? timeField = paymentTimeField[newStatus];
    final serverTime = FieldValue.serverTimestamp();

    try {
      await FirebaseFirestore.instance.runTransaction((tx) async {
        final snap = await tx.get(orderRef);
        final data = (snap.data() as Map<String, dynamic>?) ?? {};

        final update = <String, dynamic>{
          'paymentStatus': newStatus,
          'paymentChangedAt': serverTime, // log ทุกครั้งที่เปลี่ยนสถานะจ่ายเงิน
        };

        // เซ็ต paidAt ครั้งแรกเท่านั้น
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
    final bool paymentDropdownEnabled =
        selectedPaymentStatus == 'รอชำระเงินปลายทาง';

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

                      // Payment status
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          const Text(
                            '💰 สถานะการชำระเงิน:',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: _dropdownContainer(
                              child: paymentDropdownEnabled
                                  ? DropdownButtonHideUnderline(
                                      child: DropdownButton<String>(
                                        value: selectedPaymentStatus,
                                        isExpanded: true,
                                        icon:
                                            const Icon(Icons.arrow_drop_down),
                                        items: paymentOptions
                                            .map((e) => DropdownMenuItem(
                                                  value: e,
                                                  child: Text(e),
                                                ))
                                            .toList(),
                                        onChanged: (v) async {
                                          if (v == null ||
                                              v == selectedPaymentStatus) {
                                            return;
                                          }
                                          setState(
                                              () => selectedPaymentStatus = v);
                                          await updatePaymentStatus(v);
                                          if (mounted) setState(() {});
                                        },
                                      ),
                                    )
                                  : Padding(
                                      padding: const EdgeInsets.symmetric(
                                          vertical: 14, horizontal: 4),
                                      child: Text(
                                        selectedPaymentStatus,
                                        style: const TextStyle(fontSize: 16),
                                      ),
                                    ),
                            ),
                          ),
                        ],
                      ),

                      const SizedBox(height: 16),

                      // Delivery status
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          const Text(
                            '🚚 สถานะการจัดส่ง:',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: _dropdownContainer(
                              child: DropdownButtonHideUnderline(
                                child: DropdownButton<String>(
                                  value: selectedStatus,
                                  isExpanded: true,
                                  icon: const Icon(Icons.arrow_drop_down),
                                  items: deliveryOptions
                                      .map((e) => DropdownMenuItem(
                                            value: e,
                                            child: Text(e),
                                          ))
                                      .toList(),
                                  onChanged: (v) async {
                                    if (v == null || v == selectedStatus) {
                                      return;
                                    }
                                    setState(() => selectedStatus = v);
                                    await updateDeliveryStatus(v);
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
      child: Text(text, style: const TextStyle(fontSize: 18)),
    );
  }
}
