# Temu Replica Real-Time Hub Deployment & Setup Guide

This guide details the architecture, auth flow, backend deployment, and live-support administrative panel usage of your custom **Temu Replica Solution**. With beautifulMaterial 3 designs, offline room persistence, and dynamic network pooling, this app is fully production-grade.

---

## 🚀 1. Architecture Overview
The system is divided into two parts within the repository:
1.  **Android Client Application**: Powered by **Kotlin Jetpack Compose** & **Room DB Sync Flow** for mobile. It manages product displays, category filtering, persistent user checkout carts, order history logs, and instant customer-care chat rooms.
2.  **Live Express Server Backend**: Located in `/backend/`, it is a **Node.js Express REST API Container** that manages real-time chat message relays, secure multi-role auth routes (`/api/auth/login`, `/api/auth/register`), and dynamic product inventory manipulations.

---

## 🛠️ 2. Rapid Backend Setup & Local Run

To launch your backend server locally or within your development cloud terminal:

### Prerequisites:
Make sure you have [Node.js](https://nodejs.org) (v16+) installed.

### Installation Steps:
1.  Change directory to the backend workspace folder:
    ```bash
    cd backend
    ```
2.  Install required JSON web token, security, and express dependencies:
    ```bash
    npm install
    ```
3.  Boot the live backend API server:
    ```bash
    npm start
    ```
4.  The terminal will print:
    ```text
    ==================================================
    Temu Real-Time Sync Backend Running on port 3000
    Default Admin: admin@temu.com | password: admin123
    Default Buyer: user@temu.com | password: user123
    ==================================================
    ```

---

## 🔑 3. Dynamic Authentication Flow
The Temu app replica implements a custom **Hybrid Multi-Role Auth Architecture**:

-   **Role Profiles Available**:
    *   **Customer / Buyer (`user`)**: Allows standard product selection, adding items with responsive badges, live checkout placing, and access to a **Live Chat Support Terminal** to negotiate live discounts with merchants.
    *   **Administrator / Merchant (`admin`)**: Restricts typical purchasing, opening a specialized **Administrator merchant Control Hub** where they can respond to all consumer live-tickets and raise/deduct catalog stock dynamically.

### Dev-Friendly AutoFill Shortcuts:
To bypass typing long passwords in Android Emulators, the **Sync & Account Tab** includes dynamic prefilled quick buttons:
-   **Click `[Jack Buyer (Client)]`**: Instantly authenticates you as `user@temu.com` / `user123`, unlocking the consumer account.
-   **Click `[Admin Mode (Merchant)]`**: Instantly authenticates you as `admin@temu.com` / `admin123`, unlocking the Merchant Admin Deck.

---

## 🌐 4. Connecting the Android App to Backend
The Android app is designed for **Zero-Config Resiliency**:
1.  **Fully Offline-First**: If the backend is down, the app automatically runs in **Room-DB Offline Capability mode**, simulating orders, inventory deductions, and automatic bot chat support answers.
2.  **To Engage Live Sync**:
    *   Go to the bottom-right tab (**Sync / Tune** tab).
    *   Find the **Express Server Endpoint URL** field.
    *   Enter your target address (default is prefilled to `http://10.0.2.2:3000` which maps successfully to your local computer's `localhost` from within the Android Emulator).
    *   Click **Connect**.
    *   Upon connection, the sync light triggers to **`[LIVE CHANNEL SYNCED]`** (Green), dynamically syncing catalogs and messages between your device and server databases!

---

## 💬 5. Real-Time Chat & Operations
Everything is interconnected:
-   **When a buyer messages support**: The ticket is relayed instantly to the backend memory arrays.
-   **Merchant panel updates**: When an Admin selects that user under **Consumer Threads** and answers, the message relays back instantly.
-   **Restock & Delete**: Admins can restock products (`+10 units` at a click) or delete items on the **Store Control Room** tab. These immediately reflect on the consumer's front-page **Store storefront catalog** within seconds!
