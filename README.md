# Vitruvian Project Phoenix - Android Control App

[![Latest Release](https://img.shields.io/github/v/release/DasBluEyedDevil/VitruvianProjectPhoenix?include_prereleases)](https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix/releases)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)

A native Android application for controlling Vitruvian Trainer workout machines via Bluetooth Low Energy (BLE).

## Support the Project

If you find this app useful and want to support its continued development:

**[☕ Buy Me a Coffee](https://buymeacoffee.com/vitruvianredux)**

Your support helps keep the machines running and the code flowing!

---

## Project Overview

This app enables local control of Vitruvian Trainer machines after the company's bankruptcy. It's a community rescue project providing a native Android alternative to keep these machines functional and prevent them from becoming e-waste.

## 🚀 Current Status

**Version:** 0.6.0-beta
**Last Updated:** November 25, 2025

### What's Working
✅ Full BLE device control and connection
✅ All workout modes (Old School, Pump, TUT, TUT Beast, Eccentric, Echo)
✅ Visual rep feedback (pending/completed states like trainer)
✅ Exercise library (200+ exercises)
✅ Workout history and tracking
✅ Personal records with automatic detection
✅ Custom routines and program builder
✅ Analytics dashboard with charts
✅ Insights tab with volume tracking
✅ Theme customization (Light/Dark/System)
✅ Unit conversion (kg/lb)
✅ Just Lift mode for quick single exercises
✅ AMRAP (As Many Reps As Possible) support

### Recent Updates (Beta 6)
- **Visual Rep Feedback**: Rep numbers now appear grayed out at top of rep, colored when complete (matches trainer)
- **Total Volume History Chart**: New Insights card showing volume lifted over time
- **Disconnect Confirmation**: Dialog prevents accidental disconnections
- **Database v24**: Stores machine status flags for each metric sample
- **50+ Warning Fixes**: Cleaner, more maintainable codebase

## Features

### Core Functionality
- **BLE Connectivity**: Reliable connection to Vitruvian Trainer devices
- **All Workout Modes**: Old School, Pump, TUT, TUT Beast, Eccentric-Only, Echo
- **Real-time Monitoring**: Live load, position, velocity, and power metrics
- **Rep Counting**: Accurate rep detection with visual feedback
- **Auto-Stop**: Automatic workout termination based on rep targets

### Enhanced Features
- **Exercise Library**: 200+ pre-loaded exercises categorized by muscle group
- **Personal Records**: Automatic PR detection and historical tracking
- **Workout History**: Complete history with metrics stored locally
- **Custom Routines**: Build and save your own workout routines
- **Program Builder**: Create structured multi-exercise programs

### Analytics & Insights
- **Muscle Balance Radar**: Visual balance across muscle groups
- **Consistency Gauge**: Monthly workout consistency tracking
- **Volume vs Intensity**: Session comparison charts
- **Total Volume History**: Track volume lifted over time
- **Mode Distribution**: Workout mode usage breakdown

### UI/UX
- **Material 3 Design**: Modern, clean interface
- **Theme Support**: Light, Dark, and System-follow modes
- **Responsive Layout**: Adapts to device orientation
- **Haptic Feedback**: Tactile response during workouts

## Technology Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin 1.9+ |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM + Clean Architecture |
| **DI** | Hilt/Dagger |
| **BLE** | Nordic BLE Library |
| **Database** | Room (v24 schema) |
| **Preferences** | DataStore |
| **Charts** | Vico Charts + Custom Canvas |
| **Async** | Coroutines + Flow |
| **Logging** | Timber |

## Installation

### From Release
1. Download the latest APK from [Releases](https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix/releases)
2. Enable "Install from unknown sources" in Android settings
3. Install the APK
4. Grant Bluetooth permissions when prompted

### Building from Source
```bash
# Clone the repository
git clone https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix.git
cd VitruvianProjectPhoenix

# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug
```

### Build Requirements
- Android Studio Hedgehog or newer
- JDK 17+
- Android device with BLE support (API 26+)
- Vitruvian Trainer machine for testing

## Usage

1. **Launch** the app
2. **Scan** for devices (tap the connection button)
3. **Connect** to your Vitruvian machine (devices starting with "Vee")
4. **Configure** workout parameters (exercise, mode, weight, reps)
5. **Start** your workout
6. **Monitor** real-time metrics during exercise
7. **Complete** - workout auto-stops or tap stop manually

## Hardware Compatibility

### Vitruvian V-Form Trainer (Euclid / VIT-200)
- **Status:** ✅ Fully Supported
- **Device Name:** `Vee_*`
- **Max Resistance:** 200 kg (440 lbs)

### Vitruvian Trainer+
- **Status:** ✅ Supported (community verified)
- **Max Resistance:** 220 kg (485 lbs)

## Permissions Required

| Permission | Purpose |
|------------|---------|
| `BLUETOOTH_SCAN` | Discover BLE devices (Android 12+) |
| `BLUETOOTH_CONNECT` | Connect to machine (Android 12+) |
| `ACCESS_FINE_LOCATION` | BLE scanning (older Android) |
| `FOREGROUND_SERVICE` | Background workout tracking |
| `POST_NOTIFICATIONS` | Workout status notifications |

## Known Issues

- **Echo Mode Timing**: May have occasional quirks on some devices
- **Very Fast Reps**: Rapid reps may occasionally miscount
- **Eccentric-Only on Euclid**: May not work correctly on older hardware ([#80](https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix/issues/80))

## Contributing

This is an open-source community project. Contributions welcome!

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Test with real hardware
5. Submit a pull request

## Testing

```bash
# Run unit tests
./gradlew test

# Run lint checks
./gradlew lint

# Run instrumented tests (requires device)
./gradlew connectedAndroidTest
```

## Project Structure

```
app/src/main/java/com/example/vitruvianredux/
├── data/
│   ├── ble/              # BLE communication layer
│   ├── local/            # Room database & DAOs
│   ├── preferences/      # DataStore preferences
│   └── repository/       # Repository implementations
├── domain/
│   ├── model/            # Domain models
│   └── usecase/          # Business logic (rep counting, etc.)
├── presentation/
│   ├── screen/           # Compose screens
│   ├── viewmodel/        # ViewModels
│   ├── components/       # Reusable UI components
│   └── ui/theme/         # Theme configuration
├── service/              # Foreground service
├── util/                 # Constants, protocol builder
└── di/                   # Dependency injection
```

## License

MIT License - See [LICENSE](LICENSE) file for details

## Acknowledgments

- Original web app developers for debuging the BLE protocol
- Vitruvian machine owners community for testing and feedback
- Nordic Semiconductor for the excellent BLE library
- All contributors and supporters

## Support

- **Issues**: [GitHub Issues](https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix/issues)
- **Discussions**: [GitHub Discussions](https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix/discussions)
- **Support Development**: [Buy Me a Coffee](https://buymeacoffee.com/vitruvianredux)

---

*This app is a community rescue project to keep Vitruvian Trainer machines functional after the company's bankruptcy. It is not affiliated with or endorsed by Vitruvian.*
