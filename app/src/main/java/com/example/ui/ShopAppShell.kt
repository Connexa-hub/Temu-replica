package com.example.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ProductEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopAppShell(viewModel: ShopViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val cartProducts by viewModel.cartWithProducts.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartTotalItems.collectAsStateWithLifecycle()

    // Observe checkout notifications/messages
    val checkoutSuccess = viewModel.checkoutSuccess
    val errorMessage = viewModel.errorMessage

    LaunchedEffect(key1 = true) {
        checkoutSuccess.collect { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(key1 = true) {
        errorMessage.collect { err ->
            snackbarHostState.showSnackbar(
                message = err,
                duration = SnackbarDuration.Long
            )
        }
    }

    // Modal product details
    val selectedProduct by viewModel.selectedProduct.collectAsStateWithLifecycle()
    val productForEdit by viewModel.productForEdit.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNavigationBar(
                viewModel = viewModel,
                currentScreen = currentScreen,
                cartCount = cartCount,
                onScreenSelect = { viewModel.selectScreen(it) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                ShopScreen.STORES -> StorefrontScreen(viewModel)
                ShopScreen.CART -> CartScreen(viewModel)
                ShopScreen.ORDERS -> OrdersScreen(viewModel)
                ShopScreen.CHAT -> ChatSupportScreen(viewModel)
                ShopScreen.ADMIN_CHATS -> AdminDashboardScreen(viewModel)
                ShopScreen.AUTH_SETTINGS -> AuthSettingsScreen(viewModel)
            }

            // Expanded Product Details Sheet
            selectedProduct?.let { product ->
                ProductDetailsDialog(
                    product = product,
                    onDismiss = { viewModel.selectProduct(null) },
                    onAddToCart = { qty ->
                        viewModel.addToCart(product, qty)
                        viewModel.selectProduct(null)
                    }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    viewModel: ShopViewModel,
    currentScreen: ShopScreen,
    cartCount: Int,
    onScreenSelect: (ShopScreen) -> Unit
) {
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val role = activeUser?.role ?: "guest"

    NavigationBar(
        tonalElevation = 8.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.shadow(12.dp)
    ) {
        // ALWAYS visible Store tab
        NavigationBarItem(
            selected = currentScreen == ShopScreen.STORES,
            onClick = { onScreenSelect(ShopScreen.STORES) },
            icon = { Icon(Icons.Filled.Storefront, contentDescription = "Store") },
            label = { Text("Store", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TemuOrangePrimary,
                selectedTextColor = TemuOrangePrimary,
                indicatorColor = TemuOrangePrimary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_tab_stores")
        )

        if (role != "admin") {
            NavigationBarItem(
                selected = currentScreen == ShopScreen.CART,
                onClick = { onScreenSelect(ShopScreen.CART) },
                icon = {
                    BadgedBox(badge = {
                        if (cartCount > 0) {
                            Badge(containerColor = TemuOrangePrimary) {
                                Text(cartCount.toString(), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = "Cart")
                    }
                },
                label = { Text("Cart", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TemuOrangePrimary,
                    selectedTextColor = TemuOrangePrimary,
                    indicatorColor = TemuOrangePrimary.copy(alpha = 0.12f)
                ),
                modifier = Modifier.testTag("nav_tab_cart")
            )

            NavigationBarItem(
                selected = currentScreen == ShopScreen.ORDERS,
                onClick = { onScreenSelect(ShopScreen.ORDERS) },
                icon = { Icon(Icons.Filled.ReceiptLong, contentDescription = "Orders") },
                label = { Text("Orders", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TemuOrangePrimary,
                    selectedTextColor = TemuOrangePrimary,
                    indicatorColor = TemuOrangePrimary.copy(alpha = 0.12f)
                ),
                modifier = Modifier.testTag("nav_tab_orders")
            )
        }

        // Standard User Support Chat
        if (role == "user") {
            NavigationBarItem(
                selected = currentScreen == ShopScreen.CHAT,
                onClick = { onScreenSelect(ShopScreen.CHAT) },
                icon = { Icon(Icons.Filled.Chat, contentDescription = "Support Chat") },
                label = { Text("Support", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TemuOrangePrimary,
                    selectedTextColor = TemuOrangePrimary,
                    indicatorColor = TemuOrangePrimary.copy(alpha = 0.12f)
                ),
                modifier = Modifier.testTag("nav_tab_chat")
            )
        }

        // Administrator Control Hub
        if (role == "admin") {
            NavigationBarItem(
                selected = currentScreen == ShopScreen.ADMIN_CHATS,
                onClick = { onScreenSelect(ShopScreen.ADMIN_CHATS) },
                icon = { Icon(Icons.Filled.SupportAgent, contentDescription = "Support Hub") },
                label = { Text("Admin Chats", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TemuOrangePrimary,
                    selectedTextColor = TemuOrangePrimary,
                    indicatorColor = TemuOrangePrimary.copy(alpha = 0.12f)
                ),
                modifier = Modifier.testTag("nav_tab_admin_chats")
            )
        }

        // ALWAYS visible Settings / Profile Tab
        NavigationBarItem(
            selected = currentScreen == ShopScreen.AUTH_SETTINGS,
            onClick = { onScreenSelect(ShopScreen.AUTH_SETTINGS) },
            icon = { Icon(Icons.Filled.Tune, contentDescription = "Account & Sync Settings") },
            label = { Text("Sync", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TemuOrangePrimary,
                selectedTextColor = TemuOrangePrimary,
                indicatorColor = TemuOrangePrimary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_tab_settings")
        )
    }
}

// ----------------------------------------------------
// 1. STOREFRONT SCREEN
// ----------------------------------------------------
@Composable
fun StorefrontScreen(viewModel: ShopViewModel) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Category lists
    val categories = listOf("All", "Fashion", "Electronics", "Home & Living", "Beauty & Health", "Toys & Games")

    // Dynamic countdown timer for Flash Sales
    var secondsLeft by remember { mutableIntStateOf(10543) }
    LaunchedEffect(true) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
    }

    val hours = secondsLeft / 3600
    val minutes = (secondsLeft % 3600) / 60
    val secs = secondsLeft % 60
    val countdownStr = String.format("%02d:%02d:%02d", hours, minutes, secs)

    Column(modifier = Modifier.fillMaxSize()) {
        // Orange top brand header with search
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(TemuOrangePrimary, TemuOrange)
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Store,
                            contentDescription = null,
                            tint = TemuOrangePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "TEMU",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                // Header Badges
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = "Flash deals icon",
                        tint = TemuYellowAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Shop Like a Billionaire",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.setQuery(it) },
                placeholder = { Text("Search million products...", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TemuOrangePrimary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store_search_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = DarkText,
                    unfocusedTextColor = DarkText
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
        }

        // Main retail feed lazy layout
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(RetailBackground),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Dynamic Seeding / Promotional Info
            item {
                PromotionalHeroBanner(countdownTime = countdownStr, context = context)
            }

            // Category Filter Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { viewModel.selectCategory(cat) },
                            label = { Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TemuOrangePrimary,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = DarkText
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCategory == cat,
                                selectedBorderColor = Color.Transparent,
                                borderColor = Color.LightGray.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            // Section Header: "Flash Deals" or Category Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (selectedCategory == "All") "Lightning Flash Sale" else selectedCategory,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(TemuOrangePrimary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("UP TO 90% OFF", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "${products.size} items",
                        fontSize = 12.sp,
                        color = Color.DarkGray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (products.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.FindInPage,
                            contentDescription = "No products found",
                            tint = Color.Gray,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No matching deals found",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Try searching other category tags or popular clearance deals.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // High-performance double columns list items
                items(products.chunked(2)) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (product in pair) {
                            Box(modifier = Modifier.weight(1f)) {
                                ProductGridCard(
                                    product = product,
                                    onSelect = { viewModel.selectProduct(product) },
                                    onAddToCart = { viewModel.addToCart(product) }
                                )
                            }
                        }
                        if (pair.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PromotionalHeroBanner(countdownTime: String, context: Context) {
    // Elegant layered card of hero banner with custom image reflection
    val imageId = getStoreBannerResourceId(context)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        if (imageId != 0) {
            Image(
                painter = painterResource(id = imageId),
                contentDescription = "Promotional banner background image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Gradient backup if no auto-generated image is accessible
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(TemuOrange, TemuOrangePrimary)
                        )
                    )
            )
        }

        // Tint overlay for readabilities
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        // Contents
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Yellow, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "90% OFF SPECIAL EVENT",
                    fontSize = 11.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    "Flash Clearance Sale",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = TemuYellowAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Ends in: ",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            countdownTime,
                            color = TemuYellowAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color.White, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Claim",
                                color = TemuOrangePrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = TemuOrangePrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductGridCard(
    product: ProductEntity,
    onSelect: () -> Unit,
    onAddToCart: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Visual Image placeholder drawing based on product type
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                getProductCategoryColor(product.category).copy(alpha = 0.35f),
                                Color.White
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Drawing nice icon representation
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        getProductCategoryIcon(product.category),
                        contentDescription = "Product representation icon",
                        tint = getProductCategoryColor(product.category),
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                getProductCategoryColor(product.category).copy(alpha = 0.15f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            product.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = getProductCategoryColor(product.category)
                        )
                    }
                }

                // Discount Badge overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(TemuOrangePrimary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "-${product.discountPercent}%",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Info rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    product.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Price Row
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        String.format("$%.2f", product.discountedPrice),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = TemuOrangePrimary
                    )
                    Text(
                        String.format("$%.2f", product.price),
                        fontSize = 11.sp,
                        textDecoration = TextDecoration.LineThrough,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Sales & stock indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${product.salesCount} sold",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Stock alert level indicator
                    if (product.stockQuantity <= 5) {
                        Box(
                            modifier = Modifier
                                .background(AlertRed.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "Only ${product.stockQuantity} left",
                                fontSize = 9.sp,
                                color = AlertRed,
                                fontWeight = FontWeight.Black
                            )
                        }
                    } else {
                        Text(
                            "${product.stockQuantity} in stock",
                            fontSize = 10.sp,
                            color = PositiveGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Add to Cart Button (Accessible 48dp target)
                Button(
                    onClick = { onAddToCart() },
                    colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .testTag("add_to_cart_btn"),
                    enabled = product.stockQuantity > 0
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (product.stockQuantity > 0) "Add to Cart" else "Sold Out",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 2. CART SCREEN
// ----------------------------------------------------
@Composable
fun CartScreen(viewModel: ShopViewModel) {
    val items by viewModel.cartWithProducts.collectAsStateWithLifecycle()
    val subtotal by viewModel.cartSubtotalPrice.collectAsStateWithLifecycle()
    val savedAmount by viewModel.cartSavedPrice.collectAsStateWithLifecycle()
    val totalAmount by viewModel.cartTotalPrice.collectAsStateWithLifecycle()
    val totalCount by viewModel.cartTotalItems.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RetailBackground)
    ) {
        // Simple Top Header Bar
        TopAppBarHeader("Shopping Cart ($totalCount)")

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.ShoppingCart,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Your cart is empty",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Slash up to 90% off premium items in our store and add them here to order!",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.selectScreen(ShopScreen.STORES) },
                        colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Start Saving", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Cart items listing
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Warning note about orders deducting database stock
                item {
                    AlertBanner(
                        title = "Real-time Stock Checked",
                        desc = "Placing this order deducts real-time catalog quantities from the local DB, instantly updating analytics charts.",
                        icon = Icons.Default.Info,
                        color = PositiveGreen
                    )
                }

                items(items) { cartItem ->
                    CartItemRow(
                        cartItem = cartItem,
                        onUpdateQty = { qty -> viewModel.updateCartQty(cartItem.product.id, qty) },
                        onRemove = { viewModel.deleteFromCart(cartItem.product.id) }
                    )
                }
            }

            // Checkout Panel
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Pricing breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Original Subtotal:", fontSize = 13.sp, color = Color.Gray)
                        Text(String.format("$%.2f", subtotal), fontSize = 13.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Special Instant Discount:", fontSize = 13.sp, color = AlertRed, fontWeight = FontWeight.Medium)
                        Text(String.format("-$%.2f", savedAmount), fontSize = 13.sp, color = AlertRed, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Shipping Fee:", fontSize = 13.sp, color = Color.Gray)
                        Text("FREE", fontSize = 13.sp, color = PositiveGreen, fontWeight = FontWeight.Black)
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Amount:", fontSize = 13.sp, color = DarkText, fontWeight = FontWeight.Medium)
                            Text(
                                String.format("$%.2f", totalAmount),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = TemuOrangePrimary
                            )
                        }

                        // Order Checkout button (Accessible 48dp vertical target)
                        Button(
                            onClick = { viewModel.executeCheckout() },
                            colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                            modifier = Modifier
                                .height(48.dp)
                                .width(180.dp)
                                .testTag("checkout_btn"),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("Submit Order", fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    cartItem: CartProductItem,
    onUpdateQty: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left category visual symbol instead of heavy network image
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        getProductCategoryColor(cartItem.product.category).copy(alpha = 0.15f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    getProductCategoryIcon(cartItem.product.category),
                    contentDescription = null,
                    tint = getProductCategoryColor(cartItem.product.category),
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text Info Columns
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        cartItem.product.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove item from cart",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        String.format("$%.2f", cartItem.product.discountedPrice),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = TemuOrangePrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        String.format("$%.2f", cartItem.product.price),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textDecoration = TextDecoration.LineThrough
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quantity Adjuster Controls (Ensure 48dp clickable targets)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(RetailBackground, RoundedCornerShape(16.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onUpdateQty(cartItem.quantity - 1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity", tint = DarkText, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                cartItem.quantity.toString(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                            IconButton(
                                onClick = { onUpdateQty(cartItem.quantity + 1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase quantity", tint = DarkText, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Total Row
                    Text(
                        "Total: " + String.format("$%.2f", cartItem.totalDiscountedPrice),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkText
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// 3. USER ORDERS SCREEN
// ----------------------------------------------------
@Composable
fun OrdersScreen(viewModel: ShopViewModel) {
    val orders by viewModel.allOrders.collectAsStateWithLifecycle()
    val orderItems by viewModel.allOrderItems.collectAsStateWithLifecycle()
    var selectedOrderForTracking by remember { mutableStateOf<com.example.data.OrderEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RetailBackground)
    ) {
        TopAppBarHeader("My Temu Orders")

        if (orders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No orders placed yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Slash up to 90% off standard pricing and check out inside your Cart to start shopping!",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.selectScreen(ShopScreen.STORES) },
                        colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Explore Special Clearance", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info Banner
                item {
                    AlertBanner(
                        title = "Guaranteed Free Delivery",
                        desc = "All orders enjoy free premium courier tracking with standard 3-5 days delivery transit duration.",
                        icon = Icons.Default.LocalShipping,
                        color = PositiveGreen
                    )
                }

                items(orders) { order ->
                    val matchedItems = orderItems.filter { it.orderId == order.orderId }
                    OrderRowCard(
                        order = order,
                        items = matchedItems,
                        onTrackOrder = { selectedOrderForTracking = order }
                    )
                }
            }
        }
    }

    // Interactive Tracking Dialog
    selectedOrderForTracking?.let { order ->
        Dialog(onDismissRequest = { selectedOrderForTracking = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.ShareLocation,
                        contentDescription = null,
                        tint = TemuOrangePrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Courier Package Tracker",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = DarkText
                    )
                    Text(
                        "Code: TEMU-${order.orderId + 84792}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TrackingCheckpoint(time = "Today", status = "In customs / Shipped from sorting hub", isDone = true)
                    TrackingCheckpoint(time = "Yesterday", status = "Cleared warehouse terminal", isDone = true)
                    TrackingCheckpoint(time = "2 Days ago", status = "Order packaged and processed", isDone = true)

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { selectedOrderForTracking = null },
                        colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun OrderRowCard(
    order: com.example.data.OrderEntity,
    items: List<com.example.data.OrderItemEntity>,
    onTrackOrder: () -> Unit
) {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.US)
    val dateStr = sdf.format(java.util.Date(order.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Receipt #TEMU-${order.orderId + 84792}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = DarkText
                    )
                    Text(
                        dateStr,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                Box(
                    modifier = Modifier
                        .background(PositiveGreen.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Package Shipped",
                        color = PositiveGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(getProductCategoryColor(item.category), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                item.productName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "qty: ${item.quantity} • " + String.format("$%.2f", item.price * item.quantity),
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Charged Total Amount:",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        String.format("$%.2f", order.totalAmount),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = TemuOrangePrimary
                    )
                }

                Button(
                    onClick = onTrackOrder,
                    colors = ButtonDefaults.buttonColors(containerColor = RetailBackground, contentColor = DarkText),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.PinDrop, contentDescription = null, tint = TemuOrangePrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Track Package", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TrackingCheckpoint(time: String, status: String, isDone: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(if (isDone) PositiveGreen else Color.LightGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(32.dp)
                    .background(Color.LightGray)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(time, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDone) PositiveGreen else Color.Gray)
            Text(status, fontSize = 13.sp, color = DarkText)
        }
    }
}

// ----------------------------------------------------
// PRODUCT DETAILS DIALOG
// ----------------------------------------------------
@Composable
fun ProductDetailsDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onAddToCart: (Int) -> Unit
) {
    var selectedQty by remember { mutableIntStateOf(1) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth()
                .shadow(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close details")
                        }
                    }
                }

                // Category illustration drawing header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(
                                getProductCategoryColor(product.category).copy(alpha = 0.12f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                getProductCategoryIcon(product.category),
                                contentDescription = null,
                                tint = getProductCategoryColor(product.category),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "SPECIAL RELEASES: ${product.category.uppercase()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = getProductCategoryColor(product.category)
                            )
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(TemuOrangePrimary, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "-${product.discountPercent}% clearance price",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "90% off clearance event",
                                fontSize = 12.sp,
                                color = TemuOrangePrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            product.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = DarkText,
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Price layout
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                String.format("$%.2f", product.discountedPrice),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = TemuOrangePrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "List value: " + String.format("$%.2f", product.price),
                                fontSize = 14.sp,
                                textDecoration = TextDecoration.LineThrough,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Description
                        Text(
                            "Product Description",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            product.description,
                            fontSize = 13.sp,
                            color = Color.DarkGray,
                            lineHeight = 18.sp
                        )

                        Divider(modifier = Modifier.padding(vertical = 16.dp))

                        // Stock levels & metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Warehouse Inventory",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${product.stockQuantity} items in stock",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (product.stockQuantity <= 5) AlertRed else PositiveGreen
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Volume Sold",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${product.salesCount} sold",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 16.dp))

                        // Interactive quantity selector (Ensure 48dp metrics)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Quantity Desired:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )

                            Box(
                                modifier = Modifier
                                    .background(RetailBackground, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (selectedQty > 1) selectedQty-- },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Decrease select", tint = DarkText)
                                    }
                                    Text(
                                        selectedQty.toString(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = DarkText,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    )
                                    IconButton(
                                        onClick = { if (selectedQty < product.stockQuantity) selectedQty++ },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Increase select", tint = DarkText)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Large Add button
                        Button(
                            onClick = { onAddToCart(selectedQty) },
                            enabled = product.stockQuantity >= selectedQty,
                            colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("detailed_add_btn")
                        ) {
                            Text(
                                if (product.stockQuantity >= selectedQty) "Add $selectedQty to Cart • " + String.format("$%.2f", product.discountedPrice * selectedQty) else "Insufficient Stock",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// CREATE / EDIT PRODUCT DIALOG
// ----------------------------------------------------
@Composable
fun CreateEditProductDialog(
    product: ProductEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, desc: String, category: String, price: Double, discount: Int, stock: Int) -> Unit
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var desc by remember { mutableStateOf(product?.description ?: "") }
    var category by remember { mutableStateOf(product?.category ?: "Fashion") }
    var priceStr by remember { mutableStateOf(product?.price?.toString() ?: "") }
    var discountStr by remember { mutableStateOf(product?.discountPercent?.toString() ?: "0") }
    var stockStr by remember { mutableStateOf(product?.stockQuantity?.toString() ?: "25") }

    val categories = listOf("Fashion", "Electronics", "Home & Living", "Beauty & Health", "Toys & Games")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .shadow(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        if (product == null) "Create Catalog Product" else "Edit Core Product",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = DarkText
                    )
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product Title") },
                        modifier = Modifier.fillMaxWidth().testTag("product_name_input"),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Detailed Description") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }

                item {
                    Text("Product Department:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = priceStr,
                            onValueChange = { priceStr = it },
                            label = { Text("Base Price ($)") },
                            modifier = Modifier.weight(1f).testTag("product_price_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = discountStr,
                            onValueChange = { discountStr = it },
                            label = { Text("Discount (%)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = stockStr,
                        onValueChange = { stockStr = it },
                        label = { Text("Core Stock (items)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val price = priceStr.toDoubleOrNull() ?: 0.0
                                val discount = discountStr.toIntOrNull() ?: 0
                                val stock = stockStr.toIntOrNull() ?: 0
                                onSave(name, desc, category, price, discount, stock)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_product_btn")
                        ) {
                            Text("Save Product", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// LAYOUT GRAPHICAL AUXILIARIES
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarHeader(title: String) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                title,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = Color.White
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = TemuOrangePrimary
        ),
        modifier = Modifier.shadow(4.dp)
    )
}

@Composable
fun AlertBanner(title: String, desc: String, icon: ImageVector, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                Text(desc, fontSize = 11.sp, color = DarkText, lineHeight = 14.sp)
            }
        }
    }
}

// Map helper to grab consistent graphics
fun getProductCategoryIcon(cat: String): ImageVector {
    return when (cat.lowercase()) {
        "fashion" -> Icons.Default.Checkroom
        "electronics" -> Icons.Default.Headphones
        "home & living" -> Icons.Default.SoupKitchen
        "beauty & health" -> Icons.Default.AutoAwesome
        "toys & games" -> Icons.Default.SportsEsports
        else -> Icons.Default.ShoppingBag
    }
}

fun getProductCategoryColor(cat: String): Color {
    return when (cat.lowercase()) {
        "fashion" -> Color(0xFF673AB7) // Purple
        "electronics" -> Color(0xFF2196F3) // Blue
        "home & living" -> Color(0xFF4CAF50) // Green
        "beauty & health" -> Color(0xFFE91E63) // Pink
        "toys & games" -> Color(0xFFFF9800) // Orange
        else -> TemuOrangePrimary
    }
}

fun getStoreBannerResourceId(context: Context): Int {
    try {
        val drawableClass = Class.forName("${context.packageName}.R\$drawable")
        for (field in drawableClass.declaredFields) {
            if (field.name.startsWith("store_hero_banner")) {
                val resId = field.getInt(null)
                if (resId != 0) return resId
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return 0
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AuthSettingsScreen(viewModel: ShopViewModel) {
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnectedToBackend.collectAsStateWithLifecycle()
    val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()

    var urlInput by remember { mutableStateOf(backendUrl) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("user") } // user or admin

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TemuOrangePrimary.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, TemuOrangePrimary)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "TEMU REAL-TIME HUB",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = TemuOrangePrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Control catalog, prices, and chat sessions dynamically from the cloud",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Sync Status: ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(32.dp))
                            .background(if (isConnected) Color(0xFFE8F5E9) else Color(0xFFECEFF1))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF9800))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isConnected) "LIVE CHANNEL SYNCED" else "OFFLINE LOCAL DATABASE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isConnected) Color(0xFF2E7D32) else Color(0xFF37474F)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Express Server Endpoint Url") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.updateBackendUrl(urlInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            urlInput = "http://10.0.2.2:3000"
                            viewModel.updateBackendUrl("http://10.0.2.2:3000")
                        },
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text("Android Emulator Loopback", fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Authentication Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                if (activeUser == null) {
                    Text(
                        "Sign In or Register Account",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Authenticate to unlock consumer chat channels and administrator inventories",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password credentials") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Full Name (Optional for sign up)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Primary Account Role Profile:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { selectedRole = "user" }
                        ) {
                            RadioButton(
                                selected = selectedRole == "user",
                                onClick = { selectedRole = "user" },
                                colors = RadioButtonDefaults.colors(selectedColor = TemuOrangePrimary)
                            )
                            Text("Customer / Buyer", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { selectedRole = "admin" }
                        ) {
                            RadioButton(
                                selected = selectedRole == "admin",
                                onClick = { selectedRole = "admin" },
                                colors = RadioButtonDefaults.colors(selectedColor = TemuOrangePrimary)
                            )
                            Text("Administrator / Merchant", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Demo autofills
                    Text("Develoment Quick Autofill:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                emailInput = "user@temu.com"
                                passwordInput = "user123"
                                selectedRole = "user"
                                viewModel.loginRemote(emailInput, passwordInput)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Jack Buyer (Client)", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                emailInput = "admin@temu.com"
                                passwordInput = "admin123"
                                selectedRole = "admin"
                                viewModel.loginRemote(emailInput, passwordInput)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Admin Mode (Merchant)", fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.loginRemote(emailInput, passwordInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Log In", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.registerRemote(emailInput, passwordInput, nameInput.ifEmpty { "New Account" }, selectedRole) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Register", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                "Authenticated Account Profile:",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                activeUser!!.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                activeUser!!.email,
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TemuOrangePrimary.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    activeUser!!.role.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TemuOrangePrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.background(Color.Red.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Logout, contentDescription = "Log Out", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatSupportScreen(viewModel: ShopViewModel) {
    val chatMessages by viewModel.currentUserChat.collectAsStateWithLifecycle()
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            lazyListState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Chat Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TemuOrangePrimary),
            shape = RoundedCornerShape(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.SupportAgent,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Live Customer Support Chat",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        "Support center active • 24/7 assistance on catalog, prices & deliveries",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }
            }
        }

        // Chat messages column
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (chatMessages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.Chat,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No support tickets opened yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Type your inquiry below to open an immediate support chat session with our admins or AI Bot Co-pilot.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(chatMessages) { msg ->
                    val isSelf = msg.sender == "user"
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
                    ) {
                        Text(
                            text = if (isSelf) "You" else msg.senderName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp)
                        )
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelf) TemuOrangePrimary else Color(0xFFF1F1F1)
                            ),
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isSelf) 16.dp else 0.dp,
                                bottomEnd = if (isSelf) 0.dp else 16.dp
                            ),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Box(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.messageText,
                                    fontSize = 13.sp,
                                    color = if (isSelf) Color.White else Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom input
        Surface(
            tonalElevation = 8.dp,
            modifier = Modifier.shadow(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Type query ticket here...", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TemuOrangePrimary,
                        unfocusedBorderColor = Color.LightGray
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (textInput.trim().isNotEmpty()) {
                            viewModel.sendUserChatMessage(textInput)
                            textInput = ""
                        }
                    },
                    modifier = Modifier.background(TemuOrangePrimary, CircleShape)
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send Message", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun AdminDashboardScreen(viewModel: ShopViewModel) {
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnectedToBackend.collectAsStateWithLifecycle()
    val chatSessions by viewModel.adminChatSessions.collectAsStateWithLifecycle()
    val selectedUser by viewModel.selectedAdminSessionUser.collectAsStateWithLifecycle()
    val selectedChatMessages by viewModel.adminSelectedChatHistory.collectAsStateWithLifecycle()
    val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Chats, 1: Operations
    var responseInputText by remember { mutableStateOf("") }
    val productsCatalog by viewModel.allProducts.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Admin Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF37474F)),
            shape = RoundedCornerShape(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.SupportAgent,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Administrator Control Hub",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF9800))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isConnected) "Synced with Cloud Backend" else "Local Offline Mode (Room DB)",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        // Segment Tabs
        TabRow(
            selectedTabIndex = activeTab,
            contentColor = TemuOrangePrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = TemuOrangePrimary
                )
            }
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Consumer Chat Support", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Store Control Room", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
        }

        if (activeTab == 0) {
            // Split or stacked Layout for Chats
            Row(modifier = Modifier.fillMaxSize()) {
                // Chats Sessions list Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.White)
                        .border(1.dp, Color(0xFFEEEEEE))
                ) {
                    Text(
                        "Consumer Threads",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        if (chatSessions.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No active threads received yet.",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(chatSessions) { session ->
                                val isChosen = selectedUser == session.userId
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isChosen) TemuOrangePrimary.copy(alpha = 0.1f) else Color.White)
                                        .clickable { viewModel.selectAdminChatUser(session.userId) }
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        session.userId,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isChosen) TemuOrangePrimary else Color.Black
                                    )
                                    Text(
                                        session.lastMessage,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 10.sp,
                                        color = Color.DarkGray
                                    )
                                }
                                Divider(color = Color(0xFFEEEEEE))
                            }
                        }
                    }
                }

                Divider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    color = Color.LightGray
                )

                // Conversation detailed column
                Column(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight()
                ) {
                    if (selectedUser == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No Thread Selected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                "Select any consumer session thread on the left to read history and reply in real-time.",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                "Replying to ticket: ${selectedUser}",
                                modifier = Modifier.padding(8.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TemuOrangePrimary
                            )
                            Divider()

                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(selectedChatMessages) { msg ->
                                    val isSelf = msg.sender == "admin"
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
                                    ) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelf) Color(0xFF37474F) else Color(0xFFECEFF1)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.widthIn(max = 200.dp)
                                        ) {
                                            Box(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    msg.messageText,
                                                    fontSize = 11.sp,
                                                    color = if (isSelf) Color.White else Color.Black
                                                )
                                            }
                                        }
                                        Text(
                                            msg.senderName,
                                            fontSize = 9.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFEEEEEE))
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = responseInputText,
                                    onValueChange = { responseInputText = it },
                                    placeholder = { Text("Response text...", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        if (responseInputText.trim().isNotEmpty()) {
                                            viewModel.sendAdminChatMessage(selectedUser!!, responseInputText)
                                            responseInputText = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Send, contentDescription = null, tint = TemuOrangePrimary)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Tab 1: Server Controls and merchandise operations
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "Store Operations Admin deck",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Black
                )
                Text(
                    "Deductions, restock items and seed operations directly on server catalog",
                    fontSize = 10.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Restock Deck Card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Rapid Restock Controller",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TemuOrangePrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            productsCatalog.forEach { prod ->
                                Card(
                                    modifier = Modifier
                                        .width(180.dp)
                                        .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            prod.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("Price: \$${prod.price} | Stock: ${prod.stockQuantity}", fontSize = 10.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.restockProduct(prod, 10) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1f)
                                                    .height(28.dp)
                                            ) {
                                                Text("+10 Unit", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteAdminProduct(prod) },
                                                modifier = Modifier.size(28.dp)
                                                    .background(Color.Red.copy(alpha = 0.1f), CircleShape)
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Database controllers card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Database seeding controllers",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Reseed or wipe catalog cache dynamically across server or local tables.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.triggerReseed() },
                            colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Re-seed Store Catalog with default items", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

