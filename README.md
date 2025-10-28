# Vitruvian Redux - Android Control App

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

### In Progress
- [ ] Live charting with MPAndroidChart
- [ ] Mode selection UI
- [ ] CSV export
- [ ] Unit switching (kg/lb)

### Planned Features
- [ ] Workout templates
- [ ] Statistics dashboard
- [ ] CSV export
- [ ] Dark mode
- [ ] Widget support

## Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3
- **Architecture:** MVVM + Clean Architecture
- **DI:** Hilt/Dagger
- **BLE:** Nordic BLE Library (v2.7.1)
- **Database:** Room (planned)
- **Charting:** MPAndroidChart (planned)
- **Async:** Kotlin Coroutines + Flow

## Project Structure

```
app/src/main/java/com/example/vitruvianredux/
├── VitruvianApp.kt                 # Application class
├── MainActivity.kt                 # Main activity
├── data/
│   ├── ble/
│   │   └── VitruvianBleManager.kt  # BLE communication
│   └── repository/
│       └── BleRepositoryImpl.kt    # BLE repository implementation
├── domain/
│   └── model/
│       └── Models.kt               # Domain models
├── presentation/
│   ├── screen/
│   │   └── MainScreen.kt           # Main UI screen
│   └── viewmodel/
│       └── MainViewModel.kt        # Main ViewModel
├── util/
│   ├── Constants.kt                # BLE UUIDs and constants
│   └── ProtocolBuilder.kt          # Binary protocol frames
└── di/
    └── AppModule.kt                # Dependency injection
```

## Getting Started

### Prerequisites

- Android Studio Arctic Fox or newer
- Android device with BLE support (API 26+)
- Vitruvian Trainer machine for testing

### Building the Project

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Build and run on a physical device (BLE doesn't work on emulators)

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

See `reference/ANDROID_ROADMAP.md` for the complete 18-week development plan.

### Current Progress: Beta 1 (Phase 2 Complete)

**Completed:**
- ✅ Project setup and dependencies
- ✅ BLE infrastructure with Nordic library
- ✅ Complete protocol implementation
- ✅ Domain models and architecture
- ✅ Enhanced UI with device selection
- ✅ Connection management
- ✅ Workout start/stop
- ✅ Rep detection engine
- ✅ Workout history with Room database
- ✅ Permission handling with Accompanist
- ✅ Multi-tab navigation

**Next Steps (Beta 2):**
- Live charting implementation
- Mode selection UI
- CSV export functionality
- Unit conversion (kg/lb)
- Bug fixes from beta feedback

## Contributing

This is an open-source community project to rescue Vitruvian machines from becoming e-waste.

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test with real hardware
5. Submit a pull request

## Testing

### Manual Testing Checklist
- [ ] BLE device discovery
- [ ] Connection establishment
- [ ] Old School mode workout
- [ ] Pump mode workout
- [ ] TUT mode workout
- [ ] Echo mode workout
- [ ] Real-time load monitoring
- [ ] Position tracking
- [ ] Workout stop
- [ ] Disconnection handling

### Unit Tests (Planned)
- Protocol builder tests
- ViewModel tests
- Repository tests

## Known Issues

- Permission handling UI needs improvement
- Rep counting not yet implemented
- No workout history yet
- Charts not yet integrated

## Resources

- **Reference Implementation:** `reference/` directory contains original web app code
- **Documentation:** `reference/ANDROID_ROADMAP.md` - Complete development plan
- **Quick Start:** `reference/ANDROID_QUICK_START.md` - Setup guide
- **Mapping Guide:** `reference/WEB_TO_ANDROID_MAPPING.md` - Web to Android translation

## License

MIT License - See LICENSE file for details

## Acknowledgments

- Original web app developers for reverse-engineering the BLE protocol
- Vitruvian machine owners community for support and testing
- Nordic Semiconductor for the excellent BLE library

## Support

For issues, questions, or contributions:
- Open a GitHub issue
- Join the community Discord (link TBD)
- Email: vitruvianredux@example.com (TBD)

---

**Status:** Beta 1 - Ready for Testing  
**Version:** 0.1.0-beta  
**Last Updated:** October 27, 2025

## 🎉 Beta 1 Released!

The Vitruvian Redux Android app is now ready for beta testing! This release includes all core functionality needed to control your Vitruvian Trainer machine locally.

### Download Beta
- **APK Location:** `app/build/outputs/apk/debug/app-debug.apk`
- **Installation Guide:** See [BETA_TESTING_GUIDE.md](docs/BETA_TESTING_GUIDE.md)
- **Minimum Android:** 8.0 (API 26)
- **Size:** ~8 MB

