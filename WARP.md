# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Table of Contents
- [Project Overview](#project-overview)
- [Development Commands](#development-commands)
- [Application Architecture](#application-architecture)
- [Security & Encryption](#security--encryption)
- [Dropbox Integration](#dropbox-integration)
- [Database Schema](#database-schema)
- [Development Setup](#development-setup)
- [Debugging & Troubleshooting](#debugging--troubleshooting)
- [Key Dependencies](#key-dependencies)

## Project Overview

**EasyDeliverySage** is an enterprise-grade Android delivery management application that enables drivers to:
- Download and manage delivery trips from Dropbox
- Capture GPS locations with proximity validation (50m)
- Record customer signatures with hardware-backed encryption
- Take delivery photos with compression
- Generate PDF proof-of-delivery documents
- Sync data securely to Dropbox cloud storage

**Package:** `com.clone.EasyDelivery`  
**Min SDK:** 24 (Android 7.0)  
**Target SDK:** 34 (Android 14)  
**Build Tools:** Gradle 8.4.2

## Development Commands

### Building the Application
```powershell
# Clean build
.\gradlew clean

# Debug build
.\gradlew assembleDebug

# Release build (skip linting for faster builds)
.\gradlew assembleRelease -x lintReportRelease -x lintVitalRelease

# Install debug APK to connected device
.\gradlew installDebug

# Uninstall from device
adb uninstall com.clone.EasyDelivery
```

### Running and Testing
```powershell
# Run on connected device/emulator
.\gradlew installDebug
adb shell am start -n com.clone.EasyDelivery/.Activity.SplashLogin

# View live logs filtered by app
adb logcat | findstr "EasyDelivery\|EMAIL_CRITICAL\|SignatureSecurity\|TripClaiming"

# Debug database (requires root or debug build)
adb shell run-as com.clone.EasyDelivery
sqlite3 /data/data/com.clone.EasyDelivery/databases/DeliveryDb
```

### Common Development Tasks
```powershell
# Generate signed release APK
.\gradlew assembleRelease

# Run specific test
.\gradlew test --tests "*.DeliveryDbTest"

# Check dependencies for vulnerabilities
.\gradlew dependencyCheckAnalyze

# Clear app data during development
adb shell pm clear com.clone.EasyDelivery
```

## Application Architecture

### Activity Flow
```
SplashLogin (Entry) → TripDash (Trip Selection) → DashHeader (Delivery List) → Dash (Delivery Capture) → Preview (Completion)
                                     ↓
                             ReturnDash (Returns Management)
```

### Core Components

#### 1. **Data Layer**
- **DeliveryDb**: SQLite database with 5 tables (Delivery, Parcel, Sync, Email, Return)
- **SecurityManager**: Hardware-backed credential storage using Android Keystore
- **AppConstant**: Global state management and constants

#### 2. **Synchronization Layer**
- **DropboxHelper**: Manages cloud file operations with device-based trip claiming
- **SyncService**: Background service for continuous data synchronization
- **SyncConstant**: Synchronization state tracking

#### 3. **Business Logic**
- **ScheduleHelper**: Trip parsing and local storage management
- **LocationHelper**: GPS validation with 50m proximity checks
- **ImageHelper**: Photo compression and secure signature storage
- **JsonHandler**: Serialization for trip and delivery data

#### 4. **Security Layer**
- **Hardware-backed encryption** for signatures using Android Keystore
- **Key rotation** every 7 days with automatic cleanup
- **Integrity verification** using HMAC-SHA256
- **Secure credential storage** for Dropbox and email authentication

### Data Flow Architecture
```
┌─ Dropbox Cloud ─┐    ┌─ Local Storage ─┐    ┌─ Security Layer ─┐
│                 │    │                 │    │                  │
│ /Customers/     │◄──►│ SQLite Database │◄──►│ Android Keystore │
│ /InProgress/    │    │ Internal Files  │    │ Encrypted Prefs  │
│ /Completed/     │    │ Photo Storage   │    │ HMAC Validation  │
└─────────────────┘    └─────────────────┘    └──────────────────┘
         ▲                       ▲                       ▲
         │                       │                       │
    Trip Claiming          Delivery Tracking      Signature Security
```

## Security & Encryption

### Signature Security Implementation
```java
// Hardware-backed signature encryption (Dash.java)
SecurityManager securityManager = SecurityManager.getInstance(context);
String keyAlias = securityManager.generateSignatureEncryptionKey();
SignaturePackage signaturePackage = securityManager.encryptSignatureWithIntegrity(bitmapData);
String path = ImageHelper.saveEncryptedSignatureSecurely(context, signaturePackage);
```

### Credential Management
- **Dropbox credentials**: Stored in Android Keystore with AES-GCM encryption
- **Email passwords**: Hardware-backed storage with automatic key rotation
- **API tokens**: Securely migrated from BuildConfig to encrypted preferences

### Key Security Features
- ✅ **Zero-tolerance** for missing signatures in PDFs
- ✅ **Multiple validation layers** before email sending
- ✅ **Automatic retry** for failed deliveries with corruption tracking
- ✅ **Hardware-backed encryption** for all sensitive data
- ✅ **Integrity verification** for signatures using HMAC-SHA256

## Dropbox Integration

### Folder Structure
```
/Customers/[COMPANY_NAME]/
├── TripName.json                    (Pending trips)
├── InProgress/
│   └── TripName_CLAIMED_BY_DeviceID_AT_Timestamp.json
└── Completed/
    └── TripName/
        ├── TripName.json
        └── DocumentNumber/
            ├── DocumentNumber.json
            ├── signature.jpg
            └── photo.jpg
```

### Trip Claiming Mechanism
```java
// Safe trip claiming with collision avoidance (DropboxHelper.java)
String deviceId = getDeviceId(context);  // Unique device identifier
String timestamp = String.valueOf(System.currentTimeMillis());
String claimedName = createClaimedTripName(tripId, deviceId, timestamp);

// Atomic move operation to claim trip
client.files().moveV2(
    CUSTOMER_PATH + tripId + ".json",
    CUSTOMER_PATH + "InProgress/" + claimedName + ".json"
);
```

### Synchronization Patterns
- **Download**: Poll for new trips every 5 seconds
- **Claim**: Move trip to InProgress with device ID and timestamp
- **Complete**: Upload delivery artifacts and move to Completed folder
- **Cleanup**: Remove stale claims after 30 minutes

## Database Schema

### Core Tables
```sql
-- Delivery tracking with GPS and completion status
CREATE TABLE DeliveryTable (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    _tripId TEXT,
    _docu TEXT NOT NULL,              -- Document number
    _customer TEXT NOT NULL,          -- Customer name
    _address TEXT NOT NULL,           -- Delivery address
    _contactName TEXT NOT NULL,       -- Contact person
    _contactNumber TEXT NOT NULL,     -- Phone number
    _parcelQty INTEGER NOT NULL,      -- Number of parcels
    _latitude TEXT NOT NULL,          -- Expected GPS latitude
    _longitude TEXT NOT NULL,         -- Expected GPS longitude
    _capturedLatitude TEXT,           -- Actual delivery latitude
    _capturedLongitude TEXT,          -- Actual delivery longitude
    _sign TEXT,                       -- Encrypted signature path
    _pic TEXT,                        -- Photo file path
    _time TEXT,                       -- Completion timestamp
    _completed BOOLEAN NOT NULL,      -- Delivery completed flag
    _uploaded BOOLEAN NOT NULL,       -- Synced to Dropbox flag
    _comment TEXT,                    -- Driver notes
    _flagged BOOLEAN NOT NULL,        -- Issue flag
    email_retry_flag INTEGER DEFAULT 0,
    email_retry_reason TEXT DEFAULT ''
);

-- Individual parcel tracking within deliveries
CREATE TABLE ParcelTable (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    _tripId TEXT,
    _docu TEXT NOT NULL,              -- Associated document
    _parcel TEXT NOT NULL,            -- Parcel identifier
    _flagged BOOLEAN NOT NULL         -- Problem flag
);

-- Synchronization progress tracking
CREATE TABLE SyncTable (
    _tripId TEXT UNIQUE,
    _documentQty INTEGER NOT NULL,     -- Total documents in trip
    _documentSyncQty INTEGER NOT NULL  -- Successfully synced count
);
```

### Key Database Operations
```java
// Check if trip is fully completed and synced
boolean isFullyCompleted = database.isTripFullyCompleted(tripId);

// Mark delivery for retry when validation fails
database.markDeliveryForEmailRetry(document, tripId, "Missing signature");

// Get all failed deliveries for retry
List<Delivery> failedDeliveries = database.getAllUnsentEmails();
```

## Development Setup

### Prerequisites
- **Android Studio**: Arctic Fox or later
- **JDK**: OpenJDK 11 or higher
- **Gradle**: 8.8 (handled by wrapper)
- **Android SDK**: API levels 24-34

### Local Configuration
Create `local.properties` in project root:
```properties
# Dropbox API credentials (required for sync)
DROPBOX_REFRESH_TOKEN=your_refresh_token_here
DROPBOX_APP_KEY=your_app_key_here
DROPBOX_APP_SECRET=your_app_secret_here

# Android SDK location
sdk.dir=C:\\Users\\[username]\\AppData\\Local\\Android\\Sdk
```

### Required Permissions
```xml
<!-- Location services for delivery validation -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Camera and storage for photos/signatures -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- Network and services -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

## Debugging & Troubleshooting

### Critical Log Categories
```bash
# Monitor critical email validation failures
adb logcat | findstr "EMAIL_CRITICAL"

# Track signature security operations
adb logcat | findstr "SignatureSecurity"

# Debug trip claiming and race conditions
adb logcat | findstr "TripClaiming"

# Database operations and integrity
adb logcat | findstr "Database.*retry\|Database.*completed"
```

### Common Issues & Solutions

#### Dropbox Sync Failures
```bash
# Check credential status
adb logcat | findstr "Dropbox.*credentials"

# Verify folder structure
adb logcat | findstr "Dropbox.*folder.*created"
```
**Solution**: Verify `local.properties` contains valid Dropbox credentials

#### Missing Signature/Photo Errors
```bash
# Monitor validation failures
adb logcat | findstr "ABORTING EMAIL.*Missing"
```
**Solution**: Check `email_retry_flag = 1` in database for affected deliveries

#### Location Validation Issues
```bash
# Track GPS and proximity validation
adb logcat | findstr "LocationHelper\|Location.*Mismatch"
```
**Solution**: Ensure location permissions granted and GPS enabled

#### Database Migration Problems
```bash
# Monitor database version changes
adb logcat | findstr "DATABASE_VERSION.*17"
```
**Solution**: Clear app data if schema changes cause conflicts

### Performance Monitoring
```java
// Key performance indicators in logs
Log.d("PERFORMANCE", "Trip download completed: " + tripCount + " trips in " + duration + "ms");
Log.d("PERFORMANCE", "Signature encryption completed in " + encryptionTime + "ms");
Log.d("PERFORMANCE", "PDF generation completed: " + pdfSize + " bytes in " + generationTime + "ms");
```

## Key Dependencies

### Core Libraries
```gradle
// Material Design and UI components
implementation 'com.google.android.material:material:1.12.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

// Dropbox cloud synchronization
implementation 'com.dropbox.core:dropbox-core-sdk:5.4.0'

// PDF generation for proof-of-delivery
implementation 'com.itextpdf:itext7-core:7.2.5'
implementation 'com.itextpdf:html2pdf:4.0.5'

// Security and encryption
implementation 'androidx.security:security-crypto:1.0.0'
implementation 'androidx.biometric:biometric:1.1.0'

// QR code scanning
implementation 'com.journeyapps:zxing-android-embedded:4.3.0'
implementation 'com.google.zxing:core:3.2.0'

// Image processing and signatures
implementation(name: 'signature-view-1.0', ext: 'aar')
implementation 'com.squareup.picasso:picasso:2.5.2'
```

### Testing Dependencies
```gradle
testImplementation 'junit:junit:4.13.2'
androidTestImplementation 'androidx.test.ext:junit:1.2.1'
androidTestImplementation 'androidx.test.espresso:espresso-core:3.6.1'
```

---

**Last Updated:** 2024-10-02  
**WARP Version:** Compatible with Warp Terminal v0.2024+  
**Project Status:** ✅ Production Ready with Enterprise-Grade Security