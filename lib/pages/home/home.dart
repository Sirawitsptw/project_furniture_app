//import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
//import 'package:google_fonts/google_fonts.dart';
import 'package:project_furnitureapp/pages/home/cart.dart';
import 'package:project_furnitureapp/pages/home/product_model.dart';
import 'package:project_furnitureapp/pages/home/profile.dart';
//import 'package:project_furnitureapp/pages/login/login.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  _HomePageState createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  final FirebaseStorage _storage = FirebaseStorage.instance;
  int currentIndex = 0;
  List widgetOptions = [
    const Text('HomePage'),
    const cartpage(),
    const profilepage()
  ];

  List<Widget> widgets = [];

  @override
  void initState() {
    super.initState();
    readData();
  }

  Future<Null> readData() async {
    await Firebase.initializeApp().then((value) async {
      print('Initialize Success');
      await FirebaseFirestore.instance
          .collection('product')
          .orderBy('name')
          .snapshots()
          .listen((event) {
        print('Snapshots = ${event.docs}');
        for (var snapshot in event.docs) {
          Map<String, dynamic> map = snapshot.data();
          print('map = ${map}');
          productModel model = productModel.fromMap(map);
          print('name = ${model.name}');
          setState(() {
            widgets.add(createWidget(model));
          });
        }
      });
    });
  }

  Widget createWidget(productModel model) => Card(
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                height: 150,
                width: 150,
                child: Image.network(model.imageUrl),
              ),
              Text(model.name),
            ],
          ),
        ),
      );

  @override
  Widget build(BuildContext context) {
    return nav(context);
  }

  Widget nav(BuildContext context) {
    return Scaffold(
      body: Center(
        child: currentIndex == 0
            ? widgets.length == 0
                ? CircularProgressIndicator()
                : GridView.extent(
                    crossAxisSpacing: 10, // ระยะห่างระหว่างคอลัมน์
                    mainAxisSpacing: 10, // ระยะห่างระหว่างแถว
                    maxCrossAxisExtent: 200,
                    children: widgets,
                  )
            : widgetOptions[
                currentIndex], // แสดงหน้า Cart และ Profile ถ้าไม่ใช่หน้า Home
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
}
