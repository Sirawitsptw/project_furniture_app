import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:project_furnitureapp/pages/home/ProfileRider.dart';
import 'package:project_furnitureapp/pages/home/riderOrderView.dart';

class RiderHome extends StatefulWidget {
  const RiderHome({Key? key}) : super(key: key);

  @override
  State<RiderHome> createState() => RiderHomeState();
}

class RiderHomeState extends State<RiderHome> {
  int currentIndex = 0;

  final List<String> appBarTitles = ['รายการคำสั่งซื้อของลูกค้า', 'โปรไฟล์'];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(appBarTitles[currentIndex]),
        backgroundColor: Colors.deepPurple,
        foregroundColor: Colors.white,
      ),
      body: currentIndex == 0 ? buildOrderList() : const ProfileRider(),
      bottomNavigationBar: BottomNavigationBar(
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home), label: 'Orders'),
          BottomNavigationBarItem(icon: Icon(Icons.person), label: 'Profile'),
        ],
        currentIndex: currentIndex,
        onTap: (index) => setState(() => currentIndex = index),
      ),
    );
  }

  Widget buildOrderList() {
    return StreamBuilder<QuerySnapshot>(
      stream: FirebaseFirestore.instance.collection('order').snapshots(),
      builder: (context, snapshot) {
        if (!snapshot.hasData || snapshot.data!.docs.isEmpty) {
          return const Center(child: Text('ยังไม่มีคำสั่งซื้อ'));
        }

        final orders = snapshot.data!.docs;

        return ListView.builder(
          itemCount: orders.length,
          itemBuilder: (context, index) {
            final doc = orders[index];
            final orderData = doc.data() as Map<String, dynamic>;
            orderData['docId'] = doc.id;
            return Card(
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: ListTile(
                leading: ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: Image.network(
                    orderData['imageUrl'],
                    width: 60,
                    height: 60,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stackTrace) =>
                        const Icon(Icons.broken_image),
                  ),
                ),
                title: Text('ลูกค้า: ${orderData['nameCustomer']}'),
                subtitle: Text('สินค้า: ${orderData['nameOrderProduct']}\n'
                    'ราคา: ${orderData['priceOrder']}\n'
                    'เบอร์โทรศัพท์: ${orderData['phone']}\n'
                    'วิธ๊ชำระเงิน: ${orderData['paymentMethod']}\n'
                    'สถานะการชำระเงิน: ${orderData['paymentStatus']}\n'
                    'สถานะการจัดส่ง: ${orderData['deliveryStatus']}'),
                onTap: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) =>
                          RiderOrderView(orderData: orderData),
                    ),
                  );
                },
              ),
            );
          },
        );
      },
    );
  }
}
