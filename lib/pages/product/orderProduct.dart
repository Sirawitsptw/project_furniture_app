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

  var _ctrlAddress = TextEditingController();
  var _ctrlName = TextEditingController();
  var _ctrlPhone = TextEditingController();
  var _ctrlCardName = TextEditingController();
  var _ctrlCardNumber = TextEditingController();
  var _ctrlExpMonth = TextEditingController();
  var _ctrlExpYear = TextEditingController();
  var _ctrlCVC = TextEditingController();

  bool isDeliverySelected() => selectedSize == 'ส่งถึงบ้าน';

  Future OrderProduct({String? paymentStatus}) async {
    CollectionReference order = FirebaseFirestore.instance.collection('order');
    CollectionReference product =
        FirebaseFirestore.instance.collection('product');
    User? user = FirebaseAuth.instance.currentUser;
    String userPhone = user?.phoneNumber ?? '';

    if (paymentStatus == null) {
      if (selectedPayment == "ชำระเงินปลายทาง") {
        paymentStatus = "รอชำระเงินปลายทาง";
      } else {
        paymentStatus = "ชำระเงินแล้ว";
      }
    }

    return await order.add({
      'userPhone': userPhone,
      'address': _ctrlAddress.text,
      'phone': _ctrlPhone.text,
      'nameCustomer': _ctrlName.text,
      'nameOrderProduct': orderproduct.name,
      'priceOrder': orderproduct.price,
      'deliveryOption': selectedSize,
      'deliveryStatus': "รอดำเนินการ",
      'paymentMethod': selectedPayment,
      'paymentStatus': paymentStatus,
      'imageUrl': orderproduct.imageUrl,
      'timeOrder': FieldValue.serverTimestamp(),
    }).then((value) async {
      print("Product Order: ${value.id}");

      try {
        QuerySnapshot querySnapshot =
            await product.where('name', isEqualTo: orderproduct.name).get();

        if (querySnapshot.docs.isNotEmpty) {
          DocumentSnapshot doc = querySnapshot.docs.first;
          Map<String, dynamic> data = doc.data() as Map<String, dynamic>;
          int currentAmount = data['amount'] ?? 0;
          if (currentAmount > 0) {
            await doc.reference.update({'amount': currentAmount - 1});
          }
        }
      } catch (e) {}

      showDialog(
          context: context,
          builder: (context) {
            return AlertDialog(
              title: Text("สั่งซื้อสินค้าเสร็จสิ้น"),
              content: Text(selectedPayment == "ชำระเงินปลายทาง"
                  ? "สั่งซื้อสินค้าเสร็จสิ้น (รอชำระเงินปลายทาง)"
                  : "สั่งซื้อสินค้าเสร็จสิ้น (ชำระเงินแล้ว)"),
              actions: [
                TextButton(
                    onPressed: () {
                      Navigator.of(context).pop();
                      Navigator.of(context).pop();
                      Navigator.of(context).pop();
                    },
                    child: Text("ตกลง"))
              ],
            );
          });
    }).catchError((error) {
      print("Failed to Order: $error");
      showDialog(
          context: context,
          builder: (context) {
            return AlertDialog(
              title: Text("เกิดข้อผิดพลาด"),
              content: Text("ไม่สามารถสั่งซื้อสินค้าได้"),
              actions: [
                TextButton(
                    onPressed: () {
                      Navigator.of(context).pop();
                    },
                    child: Text("ตกลง"))
              ],
            );
          });
    });
  }

  Future<void> _processOnlinePayment() async {
    String? token = await _createToken();
    if (token == null) {
      showDialog(
          context: context,
          builder: (context) {
            return AlertDialog(
              title: Text("เกิดข้อผิดพลาด"),
              content: Text("ไม่สามารถสร้าง Token สำหรับบัตรเครดิตได้"),
              actions: [
                TextButton(
                    onPressed: () {
                      Navigator.of(context).pop();
                    },
                    child: Text("ตกลง"))
              ],
            );
          });
      return;
    }

    bool paymentSuccess = await _chargeCard(token);
    if (paymentSuccess) {
      await OrderProduct(paymentStatus: "ชำระเงินแล้ว");
    } else {
      showDialog(
          context: context,
          builder: (context) {
            return AlertDialog(
              title: Text("การชำระเงินล้มเหลว"),
              content: Text("กรุณาลองใหม่อีกครั้ง"),
              actions: [
                TextButton(
                    onPressed: () {
                      Navigator.of(context).pop();
                    },
                    child: Text("ตกลง"))
              ],
            );
          });
    }
  }

  Future<String?> _createToken() async {
    final url = Uri.parse('https://vault.omise.co/tokens');
    final headers = {
      'Authorization':
          'Basic ${base64Encode(utf8.encode('pkey_test_62u1x0xhtbfav9nr87w:'))}',
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
      print("Token Error: ${response.body}");
      return null;
    }
  }

  Future<bool> _chargeCard(String token) async {
    final url = Uri.parse('https://api.omise.co/charges');
    final headers = {
      'Authorization':
          'Basic ${base64Encode(utf8.encode('skey_test_62u1x0xwk6kmvednpqy:'))}',
      'Content-Type': 'application/json',
    };

    final body = jsonEncode({
      'amount': orderproduct.price * 100,
      'currency': 'THB',
      'card': token,
    });

    final response = await http.post(url, headers: headers, body: body);
    if (response.statusCode == 200) {
      return true;
    } else {
      print("Charge Error: ${response.body}");
      return false;
    }
  }

  Future<void> _showPaymentDialog() async {
    await showDialog(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: Text("ชำระเงินออนไลน์"),
            content: SingleChildScrollView(
              child: Column(
                children: [
                  TextField(
                    controller: _ctrlCardName,
                    decoration: InputDecoration(
                        border: OutlineInputBorder(), hintText: 'ชื่อบนบัตร'),
                  ),
                  SizedBox(height: 10),
                  TextField(
                    controller: _ctrlCardNumber,
                    decoration: InputDecoration(
                        border: OutlineInputBorder(),
                        hintText: 'เลขบัตรเครดิต'),
                    keyboardType: TextInputType.number,
                  ),
                  SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _ctrlExpMonth,
                          decoration: InputDecoration(
                              border: OutlineInputBorder(), hintText: 'เดือน'),
                          keyboardType: TextInputType.number,
                        ),
                      ),
                      SizedBox(width: 10),
                      Expanded(
                        child: TextField(
                          controller: _ctrlExpYear,
                          decoration: InputDecoration(
                              border: OutlineInputBorder(), hintText: 'ปี'),
                          keyboardType: TextInputType.number,
                        ),
                      ),
                    ],
                  ),
                  SizedBox(height: 10),
                  TextField(
                    controller: _ctrlCVC,
                    decoration: InputDecoration(
                        border: OutlineInputBorder(), hintText: 'CVC'),
                    keyboardType: TextInputType.number,
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(
                  onPressed: () {
                    Navigator.of(context).pop();
                  },
                  child: Text("ยกเลิก")),
              TextButton(
                  onPressed: () async {
                    Navigator.of(context).pop();
                    await _processOnlinePayment();
                  },
                  child: Text("ชำระเงิน"))
            ],
          );
        });
  }

  @override
  void initState() {
    super.initState();
    orderproduct = widget.product;
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text('สั่งซื้อสินค้า'),
          backgroundColor: Colors.deepPurple,
        ),
        body: SingleChildScrollView(
          child: Center(
            child: SizedBox(
              width: 350,
              child: Column(
                children: [
                  SizedBox(height: 30),
                  productInfo(),
                  SizedBox(height: 30),
                  RadioButton(
                    selectedValue: selectedSize,
                    onChanged: (value) {
                      setState(() {
                        selectedSize = value;
                      });
                    },
                  ),
                  SizedBox(height: 30),
                  textFieldAddress(),
                  SizedBox(height: 30),
                  textFieldPhone(),
                  SizedBox(height: 30),
                  textFieldName(),
                  SizedBox(height: 30),
                  PaymentRadioButton(
                    selectedValue: selectedPayment,
                    onChanged: (value) {
                      setState(() {
                        selectedPayment = value;
                      });
                    },
                  ),
                  SizedBox(height: 30),
                  OrderButton(),
                  SizedBox(height: 30),
                ],
              ),
            ),
          ),
        ),
      );

  OutlineInputBorder outlineBorder() =>
      OutlineInputBorder(borderSide: BorderSide(color: Colors.grey, width: 2));

  TextStyle textStyle() => TextStyle(
      color: Colors.indigo, fontSize: 20, fontWeight: FontWeight.normal);

  Widget textFieldAddress() => TextField(
        controller: _ctrlAddress,
        decoration: InputDecoration(
            border: outlineBorder(), hintText: 'ที่อยู่สำหรับจัดส่ง'),
        keyboardType: TextInputType.text,
        style: textStyle(),
        enabled: isDeliverySelected(),
      );

  Widget textFieldName() => TextField(
        controller: _ctrlName,
        decoration: InputDecoration(
            border: outlineBorder(), hintText: 'ชื่อผู้สั่งซื้อ'),
        keyboardType: TextInputType.text,
        style: textStyle(),
      );

  Widget textFieldPhone() => TextField(
        controller: _ctrlPhone,
        decoration:
            InputDecoration(border: outlineBorder(), hintText: 'เบอร์โทรศัพท์'),
        keyboardType: TextInputType.number,
        style: textStyle(),
      );

  Widget OrderButton() => ElevatedButton(
        onPressed: () {
          // if (selectedSize == null) {
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
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 80, vertical: 10),
          child: Text('สั่งซื้อ', style: TextStyle(fontSize: 18)),
        ),
      );

  Widget productInfo() => Container(
        padding: EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: Colors.grey[200],
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            Container(
              width: 60,
              height: 60,
              child: orderproduct.imageUrl != null
                  ? Image.network(
                      orderproduct.imageUrl!,
                      fit: BoxFit.cover,
                    )
                  : Icon(Icons.image, size: 40, color: Colors.grey),
            ),
            SizedBox(width: 20),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  orderproduct.name,
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                Text(
                  'ราคา: ${orderproduct.price} บาท',
                  style: TextStyle(fontSize: 16),
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

  RadioButton({
    required this.selectedValue,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        RadioListTile<String>(
          title: Text('รับสินค้าด้วยตนเอง(เวลา 9.00 - 17.00)',
              style: TextStyle(fontSize: 14)),
          value: 'รับสินค้าด้วยตนเอง',
          groupValue: selectedValue,
          onChanged: onChanged,
        ),
        RadioListTile<String>(
          title: Text('ส่งถึงบ้าน', style: TextStyle(fontSize: 14)),
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

  PaymentRadioButton({
    required this.selectedValue,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        RadioListTile<String>(
          title: Text('ชำระเงินปลายทาง', style: TextStyle(fontSize: 14)),
          value: 'ชำระเงินปลายทาง',
          groupValue: selectedValue,
          onChanged: onChanged,
        ),
        RadioListTile<String>(
          title: Text('ชำระเงินออนไลน์', style: TextStyle(fontSize: 14)),
          value: 'ชำระเงินออนไลน์',
          groupValue: selectedValue,
          onChanged: onChanged,
        ),
      ],
    );
  }
}
