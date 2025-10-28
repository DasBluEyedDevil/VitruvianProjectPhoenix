# Vitruvian Redux - Beta Testing Guide

**Version:** 0.1.0-beta  
**Date:** October 27, 2025  
**Status:** Ready for Beta Testing

## What's New in Beta 1

### ✅ Core Features Implemented
- **BLE Device Discovery & Connection** - Scan for and connect to Vitruvian machines
- **All Workout Modes** - Old School, Pump, TUT, TUT Beast, Eccentric, Echo (all 4 levels)
- **Real-time Rep Detection** - Automatic rep counting with warmup/working rep differentiation
- **Live Metrics Display** - Real-time load and position monitoring
- **Workout History** - Automatically saves all completed workouts to local database
- **Auto-stop in Just Lift Mode** - Workout ends automatically when target reps reached
- **LED Color Schemes** - 7 predefined color schemes to customize machine LEDs
- **Permission Handling** - Proper runtime permission requests with explanations
- **Device Selection UI** - Choose from multiple scanned devices
- **Material 3 Design** - Modern, clean user interface

### 🆕 Features Added Since Alpha
1. **Rep Detection Engine** - Ported from web app with rolling position windows
2. **Room Database** - Workout history persistence
3. **Enhanced UI** - Multi-tab interface (Workout, History, Settings)
4. **Better Permission Handling** - User-friendly permission request flow
5. **Device Selector Dialog** - Choose specific device from scan results
6. **Workout History Screen** - View, review, and delete past workouts
7. **Settings Screen** - Manage color schemes and data

## Installation

### Prerequisites
- **Android Device:** Android 8.0 (API 26) or higher
- **Bluetooth:** BLE support required (all modern phones have this)
- **Vitruvian Machine:** Any Vitruvian Trainer model
- **Storage:** ~15 MB free space

### Install APK

**Method 1: Direct Install (Recommended)**
1. Download `app-debug.apk` from the release
2. Transfer to your Android phone
3. Tap the APK file
4. Allow "Install unknown apps" if prompted
5. Tap Install

**Method 2: ADB Install**
```bash
adb install app-debug.apk
```

## First Launch Setup

### 1. Grant Permissions
On first launch, you'll be asked to grant:
- **Bluetooth permissions** (required)
- **Location permissions** (required for BLE scanning on Android < 12)
- **Nearby devices** (Android 12+)

**Why Location?** Android requires location permission for Bluetooth scanning due to privacy concerns about BLE beacons revealing location.

### 2. Connect to Your Machine
1. Power on your Vitruvian machine
2. Ensure it's within 10 meters of your phone
3. Tap "Scan" button in the app
4. Wait for your device to appear (name starts with "Vee")
5. Tap your device to connect
6. Wait for "Connected" status

### 3. Start Your First Workout
1. Set workout parameters:
   - Weight per cable (kg)
   - Target reps
2. Tap "Start Workout"
3. Wait for initialization
4. Begin your exercise!
5. Watch real-time metrics update
6. Tap "Stop Workout" when done

## Features Guide

### Workout Screen

**Connection Card**
- Shows connection status
- Scan/Connect/Disconnect buttons
- Device info when connected

**Workout Parameters**
- Mode selection (currently Old School default)
- Weight input (per cable, in kg)
- Reps target
- Start/Stop buttons

**Rep Counter** (Active During Workout)
- Warmup reps count (default: first 3 reps)
- Working reps count
- Total reps

**Live Metrics** (Active During Workout)
- Total load (sum of both cables)
- Cable A load and position
- Cable B load and position
- Position bars show cable extension

### History Screen

- View all completed workouts
- Shows date, time, mode, duration
- Total reps and weight used
- Delete individual workouts
- Tap workout to view details (future feature)

### Settings Screen

**LED Color Schemes**
- Blue, Green, Teal, Yellow, Pink, Red, Purple
- Tap to apply immediately to machine

**Data Management**
- Delete all workout history
- Export data (coming soon)

## Testing Checklist

Please test the following and report any issues:

### Basic Functionality
- [ ] App launches without crash
- [ ] Permission request appears and works
- [ ] Bluetooth scan finds device
- [ ] Device connection succeeds
- [ ] Workout initialization completes
- [ ] Workout starts successfully
- [ ] Real-time metrics update during workout
- [ ] Rep counter increments correctly
- [ ] Workout stops successfully
- [ ] Workout saves to history

### Advanced Features
- [ ] Warmup reps counted separately (first 3)
- [ ] Working reps counted correctly
- [ ] Auto-stop works in Just Lift mode
- [ ] Multiple workouts save correctly
- [ ] History screen displays workouts
- [ ] Delete workout works
- [ ] Color scheme changes apply to machine
- [ ] Reconnection after disconnect works
- [ ] App survives screen rotation
- [ ] Background/foreground transitions

### Edge Cases
- [ ] What happens if Bluetooth is off?
- [ ] What happens if machine powers off during workout?
- [ ] What happens if phone screen locks during workout?
- [ ] What happens if another app tries to connect?
- [ ] What happens with very rapid reps?
- [ ] What happens with partial range reps?

## Known Limitations

### Current Beta Limitations
1. **Mode Selection:** Only Old School mode available in UI (others work via code)
2. **No Charting:** Live workout charts not yet implemented
3. **No CSV Export:** Workout data export coming soon
4. **No Templates:** Workout template saving/loading not yet available
5. **No Unit Switching:** kg/lb conversion not yet in UI
6. **Basic Stats:** Advanced statistics dashboard not yet implemented

