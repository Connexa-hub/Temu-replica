# Replica Storefront Real-Time Solution: Ultimate Setup & Deployment Guide

This comprehensive guide details the architecture, auth flow, backend deployment to **Render**, MongoDB integration, and **custom brand customization** of your e-commerce application. Crafted with **Jetpack Compose (Kotlin)**, **Node.js Express (Backend)**, and **MongoDB (Mongoose)**, this marketplace is ready for production.

---

## 🚀 1. System Architecture

The repository is organized into two highly decoupled components:
1.  **Android Client Application** (Root Folder + `/app`): Built with **Kotlin Jetpack Compose** conforming to Material Design 3 guidelines. It features persistent SQL caching via **Room**, secure multi-role logins, automated shopping cart logic, and live order history.
2.  **Secured Node.js Express Container** (`/backend/`): A production-hardened REST API featuring **Helmet.js** HTTP header shields, API rate-limiting, and deep JSON validation schemas. It natively handles authentication, live order status tracking, user configuration, and multi-gateway payment processing.

---

## 🎨 2. Easy Brand Customization (Android Frontend)

Changing the name of the application, promotional coupons, buttons, and theme color accents across the entire Kotlin codebase has been centralized.

### Step-by-Step Brand Customization:
1. Open `/app/src/main/java/com/example/data/BrandConfig.kt`
2. Edit the variables inside the `BrandConfig` object:
   ```kotlin
   object BrandConfig {
       // 1. Change the application name here (e.g. "Orbit", "Luxe", "Vogue")
       const val BRAND_NAME = "Temu"      
       const val APP_TITLE = "$BRAND_NAME Shop"
       
       // 2. Modify dynamic text labels
       const val LABEL_SECURE_PAY = "$BRAND_NAME Pay Secure Wallet"
       const val LABEL_WALLET = "$BRAND_NAME Secure Checkout Wallet"
       const val LABEL_ORDERS = "My $BRAND_NAME Orders"
       const val LABEL_SIGN_IN = "Sign In to $BRAND_NAME"
       const val LABEL_CREATE_ACCOUNT = "Create $BRAND_NAME Buyer Account"
       
       // 3. Centralize dynamic promotional codes
       const val COUPON_WELCOME = "WELCOME50"             // 20% Off Coupon
       const val COUPON_FLASHSALE = "TEMUFLASHSALE40"     // 40% Off Coupon
       
       // 4. Change the primary logo color and accent rating colors in one click!
       val BrandPrimaryColor = Color(0xFFFF5000)   // Modify to change theme accent color
       val BrandYellowAccent = Color(0xFFFFC107)   // Ratings and secondary highlights
   }
   ```
3. To customize the App Name (Launcher under icon) on the phone's home screen, update `/app/src/main/res/values/strings.xml`:
   ```xml
   <resources>
       <string name="app_name">Temu Shop</string> <!-- Change to your custom brand name! -->
   </resources>
   ```

---

## 🔑 3. Advanced Security Controls & Admin Safeguards

### Admin Sign-Up Token Prevention
To prevent rogue users from signing up as administrators using `.admin.com` dummy emails or using merchant payloads, the system enforces a master secret key.
- The administrator registration endpoint `/api/auth/register` requires the **`adminToken`** parameter.
- The server compares this parameter with the secure **`ADMIN_SIGNUP_TOKEN`** environment variable stored on your production host (or local `.env`).
- If they match, they are successfully granted a merchant account. If they mismatch or are empty, the backend rejects the transaction with code `403 Forbidden`.

To sign up as an admin, typing an email containing `admin` will dynamically show the **Admin Master Security Token** input field in the signup wizard.

---

## 💳 4. Secure Virtual Wallet & Gateway Integration

