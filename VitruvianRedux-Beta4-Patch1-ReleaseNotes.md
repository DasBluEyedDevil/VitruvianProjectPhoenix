# Vitruvian Redux - Beta 4 Patch 1 Release Notes

**Version:** 0.4.0-beta (Patch 1)
**Build:** Beta 4 Patch 1
**Release Date:** November 12, 2025
**Build Status:** ✅ Build Successful

---

## 🎯 Overview

Beta 4 Patch 1 is a diagnostic and usability release focused on improving log sharing for bug reports and adding comprehensive instrumentation to investigate Issue #95 (connection command failures).

**Key Improvements:**
- 📧 Enhanced log export with automatic email pre-population
- 🔍 Diagnostic instrumentation for BLE characteristic lifecycle tracking
- 🐛 Improved debugging capabilities for connection issues

---

## 🆕 New Features

### Enhanced Log Sharing
**IMPROVED:** Connection log export now pre-populates email for easy bug reporting

**Features:**
- **Auto-filled email:** `VitruvianRedux@gmail.com` automatically set as recipient
- **Pre-populated subject:** "VitruvianRedux Connection Logs - Issue Report"
- **Structured email body template:**
  - GitHub Issue # field
  - Device Model (auto-detected)
  - Android Version (auto-detected)
  - Description of issue (template)
  - Steps to reproduce (numbered list template)
- **Improved dialog text:** Better explains the share functionality
- **Updated button label:** Changed from "Export" to "Share" for clarity

**Impact:** Makes it significantly easier for users to report bugs with proper context

**Files Modified:**
- ConnectionLogsScreen.kt:205-268

**User Experience:**
1. Tap Share button on Connection Logs screen
2. See helpful dialog explaining process
3. Tap "Share" → Android share sheet opens
4. Select Email app → All fields pre-filled
5. Fill in issue description and hit send ✅

---

## 🔍 Diagnostic Instrumentation for Issue #95

### Problem Being Investigated
Users report "NUS RX characteristic not available" errors after successful initialization, preventing LED color changes and workout starts (Issue #95).

### Diagnostic Logging Added

#### 1. Characteristic Invalidation Tracking
**File:** VitruvianBleManager.kt:211-238

Added comprehensive logging when characteristics are nulled:
```
⚠️ onServicesInvalidated() CALLED! [timestamp]
⚠️ This will NULL all characteristic references!
⚠️ Stack trace: (shows what triggered it)
```

**Purpose:** Identifies WHEN and WHY characteristics are being invalidated after successful connection

**Includes:**
- Timestamp of invalidation
- Stack trace showing calling context
- ConnectionLogger integration for export

#### 2. Command State Debugging
**File:** VitruvianBleManager.kt:419-423

Added state logging when sending commands:
```
SEND_COMMAND_DEBUG: nusRxCharacteristic is NULL/AVAILABLE
SEND_COMMAND_DEBUG: isConnected=true/false
SEND_COMMAND_DEBUG: connectionState=...
```

**Purpose:** Shows exact characteristic and connection state at time of command failure

#### 3. Characteristic Discovery Logging
**File:** VitruvianBleManager.kt:156-164

Enhanced logging when characteristics are discovered:
```
✅ CHARACTERISTICS DISCOVERED! [timestamp]
✅ RX=true, Monitor=true, Property=true, RepNotify=true
✅ nusRxCharacteristic UUID: ... instance: ...
```

**Purpose:** Tracks characteristic lifecycle from discovery through invalidation

---

## 🧪 Testing Strategy

### For Issue #95 Investigation

**What to Test:**
1. Connect to Vitruvian device
2. Wait for initialization to complete
3. Try to change LED color (should fail per issue report)
4. Export connection logs using new Share feature
5. Email logs to VitruvianRedux@gmail.com

**What We'll Learn:**
The exported logs will show:
- Exact timing of characteristic invalidation
- Stack trace revealing what triggered it
- Connection state when commands fail
- Whether invalidation happens during init or after

**Expected Outcome:**
Logs will reveal the root cause, allowing implementation of proper fix in next patch

---

## 🔧 Technical Details

### Changes Summary
**Files Modified:** 2
- ConnectionLogsScreen.kt (enhanced share functionality)
- VitruvianBleManager.kt (diagnostic instrumentation)

**Lines Changed:**
- +64 lines (diagnostic logging and email template)
- Focused, targeted changes for debugging

**Build Impact:**
- No performance impact (logging only active during debugging)
- No behavior changes to existing functionality
- Maintains compatibility with Beta 4

---

## 📋 Known Issues

### Unchanged from Beta 4
- 3 pre-existing unit test failures (non-critical)
- Euclid eccentric mode compatibility under investigation
- Issue #95 still present (diagnostic patch only)

### This Patch
- **Issue #95:** NOT YET FIXED - this is a diagnostic build to gather evidence
- Next patch will implement actual fix based on log analysis

---

## 🔄 Migration Notes

### Upgrading from Beta 4
- **No data migration required**
- All settings, routines, and programs preserved
- Drop-in replacement for Beta 4
- Enhanced logging automatically active

### For Beta Testers
**IMPORTANT:** If you experience connection issues (especially LED color changes or workout start failures):
1. Reproduce the issue
2. Tap Share button on Connection Logs screen
3. Email logs to VitruvianRedux@gmail.com (auto-filled)
4. Include Issue # 95 in the email

Your logs will help us fix this issue permanently!

---

## 📦 Distribution

**APK Location:** `app/build/outputs/apk/debug/app-debug.apk`
**Min SDK:** Android 8.0 (API 26)
**Target SDK:** Android 13 (API 36)

**Installation:**
- Uninstall Beta 4 (optional - can install over it)
- Install Beta 4 Patch 1 APK
- All data preserved automatically

---

## 🙏 Acknowledgments

### Issue #95 Reporter
Thanks to **connorcharless** for:
- Detailed issue report with clear reproduction steps
- Patience working through connection issues
- Willingness to test diagnostic builds

### Methodology
Following **systematic-debugging** best practices:
- Phase 1: Root cause investigation via instrumentation ✅
- Phase 2: Evidence gathering from user testing (in progress)
- Phase 3: Hypothesis formation (pending logs)
- Phase 4: Fix implementation (next patch)

---

## 🚀 What's Next

### Beta 4 Patch 2 (Pending Log Analysis)
Based on diagnostic logs from this patch:
1. Identify root cause of characteristic invalidation
2. Implement fix preventing premature nulling
3. Add safeguards against similar issues
4. Verify fix resolves Issue #95

### Beta 5 (Future)
- Continue code quality improvements
- Address remaining lint warnings
- Update dependencies
- Enhance BLE connection reliability

---

## 📞 Support

**Email:** VitruvianRedux@gmail.com (now auto-filled in log exports!)
**Issues:** https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix/issues
**Current Focus:** Issue #95 investigation

---

**Build Info:**
- Build Date: November 12, 2025
- Build Time: ~3s (incremental from Beta 4)
- Gradle: 8.13.1
- Kotlin: 2.0.21
- Based on: Beta 4 (0.4.0-beta)
