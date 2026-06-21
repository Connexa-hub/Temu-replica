<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/c9c9c175-82e1-4130-b377-4061290880c2

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device

---

## Backend (`backend-v2/`)

The original `backend/` (single-file, mock payments, plaintext passwords) has been
superseded by `backend-v2/`, a modular Node.js/Express + MongoDB backend with real
authentication (bcrypt + JWT), a real wallet ledger, and live integrations with Monnify,
Paystack, and Flutterwave (hosted checkout + signed webhook verification — no raw card
data ever touches this server).

See [`backend-v2/design.md`](./backend-v2/design.md) for full architecture, what's done,
what's left, and staged prompts for continuing the Android frontend build in Google AI
Studio.

**Quick start:**
```bash
cd backend-v2
npm install
cp .env.example .env   # fill in real values — see design.md for what each one is for
npm run seed            # creates the first superadmin + sample products
npm start
```

The old `backend/` directory is left in place for reference during the migration but
should not be deployed — it contains the security issues described in `design.md` §2.