Our state-of-the-art billing system enforces **strict server-side checks**:
- **Zero Hacking Space**: The database profile and wallet balance can **only** be modified by successful server authorized payment transactions.
- **Top-up Gateways**: Clicking "Add Credits" triggers our **Secure Gateway Checkout Dialog**, letting users select from **Stripe**, **PayPal**, **Paystack**, **Flutterwave**, or **Razorpay**.
- **Simulated Debit Card Engine**: Displays an interactive card graphic displaying card details as you type, enforcing a validation check prior to secure gateway resolution.

---

## 📦 5. Real-Time Tracking API

When selecting "Track Order" on the orders sheet:
- The client dispatches a request to `/api/orders/:id/tracking`, executing live, dynamic carrier checkpoints matching the lifecycle of that specific purchase:
  1. *Checked & Verified* (Sorting Center, Order Logging)
  2. *Customs clearance approved* (Processed at dispatch depot)
  3. *In Courier Transit* (Near final location)
- A dynamic loading spinner retrieves live database status, showing true telemetry coordinates on a smooth chronological timeline.

---

## ☁️ 6. How to Deploy the Backend to Render

[Render](https://render.com) is a premier platform for hosting Node.js applications with zero hassle. Follow these steps to host your backend live in the cloud:

### Step A: Push backend to your Git Repository
Ensure your repository has the `/backend` folder. Alternatively, you can slice the `/backend` folder to host as a stand-alone GitHub repository.

### Step B: Create a Web Service on Render
1. Sign in to your **Render Dashboard** and select **New +** -> **Web Service**.
2. Connect your GitHub or GitLab account and authorize access to your repository.
3. Choose your repository and configure the service as follows:
   - **Name**: `shop-backend` (or any custom prefix)
   - **Runtime**: `Node`
   - **Build Command**: `cd backend && npm install` (or just `npm install` if deploying from root)
   - **Start Command**: `cd backend && npm start` (or `node server.js` depending on root)

### Step C: Configure the Production Environment Variables
Click on the **Environment** tab inside your Render Web Service settings page and set your custom secrets:

| Variable Name | Example Value | Description |
| :--- | :--- | :--- |
| `PORT` | `10000` | (Managed automatically by Render, do not force unless needed) |
| `ADMIN_SIGNUP_TOKEN` | `MySecretSuperToken2026` | Safe token required for admin role registration |
| `MONGO_URI` | `mongodb+srv://user:pass@cluster.mongodb.net/shop?retryWrites=true` | MongoDB connection URL |
| `STRIPE_SECRET_KEY` | `sk_live_stripe_secret...` | Stripe API credentials (simulated/active) |
| `PAYPAL_CLIENT_SECRET`| `paypal_live_secret...` | PayPal processing client credentials |
| `PAYSTACK_SECRET_KEY`| `sk_paystack_live...` | Paystack merchant clearance keys |
| `FLUTTERWAVE_SECRET_KEY` | `FLWSECK-...` | Flutterwave merchant checkout key |

*If `MONGO_URI` is left blank, the server will gracefully initialize are safe, robust file-based database database (`db.json`) in server storage.*

---

## 📱 7. How to Build the Android Frontend App

The client app is prepared for local execution or release compilation:

### Prerequisites
1. Install [Android Studio Koala](https://developer.android.com/studio) or newer.
2. Install [JDK 17](https://www.oracle.com/java/technologies/downloads/).

### Running local emulator
1. Open the project root folder. Gradle will automatically sync dependencies.
2. Build and install inside your connected virtual device or physical phone.
3. Navigate to **Sync / Settings** tab in the app.
4. Input your custom backend address (locally `http://10.0.2.2:3000` or the **Render Live Web Service URL** e.g., `https://shop-backend.onrender.com`).
5. Click **Connect** to transition into immediate production live synchronization!

### Building Release APK
To compile a standalone release bundle or executable APK for user installation:
Open your console terminal at the root and compile using Gradle:
```bash
gradle assembleRelease
```
The output executable package will be available in `/app/build/outputs/apk/release/app-release-unsigned.apk`.
