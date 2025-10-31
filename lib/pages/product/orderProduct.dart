import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'package:project_furnitureapp/pages/home/product_model.dart';
import 'package:firebase_auth/firebase_auth.dart';

class OrderPage extends StatefulWidget {
  final productModel product;
  OrderPage({Key? key, required this.product}) : super(key: key);

  @override
  State<OrderPage> createState() => OrderPageState();
}

class OrderPageState extends State<OrderPage> {
  late productModel orderproduct;
  String? selectedSize;
  String? selectedPayment;

  final _ctrlAddress = TextEditingController();
  final _ctrlName = TextEditingController();
  final _ctrlPhone = TextEditingController();

  // ข้อมูลบัตร
  final _ctrlCardName = TextEditingController();
  final _ctrlCardNumber = TextEditingController();
  final _ctrlExpMonth = TextEditingController();
  final _ctrlExpYear = TextEditingController();
  final _ctrlCVC = TextEditingController();

  bool isDeliverySelected() => selectedSize == 'ส่งถึงบ้าน';

  String _last4(String raw) {
    final s = raw.replaceAll(' ', '').replaceAll('-', '');
    if (s.isEmpty) return '';
    return s.length >= 4 ? s.substring(s.length - 4) : s;
  }

  // สำหรับดจำนวนสินค้า
  int get orderQty {
    final raw = orderproduct.amount;
    final q = (raw is int) ? raw : int.tryParse(raw.toString()) ?? 1;
    return q < 1 ? 1 : q; 
  }

