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
import com.example.data.AppConfigEntity
import androidx.compose.ui.text.font.FontStyle
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
                    viewModel = viewModel,
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
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val appConfig by viewModel.appConfig.collectAsStateWithLifecycle()
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
            // --- ADMINISTRATIVE STOREFRONT PORTAL COMPONENT ---
            if (activeUser != null && activeUser!!.role == "admin") {
                item {
                    var showAdminSection by remember { mutableStateOf(false) }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TemuOrangePrimary.copy(alpha = 0.05f)),
                        border = BorderStroke(1.5.dp, TemuOrangePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showAdminSection = !showAdminSection },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Settings, contentDescription = null, tint = TemuOrangePrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Storefront Portal Editor (ADMIN MODE)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TemuOrangePrimary)
                                }
                                Icon(
                                    imageVector = if (showAdminSection) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = TemuOrangePrimary
                                )
                            }

                            if (showAdminSection) {
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                var editPromoText by remember(appConfig) { mutableStateOf(appConfig?.promoText ?: "90% OFF SPECIAL EVENT") }
                                var editAdText by remember(appConfig) { mutableStateOf(appConfig?.adText ?: "Flash Clearance Sale") }
                                var editCarousel by remember(appConfig) { mutableStateOf(appConfig?.carouselEditableContent ?: "Flash Clearance Sale;Summer Electronics Deals;Global Clearance Event;Shop Like a Billionaire") }
                                var editDiscountStr by remember(appConfig) { mutableStateOf(appConfig?.flashSalesDiscount?.toString() ?: "90") }
                                var editSliderImages by remember(appConfig) { mutableStateOf(appConfig?.sliderImages ?: "promo_banner") }
                                var editAlgoEnabled by remember(appConfig) { mutableStateOf(appConfig?.algorithmicPromotionEnabled ?: true) }

                                OutlinedTextField(
                                    value = editPromoText,
                                    onValueChange = { editPromoText = it },
                                    label = { Text("Promo Badge Label", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                )
                                OutlinedTextField(
                                    value = editAdText,
                                    onValueChange = { editAdText = it },
                                    label = { Text("Primary Ad Subtitle", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                )
                                OutlinedTextField(
                                    value = editCarousel,
                                    onValueChange = { editCarousel = it },
                                    label = { Text("Slide Titles (semicolon separated)", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = editDiscountStr,
                                        onValueChange = { editDiscountStr = it },
                                        label = { Text("Discount Label", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                    )
                                    OutlinedTextField(
                                        value = editSliderImages,
                                        onValueChange = { editSliderImages = it },
                                        label = { Text("Banner Image ReferenceKey", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Promotion Coupon Pricing Optimization", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                        Text("Runs internal pricing engine based on sales demand velocity", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Switch(
                                        checked = editAlgoEnabled,
                                        onCheckedChange = { editAlgoEnabled = it },
                                        colors = SwitchDefaults.colors(checkedTrackColor = TemuOrangePrimary)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        val disc = editDiscountStr.toIntOrNull() ?: 90
                                        viewModel.updateAppConfig(
                                            sliderImages = editSliderImages,
                                            promoText = editPromoText,
                                            adText = editAdText,
                                            flashSalesDiscount = disc,
                                            carouselEditableContent = editCarousel,
                                            algoEnabled = editAlgoEnabled
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    Text("Publish Storefront Configuration", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Dynamic Seeding / Promotional Info
            item {
                PromotionalHeroBanner(countdownTime = countdownStr, context = context, appConfig = appConfig)
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
fun PromotionalHeroBanner(countdownTime: String, context: Context, appConfig: AppConfigEntity?) {
    // Elegant layered card of hero banner with custom image reflection
    val imageId = getStoreBannerResourceId(context)

    // Dynamic slideshow titles edited by administrator
    val rawCarousel = appConfig?.carouselEditableContent ?: "Flash Clearance Sale;Summer Electronics Deals;Global Clearance Event;Shop Like a Billionaire"
    val slideList = remember(rawCarousel) {
        rawCarousel.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    }
    
    var currentSlideIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(slideList) {
        if (slideList.size > 1) {
            while (true) {
                delay(3000L)
                currentSlideIdx = (currentSlideIdx + 1) % slideList.size
            }
        }
    }
    
    val currentTitleText = if (slideList.isNotEmpty()) slideList[currentSlideIdx % slideList.size] else "Flash Clearance Sale"
    val promoBadgeText = appConfig?.promoText ?: "90% OFF SPECIAL EVENT"

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
                    promoBadgeText,
                    fontSize = 11.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    currentTitleText,
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

    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val profile by viewModel.activeUserProfile.collectAsStateWithLifecycle()

    var payWithWallet by remember { mutableStateOf(false) }
    var promoCodeInput by remember { mutableStateOf("") }

    val discountPercent = if (promoCodeInput.uppercase().trim() == "TEMUFLASHSALE40") 40 else if (promoCodeInput.uppercase().trim() == "WELCOME50") 20 else 0
    val postCouponTotal = totalAmount * (1.0 - (discountPercent / 100.0))

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
                    if (activeUser == null) {
                        // User is GUEST. Prominent error block
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = AlertRed, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Please sign in or register to complete product checkout.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AlertRed
                                    )
                                }
                                Button(
                                    onClick = { viewModel.selectScreen(ShopScreen.AUTH_SETTINGS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Sign In", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // Logged in user options
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text("Payment Gateway Option:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { payWithWallet = false }
                                ) {
                                    RadioButton(
                                        selected = !payWithWallet,
                                        onClick = { payWithWallet = false },
                                        colors = RadioButtonDefaults.colors(selectedColor = TemuOrangePrimary)
                                    )
                                    Text("Cash On Delivery (COD)", fontSize = 12.sp, color = DarkText)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { payWithWallet = true }
                                ) {
                                    RadioButton(
                                        selected = payWithWallet,
                                        onClick = { payWithWallet = true },
                                        colors = RadioButtonDefaults.colors(selectedColor = TemuOrangePrimary)
                                    )
                                    Text("Temu Pay Secure Wallet", fontSize = 12.sp, color = DarkText)
                                }
                            }

                            if (payWithWallet) {
                                val currentBal = profile?.walletBalance ?: 120.00
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = PositiveGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Secure Wallet Balance: ",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        String.format("$%.2f", currentBal),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentBal >= postCouponTotal) PositiveGreen else AlertRed
                                    )
                                    if (currentBal < postCouponTotal) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("(Shortage - Deposit under Profile)", fontSize = 10.sp, color = AlertRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Coupon Code field
                            OutlinedTextField(
                                value = promoCodeInput,
                                onValueChange = { promoCodeInput = it },
                                placeholder = { Text("Code: e.g. TEMUFLASHSALE40", fontSize = 11.sp, color = Color.Gray) },
                                label = { Text("Have a coupon/promo code?", fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TemuOrangePrimary,
                                    unfocusedBorderColor = Color.LightGray
                                ),
                                singleLine = true
                            )
                            if (discountPercent > 0) {
                                Text(
                                    "✓ Promo applied: $discountPercent% Off savings on checkout total!",
                                    color = PositiveGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

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
                    if (discountPercent > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Coupon Discount:", fontSize = 13.sp, color = AlertRed, fontWeight = FontWeight.Medium)
                            Text(String.format("-$%.2f", totalAmount - postCouponTotal), fontSize = 13.sp, color = AlertRed, fontWeight = FontWeight.Bold)
                        }
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
                                String.format("$%.2f", if (discountPercent > 0) postCouponTotal else totalAmount),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = TemuOrangePrimary
                            )
                        }

                        // Order Checkout button (Accessible 48dp vertical target)
                        Button(
                            onClick = { viewModel.executeCheckout(payWithWallet, promoCodeInput) },
                            enabled = activeUser != null,
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
        val activeTracking by viewModel.activeOrderTracking.collectAsStateWithLifecycle()

        LaunchedEffect(order.orderId) {
            viewModel.fetchLiveOrderTracking(order.orderId)
        }

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

                    val trackingInfo = activeTracking
                    if (trackingInfo == null || trackingInfo.orderId != order.orderId) {
                        CircularProgressIndicator(
                            color = TemuOrangePrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Retrieving live tracking telemetry...", fontSize = 11.sp, color = Color.Gray)
                    } else {
                        // Display the real dynamic checkpoints loaded from the backend
                        trackingInfo.checkpoints.forEach { checkpoint ->
                            TrackingCheckpoint(
                                time = checkpoint.time,
                                status = checkpoint.status,
                                isDone = checkpoint.isDone
                            )
                        }
                    }

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
    viewModel: ShopViewModel,
    onDismiss: () -> Unit,
    onAddToCart: (Int) -> Unit
) {
    var selectedQty by remember { mutableIntStateOf(1) }
    
    val allReviews by viewModel.allReviews.collectAsStateWithLifecycle()
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()

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

                        // --- CUSTOM RATINGS & COMMENTS DISPLAY ---
                        Divider(modifier = Modifier.padding(vertical = 16.dp))
                        Text(
                            "Verified Buyer Reviews",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = DarkText
                        )
                    }
                }

                // Filter database reviews for this product
                val matchingReviews = allReviews.filter { it.productId == product.id }
                if (matchingReviews.isEmpty()) {
                    item {
                        Text(
                            "No user reviews submitted yet. Rate this style below!",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(matchingReviews) { r ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RetailBackground),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        r.userName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkText
                                    )
                                    Row {
                                        repeat(5) { starIdx ->
                                            Icon(
                                                Icons.Filled.Star,
                                                contentDescription = null,
                                                tint = if (starIdx < r.rating) TemuYellowAccent else Color.LightGray,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(r.reviewText, fontSize = 12.sp, color = Color.DarkGray)
                            }
                        }
                    }
                }

                // Review input card for buyers
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (activeUser == null) {
                        Text(
                            "Please sign in to write and share feedback for this item.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        var pendingRating by remember { mutableIntStateOf(5) }
                        var commentTextInput by remember { mutableStateOf("") }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Share Product Experience",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Clearance Rating: ", fontSize = 12.sp, color = Color.Gray)
                                    repeat(5) { starIdx ->
                                        Icon(
                                            Icons.Filled.Star,
                                            contentDescription = "Select rating stars",
                                            tint = if (starIdx < pendingRating) TemuYellowAccent else Color.LightGray,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable { pendingRating = starIdx + 1 }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = commentTextInput,
                                    onValueChange = { commentTextInput = it },
                                    placeholder = { Text("Quality, sizes, delivery time...", fontSize = 11.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TemuOrangePrimary,
                                        unfocusedBorderColor = Color.LightGray
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.submitProductReview(product.id, pendingRating, commentTextInput)
                                        commentTextInput = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TemuOrangePrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Post Feedback", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
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

fun parseHexColor(hexStr: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hexStr))
    } catch (e: Exception) {
        Color(0xFFFF5000) // Fallback default orange color
    }
}

@Composable
fun AuthSettingsScreen(viewModel: ShopViewModel) {
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnectedToBackend.collectAsStateWithLifecycle()
    val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    val profile by viewModel.activeUserProfile.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()

    val brandName by viewModel.currentBrandName.collectAsStateWithLifecycle()
    val brandColorHex by viewModel.currentBrandColorHex.collectAsStateWithLifecycle()
    val launcherName by viewModel.currentLauncherName.collectAsStateWithLifecycle()

    val brandPrimaryColor = parseHexColor(brandColorHex)

    val dynamicBackground = if (isDarkTheme) Color(0xFF121214) else RetailBackground
    val dynamicCardContainer = if (isDarkTheme) Color(0xFF1E1E22) else Color.White
    val dynamicOnBackgroundText = if (isDarkTheme) Color.White else DarkText
    val dynamicSecondaryText = if (isDarkTheme) Color(0xFFB0B3BC) else Color.Gray
    val dynamicBorder = if (isDarkTheme) Color(0xFF2C2D31) else Color.LightGray

    // Auth screen states: "LOGIN", "REGISTER", "VERIFY_OTP", "FORGOT_PASSWORD", "RESET_PASSWORD"
    var authState by remember { mutableStateOf("LOGIN") }

    var urlInput by remember { mutableStateOf(backendUrl) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var resetTokenInput by remember { mutableStateOf("") }
    var newPasswordForResetInput by remember { mutableStateOf("") }
    var adminSignUpTokenInput by remember { mutableStateOf("") }

    // Changing Profile attributes
    var newPasswordInput by remember { mutableStateOf("") }
    var newPhoneInput by remember { mutableStateOf("") }
    var depositAmountInput by remember { mutableStateOf("") }
    var showPaymentGatewayDialog by remember { mutableStateOf(false) }
    var gatewayAmountToDeposit by remember { mutableStateOf(50.0) }
    
    var showDeveloperAccordion by remember { mutableStateOf(false) }

    // Dynamic customization states edited by admin
    var brandNameInput by remember(brandName) { mutableStateOf(brandName) }
    var brandColorHexInput by remember(brandColorHex) { mutableStateOf(brandColorHex) }
    var launcherNameInput by remember(launcherName) { mutableStateOf(launcherName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(dynamicBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (activeUser == null) {
            // --- CLEAN, PREMIUM BRANDED AUTHENTICATION CARD ---
            Card(
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = dynamicCardContainer)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Logo Header
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(brandPrimaryColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    val headerText = when (authState) {
                        "LOGIN" -> "Sign In to $brandName"
                        "REGISTER" -> "Create $brandName Account"
                        "VERIFY_OTP" -> "Verify OTP Confirmation"
                        "FORGOT_PASSWORD" -> "Forgot Secret Password"
                        "RESET_PASSWORD" -> "Reset Credentials"
                        else -> "Sign In"
                    }

                    val subheaderText = when (authState) {
                        "LOGIN" -> "Manage order checkouts, coupons & secure wallet balance"
                        "REGISTER" -> "Unlock up to 90% discount on cart items"
                        "VERIFY_OTP" -> "We have sent a 6-digit confirmation code to your email"
                        "FORGOT_PASSWORD" -> "Request a secure code to reset your account credentials safely"
                        "RESET_PASSWORD" -> "Enter your security token to configure a new password credentials"
                        else -> ""
                    }
                    
                    Text(
                        headerText,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = dynamicOnBackgroundText
                    )
                    Text(
                        subheaderText,
                        fontSize = 11.sp,
                        color = dynamicSecondaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    when (authState) {
                        "LOGIN" -> {
                            // OAuth providers for premium login
                            Text(
                                "CHOOSE OAUTH INBOUND PROVIDERS:",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = dynamicSecondaryText,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        emailInput = "google.oauth@gmail.com"
                                        passwordInput = "oauth123"
                                        viewModel.loginRemote("google.oauth@gmail.com", "oauth123")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = dynamicBackground),
                                    modifier = Modifier.weight(1f).height(40.dp).border(1.dp, dynamicBorder, RoundedCornerShape(8.dp)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Filled.AccountBox, contentDescription = null, tint = brandPrimaryColor, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Google Auth", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = dynamicOnBackgroundText)
                                }
                                Button(
                                    onClick = {
                                        emailInput = "apple.oauth@icloud.com"
                                        passwordInput = "oauth123"
                                        viewModel.loginRemote("apple.oauth@icloud.com", "oauth123")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = dynamicBackground),
                                    modifier = Modifier.weight(1f).height(40.dp).border(1.dp, dynamicBorder, RoundedCornerShape(8.dp)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Filled.Stars, contentDescription = null, tint = dynamicOnBackgroundText, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Apple OAuth", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = dynamicOnBackgroundText)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Email Address") },
                                modifier = Modifier.fillMaxWidth().testTag("auth_email_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandPrimaryColor,
                                    unfocusedBorderColor = dynamicBorder,
                                    focusedLabelColor = brandPrimaryColor,
                                    unfocusedLabelColor = dynamicSecondaryText,
                                    focusedTextColor = dynamicOnBackgroundText,
                                    unfocusedTextColor = dynamicOnBackgroundText
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password credentials") },
                                modifier = Modifier.fillMaxWidth().testTag("auth_password_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandPrimaryColor,
                                    unfocusedBorderColor = dynamicBorder,
                                    focusedLabelColor = brandPrimaryColor,
                                    unfocusedLabelColor = dynamicSecondaryText,
                                    focusedTextColor = dynamicOnBackgroundText,
                                    unfocusedTextColor = dynamicOnBackgroundText
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.loginRemote(emailInput, passwordInput)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_submit_btn")
                            ) {
                                Text("Sign In Securely", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = { authState = "FORGOT_PASSWORD" }) {
                                    Text("Forgot Password?", fontSize = 12.sp, color = brandPrimaryColor, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { authState = "REGISTER" }) {
                                    Text("Join Register Portal", fontSize = 12.sp, color = brandPrimaryColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        "REGISTER" -> {
                            // OAuth builders for signup
                            Text(
                                "REGISTER USING OAUTH PROVIDERS:",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = dynamicSecondaryText,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        emailInput = "google.oauth@gmail.com"
                                        nameInput = "OAuth User"
                                        passwordInput = "oauth123"
                                        viewModel.loginRemote("google.oauth@gmail.com", "oauth123")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = dynamicBackground),
                                    modifier = Modifier.weight(1f).height(40.dp).border(1.dp, dynamicBorder, RoundedCornerShape(8.dp)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Filled.Group, contentDescription = null, tint = brandPrimaryColor, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Google Sign", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = dynamicOnBackgroundText)
                                }
                                Button(
                                    onClick = {
                                        emailInput = "apple.oauth@icloud.com"
                                        nameInput = "OAuth User"
                                        passwordInput = "oauth123"
                                        viewModel.loginRemote("apple.oauth@icloud.com", "oauth123")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = dynamicBackground),
                                    modifier = Modifier.weight(1f).height(40.dp).border(1.dp, dynamicBorder, RoundedCornerShape(8.dp)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Filled.Shield, contentDescription = null, tint = dynamicOnBackgroundText, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Apple Join", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = dynamicOnBackgroundText)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Full Name") },
                                modifier = Modifier.fillMaxWidth().testTag("reg_name_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandPrimaryColor,
                                    unfocusedBorderColor = dynamicBorder,
                                    focusedLabelColor = brandPrimaryColor,
                                    unfocusedLabelColor = dynamicSecondaryText,
                                    focusedTextColor = dynamicOnBackgroundText,
                                    unfocusedTextColor = dynamicOnBackgroundText
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Email Address") },
                                modifier = Modifier.fillMaxWidth().testTag("auth_email_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandPrimaryColor,
                                    unfocusedBorderColor = dynamicBorder,
                                    focusedLabelColor = brandPrimaryColor,
                                    unfocusedLabelColor = dynamicSecondaryText,
                                    focusedTextColor = dynamicOnBackgroundText,
                                    unfocusedTextColor = dynamicOnBackgroundText
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Admin master key
                            val isEmailAdmin = emailInput.lowercase().contains("admin") || emailInput.lowercase().endsWith("@admin.com")
                            if (isEmailAdmin) {
                                OutlinedTextField(
                                    value = adminSignUpTokenInput,
                                    onValueChange = { adminSignUpTokenInput = it },
                                    label = { Text("Admin Master Security Token") },
                                    placeholder = { Text("Required for role authorization") },
                                    modifier = Modifier.fillMaxWidth().testTag("reg_admin_token_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = brandPrimaryColor,
                                        unfocusedBorderColor = dynamicBorder,
                                        focusedLabelColor = brandPrimaryColor,
                                        unfocusedLabelColor = dynamicSecondaryText,
                                        focusedTextColor = dynamicOnBackgroundText,
                                        unfocusedTextColor = dynamicOnBackgroundText
                                    )
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password credentials") },
                                modifier = Modifier.fillMaxWidth().testTag("auth_password_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandPrimaryColor,
                                    unfocusedBorderColor = dynamicBorder,
                                    focusedLabelColor = brandPrimaryColor,
                                    unfocusedLabelColor = dynamicSecondaryText,
                                    focusedTextColor = dynamicOnBackgroundText,
                                    unfocusedTextColor = dynamicOnBackgroundText
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val isMailAdmin = emailInput.lowercase().contains("admin") || emailInput.lowercase().endsWith("@admin.com")
                                    val determinedRole = if (isMailAdmin) "admin" else "user"
                                    viewModel.requestRegisterOtp(
                                        emailStr = emailInput,
                                        passwordStr = passwordInput,
                                        nameStr = nameInput.ifEmpty { "Default Buyer" },
                                        roleStr = determinedRole,
                                        adminToken = if (determinedRole == "admin") adminSignUpTokenInput else null
                                    ) { isSuccess, info ->
                                        if (isSuccess) {
                                            authState = "VERIFY_OTP"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_submit_btn")
                            ) {
                                Text("Send Verification OTP", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { authState = "LOGIN" }) {
                                Text("Already have an account? Sign In", fontSize = 12.sp, color = brandPrimaryColor, fontWeight = FontWeight.Bold)
                            }
                        }

                        "VERIFY_OTP" -> {
                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { otpInput = it },
                                label = { Text("6-Digit OTP Verification Code") },
                                placeholder = { Text("Enter OTP from registration email") },
                                modifier = Modifier.fillMaxWidth().testTag("otp_code_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandPrimaryColor,
                                    unfocusedBorderColor = dynamicBorder,
                                    focusedLabelColor = brandPrimaryColor,
                                    unfocusedLabelColor = dynamicSecondaryText,
                                    focusedTextColor = dynamicOnBackgroundText,
                                    unfocusedTextColor = dynamicOnBackgroundText
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.verifyRegisterOtp(emailInput, otpInput) { success, _ ->
                                        if (success) {
                                            authState = "LOGIN"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Verify & Activate Profile", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { authState = "REGISTER" }) {
                                Text("Back to Registration Details", fontSize = 12.sp, color = brandPrimaryColor)
                            }
                        }

                        "FORGOT_PASSWORD" -> {
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Associated Email Address") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandPrimaryColor,
                                    unfocusedBorderColor = dynamicBorder,
                                    focusedLabelColor = brandPrimaryColor,
                                    unfocusedLabelColor = dynamicSecondaryText,
                                    focusedTextColor = dynamicOnBackgroundText,
                                    unfocusedTextColor = dynamicOnBackgroundText
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.requestForgotPassword(emailInput) { success, _ ->
                                        if (success) {
                                            authState = "RESET_PASSWORD"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Request Reset Token", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { authState = "LOGIN" }) {
                                Text("Back to Sign In", fontSize = 12.sp, color = brandPrimaryColor)
                            }
                        }

                        "RESET_PASSWORD" -> {
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Email Address") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandPrimaryColor,
                                    unfocusedBorderColor = dynamicBorder,
                                    focusedTextColor = dynamicOnBackgroundText
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = resetTokenInput,
                                onValueChange = { resetTokenInput = it },
                                label = { Text("6-Digit Reset Security Token") },
                                placeholder = { Text("Look in SendGrid inbox or terminal logs!") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandPrimaryColor,
                                    unfocusedBorderColor = dynamicBorder,
                                    focusedTextColor = dynamicOnBackgroundText
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = newPasswordForResetInput,
                                onValueChange = { newPasswordForResetInput = it },
                                label = { Text("Configure New Password") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandPrimaryColor,
                                    unfocusedBorderColor = dynamicBorder,
                                    focusedTextColor = dynamicOnBackgroundText
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.resetPasswordWithToken(emailInput, resetTokenInput, newPasswordForResetInput) { success, _ ->
                                        if (success) {
                                            authState = "LOGIN"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Save New Credentials", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { authState = "LOGIN" }) {
                                Text("Discard & Back to Sign In", fontSize = 12.sp, color = brandPrimaryColor)
                            }
                        }
                    }
                }
            }
        } else {
            // --- GORGEOUS INTEGRATED PROFILE DASHBOARD FOR AUTHENTICATED USER ---
            val role = activeUser?.role ?: "user"
            val isUserAdmin = role == "admin"

            Card(
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = dynamicCardContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Profile avatar block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(brandPrimaryColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                activeUser!!.name.take(1).uppercase(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = brandPrimaryColor
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                activeUser!!.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = dynamicOnBackgroundText
                            )
                            Text(
                                activeUser!!.email,
                                fontSize = 12.sp,
                                color = dynamicSecondaryText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Only display ROLE: ADMIN badges if the administrator is active
                            if (isUserAdmin) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(brandPrimaryColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "ROLE: ADMIN",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = brandPrimaryColor
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.background(Color.Red.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Logout, contentDescription = "Log Out", tint = Color.Red)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 16.dp), color = dynamicBorder.copy(alpha = 0.5f))

                    // --- REAL-TIME WALLET & BALANCE CARD INTEGRATION ---
                    Text(
                        "$brandName Secure Checkout Wallet",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = dynamicOnBackgroundText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = brandPrimaryColor.copy(alpha = 0.05f)),
                        border = BorderStroke(1.dp, brandPrimaryColor.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Current Wallet Balance", fontSize = 11.sp, color = dynamicSecondaryText)
                                    Text(
                                        String.format("$%.2f", profile?.walletBalance ?: 120.00),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        color = brandPrimaryColor
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PositiveGreen.copy(alpha = 0.1f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("90% Clearance Protected", fontSize = 9.sp, color = PositiveGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Top up UI block with validation
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = depositAmountInput,
                                    onValueChange = { depositAmountInput = it },
                                    placeholder = { Text("Deposit Amount", fontSize = 12.sp, color = dynamicSecondaryText) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = dynamicOnBackgroundText),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PositiveGreen,
                                        unfocusedBorderColor = dynamicBorder,
                                        focusedTextColor = dynamicOnBackgroundText,
                                        unfocusedTextColor = dynamicOnBackgroundText
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val amt = depositAmountInput.toDoubleOrNull() ?: 50.0
                                        gatewayAmountToDeposit = amt
                                        showPaymentGatewayDialog = true
                                        depositAmountInput = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PositiveGreen),
                                    modifier = Modifier.height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Credits", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- ACTIVE PROMO/COUPONS PORTAL ---
                    Text(
                        "Active Coupons & Promotions",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = dynamicOnBackgroundText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(brandPrimaryColor.copy(alpha = 0.08f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("WELCOM50", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = brandPrimaryColor)
                                Text("20% Off Flat Discount", fontSize = 9.sp, color = dynamicSecondaryText)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(brandPrimaryColor.copy(alpha = 0.08f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("${brandName.uppercase()}FLASH40", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = brandPrimaryColor)
                                Text("40% Clearance Promo", fontSize = 9.sp, color = dynamicSecondaryText)
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 16.dp), color = dynamicBorder.copy(alpha = 0.5f))

                    // --- MANAGE PASSWORD & MOBILE PROFILE ---
                    Text(
                        "Update Personal Credentials",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = dynamicOnBackgroundText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("Change Password", color = dynamicSecondaryText) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = brandPrimaryColor,
                            unfocusedBorderColor = dynamicBorder,
                            focusedTextColor = dynamicOnBackgroundText,
                            unfocusedTextColor = dynamicOnBackgroundText
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            if (newPasswordInput.isNotEmpty()) {
                                viewModel.changePassword(newPasswordInput)
                                newPasswordInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor),
                        modifier = Modifier.align(Alignment.End).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                    ) {
                        Text("Save Password", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newPhoneInput,
                        onValueChange = { newPhoneInput = it },
                        placeholder = { Text(profile?.phoneNumber ?: "+1-555-019-2831", color = dynamicSecondaryText) },
                        label = { Text("Change Phone Number", color = dynamicSecondaryText) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = brandPrimaryColor,
                            unfocusedBorderColor = dynamicBorder,
                            focusedTextColor = dynamicOnBackgroundText,
                            unfocusedTextColor = dynamicOnBackgroundText
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            if (newPhoneInput.isNotEmpty()) {
                                viewModel.changePhoneNumber(newPhoneInput)
                                newPhoneInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor),
                        modifier = Modifier.align(Alignment.End).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                    ) {
                        Text("Save Phone", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // Only display admin configurations if the logged-in administrator is active!
            if (isUserAdmin) {
                Spacer(modifier = Modifier.height(16.dp))

                // --- DYNAMIC ADAPTIVE BRAND BUILDER & LAUNCHER SYSTEM CARD ---
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = dynamicCardContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ColorLens, contentDescription = null, tint = brandPrimaryColor, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Adaptive Branding & Launcher Hub",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = dynamicOnBackgroundText
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Rebrand your merchant storefront environment instantly. Changes reflect immediately across headings, launch titles, and accents.",
                            fontSize = 11.sp,
                            color = dynamicSecondaryText
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = brandNameInput,
                            onValueChange = { brandNameInput = it },
                            label = { Text("Shop Brand Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = brandPrimaryColor,
                                unfocusedBorderColor = dynamicBorder,
                                focusedTextColor = dynamicOnBackgroundText
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = brandColorHexInput,
                                onValueChange = { brandColorHexInput = it },
                                label = { Text("Brand Accent Color Hex") },
                                placeholder = { Text("#FFFF5000") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandPrimaryColor,
                                    unfocusedBorderColor = dynamicBorder,
                                    focusedTextColor = dynamicOnBackgroundText
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            // Swatch preview box
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(parseHexColor(brandColorHexInput))
                                    .border(1.dp, dynamicBorder, RoundedCornerShape(8.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = launcherNameInput,
                            onValueChange = { launcherNameInput = it },
                            label = { Text("App Launcher Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = brandPrimaryColor,
                                unfocusedBorderColor = dynamicBorder,
                                focusedTextColor = dynamicOnBackgroundText
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                viewModel.saveCustomBranding(
                                    brandName = brandNameInput,
                                    brandColorHex = brandColorHexInput,
                                    launcherName = launcherNameInput
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Apply Store Branding Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- GLOBAL COLOR MODE PREFERENCE CARD ---
        Card(
            modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = dynamicCardContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = null,
                            tint = brandPrimaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Application Theme Settings", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dynamicOnBackgroundText)
                            Text("Toggle between light and dark backgrounds", fontSize = 10.sp, color = dynamicSecondaryText)
                        }
                    }
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { viewModel.toggleDarkMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = brandPrimaryColor,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = dynamicBorder
                        )
                    )
                }
            }
        }

        // Only show developer sync configs for Admins
        val role = activeUser?.role ?: "guest"
        if (role == "admin") {
            Spacer(modifier = Modifier.height(16.dp))

            // --- COLLAPSIBLE DEVELOPMENT / SYNC ACCORDION SHEET (No visual clutter!) ---
            Card(
                modifier = Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = dynamicCardContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeveloperAccordion = !showDeveloperAccordion },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = dynamicSecondaryText, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Developer & Connection Settings",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = dynamicSecondaryText
                            )
                        }
                        Icon(
                            if (showDeveloperAccordion) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Expand collapsible parameters",
                            tint = dynamicSecondaryText
                        )
                    }

                    if (showDeveloperAccordion) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Sync Connection Status: ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = dynamicOnBackgroundText
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(if (isConnected) Color(0xFFE8F5E9) else Color(0xFFECEFF1))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (isConnected) "LIVE CHANNEL SYNCED" else "OFFLINE LOCAL DATABASE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConnected) Color(0xFF2E7D32) else Color(0xFF37474F)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("Express Server Endpoint Url") },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = dynamicOnBackgroundText),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = dynamicOnBackgroundText,
                                unfocusedTextColor = dynamicOnBackgroundText
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.updateBackendUrl(urlInput) },
                                colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Save Endpoint", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    urlInput = "http://10.0.2.2:3000"
                                    viewModel.updateBackendUrl("http://10.0.2.2:3000")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = brandPrimaryColor)
                            ) {
                                Text("Android Localhost", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPaymentGatewayDialog) {
        var cardNumber by remember { mutableStateOf("") }
        var cardExpiry by remember { mutableStateOf("") }
        var cardCvc by remember { mutableStateOf("") }
        var selectedGateway by remember { mutableStateOf("Stripe") }
        var isProcessingPayment by remember { mutableStateOf(false) }
        var paymentStatusMessage by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { if (!isProcessingPayment) showPaymentGatewayDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().shadow(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Secure Gateway checkout",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = DarkText
                        )
                        IconButton(
                            onClick = { showPaymentGatewayDialog = false },
                            enabled = !isProcessingPayment
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    
                    Divider(color = Color(0xFFF1F1F1), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Gateway options
                    Text(
                        "CHOOSE GATEWAY CHANNELS:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val gateways = listOf("Stripe", "PayPal", "Paystack", "Flutterwave", "Razorpay")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gateways.forEach { gw ->
                            val isChosen = selectedGateway == gw
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isChosen) TemuOrangePrimary.copy(alpha = 0.12f) else Color(0xFFF5F5F5))
                                    .border(1.dp, if (isChosen) TemuOrangePrimary else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { selectedGateway = gw }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    gw,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isChosen) TemuOrangePrimary else Color.DarkGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Card preview decoration
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF2C3E50), Color(0xFF3498DB))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("SECURE COURIER CARD", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Filled.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                cardNumber.ifEmpty { "••••  ••••  ••••  ••••" },
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("EXPIRY", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp)
                                    Text(cardExpiry.ifEmpty { "MM/YY" }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("CVC/CVV", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp)
                                    Text(cardCvc.ifEmpty { "•••" }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { if (it.length <= 16) cardNumber = it.filter { char -> char.isDigit() } },
                        label = { Text("16-Digit Card Number") },
                        placeholder = { Text("4000 1234 5678 9010") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TemuOrangePrimary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = cardExpiry,
                            onValueChange = { cardExpiry = it },
                            label = { Text("Expiry (MM/YY)") },
                            placeholder = { Text("12/28") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TemuOrangePrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = cardCvc,
                            onValueChange = { if (it.length <= 4) cardCvc = it.filter { char -> char.isDigit() } },
                            label = { Text("CVV/CVC") },
                            placeholder = { Text("123") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TemuOrangePrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isProcessingPayment) {
                        CircularProgressIndicator(color = TemuOrangePrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Resolving deposit with $selectedGateway gateway...", fontSize = 11.sp, color = Color.Gray)
                    } else {
                        Button(
                            onClick = {
                                if (cardNumber.length < 16) {
                                    paymentStatusMessage = "Invalid card credentials. Must input a 16-digit card number"
                                    return@Button
                                }
                                isProcessingPayment = true
                                viewModel.depositToWalletSecure(
                                    amount = gatewayAmountToDeposit,
                                    cardNumber = cardNumber,
                                    expiry = cardExpiry,
                                    cvc = cardCvc,
                                    gateway = selectedGateway
                                ) { success, msg ->
                                    isProcessingPayment = false
                                    if (success) {
                                        showPaymentGatewayDialog = false
                                    } else {
                                        paymentStatusMessage = msg
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PositiveGreen),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Filled.OfflinePin, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pay $${String.format("%.2f", gatewayAmountToDeposit)} securely", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    paymentStatusMessage?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(err, color = AlertRed, fontSize = 11.sp, textAlign = TextAlign.Center)
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

