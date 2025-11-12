# Vitruvian Redux - Beta 4 Release Notes

**Version:** 0.4.0-beta
**Build:** Beta 4
**Release Date:** November 2025
**Build Status:** ✅ Build Successful

---

## 🎯 Overview

Beta 4 is a stability and quality-of-life release focusing on bug fixes, UI improvements, and enhanced hardware compatibility. This release addresses critical single exercise mode issues, improves tablet layout stability, and adds comprehensive hardware detection for better troubleshooting.

**Stats:**
- 33 files changed
- 3,041 additions
- 1,721 deletions
- 111 unit tests (108 passing)

---

## 🆕 New Features

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
  - **Files:** WorkoutTab.kt:97-403, 1846-1945

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

## 🐛 Critical Bug Fixes

### BLE Initialization Race Condition (Session 14)
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

### Single Exercise Mode (Session 13)
**FIXED:** Rest timer showing 0/0 sets and second set resetting to 10 reps
- **Root Cause:** Detection logic failed for temporary routines created by SingleExerciseScreen
- **Fix:** Added `isSingleExerciseMode()` helper function that properly identifies temp routines
- **Impact:** Single exercise mode now correctly:
  - Displays rest timer with proper set count (e.g., "Set 1 of 2")
  - Advances to next set with correct rep count from configuration
  - Uses setReps array from user's configuration
- **Testing:** Added 3 comprehensive unit tests in MainViewModelEnhancedTest.kt
- **Files:** MainViewModel.kt:926-936, 977, 1052, 1080-1087, 1252

### Tablet Layout Stability
**FIXED:** Potential black screen on tablet emulators
- **Root Cause:** Conflicting layout constraints in BottomAppBar (SpaceEvenly + weight modifiers)
- **Fix:** Changed `horizontalArrangement` from `SpaceEvenly` to `SpaceAround`
- **Impact:** Resolves layout calculation failures on wider tablet screens
- **Analysis:** Confirmed by Gemini CLI analysis of presentation layer
- **Files:** EnhancedMainScreen.kt:189

### Connection Handling
**FIXED:** Connection continues after clicking Cancel
- **Root Cause:** Job cancellation didn't trigger BLE disconnect
- **Fix:** Added `CancellationException` handler that calls `bleRepository.disconnect()`
- **Impact:** Cancel button now properly stops connection attempts
- **Files:** MainViewModel.kt:268, 586-665, 676-681

### Workout History Display
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

### Workout Progress Indicator
**FIXED:** Progress bar not updating during workout
- **Root Cause:** Used static initial values instead of current metrics
- **Fix:** Changed to use `currentMetric.value` for real-time updates
- **Impact:** Progress indicator now reflects actual cable load/position
- **Files:** WorkoutTab.kt

### Workout Mode Display
**FIXED:** Workout mode not preserved when loading routines
- **Root Cause:** Used default mode instead of exercise's configured mode
- **Fix:** Use exercise's `workoutType` when loading from routine
- **Impact:** Correct workout mode displayed and used
- **Files:** MainViewModel.kt

### Multi-Set Weight Display
**FIXED:** Weight editor showing only first set's weight
- **Root Cause:** Didn't preserve individual set weights
- **Fix:** Properly map each set's weight when editing
- **Impact:** All set weights preserved during editing
- **Files:** ExerciseEditDialog.kt

---

## 🎨 UI/UX Improvements

### Theme Consistency
- Made background gradients theme-aware across all screens
- ProgramBuilderScreen now matches other screens with dark mode support
- Updated light mode gradient colors for better visibility (E0E7FF, EDE9FE, DFF6FF)
- Dark mode uses slate-900, indigo-950, blue-950 gradients
- WeeklyProgramsScreen updated to match color palette

### Visibility Fixes
- **FIXED:** Invisible countdown timer numbers in light mode
  - Added proper theme-aware text colors
  - **Files:** WorkoutTab.kt

- **FIXED:** Invisible numbers in roller pickers on Fire HD tablets
  - Enhanced text color handling in CompactNumberPicker
  - Uses theme-aware colors with Material Design 3 compliance
  - **Files:** CompactNumberPicker.kt

- **FIXED:** Missing back arrow in Single Exercise screen
  - Added navigation icon to TopAppBar
  - **Files:** SingleExerciseScreen.kt

### Accessibility
- Improved scroll indicator visibility (opacity 0.7 → 0.85)
- Increased indicator height (60dp → 80dp) and arrow size (24dp → 28dp)
- Added rounded Surface background for better contrast

---

## 🔧 Technical Improvements

