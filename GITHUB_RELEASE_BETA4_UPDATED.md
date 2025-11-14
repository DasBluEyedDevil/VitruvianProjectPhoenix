# Vitruvian Redux Beta 4 (v0.4.0-beta) + Patches

⚠️ **LATEST UPDATE:** Patch 3 (Echo Mode Complete Fix) released November 12, 2025 - **RECOMMENDED DOWNLOAD**

---

## 📦 Available Downloads

### ⭐ Patch 3: Echo Mode Complete Fix (November 12, 2025) - **RECOMMENDED**
**File:** `VitruvianRedux-Beta4-EchoModeFixes-20251112.apk` (22 MB)

**What's Fixed:**
- ✅ **Issue #108:** Echo difficulty level and eccentric load now persist correctly
- ✅ **Issue #109:** Echo exercises now show "Adaptive" instead of weight values
- ✅ **Privacy:** Removed MAC addresses from all connection logs
- ✅ **Diagnostics:** Enhanced logging for eccentric load and routine loading

**Why Upgrade:**
- Fixes critical Echo mode usability issues
- Makes logs safe to share publicly (no MAC addresses)
- Includes all previous patch fixes
- Enhanced diagnostic logging for Issue #107 investigation

**Details:** See [RELEASE_NOTES_ECHO_MODE_FIXES.md](RELEASE_NOTES_ECHO_MODE_FIXES.md)

---

### Patch 2: Issue #108 Fix (November 12, 2025)
**File:** `VitruvianRedux-Beta4-Issue108Fix-20251112.apk`
**Status:** ⚠️ Superseded by Patch 3
**What's Fixed:** Echo mode settings not persisting correctly
**Details:** See [RELEASE_NOTES_ISSUE108.md](RELEASE_NOTES_ISSUE108.md)

---

### Patch 1: Enhanced Log Sharing (November 12, 2025)
**File:** `VitruvianRedux-Beta4-Patch1-20251112.apk`
**What's New:**
- Log export auto-fills email to VitruvianRedux@gmail.com
- Pre-populated bug report template with device info
- Diagnostic instrumentation for Issue #95 (connection failures)

**Details:** See [DISTRIBUTION_PACKAGE.md](DISTRIBUTION_PACKAGE.md)

---

### Original Beta 4 Release
**File:** `app-debug.apk` (21 MB)
**Status:** ⚠️ Recommend upgrading to Patch 3

---

## 🎯 Beta 4 Overview

**Version:** 0.4.0-beta
**Release Date:** November 2025
**Build Status:** ✅ Build Successful

Beta 4 is a stability and quality-of-life release focusing on bug fixes, UI improvements, and enhanced hardware compatibility. This release addresses critical single exercise mode issues, improves tablet layout stability, and adds comprehensive hardware detection for better troubleshooting.

**Stats:**
- 33 files changed (base release)
- 3,041 additions / 1,721 deletions
- 111 unit tests (108 passing)
- **+7 files modified in Patch 3** (~150 lines)

---

## 📱 Installation

### ⚠️ INSTALLATION NOTE
All releases include DEBUG-SIGNED APKs for easy sideloading without signature verification issues. The debug signature allows you to install updates without uninstalling previous versions.

**Package:** `com.example.vitruvianredux.debug`
**Min Android:** 8.0 (API 26)
**Target Android:** 13 (API 36)

### Installation Steps
1. Enable "Install from Unknown Sources" in Android settings
2. Download the APK to your Android device
3. Tap the APK file to install
4. Accept permissions when prompted
5. **Grant Bluetooth permissions** when you first open the app

### Upgrading from Beta 3 or Earlier Patches
- Install over existing installation (keeps all data)
- All routines, programs, and settings preserved
- Hardware model detection is automatic on next connection

---

## 🆕 New Features (Beta 4)

### Hardware Detection System
- **NEW:** Added automatic hardware model detection (Euclid vs Trainer+)
  - Identifies V-Form Trainer (VIT-200) "Euclid" model from device name pattern
  - Displays hardware model and capabilities in connection logs
  - Tracks known hardware limitations for troubleshooting
  - Enhanced diagnostic logging for eccentric mode issues

### Exercise Video Controls
- **NEW:** Option to disable video playback in Settings
  - Prevents slow loading when videos are unavailable
  - Configurable per-user preference
  - Default: enabled
  - **Resolves:** Issue #4

