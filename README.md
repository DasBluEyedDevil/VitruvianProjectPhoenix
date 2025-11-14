# Vitruvian Project Phoenix - Android Control App

A native Android application for controlling Vitruvian Trainer workout machines via Bluetooth Low Energy (BLE).

## Project Overview

This app enables local control of Vitruvian Trainer machines after the company's bankruptcy. It's a direct port of the proven web application to native Android, providing better stability, user experience, and offline capability.

## Features Implemented

### Phase 1: Core Functionality ✅
- [x] BLE device scanning and connection
- [x] Protocol implementation (all commands)
- [x] Workout modes (Old School, Pump, TUT, TUT Beast, Eccentric, Echo)
- [x] Real-time monitoring (load, position, ticks)
- [x] Workout parameter configuration
- [x] Color scheme customization
- [x] MVVM architecture with Clean Architecture principles
- [x] Dependency injection with Hilt
- [x] Modern UI with Jetpack Compose

### Phase 2: Enhanced Features ✅
- [x] Rep counting and auto-stop detection
- [x] Workout history with Room database
- [x] Permission handling UI
- [x] Device selection dialog
- [x] Multi-tab navigation
- [x] Workout history screen
- [x] Settings screen
- [x] Foreground service for workout tracking
- [x] Exercise library with 200+ exercises
- [x] Personal records tracking
- [x] Theme customization

### Phase 3: Advanced Features ✅
- [x] Active workout screen with real-time metrics
- [x] Analytics and statistics dashboard
- [x] Program builder for custom workout routines
- [x] Daily and weekly routine management
- [x] Single exercise mode (Just Lift)
- [x] Rest timer and countdown features
- [x] Haptic feedback
- [x] DataStore preferences
- [x] Comprehensive unit and integration tests

### Planned Features
- [x] Live charting visualization (Vico Charts, Compose Charts, Charty)
- [x] CSV export functionality
- [ ] Unit switching (kg/lb)
- [ ] Dark mode toggle
- [ ] Widget support
- [ ] Cloud backup

## Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3
- **Architecture:** MVVM + Clean Architecture
- **DI:** Hilt/Dagger
- **BLE:** Nordic BLE Library (v2.7.1)
- **Database:** Room with DAO pattern
- **Preferences:** DataStore
- **Charting:** 
  - Vico Charts (v2.1.3) - Modern Compose-native charts with Material 3 support
  - Compose Charts (v0.0.13) - Area charts with gradients
  - Charty (v1.0.0) - Circle/donut charts
  - MPAndroidChart (v3.1.0) - Legacy pie charts (being phased out)
- **Async:** Kotlin Coroutines + Flow
- **Image Loading:** Coil
- **Logging:** Timber
- **Testing:** JUnit, Mockk, Turbine, Truth, Robolectric

## Project Structure

```
app/src/main/java/com/example/vitruvianredux/
├── VitruvianApp.kt                          # Application class
├── MainActivity.kt                          # Main activity
├── data/
│   ├── ble/
│   │   └── VitruvianBleManager.kt           # BLE communication
│   ├── local/
│   │   ├── WorkoutDatabase.kt               # Room database
│   │   ├── WorkoutDao.kt                    # Workout data access
│   │   ├── ExerciseDao.kt                   # Exercise library access
│   │   ├── PersonalRecordDao.kt             # PR tracking
│   │   └── ExerciseImporter.kt              # Exercise library importer
│   ├── preferences/
│   │   └── PreferencesManager.kt            # DataStore preferences
│   └── repository/
│       ├── BleRepositoryImpl.kt             # BLE repository
│       ├── WorkoutRepository.kt             # Workout data repository
│       ├── ExerciseRepository.kt            # Exercise library repository
│       └── PersonalRecordRepository.kt      # Personal records repository
├── domain/
│   ├── model/
│   │   ├── Models.kt                        # Domain models
│   │   ├── Exercise.kt                      # Exercise models
│   │   ├── Routine.kt                       # Routine models
│   │   └── UserPreferences.kt               # Preferences models
│   └── usecase/
│       └── RepCounterFromMachine.kt         # Rep counting logic
├── presentation/
│   ├── screen/
│   │   ├── HomeScreen.kt                    # Home dashboard
│   │   ├── ActiveWorkoutScreen.kt           # Active workout tracking
│   │   ├── AnalyticsScreen.kt               # Statistics and analytics
│   │   ├── JustLiftScreen.kt                # Single exercise mode
│   │   ├── ProgramBuilderScreen.kt          # Custom routine builder
│   │   ├── SingleExerciseScreen.kt          # Individual exercise config
│   │   ├── DailyRoutinesScreen.kt           # Daily workout routines
│   │   └── WeeklyProgramsScreen.kt          # Weekly programs
│   ├── viewmodel/
│   │   ├── MainViewModel.kt                 # Main ViewModel
│   │   ├── ExerciseConfigViewModel.kt       # Exercise configuration
│   │   ├── ExerciseLibraryViewModel.kt      # Exercise library
│   │   └── ThemeViewModel.kt                # Theme management
│   ├── components/
│   │   ├── ConnectionStatusBanner.kt        # Connection status UI
│   │   └── EmptyStateComponent.kt           # Empty state UI
│   └── ui/theme/
│       ├── Theme.kt                         # Theme configuration
│       ├── Color.kt                         # Color definitions
│       ├── Type.kt                          # Typography
│       └── Spacing.kt                       # Spacing system
├── service/
│   └── WorkoutForegroundService.kt          # Foreground workout service
├── util/
│   ├── Constants.kt                         # BLE UUIDs and constants
│   └── ProtocolBuilder.kt                   # Binary protocol frames
└── di/
    └── AppModule.kt                         # Dependency injection
```

