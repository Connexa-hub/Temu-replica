package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
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
    ORDERS,
    CHAT,
    ADMIN_CHATS,
    AUTH_SETTINGS
}

// Data holder for simplified dynamic charts in analytics
data class CategorySales(val category: String, val amount: Double, val quantityUnits: Int)
data class DailySales(val dayStr: String, val amount: Double)

class ShopViewModel(private val repository: ShopRepository) : ViewModel() {

    // Selected Navigation Tab
    private val _currentScreen = MutableStateFlow(ShopScreen.STORES)
    val currentScreen: StateFlow<ShopScreen> = _currentScreen.asStateFlow()

    // Active User session data
    private val _activeUser = MutableStateFlow<LoginResponse?>(null)
    val activeUser: StateFlow<LoginResponse?> = _activeUser.asStateFlow()

    // Remote integration configurations
    private val _backendUrl = MutableStateFlow("http://10.0.2.2:3000")
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

    // State indicators
    private val _checkoutSuccess = MutableSharedFlow<String>()
    val checkoutSuccess = _checkoutSuccess.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    // Observe DB products
    val allProducts: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Combined search/category filtered products
    val filteredProducts: StateFlow<List<ProductEntity>> = combine(
        allProducts,
        searchQuery,
        selectedCategory
    ) { products, query, cat ->
        products.filter { prod ->
            val matchesQuery = prod.name.contains(query, ignoreCase = true) || 
                               prod.description.contains(query, ignoreCase = true)
            val matchesCategory = cat == "All" || prod.category.equals(cat, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun selectProduct(product: ProductEntity?) {
        _selectedProduct.value = product
    }

    fun setProductForEdit(product: ProductEntity?) {
        _productForEdit.value = product
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

    fun executeCheckout() {
        val currentCart = cartWithProducts.value
        if (currentCart.isEmpty()) {
            viewModelScope.launch { _errorMessage.emit("Your cart is empty.") }
            return
        }
        viewModelScope.launch {
            val list = currentCart.map { it.product to it.quantity }
            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    val req = OrderRequest(
                        totalAmount = cartTotalPrice.value,
                        items = currentCart.map { OrderItemRequest(it.product.id, it.quantity) }
                    )
                    api.checkout(req)
                    repository.clearCart()
                    _checkoutSuccess.emit("Order Placed on Live Server! Stock deducted.")
                    syncAllData()
                } catch (e: Exception) {
                    _errorMessage.emit("Server Error during Checkout: ${e.message}")
                }
            } else {
                val completed = repository.checkout(list)
                if (completed) {
                    _checkoutSuccess.emit("Order Placed Successfully! Stock deducted.")
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

    fun restockProduct(product: ProductEntity, amount: Int) {
        if (amount <= 0) return
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    val updated = product.copy(stockQuantity = product.stockQuantity + amount)
                    api.updateProduct(product.id, updated)
                    _checkoutSuccess.emit("Restocked on Server + $amount")
                    repository.updateProduct(updated)
                } catch (e: Exception) {
                    _errorMessage.emit("Live Server update failed: ${e.message}")
                }
            } else {
                val updated = product.copy(stockQuantity = product.stockQuantity + amount)
                repository.updateProduct(updated)
                _checkoutSuccess.emit("Restocked + $amount for ${product.name}")
            }
        }
    }

    fun deleteAdminProduct(product: ProductEntity) {
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    api.deleteProduct(product.id)
                    repository.deleteProduct(product)
                    _checkoutSuccess.emit("Removed item from Server!")
                } catch (e: Exception) {
                    _errorMessage.emit("Live Server deletion failed: ${e.message}")
                }
            } else {
                repository.deleteProduct(product)
                _checkoutSuccess.emit("Removed ${product.name} from Local Database.")
            }
        }
    }

    fun triggerReseed() {
        viewModelScope.launch {
            if (_isConnectedToBackend.value) {
                try {
                    val api = getApiService()
                    api.reseedProducts()
                    val products = api.getProducts()
                    for (ent in products.map { it.toEntity() }) {
                        repository.insertProduct(ent)
                    }
                    _checkoutSuccess.emit("Server Catalog fully reseeded & hydrated!")
                } catch (e: Exception) {
                    _errorMessage.emit("Live Server reseed failed: ${e.message}")
                }
            } else {
                repository.seedDatabase()
                _checkoutSuccess.emit("Local Database fully re-seeded.")
            }
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
            try {
                val api = getApiService()
                val response = api.login(LoginRequest(emailStr, passwordStr))
                _activeUser.value = response
                _isConnectedToBackend.value = true
                _checkoutSuccess.emit("Welcome back, ${response.name}!")
                if (response.role == "admin") {
                    _currentScreen.value = ShopScreen.ADMIN_CHATS
                } else {
                    _currentScreen.value = ShopScreen.STORES
                }
                syncAllData()
                onResult(true)
            } catch (e: Exception) {
                if (emailStr.lowercase() == "admin@temu.com" && passwordStr == "admin123") {
                    val response = LoginResponse("admin@temu.com", "Temu Administrator", "admin", "LOCAL_MOCK_ADMIN")
                    _activeUser.value = response
                    _checkoutSuccess.emit("Logged in as Local Admin (Offline Mode)")
                    _currentScreen.value = ShopScreen.ADMIN_CHATS
                    onResult(true)
                } else if (emailStr.lowercase() == "user@temu.com" && passwordStr == "user123") {
                    val response = LoginResponse("user@temu.com", "Jack Buyer", "user", "LOCAL_MOCK_USER")
                    _activeUser.value = response
                    _checkoutSuccess.emit("Logged in as Local User (Offline Mode)")
                    _currentScreen.value = ShopScreen.STORES
                    onResult(true)
                } else {
                    _errorMessage.emit("Network Auth Error: ${e.message ?: "Invalid details"}")
                    onResult(false)
                }
            }
        }
    }

    fun registerRemote(emailStr: String, passwordStr: String, nameStr: String, roleStr: String) {
        viewModelScope.launch {
            try {
                val api = getApiService()
                val response = api.register(RegisterRequest(emailStr, passwordStr, nameStr, roleStr))
                _activeUser.value = response
                _isConnectedToBackend.value = true
                _checkoutSuccess.emit("Profile Registered and Connected!")
                if (response.role == "admin") {
                    _currentScreen.value = ShopScreen.ADMIN_CHATS
                } else {
                    _currentScreen.value = ShopScreen.STORES
                }
                syncAllData()
            } catch (e: Exception) {
                _errorMessage.emit("Fail to register registration ticket: ${e.message}")
            }
        }
    }

    fun logout() {
        _activeUser.value = null
        _currentScreen.value = ShopScreen.AUTH_SETTINGS
        _currentUserChat.value = emptyList()
        _adminSelectedChatHistory.value = emptyList()
        _selectedAdminSessionUser.value = null
        viewModelScope.launch {
            _checkoutSuccess.emit("Credentials cleared safely.")
        }
    }

    private fun syncAllData() {
        viewModelScope.launch {
            try {
                if (_isConnectedToBackend.value) {
                    val api = getApiService()
                    val products = api.getProducts()
                    for (p in products) {
                        repository.insertProduct(p.toEntity())
                    }
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
