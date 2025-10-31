import 'package:flutter/material.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

class UserPage extends StatelessWidget {
  const UserPage({super.key});

  @override

  static String thaiPhone(String raw) {
    final s = raw.replaceAll(' ', '');
    if (s.isEmpty) return '-';
    if (s.startsWith('+66')) return '0${s.substring(3)}'; 
    if (s.startsWith('66'))  return '0${s.substring(2)}'; 
    return s; // กรณีเก็บเป็น 0 อยู่แล้ว ก็คืนเดิม
  }

  Widget build(BuildContext context) {
    final uid = FirebaseAuth.instance.currentUser?.uid;

    if (uid == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('โปรไฟล์')),
        body: const Center(child: Text('ยังไม่ได้เข้าสู่ระบบ')),
      );
    }

    final docRef = FirebaseFirestore.instance.collection('user').doc(uid);

    return Scaffold(
      appBar: AppBar(title: const Text('ข้อมูลผู้ใช้'), backgroundColor: Colors.deepPurple, foregroundColor: Colors.white),
      body: FutureBuilder<DocumentSnapshot<Map<String, dynamic>>>(
        future: docRef.get(),
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (!snap.hasData || !snap.data!.exists) {
            return const Center(child: Text('ไม่พบข้อมูลผู้ใช้'));
          }

          final data = snap.data!.data()!;
          final firstName = (data['firstName'] as String?)?.trim() ?? '';
          final lastName  = (data['lastName']  as String?)?.trim() ?? '';
          final email     = (data['email']     as String?)?.trim() ?? '';
          final phone     = (data['phone']     as String?)?.trim() ?? '';

          return Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                const SizedBox(height: 24),
                const CircleAvatar(
                  radius: 48,
                  child: Icon(Icons.person, size: 48),
                ),
                const SizedBox(height: 24),

                Row(
                  children: [
                    Expanded(child: _box(label: 'ชื่อ', value: firstName)),
                    const SizedBox(width: 12),
                    Expanded(child: _box(label: 'นามสกุล', value: lastName)),
                  ],
                ),
                const SizedBox(height: 12),
                _box(label: 'อีเมล', value: email),
                const SizedBox(height: 12),
                _box(label: 'เบอร์โทรศัพท์', value: thaiPhone(phone)),
              ],
            ),
          );
        },
      ),
    );
  }

  static Widget _box({required String label, required String value}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label),
        const SizedBox(height: 6),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
          decoration: BoxDecoration(
            border: Border.all(color: Colors.black87, width: 1.8),
            borderRadius: BorderRadius.circular(6),
          ),
          child: Text(value.isEmpty ? '-' : value),
        ),
      ],
    );
  }
}
