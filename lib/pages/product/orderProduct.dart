import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:project_furnitureapp/pages/home/product_model.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

class OrderPage extends StatefulWidget {
  final productModel product;
  OrderPage({Key? key, required this.product}) : super(key: key);

  @override
  State<OrderPage> createState() => OrderPageState();
}

class OrderPageState extends State<OrderPage> {
  late productModel orderproduct;
  String? promptPayUrl;

  @override
  void initState() {
    super.initState();
    orderproduct = widget.product;
  }

  var _ctrlAddress = TextEditingController();
  var _ctrlName = TextEditingController();
  var _ctrlPhone = TextEditingController();
  String? _selectedSize;

  bool isDeliverySelected() => _selectedSize == 'ส่ง';

  Future<void> OrderProduct() async {
    CollectionReference order = FirebaseFirestore.instance.collection('order');
    return await order.add({
      'address': _ctrlAddress.text,
      'phone': _ctrlPhone.text,
      'nameCustomer': _ctrlName.text,
      'nameOrderProduct': orderproduct.name,
      'priceOrder': orderproduct.price,
      'deliveryOption': _selectedSize,
      'timeCart': FieldValue.serverTimestamp(),
    }).then((value) {
      print("Product Order: ${value.id}");
    }).catchError((error) {
      print("Failed to Order: $error");
    });
  }

  Future<void> makePayment() async {
    var url = Uri.parse('https://sandbox-cdnv3.chillpay.co/Payment/');
    var response = await http.post(
      url,
      body: {
        'MerchantCode': 'M035920',
        'OrderName': orderproduct.name,
        'Amount': orderproduct.price.toString(),
        'APIKey':
            '7FxwZsydrhhlDzz6EfQTB9eL6om4TOxy7K3IHEQv0hXh76YNvrQbTYjUGpvymq3l',
      },
    );

    if (response.statusCode == 200) {
      var responseData = json.decode(response.body);
      String orderNumber = responseData['order_number'];
      promptPayUrl = responseData['promptpay_url']; // เก็บ URL ของ QR Code
      checkPaymentStatus(orderNumber); // เรียกใช้ฟังก์ชันเพื่อตรวจสอบสถานะ
    } else {
      print('Error: ${response.statusCode}, ${response.body}');
    }
  }

  Future<void> checkPaymentStatus(String orderNumber) async {
    var url = Uri.parse(
        'https://sandbox-cdnv3.chillpay.co/PaymentStatus/$orderNumber');
    var response = await http.get(
      url,
      headers: {
        'Authorization':
            'Bearer 7FxwZsydrhhlDzz6EfQTB9eL6om4TOxy7K3IHEQv0hXh76YNvrQbTYjUGpvymq3l',
      },
    );

    if (response.statusCode == 200) {
      var paymentStatus = json.decode(response.body);
      if (paymentStatus['status'] == 'success') {
        print('Payment was successful');
        _showPaymentSuccessDialog(); // แสดงข้อความว่าชำระเงินเสร็จสิ้น
      } else {
        print('Payment failed or pending');
      }
    } else {
      print('Failed to retrieve payment status: ${response.statusCode}');
    }
  }

  void _showPaymentSuccessDialog() {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text('ชำระเงินเสร็จสิ้น'),
          content: Text('การชำระเงินของคุณสำเร็จแล้ว'),
          actions: <Widget>[
            TextButton(
              onPressed: () {
                Navigator.of(context).pop();
              },
              child: Text('ปิด'),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text('สั่งซื้อสินค้า'),
          backgroundColor: Colors.deepPurple,
        ),
        body: Center(
          child: SizedBox(
            width: 350,
            child: Column(
              children: [
                SizedBox(height: 30),
                productInfo(),
                SizedBox(height: 30),
                RadioButton(
                  selectedValue: _selectedSize,
                  onChanged: (value) {
                    setState(() {
                      _selectedSize = value;
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
                OrderButton(),
                if (promptPayUrl != null) ...[
                  SizedBox(height: 30),
                  Text('QR Code สำหรับการชำระเงิน:'),
                  Image.network(promptPayUrl!), // แสดง QR Code
                ],
              ],
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
        enabled: isDeliverySelected(), // เปิด/ปิด textfield
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
          OrderProduct();
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
          value: 'รับ',
          groupValue: selectedValue,
          onChanged: onChanged,
        ),
        RadioListTile<String>(
          title: Text('ส่งถึงบ้าน', style: TextStyle(fontSize: 14)),
          value: 'ส่ง',
          groupValue: selectedValue,
          onChanged: onChanged,
        ),
      ],
    );
  }
}
