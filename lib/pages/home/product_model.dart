// ignore_for_file: public_member_api_docs, sort_constructors_first
import 'dart:convert';

class productModel {
  final String name;
  final String imageUrl;
  final int price;
  final String model;
  final String desc;
  final int amount;
  final double width;
  final double height;
  final double depth;
  final double longest;

  productModel({
    required this.name,
    required this.imageUrl,
    required this.price,
    required this.model,
    required this.desc,
    required this.amount,
    required this.width,
    required this.height,
    required this.depth,
    required this.longest,
  });

  // copyWith, toMap, fromJson, etc. ไม่ต้องแก้ไข เพราะมันเรียกใช้ fromMap และ toMap ที่เราจะแก้
  // ดังนั้นผมจะแสดงเฉพาะส่วนที่สำคัญที่สุดคือ fromMap

  factory productModel.fromMap(Map<String, dynamic> map) {
    return productModel(
      // --- ส่วนของ String และ int ที่ generate มาถูกต้องแล้ว ---
      name: map['name'] as String? ?? 'No Name',
      imageUrl: map['imageUrl'] as String? ?? '',
      price: map['price'] as int? ?? 0,
      model: map['model'] as String? ?? '',
      desc: map['desc'] as String? ?? '',
      amount: map['amount'] as int? ?? 0,
      width: (map['width'] as num? ?? 0.0).toDouble(),
      height: (map['height'] as num? ?? 0.0).toDouble(),
      depth: (map['depth'] as num? ?? 0.0).toDouble(),
      longest: (map['longest'] as num? ?? 1.5).toDouble(),
    );
  }

  productModel copyWith({
    String? name,
    String? imageUrl,
    int? price,
    String? model,
    String? desc,
    int? amount,
    double? width,
    double? height,
    double? depth,
    double? longest,
  }) {
    return productModel(
      name: name ?? this.name,
      imageUrl: imageUrl ?? this.imageUrl,
      price: price ?? this.price,
      model: model ?? this.model,
      desc: desc ?? this.desc,
      amount: amount ?? this.amount,
      width: width ?? this.width,
      height: height ?? this.height,
      depth: depth ?? this.depth,
      longest: longest ?? this.longest,
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
      'width_m': width,
      'height_m': height,
      'depth_m': depth,
      'longest_dimension_m': longest,
    };
  }

  String toJson() => json.encode(toMap());

  factory productModel.fromJson(String source) =>
      productModel.fromMap(json.decode(source) as Map<String, dynamic>);

  @override
  String toString() {
    return 'productModel(name: $name, imageUrl: $imageUrl, price: $price, model: $model, desc: $desc, amount: $amount, width: $width, height: $height, depth: $depth, longest: $longest)';
  }

  @override
  bool operator ==(covariant productModel other) {
    if (identical(this, other)) return true;

    return other.name == name &&
        other.imageUrl == imageUrl &&
        other.price == price &&
        other.model == model &&
        other.desc == desc &&
        other.amount == amount &&
        other.width == width &&
        other.height == height &&
        other.depth == depth &&
        other.longest == longest;
  }

  @override
  int get hashCode {
    return name.hashCode ^
        imageUrl.hashCode ^
        price.hashCode ^
        model.hashCode ^
        desc.hashCode ^
        amount.hashCode ^
        width.hashCode ^
        height.hashCode ^
        depth.hashCode ^
        longest.hashCode;
  }
}
