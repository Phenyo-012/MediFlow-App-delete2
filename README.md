<p align="center">
    <img src="docs/images/mediflow-logo.png" alt="MediFlow logo" width="320" />
</p>

<h1 align="center">MediFlow</h1>
<p align="center"><em>Track smart. Store better. Grow faster.</em></p>
<p align="center"><strong>Digital Medication Inventory Management System</strong></p>

MediFlow is an **Android-based digital medication inventory management system** designed for pharmacy stock control, with a focus on public healthcare facilities. It brings medicine records, stock batches, receipts, issuing, corrections, expiry monitoring, alerts, reporting, and role-based administration into one system.

It uses an **offline-first Android application**, a local Room database, Firebase Authentication and Firestore, and a **trusted Node.js backend hosted on Vercel**. Staff can continue supported work with previously cached data when connectivity is unavailable; the backend validates and confirms shared stock movements when the device reconnects. MediFlow also supports **optional authenticator-app two-factor authentication (TOTP)** with protected administrator-assisted recovery.

MediFlow is a medication **inventory** application, not a patient-record, prescribing, dispensing-to-patient, or clinical decision-support system.

## 1. The Problem MediFlow Addresses

Medication stock management becomes difficult when information is fragmented, updates are delayed, or stock is tracked without enough detail about individual batches and expiry dates. These challenges can contribute to:

- **Stock shortages:** insufficient visibility into low or depleted stock and when to reorder.
- **Expired or wasted medicines:** limited monitoring of expiry dates and which batches should be issued first.
- **Inaccurate stock balances:** difficulty recording receipts, issues, adjustments, and corrections consistently.
- **Poor traceability:** limited visibility into who performed a stock movement, when it happened, and why.
- **Connectivity interruptions:** difficulty continuing day-to-day stock work when internet access is unstable.

**MediFlow's goal** is to make medication inventory easier to monitor, manage, and audit while improving the reliability of stock information across authorised devices. It is an **inventory management system**, not a patient record, prescribing, or clinical decision-support application.

## 2. Main Features

| Area | What MediFlow provides                                                                                                                                                                                      |
|---|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Authentication and access control** | Firebase email/password sign-in, **optional authenticator-app TOTP 2FA**, role- and permission-based access, active-account checks, live account revocation, and protected administrator operations.        |
| **Account security and TOTP recovery** | Self-service authenticator enrollment from **More → Account Security** (QR code or setup key), TOTP verification at login for enrolled accounts, and audited administrator-assisted reset for another user. |
| **Saved offline session unlock** | After a successful online sign-in, an eligible returning user can unlock locally cached access using device biometrics or screen-lock credentials, without entering a Firebase password offline.            |
| **Automatic session locking** | Lock after **15 minutes of inactivity**, including while MediFlow remains in the foreground and after extended time in the background.                                                                      |
| **Medicine management** | Add, update, view, and deactivate medicines; manage categories, medicine codes, reorder levels, and target stock levels.                                                                                    |
| **Batch management** | Track batches separately by batch number, expiry date, status, and available quantity.                                                                                                                      |
| **Stock receiving** | Record incoming medicines as a **new batch** or receive **additional stock into an existing batch** without creating a duplicate batch.                                                                     |
| **Stock issuing** | Issue stock using **First-Expiry-First-Out (FEFO)** so eligible batches with the earliest expiry dates are prioritised. Authorised overrides require a reason.                                              |
| **Adjustments and corrections** | Record justified stock increases/decreases and corrections while preserving confirmed movement history.                                                                                                     |
| **Barcode and QR scanning** | Scan identifiers to find or verify medicines; link identifiers to medicines and use supported GS1 batch/expiry data as reviewed input suggestions.                                                          |
| **Inventory search and filters** | Search medicines and filter by category, stock availability, expiry period, and, where permitted, ABC/VED classification.                                                                                   |
| **Alerts and notifications** | Identify low stock, near-expiry, and expired stock; support alert acknowledgement and Android notifications.                                                                                                |
| **Reorder recommendations** | Use medicine-specific reorder thresholds, target stock levels, and usable inventory quantities to identify medicines needing replenishment and recommend quantities to order.                               |
| **Reports and analytics** | View stock-level, alert, stock-movement, and inventory analytics reports, including charts and inventory-health summaries.                                                                                  |
| **ABC–VED classification** | Classify medicines by consumption-value category (ABC) and criticality (Vital, Essential, Desirable) for authorised users.                                                                                  |
| **Administration and audit** | Manage authorised users, permissions, inventory settings, and protected audit records.                                                                                                                      |
| **Offline operation and synchronisation** | Work from cached inventory, queue supported operations in Room, submit and reconcile them on reconnection, and surface confirmation, failure, retry, or conflict outcomes.                                  |

