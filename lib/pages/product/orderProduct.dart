import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:project_furnitureapp/pages/home/product_model.dart';

class OrderPage extends StatefulWidget {
  final productModel product;
  OrderPage({Key? key, required this.product}) : super(key: key);

  @override
  State<OrderPage> createState() => OrderPageState();
}

class OrderPageState extends State<OrderPage> {
  late productModel orderproduct;

  @override
  void initState() {
    super.initState();
    orderproduct = widget.product;
  }

  var _ctrlAddress = TextEditingController();
  var _ctrlName = TextEditingController();
  var _ctrlPhone = TextEditingController();

  Future<void> OrderProduct() async {
    CollectionReference order = FirebaseFirestore.instance.collection('order');
    return await order.add({
      'address': _ctrlAddress.text,
      'phone': _ctrlPhone.text,
      'nameCustomer': _ctrlName.text,
      'nameOrderProduct': orderproduct.name,
      'priceOrder': orderproduct.price,
      'timeCart': FieldValue.serverTimestamp(),
    }).then((value) {
      print("Product Order: ${value.id}");
    }).catchError((error) {
      print("Failed to Order: $error");
    });
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
            title: Text('สั่งซื้อสินค้า'), backgroundColor: Colors.deepPurple),
        body: Center(
          child: SizedBox(
            width: 350,
            child: Column(children: [
              SizedBox(height: 30),
              textFieldAddress(),
              SizedBox(height: 30),
              textFieldPhone(),
              SizedBox(height: 30),
              textFieldName(),
              SizedBox(height: 30),
              OrderButton(),
            ]),
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
}
