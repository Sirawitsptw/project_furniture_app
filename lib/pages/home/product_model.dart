// ignore_for_file: public_member_api_docs, sort_constructors_first
import 'dart:convert';

class productModel {
  final String name;
  final String imageUrl;
  final String price;
  final String model;
  final String desc;
  productModel({
    required this.name,
    required this.imageUrl,
    required this.price,
    required this.model,
    required this.desc,
  });

  productModel copyWith({
    String? name,
    String? imageUrl,
    String? price,
    String? model,
    String? desc,
  }) {
    return productModel(
      name: name ?? this.name,
      imageUrl: imageUrl ?? this.imageUrl,
      price: price ?? this.price,
      model: model ?? this.model,
      desc: desc ?? this.model,
    );
  }

  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'name': name,
      'imageUrl': imageUrl,
      'price': price,
      'model': model,
      'desc': desc,
    };
  }

  factory productModel.fromMap(Map<String, dynamic> map) {
    return productModel(
      name: map['name'] as String,
      imageUrl: map['imageUrl'] as String,
      price: map['price'] as String,
      model: map['model'] as String,
      desc: map['desc'] as String,
    );
  }

  String toJson() => json.encode(toMap());

  factory productModel.fromJson(String source) =>
      productModel.fromMap(json.decode(source) as Map<String, dynamic>);

  @override
  String toString() {
    return 'productModel(name: $name, imageUrl: $imageUrl, price: $price, model: $model, desc: $desc)';
  }

  @override
  bool operator ==(covariant productModel other) {
    if (identical(this, other)) return true;

    return other.name == name &&
        other.imageUrl == imageUrl &&
        other.price == price &&
        other.model == model &&
        other.desc == desc;
  }

  @override
  int get hashCode {
    return name.hashCode ^
        imageUrl.hashCode ^
        price.hashCode ^
        model.hashCode ^
        desc.hashCode;
  }
}