### Reorder recommendations

MediFlow evaluates medicine stock against configured **reorder levels** and **target stock levels** to identify replenishment needs. Recommendations assist pharmacy stock planning; they are **not automatically placed supplier orders**. As with all inventory decisions, the information available offline may differ from the latest authoritative cloud quantities until synchronisation completes.

### Inventory rules

- Stock is managed at **medicine and batch level**, rather than as a single unrestricted quantity.
- **Expired stock is excluded** from usable-stock calculations, and issuing follows FEFO unless an authorised override is made.
- Receipts, issues, adjustments, and corrections are recorded as identifiable operations with a history of their outcomes.
- **The trusted backend confirms shared stock changes.** An operation saved on the device may initially be pending and is not treated as cloud-confirmed until processing completes.
- Repeated uploads retain the original operation identity; the backend uses transactional and idempotent processing to prevent duplicate stock movements.
- Receiving **additional stock into an existing batch** increments the authoritative quantity of that batch without creating a duplicate medicine/batch-number record.
- A pending local operation is **not** proof that shared stock has changed; authoritative confirmation comes from the backend.
- An expired, inactive, or unauthorised session must not be treated as permission to process cloud inventory operations.

## 3. Tools and Technologies — Full Tech Stack

### Android application

| Category | Technology | Purpose |
|---|---|---|
| Development environment | **Android Studio**, Android SDK, Android Emulator, Android Debug Bridge (ADB) | Develop, build, install, debug, and run the application. |
| Programming language | **Java** | Android application logic and services. |
| Interface | **Android XML layouts**, Material Components, AndroidX AppCompat, ConstraintLayout, RecyclerView | Screens, navigation, forms, lists, and reusable interface elements. |
| Application architecture | **Android Jetpack ViewModel and LiveData**, repositories, services | Separate interface state, business logic, and data access. |
| Local database | **Room Persistence Library + SQLite** | Store medicine, batch, operation, alert, and other local application data. |
| Background tasks | **Android Jetpack WorkManager** | Schedule synchronisation, stock-related maintenance, and notification checks. |
| Barcode scanning | **CameraX + Google ML Kit Barcode Scanning** | Camera preview and on-device barcode/QR decoding. |
| Notifications and preferences | **Android NotificationManager**, notification channels, **SharedPreferences** | Device alerts and local notification settings. |
| Offline authentication | **Android Keystore**, **AndroidX Biometric**, device screen-lock credentials | Protect saved offline enrollment and require local user verification when unlocking. |
| TOTP enrollment interface | **Firebase Authentication multi-factor APIs**, **ZXing** | Enroll authenticator apps, show a local QR code or setup key, and verify time-based codes. |
| Session lifecycle | **Application.ActivityLifecycleCallbacks**, Activity input monitoring, `SystemClock` | Enforce background and foreground inactivity timeouts without adding separate timers to inventory screens. |
| Data visualisation | **Android View and Canvas** | Display inventory charts and analytics. |

### Cloud, backend, and security

| Category | Technology | Purpose |
|---|---|---|
| User authentication | **Firebase Authentication with Identity Platform** | Verify email/password credentials, support optional TOTP multi-factor sign-in, and provide identity tokens. |
| Cloud database | **Google Cloud Firestore** | Store shared medicine, batch, operation, settings, and other authorised data. |
| Cloud access controls | **Firestore Security Rules** | Restrict direct client access according to authentication, permissions, and request validation. |
| Backend runtime | **Node.js, JavaScript** | Run trusted server-side application logic; `trusted-backend/package.json` specifies **22.x** while the existing Vercel project was configured to build with **24.x** at the latest deployment. |
| Backend hosting | **Vercel Serverless Functions** | Host the trusted backend endpoints used by the Android application. |
| Server-side Firebase access | **Firebase Admin SDK** | Verify Firebase ID tokens (including revocation on protected endpoints), perform authoritative cloud operations, and remove enrolled TOTP factors during authorised account recovery. |
| Communication | **HTTPS and JSON APIs**, Firebase ID tokens | Secure communication between the Android application, Firebase, and the backend. |

### Build and testing tools