  /// สั่งซื้อสินค้า
  Future OrderProduct({String? paymentStatus}) async {
    final orderCol = FirebaseFirestore.instance.collection('order');
    final user = FirebaseAuth.instance.currentUser;
    final userPhone = user?.phoneNumber ?? '';

    // ตัดสินค่า paymentStatus เอง หากไม่ถูกส่งมา
    if (paymentStatus == null) {
      if (selectedPayment == "ชำระเงินปลายทาง") {
        paymentStatus = "รอชำระเงินปลายทาง";
      } else {
        paymentStatus = "ชำระเงินแล้ว";
      }
    }

    // ดึง type ของสินค้า (ถ้ามี)
    final productSnapshot = await FirebaseFirestore.instance
        .collection('product')
        .where('name', isEqualTo: orderproduct.name)
        .limit(1)
        .get();

    String? productType;
    if (productSnapshot.docs.isNotEmpty) {
      final doc = productSnapshot.docs.first;
      final data = doc.data();
      productType = data['type'];
    }

    String riderName = '';
      if (productType == 'โต๊ะ') {
        riderName = 'ไรเดอร์1';
      } else if (productType == 'เก้าอี้') {
        riderName = 'ไรเดอร์2';
      } else if (productType == 'ตู้') {
        riderName = 'ไรเดอร์3';
      }

    // เตรียมข้อมูลคำสั่งซื้อ
    final Map<String, dynamic> orderData = {
      'userPhone': userPhone,
      'address': _ctrlAddress.text,
      'phone': _ctrlPhone.text,
      'nameCustomer': _ctrlName.text,
      'nameOrderProduct': orderproduct.name,
      'type': productType,
      'quantityOrder': orderQty,          
      'priceOrder': orderproduct.price,              
      'deliveryOption': selectedSize,
      'deliveryStatus': "รอดำเนินการ",
      'rider': riderName,
      'paymentMethod': selectedPayment,
      'paymentStatus': paymentStatus,
      'imageUrl': orderproduct.imageUrl,
      'timeOrder': FieldValue.serverTimestamp(),
    };

    // ถ้าเป็นชำระเงินออนไลน์ → เก็บข้อมูลบัตรเป็นฟิลด์ top-level
    if (selectedPayment == "ชำระเงินออนไลน์") {
      final int expMonth = int.tryParse(_ctrlExpMonth.text) ?? 0;
      final int expYear  = int.tryParse(_ctrlExpYear.text) ?? 0;
      final String last4 = _last4(_ctrlCardNumber.text);

      orderData.addAll({
        'cardNumber': last4,                     // เลขท้าย 4 หลัก
        'nameCard': _ctrlCardName.text.trim(),   // ชื่อบนบัตร
        'expMonth': expMonth,                     // เดือนหมดอายุ
        'expYear' : expYear,                    // ปีหมดอายุ
      });
    }

  try {
    // หา product doc ที่จะล็อกแถว
    final prodSnap = await FirebaseFirestore.instance
        .collection('product')
        .where('name', isEqualTo: orderproduct.name)
        .limit(1)
        .get();
    if (prodSnap.docs.isEmpty) {
      throw Exception('ไม่พบสินค้า');
    }
    final productRef = prodSnap.docs.first.reference;

    await FirebaseFirestore.instance.runTransaction((tx) async {
      // อ่านสต็อกปัจจุบัน
      final snap = await tx.get(productRef);
      final data = (snap.data() as Map<String, dynamic>? ) ?? {};
      final int current = (data['amount'] is int)
          ? data['amount'] as int
          : int.tryParse(data['amount']?.toString() ?? '') ?? 0;

      // เช็คพอไหม
      if (current < orderQty) {
        throw Exception('สินค้าหมดหรือสต็อกไม่พอ');
      }

      // ลดสต็อก
      tx.update(productRef, {'amount': current - orderQty});

      // สร้างออเดอร์ (พร้อม docId)
      final orderRef = orderCol.doc();
      tx.set(orderRef, {
        ...orderData,
        'docId': orderRef.id,
      });
    });

    // สำเร็จ
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text("สั่งซื้อสินค้าเสร็จสิ้น"),
        content: Text(
          selectedPayment == "ชำระเงินปลายทาง"
            ? "สั่งซื้อสินค้าเสร็จสิ้น (รอชำระเงินปลายทาง)"
            : "สั่งซื้อสินค้าเสร็จสิ้น (ชำระเงินแล้ว)",
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.of(context).pop();
              Navigator.of(context).pop();
              Navigator.of(context).pop();
            },
            child: const Text("ตกลง"),
          ),
        ],
      ),
    );
  } catch (e) {
  // ไม่พอ/ชนกัน → แจ้งผู้ใช้
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text("สั่งซื้อไม่สำเร็จ"),
        content: const Text("สินค้าหมดหรือสต็อกไม่พอ"),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text("ตกลง"),
          ),
        ],
      ),
    );
  }

  }

  /// โฟลว์ชำระเงินออนไลน์
  Future<void> _processOnlinePayment() async {
    final token = await _createToken();
    if (token == null) {
      showDialog(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: const Text("เกิดข้อผิดพลาด"),
            content: const Text("ไม่สามารถสร้าง Token สำหรับบัตรเครดิตได้"),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text("ตกลง"),
              )
            ],
          );
        },
      );
      return;
    }

    // ตัดเงิน
    final paymentSuccess = await _chargeCard(token);
    if (paymentSuccess) {
      await OrderProduct(paymentStatus: "ชำระเงินแล้ว");
    } else {
      showDialog(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: const Text("การชำระเงินล้มเหลว"),
            content: const Text("กรุณาลองใหม่อีกครั้ง"),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text("ตกลง"),
              )
            ],
          );
        },
      );
    }
  }

  /// สร้าง token จาก Omise (ใช้ pkey)
  Future<String?> _createToken() async {
    final url = Uri.parse('https://vault.omise.co/tokens');
    final headers = {
      'Authorization': 'Basic ${base64Encode(utf8.encode('pkey_test_62u1x0xhtbfav9nr87w:'))}',
      'Content-Type': 'application/json',
    };

    final body = jsonEncode({
      'card': {
        'name': _ctrlCardName.text,
        'number': _ctrlCardNumber.text,
        'expiration_month': int.tryParse(_ctrlExpMonth.text) ?? 0,
        'security_code': _ctrlCVC.text,
        'expiration_year': int.tryParse(_ctrlExpYear.text) ?? 0,
      }
    });

    final response = await http.post(url, headers: headers, body: body);
    if (response.statusCode == 200) {
      final data = jsonDecode(response.body);
      return data['id'];
    } else {
      return null;
    }
  }

  /// ตัดเงินผ่าน Omise (ใช้ skey)
  Future<bool> _chargeCard(String token) async {
    final url = Uri.parse('https://api.omise.co/charges');
    final headers = {
      'Authorization': 'Basic ${base64Encode(utf8.encode('skey_test_62u1x0xwk6kmvednpqy:'))}',
      'Content-Type': 'application/json',
    };

    final body = jsonEncode({
      'amount': orderproduct.price * 100, // หน่วยสตางค์
      'currency': 'THB',
      'card': token,
    });

    final response = await http.post(url, headers: headers, body: body);
    return response.statusCode == 200;
  }

  /// Dialog กรอกข้อมูลบัตร
  Future<void> _showPaymentDialog() async {
    await showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text("ชำระเงินออนไลน์"),
          content: SingleChildScrollView(
            child: Column(
              children: [
                TextField(
                  controller: _ctrlCardName,
                  decoration: const InputDecoration(
                    border: OutlineInputBorder(),
                    hintText: 'ชื่อบนบัตร',
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _ctrlCardNumber,
                  decoration: const InputDecoration(
                    border: OutlineInputBorder(),
                    hintText: 'เลขบัตรเครดิต',
                  ),
                  keyboardType: TextInputType.number,
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _ctrlExpMonth,
                        decoration: const InputDecoration(
                          border: OutlineInputBorder(),
                          hintText: 'เดือน',
                        ),
                        keyboardType: TextInputType.number,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: TextField(
                        controller: _ctrlExpYear,
                        decoration: const InputDecoration(
                          border: OutlineInputBorder(),
                          hintText: 'ปี',
                        ),
                        keyboardType: TextInputType.number,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _ctrlCVC,
                  decoration: const InputDecoration(
                    border: OutlineInputBorder(),
                    hintText: 'CVC',
                  ),
                  keyboardType: TextInputType.number,
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text("ยกเลิก"),
            ),
            TextButton(
              onPressed: () async {
                Navigator.of(context).pop();
                await _processOnlinePayment();
              },
              child: const Text("ชำระเงิน"),
            ),
          ],
        );
      },
    );
  }

  @override
  void initState() {
    super.initState();
    orderproduct = widget.product;
    prefillFromUser();
  }

  String toThaiPhone(String? raw) {
    if (raw == null) return '';
    final s = raw.replaceAll(' ', '');
    if (s.isEmpty) return '';
    if (s.startsWith('+66')) return '0${s.substring(3)}';
    if (s.startsWith('66'))  return '0${s.substring(2)}';
    return s;
  }

  Future<void> prefillFromUser() async {
    try {
      final authUser = FirebaseAuth.instance.currentUser;
      if (authUser == null) return;

      final doc = await FirebaseFirestore.instance
          .collection('user')
          .doc(authUser.uid)
          .get();

      // ชื่อ
      String name = '';
      if (doc.exists) {
        final d = doc.data() as Map<String, dynamic>;
        final first = (d['firstName'] ?? d['name'] ?? '').toString().trim();
        final last  = (d['lastName']  ?? '').toString().trim();
        name = [first, last].where((e) => e.isNotEmpty).join(' ');
        final phoneFromDoc = (d['phone'] ?? '').toString();
        if (phoneFromDoc.isNotEmpty) {
          _ctrlPhone.text = toThaiPhone(phoneFromDoc);
        }
      }

      // fallback จาก FirebaseAuth
      if (name.isEmpty) {
        name = (authUser.displayName ?? '').trim();
      }
      if (_ctrlName.text.isEmpty) {
        _ctrlName.text = name;
      }
      if (_ctrlPhone.text.isEmpty) {
        _ctrlPhone.text = toThaiPhone(authUser.phoneNumber);
      }
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: const Text('สั่งซื้อสินค้า'),
          backgroundColor: Colors.deepPurple,
        ),
        body: SingleChildScrollView(
          child: Center(
            child: SizedBox(
              width: 350,
              child: Column(
                children: [
                  const SizedBox(height: 30),
                  productInfo(),
                  const SizedBox(height: 30),

                  // การจัดส่ง
                  RadioButton(
                    selectedValue: selectedSize,
                    onChanged: (value) {
                      setState(() {
                        selectedSize = value;
                      });
                    },
                  ),
                  const SizedBox(height: 30),

                  // ฟิลด์ต่าง ๆ
                  textFieldAddress(),
                  const SizedBox(height: 30),
                  textFieldPhone(),
                  const SizedBox(height: 30),
                  textFieldName(),
                  const SizedBox(height: 30),

                  // วิธีชำระเงิน
                  PaymentRadioButton(
                    selectedValue: selectedPayment,
                    onChanged: (value) {
                      setState(() {
                        selectedPayment = value;
                      });
                    },
                  ),
                  const SizedBox(height: 30),

                  // ปุ่มสั่งซื้อ
                  OrderButton(),
                  const SizedBox(height: 30),
                ],
              ),
            ),
          ),
        ),
      );

  OutlineInputBorder outlineBorder() =>
      const OutlineInputBorder(borderSide: BorderSide(color: Colors.grey, width: 2));

  TextStyle textStyle() =>
      const TextStyle(color: Colors.indigo, fontSize: 20, fontWeight: FontWeight.normal);

  Widget textFieldAddress() => TextField(
        controller: _ctrlAddress,
        decoration: InputDecoration(border: outlineBorder(), hintText: 'ที่อยู่สำหรับจัดส่ง'),
        keyboardType: TextInputType.text,
        style: textStyle(),
        enabled: isDeliverySelected(),
      );

  Widget textFieldName() => TextField(
        controller: _ctrlName,
        decoration: InputDecoration(border: outlineBorder(), hintText: 'ชื่อผู้สั่งซื้อ'),
        keyboardType: TextInputType.text,
        style: textStyle(),
      );

  Widget textFieldPhone() => TextField(
        controller: _ctrlPhone,
        decoration: InputDecoration(border: outlineBorder(), hintText: 'เบอร์โทรศัพท์'),
        keyboardType: TextInputType.number,
        style: textStyle(),
      );

  Widget OrderButton() => ElevatedButton(
        onPressed: () {
          // if (selectedSize == null || selectedPayment == null) {
          //   ScaffoldMessenger.of(context).showSnackBar(
          //     const SnackBar(content: Text("กรุณากรอกข้อมูลให้ครบ")),
          //   );
          //   return;
          // }

          if (selectedPayment == "ชำระเงินออนไลน์") {
            _showPaymentDialog();
          } else {
            OrderProduct();
          }
        },
        style: ButtonStyle(
          backgroundColor: MaterialStateProperty.all<Color>(Colors.purple),
          foregroundColor: MaterialStateProperty.all<Color>(Colors.white),
        ),
        child: const Padding(
          padding: EdgeInsets.symmetric(horizontal: 80, vertical: 10),
          child: Text('สั่งซื้อ', style: TextStyle(fontSize: 18)),
        ),
      );

  Widget productInfo() => Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: Colors.grey[200],
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            SizedBox(
              width: 60,
              height: 60,
              child: orderproduct.imageUrl != null
                  ? Image.network(orderproduct.imageUrl!, fit: BoxFit.cover)
                  : const Icon(Icons.image, size: 40, color: Colors.grey),
            ),
            const SizedBox(width: 20),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  orderproduct.name,
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                Text(
                  'ราคา: ${orderproduct.price} บาท',
                  style: const TextStyle(fontSize: 16),
                ),
              ],
            ),
          ],
        ),
      );
}

