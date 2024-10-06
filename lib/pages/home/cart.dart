import 'package:flutter/material.dart';

class cartpage extends StatefulWidget {
  const cartpage({super.key});

  @override
  State<cartpage> createState() => _MyWidgetState();
}

class _MyWidgetState extends State<cartpage> {
  @override
  Widget build(BuildContext context) => Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [Text('Cart')],
      );
}