### UI Enhancements
- **NEW:** Set summary card with detailed workout statistics
  - Shows peak power, average power, and rep count
  - Displayed after completing each set
  - Helps track performance within workouts

- **NEW:** Real-time cable position visualization (Session 14)
  - Vertical position bars on left and right screen edges
  - Shows Cable A (left hand) and Cable B (right hand) positions independently
  - Fills from bottom to top as cables extend
  - Displays range zones with semi-transparent overlay
  - Visual feedback for form and range of motion
  - Uses absolute positioning for proper edge placement

- **NEW:** Scroll indicators on long lists
  - Added to Program Builder day selection
  - 80dp gradient overlay with down arrow icon
  - Improves discoverability of Saturday/Sunday options

### Connection Improvements
- **NEW:** Cancel button on connection overlay
  - Allows interrupting auto-connect attempts
  - Properly cancels BLE operations and cleans up state
  - **Resolves:** Issue #5

### Confirmation Dialogs
- **NEW:** Confirmation dialog for "End Workout" button
  - Prevents accidental workout termination
  - Shows warning about losing unsaved progress

---

## 🐛 Critical Bug Fixes (Beta 4 + Patches)

### Patch 3: Echo Mode Fixes (November 12, 2025)

#### Issue #108: Echo Mode Settings Not Persisting ✅
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

#### Issue #109: Echo Exercises Showing Weight Instead of "Adaptive" ✅
**Problem:**
- Echo mode exercises displayed numeric weight values (e.g., "44 lbs") instead of "Adaptive"
- Occurred in both routine editor and active workout screens
- Caused confusion since Echo mode uses adaptive weight resistance

**Fix:**
- WorkoutTab.kt:1484-1527 - Active workout description now shows "Adaptive" for Echo mode
- RoutineBuilderDialog.kt:329-330 - Routine editor exercise cards now show "Adaptive" for Echo mode

**Before:**
```
Shoulder Press
4 sets: 12/10/8/6   44lbs    ← Wrong for Echo mode
60s rest

Active Workout:
"12, 10, 8, 6 @ 44.1 lbs/cable (Double) - Echo"
```

**After:**
```
Shoulder Press
4 sets: 12/10/8/6   Adaptive    ← Correct!
60s rest

Active Workout:
"12, 10, 8, 6 reps (Double) - Echo - Adaptive"
```

---

### Beta 4 Base: BLE Initialization Race Condition (Session 14)
**FIXED:** "NUS RX characteristic not available" error when starting workouts
- **Root Cause:** Commands sent before BLE initialization completed (race condition)
- **Symptom:** START_WORKOUT command failed with "characteristic not available" error
- **Fix:** Implemented AtomicInteger-based operation tracking
  - Tracks completion of 25 async BLE operations (MTU request + 24 notification enables)
  - Only sets ConnectionStatus.Ready after ALL operations complete
  - Added `checkAllOperationsComplete()` helper function
- **Impact:** Workouts now start reliably without connection errors
- **Testing:** Verified on device with successful workout starts
- **Files:** VitruvianBleManager.kt:220-285

### Beta 4 Base: Single Exercise Mode (Session 13)
**FIXED:** Rest timer showing 0/0 sets and second set resetting to 10 reps
- **Root Cause:** Detection logic failed for temporary routines created by SingleExerciseScreen
- **Fix:** Added `isSingleExerciseMode()` helper function that properly identifies temp routines
- **Impact:** Single exercise mode now correctly:
  - Displays rest timer with proper set count (e.g., "Set 1 of 2")
  - Advances to next set with correct rep count from configuration
  - Uses setReps array from user's configuration
- **Testing:** Added 3 comprehensive unit tests in MainViewModelEnhancedTest.kt
- **Files:** MainViewModel.kt:926-936, 977, 1052, 1080-1087, 1252

### Beta 4 Base: Tablet Layout Stability
**FIXED:** Potential black screen on tablet emulators
- **Root Cause:** Conflicting layout constraints in BottomAppBar (SpaceEvenly + weight modifiers)
- **Fix:** Changed `horizontalArrangement` from `SpaceEvenly` to `SpaceAround`
- **Impact:** Resolves layout calculation failures on wider tablet screens
- **Analysis:** Confirmed by Gemini CLI analysis of presentation layer
- **Files:** EnhancedMainScreen.kt:189

