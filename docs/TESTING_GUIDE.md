# Testing Guide - Vitruvian Redux

## Pre-Testing Setup

### 1. Hardware Requirements
- ✅ Android device with BLE support (API 26+, Android 8.0+)
- ✅ Vitruvian Trainer machine
- ✅ USB cable for device debugging

### 2. Enable Developer Mode
1. Go to Settings → About Phone
2. Tap Build Number 7 times
3. Enable USB Debugging in Developer Options
4. Enable "Stay Awake" (optional, helpful for testing)

### 3. Install App
```bash
cd C:\Users\dasbl\AndroidStudioProjects\VitruvianRedux
.\gradlew.bat installDebug
```

Or use Android Studio: Run → Run 'app'

---

## Test Scenarios

### Test 1: BLE Scanning ✅

**Steps:**
1. Launch app
2. Tap "Scan for Device"
3. Observe scanning indicator

**Expected:**
- Scanning state shows "Scanning for devices..."
- Progress indicator appears
- Scanning auto-stops after 10 seconds

**Pass Criteria:**
- ✅ App requests BLE permissions
- ✅ Scanning starts without crash
- ✅ Scanning stops after timeout

### Test 2: Device Discovery ✅

**Prerequisites:**
- Vitruvian machine powered on
- Machine within BLE range (~10 meters)

**Steps:**
1. Start scanning
2. Wait for device discovery

**Expected:**
- Device appears in scan results (name starts with "Vee")
- Device address visible

**Pass Criteria:**
- ✅ Vitruvian device discovered
- ✅ Device info correct

### Test 3: Connection ✅

**Steps:**
1. Scan for device
2. Tap on discovered device (or use hardcoded connection)
3. Wait for connection

**Expected:**
- Connection state changes to "Connecting..."
- After ~5 seconds, shows "Connected to: [Device Name]"
- Device address displayed

**Pass Criteria:**
- ✅ Connection successful
- ✅ No timeout errors
- ✅ Connection state accurate

### Test 4: Init Sequence ✅

**Steps:**
1. Connect to device
2. Observe logs (logcat with tag "VitruvianBLE")

**Expected:**
- Init command sent (4 bytes: 0A 00 00 00)
- Init preset sent (34 bytes)
- Notifications enabled on characteristics

**Pass Criteria:**
- ✅ Init sequence completes without errors
- ✅ No GATT operation failures
- ✅ Notifications active

### Test 5: Workout Start (Old School Mode) ✅

**Steps:**
1. Connect to device
2. Set weight: 10 kg per cable
3. Set reps: 10
4. Ensure "Just Lift" is OFF
5. Tap "Start Workout"

**Expected:**
- Workout state changes to "Initializing..."
- Then changes to "Active"
- Init sequence sent
- Program params sent (96 bytes)
- Machine responds (loads appear)

**Pass Criteria:**
- ✅ No errors
- ✅ Workout state = Active
- ✅ Machine resistance activates

### Test 6: Real-Time Monitoring ✅

**Prerequisites:**
- Workout active
- Pull on machine cables

**Steps:**
1. Start workout
2. Pull cables slowly
3. Observe metrics card

**Expected:**
- Load A updates in real-time
- Load B updates in real-time
- Total load = Load A + Load B
- Position A changes with cable movement
- Updates at ~10Hz (100ms intervals)

**Pass Criteria:**
- ✅ Load values accurate (compare to machine display)
- ✅ Values update smoothly
- ✅ No lag or freezing
- ✅ Position tracking working

### Test 7: Workout Stop ✅

**Steps:**
1. During active workout
2. Tap "Stop Workout"

**Expected:**
- Workout state changes to "Completed"
- Machine resistance releases
- Metrics stop updating

**Pass Criteria:**
- ✅ Workout stops immediately
- ✅ No errors
- ✅ Machine safe to unload

### Test 8: Disconnection ✅

**Steps:**
1. While connected
2. Tap "Disconnect"

**Expected:**
- Connection state changes to "Disconnected"
- BLE connection closed
- UI reverts to initial state

**Pass Criteria:**
- ✅ Clean disconnection
- ✅ No memory leaks
- ✅ Can reconnect after

### Test 9: Just Lift Mode ✅

**Steps:**
1. Connect to device
2. Enable "Just Lift Mode" toggle
3. Set weight: 15 kg per cable
4. Start workout

**Expected:**
- Program params sent with reps = 0xFF
- Workout runs indefinitely
- No auto-stop on rep count

