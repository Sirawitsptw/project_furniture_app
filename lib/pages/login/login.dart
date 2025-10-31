import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/gestures.dart';
import 'package:project_furnitureapp/pages/home/home.dart';
import 'package:project_furnitureapp/pages/signup/signup.dart';
import 'package:project_furnitureapp/pages/home/RiderHome.dart';

class Login extends StatefulWidget {
  const Login({super.key});

  @override
  State<Login> createState() => LoginState();
}

class LoginState extends State<Login> {
  final TextEditingController _phoneController = TextEditingController();
  final TextEditingController _otpController = TextEditingController();

  bool showOtpField = false;
  String verificationId = '';
  bool isLoading = false;

  String formatPhoneNumber(String rawPhone) {
    if (rawPhone.startsWith('0')) {
      return rawPhone.replaceFirst('0', '+66');
    } else if (!rawPhone.startsWith('+')) {
      return '+66$rawPhone';
    }
    return rawPhone;
  }

  Future<void> _sendOtp() async {
    final phone = _phoneController.text.trim();
    if (phone.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("กรุณาใส่เบอร์โทรศัพท์")),
      );
      return;
    }

    setState(() => isLoading = true);

    await FirebaseAuth.instance.verifyPhoneNumber(
      phoneNumber: formatPhoneNumber(phone),
      verificationCompleted: (PhoneAuthCredential credential) async {
        await FirebaseAuth.instance.signInWithCredential(credential);
        _showSuccess();
      },
      verificationFailed: (FirebaseAuthException e) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("เกิดข้อผิดพลาด: ${e.message}")),
        );
        setState(() => isLoading = false);
      },
      codeSent: (String verId, int? resendToken) {
        setState(() {
          verificationId = verId;
          showOtpField = true;
          isLoading = false;
        });
      },
      codeAutoRetrievalTimeout: (String verId) {
        verificationId = verId;
      },
    );
  }

  Future<void> _verifyOtp() async {
    final otp = _otpController.text.trim();
    if (otp.isEmpty || verificationId.isEmpty) return;

    setState(() => isLoading = true);

    try {
      final credential = PhoneAuthProvider.credential(
        verificationId: verificationId,
        smsCode: otp,
      );
      await FirebaseAuth.instance.signInWithCredential(credential);
      _showSuccess();
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("OTP ไม่ถูกต้อง")),
      );
    } finally {
      setState(() => isLoading = false);
    }
  }

void _showSuccess() {
  final phone = FirebaseAuth.instance.currentUser?.phoneNumber;

  if (phone == '+66111111111' || phone == '+66222222222' || phone == '+66333333333') {
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (context) => RiderHome()),
    );
  } else {
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (context) => HomePage()),
    );
  }
}


  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      resizeToAvoidBottomInset: false,
      bottomNavigationBar: _signup(context),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        toolbarHeight: 100,
        leading: GestureDetector(
          onTap: () => Navigator.pop(context),
        ),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          child: Column(
            children: [
              // Simple Beautiful Welcome Text
              Center(
                child: ShaderMask(
                  shaderCallback: (bounds) => LinearGradient(
                    colors: [
                      Color(0xff0D6EFD),
                      Color(0xff8B5CF6),
                      Color(0xffEC4899),
                    ],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ).createShader(bounds),
                  child: Text(
                    'ยินดีต้อนรับเข้าสู่แอปพลิเคชันซื้อเฟอร์นิเจอร์',
                    textAlign: TextAlign.center,
                    style: GoogleFonts.kanit(
                      textStyle: const TextStyle(
                        color: Colors.white, // จำเป็นสำหรับ ShaderMask
                        fontWeight: FontWeight.bold,
                        fontSize: 32,
                        height: 1.3,
                        shadows: [
                          Shadow(
                            offset: Offset(3, 3),
                            blurRadius: 6,
                            color: Colors.black26,
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 40),
              _phoneNumberField(),
              if (showOtpField) ...[
                const SizedBox(height: 20),
                _otpField(),
              ],
              const SizedBox(height: 20),
              isLoading
                  ? const CircularProgressIndicator()
                  : ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xff0D6EFD),
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                        minimumSize: const Size(double.infinity, 60),
                        elevation: 0,
                      ),
                      onPressed: showOtpField ? _verifyOtp : _sendOtp,
                      child: Text(showOtpField ? "ยืนยัน OTP" : "ส่งรหัส OTP"),
                    ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _phoneNumberField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Phone Number',
          style: GoogleFonts.raleway(
            textStyle: const TextStyle(
              color: Colors.black,
              fontWeight: FontWeight.normal,
              fontSize: 16,
            ),
          ),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _phoneController,
          keyboardType: TextInputType.phone,
          decoration: InputDecoration(
            filled: true,
            fillColor: const Color(0xffF7F7F9),
            border: OutlineInputBorder(
              borderSide: BorderSide.none,
              borderRadius: BorderRadius.circular(14),
            ),
          ),
        ),
      ],
    );
  }

  Widget _otpField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'OTP',
          style: GoogleFonts.raleway(
            textStyle: const TextStyle(
              color: Colors.black,
              fontWeight: FontWeight.normal,
              fontSize: 16,
            ),
          ),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _otpController,
          keyboardType: TextInputType.number,
          decoration: InputDecoration(
            hintText: 'กรอกรหัส OTP',
            filled: true,
            fillColor: const Color(0xffF7F7F9),
            border: OutlineInputBorder(
              borderSide: BorderSide.none,
              borderRadius: BorderRadius.circular(14),
            ),
          ),
        ),
      ],
    );
  }

  Widget _signup(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: RichText(
        textAlign: TextAlign.center,
        text: TextSpan(children: [
          const TextSpan(
            text: "New User? ",
            style: TextStyle(
              color: Color(0xff6A6A6A),
              fontWeight: FontWeight.normal,
              fontSize: 16,
            ),
          ),
          TextSpan(
            text: "Create Account",
            style: const TextStyle(
              color: Color(0xff1A1D1E),
              fontWeight: FontWeight.normal,
              fontSize: 16,
            ),
            recognizer: TapGestureRecognizer()
              ..onTap = () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => Signup()),
                );
              },
          ),
        ]),
      ),
    );
  }
}
