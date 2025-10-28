# Android App Quick Start Guide
## Vitruvian Trainer Local Control - Getting Started

This guide will help you quickly set up and start developing the Android version of the Vitruvian Trainer control app.

---

## Prerequisites

### Required
- **Android Studio:** Arctic Fox or newer (latest stable recommended)
- **JDK:** Version 11 or higher
- **Minimum Android SDK:** API 26 (Android 8.0) - for stable BLE support
- **Target Android SDK:** API 34 (Android 14) or latest
- **Git:** For version control
- **Physical Android Device:** With BLE support for testing

### Recommended
- **Vitruvian Trainer machine:** For real hardware testing
- **nRF Connect app:** For BLE debugging and sniffing
- **Multiple test devices:** Different manufacturers and Android versions

---

## Project Setup (30 minutes)

### Step 1: Create New Android Project

```bash
# Open Android Studio
# File → New → New Project
# Select "Empty Activity" (for Compose) or "Empty Views Activity" (for XML)
# Configure project:
```

**Project Configuration:**
- **Name:** Vitruvian Trainer Control
- **Package name:** com.vitruvian.trainer (or your choice)
- **Save location:** Choose appropriate directory
- **Language:** Kotlin
- **Minimum SDK:** API 26 (Android 8.0)
- **Build configuration language:** Kotlin DSL (recommended)

### Step 2: Update build.gradle.kts (Module)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android") version "2.48"
}

android {
    namespace = "com.vitruvian.trainer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vitruvian.trainer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    
    // Compose Tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // BLE - Nordic Library (Recommended)
    implementation("no.nordicsemi.android:ble:2.6.1")
    implementation("no.nordicsemi.android:ble-ktx:2.6.1")

    // Dependency Injection - Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Chart Library - MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Logging - Timber
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("com.google.truth:truth:1.1.5")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

### Step 3: Update build.gradle.kts (Project)

```kotlin
plugins {
    id("com.android.application") version "8.1.4" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
}
```

### Step 4: Update settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // For MPAndroidChart
    }
}

rootProject.name = "Vitruvian Trainer Control"
include(":app")
```

### Step 5: Update AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Bluetooth permissions -->
    <uses-permission android:name="android.permission.BLUETOOTH" 
        android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" 
        android:maxSdkVersion="30" />
    
    <!-- Bluetooth permissions for Android 12+ -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    
    <!-- Location permission (required for BLE scanning on older Android) -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- Storage for workout export -->
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />

    <!-- Bluetooth LE feature -->
    <uses-feature
        android:name="android.hardware.bluetooth_le"
        android:required="true" />

    <application
        android:name=".VitruvianApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.VitruvianTrainerControl"
        tools:targetApi="31">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.VitruvianTrainerControl">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

### Step 6: Create Package Structure

```
app/src/main/java/com/vitruvian/trainer/
├── VitruvianApp.kt                     # Application class
├── di/                                  # Dependency Injection
│   ├── AppModule.kt
│   ├── BleModule.kt
│   └── DatabaseModule.kt
├── data/                                # Data layer
│   ├── ble/
│   │   ├── VitruvianBleManager.kt
│   │   ├── GattCommandQueue.kt
│   │   └── NotificationHandler.kt
│   ├── local/
│   │   ├── database/
│   │   │   ├── VitruvianDatabase.kt
│   │   │   ├── WorkoutDao.kt
│   │   │   └── entities/
│   │   └── datastore/
│   │       └── PreferencesManager.kt
│   ├── repository/
│   │   ├── BleRepository.kt
│   │   ├── BleRepositoryImpl.kt
│   │   ├── WorkoutRepository.kt
│   │   └── WorkoutRepositoryImpl.kt
│   └── model/
│       └── (data models)
├── domain/                              # Domain layer
│   ├── model/
│   │   ├── WorkoutSession.kt
│   │   ├── WorkoutMetric.kt
│   │   ├── WorkoutMode.kt
│   │   └── ConnectionState.kt
│   ├── repository/
│   │   └── (repository interfaces)
│   └── usecase/
│       ├── ConnectToDeviceUseCase.kt
│       ├── StartWorkoutUseCase.kt
│       ├── MonitorWorkoutUseCase.kt
│       └── (other use cases)
├── presentation/                        # UI layer
│   ├── MainActivity.kt
│   ├── navigation/
│   │   └── NavigationGraph.kt
│   ├── screens/
│   │   ├── connection/
│   │   │   ├── ConnectionScreen.kt
│   │   │   └── ConnectionViewModel.kt
│   │   ├── workout/
│   │   │   ├── WorkoutControlScreen.kt
│   │   │   └── WorkoutControlViewModel.kt
│   │   ├── metrics/
│   │   │   ├── LiveMetricsScreen.kt
│   │   │   └── LiveMetricsViewModel.kt
│   │   ├── history/
│   │   │   ├── HistoryScreen.kt
│   │   │   └── HistoryViewModel.kt
│   │   └── settings/
│   │       ├── SettingsScreen.kt
│   │       └── SettingsViewModel.kt
│   ├── components/
│   │   ├── LoadDisplay.kt
│   │   ├── PositionBars.kt
│   │   ├── WorkoutChart.kt
│   │   └── (reusable components)
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
└── util/                                # Utilities
    ├── Constants.kt
    ├── Extensions.kt
    ├── ProtocolBuilder.kt
    └── RepDetectionEngine.kt
