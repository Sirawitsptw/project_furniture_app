import 'package:flutter/material.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:project_furnitureapp/pages/login/login.dart';
import 'package:project_furnitureapp/pages/product/orderlist.dart';
import 'user.dart';

class ProfilePage extends StatelessWidget {
  const ProfilePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                children: [
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.grey,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14),
                      ),
                      minimumSize: Size(double.infinity, 50),
                    ),
                    onPressed: () {
                      Navigator.push(context,
                          MaterialPageRoute(builder: (context) => UserPage()));
                    },
                    child: const Text(
                      'ข้อมูลผู้ใช้',
                      style: TextStyle(color: Colors.white),
                    ),
                  ),
                  SizedBox(height: 16),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.grey,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14),
                      ),
                      minimumSize: Size(double.infinity, 50),
                    ),
                    onPressed: () {
                      Navigator.push(context,
                          MaterialPageRoute(builder: (context) => OrderList()));
                    },
                    child: const Text(
                      'รายการคำสั่งซื้อ',
                      style: TextStyle(color: Colors.white),
                    ),
                  ),
                  SizedBox(height: 16),
                ],
              ),
              _logout(context),
            ],
          ),
        ),
      ),
    );
  }

  Widget _logout(BuildContext context) {
    return ElevatedButton(
      style: ElevatedButton.styleFrom(
        backgroundColor: Colors.red,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
        ),
        minimumSize: const Size(double.infinity, 50),
        elevation: 0,
      ),
      onPressed: () async {
        await FirebaseAuth.instance.signOut();
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(
            builder: (context) => Login(),
          ),
        );
      },
      child: const Text(
        "ออกจากระบบ",
        style: TextStyle(color: Colors.white),
      ),
    );
  }
}
