# Connexa Storefront — Design Document

**Status:** Backend foundation rebuilt (Phase 1 complete). Frontend (Android/AI Studio) integration and remaining backend modules pending.
**Last updated:** 2026-06-21

---

## 1. What this document is

This describes the rebuilt backend architecture for the Connexa storefront (originally
`Temu-replica`), why it was rebuilt this way, what's done, what's left, and how to pick the
work back up in a future session — including in Google AI Studio for the Android frontend.

---

## 2. Why a rebuild instead of patching the original

An audit of the original repo (`backend/server.js`, 1594 lines, single file) found three
issues serious enough that incremental patching wasn't a responsible path forward:

1. **Plaintext password storage.** Login compared `user.password === password` directly —
   no hashing at all. Anyone with read access to the database (or a backup, or a logging
   leak) would have every user's password in cleartext.
2. **Forgeable auth tokens.** "JWT" tokens were literally the string
   `'JWT_SECURE_TOKEN_FOR_' + role`, not signed tokens. Any client could mint
   `JWT_SECURE_TOKEN_FOR_ADMIN_anything` and get full admin access to every endpoint,
   including the wallet and app-config admin routes.
3. **Raw card numbers handled server-side.** `/api/payment/deposit` accepted `cardNumber`,
   `cardExpiry`, `cardCvc` directly in the request body, logged the card number to the
   console, and then *always* approved the payment (`paymentAuthorized = true` was
   hardcoded). This is both a fabricated payment flow and a PCI-DSS liability — handling
   raw PANs without being a PCI Level 1 certified processor is the kind of thing that gets
   apps removed from app stores and creates real legal exposure.

Given the request was explicitly "nothing should be mock," patching around these issues
piecemeal inside a single 1600-line file would have been slower and riskier than rebuilding
the backend as a properly modularized service with real security primitives from the start.

---

## 3. Architecture overview

```
backend-v2/
├── src/
│   ├── config/database.js          MongoDB connection (fails loudly if unreachable — no mock fallback)
│   ├── models/                     Mongoose schemas (User, Wallet, WalletTransaction, Product,
│   │                                Order, Review, Banner, ThemeConfig, Otp)
│   ├── middleware/
│   │   ├── auth.js                 requireAuth / requireRole / optionalAuth — real JWT verification
│   │   ├── geo.js                  IP-based country detection
│   │   └── validate.js             Joi request validation
│   ├── services/
│   │   ├── walletService.js        Sole authority over wallet balance changes (atomic, idempotent)
│   │   ├── paymentService.js       Unifies the 3 gateways behind one interface
│   │   ├── gateways/               monnifyGateway.js, paystackGateway.js, flutterwaveGateway.js
│   │   ├── cardBrandService.js     BIN-prefix card brand detection (Visa/Mastercard/Verve visuals)
│   │   ├── rankingAlgorithm.js     Weighted scoring for homepage carousel ordering
│   │   ├── uploadService.js        Multer + Sharp image processing for banner/logo uploads
│   │   └── emailService.js         Mailjet OTP delivery
│   ├── routes/                     authRoutes, productRoutes, orderRoutes, paymentRoutes,
│   │                                adminRoutes, utilityRoutes
│   └── server.js                   Express app wiring, webhook raw-body handling, startup guards
└── .env.example                    Every secret the app needs, documented, with sandbox URLs
```

### Why this structure

Each concern (auth, payments, wallet, theme, banners) lives in its own file so a future
session — yours or another Claude session — can open exactly the file relevant to the task
at hand instead of scrolling through a multi-thousand-line monolith. This was also true of
the original Android UI layer (`ShopAppShell.kt` at ~4,855 lines), which is flagged in
Section 7 as a refactor target for the same reason.

---

## 4. Core systems, in detail

### 4.1 Authentication

- Passwords hashed with **bcrypt**, 12 salt rounds.
- **Real JWTs** (HS256), separate access (short-lived, 15 min default) and refresh
  (30 days default) secrets.