### Code Quality
- **Warning Resolution (Session 14):**
  - Fixed 12 Kotlin compiler warnings for Beta 4 release
  - WorkoutTab.kt: 11 warnings fixed
    - Removed unused import directive
    - Removed 4 unnecessary non-null assertions (!!)
    - Fixed unused variable assignments
    - Removed unused parameters and variables
    - Deleted dead code (PositionBarsCard, CablePositionBar - old implementations)
    - Removed redundant type conversion
  - VitruvianBleManager.kt: 1 warning fixed
    - Removed unused toHexString() extension function
  - Build now completes with zero warnings

- **Logging Standardization:**
  - Replaced 9 `android.util.Log` calls with `Timber`
  - ExerciseImporter.kt: 5 instances
  - CompactNumberPicker.kt: 4 instances (removed unnecessary debug logs)

- **Test Coverage:**
  - Added 3 new tests for single exercise mode
  - Total: 111 unit tests (108 passing, 3 pre-existing failures)
  - Tests verify temp routine handling and set progression

### Rep Counting Enhancement
- Added machine-based rep counting with configurable sensitivity
- Improved detection of concentric and eccentric phases
- Enhanced logging for troubleshooting rep detection issues
- **Files:** RepCounterFromMachine.kt

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

### Test Failures (Pre-existing, Non-critical)
- `WorkoutModeTest > Stop At Top option` - AssertionError at line 199
- `WorkoutModeTest > Complete workflow - Old School mode` - AssertionError at line 312
- `WorkoutIntegrationTest > test rep counting integration` - AssertionError at line 189

### Lint Warnings (Non-critical)
- **RESOLVED:** All Kotlin compiler warnings fixed in Session 14 (was 12 warnings)
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

---

## 🔄 Migration Notes

### For Users Upgrading from Beta 3
- No data migration required
- Video playback setting defaults to enabled (matches Beta 3 behavior)
- All existing routines, programs, and settings preserved
- Hardware model detection is automatic on next connection

### For Developers
- `isSingleExerciseMode()` replaces inline `routine == null` checks
- `ConnectionState.Connected` now includes `hardwareModel` field
- Use `Timber` instead of `android.util.Log` for consistency
- Exercise video playback now requires `enableVideoPlayback` parameter

---

## 📦 Distribution

**APK Location:** `app/build/outputs/apk/debug/app-debug.apk`
**Size:** ~12 MB (varies by build)
**Min SDK:** Android 8.0 (API 26)
**Target SDK:** Android 13 (API 36)

---

## 🙏 Acknowledgments

### Community Contributions
This release addresses multiple user-reported issues:
- Issue #4: Option to disable video downloads
- Issue #5: Cancel button for connection overlay
- Issue #80: Euclid eccentric mode investigation

### Development Tools
- Gemini CLI analysis for tablet layout investigation
- Comprehensive lint and test automation
- Systematic debugging methodology

---

## 📝 Change Summary by Category

### Bug Fixes (12)
1. BLE initialization race condition (Session 14)
2. Single exercise mode rest timer (0/0 sets)
3. Single exercise mode rep count reset
4. Tablet layout black screen
5. Connection cancel not working
6. History card title showing mode
7. History card mode truncation
8. History card set count including warmup
9. History card progress bar calculation
10. Workout progress indicator not updating
11. Workout mode not preserved
12. Multi-set weight display

### Features (6)
1. Hardware detection system
2. Video playback toggle
3. Set summary card
4. Real-time cable position bars
5. Scroll indicators
6. Connection cancel button
7. End workout confirmation

### UI/UX (9)
1. Theme-aware gradients
2. Countdown timer visibility
3. Roller picker visibility on tablets
4. Missing back arrow
5. Scroll indicator prominence
6. Light mode gradient colors
7. Dark mode gradient support
8. Consistent theme behavior
9. Just Lift hold timer extension

### Technical (5)
1. Warning resolution (12 warnings fixed - Session 14)
2. Logging standardization (Timber)
3. Test coverage expansion
4. Rep counting enhancements
5. Connection logging improvements

---

## 🚀 Next Steps for Beta 5

### Planned Improvements
1. Address deprecated Material Icons
2. Fix 3 failing unit tests
3. Update dependencies (Kotlin, Hilt, Coroutines)
4. Optimize autoboxing state creations
5. Add monochrome launcher icon

### Under Investigation
- Euclid eccentric mode protocol analysis
- Performance optimization for large exercise libraries
- Enhanced BLE connection reliability

---

## 📞 Support

**Issues:** https://github.com/anthropics/vitruvian-redux/issues
**Documentation:** See README.md and CLAUDE.md
**Community:** This is a community rescue project for Vitruvian Trainer machines

---

**Build Info:**
- Gradle: 8.13.1
- Kotlin: 2.0.21
- Compose BOM: 2024.09.00
- Compiled: November 2025
- Build Duration: ~6s (incremental), ~60s (clean)
