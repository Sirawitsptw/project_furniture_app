import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_storage/firebase_storage.dart';
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

  List<Widget> widgets = [];
  List<productModel> productmodelList = [];

  @override
  void initState() {
    super.initState();
    readData();
  }

  Future<Null> readData() async {
    await Firebase.initializeApp().then((value) async {
      print('Initialize Success');
      await FirebaseFirestore.instance.collection('product').snapshots().listen(
        (event) {
          print('Snapshots = ${event.docs}');
          int index = 0;
          for (var snapshot in event.docs) {
            Map<String, dynamic> map = snapshot.data();
            print('map = $map');
            productModel model = productModel.fromMap(map);
            productmodelList.add(model);
            print('name = ${model.name}');
            setState(() {
              widgets.add(createWidget(model, index));
            });
            index++;
          }
        },
      );
    });
  }

  Widget createWidget(productModel model, int index) => GestureDetector(
        onTap: () {
          print('You Clicked from index = $index');
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => ProductView(
                productmodel: productmodelList[index],
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(appBarTitles[currentIndex]),
        backgroundColor: Colors.deepPurple,
      ),
      body: Center(
        child: currentIndex == 0
            ? widgets.isEmpty
                ? const CircularProgressIndicator()
                : GridView.extent(
                    crossAxisSpacing: 10,
                    mainAxisSpacing: 10,
                    maxCrossAxisExtent: 200,
                    children: widgets,
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
}