- Refresh tokens carry a `tokenVersion` claim checked against the user record — bumping
  `refreshTokenVersion` (on password reset, logout-all, or admin role change) instantly
  invalidates every outstanding refresh token for that user, without needing a token
  blocklist.
- OTP-gated registration: a 6-digit code, **hashed with bcrypt before storage** (so even a
  database leak doesn't expose live codes), with a TTL index so expired OTPs are
  auto-purged by MongoDB itself.
- Login/registration responses are enumeration-resistant (same response shape whether or
  not an account exists).
- IP-based country detection (`geoip-lite`) runs on every register/login, populating
  `country` and `preferredCurrency` on the user record — this is the "country-based login"
  requirement. It degrades gracefully (returns `null` fields) rather than blocking the
  request when geolocation fails, e.g. on localhost or for IPs not in the database.

**Known limitation:** `geoip-lite` ships a static, periodically-stale IP database bundled
with the npm package. It's free and adequate for an MVP, but for production-grade accuracy
consider a paid IP geolocation API (ipinfo.io, MaxMind GeoIP2) as a drop-in replacement —
the `detectCountry` middleware is the only place that would need to change.

### 4.2 Wallet & payments

The wallet is an **append-only ledger** (`WalletTransaction`), not a single mutable number.
`Wallet.balance` is a cached, always-derived-from-the-ledger figure, and it is **only ever
changed by `walletService.js`** — no route handler touches `wallet.balance` directly except
through that service's atomic, transactional functions. This is what "nothing should be
mock" actually requires for money: every credit and debit is traceable, idempotent, and
survives a server crash mid-operation without corrupting the balance (MongoDB sessions /
transactions are used throughout).

**Payment flow (Monnify, Paystack, Flutterwave — all three follow the same shape):**

1. Client calls `POST /api/payments/deposit/start` with `{ amount, gateway }`.
2. Server creates a `PENDING` `WalletTransaction` with a unique internal reference, then
   calls the chosen gateway's "initialize transaction" endpoint.
3. Server returns a **hosted checkout URL**. The customer enters their card number on the
   *gateway's* page — never on ours. This is the same pattern Stripe, Paystack, and
   Flutterwave all recommend, and it's what keeps the platform out of PCI-DSS scope.
4. The gateway sends a **signed webhook** (`POST /api/payments/webhooks/{gateway}`) when
   the payment completes. Each gateway's signature is verified before any processing:
   - Monnify & Paystack: HMAC-SHA512 of the raw request body.
   - Flutterwave: a dashboard-configured secret hash compared directly (`verif-hash`
     header) — not HMAC, by design of their API.
5. On a verified webhook, the server makes a **second, server-side verify call** to the
   gateway's API (never trusts the webhook payload's status field alone) and cross-checks
   amount + currency + reference before crediting the wallet.
6. A `POST /api/payments/deposit/verify` polling endpoint exists as a fallback for cases
   where a webhook is delayed or missed (recommended by all three gateways' own docs).

**Card visual ("display it like big payment gateways"):** `cardBrandService.js` detects
Visa / Mastercard / Verve / Amex / Discover from the **BIN prefix only** (first 6-8 digits)
and returns a brand name + gradient + logo identifier, so the frontend can render an
animated card face exactly like Stripe/Paystack's own checkout UI does. This deliberately
stops short of looking up the *issuing bank's* card artwork — that data isn't available via
any public API, and obtaining it would require handling the full PAN, which defeats the
purpose of the hosted-checkout approach above. The honest version of "real-time card
visual" is brand-detection-as-you-type, which is what was built.

### 4.3 Theme engine

`ThemeConfig` is a singleton document holding two complete token sets (`light` and `dark`,
12 tokens each: primary, secondary, background, surface, text-on-* colors, semantic
success/warning/error, border). A `version` integer auto-increments on every save.

The frontend's bootstrap call (`GET /api/utility/storefront-bootstrap`) returns the current
theme plus its version. The frontend should poll this endpoint (or re-fetch on app resume)
and compare `version` against its last-seen value — a mismatch means an admin pushed a
change, and the client re-applies the full token set immediately. This is the "instant
propagation" mechanism: no app rebuild, no app store update, just a config refetch.

Admin endpoints (`PUT /api/admin/theme`) let any token be changed independently, plus
brand name and logo (processed through Sharp into web-optimized WebP on upload, with an
automatic small preview thumbnail generated alongside the full-size image).

### 4.4 Banner / carousel CMS

`Banner` documents support: title/subtitle, full-size + optional dark-mode image variant,
link target (product / category / external URL / none), placement zone (home carousel,
promo strip, category top, flash sale), manual `sortOrder` (drag-to-reorder from the admin
UI), active scheduling window (`startsAt`/`endsAt`), and optional country targeting.

`GET /api/utility/storefront-bootstrap` returns only banners that are currently active,
within their schedule window, and matching the visitor's detected country (or untargeted
banners, which show everywhere) — already sorted and ready to render.

