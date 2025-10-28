# 🚀 Quick Start - Next Steps

## Immediate Actions Required

### 1. ✅ Build Status - IN PROGRESS
The project is currently building. Once complete:

```bash
# Check build output
cd C:\Users\dasbl\AndroidStudioProjects\VitruvianRedux
dir app\build\outputs\apk\debug\
```

Expected output: `app-debug.apk` (approximately 5-10 MB)

---

### 2. 🔧 Fix Remaining Build Issues (If Any)

If build fails, common fixes:
- **compileSdk mismatch:** Already fixed to SDK 36
- **Missing dependencies:** Run `.\gradlew.bat --refresh-dependencies`
- **Cache issues:** Run `.\gradlew.bat clean`

---

### 3. 📱 Install on Device

```bash
# Connect Android device via USB
# Enable USB debugging on device
# Install APK
adb install app\build\outputs\apk\debug\app-debug.apk

# Or use Android Studio
# Run -> Run 'app' (Shift+F10)
```

---

### 4. ⚠️ Grant Permissions

**On first launch, manually grant permissions:**
1. Settings → Apps → Vitruvian Control
2. Permissions → Enable:
   - Location (required for BLE scanning)
   - Nearby devices / Bluetooth (Android 12+)

---

### 5. 🧪 Initial Test Sequence

**Test 1: BLE Scanning**
```
1. Launch app
2. Tap "Scan for Device"
3. Verify: "Scanning for devices..." appears
4. Wait 10 seconds for auto-stop
```

**Test 2: Connection (Need Real Device)**
```
1. Power on Vitruvian machine
2. Ensure within 10 meters
3. Scan for device
4. Look for device name starting with "Vee"
5. Connect (may need to hardcode address initially)
```

**Test 3: Monitor Logs**
```bash
# Open terminal and run:
adb logcat -s VitruvianBLE:V MainViewModel:D

# Look for:
# - "Started BLE scanning"
# - "Found device: Vee..."
# - "Connecting to device"
# - "BLE initialization complete"
# - "Sent command: 0A 00 00 00" (init)
```

---

### 6. 🐛 Known Issues to Address

#### A. Device Selection UI (HIGH PRIORITY)
Currently, there's no UI to select from scanned devices. Need to add:

**Quick Fix:**
Hardcode your device address in `BleRepositoryImpl.kt` for testing:

```kotlin
// In startScanning() callback
override fun onScanResult(callbackType: Int, result: ScanResult) {
    Timber.d("Found device: ${result.device.name} (${result.device.address})")
    
    // AUTO-CONNECT TO FIRST DEVICE (TEMPORARY)
    if (result.device.name?.startsWith("Vee") == true) {
        scope.launch {
            stopScanning()
            connectToDevice(result.device.address)
        }
    }
    
    _scannedDevices.tryEmit(result)
}
```

#### B. Permission Handling (MEDIUM PRIORITY)
App doesn't gracefully request permissions. Current workaround:
- Grant manually in Settings before launch
- Or app will crash on scan attempt

**TODO:** Add permission request UI in MainScreen.kt

#### C. Error Messages (MEDIUM PRIORITY)
Generic error messages. Need user-friendly text.

---

### 7. 📊 Success Criteria - First Test

**PASS if:**
- ✅ App launches without crash
- ✅ BLE scanning starts
- ✅ Device discovered (name visible in logs)
- ✅ Connection established (logs show "Connected")
- ✅ Init sequence sent (logs show hex dumps)

**Then test workout:**
- ✅ Set weight: 10kg per cable
- ✅ Set reps: 10
- ✅ Tap "Start Workout"
- ✅ Logs show program params sent (96 bytes)
- ✅ Machine activates (resistance appears)
- ✅ Pull cables - metrics update in UI
- ✅ Tap "Stop Workout"
- ✅ Machine deactivates

---

### 8. 🔍 Debugging Tools

**View Real-Time Logs:**
```bash
# All app logs
adb logcat -s VitruvianBLE:V VitruvianApp:D MainViewModel:D

# BLE protocol frames only
adb logcat -s VitruvianBLE:V | findstr "Sent"

# Errors only
adb logcat *:E
```

