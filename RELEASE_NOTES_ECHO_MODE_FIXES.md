# VitruvianRedux - Beta 4 Echo Mode Fixes
## Issues #108 & #109 + Privacy Enhancement

**Build Date:** November 12, 2025
**Version:** 0.4.0-beta (Echo Mode Fixes)
**APK:** `VitruvianRedux-Beta4-EchoModeFixes-20251112.apk`

---

## 🐛 Bugs Fixed

### Issue #108: Echo Mode Settings Not Persisting
**Problem:**
- Echo difficulty level was reverting to "Hardest" after saving routines
- Eccentric load settings were not persisting correctly
- Settings would reset when reopening routine editor

**Root Cause:**
Incorrect enum mapping in the database layer using array indices instead of property matching.

**Fix:**
- WorkoutRepository.kt:461, 472 - Fixed enum mapping to use `levelValue` property
- WorkoutEntities.kt:30, 103 - Updated default values to match HARDER (levelValue=1)
- Now correctly maps: 0→Hard, 1→Harder, 2→Hardest, 3→Epic

**Status:** ✅ Fixed

---

### Issue #109: Echo Exercises Showing Weight Instead of "Adaptive"
**Problem:**
- Echo mode exercises displayed numeric weight values (e.g., "44 lbs") instead of "Adaptive"
- Occurred in both routine editor and active workout screens
- Caused confusion since Echo mode uses adaptive weight resistance

**Fix:**
- **WorkoutTab.kt:1484-1527** - Active workout exercise description now shows "Adaptive" for Echo mode
  - Echo: "12, 10, 8, 6 reps (Double) - Echo - Adaptive"
  - Non-Echo: "12, 10, 8, 6 @ 44.1 lbs/cable (Double) - Old School"
- **RoutineBuilderDialog.kt:329-330** - Routine editor exercise cards now show "Adaptive" for Echo mode

**Before:**
```
Shoulder Press
4 sets: 12/10/8/6   44lbs    ← Wrong for Echo mode
60s rest
```

**After:**
```
Shoulder Press
4 sets: 12/10/8/6   Adaptive    ← Correct!
60s rest
```

**Status:** ✅ Fixed

---

## 🔒 Privacy Enhancement

### MAC Address Removed from Logging
**Change:**
- Removed Bluetooth MAC addresses from all connection logs
- Users were concerned about privacy when sharing logs
- Device names (e.g., "Vee_12345") are still logged for troubleshooting

**Files Modified:**
- ConnectionLogger.kt:134 - Now stores `null` for deviceAddress
- ConnectionLogger.kt:187, 202 - Removed "MAC Address:" lines from log details
- ConnectionLogger.kt:206 - Removed address from JSON metadata

**Impact:**
- Connection logs are now safe to share publicly
- Hardware model and device name still available for debugging
- No functional impact on BLE connectivity

---

## 🔧 Enhanced Diagnostic Logging

### Eccentric Load Debugging (for Issue #107)
**Added comprehensive logging in ProtocolBuilder.kt:**
- Shows input parameters (eccentricPct, level, reps)
- Shows calculated echo parameters (gain, cap, smoothing)
- Shows raw byte values written to BLE frame
- Helps diagnose if 150% eccentric load is transmitted correctly

**Example Log Output:**
```
━━━━━━━━━━ ECHO FRAME CONSTRUCTION ━━━━━━━━━━
Input Parameters:
  level: Harder (levelValue=1)
  eccentricPct: 150%
  warmupReps: 3
  targetReps: 10
  isJustLift: false

Echo Parameters (calculated):
  eccentricPct: 150%
  concentricPct: 50%
  gain: 1.25
  cap: 40.0
  smoothing: 0.1
  floor: 0.0
  negLimit: -100.0
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Frame bytes:
  0x08-0x09 (eccentric u16): 0x96 0x00 = 150
  0x0A-0x0B (concentric u16): 0x32 0x00 = 50
```

### Routine Loading Debugging (for Issue #109)
**Added logging in MainViewModel.kt and WorkoutRepository.kt:**
- Shows Echo level and eccentric load when loading routines
- Shows database → domain mapping for verification
- Helps confirm settings are loaded correctly

---

## 📋 Testing Recommendations

### Test Case 1: Echo Difficulty Persistence (Issue #108)
1. Create routine with Echo exercise, set difficulty to "Hard"
2. Save routine and exit editor
3. Reopen routine editor
4. **Expected:** Difficulty still shows "Hard" ✓

### Test Case 2: Echo "Adaptive" Display (Issue #109)
1. Create routine with Echo exercise
2. View routine in editor
3. **Expected:** Exercise card shows "Adaptive" instead of weight ✓
4. Start workout from routine
5. **Expected:** Description shows "Adaptive" instead of weight ✓

### Test Case 3: Eccentric Load 150% (Issue #107)
1. Create Echo exercise with 150% eccentric load
2. Start workout
3. Check connection logs for "ECHO FRAME CONSTRUCTION"
4. **Expected:** Logs show "eccentricPct: 150%" and bytes "0x96 0x00 = 150" ✓

### Test Case 4: Privacy (MAC Address Removal)
1. Connect to device
2. Export connection logs
3. **Expected:** No MAC addresses visible in logs ✓
4. Device name (e.g., "Vee_12345") still shown ✓

---

## 🔄 Upgrade Instructions

### From Beta 4 Patch 1
1. Install APK over existing installation
2. All data preserved automatically
3. **Action Required:** Check existing Echo mode routines:
   - Verify difficulty levels are correct
   - Verify eccentric load percentages are correct
   - Re-save any affected routines if needed

### Clean Install
1. Uninstall previous version (optional)
2. Install new APK
3. Grant Bluetooth permissions when prompted
4. Create new routines to test fixes

---

## 📊 Changes Summary

**Files Modified:**
- WorkoutRepository.kt (Issue #108 fix + diagnostic logging)
- WorkoutEntities.kt (Issue #108 database defaults)
- WorkoutTab.kt (Issue #109 active workout display)
- RoutineBuilderDialog.kt (Issue #109 routine editor display)
- ConnectionLogger.kt (Privacy: MAC address removal)
- ProtocolBuilder.kt (Diagnostic logging for Issue #107)
- MainViewModel.kt (Diagnostic logging for routine loading)
- VitruvianBleManager.kt (Removed invalid override)

**Lines Changed:** ~150 lines modified
**Issues Fixed:** 2 confirmed bugs
**Privacy Enhancements:** 1 (MAC address removal)
**Diagnostic Improvements:** 2 (eccentric load + routine loading)

---

## 🐛 Known Issues

- **Issue #95:** Connection command failures (diagnostic patch available separately)
- **Issue #107:** Eccentric load 150% may feel like 100% (diagnostic logging added, awaiting test results)
- **Issue #102:** Single-handle exercises (manual fix in progress)
- **Hardware Detection:** Cannot distinguish Euclid from Trainer+ by BLE name alone

---

## 📞 Support & Feedback

**Email:** VitruvianRedux@gmail.com (auto-filled in log exports!)
**GitHub Issues:**
- #108: https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix/issues/108
- #109: https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix/issues/109

---

## 🙏 Special Thanks

Thanks to **viczhong** for reporting Issue #109 with clear screenshots showing the Echo mode display bug!

---

**Build System:** Gradle 8.13.1, Kotlin 2.0.21
**Testing:** Functional testing recommended for Echo mode routines
**Quality:** Following systematic-debugging methodology with comprehensive logging