### 4.5 Product ranking ("algorithm")

`rankingAlgorithm.js` implements a transparent, explainable weighted-scoring model:

```
score = (salesVelocity × 0.35) + (rating × 0.25) + (recency × 0.15) + (adminPromotion × 0.25)
```

All four inputs are normalized to 0–1 before weighting (so a product with 10,000 sales
doesn't automatically dominate just because of raw scale vs. a 0–5 star rating). Admin
"promotion" is itself one weighted input, not an override — but a high `promotionWeight`
combined with `isPromoted: true` will reliably surface a product near the top, with an
expiry (`promotionEndsAt`) so promotions don't silently run forever. This is deliberately
not a black box or random shuffle — every product's score is computed from real,
inspectable signals.

### 4.6 Admin control surface

Everything an admin needs to run the storefront day-to-day is under `/api/admin/*`, gated
by `requireRole('admin', 'superadmin')` (role escalation itself requires `superadmin`
specifically, so a compromised regular admin account can't grant itself superadmin):
theme editing, logo upload, banner CRUD + reorder, manual wallet adjustments (fully logged
as an `ADMIN_ADJUSTMENT` ledger entry with the responsible admin's ID and a reason), and
user search / deactivation / role management.

---

## 5. What's NOT built yet (honest gap list)

This was explicitly scoped as Phase 1 of a multi-session build. Still outstanding:

- **Android (Kotlin/Compose) frontend integration.** The existing `ShopAppShell.kt`
  (4,855 lines) and `ShopViewModel.kt` (1,492 lines) still point at the old mock/local
  backend contract. They need to be updated to call the new v2 API, handle the JWT
  refresh flow, render the theme tokens dynamically, and implement the hosted-checkout
  WebView flow for payments (see Section 7 — staged AI Studio prompts).
- **Network awareness** (explicit "offline / poor connection" UI states) — not yet
  implemented on the frontend; the backend returns clean error shapes that a network-aware
  client can branch on, but the actual offline-detection/retry/caching logic is a frontend
  task.
- **Refund flow.** `Order.status` includes `REFUNDED` and `WalletTransaction.type`
  includes `REFUND`, but no route currently triggers a gateway-side refund call.
- **Stripe** integration (mentioned as "all other payment gateways" — Stripe env vars are
  reserved in `.env.example` but no `stripeGateway.js` exists yet; same pattern as the
  other three, lowest priority since Monnify/Paystack/Flutterwave cover the Nigerian
  market).
- **Algorithm weight tuning endpoint.** The ranking weights are currently a constant in
  `rankingAlgorithm.js`; an admin-facing endpoint to adjust them live (mentioned in a code
  comment as a planned route) doesn't exist yet.
- **Automated tests.** Nothing here has unit/integration test coverage yet. Given this is
  a money-handling system, this should be treated as a near-term priority, not a "someday."
- **Production image storage.** `uploadService.js` currently writes to local disk
  (`/uploads`), which works for a single Render instance but won't survive a redeploy or
  scale past one dyno. `.env.example` reserves `UPLOAD_DRIVER=s3` as the production path;
  the S3-compatible adapter itself isn't written yet.
- **Rate limiting on webhook endpoints** specifically (currently covered by the general
  `/api/` limiter, which is probably fine, but worth a dedicated look once real traffic
  patterns are known).

---

## 6. Deployment notes (Render)

1. Set every variable in `.env.example` as a Render environment variable — **never commit
   a real `.env` file**.
2. Run `npm run seed` once after first deploy (e.g. via Render's shell) to create the
   first superadmin account and sample products.
3. Point each gateway's webhook URL (in their respective dashboards) at
   `https://<your-render-url>/api/payments/webhooks/{monnify|paystack|flutterwave}`.
4. Switch `MONNIFY_BASE_URL` from `sandbox.monnify.com` to the live URL, and swap all test
   keys for live keys, only once sandbox testing is fully verified end-to-end.
5. `UPLOAD_DRIVER` should move to an S3-compatible service before relying on uploaded
   banner images surviving a redeploy.

---

## 7. Staged prompts for Google AI Studio (frontend)

The Android app in this repo was originally generated via Google AI Studio's app builder.
These prompts are written to be pasted into AI Studio **one at a time, in order**,
each building on the last, so the Kotlin/Compose frontend catches up to the new backend
contract. Each stage is scoped to be reviewable on its own rather than one giant prompt.

> **Before starting:** update the AI Studio project's `BASE_URL` constant (or `.env` /
> `local.properties`, depending on how the project stores it) to point at your deployed
> backend-v2 URL, and confirm `/api/health` returns `{ "status": "ok" }` in a browser first.

### Stage 1 — Auth & token handling
> "Update the app's networking layer to call the new backend contract: `POST /api/auth/register` now returns a message and requires a follow-up `POST /api/auth/verify-otp` call with the emailed code before login is possible. `POST /api/auth/login` and `verify-otp` both return `{ user, accessToken, refreshToken }`. Store both tokens securely (EncryptedSharedPreferences or DataStore with encryption). Add an OkHttp/Ktor interceptor that attaches `Authorization: Bearer <accessToken>` to every request, and on a 401 response, calls `POST /api/auth/refresh` with the stored refresh token to get a new pair, retries the original request once, and otherwise logs the user out and returns to the login screen."

### Stage 2 — Theme engine wiring
> "Replace the app's hardcoded color scheme with dynamic theme loading. On app start, call `GET /api/utility/storefront-bootstrap`, read the `theme.light` and `theme.dark` token objects, and build a Compose `ColorScheme` from them at runtime instead of compile-time constants. Store the returned `theme.version` in app state. Add a lightweight polling check (e.g. every 60 seconds while the app is foregrounded, or on each app resume) that re-fetches `storefront-bootstrap` and compares `version` — if it changed, rebuild the ColorScheme and trigger a recomposition so the new theme applies without restarting the app. Respect `theme.allowUserToggle`: if true, show a light/dark toggle in settings that overrides `theme.defaultMode` locally; if false, hide the toggle and always follow `defaultMode`."

### Stage 3 — Banner carousel
> "Replace the hardcoded promotional banner carousel with one driven by the `banners` array returned from `storefront-bootstrap`. Each banner has `imageUrl` (and optionally `imageUrlDark` — use this one when the app is in dark mode, falling back to `imageUrl` if null), `title`, `subtitle`, `linkType`, and `linkValue`. Tapping a banner should navigate based on `linkType`: `PRODUCT` opens the product detail screen for the product ID in `linkValue`, `CATEGORY` filters the product grid by that category, `EXTERNAL_URL` opens it in a Custom Tab, and `NONE` does nothing. Banners already arrive pre-sorted and pre-filtered by the backend (active, in-schedule, country-matched) — don't add any client-side filtering logic."

### Stage 4 — Product catalog & ranked carousel
> "Wire the product grid to `GET /api/products` (paginated, supports `category` and `search` query params) and add a 'Recommended for you' horizontal rail on the home screen backed by `GET /api/products/carousel`, which returns products pre-ranked by the backend's scoring algorithm — render them in the order received, no client-side re-sorting. Product detail screen should call `GET /api/products/:id` and `GET /api/products/:id/reviews`, and allow authenticated users to submit a review via `POST /api/products/:id/reviews`."

### Stage 5 — Wallet UI & the card visual
> "Build a wallet screen calling `GET /api/payments/wallet` (returns `{ wallet: { balance, currency }, recentTransactions }`) showing the balance prominently and a scrollable transaction history list, each row showing type, amount, status (with color coding: green for SUCCESS, amber for PENDING, red for FAILED), and date. Add a 'Top Up' flow: a gateway picker (Monnify / Paystack / Flutterwave as radio options) and an amount field. As the user types into an amount field, call `POST /api/utility/card-brand` with whatever digits exist so far (this happens entirely client-side before any real payment — no card number is sent at this stage, just illustrative digits if you choose to preview a card face) and render an animated card face using the returned `brand`, `gradient`, and `logo` to give a polished 'this looks like a real payment gateway' feel. On submit, call `POST /api/payments/deposit/start`, then open the returned `checkoutUrl` in a WebView (this is where the customer enters their REAL card details — Monnify/Paystack/Flutterwave's own page, not a screen in this app). Listen for the WebView navigating to the configured redirect URL, then call `POST /api/payments/deposit/verify` with the `internalReference` to confirm and refresh the wallet balance."

### Stage 6 — Checkout & order tracking
> "Build a cart-to-checkout flow that collects a shipping address and calls `POST /api/orders` with `{ items: [{ productId, quantity }], shippingAddress }`. This single call atomically validates stock, debits the wallet, and creates the order — handle the `402` status code specifically (insufficient wallet balance) by prompting the user to top up via the Stage 5 flow before retrying checkout. Build an order history screen (`GET /api/orders`) and an order detail/tracking screen (`GET /api/orders/:id/tracking`) that renders the `trackingEvents` array as a vertical timeline."

### Stage 7 — Admin panel (separate build target or role-gated screen)
> "Add an admin section, visible only when the logged-in user's `role` is `admin` or `superadmin` (returned in the `/api/auth/me` response). Build: (1) a theme editor with color pickers for every light/dark token plus a live preview pane, saving via `PUT /api/admin/theme`; (2) a banner manager — list, create (with image picker uploading via `multipart/form-data` to `POST /api/admin/banners`), edit, delete, and a drag-to-reorder list calling `PUT /api/admin/banners-reorder`; (3) a product manager with full CRUD against `/api/admin/products` equivalents already covered by the existing product routes; (4) a simple wallet-adjustment tool for support cases, calling `POST /api/admin/wallet/adjust` with a required reason field, since every adjustment is permanently logged."

### Stage 8 — Network awareness & polish
> "Add a network connectivity listener (ConnectivityManager) that shows a persistent but unobtrusive banner ('You're offline — showing cached content') when there's no internet connection, and automatically retries any pending requests (especially the wallet/payment verify calls from Stage 5) when connectivity returns. Cache the last successful `storefront-bootstrap` response (theme + banners) to local storage so the app still renders its branding correctly on a cold start with no connection, rather than showing a blank/broken screen."

---

## 8. Suggested order for the next backend session

If continuing the backend work before tackling the frontend:

1. Write integration tests for the wallet service and the checkout transaction (highest
   risk surface — money).
2. Build the S3-compatible upload adapter so banner/logo images survive redeploys.
3. Add the Stripe gateway module (mirrors the existing three).
4. Build the refund route (gateway-side refund call + `WalletTransaction` of type
   `REFUND` + order status update).
5. Add the admin algorithm-weight-tuning endpoint referenced in `rankingAlgorithm.js`.
