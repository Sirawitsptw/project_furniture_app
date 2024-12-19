import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:project_furnitureapp/pages/home/cart.dart';
import 'package:project_furnitureapp/pages/home/product_model.dart';
import 'package:project_furnitureapp/pages/home/profile.dart';
import 'package:project_furnitureapp/pages/product/product_view.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  _HomePageState createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int currentIndex = 0;

  final List<String> appBarTitles = ['หน้าหลัก', 'ตะกร้าสินค้า', 'โปรไฟล์'];

  List<Widget> widgetOptions = [
    const Text('HomePage'),
    const cartpage(),
    const ProfilePage(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(appBarTitles[currentIndex]),
        backgroundColor: Colors.deepPurple,
      ),
      body: Center(
        child: currentIndex == 0
            ? StreamBuilder(
                stream: FirebaseFirestore.instance
                    .collection('product')
                    .snapshots(),
                builder: (context, AsyncSnapshot<QuerySnapshot> snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    return const CircularProgressIndicator();
                  }
                  if (!snapshot.hasData || snapshot.data!.docs.isEmpty) {
                    return const Text('No products available');
                  }
                  final List<productModel> productmodelList = snapshot
                      .data!.docs
                      .map((doc) => productModel
                          .fromMap(doc.data() as Map<String, dynamic>))
                      .toList();

                  return GridView.extent(
                    crossAxisSpacing: 10,
                    mainAxisSpacing: 10,
                    maxCrossAxisExtent: 200,
                    children: productmodelList
                        .asMap()
                        .entries
                        .map((entry) => createWidget(entry.value, entry.key))
                        .toList(),
                  );
                },
              )
            : widgetOptions[currentIndex],
      ),
      bottomNavigationBar: BottomNavigationBar(
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home), label: 'Home'),
          BottomNavigationBarItem(
              icon: Icon(Icons.shopping_basket), label: 'Cart'),
          BottomNavigationBarItem(icon: Icon(Icons.person), label: 'Profile'),
        ],
        currentIndex: currentIndex,
        onTap: (index) => setState(() => currentIndex = index),
      ),
    );
  }

  Widget createWidget(productModel model, int index) => GestureDetector(
        onTap: () {
          print('You Clicked from index = $index');
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => ProductView(
                productmodel: model,
              ),
            ),
          );
        },
        child: Card(
          child: Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  height: 150,
                  width: 150,
                  child: Image.network(model.imageUrl),
                ),
                Text('${model.name}                 ${model.price}'),
              ],
            ),
          ),
        ),
      );
}