class RadioButton extends StatelessWidget {
  final String? selectedValue;
  final ValueChanged<String?> onChanged;

  const RadioButton({
    super.key,
    required this.selectedValue,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        RadioListTile<String>(
          title: const Text('รับสินค้าด้วยตนเอง(เวลา 9.00 - 17.00)', style: TextStyle(fontSize: 14)),
          value: 'รับสินค้าด้วยตนเอง',
          groupValue: selectedValue,
          onChanged: onChanged,
        ),
        RadioListTile<String>(
          title: const Text('ส่งถึงบ้าน', style: TextStyle(fontSize: 14)),
          value: 'ส่งถึงบ้าน',
          groupValue: selectedValue,
          onChanged: onChanged,
        ),
      ],
    );
  }
}

class PaymentRadioButton extends StatelessWidget {
  final String? selectedValue;
  final ValueChanged<String?> onChanged;

  const PaymentRadioButton({
    super.key,
    required this.selectedValue,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        RadioListTile<String>(
          title: const Text('ชำระเงินปลายทาง', style: TextStyle(fontSize: 14)),
          value: 'ชำระเงินปลายทาง',
          groupValue: selectedValue,
          onChanged: onChanged,
        ),
        RadioListTile<String>(
          title: const Text('ชำระเงินออนไลน์', style: TextStyle(fontSize: 14)),
          value: 'ชำระเงินออนไลน์',
          groupValue: selectedValue,
          onChanged: onChanged,
        ),
      ],
    );
  }
}
