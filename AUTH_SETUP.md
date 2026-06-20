# Authentication & Environment Setup Guide

This guide explains how to properly configure the environment variables and authentication systems for both the Android app (Frontend) and the Node.js API (Backend).

## 1. Backend Setup

The backend handles the core authentication, OTP registration, and database processing. It is located in the `backend/` directory.

### Step 1.1: Install Dependencies
Open your terminal, navigate to the `backend` folder, and install the required NPM packages.
```bash
cd backend
npm install
```

### Step 1.2: Configure `.env`
In the `backend/` directory, duplicate the `.env.example` file and rename the copy to `.env`. This is where all sensitive variables are stored securely.

```bash
cp .env.example .env
```

**Key Environment Variables Explained:**

* **`PORT`**: The port to run the Node.js server (Default: `3000`).
* **`MONGO_URI`**: The connection string for your MongoDB cluster. If this is left blank, the system will automatically fall back to using a secure local JSON file (`db.json`) for data persistence.
* **`ADMIN_SIGNUP_TOKEN`**: A secure string token used to authorize Administrator registration. Any user attempting to sign up with the `admin` role must supply this token, otherwise the system will demote them or reject the action.

**Email Delivery (Crucial for OTP / Password Reset):**
* **`SENDGRID_API_KEY`**: Provide a valid SendGrid key to enable real email delivery for the Registration OTP and Password Reset.
* **`SENDGRID_SENDER_EMAIL`**: A registered sender identity on SendGrid.
*(Note: If `SENDGRID_API_KEY` is not present, the server runs in local debug mode and will print outgoing emails—including OTP codes—directly into your backend terminal console.)*

### Step 1.3: Start the Backend Server
Once your `.env` file is ready, start the server:

```bash
node server.js
```

Ensure it prints that the server is running on port 3000.

---

## 2. Frontend (Android) Setup

The Android frontend is built with Jetpack Compose and connects to your backend using Retrofit.

### Step 2.1: Frontend Environment Variables
If you need any API keys inside your Kotlin code (such as Gemini Key or Google API keys), define them in a root-level `.env` file (e.g. `/.env`). AI Studio handles injection based on this file. 
For backend connection, however, the configuration is modified seamlessly inside the app, without recompiling.

### Step 2.2: Pointing the App to the Backend
By default, the Android App is configured to talk to your local backend.

1. Open the Android application.
2. Under the "Login" interface, press the **Gear (Settings)** icon in the top right to access **System Preferences**.
3. In the **Connection URL** setting:
   * **If running on an Android Emulator inside the same machine**: Leave it at `http://10.0.2.2:3000` (which is the emulator's alias for the localhost of your machine).
   * **If running on a physically connected phone OR browser preview**: Change the string to reflect the IP Address of your local machine on the WiFi Network. Example: `http://192.168.1.15:3000`.
4. The status indicator on this page should change from **Yellow (Local)** to **Green (Synced with Cloud Backend)** if it can reach the backend.

---

## 3. Testing the Login Flow

Once the backend is active, follow this test checklist:

1. **Test Standard Sign Up (User)**:
   * Navigate to Sign Up. Fill in dummy user details. Ensure role is `User`.
   * Submit and verify that the backend triggers the OTP challenge screen.
   * If `SENDGRID_API_KEY` is not configured, check your Node JS server terminal to find the 6-digit OTP code in the "Mock Email logs".
   * Enter the code back in the app to authenticate.

2. **Test Admin Sign Up**:
   * Attempt to sign up an Administrator by filling the form and selecting the `admin` role checkbox or providing the Admin validation input.
   * Check that it requires the same string value defined in your backend variables.

3. **Forgot Password**:
   * In the Sign-In interface, trigger a Forgot Password dispatch. Check your inbox or Node terminal for the 6-digit recovery code. Apply a new password and try logging in.

With these configurations locked in place, both the Android and Backend layers share a perfectly synchronized authentication layer bridging REST, Email Verification, and offline database continuity.
