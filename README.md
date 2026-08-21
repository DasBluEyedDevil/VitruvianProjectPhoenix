# Project Phoenix

[![Latest Release](https://img.shields.io/github/v/release/9thLevelSoftware/ProjectPhoenix?include_prereleases)](https://github.com/9thLevelSoftware/ProjectPhoenix/releases)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)

A native Android application for controlling dual-cable trainers that advertise over Bluetooth Low Energy as `Vee_*`.

## Project Overview

This app provides local BLE control of compatible cable trainers: workout modes, live metrics, history, routines, and analytics. It is a community project with no affiliation with any hardware manufacturer.

## Current Status

**Version:** 1.1.0

### What's Working
- Full BLE device control and connection
- Workout modes (Old School, Pump, TUT, TUT Beast, Eccentric, Echo)
- Visual rep feedback (pending/completed states)
- Built-in generic exercise list + favorites
- Workout history and tracking
- Personal records with automatic detection
- Custom routines and program builder
- Analytics dashboard with charts
- Insights tab with volume tracking
- Theme customization (Light/Dark/System)
- Unit conversion (kg/lb)
- Just Lift mode for quick single exercises
- AMRAP support

## Features

- **BLE Connectivity**: Connection to trainers advertising as `Vee_*`
- **Workout Control**: Load, reps, rest, and mode selection from the phone
- **Exercise List**: Built-in generic movements with muscle group and equipment filters
- **History & PRs**: Local Room database, backup/restore via JSON
- **Offline**: No internet permission; everything runs on-device

## Getting Started

1. Download the latest APK from [Releases](https://github.com/9thLevelSoftware/ProjectPhoenix/releases)
2. Install on Android 8.0+
3. Enable Bluetooth and scan for a `Vee_*` device

```bash
git clone https://github.com/9thLevelSoftware/ProjectPhoenix.git
cd ProjectPhoenix
```

Open in Android Studio and run the `app` configuration.

## Hardware

Compatible with dual-cable trainers that advertise as `Vee_*`. Cables pull upward from a floor platform. The BLE name prefix `Vee` is required for scanning.

## License

MIT. See [LICENSE](LICENSE).

## Support

- **Issues**: [GitHub Issues](https://github.com/9thLevelSoftware/ProjectPhoenix/issues)
- **Discussions**: [GitHub Discussions](https://github.com/9thLevelSoftware/ProjectPhoenix/discussions)

*This is a community project with no affiliation with any hardware manufacturer.*
