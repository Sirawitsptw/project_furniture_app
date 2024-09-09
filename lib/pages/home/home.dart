import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:project_furnitureapp/pages/login/login.dart';
import 'package:model_viewer_plus/model_viewer_plus.dart'; // ใช้สำหรับแสดงโมเดล 3D

class Home extends StatefulWidget {
  const Home({super.key});

  @override
  _HomeState createState() => _HomeState();
}

class _HomeState extends State<Home> {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  final FirebaseStorage _storage = FirebaseStorage.instance;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                'Hello👋',
                style: GoogleFonts.raleway(
                    textStyle: const TextStyle(
                        color: Colors.black,
                        fontWeight: FontWeight.bold,
                        fontSize: 20)),
              ),
              const SizedBox(
                height: 10,
              ),
              Text(
                FirebaseAuth.instance.currentUser!.email!.toString(),
                style: GoogleFonts.raleway(
                    textStyle: const TextStyle(
                        color: Colors.black,
                        fontWeight: FontWeight.bold,
                        fontSize: 20)),
              ),
              const SizedBox(
                height: 30,
              ),
              Expanded(
                child: StreamBuilder(
                  stream: _firestore.collection('posts').snapshots(),
                  builder: (context, snapshot) {
                    if (snapshot.connectionState == ConnectionState.waiting) {
                      return const Center(child: CircularProgressIndicator());
                    }
                    if (!snapshot.hasData || snapshot.data!.docs.isEmpty) {
                      return const Center(child: Text('No posts found'));
                    }

                    final posts = snapshot.data!.docs;
                    return ListView.builder(
                      itemCount: posts.length,
                      itemBuilder: (context, index) {
                        final post = posts[index];
                        final imageUrl = post['imageUrl'];
                        final text = post['text'];
                        final modelUrl = post[
                            'modelUrl']; // ดึง URL ของโมเดล 3D จาก Firestore

                        return FutureBuilder<String>(
                          future: _getImageUrl(imageUrl),
                          builder: (context, snapshot) {
                            if (snapshot.connectionState ==
                                ConnectionState.waiting) {
                              return ListTile(
                                title: Text(text),
                                subtitle: const Center(
                                    child: CircularProgressIndicator()),
                              );
                            }

                            if (snapshot.hasError) {
                              return ListTile(
                                title: Text(text),
                                subtitle: const Text('Error loading image'),
                              );
                            }

                            return ListTile(
                              title: Text(text),
                              subtitle: snapshot.hasData
                                  ? GestureDetector(
                                      onTap: () {
                                        Navigator.push(
                                          context,
                                          MaterialPageRoute(
                                            builder: (context) =>
                                                ModelViewerScreen(
                                              modelUrl: modelUrl,
                                            ),
                                          ),
                                        );
                                      },
                                      child: Image.network(snapshot.data!),
                                    )
                                  : const Text('Image not available'),
                            );
                          },
                        );
                      },
                    );
                  },
                ),
              ),
              _logout(context)
            ],
          ),
        ),
      ),
    );
  }

  Future<String> _getImageUrl(String imagePath) async {
    final ref = _storage.ref().child(imagePath);
    return await ref.getDownloadURL();
  }

  Widget _logout(BuildContext context) {
    return ElevatedButton(
      style: ElevatedButton.styleFrom(
        backgroundColor: const Color(0xff0D6EFD),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
        ),
        minimumSize: const Size(double.infinity, 60),
        elevation: 0,
      ),
      onPressed: () async {
        await FirebaseAuth.instance.signOut();
        Navigator.of(context).pushReplacement(MaterialPageRoute(
          builder: (context) => Login(),
        ));
      },
      child: const Text("Sign Out"),
    );
  }
}

class ModelViewerScreen extends StatelessWidget {
  final String modelUrl;

  const ModelViewerScreen({required this.modelUrl, Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('3D Model Viewer'),
      ),
      body: Center(
        child: ModelViewer(
          src: modelUrl, // ใส่ URL ของโมเดล 3D ที่ได้รับมา
          ar: true,
          autoRotate: true,
          cameraControls: true,
          backgroundColor: Colors.white,
        ),
      ),
    );
  }
}
