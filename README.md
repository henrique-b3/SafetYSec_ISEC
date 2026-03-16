# 🛡️ SafetYSec

SafetYSec is an Android application developed within the scope of the Mobile Architectures course (2025/2026) at the Instituto Superior de Engenharia de Coimbra (ISEC).  
The main objective of the application is to ensure the protection and safety of vulnerable users (Protected users), allowing them to be remotely monitored by responsible users (Monitors).

---

## 👥 Authors

- Henrique Bernardi Netto  
- Tiago Filipe dos Santos Morais  

---

## 🌟 Main Features

The application supports two distinct user profiles, and the same person may assume both roles, although a user cannot monitor themselves.

### 🛡️ Protected Profile

- **SOS (Panic) Button**  
  Allows the protected user to trigger an alarm autonomously and immediately, starting the emergency workflow.

- **Privacy Control**  
  The protected user can authorize or revoke access to Monitors and define temporal monitoring windows.

- **Secure Cancellation**  
  In case of an alert, the user has 10 seconds to cancel the notification using a personal PIN (Cancellation Code).

- **Evidence Recording**  
  When a critical event is detected, the application automatically records a 30‑second video (via CameraX) and sends it to the Monitor as forensic evidence.

### 👁️ Monitor Profile

- **Monitoring Dashboard**  
  Real‑time visualization of the status of all associated Protected users in a single dashboard.

- **Automatic Security Rules**  
  Creation and configuration of automatic rules based on device sensors:
  - **Fall**: Detection of impacts greater than 5G.  
  - **Accident**: Detection of abrupt decelerations/impacts greater than 10G.  
  - **Speed**: Alerts when a predefined speed limit (km/h) is exceeded.  
  - **Geofencing**: Alerts when the Protected user leaves a configured safety radius (GPS coordinates).  
  - **Inactivity**: Detection of prolonged absence of movement.

---

## 🛠️ Architecture and Technologies

The project was developed following modern mobile development best practices.

### Language & UI

- Kotlin  
- Jetpack Compose (declarative UI)

### Architecture Pattern

- **MVVM (Model–View–ViewModel)** for clear separation of responsibilities:
  - View: Jetpack Compose screens observe state.
  - ViewModel: State management and business logic.
  - Model/Repository: Data access (Firebase, sensors, services).

### Backend (BaaS)

Integration with Firebase services:

- Firebase Authentication  
- Cloud Firestore Database  
- Firebase Storage  

### Asynchronous Programming

- Kotlin Coroutines  
- Kotlin Flow  

### APIs and Sensors

- `SensorManager` (Accelerometer)  
- `FusedLocationProviderClient` (GPS Location)  
- `CameraX` (Video Capture)  

### Background Processing

- Foreground Services compliant with Android 14 policies, enabling continuous monitoring (location, sensors, alerts) while respecting platform restrictions.

---

## 🌐 Accessibility & Internationalization

- Support for Portuguese and English, with dynamic language switching.  
- Support for **Portrait** and **Landscape** layouts.  
- Optimizations for accessibility tools, including screen readers.

---
## 📂 Project Structure

The application's codebase is strictly organized following the MVVM architecture, ensuring a clean separation of concerns:

```text
src/main/java/pt/isec/amov/safetysec/
├── model/          # Data classes representing Firestore documents (SafetyUser, SafetyRule, SafetyAlert, etc.)
├── repository/     # Data access layer managing Firebase operations (AuthRepository, DashboardRepository, etc.)
├── services/       # Android Foreground Services (SafetyMonitoringService, MonitorService)
├── ui/             # Jetpack Compose UI presentation layer
│   ├── components/ # Reusable UI elements (e.g., LanguageSelector)
│   ├── screens/    # Separated modules for application screens (account, alert, dashboard/monitor, dashboard/protected)
│   └── viewmodels/ # State management bridging the UI and the repositories
├── utils/          # Helper functions and utilities (PermissionUtils, VideoManager, AuthErrorHandler, etc.)
└── MainActivity.kt # The single Activity and entry point of the application