```

---

## Initial Code Templates

### 1. VitruvianApp.kt (Application Class)

```kotlin
package com.vitruvian.trainer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class VitruvianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.d("Vitruvian Trainer Control app initialized")
    }
}
```

### 2. Constants.kt (BLE UUIDs and Constants)

```kotlin
package com.vitruvian.trainer.util

import java.util.UUID

object BleConstants {
    // Service UUIDs
    val GATT_SERVICE_UUID: UUID = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb")
    val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    
    // Characteristic UUIDs
    val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val MONITOR_CHAR_UUID: UUID = UUID.fromString("90e991a6-c548-44ed-969b-eb541014eae3")
    val PROPERTY_CHAR_UUID: UUID = UUID.fromString("5fa538ec-d041-42f6-bbd6-c30d475387b7")
    val REP_NOTIFY_CHAR_UUID: UUID = UUID.fromString("8308f2a6-0875-4a94-a86f-5c5c5e1b068a")
    
    val NOTIFY_CHAR_UUIDS = listOf(
        UUID.fromString("383f7276-49af-4335-9072-f01b0f8acad6"),
        UUID.fromString("74e994ac-0e80-4c02-9cd0-76cb31d3959b"),
        UUID.fromString("67d0dae0-5bfc-4ea2-acc9-ac784dee7f29"),
        REP_NOTIFY_CHAR_UUID,
        UUID.fromString("c7b73007-b245-4503-a1ed-9e4e97eb9802"),
        UUID.fromString("36e6c2ee-21c7-404e-aa9b-f74ca4728ad4"),
        UUID.fromString("ef0e485a-8749-4314-b1be-01e57cd1712e")
    )
    
    // Device name prefix for filtering
    const val DEVICE_NAME_PREFIX = "Vee"
    
    // Connection timeouts
    const val CONNECTION_TIMEOUT_MS = 15000L
    const val GATT_OPERATION_TIMEOUT_MS = 5000L
}

object WorkoutConstants {
    const val LB_PER_KG = 2.2046226218488
    const val KG_PER_LB = 1 / LB_PER_KG
    
    const val MIN_WEIGHT_KG = 0f
    const val MAX_WEIGHT_KG = 100f
    const val MAX_PROGRESSION_KG = 3f
    