| Category | Technology | Purpose |
|---|---|---|
| Android build system | **Gradle**, Android Gradle Plugin, **Kotlin DSL** build files, Gradle Wrapper | Manage dependencies, build variants, builds, and tests. |
| Android tests | **JUnit**, **Mockito**, AndroidX Test, **Espresso** | Test Java logic, application behaviour, and Android interactions. |
| Backend tests | **Node.js built-in test runner**, **ESLint**, npm | Run backend tests, check code quality, and manage dependencies. |
| Version control | **Git and GitHub** | Develop in feature branches, review pull requests, and integrate changes into `development`. |

**Android compatibility:** minimum Android version **8.0 (API 26)**. The project is configured with **compile SDK 37**, **target SDK 37**, and Java 11 language compatibility.

## 4. How MediFlow Works

MediFlow follows an **offline-first architecture**. Room/SQLite stores the device's local inventory and pending operations. Firebase Authentication establishes user identity, Firestore stores shared cloud data, and the trusted Vercel backend validates and confirms authoritative inventory changes.

```text
Android application (Java + XML)
             |
      ViewModels / services
             |
       Repositories
             |
    Room / SQLite local data
             |
  Pending inventory operation
             |
     WorkManager sync
             |
   Firestore submission +
    authenticated HTTPS API
             |
    Trusted Vercel backend
   (Firebase Admin + Firestore)
             |
     Validate, authorise,
      confirm or reject
             |
  Reconcile device's Room data
```

### Inventory synchronisation

1. A signed-in and authorised employee uses locally available medicine and batch information.
2. Supported receipts, issues, adjustments, or corrections are recorded locally with a stable operation ID and an initial pending status.
3. When connectivity permits, the application submits and processes the operation through Firestore and the trusted backend.
4. The backend checks the authenticated identity, current account status, required permissions, submitted data, stock balances, and applicable inventory rules.
5. A successful transaction updates authoritative inventory exactly once; rejected or conflicting operations are reported for reconciliation.
6. The device updates its Room records from authoritative results. Pending operations must not be silently discarded.

### Optional TOTP two-factor authentication and recovery

TOTP is **optional for every MediFlow user**, including administrators. Enabling the Firebase TOTP provider does **not** require users to enroll and does not impose an organisation-specific email domain.

- **Without TOTP enrollment:** users continue signing in with their Firebase email and password, subject to their active account and application permissions.
- **With TOTP enrollment:** after email/password authentication, Firebase requires a code from the user's authenticator app before the online sign-in completes.
- **Enrolling an authenticator:** an eligible user opens **More → Account Security**, scans the QR code (or enters the setup key) in a compatible authenticator app, then confirms the six-digit code. New enrollment requires a **Firebase-verified email address**; development accounts with unverified addresses may continue using password-only sign-in.
- **After enrollment:** the user signs in again with their password and authenticator code before obtaining a new eligible offline session.
- **Recovery:** an administrator with `MANAGE_USER_PERMISSIONS` can inspect a different user's TOTP status and perform an identity-verified reset from **User Management → User Detail**. The backend requires a recent administrator sign-in, a recovery reason, and a unique request ID; it prevents self-reset, records an audit trail, removes the enrolled factor, and revokes Firebase refresh tokens.
- **After recovery:** the employee can sign in with email and password again, and may optionally enroll a new authenticator. There is no shared master code or password-only TOTP bypass for an already-enrolled account.

**Recovery limitations:** Changing a Firebase factor or revoking online tokens cannot immediately reach a fully disconnected device. Previously eligible offline access remains bounded by the existing 24-hour window and is revalidated when online. If a recovery request has an uncertain or partially completed result, check its status and audit record rather than repeatedly submitting resets.

### Online sign-in and offline unlock

- **First sign-in is online:** an employee authenticates with Firebase and obtains a valid, server-verified application profile.
- **Saved access is device-bound:** eligible returning users can unlock with a supported biometric or device screen-lock credential. No Firebase password is stored for offline sign-in.
- **24-hour offline eligibility:** a saved offline enrollment expires **24 hours after its last authoritative validation**. This is a maximum offline access window, not a promise that permissions can be checked while disconnected.
- **15-minute inactivity lock:** MediFlow locks after inactivity in the foreground or an extended background interval. Locking does not itself delete queued operations or sign out the Firebase account.
- **When connectivity returns:** MediFlow revalidates the account; server-confirmed account deactivation or profile deletion invalidates local access. Live profile changes also support prompt revocation while connected.
- **Explicit logout:** clears the saved offline enrollment. Access is restricted to the enrolled, matching account on that device.

**Offline limitation:** The application can only use data already cached on the device. New cloud data, live permission changes, and server confirmation require connectivity. A remotely disabled account cannot be detected while the device is fully offline; the bounded enrollment window limits that exposure.