### Technical Limitations
1. **Single Connection:** Can only connect to one machine at a time
2. **Foreground Only:** Workout monitoring requires app in foreground
3. **No Cloud Sync:** All data stored locally only
4. **Limited History:** Shows last 20 workouts (all stored, limited display)

## Reporting Issues

### What to Include
When reporting a bug, please include:

1. **Device Info**
   - Phone model and manufacturer
   - Android version
   - Bluetooth version (if known)

2. **Machine Info**
   - Vitruvian model
   - Firmware version (if known)

3. **Steps to Reproduce**
   - What you did step by step
   - What you expected to happen
   - What actually happened

4. **Logs** (if possible)
   - Enable Developer Options
   - Use `adb logcat -s VitruvianBLE:V VitruvianApp:D MainViewModel:D`
   - Copy relevant log output

### Where to Report
- **GitHub Issues:** [Create an issue](https://github.com/yourusername/vitruvian-redux/issues)
- **Email:** vitruvianredux@example.com
- **Discord:** [Join our community](https://discord.gg/example)

## Frequently Asked Questions

### Connection Issues

**Q: App can't find my machine**
- Ensure machine is powered on
- Ensure you're within 10 meters
- Try disabling/enabling Bluetooth on phone
- Restart the app
- Restart the machine

**Q: Connection keeps dropping**
- Move phone closer to machine
- Close other Bluetooth apps
- Ensure phone isn't in power saving mode
- Try disabling WiFi (interference)

**Q: "GATT error 133"**
- This is a common Android Bluetooth issue
- Disconnect and wait 5 seconds
- Reconnect
- If persists, restart phone

### Workout Issues

**Q: Rep counter not working**
- Ensure workout is Active (not Initializing)
- Try full range reps (top to bottom)
- Check that cables are moving
- Rep detection requires position change

**Q: Metrics showing zero**
- Ensure cables have resistance (weight set)
- Try pulling/pushing cables
- Check connection is stable
- Try stopping and restarting workout

**Q: Auto-stop not working**
- Ensure "Just Lift" mode is enabled (code change required in beta)
- Ensure target reps is set correctly
- Rep must be completed (full range)

### Data Issues

**Q: Workout didn't save to history**
- Ensure workout was stopped (not just disconnected)
- Check History tab after ~2 seconds
- Try pull-to-refresh (not yet implemented, check back)

**Q: Can I export my data?**
- Not yet in beta, coming in next release
- Data is stored in app database
- Can be accessed via ADB for advanced users

## Advanced Features (For Developers)

### Accessing the Database
```bash
# Pull database from device
adb pull /data/data/com.example.vitruvianredux.debug/databases/vitruvian_workout_db ./

# Open with SQLite browser
# Tables: workout_sessions, workout_metrics
```

### Enabling Debug Logging
```bash
# View all app logs
adb logcat -s VitruvianBLE:V VitruvianApp:D MainViewModel:D RepDetectionEngine:D

# View BLE protocol frames
adb logcat -s VitruvianBLE:V | findstr "Sent\|Received"

# Save to file
adb logcat -s VitruvianBLE:V > workout_log.txt
```

### Bluetooth HCI Snoop
For deep BLE debugging:
1. Enable Developer Options
2. Enable "Bluetooth HCI snoop log"
3. Reproduce issue
4. Pull log: `adb pull /sdcard/btsnoop_hci.log`
5. Analyze with Wireshark

## Roadmap to Release

### Beta 2 (2 weeks)
- Mode selection UI
- Live charting with MPAndroidChart
- CSV export functionality
- Unit switching (kg/lb)
- Bug fixes from Beta 1 feedback

### Beta 3 (4 weeks)
- Workout templates
- Advanced statistics
- Dark mode support
- Performance optimizations
- Extensive testing

### Release Candidate (6 weeks)
- Polish and UX improvements
- Comprehensive testing
- Documentation completion
- Play Store preparation

### Play Store Release (8 weeks)
- Public release on Google Play
- Marketing materials
- User guide videos
- Community support channels

## Privacy & Data

### What Data is Collected?
- **None!** This app is fully offline and collects no personal data
- All workout data stored locally on your device
- No analytics or tracking
- No network connections required
- No ads

### What Data is Stored?
- Workout history (date, duration, reps, weight)
- Real-time metric samples (load, position, time)
- User preferences (color scheme, units)

### How to Delete Data?
- Settings → Delete All Workouts (removes all history)
- Or uninstall the app (removes everything)

## Contributing

This is an open-source community project! We welcome contributions.

### How to Contribute
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

### Areas Needing Help
- Testing on different devices
- UI/UX improvements
- Documentation
- Translations
- Feature development

## Support

### Getting Help
- **Documentation:** Check this guide and README
- **GitHub:** Search existing issues
- **Community:** Join Discord for real-time help
- **Email:** For private support inquiries

### Emergency Contact
For critical bugs that could cause safety issues or data loss, please email immediately: [critical@example.com]

---

**Thank you for being a beta tester!** Your feedback is crucial to making this app the best possible solution for Vitruvian machine control.

**Happy lifting! 💪**

---

*Last Updated: October 27, 2025*  
*Document Version: 1.0*