    const val DEFAULT_WARMUP_REPS = 3
    const val MAX_HISTORY_POINTS = 72000 // 2 hours at 100ms
}
```

### 3. ProtocolBuilder.kt (Initial Implementation)

```kotlin
package com.vitruvian.trainer.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ProtocolBuilder {
    
    /**
     * Build the initial 4-byte command sent before INIT
     */
    fun buildInitCommand(): ByteArray {
        return byteArrayOf(0x0A, 0x00, 0x00, 0x00)
    }
    
    /**
     * Build the INIT preset frame with coefficient table (34 bytes)
     */
    fun buildInitPreset(): ByteArray {
        return byteArrayOf(
            0x11, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0xCD.toByte(), 0xCC.toByte(), 0xCC.toByte(), 0x3E.toByte(), // 0.4 as float32 LE
            0xFF.toByte(), 0x00, 0x4C, 0xFF.toByte(),
            0x23, 0x8C.toByte(), 0xFF.toByte(), 0x8C.toByte(),
            0x8C.toByte(), 0xFF.toByte(), 0x00, 0x4C,
            0xFF.toByte(), 0x23, 0x8C.toByte(), 0xFF.toByte(),
            0x8C.toByte(), 0x8C.toByte()
        )
    }
    
    /**
     * Build the 96-byte program parameters frame
     */
    fun buildProgramParams(
        mode: Int,
        reps: Int,
        effectiveKg: Float,
        perCableKg: Float,
        progressionKg: Float = 0f,
        isJustLift: Boolean = false
    ): ByteArray {
        val buffer = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN)
        
        // Header
        buffer.put(0x04)
        buffer.put(0x00)
        buffer.put(0x00)
        buffer.put(0x00)
        
        // Reps field
        buffer.put(if (isJustLift) 0xFF.toByte() else (reps + 3).toByte())
        
        // Constants from working capture
        buffer.put(0x03)
        buffer.put(0x03)
        buffer.put(0x00)
        
        // Float values
        buffer.putFloat(5.0f) // 0x08
        buffer.putFloat(5.0f) // 0x0C
        
        // More constants...
        // TODO: Complete implementation from protocol.js
        
        return buffer.array()
    }
    
    /**
     * Helper: Convert byte array to hex string for logging
     */
    fun ByteArray.toHexString(): String {
        return joinToString(" ") { "%02X".format(it) }
    }
}
```

### 4. ConnectionState.kt (Domain Model)

```kotlin
package com.vitruvian.trainer.domain.model

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
```

### 5. MainActivity.kt (Compose Setup)

```kotlin
package com.vitruvian.trainer.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vitruvian.trainer.presentation.theme.VitruvianTrainerControlTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VitruvianTrainerControlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // TODO: Add navigation graph
                }
            }
        }
    }
}
```

---

## Development Workflow

### Day 1-3: BLE Foundation
1. ✅ Project setup complete
2. ⬜ Implement `VitruvianBleManager` with Nordic library
3. ⬜ Create device scanning functionality
4. ⬜ Test BLE connection with nRF Connect app
5. ⬜ Implement GATT operation queue

### Day 4-7: Protocol Layer
1. ⬜ Complete `ProtocolBuilder` (port all functions from `protocol.js`)
2. ⬜ Create unit tests for protocol builders
3. ⬜ Implement command sending
4. ⬜ Test init sequence with real device

### Day 8-14: Core Features
1. ⬜ Implement workout start/stop
2. ⬜ Add monitoring notifications
3. ⬜ Create basic UI for connection
4. ⬜ Add workout control screen
5. ⬜ Test full workout flow

### Day 15-21: Data & Polish
1. ⬜ Set up Room database
2. ⬜ Implement workout history
3. ⬜ Add charting
4. ⬜ Polish UI/UX
5. ⬜ Beta testing

---

## Testing Strategy

### Unit Tests
```kotlin
// Example test for ProtocolBuilder
class ProtocolBuilderTest {
    @Test
    fun `buildInitCommand returns correct 4-byte array`() {
        val result = ProtocolBuilder.buildInitCommand()
        
        assertThat(result.size).isEqualTo(4)
        assertThat(result[0]).isEqualTo(0x0A)
        assertThat(result[1]).isEqualTo(0x00)
        assertThat(result[2]).isEqualTo(0x00)
        assertThat(result[3]).isEqualTo(0x00)
    }
}
```

### Integration Tests
- Test BLE connection flow
- Verify protocol frames match web app
- Validate workout execution

### Manual Testing Checklist
- [ ] BLE device discovery
- [ ] Connection establishment
- [ ] Workout start (each mode)
- [ ] Real-time monitoring
- [ ] Rep counting
- [ ] Workout stop
- [ ] History saving
- [ ] Data export

---

## Debugging Tips

### BLE Debugging
1. **Use nRF Connect:** Monitor all BLE traffic
2. **Enable Bluetooth HCI logging:** Settings → Developer Options → Enable Bluetooth HCI snoop log
3. **Compare with web app:** Use browser console to see exact byte sequences
4. **Log everything:** Use Timber to log all BLE operations

### Common Issues

**Issue:** "GATT operation already in progress"
- **Solution:** Ensure command queue is working properly

**Issue:** Connection drops frequently
- **Solution:** Check signal strength, remove other BLE devices, test closer to machine

**Issue:** Protocol frames not working
- **Solution:** Compare byte-by-byte with web app using hex dumps

**Issue:** Permissions denied
- **Solution:** Request runtime permissions properly for Android 12+

---

## Resources

### Documentation
- [Android BLE Guide](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
- [Nordic BLE Library](https://github.com/NordicSemiconductor/Android-BLE-Library)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

### Tools
- [nRF Connect for Mobile](https://play.google.com/store/apps/details?id=no.nordicsemi.android.mcp)
- [Android Studio](https://developer.android.com/studio)
- [Bluetooth HCI Snoop Log Viewer](https://www.bluetooth.com/develop-with-bluetooth/developer-resources-tools/)

### Community
- **GitHub Issues:** For bug reports and feature requests
- **Discord/Slack:** For real-time discussion (to be set up)
- **Stack Overflow:** Tag with `android-bluetooth-le`

---

## Next Steps

1. ✅ **Read the full roadmap:** `ANDROID_ROADMAP.md`
2. ⬜ **Set up development environment:** Follow this guide
3. ⬜ **Clone/fork the repository**
4. ⬜ **Start with BLE foundation:** Week 1-2 tasks
5. ⬜ **Test with real hardware:** Early and often
6. ⬜ **Join the community:** Contribute and get help
7. ⬜ **Ship the MVP:** Make those machines work again!

---

**Good luck building! Together we'll bring these machines back to life!** 💪

---

**Document Version:** 1.0  
**Last Updated:** October 27, 2025