**Enable Bluetooth HCI Snoop:**
1. Settings → Developer Options
2. Enable "Bluetooth HCI snoop log"
3. Reproduce issue
4. Pull log: `adb pull /sdcard/btsnoop_hci.log`
5. Analyze with Wireshark

**Use nRF Connect:**
- Install from Play Store
- Scan for same device
- Compare UUIDs
- Verify services/characteristics

---

### 9. 📝 Report Issues

If something doesn't work:

**Create Issue Report:**
```markdown
## Issue: [Brief description]

**Device:** [Phone model] (Android [version])
**Machine:** Vitruvian [model]
**App Version:** 0.1.0-alpha

**Steps to Reproduce:**
1. 
2. 
3. 

**Expected:**


**Actual:**


**Logs:**
```
[Paste relevant logcat output]
```

**Additional Notes:**

```

---

### 10. 🎯 Top 3 Priorities After Initial Test

1. **Add Device Selection List**
   - Show scanned devices in UI
   - Tap to connect
   - Remember last device

2. **Improve Permission Handling**
   - Request permissions with rationale
   - Handle denials gracefully
   - Guide user to settings if needed

3. **Add Rep Detection**
   - Port algorithm from app.js
   - Track position windows
   - Detect top/bottom transitions
   - Count warmup vs working reps

---

## Development Workflow

### Making Changes

```bash
# 1. Edit code in Android Studio

# 2. Build
.\gradlew.bat assembleDebug

# 3. Install
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 4. Test

# 5. View logs
adb logcat -s VitruvianBLE:V
```

### Code Locations

**To modify BLE behavior:**
- `VitruvianBleManager.kt` - Low-level BLE
- `BleRepositoryImpl.kt` - High-level BLE logic

**To modify UI:**
- `MainScreen.kt` - All UI components
- `MainViewModel.kt` - UI state management

**To modify protocol:**
- `ProtocolBuilder.kt` - Binary frame builders
- `Constants.kt` - UUIDs, values

**To add features:**
- Create in `domain/` first (models)
- Then `data/` (implementation)
- Then `presentation/` (UI)

---

## Files Modified Summary

### Recently Changed:
- ✅ `app/build.gradle.kts` - Updated compileSdk to 36
- ✅ `gradle/libs.versions.toml` - All dependencies added
- ✅ `settings.gradle.kts` - JitPack repository added

### Created:
- ✅ All core application files (12 files)
- ✅ Documentation (README, TESTING_GUIDE, etc.)

### Ready for:
- ⏳ Device testing
- ⏳ Protocol validation
- ⏳ User feedback

---

## Troubleshooting Common Issues

### "Bluetooth is disabled"
- Enable Bluetooth on phone
- Grant Bluetooth permissions

### "Device not found"
- Machine powered on?
- Within 10 meters?
- Name starts with "Vee"?
- Check filters in code

### "Connection failed"
- Another app connected?
- Restart machine
- Restart phone
- Move closer

### "GATT error 133"
- Nordic library should handle this
- If persists, report as bug
- Workaround: Disconnect, wait 5s, reconnect

### "Metrics not updating"
- Workout in Active state?
- Notifications enabled? (check logs)
- Machine responding?
- Try stop/restart workout

---

## Next Documentation to Create

After successful device test:

1. **USER_GUIDE.md** - End-user instructions
2. **DEVELOPER_GUIDE.md** - Contributing guidelines
3. **PROTOCOL_SPEC.md** - Full protocol documentation
4. **CHANGELOG.md** - Version history

---

## Build Status Check

Run this to verify build succeeded:
```bash
cd C:\Users\dasbl\AndroidStudioProjects\VitruvianRedux
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo Build SUCCESS!
    dir app\build\outputs\apk\debug\app-debug.apk
) else (
    echo Build FAILED - check gradlew output
)
```

---

**Current Status:** Build in progress...  
**Next Action:** Wait for build to complete, then install on device  
**Estimated Time:** 2-5 minutes for build

🚀 **Almost ready to test!**