## 5. How to Run MediFlow in Android Studio

The following steps are for someone who has been given the **MediFlow project folder**. They do not need to start a separate local backend server; the Android debug build is configured to use the deployed trusted backend.

### Requirements

- **Android Studio** with its bundled JDK and the required Android SDK components.
- **Android SDK Platform 37** installed for compiling the project.
- An **Android emulator** or a physical device running **Android 8.0 (API 26) or newer**.
- An internet connection for the first build, initial online sign-in, server-profile validation, and cloud synchronisation.
- A **secure device screen lock** (PIN, pattern, password, or supported biometrics) to use saved offline unlocking.
- The project's Firebase configuration file at **`app/google-services.json`**. If it is not present, request the correct file from the development team.
- An **active MediFlow test account** with the necessary application permissions. Obtain sign-in details securely from the development team.

### Step 1 — Open the project

1. Launch **Android Studio**.
2. Select **File → Open** and choose the main MediFlow project folder (the one containing `app/` and `settings.gradle.kts`).
3. Trust the project if prompted.
4. Let Android Studio download dependencies and complete **Gradle Sync**. If needed, select **File → Sync Project with Gradle Files**.
5. Set the build variant to **`debug`**.

### Step 2 — Build the application

Open **Terminal** inside Android Studio and run this command from the project root:

```powershell
.\gradlew.bat :app:assembleDebug
```

Wait for **`BUILD SUCCESSFUL`**. The debug APK is generated under `app/build/outputs/apk/debug/`.

### Step 3 — Start an emulator or connect a phone

**Option A: Android emulator**

1. Open **Tools → Device Manager**.
2. Create a virtual device if necessary, choosing a compatible Android system image.
3. Start the emulator and wait for it to finish booting.

**Option B: Physical Android phone**

1. Enable **Developer Options** and **USB debugging** on the phone.
2. Connect it by USB and approve the debugging prompt.
3. Wait until Android Studio lists the device as available.

A physical phone with a working camera is recommended when demonstrating barcode scanning.

### Step 4 — Launch MediFlow

1. Select the **`app`** run configuration in Android Studio.
2. Select the emulator or connected phone.
3. Click the green **Run ▶** button.
4. When MediFlow opens, **Sign In** with an authorised Firebase account while online. If that account has enrolled an authenticator, complete the prompted **six-digit TOTP verification**.
5. To enroll optionally, open **More → Account Security** and follow the QR-code/setup-key instructions (a verified email address is required for enrollment).
6. If the account is enrolled for offline access, subsequent locked sessions can use **Unlock Saved Session** with the device's authentication prompt.

**Alternative terminal command** (with a device or emulator already connected):

```powershell
.\gradlew.bat :app:installDebug
```

This installs the debug app. Open **MediFlow** on the device afterward.

### Step 5 — Run Android tests (recommended)

Run the debug build and Android unit tests together:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Or run Android unit tests separately:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Run Android instrumented tests (requires a connected device or running emulator):

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

### Backend lint and tests (contributors)

The trusted backend is already deployed; **you do not need to run a local server to launch the Android app**. Contributors changing the backend can validate it in the Android Studio Terminal:

```powershell
cd trusted-backend
npm ci
npm run lint
npm test
cd ..
```

The backend `package.json` specifies **Node.js 22.x**, although the existing Vercel project was configured with **Node.js 24.x** during the October 2026 deployment. Use Node.js 22 to validate the declared package requirement, and align the two configurations deliberately before future runtime upgrades. If your globally installed version differs, run tests with Node.js 22 via:

```powershell
cd trusted-backend
npx --yes node@22 --test
cd ..
```

The deployed health endpoint is:

`https://digital-medication-inventory-backen.vercel.app/api/health`

A successful health response confirms reachability, **not** full security validation. Do not submit live stock operations just to test an endpoint unless you have an authorised, safe test procedure.

### Common setup issues