## Getting Started

### Prerequisites

- Android Studio Arctic Fox or newer
- Android device with BLE support (API 26+)
- Vitruvian Trainer machine for testing

### Building the Project

1. Clone the repository
2. Open in Android Studio Arctic Fox or newer
3. Sync Gradle files (will auto-download dependencies)
4. Build the project: `./gradlew build`
5. Run on a physical device (BLE doesn't work on emulators)

**Build Configuration:**
- Compile SDK: 36
- Min SDK: 26 (Android 8.0)
- Target SDK: 36
- Kotlin: Latest stable
- Gradle: 8.x

### Building Signed Release APK

The app now includes automatic APK signing for release builds to prevent "App not installed as package appears to be invalid" errors:

```bash
# Build signed release APK
./gradlew assembleRelease

# Output location
app/build/outputs/apk/release/app-release.apk
```

**Note:** The release builds use Android's debug keystore for signing. For production releases, configure a proper release keystore in `app/build.gradle.kts`.

### Permissions Required

- `BLUETOOTH_SCAN` - For scanning BLE devices (Android 12+)
- `BLUETOOTH_CONNECT` - For connecting to BLE devices (Android 12+)
- `ACCESS_FINE_LOCATION` - Required for BLE scanning on older Android versions
- `BLUETOOTH` / `BLUETOOTH_ADMIN` - For older Android versions

## Usage

1. Launch the app
2. Tap "Scan for Device" to find your Vitruvian machine (devices starting with "Vee")
3. Connect to your device
4. Configure workout parameters (mode, weight, reps)
5. Tap "Start Workout" to begin
6. Monitor real-time metrics during workout
7. Tap "Stop Workout" when complete

## BLE Protocol

The app implements the full Vitruvian BLE protocol:

- **Init Command:** 4-byte initialization
- **Init Preset:** 34-byte coefficient table
- **Program Params:** 96-byte workout configuration
- **Echo Control:** 32-byte Echo mode parameters
- **Color Scheme:** 34-byte LED color configuration

All protocol frames are byte-perfect matches to the original web application.

## Development Roadmap

### Current Progress: Alpha Release (Phase 3 Complete)

**Completed:**
- ✅ Project setup and dependencies
- ✅ BLE infrastructure with Nordic library
- ✅ Complete protocol implementation
- ✅ Domain models and Clean Architecture
- ✅ Enhanced UI with device selection
- ✅ Connection management
- ✅ Workout start/stop with foreground service
- ✅ Rep detection engine
- ✅ Workout history with Room database
- ✅ Permission handling with Accompanist
- ✅ Multi-tab navigation
- ✅ Exercise library (200+ exercises)
- ✅ Personal records tracking
- ✅ Program builder for custom routines
- ✅ Analytics and statistics dashboard
- ✅ Daily and weekly routine management
- ✅ Theme customization
- ✅ Comprehensive test coverage

**Next Steps (Beta Release):**
- Live charting visualization
- CSV export functionality
- Unit conversion (kg/lb)
- Dark mode toggle
- Performance optimization
- UI/UX refinements
- Beta testing feedback integration

## Contributing

This is an open-source community project to rescue Vitruvian machines from becoming e-waste.

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test with real hardware
5. Submit a pull request

## Testing

### Running Tests

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.example.vitruvianredux.protocol.ProtocolBuilderTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

### Test Coverage

The project includes comprehensive test suites:

**Unit Tests:**
- Protocol builder tests (`ProtocolBuilderTest.kt`)
- ViewModel tests (`MainViewModelTest.kt`, `MainViewModelEnhancedTest.kt`)
- BLE manager tests (`VitruvianBleManagerTest.kt`)
- Repository tests (`WorkoutRepositoryTest.kt`)
- Domain logic tests (`WorkoutModeTest.kt`)
- Rep counting tests (`RepCountingTest.kt`, `RepTrackingTest.kt`)

**Integration Tests:**
- Workout integration tests (`WorkoutIntegrationTest.kt`)
- BLE connection tests (`BleConnectionTest.kt`)
- Offline functionality tests (`OfflineFunctionalityTest.kt`)

### Manual Testing Checklist
- [ ] BLE device discovery and scanning
- [ ] Connection establishment and stability
- [ ] All workout modes (Old School, Pump, TUT, TUT Beast, Eccentric, Echo)
- [ ] Real-time load and position monitoring
- [ ] Rep counting accuracy
- [ ] Workout history recording
- [ ] Exercise library browsing
- [ ] Personal records tracking
- [ ] Program builder functionality
- [ ] Routine management
- [ ] Analytics dashboard
- [ ] Theme customization
- [ ] Permission handling
- [ ] Foreground service persistence
- [ ] Disconnection and reconnection handling

## Hardware Compatibility

This app supports the following Vitruvian Trainer models:

### Vitruvian V-Form Trainer (Euclid / VIT-200)
- **Status:** ✅ Supported
- **Device Name Pattern:** `Vee_*`
- **Max Resistance:** 200 kg (440 lbs)
- **Known Limitations:**
  - ⚠️ **Eccentric-Only Mode:** Users report that eccentric-only mode may not function correctly on this hardware. While the feature is supported in the original Vitruvian software, implementation issues or firmware differences may prevent proper operation. Under investigation - see [Issue #80](https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix/issues/80).
  - All other workout modes (Old School, Pump, TUT, TUT Beast, Echo) work normally

### Vitruvian Trainer+
- **Status:** ✅ Supported (expected)
- **Max Resistance:** 220 kg (485 lbs)
- **Features:** Improved motors with better eccentric mode performance
- **Note:** Device name pattern to be confirmed with community testing

If you experience issues with eccentric-only mode on Euclid hardware, please:
1. Export connection logs via Settings → Connection Logs
2. Report the issue on GitHub with logs attached
3. Try other workout modes as alternatives (Echo mode provides similar eccentric loading)

## Known Issues

- **Eccentric-only mode on Euclid hardware:** Not working correctly (under investigation)
- Live charting visualization not yet implemented
- CSV export feature pending
- Unit conversion (kg/lb) not yet available
- Dark mode toggle not yet implemented
- Some UI elements need polish

## Additional Features

### Exercise Library
The app includes a comprehensive exercise library with 200+ pre-loaded exercises:
- Categorized by muscle group
- Detailed instructions
- Equipment requirements
- Difficulty ratings

### Personal Records Tracking
- Automatic PR detection during workouts
- Historical PR tracking
- Performance trends
- Progress visualization

### Routine Management
- **Daily Routines:** Quick-access workout templates
- **Weekly Programs:** Structured multi-day training plans
- **Program Builder:** Create custom workout routines
- **Template Library:** Pre-built workout templates

### Foreground Service
The app uses a foreground service during workouts to ensure:
- Persistent BLE connection
- Uninterrupted workout tracking
- Background operation
- System notification for quick access

## License

MIT License - See LICENSE file for details

## Acknowledgments

- Original web app developers for debuging the BLE protocol
- Vitruvian machine owners community for support and testing
- Nordic Semiconductor for the excellent BLE library

## Support

For issues, questions, or contributions:
- Open a GitHub issue
- Join the community Discord (link TBD)
- Email: vitruvianprojectphoenix@example.com (TBD)

---

**Status:** Beta - Active Development  
**Version:** 0.2.0-beta  
**Last Updated:** November 6, 2025

### Recent Fixes (Beta 2+)
- ✅ **APK Signing:** Fixed "App not installed as package appears to be invalid" error by adding proper signing configuration for release builds

## 🚀 Current Status

The Vitruvian Project Phoenix Android app is under active development with most core functionality complete. The app provides comprehensive control of Vitruvian Trainer machines with advanced features for workout tracking, routine management, and performance analytics.

### Build Information
- **Build Location:** `app/build/outputs/apk/debug/app-debug.apk`
- **Minimum Android:** 8.0 (API 26)
- **Target Android:** API 36
- **APK Size:** ~8-10 MB

### What Works
✅ Full BLE device control  
✅ All workout modes  
✅ Exercise library (200+ exercises)  
✅ Workout history and tracking  
✅ Personal records  
✅ Custom routines and programs  
✅ Analytics dashboard  
✅ Theme customization  

### In Development
🚧 Live charting  
🚧 CSV export  
🚧 Unit conversion  
🚧 Dark mode
