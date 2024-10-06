// ignore_for_file: public_member_api_docs, sort_constructors_first
import 'dart:convert';

class productModel {
  final String name;
  final String imageUrl;
  final String price;
  productModel({
    required this.name,
    required this.imageUrl,
    required this.price,
  });

  productModel copyWith({
    String? name,
    String? imageUrl,
    String? price,
  }) {
    return productModel(
      name: name ?? this.name,
      imageUrl: imageUrl ?? this.imageUrl,
      price: price ?? this.price,
    );
  }

  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'name': name,
      'imageUrl': imageUrl,
      'price': price,
    };
  }

  factory productModel.fromMap(Map<String, dynamic> map) {
    return productModel(
      name: map['name'] as String,
      imageUrl: map['imageUrl'] as String,
      price: map['price'] as String,
    );
  }

  String toJson() => json.encode(toMap());

  factory productModel.fromJson(String source) =>
      productModel.fromMap(json.decode(source) as Map<String, dynamic>);

  @override
  String toString() =>
      'productModel(name: $name, imageUrl: $imageUrl, price: $price)';

  @override
  bool operator ==(covariant productModel other) {
    if (identical(this, other)) return true;

    return other.name == name &&
        other.imageUrl == imageUrl &&
        other.price == price;
  }

  @override
  int get hashCode => name.hashCode ^ imageUrl.hashCode ^ price.hashCode;
}
