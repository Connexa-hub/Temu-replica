package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ShopRepository(private val db: AppDatabase) {
    private val productDao = db.productDao()
    private val cartDao = db.cartDao()
    private val orderDao = db.orderDao()
    private val chatDao = db.chatDao()
    private val seedMutex = Mutex()

    fun getChatHistory(userId: String): Flow<List<ChatMessageEntity>> = chatDao.getChatHistory(userId)
    val distinctChatUsers: Flow<List<String>> = chatDao.getDistinctChatUsers()

    suspend fun insertChatMessage(message: ChatMessageEntity) = chatDao.insertMessage(message)
    suspend fun clearAllChatMessages() = chatDao.clearAllMessages()

    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()
    val cartItems: Flow<List<CartItemEntity>> = cartDao.getCartItems()
    val allOrders: Flow<List<OrderEntity>> = orderDao.getAllOrders()
    val allOrderItems: Flow<List<OrderItemEntity>> = orderDao.getAllOrderItems()

    fun getProductById(id: Long): Flow<ProductEntity?> = productDao.getProductById(id)

    suspend fun insertProduct(product: ProductEntity): Long = productDao.insertProduct(product)

    suspend fun updateProduct(product: ProductEntity) = productDao.updateProduct(product)

    suspend fun deleteProduct(product: ProductEntity) = productDao.deleteProduct(product)

    suspend fun addToCart(productId: Long, quantity: Int) {
        val currentCart = cartItems.first()
        val existing = currentCart.find { it.productId == productId }
        if (existing != null) {
            cartDao.insertCartItem(CartItemEntity(productId, existing.quantity + quantity))
        } else {
            cartDao.insertCartItem(CartItemEntity(productId, quantity))
        }
    }

    suspend fun updateCartQuantity(productId: Long, quantity: Int) {
        if (quantity <= 0) {
            cartDao.deleteCartItem(productId)
        } else {
            cartDao.insertCartItem(CartItemEntity(productId, quantity))
        }
    }

    suspend fun removeFromCart(productId: Long) = cartDao.deleteCartItem(productId)

    suspend fun clearCart() = cartDao.clearCart()

    suspend fun checkout(itemsToCheckout: List<Pair<ProductEntity, Int>>): Boolean {
        if (itemsToCheckout.isEmpty()) return false

        var totalAmount = 0.0
        var totalItems = 0
        val orderItems = mutableListOf<OrderItemEntity>()

        // Check stock levels first
        for ((product, qty) in itemsToCheckout) {
            if (product.stockQuantity < qty) {
                return false // Insufficient stock
            }
        }

        // Create the order object
        val orderTimestamp = System.currentTimeMillis()
        val tempOrderId = 0L // Determined on insert

        for ((product, qty) in itemsToCheckout) {
            val itemPrice = product.discountedPrice
            totalAmount += itemPrice * qty
            totalItems += qty

            // Deduct stock and increment sales count
            val updatedProduct = product.copy(
                stockQuantity = product.stockQuantity - qty,
                salesCount = product.salesCount + qty
            )
            productDao.updateProduct(updatedProduct)
        }

        val orderId = orderDao.insertOrder(
            OrderEntity(
                timestamp = orderTimestamp,
                totalAmount = totalAmount,
                totalItems = totalItems
            )
        )

        for ((product, qty) in itemsToCheckout) {
            orderItems.add(
                OrderItemEntity(
                    orderId = orderId,
                    productId = product.id,
                    productName = product.name,
                    price = product.discountedPrice,
                    quantity = qty,
                    category = product.category
                )
            )
        }

        orderDao.insertOrderItems(orderItems)
        cartDao.clearCart()
        return true
    }

    suspend fun seedDatabase() = seedMutex.withLock {
        withContext(Dispatchers.IO) {
            // Clear all
            db.clearAllTables()

        val mockProducts = listOf(
            ProductEntity(
                id = 1,
                name = "Trendy Oversized Winter Hoodie",
                description = "Ultra-comfortable fleece lining, dropped shoulders, and kangaroo pockets. Perfect for casual wear. Heavyweight-style fabric available in multiple pastel colors.",
                category = "Fashion",
                price = 45.99,
                discountPercent = 45,
                imageUrl = "fashion_hoodie",
                stockQuantity = 25,
                salesCount = 132
            ),
            ProductEntity(
                id = 2,
                name = "Wireless active bass Noise Cancelling Earbuds",
                description = "High-fidelity audio with active noise cancellation up to 40dB. Smart touch controls, IPX7 sweat-resistant, and 30-hour battery life package.",
                category = "Electronics",
                price = 39.99,
                discountPercent = 60,
                imageUrl = "electronics_buds",
                stockQuantity = 15,
                salesCount = 290
            ),
            ProductEntity(
                id = 3,
                name = "Automatic Self-Stirring Heat-Proof Mug",
                description = "Requires no spoons! Stir coffee, cocoa, or protein shakes with a press of a button. Double-walled stainless steel keeps drinks warm.",
                category = "Home & Living",
                price = 19.99,
                discountPercent = 30,
                imageUrl = "home_mug",
                stockQuantity = 40,
                salesCount = 85
            ),
            ProductEntity(
                id = 4,
                name = "Ultra-Glow Velvet Matte Lipstick Set",
                description = "A gorgeous selection of 6 highly pigmented, long-lasting nude and crimson shades. Vegan formula, infused with nourishing Vitamin E and Jojoba oil.",
                category = "Beauty & Health",
                price = 24.99,
                discountPercent = 50,
                imageUrl = "beauty_lipgloss",
                stockQuantity = 8,
                salesCount = 175
            ),
            ProductEntity(
                id = 5,
                name = "Magnetic STEM Geometry Block Set (100pcs)",
                description = "Inspire your child's three-dimensional imagination. High-quality non-toxic ABS plastic blocks with ultra-strong rare-earth magnets.",
                category = "Toys & Games",
                price = 34.50,
                discountPercent = 40,
                imageUrl = "toys_magnet",
                stockQuantity = 30,
                salesCount = 60
            ),
            ProductEntity(
                id = 6,
                name = "USB-C Portable Electric Lint Shaver",
                description = "Breathe new life into old sweaters and coats. Double-speed shaving blades with adjustable guard to prevent snags. Easy-clean fuzz container.",
                category = "Home & Living",
                price = 15.99,
                discountPercent = 25,
                imageUrl = "home_shaver",
                stockQuantity = 75,
                salesCount = 243
            ),
            ProductEntity(
                id = 7,
                name = "Dynamic RGB Ambient Smart Lightbar Pair",
                description = "Syncs with your gaming PC, smart TV, or ambient music beats. Multi-color combinations and app controller customization.",
                category = "Electronics",
                price = 49.99,
                discountPercent = 55,
                imageUrl = "electronics_light",
                stockQuantity = 3,
                salesCount = 412
            ),
            ProductEntity(
                id = 8,
                name = "Mini USB Rechargeable Compact Food Chopper",
                description = "Chops garlic, onions, peppers, or herbs in under 5 seconds. Durable food-grade stainless-steel blades. Portable and easy to wash.",
                category = "Home & Living",
                price = 12.49,
                discountPercent = 15,
                imageUrl = "home_chopper",
                stockQuantity = 120,
                salesCount = 189
            ),
            ProductEntity(
                id = 9,
                name = "Organic Hydrating Retinol Cream + Serum",
                description = "Reduces appearance of wrinkles and brightens dark aging spots. Formulated with organic Aloe, Hyaluronic acid, Shea Butter, and active Retinol.",
                category = "Beauty & Health",
                price = 29.99,
                discountPercent = 35,
                imageUrl = "beauty_cream",
                stockQuantity = 12,
                salesCount = 98
            ),
            ProductEntity(
                id = 10,
                name = "Racer High-Speed Drift RC Car 4WD",
                description = "4-wheel-drive remote control speedster. Realistic design with LED tail headlights, high grip anti-wear rubber tires, and extra rechargeable lithium battery.",
                category = "Toys & Games",
                price = 27.00,
                discountPercent = 20,
                imageUrl = "toys_car",
                stockQuantity = 18,
                salesCount = 110
            )
        )

        productDao.insertProducts(mockProducts)

        // Seed some gorgeous mock orders from the past 5 days to populate the dashboard analytics instantly!
        val dayMillis = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()

        // We will seed 15 orders scattered across past days
        val mockOrders = listOf(
            OrderRecord(timestamp = now - 4 * dayMillis, totalAmount = 84.48, items = listOf(
                OrderItemRecord(productId = 2, name = "Wireless active bass Noise Cancelling Earbuds", price = 15.99, qty = 2, cat = "Electronics"),
                OrderItemRecord(productId = 1, name = "Trendy Oversized Winter Hoodie", price = 25.29, qty = 2, cat = "Fashion")
            )),
            OrderRecord(timestamp = now - 3 * dayMillis, totalAmount = 148.97, items = listOf(
                OrderItemRecord(productId = 7, name = "Dynamic RGB Ambient Smart Lightbar Pair", price = 22.49, qty = 3, cat = "Electronics"),
                OrderItemRecord(productId = 8, name = "Mini USB Rechargeable Compact Food Chopper", price = 10.61, qty = 5, cat = "Home & Living"),
                OrderItemRecord(productId = 4, name = "Ultra-Glow Velvet Matte Lipstick Set", price = 12.49, qty = 2, cat = "Beauty & Health")
            )),
            OrderRecord(timestamp = now - 3 * dayMillis, totalAmount = 54.34, items = listOf(
                OrderItemRecord(productId = 5, name = "Magnetic STEM Geometry Block Set (100pcs)", price = 20.70, qty = 1, cat = "Toys & Games"),
                OrderItemRecord(productId = 3, name = "Automatic Self-Stirring Heat-Proof Mug", price = 13.99, qty = 1, cat = "Home & Living"),
                OrderItemRecord(productId = 6, name = "USB-C Portable Electric Lint Shaver", price = 11.99, qty = 1, cat = "Home & Living")
            )),
            OrderRecord(timestamp = now - 2 * dayMillis, totalAmount = 192.50, items = listOf(
                OrderItemRecord(productId = 2, name = "Wireless active bass Noise Cancelling Earbuds", price = 15.99, qty = 10, cat = "Electronics"),
                OrderItemRecord(productId = 9, name = "Organic Hydrating Retinol Cream + Serum", price = 19.49, qty = 1, cat = "Beauty & Health")
            )),
            OrderRecord(timestamp = now - dayMillis, totalAmount = 69.58, items = listOf(
                OrderItemRecord(productId = 10, name = "Racer High-Speed Drift RC Car 4WD", price = 21.60, qty = 2, cat = "Toys & Games"),
                OrderItemRecord(productId = 1, name = "Trendy Oversized Winter Hoodie", price = 25.29, qty = 1, cat = "Fashion"),
                OrderItemRecord(productId = 8, name = "Mini USB Rechargeable Compact Food Chopper", price = 10.61, qty = 1, cat = "Home & Living")
            )),
            OrderRecord(timestamp = now - 4 * 60 * 60 * 1000L, totalAmount = 45.28, items = listOf(
                OrderItemRecord(productId = 4, name = "Ultra-Glow Velvet Matte Lipstick Set", price = 12.49, qty = 1, cat = "Beauty & Health"),
                OrderItemRecord(productId = 3, name = "Automatic Self-Stirring Heat-Proof Mug", price = 13.99, qty = 1, cat = "Home & Living"),
                OrderItemRecord(productId = 9, name = "Organic Hydrating Retinol Cream + Serum", price = 19.49, qty = 1, cat = "Beauty & Health")
            ))
        )

        for (or in mockOrders) {
            val ordId = orderDao.insertOrder(
                OrderEntity(
                    timestamp = or.timestamp,
                    totalAmount = or.totalAmount,
                    totalItems = or.items.sumOf { it.qty }
                )
            )
            val itemsEntities = or.items.map {
                OrderItemEntity(
                    orderId = ordId,
                    productId = it.productId,
                    productName = it.name,
                    price = it.price,
                    quantity = it.qty,
                    category = it.cat
                )
            }
            orderDao.insertOrderItems(itemsEntities)
        }
        }
    }

    private data class OrderRecord(val timestamp: Long, val totalAmount: Double, val items: List<OrderItemRecord>)
    private data class OrderItemRecord(val productId: Long, val name: String, val price: Double, val qty: Int, val cat: String)
}
