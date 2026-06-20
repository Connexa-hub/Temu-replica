package com.example.data

import androidx.compose.ui.graphics.Color

/**
 * Global Branding Customization Engine
 * Redefine these parameters to change the entire application name, layout labels, coupons, and accent theme colors.
 */
object BrandConfig {
    // 1. BRAND PARAMS
    const val BRAND_NAME = "Temu"      // CHANGER LABEL: Edit this value (e.g. "Orbit", "Luxe", "Vogue")
    const val APP_TITLE = "$BRAND_NAME Shop"
    
    // 2. TEXT LABELS
    const val LABEL_SECURE_PAY = "$BRAND_NAME Pay Secure Wallet"
    const val LABEL_WALLET = "$BRAND_NAME Secure Checkout Wallet"
    const val LABEL_ORDERS = "My $BRAND_NAME Orders"
    const val LABEL_SIGN_IN = "Sign In to $BRAND_NAME"
    const val LABEL_CREATE_ACCOUNT = "Create $BRAND_NAME Buyer Account"
    
    // 3. PROMOTIONAL COUPONS
    const val COUPON_WELCOME = "WELCOME50"             // 20% Off
    const val COUPON_FLASHSALE = "TEMUFLASHSALE40"     // 40% Off
    
    // 4. THEMATIC INTEGRATION COLORS
    val BrandPrimaryColor = Color(0xFFFF5000)   // Classical storefront signature orange. Set to any color!
    val BrandYellowAccent = Color(0xFFFFC107)   // Golden star ratings and countdown badges
}
