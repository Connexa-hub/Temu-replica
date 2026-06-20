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
    ORDERS
}

// Data holder for simplified dynamic charts in analytics
data class CategorySales(val category: String, val amount: Double, val quantityUnits: Int)
data class DailySales(val dayStr: String, val amount: Double)

class ShopViewModel(private val repository: ShopRepository) : ViewModel() {

    // Selected Navigation Tab
    private val _currentScreen = MutableStateFlow(ShopScreen.STORES)
    val currentScreen: StateFlow<ShopScreen> = _currentScreen.asStateFlow()

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
            val completed = repository.checkout(list)
            if (completed) {
                _checkoutSuccess.emit("Order Placed Successfully! Stock deducted.")
            } else {
                _errorMessage.emit("Checkout failed. Please inspect product stock levels.")
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
            if (id == 0L) {
                // Create new
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
                _checkoutSuccess.emit("Product created: $name")
            } else {
                // Update
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
                    _checkoutSuccess.emit("Product updated: $name")
                }
            }
            _productForEdit.value = null
        }
    }

    fun restockProduct(product: ProductEntity, amount: Int) {
        if (amount <= 0) return
        viewModelScope.launch {
            val updated = product.copy(stockQuantity = product.stockQuantity + amount)
            repository.updateProduct(updated)
            _checkoutSuccess.emit("Restocked + $amount for ${product.name}")
        }
    }

    fun deleteAdminProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            _checkoutSuccess.emit("Removed ${product.name} from Database.")
        }
    }

    fun triggerReseed() {
        viewModelScope.launch {
            repository.seedDatabase()
            _checkoutSuccess.emit("Database fully re-seeded with mock catalog & sales.")
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
