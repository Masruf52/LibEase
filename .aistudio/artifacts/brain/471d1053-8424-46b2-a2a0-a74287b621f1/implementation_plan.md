# LibEase Cloud Sync & Firebase Integration

Upgrade LibEase from a device-only library app into a cloud-connected Campus Library Management System backed by **Firebase Authentication** and **Cloud Firestore**, while retaining the **Room** database as a fast local cache. Users sign in with Google, seamlessly switch between Librarian (Admin) and Student portals, and keep books, loans, reservations, fines, and member profiles synchronized across devices in real time.

## User Review & Critical Decisions

> [!IMPORTANT]
> The architecture below incorporates your confirmed preferences along with platform authentication requirements for Firebase on Android.

- **Confirmed Storage Strategy — Hybrid Firestore + Room Cache**: Cloud Firestore serves as the durable source of truth across devices, while Room mirrors synchronized records locally for instant rendering and offline read resilience.
- **Confirmed Sync Scope — Full Library Dataset**: Book catalog inventory, active/historical loans, reservations, overdue fines, notifications, and member profiles all synchronize with Cloud Firestore.
- **Confirmed Role Workflow — In-App Role Selection & Switching**: Users can choose their starting portal (Student or Librarian) when signing in with Google and toggle between roles anytime from the top bar or Settings screen.
- **Authentication Provider — Google Sign-In via Credential Manager**: Firebase Authentication in this environment provisions Google Sign-In exclusively. Legacy email/password forms are replaced with a streamlined **Sign in with Google** experience (with automatic silent sign-in on startup and an interactive Google account selector button).

---

## 1. Overview & Core Concept

- **What It Does**: Connects every core LibEase workflow—cataloging books, borrowing and returning titles, placing holds, settling fines, and managing campus members—to a live Cloud Firestore database gated behind Google Sign-In, while keeping local Room tables synchronized automatically.
- **Target Audience / Persona**: University librarians managing shelf inventory, circulation, and overdue fines, alongside students browsing the catalog, tracking active loans, and reserving books across sessions and devices.
- **Key Value**: Eliminates data loss across app reinstalls or device switches, enforces authenticated multi-user security boundaries, and preserves instant local responsiveness through hybrid Room caching.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Startup & Silent Session Restore**: The animated LibEase splash screen checks for an active Google session and attempts silent auto-sign-in via Android Credential Manager, routing authenticated users straight into their active portal.
  2. **Google Sign-In & Portal Choice**: Unauthenticated users land on a focused authentication screen featuring a role selector (**Student Member** vs. **Librarian / Admin**) and a prominent **Sign in with Google** primary action.
  3. **Real-Time Catalog & Circulation Sync**: Adding or editing a book on the dedicated Book Entry screen, checking out or returning a title, placing a hold, or paying/waiving a fine writes to Cloud Firestore with server timestamps and immediately reflects in the local Room cache and UI.
  4. **Instant Role Switching & Sign-Out**: Tapping the role badge in the top app bar or toggling roles in Settings updates the user's active portal preference in the cloud and switches between the Admin Dashboard and Student Portal. Signing out clears both Firebase Auth and Credential Manager state cleanly.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Elevated collegiate dark-first UI (with light theme toggle support) designed for high-contrast catalog scanning and clear operational status badges.
  - *Color Palette & Mood*: Deep slate-indigo dark surfaces (`#12131A` background, `#1E1F26` / `#282A31` elevated containers) paired with electric periwinkle primary accents (`#B8C4FF` dark / `#1E40AF` light) and semantic status indicators for Available (`#16A34A`), Reserved/Due Soon (`#F59E0B`), and Overdue/Fines (`#DC2626`).
  - *Typography & Hierarchy*: Bold geometric headlines for dashboard metrics and screen titles, medium-weight labels for filter chips and ISBN metadata, and generous line spacing for book synopses.
  - *Component Styling & Layout*: Material Design 3 surfaces with `16dp–22dp` rounded cards, subtle `1dp` outline borders, minimum `48dp` accessible touch targets, and adaptive `600dp` max-width form containers.

- **Interactive Feedback & Motion**:
  - Smooth slide and fade navigation transitions between Splash, Google Sign-In, Main Portals, and the Add/Edit Book screen.
  - Non-blocking loading indicators inside the Google Sign-In button, graceful handling of dismissed credential sheets without error spam, real-time sync status badges in Settings, and snackbar confirmations on every catalog or loan action.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Two-Way Firestore-to-Room Synchronization**
  - *Chosen Approach*: Real-time Firestore snapshot streams listen to cloud collections only after Google authentication succeeds and upsert incoming documents into Room DAOs; UI ViewModels observe Room flows while user mutations write to Firestore and mirror locally.
  - *Why*: Combines cloud durability and multi-device sync with zero-jank local filtering, search, and existing Room DAO compatibility.
  - *Alternatives Considered*: Querying Firestore directly from every screen without Room was passed over because local search/filtering and existing offline inventory capabilities benefit from Room's reactive local cache.

