package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val category: String,
    val price: Double,
    val discountPercent: Int,
    val imageUrl: String,
    val stockQuantity: Int,
    val salesCount: Int
) {
    val discountedPrice: Double
        get() = price * (1.0 - (discountPercent / 100.0))
}

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey val productId: Long,
    val quantity: Int
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val orderId: Long = 0L,
    val timestamp: Long,
    val totalAmount: Double,
    val totalItems: Int
)

@Entity(tableName = "order_items")
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val orderId: Long,
    val productId: Long,
    val productName: String,
    val price: Double,
    val quantity: Int,
    val category: String
)
