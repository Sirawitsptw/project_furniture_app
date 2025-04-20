// ignore_for_file: public_member_api_docs, sort_constructors_first
import 'dart:convert';

class productModel {
  final String name;
  final String imageUrl;
  final int price;
  final String model;
  final String desc;
  final int amount;
  productModel({
    required this.name,
    required this.imageUrl,
    required this.price,
    required this.model,
    required this.desc,
    required this.amount,
  });

  productModel copyWith({
    String? name,
    String? imageUrl,
    int? price,
    String? model,
    String? desc,
    int? amount,
  }) {
    return productModel(
      name: name ?? this.name,
      imageUrl: imageUrl ?? this.imageUrl,
      price: price ?? this.price,
      model: model ?? this.model,
      desc: desc ?? this.model,
      amount: amount ?? this.amount,
    );
  }

  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'name': name,
      'imageUrl': imageUrl,
      'price': price,
      'model': model,
      'desc': desc,
      'amount': amount,
    };
  }

  factory productModel.fromMap(Map<String, dynamic> map) {
    return productModel(
      name: map['name'] as String,
      imageUrl: map['imageUrl'] as String,
      price: map['price'] as int,
      model: map['model'] as String,
      desc: map['desc'] as String,
      amount: map['amount'] as int,
    );
  }

  String toJson() => json.encode(toMap());

  factory productModel.fromJson(String source) =>
      productModel.fromMap(json.decode(source) as Map<String, dynamic>);

  @override
  String toString() {
    return 'productModel(name: $name, imageUrl: $imageUrl, price: $price, model: $model, desc: $desc, amount: $amount)';
  }

  @override
  bool operator ==(covariant productModel other) {
    if (identical(this, other)) return true;

    return other.name == name &&
        other.imageUrl == imageUrl &&
        other.price == price &&
        other.model == model &&
        other.desc == desc &&
        other.amount == amount;
  }

  @override
  int get hashCode {
    return name.hashCode ^
        imageUrl.hashCode ^
        price.hashCode ^
        model.hashCode ^
        desc.hashCode ^
        amount.hashCode;
  }
}
