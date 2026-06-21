package com.example.data

import androidx.compose.ui.graphics.Color

/**
 * Global Branding Customization Engine
 * Redefine these parameters to change the entire application name, layout labels, coupons, and accent theme colors.
 */
object BrandConfig {
    // 1. BRAND PARAMS
    const val BRAND_NAME = "MarketEdge Pro"      // CHANGER LABEL: Edit this value (e.g. "Orbit", "Luxe", "Vogue")
    const val APP_TITLE = BRAND_NAME
    
    // 2. TEXT LABELS
    const val LABEL_SECURE_PAY = "$BRAND_NAME Secure Gateway"
    const val LABEL_WALLET = "$BRAND_NAME Business Wallet"
    const val LABEL_ORDERS = "Manage My Orders"
    const val LABEL_SIGN_IN = "Authenticate Profile"
    const val LABEL_CREATE_ACCOUNT = "Establish New Profile"
    
    // 3. PROMOTIONAL COUPONS
    const val COUPON_WELCOME = "PRO_WELCOME_50"             // 50% Off
    const val COUPON_FLASHSALE = "MARKET_EDGE_FLASH"     // 40% Off
    
    // 4. THEMATIC INTEGRATION COLORS
    val BrandPrimaryColor = Color(0xFF1A73E8)   // Modern Enterprise Blue
    val BrandYellowAccent = Color(0xFFFBBC04)   // Professional Gold Accent
}
