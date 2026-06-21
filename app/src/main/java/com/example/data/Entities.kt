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
    val salesCount: Int,
    val brand: String = "Temu Signature"
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

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String,
    val sender: String, // "user" or "admin"
    val senderName: String,
    val messageText: String,
    val timestamp: Long
)

@Entity(tableName = "product_reviews")
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val productId: Long,
    val userEmail: String,
    val userName: String,
    val rating: Int,
    val reviewText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_configs")
data class AppConfigEntity(
    @PrimaryKey val id: String = "globals",
    val sliderImages: String, // comma-separated strings of names
    val promoText: String,
    val adText: String,
    val flashSalesEnds: Long, // timestamp
    val flashSalesDiscount: Int, // e.g. 50%
    val carouselEditableContent: String, // dynamic semicolon-separated title list
    val algorithmicPromotionEnabled: Boolean = true,
    val customBrandName: String = "Temu",
    val customBrandColorHex: String = "#FFFF5000",
    val customLauncherName: String = "Temu Shop",
    val referralBonusAmount: Int = 20,
    val storeCategories: String = "All,Fashion,Electronics,Home & Living,Beauty & Health,Toys & Games"
)

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val email: String,
    val name: String,
    val phoneNumber: String,
    val passwordHash: String,
    val walletBalance: Double = 120.00,
    val couponCount: Int = 3,
    val promoCodeUsed: String = "",
    val referralCode: String = "",
    val referredBy: String = "",
    val referralBonusEarned: Double = 0.0,
    val purchaseCount: Int = 0,
    val totalSpent: Double = 0.0,
    val suspicious: Boolean = false,
    val country: String = "USA",
    val shippingAddress: String = ""
)


