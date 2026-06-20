package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// --- Network Data Classes ---
data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val email: String,
    val name: String,
    val role: String,
    val token: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val role: String = "user",
    val adminToken: String? = null
)

data class NetworkProduct(
    val id: Long,
    val name: String,
    val description: String,
    val category: String,
    val price: Double,
    val discountPercent: Int,
    val imageUrl: String,
    val stockQuantity: Int,
    val salesCount: Int
) {
    fun toEntity(): ProductEntity = ProductEntity(
        id = id,
        name = name,
        description = description,
        category = category,
        price = price,
        discountPercent = discountPercent,
        imageUrl = imageUrl,
        stockQuantity = stockQuantity,
        salesCount = salesCount
    )
}

data class OrderItemRequest(
    val productId: Long,
    val quantity: Int
)

data class OrderRequest(
    val totalAmount: Double,
    val items: List<OrderItemRequest>,
    val userEmail: String? = null,
    val payWithWallet: Boolean = false
)

data class NetworkOrder(
    val orderId: Long,
    val timestamp: Long,
    val totalAmount: Double
)

data class NetworkOrderItem(
    val itemId: Long,
    val orderId: Long,
    val productId: Long,
    val productName: String,
    val category: String,
    val price: Double,
    val quantity: Int
)

data class NetworkOrdersResponse(
    val orders: List<NetworkOrder>,
    val orderItems: List<NetworkOrderItem>
)

data class CheckoutResponse(
    val success: Boolean,
    val order: NetworkOrder,
    val items: List<NetworkOrderItem>,
    val walletDeducted: Boolean = false
)

data class DepositRequest(
    val email: String,
    val amount: Double,
    val cardNumber: String,
    val cardExpiry: String,
    val cardCvc: String,
    val gateway: String
)

data class DepositResponse(
    val success: Boolean,
    val message: String,
    val receiptNo: String,
    val gatewayStatus: String,
    val newWalletBalance: Double
)

data class NetworkTrackingCheckpoint(
    val time: String,
    val status: String,
    val isDone: Boolean
)

data class OrderTrackingResponse(
    val orderId: Long,
    val status: String,
    val checkpoints: List<NetworkTrackingCheckpoint>
)

data class ProfileSyncResponse(
    val email: String,
    val name: String,
    val role: String,
    val phoneNumber: String,
    val walletBalance: Double
)

data class UpdateProfileRequest(
    val email: String,
    val newPassword: String? = null,
    val phoneNumber: String? = null
)

data class UpdateProfileResponse(
    val success: Boolean,
    val email: String,
    val phoneNumber: String
)

data class GenericMsgResponse(
    val success: Boolean,
    val message: String
)

data class RegisterOtpRequest(
    val email: String,
    val password: String,
    val name: String,
    val role: String,
    val adminToken: String?
)

data class VerifyOtpRequest(
    val email: String,
    val otp: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class ResetPasswordRequest(
    val email: String,
    val token: String,
    val newPassword: String
)

data class ChatMessageRequest(
    val text: String,
    val sender: String, // "user" or "admin"
    val name: String
)

data class ChatMessageResponse(
    val msgId: Long,
    val sender: String,
    val name: String,
    val text: String,
    val timestamp: Long
) {
    fun toEntity(userId: String): ChatMessageEntity = ChatMessageEntity(
        userId = userId,
        sender = sender,
        senderName = name,
        messageText = text,
        timestamp = timestamp
    )
}

data class ChatSessionResponse(
    val userId: String,
    val messageCount: Int,
    val lastMessage: String,
    val lastSender: String,
    val timestamp: Long
)

// --- Retrofit Api Interface ---
interface ShopApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): LoginResponse

    @GET("api/products")
    suspend fun getProducts(): List<NetworkProduct>

    @POST("api/products")
    suspend fun createProduct(@Body product: ProductEntity): NetworkProduct

    @PUT("api/products/{id}")
    suspend fun updateProduct(@Path("id") id: Long, @Body product: ProductEntity): NetworkProduct

    @DELETE("api/products/{id}")
    suspend fun deleteProduct(@Path("id") id: Long): ResponseBody

    @POST("api/products/reseed")
    suspend fun reseedProducts(): ResponseBody

    @GET("api/orders")
    suspend fun getOrders(): NetworkOrdersResponse

    @POST("api/orders")
    suspend fun checkout(@Body request: OrderRequest): CheckoutResponse

    @GET("api/chat/sessions")
    suspend fun getChatSessions(): List<ChatSessionResponse>

    @GET("api/chat/{userId}")
    suspend fun getChatHistory(@Path("userId") userId: String): List<ChatMessageResponse>

    @POST("api/chat/{userId}/message")
    suspend fun sendChatMessage(@Path("userId") userId: String, @Body message: ChatMessageRequest): ChatMessageResponse

    @POST("api/payment/deposit")
    suspend fun depositFunds(@Body request: DepositRequest): DepositResponse

    @GET("api/orders/{id}/tracking")
    suspend fun getOrderTracking(@Path("id") orderId: Long): OrderTrackingResponse

    @GET("api/user/profile")
    suspend fun getRemoteUserProfile(@Query("email") email: String): ProfileSyncResponse

    @POST("api/user/profile/update")
    suspend fun updateRemoteProfile(@Body request: UpdateProfileRequest): UpdateProfileResponse

    @GET("api/appconfig")
    suspend fun getAppConfig(): AppConfigEntity

    @POST("api/appconfig")
    suspend fun updateAppConfig(@Body request: AppConfigEntity): AppConfigEntity

    @POST("api/auth/register-otp")
    suspend fun registerOtp(@Body request: RegisterOtpRequest): GenericMsgResponse

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): LoginResponse

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): GenericMsgResponse

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): GenericMsgResponse
}

// --- Dynamic Retrofit Factory ---
object RetrofitClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    fun createService(baseUrl: String): ShopApiService {
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ShopApiService::class.java)
    }
}