**Pass Criteria:**
- ✅ Workout doesn't auto-stop
- ✅ Can lift unlimited reps
- ✅ Manual stop works

### Test 10: Echo Mode (Future)

**Steps:**
1. Change mode to Echo Level 1
2. Set weight: 10 kg
3. Start workout

**Expected:**
- Program params sent
- Echo control frame sent (32 bytes)
- Echo mode parameters active

**Status:** ⏳ Not yet testable (need mode selector UI)

---

## Debugging Tools

### Logcat Filtering

```bash
# View all Vitruvian app logs
adb logcat -s VitruvianBLE:D VitruvianApp:D MainViewModel:D

# View BLE protocol frames
adb logcat -s VitruvianBLE:V

# View only errors
adb logcat *:E
```

### Enable Bluetooth HCI Logging

1. Settings → Developer Options
2. Enable "Bluetooth HCI snoop log"
3. Reproduce issue
4. Extract log: `adb pull /sdcard/btsnoop_hci.log`
5. Analyze with Wireshark

### nRF Connect App

Install nRF Connect for side-by-side comparison:
- Scan for same device
- Compare UUIDs
- Verify characteristics
- Monitor notifications

---

## Common Issues & Solutions

### Issue: Permission Denied

**Symptom:** "Bluetooth is disabled" or crash on scan

**Solution:**
1. Grant all permissions manually in Settings
2. Ensure Location is enabled
3. Restart app

### Issue: Device Not Found

**Symptom:** Scanning completes, no device found

**Solutions:**
1. Check machine is powered on
2. Move phone closer (<5 meters)
3. Turn Bluetooth off/on
4. Restart phone
5. Check device name filter ("Vee")

### Issue: Connection Timeout

**Symptom:** "Connection failed" error

**Solutions:**
1. Ensure no other app connected
2. Restart machine
3. Reduce distance to machine
4. Clear Bluetooth cache
5. Check for interference

### Issue: GATT Operation in Progress

**Symptom:** Commands fail, "GATT busy" in logs

**Solution:**
- This shouldn't happen (we use Nordic BLE Library queue)
- If it does, report as bug
- Workaround: Disconnect and reconnect

### Issue: Metrics Not Updating

**Symptom:** Connected but no data in Live Metrics

**Solutions:**
1. Check workout is Active state
2. Verify notifications enabled (check logs)
3. Check machine is responding
4. Try stopping and restarting workout

---

## Performance Benchmarks

### Connection Time
- **Target:** <5 seconds
- **Acceptable:** <10 seconds
- **Failure:** >15 seconds

### Command Latency
- **Target:** <100ms per command
- **Acceptable:** <500ms
- **Failure:** >1 second

### Update Frequency
- **Target:** 10 Hz (100ms intervals)
- **Acceptable:** 5-10 Hz
- **Failure:** <5 Hz

### Battery Drain
- **Target:** <5% per hour during workout
- **Acceptable:** <10% per hour
- **Failure:** >15% per hour

---

## Test Results Template

```markdown
## Test Session: [Date]

**Device:** [Manufacturer] [Model] (Android [Version])
**Machine:** Vitruvian [Model]
**App Version:** 0.1.0-alpha
**Tester:** [Name]

### Test Results

| Test # | Test Name | Result | Notes |
|--------|-----------|--------|-------|
| 1 | BLE Scanning | ✅ PASS | |
| 2 | Device Discovery | ✅ PASS | |
| 3 | Connection | ✅ PASS | |
| 4 | Init Sequence | ✅ PASS | |
| 5 | Workout Start | ✅ PASS | |
| 6 | Real-Time Monitoring | ✅ PASS | |
| 7 | Workout Stop | ✅ PASS | |
| 8 | Disconnection | ✅ PASS | |
| 9 | Just Lift Mode | ✅ PASS | |

### Issues Found
- [Description of any issues]

### Performance
- Connection Time: [X] seconds
- Update Frequency: [X] Hz
- Battery Drain: [X]% per hour

### Additional Notes
- [Any other observations]
```

---

## Next Testing Phase

After basic functionality confirmed:

1. **Stress Testing**
   - Multiple connect/disconnect cycles
   - Extended workout sessions (30+ min)
   - Rapid command sequences

2. **Edge Cases**
   - Connection loss during workout
   - Machine power off during session
   - App background/foreground transitions
   - Phone call interruption

3. **Multi-Device Testing**
   - Various Android versions
   - Different manufacturers
   - Different BLE chipsets

---

**Last Updated:** October 27, 2025  
**Status:** Ready for initial device testing

