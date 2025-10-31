import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:fluttertoast/fluttertoast.dart';
import '../pages/home/home.dart';
import '../pages/login/login.dart';
import '../pages/signup/signup.dart';

class AuthService {
  final FirebaseAuth _auth = FirebaseAuth.instance;
  static String? _verificationId;

  Future<void> sendOTP({
    required String phoneNumber,
    required BuildContext context,
    required Function(String verificationId) onCodeSent,
  }) async {
    try {
      await _auth.verifyPhoneNumber(
        phoneNumber: phoneNumber,
        timeout: const Duration(seconds: 60),
        verificationCompleted: (PhoneAuthCredential credential) async {
          await _auth.signInWithCredential(credential);
          _goToHome(context);
        },
        verificationFailed: (FirebaseAuthException e) {
          Fluttertoast.showToast(msg: "เกิดข้อผิดพลาด: ${e.message}");
        },
        codeSent: (String verificationId, int? resendToken) {
          _verificationId = verificationId;
          onCodeSent(verificationId);
        },
        codeAutoRetrievalTimeout: (String verificationId) {
          _verificationId = verificationId;
        },
      );
    } catch (e) {
      Fluttertoast.showToast(msg: "เกิดข้อผิดพลาดในการส่ง OTP");
    }
  }

  Future<void> verifyOTP({
    required String verificationId,
    required String smsCode,
    required BuildContext context,
    String? email,       
    String? firstName,   
    String? lastName,    
    String? phone,      
  }) async {
    try {
      final credential = PhoneAuthProvider.credential(
        verificationId: verificationId,
        smsCode: smsCode,
      );
      final result = await _auth.signInWithCredential(credential);
      final user = result.user;

      if (user != null) {
        await saveUserProfile(
          user,
          email: email,
          firstName: firstName,
          lastName: lastName,
          phone: phone,
        ); 
        _goToHome(context);
      }
    } catch (e) {
      Fluttertoast.showToast(msg: 'ยืนยันรหัสไม่สำเร็จ');
    }
  }


  Future<void> signout({required BuildContext context}) async {
    await _auth.signOut();
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (context) => Login()),
    );
  }

  void _goToHome(BuildContext context) {
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (context) => HomePage()),
    );
  }

  Future<void> saveUserProfile(
    User user, {
    String? email,
    String? firstName,
    String? lastName,
    String? phone,
  }) async {
    final ref = FirebaseFirestore.instance.collection('user').doc(user.uid);

    final snapshot = await ref.get();
    final data = <String, dynamic>{
      'email': email ?? user.email,
      'firstName': firstName,
      'lastName': lastName,
      'phone': phone ?? user.phoneNumber,
    };

    if (!snapshot.exists) {
      await ref.set({
        ...data,
        'createdAt': FieldValue.serverTimestamp(), // เวลาที่สมัคร
      });
    } else {
      await ref.update(data);
    }
  }
}
