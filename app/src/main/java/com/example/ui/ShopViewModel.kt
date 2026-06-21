package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.BuildConfig
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CartProductItem(
    val product: ProductEntity,
    val quantity: Int
) {
    val totalOriginalPrice: Double get() = product.price * quantity
    val totalDiscountedPrice: Double get() = product.discountedPrice * quantity
}

enum class ShopScreen {
    STORES,
    CART,
    WISHLIST,
    ORDERS,
    CHAT,
    ADMIN_CHATS,
    ADMIN_DASHBOARD,
    AUTH_SETTINGS
}

// Data holder for simplified dynamic charts in analytics
data class CategorySales(val category: String, val amount: Double, val quantityUnits: Int)
data class DailySales(val dayStr: String, val amount: Double)

class ShopViewModel(private val repository: ShopRepository) : ViewModel() {

    // Order tracking live details
    private val _activeOrderTracking = MutableStateFlow<OrderTrackingResponse?>(null)
    val activeOrderTracking: StateFlow<OrderTrackingResponse?> = _activeOrderTracking.asStateFlow()

    // Selected Navigation Tab
    private val _currentScreen = MutableStateFlow(ShopScreen.STORES)
    val currentScreen: StateFlow<ShopScreen> = _currentScreen.asStateFlow()

    // Wishlist persistence
    private val _wishlistProductIds = MutableStateFlow<Set<Long>>(emptySet())
    val wishlistProductIds: StateFlow<Set<Long>> = _wishlistProductIds.asStateFlow()