### Beta 4 Base: Connection Handling
**FIXED:** Connection continues after clicking Cancel
- **Root Cause:** Job cancellation didn't trigger BLE disconnect
- **Fix:** Added `CancellationException` handler that calls `bleRepository.disconnect()`
- **Impact:** Cancel button now properly stops connection attempts
- **Files:** MainViewModel.kt:268, 586-665, 676-681

### Beta 4 Base: Workout History Display
**FIXED:** Four separate history card display issues
1. Title showed mode ("Old School") instead of exercise name
   - Now shows exercise name or "Just Lift" when no exercise selected
2. Mode truncated to 8 characters ("Old Scho", "Eccentri")
   - Removed `.take(8)` truncation
3. Set count included warmup reps (showed 2 instead of 1)
   - Changed from `((totalReps / reps) + 1)` to `(workingReps / reps)`
4. Progress bar showed confusing calculation
   - Fixed to show `(workingReps % reps) / reps` for current set progress
- **Files:** HistoryAndSettingsTabs.kt:189, 197, 229-232, 254, 274

### Beta 4 Base: Workout Progress Indicator
**FIXED:** Progress bar not updating during workout
- **Root Cause:** Used static initial values instead of current metrics
- **Fix:** Changed to use `currentMetric.value` for real-time updates
- **Impact:** Progress indicator now reflects actual cable load/position
- **Files:** WorkoutTab.kt

### Beta 4 Base: Workout Mode Display
**FIXED:** Workout mode not preserved when loading routines
- **Root Cause:** Used default mode instead of exercise's configured mode
- **Fix:** Use exercise's `workoutType` when loading from routine
- **Impact:** Correct workout mode displayed and used
- **Files:** MainViewModel.kt

### Beta 4 Base: Multi-Set Weight Display
**FIXED:** Weight editor showing only first set's weight
- **Root Cause:** Didn't preserve individual set weights
- **Fix:** Properly map each set's weight when editing
- **Impact:** All set weights preserved during editing
- **Files:** ExerciseEditDialog.kt

---

## 🎨 UI/UX Improvements (Beta 4)

### Theme Consistency
- Made background gradients theme-aware across all screens
- ProgramBuilderScreen now matches other screens with dark mode support
- Updated light mode gradient colors for better visibility (E0E7FF, EDE9FE, DFF6FF)
- Dark mode uses slate-900, indigo-950, blue-950 gradients
- WeeklyProgramsScreen updated to match color palette

### Visibility Fixes
- **FIXED:** Invisible countdown timer numbers in light mode
  - Added proper theme-aware text colors
  - Files: WorkoutTab.kt
- **FIXED:** Invisible numbers in roller pickers on Fire HD tablets
  - Enhanced text color handling in CompactNumberPicker
  - Uses theme-aware colors with Material Design 3 compliance
  - Files: CompactNumberPicker.kt
- **FIXED:** Missing back arrow in Single Exercise screen
  - Added navigation icon to TopAppBar
  - Files: SingleExerciseScreen.kt

### Accessibility
- Improved scroll indicator visibility (opacity 0.7 → 0.85)
- Increased indicator height (60dp → 80dp) and arrow size (24dp → 28dp)
- Added rounded Surface background for better contrast

---

## 🔒 Privacy Enhancement (Patch 3)

### MAC Address Removed from Logging
**Change:**
- Removed Bluetooth MAC addresses from all connection logs
- Users were concerned about privacy when sharing logs for Issue #95 debugging

**Impact:**
- Connection logs are now safe to share publicly
- Device names (e.g., "Vee_12345") are still logged for troubleshooting
- No functional impact on BLE connectivity

**Files Modified:**
- ConnectionLogger.kt:134 - Now stores `null` for deviceAddress
- ConnectionLogger.kt:187, 202 - Removed "MAC Address:" lines from log details

---

## 🔍 Enhanced Diagnostics (Patches)

### Patch 3: Eccentric Load Debugging (for Issue #107)
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
  ...
Frame bytes:
  0x08-0x09 (eccentric u16): 0x96 0x00 = 150
