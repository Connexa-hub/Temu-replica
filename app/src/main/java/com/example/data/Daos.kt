package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY id DESC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductById(id: Long): Flow<ProductEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)
}

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items")
    fun getCartItems(): Flow<List<CartItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(cartItem: CartItemEntity)

    @Query("DELETE FROM cart_items WHERE productId = :productId")
    suspend fun deleteCartItem(productId: Long)

    @Query("DELETE FROM cart_items")
    suspend fun clearCart()
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM order_items")
    fun getAllOrderItems(): Flow<List<OrderItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(orderItems: List<OrderItemEntity>)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE userId = :userId ORDER BY timestamp ASC")
    fun getChatHistory(userId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT DISTINCT userId FROM chat_messages")
    fun getDistinctChatUsers(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM product_reviews WHERE productId = :productId ORDER BY timestamp DESC")
    fun getReviewsForProduct(productId: Long): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM product_reviews ORDER BY timestamp DESC")
    fun getAllReviews(): Flow<List<ReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: ReviewEntity)
}

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_configs WHERE id = 'globals' LIMIT 1")
    fun getAppConfig(): Flow<AppConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAppConfig(config: AppConfigEntity)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE email = :email LIMIT 1")
    fun getUserProfile(email: String): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfileEntity)
}