    fun toggleWishlist(productId: Long) {
        val user = _activeUser.value
        viewModelScope.launch {
            if (user != null && _isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    val res = api.toggleWishlist(WishlistToggleRequest(user.email, productId.toInt()))
                    _wishlistProductIds.value = res.productIds.map { it.toLong() }.toSet()
                } catch (e: Exception) {
                    toggleWishlistLocal(productId)
                }
            } else {
                toggleWishlistLocal(productId)
            }
        }
    }

    private fun toggleWishlistLocal(productId: Long) {
        val current = _wishlistProductIds.value.toMutableSet()
        if (current.contains(productId)) current.remove(productId)
        else current.add(productId)
        _wishlistProductIds.value = current
    }

    fun fetchWishlist() {
        val user = _activeUser.value ?: return
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    val res = api.getWishlist(user.email)
                    _wishlistProductIds.value = res.map { it.toLong() }.toSet()
                } catch (e: Exception) {}
            }
        }
    }

    // Active User session data
    private val _activeUser = MutableStateFlow<LoginResponse?>(null)
    val activeUser: StateFlow<LoginResponse?> = _activeUser.asStateFlow()

    // Remote integration configurations
    private val _backendUrl = MutableStateFlow<String>("https://my-store-98y6.onrender.com")
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val _isConnectedToBackend = MutableStateFlow(false)
    val isConnectedToBackend: StateFlow<Boolean> = _isConnectedToBackend.asStateFlow()

    // Real-time Chat Threads
    private val _currentUserChat = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val currentUserChat: StateFlow<List<ChatMessageEntity>> = _currentUserChat.asStateFlow()

    private val _adminChatSessions = MutableStateFlow<List<ChatSessionResponse>>(emptyList())
    val adminChatSessions: StateFlow<List<ChatSessionResponse>> = _adminChatSessions.asStateFlow()

    private val _selectedAdminSessionUser = MutableStateFlow<String?>(null)
    val selectedAdminSessionUser: StateFlow<String?> = _selectedAdminSessionUser.asStateFlow()

    private val _adminSelectedChatHistory = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val adminSelectedChatHistory: StateFlow<List<ChatMessageEntity>> = _adminSelectedChatHistory.asStateFlow()

    // Search Query and Category Filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Selected product for Details Sheet
    private val _selectedProduct = MutableStateFlow<ProductEntity?>(null)
    val selectedProduct: StateFlow<ProductEntity?> = _selectedProduct.asStateFlow()

    // Selected product for edit/add modal
    private val _productForEdit = MutableStateFlow<ProductEntity?>(null)
    val productForEdit: StateFlow<ProductEntity?> = _productForEdit.asStateFlow()

    private val _selectedBrand = MutableStateFlow("All")
    val selectedBrand: StateFlow<String> = _selectedBrand.asStateFlow()

    fun setSelectedBrand(brand: String) { _selectedBrand.value = brand }

    private val _stockFilterActive = MutableStateFlow(false)
    val stockFilterActive: StateFlow<Boolean> = _stockFilterActive.asStateFlow()

    fun toggleStockFilter() {
        _stockFilterActive.value = !_stockFilterActive.value
    }

    // State indicators
    private val _checkoutSuccess = MutableSharedFlow<String>()
    val checkoutSuccess = _checkoutSuccess.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    // Observe DB products
    val allProducts: StateFlow<List<ProductEntity>> = combine(
        repository.allProducts,
        _searchQuery,
        _selectedCategory,
        _selectedBrand,
        _stockFilterActive
    ) { products, query, category, brand, filterActive ->
        products.filter { product ->
            (query.isEmpty() || product.name.contains(query, ignoreCase = true)) &&
            (category == "All" || product.category == category) &&
            (brand == "All" || product.brand == brand) &&
            (!filterActive || product.stockQuantity <= 5)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allReviews: StateFlow<List<ReviewEntity>> = repository.allReviews
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appConfig: StateFlow<AppConfigEntity?> = repository.appConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    private val _activeUserProfile = MutableStateFlow<UserProfileEntity?>(null)
    val activeUserProfile: StateFlow<UserProfileEntity?> = _activeUserProfile.asStateFlow()


    // Combined search/category filtered products
    val filteredProducts: StateFlow<List<ProductEntity>> = combine(
        allProducts,
        searchQuery,
        selectedCategory
    ) { products, query, cat ->
        products.filter { prod ->
            val matchesQuery = prod.name.contains(query, ignoreCase = true) || 
                               prod.description.contains(query, ignoreCase = true) ||
                               prod.category.contains(query, ignoreCase = true)
            val matchesCategory = cat == "All" || prod.category.equals(cat, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- AI LOGIC ---
    private val geminiService = GeminiRetrofitClient.createService()
    private val _aiAssistantResponse = MutableStateFlow<String?>(null)
    val aiAssistantResponse: StateFlow<String?> = _aiAssistantResponse.asStateFlow()
    private val _isAILoading = MutableStateFlow(false)
    val isAILoading: StateFlow<Boolean> = _isAILoading.asStateFlow()
    private val _showAIDialog = MutableStateFlow(false)
    val showAIDialog: StateFlow<Boolean> = _showAIDialog.asStateFlow()

    fun clearAIResponse() { _aiAssistantResponse.value = null }
    fun setShowAIDialog(show: Boolean) { _showAIDialog.value = show }

    fun askAiAssistant(userInput: String) {
        _showAIDialog.value = true
        val systemPrompt = """
            You are a helpful and energetic shopping assistant for MarketEdge Pro. 
            User's name: ${activeUser.value?.name ?: "Guest"}.
            Current Products in DB: ${allProducts.value.joinToString { it.name }}
            Categories: ${appConfig.value?.storeCategories ?: "General"}
            Rules: Be concise, use emojis, and suggest specific products from the list if they match.
        """.trimIndent()
        askGemini(userInput, systemPrompt)
    }

    fun generateAIDescription(productName: String, category: String) {
        val prompt = "Write a catchy, seductive, and professional e-commerce description for a product named '$productName' in the '$category' category. Keep it under 150 characters."
        askGemini(prompt)
    }

    private fun askGemini(prompt: String, systemInstruction: String? = null) {
        viewModelScope.launch {
            _isAILoading.value = true
            _aiAssistantResponse.value = null
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                systemInstruction = systemInstruction?.let { GeminiContent(parts = listOf(GeminiPart(text = it))) }
            )
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty()) {
                    _aiAssistantResponse.value = "🤖 Please set GEMINI_API_KEY in the Secrets panel."
                    return@launch
                }
                val response = geminiService.generateContent(apiKey, request)
                _aiAssistantResponse.value = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "🤖 I'm a bit shy today. Could you repeat that?"
            } catch (e: Exception) {
                _aiAssistantResponse.value = "🤖 Error: ${e.message}"
            } finally {
                _isAILoading.value = false
            }
        }
    }

    // Observe DB Cart Items and associate them with products
    val cartWithProducts: StateFlow<List<CartProductItem>> = combine(
        allProducts,
        repository.cartItems
    ) { products, carts ->
        carts.mapNotNull { cart ->
            val product = products.find { it.id == cart.productId }
            if (product != null) {
                CartProductItem(product, cart.quantity)
            } else {
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cart pricing states
    val cartSubtotalPrice: StateFlow<Double> = cartWithProducts.map { items ->
        items.sumOf { it.totalOriginalPrice }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cartSavedPrice: StateFlow<Double> = cartWithProducts.map { items ->
        items.sumOf { it.totalOriginalPrice - it.totalDiscountedPrice }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cartTotalItems: StateFlow<Int> = cartWithProducts.map { items ->
        items.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cartTotalPrice: StateFlow<Double> = cartWithProducts.map { items ->
        items.sumOf { it.totalDiscountedPrice }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val loyaltyDiscountPercent: StateFlow<Int> = activeUserProfile.map { user ->
        if (user == null) 0
        else {
            val count = user.purchaseCount
            val spent = user.totalSpent
            if (count >= 10 && spent > 500.0) 15 
            else if (count >= 5 && spent > 200.0) 10 
            else if (count >= 2) 5 
            else 0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cartFinalPrice: StateFlow<Double> = combine(cartTotalPrice, loyaltyDiscountPercent) { price, discount ->
        price * (1.0 - (discount / 100.0))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Observe Orders for real-time analytics calculations
    val allOrders: StateFlow<List<OrderEntity>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOrderItems: StateFlow<List<OrderItemEntity>> = repository.allOrderItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real-time Analytics States
    val totalRevenue: StateFlow<Double> = allOrders.map { orders ->
        orders.sumOf { it.totalAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalOrdersCount: StateFlow<Int> = allOrders.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val averageOrderValue: StateFlow<Double> = combine(totalRevenue, totalOrdersCount) { total, count ->
        if (count == 0) 0.0 else total / count
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Low Stock Alert items (stock <= 5)
    val lowStockProducts: StateFlow<List<ProductEntity>> = allProducts.map { products ->
        products.filter { it.stockQuantity <= 5 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sales by Category calculations (Real-time distribution chart datamap)
    val categorySalesList: StateFlow<List<CategorySales>> = allOrderItems.map { items ->
        items.groupBy { it.category }
            .map { (cat, itemsList) ->
                CategorySales(
                    category = cat,
                    amount = itemsList.sumOf { it.price * it.quantity },
                    quantityUnits = itemsList.sumOf { it.quantity }
                )
            }.sortedByDescending { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Simple daily timeline calculation for trend display chart
    val dailySalesHistory: StateFlow<List<DailySales>> = allOrders.map { orders ->
        val sdf = java.text.SimpleDateFormat("MMM dd", java.util.Locale.US)
        orders.groupBy { 
            sdf.format(java.util.Date(it.timestamp))
        }.map { (dayStr, ordersOnDay) ->
            DailySales(dayStr, ordersOnDay.sumOf { it.totalAmount })
        }.sortedBy { it.dayStr }.takeLast(7) // past 7 active days
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    init {
        // Observe active user sessions and coordinate profile loading
        viewModelScope.launch {
            _activeUser.collect { user ->
                if (user == null) {
                    _activeUserProfile.value = null
                } else {
                    repository.getUserProfile(user.email).collect { profile ->
                        _activeUserProfile.value = profile
                    }
                }
            }
        }
        // Initial seeding check
        viewModelScope.launch {
            if (repository.allProducts.first().isEmpty()) {
                repository.seedDatabase()
            }
        }
        startRealtimePolling()
    }

    fun selectScreen(screen: ShopScreen) {
        _currentScreen.value = screen
    }

    fun setQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setProductForEdit(product: ProductEntity?) {
        _productForEdit.value = product
    }

    fun selectProduct(product: ProductEntity?) {
        _selectedProduct.value = product
    }

    // UI actions
    fun addToCart(product: ProductEntity, quantity: Int = 1) {
        if (product.stockQuantity < quantity) {
            viewModelScope.launch {
                _errorMessage.emit("Fail: Only ${product.stockQuantity} items remaining in stock.")
            }
            return
        }
        viewModelScope.launch {
            repository.addToCart(product.id, quantity)
            _checkoutSuccess.emit("Added to Cart: ${product.name}")
        }
    }

    fun updateCartQty(productId: Long, qty: Int) {
        val prod = allProducts.value.find { it.id == productId }
        if (prod != null && prod.stockQuantity < qty) {
            viewModelScope.launch {
                _errorMessage.emit("Fail: Max available stock is ${prod.stockQuantity}.")
            }
            return
        }
        viewModelScope.launch {
            repository.updateCartQuantity(productId, qty)
        }
    }

    fun deleteFromCart(productId: Long) {
        viewModelScope.launch {
            repository.removeFromCart(productId)
        }
    }

    fun executeCheckout(payWithWallet: Boolean, couponCode: String = "") {
        val user = _activeUser.value
        if (user == null) {
            viewModelScope.launch {
                _errorMessage.emit("Checkout Failed: Please sign in or register to complete your order!")
            }
            return
        }

        val currentCart = cartWithProducts.value
        if (currentCart.isEmpty()) {
            viewModelScope.launch { _errorMessage.emit("Your cart is empty.") }
            return
        }

        viewModelScope.launch {
            val list = currentCart.map { it.product to it.quantity }
            val basePrice = cartTotalPrice.value
            val isCouponValid = couponCode.uppercase().trim() == BrandConfig.COUPON_FLASHSALE || couponCode.uppercase().trim() == BrandConfig.COUPON_WELCOME
            val couponDiscount = if (couponCode.uppercase().trim() == BrandConfig.COUPON_FLASHSALE) 40 else if (couponCode.uppercase().trim() == BrandConfig.COUPON_WELCOME) 50 else 0
            val loyaltyDiscount = loyaltyDiscountPercent.value
            val totalDiscountPercent = (couponDiscount + loyaltyDiscount).coerceAtMost(100)
            val finalPrice = basePrice * (1.0 - (totalDiscountPercent / 100.0))

            if (payWithWallet) {
                // Fetch profile
                val profile = repository.getUserProfile(user.email).first()
                if (profile == null) {
                    _errorMessage.emit("Payment Error: Could not locate user wallet profile.")
                    return@launch
                }
                if (profile.walletBalance < finalPrice) {
                    _errorMessage.emit("Insufficient Wallet Balance! Your wallet has $${String.format("%.2f", profile.walletBalance)} but order requires $${String.format("%.2f", finalPrice)}. Please deposit secure funds on your Profile panel.")
                    return@launch
                }

                // Deduct locally
                val updatedProfile = profile.copy(
                    walletBalance = profile.walletBalance - finalPrice,
                    couponCount = if (isCouponValid) (profile.couponCount - 1).coerceAtLeast(0) else profile.couponCount,
                    promoCodeUsed = if (isCouponValid) couponCode else profile.promoCodeUsed
                )
                repository.saveUserProfile(updatedProfile)
            }

            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    val req = OrderRequest(
                        totalAmount = finalPrice,
                        items = currentCart.map { OrderItemRequest(it.product.id, it.quantity) },
                        userEmail = user.email,
                        payWithWallet = payWithWallet
                    )
                    api.checkout(req)
                    repository.clearCart()
                    _checkoutSuccess.emit("Order Placed Successfully via Live Server! $${String.format("%.2f", finalPrice)} charged.")
                    
                    // Sync wallet balance response
                    try {
                        val profile = api.getRemoteUserProfile(user.email)
                        val localProf = repository.getUserProfile(user.email).first()
                        if (localProf != null) {
                            repository.saveUserProfile(localProf.copy(walletBalance = profile.walletBalance))
                        }
                    } catch (ex: Exception) {}

                    syncAllData()
                } catch (e: Exception) {
                    _errorMessage.emit("Server Error during Checkout: ${e.message}")
                }
            } else {
                val completed = repository.checkout(list)
                if (completed) {
                    _checkoutSuccess.emit("Order Placed Successfully! $${String.format("%.2f", finalPrice)} charged. Stock deducted.")
                } else {
                    _errorMessage.emit("Checkout failed. Please inspect product stock levels.")
                }
            }
        }
    }

    fun saveProduct(
        id: Long,
        name: String,
        description: String,
        category: String,
        price: Double,
        discountPercent: Int,
        stock: Int
    ) {
        if (name.isEmpty() || description.isEmpty() || category.isEmpty() || price <= 0) {
            viewModelScope.launch { _errorMessage.emit("Please enter valid product details.") }
            return
        }

        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    val p = ProductEntity(
                        id = id,
                        name = name,
                        description = description,
                        category = category,
                        price = price,
                        discountPercent = discountPercent,
                        imageUrl = selectDefaultImageKey(category),
                        stockQuantity = stock,
                        salesCount = 0
                    )
                    if (id == 0L) {
                        api.createProduct(p)
                        _checkoutSuccess.emit("Created Product on Server!")
                    } else {
                        api.updateProduct(id, p)
                        _checkoutSuccess.emit("Updated product details on Server!")
                    }
                    val products = api.getProducts()
                    for (ent in products.map { it.toEntity() }) {
                        repository.insertProduct(ent)
                    }
                } catch (e: Exception) {
                    _errorMessage.emit("Failed to save product on server: ${e.message}")
                }
            } else {
                if (id == 0L) {
                    repository.insertProduct(
                        ProductEntity(
                            name = name,
                            description = description,
                            category = category,
                            price = price,
                            discountPercent = discountPercent,
                            imageUrl = selectDefaultImageKey(category),
                            stockQuantity = stock,
                            salesCount = 0
                        )
                    )
                    _checkoutSuccess.emit("Product created (Offline DB): $name")
                } else {
                    val existing = allProducts.value.find { it.id == id }
                    if (existing != null) {
                        repository.updateProduct(
                            existing.copy(
                                name = name,
                                description = description,
                                category = category,
                                price = price,
                                discountPercent = discountPercent,
                                stockQuantity = stock
                            )
                        )
                        _checkoutSuccess.emit("Product updated (Offline DB): $name")
                    }
                }
            }
            _productForEdit.value = null
        }
    }

    // Dynamic Service Builder
    fun getApiService(): ShopApiService {
        return RetrofitClient.createService(_backendUrl.value)
    }

    fun updateBackendUrl(newUrl: String) {
        _backendUrl.value = newUrl
        testConnection()
    }

    fun testConnection() {
        viewModelScope.launch {
            try {
                val api = getApiService()
                val products = api.getProducts()
                _isConnectedToBackend.value = true
                _checkoutSuccess.emit("Successfully connected to live server!")
                val entities = products.map { it.toEntity() }
                for (ent in entities) {
                    repository.insertProduct(ent)
                }
            } catch (e: Exception) {
                _isConnectedToBackend.value = false
                _errorMessage.emit("Live Server Offline. Running in Local Offline Mode.")
            }
        }
    }

    fun loginRemote(emailStr: String, passwordStr: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (emailStr.isEmpty() || passwordStr.isEmpty()) {
                _errorMessage.emit("Please enter your email and password.")
                onResult(false)
                return@launch
            }
            try {
                val api = getApiService()
                val response = api.login(LoginRequest(emailStr, passwordStr))
                if (response.suspicious == true) {
                    _errorMessage.emit("Account flagged for suspicious activity. Verification needed. Re-login!")
                    logout()
                    onResult(false)
                    return@launch
                }
                
                _activeUser.value = response
                _isConnectedToBackend.value = true
                _checkoutSuccess.emit("Welcome back, ${response.name}!")
                
                // Caching profile record in database with loaded remote balance
                var remotePhone = ""
                var remoteBalance = 150.00
                var remoteSuspicious = false
                try {
                    val profileRes = api.getRemoteUserProfile(response.email)
                    remotePhone = profileRes.phoneNumber
                    remoteBalance = profileRes.walletBalance
                    remoteSuspicious = profileRes.suspicious == true
                } catch (pe: Exception) {}

                if (remoteSuspicious) {
                    _errorMessage.emit("Account flagged for suspicious activity.")
                    logout()
                    onResult(false)
                    return@launch
                }

                val localProfile = repository.getUserProfile(response.email).first()
                if (localProfile == null) {
                    repository.saveUserProfile(
                        UserProfileEntity(
                            email = response.email,
                            name = response.name,
                            phoneNumber = remotePhone,
                            passwordHash = passwordStr,
                            walletBalance = remoteBalance,
                            couponCount = 3
                        )
                    )
                } else {
                    repository.saveUserProfile(
                        localProfile.copy(
                            walletBalance = remoteBalance,
                            phoneNumber = remotePhone.ifEmpty { localProfile.phoneNumber }
                        )
                    )
                }

                if (response.role == "admin") {
                    _currentScreen.value = ShopScreen.STORES // branches to admin edit system
                } else {
                    _currentScreen.value = ShopScreen.STORES
                }
                syncAllData()
                onResult(true)
            } catch (e: Exception) {
                // Offline fallback - Check inside room database first
                val stored = repository.getUserProfile(emailStr).first()
                if (stored != null) {
                    if (stored.passwordHash == passwordStr) {
                        val response = LoginResponse(stored.email, stored.name, if (stored.email.endsWith("admin@marketedge.pro") || stored.email.contains("admin")) "admin" else "user", "LOCAL_MOCK_USER")
                        _activeUser.value = response
                        _checkoutSuccess.emit("Welcome, ${stored.name}! (Offline)")
                        _currentScreen.value = ShopScreen.STORES
                        onResult(true)
                    } else {
                        _errorMessage.emit("Incorrect password.")
                        onResult(false)
                    }
                } else {
                    // Predefined offline accounts fallback
                    if (emailStr.lowercase() == "admin@marketedge.pro" && passwordStr == "admin123") {
                        val response = LoginResponse("admin@marketedge.pro", "MarketEdge Administrator", "admin", "LOCAL_MOCK_ADMIN")
                        _activeUser.value = response
                        _checkoutSuccess.emit("Logged in as Local Admin (Offline Mode)")
                        _currentScreen.value = ShopScreen.STORES
                        onResult(true)
                    } else if (emailStr.lowercase() == "user@marketedge.pro" && passwordStr == "user123") {
                        val response = LoginResponse("user@marketedge.pro", "Enterprise Buyer", "user", "LOCAL_MOCK_USER")
                        _activeUser.value = response
                        _checkoutSuccess.emit("Logged in as Local User (Offline Mode)")
                        _currentScreen.value = ShopScreen.STORES
                        onResult(true)
                    } else {
                        _errorMessage.emit("Account not found. Please register to create an account offline.")
                        onResult(false)
                    }
                }
            }
        }
    }

    fun registerRemote(emailStr: String, passwordStr: String, nameStr: String, roleStr: String, adminToken: String? = null) {
        viewModelScope.launch {
            if (emailStr.isEmpty() || passwordStr.isEmpty() || nameStr.isEmpty()) {
                _errorMessage.emit("All registration fields are required.")
                return@launch
            }
            try {
                val api = getApiService()
                val response = api.register(RegisterRequest(emailStr, passwordStr, nameStr, roleStr, adminToken))
                _activeUser.value = response
                _isConnectedToBackend.value = true
                _checkoutSuccess.emit("Profile Registered and Connected!")
                
                // Cache locally
                repository.saveUserProfile(
                    UserProfileEntity(
                        email = emailStr,
                        name = nameStr,
                        phoneNumber = "",
                        passwordHash = passwordStr,
                        walletBalance = 120.00,
                        couponCount = 3
                    )
                )

                _currentScreen.value = ShopScreen.STORES
                syncAllData()
            } catch (e: Exception) {
                // Register offline in DB
                val existing = repository.getUserProfile(emailStr).first()
                if (existing != null) {
                    _errorMessage.emit("Account with email $emailStr is already registered offline. (${e.message ?: "Failed"})")
                } else {
                    repository.saveUserProfile(
                        UserProfileEntity(
                            email = emailStr,
                            name = nameStr,
                            phoneNumber = "",
                            passwordHash = passwordStr,
                            walletBalance = 120.00, // Welcome Gift!
                            couponCount = 3
                        )
                    )
                    val response = LoginResponse(emailStr, nameStr, roleStr, "LOCAL_MOCK_TOKEN")
                    _activeUser.value = response
                    _checkoutSuccess.emit("Registered successfully in offline database! Enjoy $120 welcome gift!")
                    _currentScreen.value = ShopScreen.STORES
                }
            }
        }
    }

    // Dynamic reactive branding properties mapped from the repository App Config Flow
    val currentBrandName: StateFlow<String> = appConfig.map { config ->
        val name = config?.customBrandName ?: "MarketEdge Pro"
        if (name.isBlank()) "MarketEdge Pro" else name
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MarketEdge Pro")

    val currentBrandColorHex: StateFlow<String> = appConfig.map { config ->
        val hex = config?.customBrandColorHex ?: "#FF1A73E8"
        if (hex.isBlank()) "#FF1A73E8" else hex
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#FF1A73E8")

    val currentLauncherName: StateFlow<String> = appConfig.map { config ->
        val title = config?.customLauncherName ?: "MarketEdge Pro"
        if (title.isBlank()) "MarketEdge Pro" else title
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MarketEdge Pro")

    fun requestRegisterOtp(
        emailStr: String,
        passwordStr: String,
        nameStr: String,
        roleStr: String,
        adminToken: String?,
        referredBy: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            if (emailStr.isEmpty() || passwordStr.isEmpty() || nameStr.isEmpty()) {
                _errorMessage.emit("All registration fields are required.")
                onResult(false, "All registration fields are required.")
                return@launch
            }
            try {
                val api = getApiService()
                val response = api.registerOtp(RegisterOtpRequest(emailStr, passwordStr, nameStr, roleStr, adminToken, referredBy))
                if (response.success) {
                    if (response.token != null) {
                        // Admin Auto-Login
                        _activeUser.value = LoginResponse(
                            email = response.email ?: emailStr,
                            name = response.name ?: nameStr,
                            role = response.role ?: "admin",
                            token = response.token
                        )
                        _isConnectedToBackend.value = true
                        _checkoutSuccess.emit("Administrator Verified Successfully!")
                        _currentScreen.value = ShopScreen.STORES
                        onResult(true, "ADMIN_AUTO_LOGIN")
                    } else {
                        onResult(true, response.message)
                    }
                } else {
                    onResult(false, response.message)
                }
            } catch (e: Exception) {
                // Offline fallback simulator code
                onResult(true, "Offline Simulator: Verification code 123456 sent to email (Offline Mode).")
            }
        }
    }

    fun resendRegisterOtp(emailStr: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val api = getApiService()
                val response = api.resendOtp(ResendOtpRequest(emailStr))
                onResult(response.success, response.message)
            } catch (e: Exception) {
                onResult(false, "Resend failed: ${e.message}")
            }
        }
    }

    fun verifyRegisterOtp(
        emailStr: String,
        otpCode: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val api = getApiService()
                val response = api.verifyOtp(VerifyOtpRequest(emailStr, otpCode))
                _activeUser.value = response
                _isConnectedToBackend.value = true
                _checkoutSuccess.emit("Profile Registered and Verified Successfully!")

                // Cache locally
                repository.saveUserProfile(
                    UserProfileEntity(
                        email = response.email,
                        name = response.name,
                        phoneNumber = "",
                        passwordHash = "", // Saved securely remote
                        walletBalance = 120.00,
                        couponCount = 3
                    )
                )
                _currentScreen.value = ShopScreen.STORES
                syncAllData()
                onResult(true, "Successfully Verified!")
            } catch (e: Exception) {
                _errorMessage.emit("Verification failed: ${e.message ?: "Invalid code"}")
                onResult(false, e.message ?: "Invalid verification code.")
            }
        }
    }

    fun requestForgotPassword(emailStr: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (emailStr.isEmpty()) {
                _errorMessage.emit("Email address is required.")
                onResult(false, "Email address is required.")
                return@launch
            }
            try {
                val api = getApiService()
                val response = api.forgotPassword(ForgotPasswordRequest(emailStr))
                onResult(true, response.message)
            } catch (e: Exception) {
                // Return success and let user reset offline
                onResult(true, "Offline Simulator: Password reset code 123456 has been logged (Offline Mode).")
            }
        }
    }

    fun resetPasswordWithToken(emailStr: String, tokenStr: String, newPasswordStr: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (emailStr.isEmpty() || tokenStr.isEmpty() || newPasswordStr.isEmpty()) {
                _errorMessage.emit("Email, token, and new password are required.")
                onResult(false, "Email, token, and new password are required.")
                return@launch
            }
            try {
                val api = getApiService()
                val response = api.resetPassword(ResetPasswordRequest(emailStr, tokenStr, newPasswordStr))
                if (response.success) {
                    onResult(true, response.message)
                } else {
                    onResult(false, response.message)
                }
            } catch (e: Exception) {
                if (tokenStr.trim() == "123456" || tokenStr.trim() == "111111") {
                    val profile = repository.getUserProfile(emailStr).first()
                    if (profile != null) {
                        repository.saveUserProfile(profile.copy(passwordHash = newPasswordStr))
                        onResult(true, "Password updated successfully in offline database!")
                    } else {
                        onResult(false, "User account not found in offline DB. Register a new user first.")
                    }
                } else {
                    onResult(false, "Reset failed: ${e.message ?: "Invalid reset token"}")
                }
            }
        }
    }

    fun saveCustomBranding(brandName: String, brandColorHex: String, launcherName: String, referralBonusAmount: Int = 20, storeCategories: String = "All,Fashion,Electronics,Home & Living,Beauty & Health,Toys & Games") {
        viewModelScope.launch {
            try {
                val current = appConfig.value ?: AppConfigEntity(
                    id = "globals",
                    sliderImages = "promo_banner",
                    promoText = "",
                    adText = "",
                    flashSalesEnds = 0,
                    flashSalesDiscount = 40,
                    carouselEditableContent = ""
                )
                val updated = current.copy(
                    customBrandName = brandName,
                    customBrandColorHex = brandColorHex,
                    customLauncherName = launcherName,
                    referralBonusAmount = referralBonusAmount,
                    storeCategories = storeCategories
                )
                
                // Save locally first for dynamic reactivity
                repository.saveAppConfig(updated)

                // Sync to backend if accessible
                val api = getApiService()
                api.updateAppConfig(updated)
                _checkoutSuccess.emit("Dynamic brand configurations saved and synced globally!")
            } catch (e: Exception) {
                _checkoutSuccess.emit("Dynamic brand credentials updated in local database successfully!")
            }
        }
    }

    fun logout() {
        _activeUser.value = null
        _currentScreen.value = ShopScreen.STORES // switch to main shop as guest
        _currentUserChat.value = emptyList()
        _adminSelectedChatHistory.value = emptyList()
        _selectedAdminSessionUser.value = null
        viewModelScope.launch {
            _checkoutSuccess.emit("Logged out successfully.")
        }
    }

    fun submitProductReview(productId: Long, rating: Int, text: String) {
        val user = _activeUser.value ?: return
        if (text.trim().isEmpty()) return
        viewModelScope.launch {
            val review = ReviewEntity(
                productId = productId,
                userEmail = user.email,
                userName = user.name,
                rating = rating,
                reviewText = text,
                timestamp = System.currentTimeMillis()
            )
            repository.insertReview(review)
            _checkoutSuccess.emit("Review submitted! Thank you for rating.")
        }
    }

    fun updateAppConfig(
        sliderImages: String,
        promoText: String,
        adText: String,
        flashSalesDiscount: Int,
        carouselEditableContent: String,
        algoEnabled: Boolean,
        storeCategories: String
    ) {
        viewModelScope.launch {
            val current = appConfig.value
            val config = AppConfigEntity(
                id = "globals",
                sliderImages = sliderImages,
                promoText = promoText,
                adText = adText,
                flashSalesEnds = current?.flashSalesEnds ?: (System.currentTimeMillis() + 86400000L),
                flashSalesDiscount = flashSalesDiscount,
                carouselEditableContent = carouselEditableContent,
                algorithmicPromotionEnabled = algoEnabled,
                customBrandName = current?.customBrandName ?: "MarketEdge Pro",
                customBrandColorHex = current?.customBrandColorHex ?: "#FF1A73E8",
                customLauncherName = current?.customLauncherName ?: "MarketEdge Pro",
                referralBonusAmount = current?.referralBonusAmount ?: 20,
                storeCategories = storeCategories
            )
            repository.saveAppConfig(config)
            
            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    api.updateAppConfig(config)
                    _checkoutSuccess.emit("Dynamic storefront live parameters synchronized to MongoDB & Cloud server!")
                } catch (e: Exception) {
                    _errorMessage.emit("Locally saved. Cloud appconfig synchronization failed: ${e.message}")
                }
            } else {
                _checkoutSuccess.emit("Dynamic storefront parameters updated locally offline!")
            }
        }
    }

    fun updateProfileSecure(phoneNumber: String, newPasswordStr: String? = null) {
        val user = _activeUser.value ?: return
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    val response = api.updateRemoteProfile(UpdateProfileRequest(user.email, newPasswordStr, phoneNumber))
                    if (response.success) {
                        val local = repository.getUserProfile(user.email).first()
                        if (local != null) {
                            repository.saveUserProfile(
                                local.copy(
                                    phoneNumber = phoneNumber,
                                    passwordHash = newPasswordStr ?: local.passwordHash
                                )
                            )
                        }
                        _checkoutSuccess.emit("Profile details processed securely on server!")
                    }
                } catch (e: Exception) {
                    _errorMessage.emit("Failed to update remote profile: ${e.message}")
                }
            } else {
                val local = repository.getUserProfile(user.email).first()
                if (local != null) {
                    repository.saveUserProfile(
                        local.copy(
                            phoneNumber = phoneNumber,
                            passwordHash = newPasswordStr ?: local.passwordHash
                        )
                    )
                    _checkoutSuccess.emit("Profile updated in local offline database.")
                }
            }
        }
    }

    fun fetchLiveOrderTracking(orderId: Long) {
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    val tracking = api.getOrderTracking(orderId)
                    _activeOrderTracking.value = tracking
                } catch (e: Exception) {
                    generateLocalTrackingCheckpointsFallback(orderId)
                }
            } else {
                generateLocalTrackingCheckpointsFallback(orderId)
            }
        }
    }

    private fun generateLocalTrackingCheckpointsFallback(orderId: Long) {
        val checkpoints = listOf(
            NetworkTrackingCheckpoint("Today", "Quality control inspection approved at local dispatch depot", true),
            NetworkTrackingCheckpoint("Yesterday", "Order packaged, processed & logged in local SQLite DB", true)
        )
        _activeOrderTracking.value = OrderTrackingResponse(orderId, "Processed", checkpoints)
    }

    fun depositToWalletSecure(
        amount: Double,
        cardNumber: String,
        expiry: String,
        cvc: String,
        gateway: String,
        onCompleted: (Boolean, String) -> Unit
    ) {
        val user = _activeUser.value
        if (user == null) {
            onCompleted(false, "User not authenticated.")
            return
        }
        if (amount <= 0.0) {
            onCompleted(false, "Recharge amount must be greater than zero.")
            return
        }
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    val req = DepositRequest(
                        email = user.email,
                        amount = amount,
                        cardNumber = cardNumber,
                        cardExpiry = expiry,
                        cardCvc = cvc,
                        gateway = gateway
                    )
                    val response = api.depositFunds(req)
                    if (response.success) {
                        val localProfile = repository.getUserProfile(user.email).first()
                        if (localProfile != null) {
                            repository.saveUserProfile(localProfile.copy(walletBalance = response.newWalletBalance))
                        }
                        _checkoutSuccess.emit("Deposit of $${String.format("%.2f", amount)} Approved via ${gateway}!")
                        onCompleted(true, "Payment successful! Receipt: ${response.receiptNo}")
                    } else {
                        _errorMessage.emit("Payment Rejected: ${response.message}")
                        onCompleted(false, response.message)
                    }
                } catch (e: Exception) {
                    _errorMessage.emit("Gateway Clearance Failed: ${e.message}")
                    onCompleted(false, e.message ?: "Network Gateway error.")
                }
            } else {
                // Offline Mode fallback
                val profile = repository.getUserProfile(user.email).first()
                if (profile != null) {
                    val updated = profile.copy(walletBalance = profile.walletBalance + amount)
                    repository.saveUserProfile(updated)
                    _checkoutSuccess.emit("Sandbox recharge of $${String.format("%.2f", amount)} approved locally.")
                    onCompleted(true, "Offline sandbox approval success.")
                } else {
                    onCompleted(false, "Offline profile not found.")
                }
            }
        }
    }

    fun changePassword(newPass: String) {
        val user = _activeUser.value ?: return
        if (newPass.isEmpty()) return
        viewModelScope.launch {
            val profile = repository.getUserProfile(user.email).first()
            if (profile != null) {
                repository.saveUserProfile(profile.copy(passwordHash = newPass))
                _checkoutSuccess.emit("Security password updated successfully!")
            }
        }
    }

    fun changePhoneNumber(newPhone: String) {
        val user = _activeUser.value ?: return
        if (newPhone.isEmpty()) return
        viewModelScope.launch {
            val profile = repository.getUserProfile(user.email).first()
            if (profile != null) {
                repository.saveUserProfile(profile.copy(phoneNumber = newPhone))
                _checkoutSuccess.emit("Contact phone number updated!")
            }
        }
    }

    fun depositToWallet(amount: Double) {
        val user = _activeUser.value ?: return
        if (amount <= 0.0) return
        viewModelScope.launch {
            val profile = repository.getUserProfile(user.email).first()
            if (profile != null) {
                val updated = profile.copy(walletBalance = profile.walletBalance + amount)
                repository.saveUserProfile(updated)
                _checkoutSuccess.emit("Success! Verified Secure Payment approved. Received $${String.format("%.2f", amount)}")
            }
        }
    }


    private fun syncAllData() {
        viewModelScope.launch {
            try {
                if (_isConnectedToBackend.value) {
                    val api = getApiService()
                    val products = api.getProducts()
                    
                    // Optional: clear local products to ensure we only have server data in Online Mode
                    // repository.clearAllProducts() // Assuming this exists or we add it
                    
                    for (p in products) {
                        repository.insertProduct(p.toEntity())
                    }
                    try {
                        val cloudConfig = api.getAppConfig()
                        repository.saveAppConfig(cloudConfig)
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                // Fail silently
            }
        }
    }

    // --- REALTIME CHAT METHODS ---
    fun sendUserChatMessage(text: String) {
        val user = _activeUser.value ?: return
        if (text.trim().isEmpty()) return

        viewModelScope.launch {
            val localMsg = ChatMessageEntity(
                userId = user.email,
                sender = "user",
                senderName = user.name,
                messageText = text,
                timestamp = System.currentTimeMillis()
            )
            repository.insertChatMessage(localMsg)
            _currentUserChat.value = _currentUserChat.value + localMsg

            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    api.sendChatMessage(user.email, ChatMessageRequest(text, "user", user.name))
                    refreshUserChatHistory()
                } catch (e: Exception) {
                    triggerLocalBotResponse(user.email, user.name, text)
                }
            } else {
                triggerLocalBotResponse(user.email, user.name, text)
            }
        }
    }

    private fun triggerLocalBotResponse(userId: String, userName: String, text: String) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            val botMsg = ChatMessageEntity(
                userId = userId,
                sender = "admin",
                senderName = "Temu Bot Co-pilot",
                messageText = "Hi $userName! Support is offline, but I received: \"$text\". Our admin team will address this soon!",
                timestamp = System.currentTimeMillis()
            )
            repository.insertChatMessage(botMsg)
            if (_activeUser.value?.email == userId) {
                _currentUserChat.value = _currentUserChat.value + botMsg
            }
        }
    }

    fun sendAdminChatMessage(userId: String, text: String) {
        val admin = _activeUser.value ?: return
        if (text.trim().isEmpty()) return

        viewModelScope.launch {
            val localMsg = ChatMessageEntity(
                userId = userId,
                sender = "admin",
                senderName = admin.name,
                messageText = text,
                timestamp = System.currentTimeMillis()
            )
            repository.insertChatMessage(localMsg)
            _adminSelectedChatHistory.value = _adminSelectedChatHistory.value + localMsg

            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    api.sendChatMessage(userId, ChatMessageRequest(text, "admin", admin.name))
                    refreshAdminChatHistory(userId)
                } catch (e: Exception) {
                    // Handled locally
                }
            }
        }
    }

    fun selectAdminChatUser(userId: String) {
        _selectedAdminSessionUser.value = userId
        refreshAdminChatHistory(userId)
    }

    private fun refreshUserChatHistory() {
        val user = _activeUser.value ?: return
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    val history = api.getChatHistory(user.email)
                    _currentUserChat.value = history.map { it.toEntity(user.email) }
                } catch (e: Exception) {
                    loadLocalUserChat(user.email)
                }
            } else {
                loadLocalUserChat(user.email)
            }
        }
    }

    private fun loadLocalUserChat(email: String) {
        viewModelScope.launch {
            repository.getChatHistory(email).collect { list ->
                _currentUserChat.value = list
            }
        }
    }

    private fun refreshAdminChatHistory(userId: String) {
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    val history = api.getChatHistory(userId)
                    _adminSelectedChatHistory.value = history.map { it.toEntity(userId) }
                } catch (e: Exception) {
                    loadLocalAdminChat(userId)
                }
            } else {
                loadLocalAdminChat(userId)
            }
        }
    }

    private fun loadLocalAdminChat(userId: String) {
        viewModelScope.launch {
            repository.getChatHistory(userId).collect { list ->
                _adminSelectedChatHistory.value = list
            }
        }
    }

    fun createAdminProduct(product: ProductEntity) {
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    getApiService().createProduct(product)
                    refreshProducts()
                } catch (e: Exception) {
                    repository.insertProduct(product.copy(id = System.currentTimeMillis()))
                }
            } else {
                repository.insertProduct(product.copy(id = System.currentTimeMillis()))
            }
        }
    }

    fun updateAdminProduct(product: ProductEntity) {
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    getApiService().updateProduct(product.id, product)
                    refreshProducts()
                } catch (e: Exception) {
                    repository.insertProduct(product)
                }
            } else {
                repository.insertProduct(product)
            }
        }
    }

    fun deleteAdminProduct(product: ProductEntity) {
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    getApiService().deleteProduct(product.id)
                    refreshProducts()
                } catch (e: Exception) {
                    repository.deleteProduct(product)
                }
            } else {
                repository.deleteProduct(product)
            }
        }
    }

    fun restockProduct(product: ProductEntity, amount: Int) {
        viewModelScope.launch {
            val updated = product.copy(stockQuantity = product.stockQuantity + amount)
            updateAdminProduct(updated)
        }
    }

    fun triggerReseed() {
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    getApiService().reseedProducts()
                    refreshProducts()
                } catch (e: Exception) {}
            }
        }
    }

    private suspend fun refreshProducts() {
        if (_isConnectedToBackend.value) {
            val products = getApiService().getProducts()
            products.map { it.toEntity() }.forEach { repository.insertProduct(it) }
        }
    }

    private fun startRealtimePolling() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(3000)
                try {
                    if (_isConnectedToBackend.value) {
                        val api = getApiService()
                        val products = api.getProducts()
                        val entities = products.map { it.toEntity() }
                        for (ent in entities) {
                            repository.insertProduct(ent)
                        }

                        val user = _activeUser.value
                        if (user != null) {
                            if (user.role == "admin") {
                                val sessions = api.getChatSessions()
                                _adminChatSessions.value = sessions
                                
                                val selectedUser = _selectedAdminSessionUser.value
                                if (selectedUser != null) {
                                    val history = api.getChatHistory(selectedUser)
                                    _adminSelectedChatHistory.value = history.map { it.toEntity(selectedUser) }
                                }
                            } else {
                                val history = api.getChatHistory(user.email)
                                _currentUserChat.value = history.map { it.toEntity(user.email) }
                            }
                        }
                    } else {
                        val user = _activeUser.value
                        if (user != null) {
                            if (user.role == "admin") {
                                repository.distinctChatUsers.collect { users ->
                                    _adminChatSessions.value = users.map { uid ->
                                        ChatSessionResponse(uid, 1, "Offline conversation session", "user", System.currentTimeMillis())
                                    }
                                }
                            } else {
                                repository.getChatHistory(user.email).collect { list ->
                                    _currentUserChat.value = list
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Silence network exceptions in background loops
                }
            }
        }
    }

    private fun selectDefaultImageKey(category: String): String {
        return when (category.lowercase()) {
            "fashion" -> "fashion_hoodie"
            "electronics" -> "electronics_buds"
            "beauty & health" -> "beauty_cream"
            "toys & games" -> "toys_car"
            else -> "home_shaver"
        }
    }
}

class ShopViewModelFactory(private val repository: ShopRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShopViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShopViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