- **Decision 2: Google Sign-In Exclusively via Jetpack Credential Manager**
  - *Chosen Approach*: Separate `GetCredentialRequest` flows—`GetGoogleIdOption` for silent startup restoration and `GetSignInWithGoogleOption` for interactive button sign-in—paired with navigation-level auth gating so no Firestore query runs before login completes.
  - *Why*: Prevents cold-start `PERMISSION_DENIED` races, stays responsive on Android 16 (API 36), and matches the backend's provisioned Google identity provider.
  - *Alternatives Considered*: Keeping email/password or anonymous sign-in fallbacks was rejected because unprovisioned providers fail at runtime and weaken user identity guarantees.

- **Decision 3: Zero-Trust Security Rules & PII Separation**
  - *Chosen Approach*: Isolate private account details (email, phone, active role preference) to owner-only profile documents while storing non-sensitive library card summaries and catalog items with explicit schema validation, string length bounds, immutable creation timestamps, and query-aligned access rules.
  - *Why*: Protects personal contact information from cross-user exposure while allowing seamless catalog browsing and in-app Librarian/Student portal switching.

---

## 4. Technical Architecture & Data Strategy

- **Architecture & Component Diagram**:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       Presentation Layer (Compose)                      │
│                                                                         │
│  ┌──────────────┐    ┌────────────────────┐    ┌─────────────────────┐  │
│  │ SplashScreen │───▶│  GoogleAuthScreen  │───▶│   Main LMS Shell    │  │
│  │ (Auto-Check) │    │ (Role + Google ID) │    │ (Admin / Student)   │  │
│  └──────────────┘    └────────────────────┘    └──────────┬──────────┘  │
│                                                           │             │
│                              ┌────────────────────────────┼──────────┐  │
│                              ▼                            ▼          ▼  │
│                     ┌─────────────────┐          ┌──────────┐  ┌──────┐ │
│                     │  AddBookScreen  │          │ Catalog  │  │Loans │ │
│                     └────────┬────────┘          └────┬─────┘  └──┬───┘ │
└──────────────────────────────┼────────────────────────┼───────────┼─────┘
                               │                        │           │
                               ▼                        ▼           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     State & Sync Coordination Layer                     │
│                                                                         │
│  ┌──────────────────────────┐          ┌─────────────────────────────┐  │
│  │ AuthenticationViewModel  │          │      LibraryViewModel       │  │
│  │ (CredentialManager Flow) │          │ (Auth-Gated Sync & Actions) │  │
│  └────────────┬─────────────┘          └──────┬───────────────┬──────┘  │
└───────────────┼───────────────────────────────┼───────────────┼─────────┘
                │                               │               │
                ▼                               ▼               ▼
┌──────────────────────────────┐  ┌───────────────────────┐  ┌────────────┐
│        Firebase Auth         │  │    Cloud Firestore    │  │ Local Room │
│ (Google Sign-In / ID Token)  │  │ (Named Enterprise DB) │◀─┤  Database  │
│                              │  │  • books              │─▶│ (Cached    │
│                              │  │  • users (private)    │  │  Entities  │
│                              │  │  • member_profiles    │  │  & DAOs)   │
│                              │  │  • librarians         │  │            │
│                              │  │  • loans / holds      │  │            │
│                              │  │  • fines / alerts     │  │            │
└──────────────────────────────┘  └───────────────────────┘  └────────────┘
```

- **Data Model & State**:
  - **Books (`books`)**: Shared library catalog documents containing title, author, ISBN, category, total/available copies, availability status, shelf location, campus visibility tag, and server `createdAt`/`updatedAt` timestamps.
  - **Private User Settings (`users`) & Member Directory (`member_profiles`)**: Owner-isolated account details (email, phone, preferred portal role) separated from non-PII library membership cards (display name, student/staff ID, department, status, borrowed count) to uphold strict PII isolation.
  - **Librarian Role Registry (`librarians`)**: Per-user opt-in role document (`uid == request.auth.uid`) enabling in-app Librarian mode permissions in Firestore security rules without exposing mutable privilege fields to other users.
  - **Circulation Records (`loans`, `reservations`, `fines`, `notifications`)**: Scoped with `userId` and validated status transitions (`Borrowed` -> `Returned`, `Active` -> `Cancelled`, `Unpaid` -> `Paid`/`Waived`), synchronized bidirectionally with local Room tables.

- **Interactive Component & State Mapping**:
  - **Google Sign-In Screen**: Selecting Student or Librarian updates `selectedRole` in `AuthenticationViewModel`; tapping **Sign in with Google** launches `CredentialManager` with `GetSignInWithGoogleOption`, exchanges the Google ID token with Firebase Auth, provisions/updates the user's Firestore profile and role state, and transitions into the main navigation graph.
  - **Auth-Gated Sync Lifecycle**: Once `FirebaseAuth` emits a non-null user, the sync coordinator attaches real-time `snapshots()` listeners to the named Firestore database (`firestore_database_id`), seeds starter catalog data on first run if empty, and mirrors updates into Room; signing out immediately cancels all listeners before clearing credentials.
  - **Catalog & Circulation Mutations**: Submitting `AddBookScreen`, borrowing/reserving a book, returning a loan, or settling a fine validates inputs, writes to Firestore with `FieldValue.serverTimestamp()` and structured error logging, updates Room immediately, and surfaces confirmation feedback via Snackbar.