| Issue | What to check |
|---|---|
| **Gradle sync/build fails** | Verify internet connectivity, SDK 37 installation, and the JDK selected by Android Studio. Read the first relevant error in Build Output. |
| **Firebase configuration error** | Confirm that `app/google-services.json` exists and belongs to the configured MediFlow Firebase project. |
| **No device available** | Start an emulator or reconnect the phone and enable USB debugging. |
| **Cannot sign in online** | Check connectivity, credentials, and whether the account has an active application profile. If TOTP is enrolled, complete the authenticator challenge. |
| **TOTP enrollment unavailable** | Enrollment requires a verified Firebase email and an eligible signed-in account. TOTP remains optional, so password-only login continues for users without enrollment. |
| **Lost authenticator** | Contact an authorised administrator for identity-verified recovery via the user's **User Detail** screen; do not share the account password or a TOTP setup key. |
| **Recovery reset refused** | Confirm administrator permission (`MANAGE_USER_PERMISSIONS`), recent sign-in, a different target user, and a valid recovery reason; check audit records before retrying after an uncertain result. |
| **Offline unlock is unavailable** | The account must have previously signed in and been verified online on that device; enrollment must be unexpired, the matching Firebase user must still be present, and a secure device lock must be configured. |
| **App locks unexpectedly** | A 15-minute inactivity interval or account revocation may have triggered the session gate. Re-authenticate; if the account was revoked, contact an administrator. |
| **HTTP 401 from the trusted backend** | Confirm that Android is sending a valid Firebase ID token for the signed-in account. Revoked or disabled Firebase sessions are rejected. |
| **Stock action remains pending** | Check connectivity and the Synchronisation screen. Cloud stock changes require the trusted backend to confirm them. |

**Important:** Do not clear app storage or uninstall an existing test installation unless instructed to do so; pending local data may be lost.

## 6. User Access and Security

MediFlow uses four primary application roles: **Pharmacy Assistant**, **Stock Controller**, **Pharmacist**, and **Pharmacy Manager / Administrator**. Roles are combined with **explicit permissions**: an account is not allowed to perform an action merely because its role name sounds privileged.

### Defence in depth

| Layer | Responsibility |
|---|---|
| **Android interface and local session gate** | Show eligible actions, enforce offline eligibility and inactivity locks, and react to live account-profile updates. UI hiding alone is not an authorization boundary. |
| **Firebase Authentication with Identity Platform** | Authenticate users, require TOTP challenges for enrolled accounts, and issue identity tokens; users without TOTP remain eligible for password-only authentication. |
| **Firestore Security Rules** | Restrict direct Android access to cloud documents; deny client writes to authoritative medicine, batch, transaction, classification, audit, and account-permission records. |
| **Trusted Vercel backend** | Verify Firebase ID tokens **with token-revocation checks**, re-read current account status and permissions, validate operation ownership, and control authoritative stock mutations. |
| **Audit and inventory history** | Preserve traceable operations and protected administrative changes. |

### Specific safeguards

- **Optional TOTP:** enrolled accounts must complete their authenticator challenge during online sign-in; non-enrolled accounts retain password-only login. Enrollment requires email verification, but no mandatory MFA policy is in force.
- **TOTP recovery:** permission-protected administrator resets require recent sign-in, confirmed employee identity, an explanation, and audit recording; they invalidate Firebase refresh tokens but cannot immediately revoke a fully offline device.
- **Account revocation:** server-confirmed profile deactivation or deletion invalidates saved local access when received online. Temporary network failures do not by themselves revoke an otherwise eligible offline session.
- **Permission changes:** user-profile updates refresh local permission state; sensitive data such as ABC–VED classifications has additional restricted-cache protections. Server-side permissions remain authoritative for protected operations.
- **Offline access:** bound to the last eligible signed-in account, protected with Android Keystore encryption and device authentication, and limited to a 24-hour authorization window.
- **Inactivity:** foreground and background inactivity locking after 15 minutes without requiring Firebase sign-out.
- **Stock integrity:** FEFO rules, required override reasons, non-negative stock checks, existing-batch top-ups, duplicate batch-number protection, and idempotent transaction processing.
- **Operation history:** only the authenticated, active owner or appropriately authorised users may access protected operation information; confirmed results do not bypass current account validation.
- **Administrator functions:** authorization is enforced by backend handler/service checks rather than Android screen visibility alone.

**Security boundary:** A device that is completely offline cannot receive immediate remote revocation or permission changes. Server-dependent actions are re-authorised by the backend when sent, and offline enrollment expires unless renewed by an authoritative online validation.

## 7. Project Scope

MediFlow focuses on **medication inventory operations and stock accountability**. It does not manage patient medical records, prescriptions, diagnoses, supplier invoicing, or financial accounting.

The project is intended to demonstrate a practical, research-informed approach to pharmacy inventory management using Android, offline-capable local storage, cloud synchronisation, and controlled server-side stock processing.

---

*MediFlow is a development project intended to improve medication inventory visibility and accountability. Deployment in a live healthcare setting requires site-specific operational, security, data-governance, and acceptance testing.*