```

### Patch 3: Routine Loading Debugging (for Issue #109)
**Added logging in MainViewModel.kt and WorkoutRepository.kt:**
- Shows Echo level and eccentric load when loading routines
- Shows database → domain mapping for verification
- Helps confirm settings are loaded correctly

### Patch 1: BLE Characteristic Lifecycle Tracking (for Issue #95)
**Enhanced VitruvianBleManager.kt with diagnostic logging:**
- Tracks when/why BLE characteristics are invalidated
- Logs connection state changes with timestamps and stack traces
- Aids in identifying root cause of "NUS RX characteristic not available" errors

---

## 🔧 Technical Improvements (Beta 4)

### Code Quality
**Logging Standardization:**
- Replaced 9 `android.util.Log` calls with Timber
  - ExerciseImporter.kt: 5 instances
  - CompactNumberPicker.kt: 4 instances (removed unnecessary debug logs)

**Test Coverage:**
- Added 3 new tests for single exercise mode
- Total: 111 unit tests (108 passing, 3 pre-existing failures)
- Tests verify temp routine handling and set progression

### Rep Counting Enhancement
- Added machine-based rep counting with configurable sensitivity
- Improved detection of concentric and eccentric phases
- Enhanced logging for troubleshooting rep detection issues
- Files: RepCounterFromMachine.kt

### Just Lift Mode
- Extended auto-start hold timer from 1.2s to 3s
- Reduces accidental workout starts
- Improves user control over workout initiation

### Connection Logging
- Enhanced ConnectionLogger with hardware model tracking
- Logs eccentric mode support status
- Includes max resistance and capability notes
- Aids in debugging hardware-specific issues

---

## 📋 Known Issues

### Active Issues Under Investigation
- **Issue #95:** "NUS RX characteristic not available" connection failures
  - Status: Diagnostic logging added in Patch 1
  - Waiting for user logs to identify root cause
- **Issue #107:** Eccentric load 150% may feel like 100%
  - Status: Enhanced diagnostic logging added in Patch 3
  - Awaiting test results from users
- **Issue #102:** Single-handle exercises
  - Status: Manual fix in progress

### Test Failures (Pre-existing, Non-critical)
- WorkoutModeTest > Stop At Top option - AssertionError at line 199
- WorkoutModeTest > Complete workflow - Old School mode - AssertionError at line 312
- WorkoutIntegrationTest > test rep counting integration - AssertionError at line 189

### Lint Warnings (Non-critical)
- 9 deprecated Material Icon references (AutoMirrored versions available)
- Dependency updates available (Kotlin 2.0.21 → 2.2.21, Hilt 2.51.1 → 2.57.2, etc.)
- 9 composables have modifier parameter ordering issues
- 4 autoboxing state creations (performance optimization opportunity)

### Hardware Compatibility
- **Euclid (VIT-200) Eccentric Mode:** Known issue with eccentric-only mode
  - Reported not working correctly on first-gen hardware
  - Likely protocol-related, not fundamental hardware limitation
  - Enhanced logging added to aid debugging
  - Documented in README Hardware Compatibility section

### Hardware Detection Limitation
- Cannot distinguish Euclid from Trainer+ by BLE device name alone
  - Both use "Vee_" prefix pattern
  - Firmware-based detection planned but requires BLE Device Information Service
  - Current detection is best-effort based on name patterns

---

## 🔄 Migration Notes

### For Users Upgrading from Beta 3
- No data migration required
- Video playback setting defaults to enabled (matches Beta 3 behavior)
- All existing routines, programs, and settings preserved
- Hardware model detection is automatic on next connection

### For Users Upgrading from Earlier Beta 4 Patches
- **From Patch 1 or 2:** Install Patch 3 over existing installation
- All data preserved automatically
- **Action Required:** Check existing Echo mode routines:
  - Verify difficulty levels are correct (Issue #108 fix)
  - Verify eccentric load percentages are correct (Issue #108 fix)
  - Re-save any affected routines if needed
  - Exercise cards should now show "Adaptive" (Issue #109 fix)

### For Developers
- `isSingleExerciseMode()` replaces inline `routine == null` checks
- `ConnectionState.Connected` now includes `hardwareModel` field
- Use Timber instead of `android.util.Log` for consistency
- Exercise video playback now requires `enableVideoPlayback` parameter
- Echo mode exercises should check `workoutType is WorkoutType.Echo` before displaying weight

---

## 📦 Distribution

**APK Location:** `app/build/outputs/apk/debug/` (base release)
**Patch APKs:** Project root directory
**Size:** ~21-22 MB
**Min SDK:** Android 8.0 (API 26)
**Target SDK:** Android 13 (API 36)

---

## 🙏 Acknowledgments

### Community Contributions
This release addresses multiple user-reported issues:
- **Issue #4:** Option to disable video downloads
- **Issue #5:** Cancel button for connection overlay
- **Issue #80:** Euclid eccentric mode investigation
- **Issue #95:** Connection command failures (diagnostic logging added)
- **Issue #108:** Echo mode settings persistence (fixed in Patch 3)
- **Issue #109:** Echo mode "Adaptive" display (fixed in Patch 3)

**Special Thanks:**
- **viczhong** - Reported Issue #109 with clear screenshots
- **connorcharless** - Detailed bug reports and willingness to test diagnostic builds

### Development Tools
- Gemini CLI analysis for tablet layout investigation
- Comprehensive lint and test automation
- Systematic debugging methodology with enhanced logging
- Superpowers skills for structured workflows

---

## 📝 Change Summary by Category

### Patch 3 (November 12, 2025)
**Bug Fixes (2):**
- Echo mode settings persistence (Issue #108)
- Echo exercises showing "Adaptive" (Issue #109)

**Privacy (1):**
- MAC address removal from all logs

**Diagnostics (2):**
- Eccentric load BLE frame logging
- Routine loading WorkoutType logging

**Files Modified:** 7 files, ~150 lines

### Beta 4 Base Release
**Bug Fixes (11):**
- Single exercise mode rest timer (0/0 sets)
- Single exercise mode rep count reset
- Tablet layout black screen
- Connection cancel not working
- History card title showing mode
- History card mode truncation
- History card set count including warmup
- History card progress bar calculation
- Workout progress indicator not updating
- Workout mode not preserved
- Multi-set weight display

**Features (6):**
- Hardware detection system
- Video playback toggle
- Set summary card
- Real-time cable position bars
- Scroll indicators
- Connection cancel button
- End workout confirmation

**UI/UX (9):**
- Theme-aware gradients
- Countdown timer visibility
- Roller picker visibility on tablets
- Missing back arrow
- Scroll indicator prominence
- Light mode gradient colors
- Dark mode gradient support
- Consistent theme behavior
- Just Lift hold timer extension

**Technical (4):**
- Logging standardization (Timber)
- Test coverage expansion
- Rep counting enhancements
- Connection logging improvements

---

## 🚀 Next Steps for Beta 5

### Planned Improvements
- Address deprecated Material Icons
- Fix 3 failing unit tests
- Update dependencies (Kotlin, Hilt, Coroutines)
- Optimize autoboxing state creations
- Add monochrome launcher icon

### Under Investigation
- Issue #95: Root cause analysis from user logs
- Issue #107: Eccentric load 150% verification
- Euclid eccentric mode protocol analysis
- Performance optimization for large exercise libraries
- Enhanced BLE connection reliability
- Firmware-based hardware detection

---

## 📞 Support

**Issues:** https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix/issues
**Email:** VitruvianRedux@gmail.com (auto-filled in log exports!)
**Documentation:** See README.md and CLAUDE.md
**Community:** This is a community rescue project for Vitruvian Trainer machines

---

## 🔨 Build Info

**Gradle:** 8.13.1
**Kotlin:** 2.0.21
**Compose BOM:** 2024.09.00
**Compiled:** November 2025
**Build Duration:** ~6s (incremental), ~60s (clean)

---

## 📄 Additional Documentation

- **Patch 3 Details:** [RELEASE_NOTES_ECHO_MODE_FIXES.md](RELEASE_NOTES_ECHO_MODE_FIXES.md)
- **Issue #108 Fix:** [RELEASE_NOTES_ISSUE108.md](RELEASE_NOTES_ISSUE108.md)
- **Patch 1 Details:** [DISTRIBUTION_PACKAGE.md](DISTRIBUTION_PACKAGE.md)
- **Full Changelog:** [RELEASE_NOTES_BETA4.md](RELEASE_NOTES_BETA4.md)
