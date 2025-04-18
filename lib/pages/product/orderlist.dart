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
      body: StreamBuilder(
        stream: order.where('userPhone', isEqualTo: userPhone).snapshots(),
        builder: (context, AsyncSnapshot<QuerySnapshot> snapshot) {
          if (!snapshot.hasData || snapshot.data!.docs.isEmpty) {
            return Center(child: Text('ไม่มีคำสั่งซื้อ'));
          }

          return ListView(
            padding: EdgeInsets.all(10),
            children: snapshot.data!.docs.map((doc) {
              var data = doc.data() as Map<String, dynamic>;
              return OrderCard(data: data);
            }).toList(),
          );
        },
      ),
    );
  }
}

class OrderCard extends StatelessWidget {
  final Map<String, dynamic> data;

  OrderCard({required this.data});

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Colors.grey[200],
      margin: EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: EdgeInsets.all(10),
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
                  : Center(child: Text('No Image')),
            ),
            SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    data['nameOrderProduct'] ?? 'ชื่อสินค้า',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                  Text(
                      'สถานะการชำระเงิน: ${data['paymentStatus'] ?? 'รอชำระ'}'),
                  Text(
                      'สถานะการจัดส่ง: ${data['deliveryStatus'] ?? 'รอดำเนินการ'}'),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
